/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui.loaders

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Trace
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.TextUtils
import android.util.Log
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.roots.RootCursorWrapper
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.util.FlagUtils.Companion.isSyncStateEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseLocalSearchProviderEnabled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.measureTime

/**
 * The extra arg of URI to pass the context where the search is performed at. DocumentsProvider may
 * use this information to apply the appropriate scope to the search.
 */
const val EXTRA_URI = "uri"

/**
 * A wrapper around the cursor. We use it to distinguish between pending tasks (null query result)
 * and completed tasks. Completed tasks may have cursor null, if this is what a given content
 * provider returns.
 */
internal data class QueryResult(var rootInfo: RootInfo, var cursor: Cursor? = null)

/**
 * A specialization of the BaseFileLoader that searches the set of specified roots. To search the
 * roots you must a provider:
 * - The current application context
 * - A content lock for which a locking content observer is built
 * - A list of user IDs, on whose behalf we query content provider clients.
 * - A list of RootInfo objects representing searched roots
 * - A query used to search for matching files.
 * - Query options such as maximum number of results, last modified time delta, etc.
 * - a lookup from file extension to file type
 * - The model capable of sorting results
 * - An executor for running searches across multiple roots in parallel
 *
 * SearchLoader requires that either a query is not null and not empty or that QueryOptions specify
 * a last modified time restriction. This is to prevent searching for every file across every
 * specified root.
 */
class SearchLoader(
    context: Context,
    private val rootInfoList: Collection<RootInfo>,
    private val semanticSearchRootInfo: RootInfo?,
    mimeTypeLookup: Lookup<String, String>,
    private val observer: LockingContentObserver,
    private val query: String?,
    private val options: QueryOptions,
    private val sortModel: SortModel,
    private val executorService: ExecutorService,
) : BaseFileLoader(context, mimeTypeLookup) {

    /**
     * Helper class that runs query on a single user for the given parameter, until the first
     * queried URI is successful. This class implements an abstract future so that if the task is
     * completed, we can retrieve the cursor via the get method.
     */
    inner class SearchTask(
        private val rootInfo: RootInfo,
        private val searchUris: List<Uri>,
        private val queryArgs: Bundle,
        internal val index: Int,
        private val latch: CountDownLatch,
    ) : Runnable {
        internal var cursor: Cursor? = null
        internal val taskId: String
            get() = searchUris.joinToString()

        private fun tryQuery(searchUri: Uri): Cursor? {
            var result: Cursor? = null
            val queryDuration = measureTime {
                try {
                    result = queryLocation(rootInfo, searchUri, queryArgs)
                } catch (e: Exception) {
                    debugLog("failed to get cursor for ${searchUri.authority}", e)
                }
            }
            debugLog("query on ${searchUri.authority} took $queryDuration")
            return result
        }

        override fun run() {
            Trace.beginSection("documentsui.searchv2.SearchLoader.SearchTask#run")
            for (searchUri in searchUris) {
                val result = tryQuery(searchUri)
                if (result != null) {
                    cursor = result
                    break
                }
            }
            // Content observer must be set only once. This is why we are setting it
            // on each retrieved cursor, rather than on the merged cursor.
            // TODO(b:388130971): Content change should only force requery (#comment3).
            cursor?.registerContentObserver(observer)
            onTaskCompleted(this)
            latch.countDown()
            Trace.endSection()
        }

        internal val queryResult: QueryResult
            get() = QueryResult(this.rootInfo, cursor)
    }

    override fun createRootCursorWrapper(
        rootInfo: RootInfo,
        locationUri: Uri,
        cursor: Cursor,
    ): RootCursorWrapper =
        RootCursorWrapper(
            rootInfo.userId,
            if (shouldUseSemanticSearch(rootInfo)) rootInfo.authority else locationUri.authority,
            rootInfo.rootId,
            rootInfo.hasLimitedFunctionalityWhenOffline(),
            cursor,
            options.maxResults,
        )

    private val searchTaskList = mutableListOf<SearchTask>()

    // The results cursors, set by search task as they become available.
    private val queryResults = Array<QueryResult?>(rootInfoList.size) { null }

    // Indicates if the first pass for results is done. This is used to prevent tasks
    // that completed before the deadline from forcing content change calls.
    private var firstPassDone = AtomicBoolean(false)

    // A latch that counts the number of tasks done. Used to check if all tasks are completed.
    private var countDownLatch = CountDownLatch(rootInfoList.size)

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        try {
            Trace.beginSection("documentsui.searchv2.SearchLoader#loadInBackground")
            return loadInBackgroundTraced()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Forces content fresh if the first pass has been done. This method is called by each search
     * tasks that complete. If the call is made after the first pass, it triggers onContentChanged
     * call, which results in re-run for loadInBackground. This re-run tries to create not yet
     * created search tasks, and runs them on the provided executor. For already running, but not
     * yet completed tasks the code just waits for them to be completed.
     */
    private fun maybeRefreshContent(taskId: String) {
        if (firstPassDone.get()) {
            debugLog("forcing refresh on cursor $taskId completed")
            onContentChanged()
        }
    }

    /**
     * Runs search for the first time. The code creates a new list of search tasks, schedules them
     * to be run on the executor and gives tasks up to maxQueryTime (if set) to complete the first
     * run.
     */
    @Throws(InterruptedException::class)
    private fun firstPassRun(latch: CountDownLatch, rejectBeforeTimestamp: Long): Boolean {
        // Step 1: Create a list of new search tasks.
        createSearchTaskList(rejectBeforeTimestamp, latch)
        debugLog("first run created ${searchTaskList.size} tasks")

        // Check if we are canceled; if not copy the task list.
        if (isLoadInBackgroundCanceled) {
            return false
        }

        // Step 2: Enqueue tasks and wait for them to complete or time out.
        for (task in searchTaskList) {
            executorService.execute(task)
        }
        debugLog("started ${searchTaskList.size} search tasks")

        // Step 3: Wait for the results.
        if (options.isQueryTimeUnlimited()) {
            debugLog("waiting for results with no time limit")
            latch.await()
        } else {
            debugLog("waiting ${options.maxQueryTime!!.toMillis()}ms for results")
            latch.await(options.maxQueryTime.toMillis(), TimeUnit.MILLISECONDS)
        }
        debugLog("waiting for results is done")
        return true
    }

    /** The loadInBackground code run within a trace. */
    private fun loadInBackgroundTraced(): DirectoryResult? {
        // While we are building results we do not wish the observer to send any notifications.
        debugLog("pausing content change notifications")
        observer.setPaused(true)
        val rejectBeforeTimestamp = options.getRejectBeforeTimestamp()
        val result = DirectoryResult()
        // TODO(b:378590632): If root list has one root use it to construct result.doc
        result.doc = DocumentInfo()
        result.cursor = emptyCursor()
        result.queryOptions = options
        result.query = query

        var firstPassComplete = false
        if (!firstPassDone.get()) {
            try {
                // Create a new task list and schedule it with the executor.
                firstPassComplete = firstPassRun(countDownLatch, rejectBeforeTimestamp)
            } catch (e: InterruptedException) {
                debugLog("interrupted during first pass ${options.maxQueryTime}")
                // TODO(b:388336095): Record a metrics indicating incomplete search.
                throw RuntimeException(e)
            } finally {
                firstPassDone.set(firstPassComplete)
                debugLog("set firstPassDone to $firstPassComplete")
            }
        }

        // Collect cursors from done tasks.
        var allDone = true
        val cursorList = mutableListOf<Cursor>()
        for (data in queryResults) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            // TODO(b:388336095): Record a metric for each done and not done task.
            if (data == null) {
                allDone = false
            } else {
                // TODO(b:388336095): Record a metric for null and not null cursor.
                val cursor = data.cursor
                if (cursor != null) {
                    cursorList.add(cursor)
                }
                if (
                    isSyncStateEnabled() &&
                        data.rootInfo.hasLimitedFunctionalityWhenOffline() &&
                        (cursor?.count ?: 0) > 0
                ) {
                    // When there are multiple roots in the result, we set this condition when there
                    // is at least one file from a root that has limited functionality when offline.
                    result.hasLimitedFunctionalityWhenOffline = true
                }
            }
        }
        if (
            isSyncStateEnabled() &&
                queryResults.size == 1 &&
                queryResults[0]?.rootInfo?.hasLimitedFunctionalityWhenOffline() ?: false
        ) {
            // When there is only one root in the result, we set this condition when that root has
            // limited functionality when offline. There do not need to be any files present.
            result.hasLimitedFunctionalityWhenOffline = true
        }
        debugLog("search complete with ${cursorList.size} cursors collected")

        // Assign the cursor, after adding filtering and sorting, to the results.
        val cursorExtras = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, !allDone) }
        val mergedCursor = toSingleCursor(cursorList).apply { setExtras(cursorExtras) }
        val nonClosingFilteringCursor =
            object : FilteringCursorWrapper(mergedCursor) {
                override fun close() {
                    debugLog("ignoring close action; closing in onReset")
                }
            }
        nonClosingFilteringCursor.filterHiddenFiles(options.showHidden)
        nonClosingFilteringCursor.filterMimes(
            computeAcceptableMimeTypes(options),
            if (TextUtils.isEmpty(query)) arrayOf(Document.MIME_TYPE_DIR) else null,
        )
        if (rejectBeforeTimestamp > 0L) {
            nonClosingFilteringCursor.filterLastModified(rejectBeforeTimestamp)
        }
        result.cursor = sortModel.sortCursor(nonClosingFilteringCursor, mimeTypeLookup)

        // TODO(b:388336095): Record the total time it took to complete search.
        return result
    }

    /**
     * Notifies this loader that a task running on an executor thread has been completed. Search
     * tasks update different queryResults, so no locks are used in this method. For every completed
     * task we check if we need to refresh the content.
     */
    private fun onTaskCompleted(searchTask: SearchTask) {
        queryResults[searchTask.index] = searchTask.queryResult
        maybeRefreshContent(searchTask.taskId)
    }

    /**
     * Determines if the query is for recent or search.
     *
     * NOTE: recent document URI does not respect query-arg-mime-types restrictions. Thus, we only
     * create the recents URI if both the query and other args are empty.
     */
    private fun isRecentQuery(): Boolean =
        TextUtils.isEmpty(query) && options.otherQueryArgs.isEmpty

    private fun shouldUseSemanticSearch(rootInfo: RootInfo): Boolean =
        !isRecentQuery() &&
            // In this class, "local search" is treated as "semantic search"
            // to make its behavior, such as failure handling, more explicit.
            isUseLocalSearchProviderEnabled() &&
            semanticSearchRootInfo?.supportsSearch() == true &&
            !semanticSearchRootInfo.isEmpty &&
            rootInfo.isLocalOnly

    /** Gets semantic search URI if applicable, or null otherwise. */
    private fun maybeGetSemanticSearchUri(rootInfo: RootInfo): Uri? {
        if (!shouldUseSemanticSearch(rootInfo)) {
            return null
        }
        return semanticSearchRootInfo?.let { rootToSearchUri(it.uri, query) }
    }

    private fun buildSearchDocumentsUri(rootInfo: RootInfo): Uri =
        DocumentsContract.buildSearchDocumentsUri(rootInfo.authority, rootInfo.rootId, query)

    private fun createContentProviderQuery(rootInfo: RootInfo): List<Uri> {
        val semanticSearchUri = maybeGetSemanticSearchUri(rootInfo)

        return if (isRecentQuery()) {
            listOf(DocumentsContract.buildRecentDocumentsUri(rootInfo.authority, rootInfo.rootId))
        } else if (semanticSearchUri != null) {
            listOf(semanticSearchUri, buildSearchDocumentsUri(rootInfo))
        } else {
            listOf(buildSearchDocumentsUri(rootInfo))
        }
    }

    /** Validates if the given URI is a root URI and converts it to a search URI. */
    private fun rootToSearchUri(rootUri: Uri, query: String?): Uri? {
        if (DocumentsContract.isRootUri(context, rootUri)) {
            val rootId = DocumentsContract.getRootId(rootUri)
            return DocumentsContract.buildSearchDocumentsUri(rootUri.authority, rootId, query)
        } else {
            Log.w(
                TAG,
                "The provided URI is not a valid root URI: $rootUri, " +
                    "falling back to regular search.",
            )
            return null
        }
    }

    private fun createQueryArgs(rootInfo: RootInfo, rejectBeforeTimestamp: Long): Bundle {
        val queryArgs = Bundle()
        sortModel.addQuerySortArgs(queryArgs)
        if (rejectBeforeTimestamp > 0L) {
            queryArgs.putLong(
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                rejectBeforeTimestamp,
            )
        }
        if (!TextUtils.isEmpty(query)) {
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, query)
        }
        if (rootInfo.supportsSearchResultLimit() && options.maxResultsPerRoot > ALL_RESULTS) {
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, options.maxResultsPerRoot)
        }
        if (shouldUseSemanticSearch(rootInfo)) {
            // TODO(b:444354898): pass the actual folder stack instead of root to support limit
            // search to folder.
            queryArgs.putParcelable(
                EXTRA_URI,
                DocumentsContract.buildDocumentUri(rootInfo.authority, rootInfo.documentId),
            )
        }
        queryArgs.putAll(options.otherQueryArgs)
        return queryArgs
    }

    /** Helper function that sets the list of search tasks for the given countdown latch. */
    private fun createSearchTaskList(rejectBeforeTimestamp: Long, countDownLatch: CountDownLatch) {
        searchTaskList.clear()
        for ((index, rootInfo) in rootInfoList.withIndex()) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            // Create a task that will set the cursor, once query call completes.
            val searchUris = createContentProviderQuery(rootInfo)
            val queryArgs = createQueryArgs(rootInfo, rejectBeforeTimestamp)
            sortModel.addQuerySortArgs(queryArgs)
            debugLog("querying ${searchUris.map { it.authority }}")
            searchTaskList.add(SearchTask(rootInfo, searchUris, queryArgs, index, countDownLatch))
        }
    }

    override fun deliverResult(result: DirectoryResult?) {
        super.deliverResult(result)
        debugLog("unpausing content change notifications")
        observer.setPaused(false)
    }

    override fun onStopLoading() {
        // If this loader is stopped, do not deliver any change notifications.
        debugLog("pausing content change notifications")
        observer.setPaused(true)
        super.onStopLoading()
    }

    override fun onReset() {
        resetInternal()
        super.onReset()
    }

    /** Overrides the method called when forced load takes place to force full cursor reload. */
    override fun resetInternal() {
        debugLog("resetting internal data structures")
        observer.setPaused(true)
        val previousQueryResults = queryResults.copyOf()
        queryResults.fill(null)
        executor.execute {
            for (data in previousQueryResults) {
                val cursor = data?.cursor
                if (cursor != null) {
                    cursor.unregisterContentObserver(observer)
                    cursor.close()
                }
            }
        }
        searchTaskList.clear()
        firstPassDone.set(false)
        countDownLatch = CountDownLatch(rootInfoList.size)
    }
}
