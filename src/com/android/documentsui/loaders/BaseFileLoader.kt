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

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Trace
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import com.android.documentsui.DirectoryResult
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.roots.RootCursorWrapper

val FILE_ENTRY_COLUMNS =
    arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SUMMARY,
        Document.COLUMN_SIZE,
        Document.COLUMN_ICON,
    )

fun emptyCursor(): Cursor {
    return MatrixCursor(FILE_ENTRY_COLUMNS)
}

/** Merges MIME types specs supplied by V1 and V2 of search. */
fun computeAcceptableMimeTypes(queryOptions: QueryOptions): Array<String>? {
    var mimeTypes = queryOptions.acceptableMimeTypes
    val otherMimeTypes =
        queryOptions.otherQueryArgs.getStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES)
    if (mimeTypes == null) {
        mimeTypes = otherMimeTypes
    } else if (otherMimeTypes != null) {
        mimeTypes = (mimeTypes + otherMimeTypes).toSet().toTypedArray()
    }
    // TODO(b/450381836): Optimize. Remove duplicates; if */* remove all but */*.
    return mimeTypes
}

/**
 * Helper function that returns a single, non-null cursor constructed from the given list of
 * cursors.
 */
fun toSingleCursor(cursorList: List<Cursor>): Cursor {
    if (cursorList.isEmpty()) {
        return emptyCursor()
    }
    if (cursorList.size == 1) {
        return cursorList[0]
    }
    return MergeCursor(cursorList.toTypedArray())
}

/**
 * The base class for search and directory loaders. This class implements common functionality
 * shared by these loaders. The extending classes should implement loadInBackground, which should
 * call the queryLocation method.
 */
abstract class BaseFileLoader(
    context: Context,
    protected val mimeTypeLookup: Lookup<String, String>,
) : AsyncTaskLoader<DirectoryResult>(context) {

    companion object {
        const val TAG = "SearchV2"
        var instanceCounter = 0
    }

    val myInstance = ++instanceCounter

    /**
     * The cancellation signal passed to the `client.query()` method that allows us to notify the
     * client about the query being canceled while it is still being run. Extending classes need to
     * set it to a non-null value if they wish to be able to cancel queries in progress.
     */
    protected var cancelNotifier: CancellationSignal? = null
    private var storedResult: DirectoryResult? = null

    /** A convenience debug logging method. */
    protected fun debugLog(message: String, e: Exception? = null) {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}#$myInstance: $message", e)
        }
    }

    /**
     * Overrides the default implementation to notify content provider clients with still running
     * queries that the loading has been canceled. This only takes place if the cancelNotifier
     * instance variable has been initialized by extending classes.
     */
    override fun cancelLoadInBackground() {
        debugLog("cancelLoadInBackground")
        super.cancelLoadInBackground()

        synchronized(this) { cancelNotifier?.cancel() }
    }

    override fun deliverResult(result: DirectoryResult?) {
        debugLog("deliverResult")
        if (isReset) {
            closeResult(result)
            return
        }
        val oldResult: DirectoryResult? = storedResult
        storedResult = result

        if (isStarted) {
            super.deliverResult(result)
        }

        if (oldResult != null && oldResult !== result) {
            closeResult(oldResult)
        }
    }

    override fun onStartLoading() {
        debugLog("onStartLoading")
        val isCursorStale: Boolean = checkIfCursorStale(storedResult)
        if (storedResult != null && !isCursorStale) {
            deliverResult(storedResult)
        }
        if (isCursorStale) {
            resetInternal()
        }
        if (takeContentChanged() || storedResult == null || isCursorStale) {
            forceLoad()
        }
    }

    /** Used when start loading detects stale cursor or content changed to reset internal data. */
    open fun resetInternal() {}

    override fun onStopLoading() {
        debugLog("onStopLoading")
        cancelLoad()
    }

    override fun onCanceled(result: DirectoryResult?) {
        debugLog("onCanceled")
        closeResult(result)
    }

    override fun onReset() {
        debugLog("onReset")
        super.onReset()

        // Ensure the loader is stopped
        onStopLoading()

        closeResult(storedResult)
        storedResult = null
    }

    /** Quietly closes the result cursor, if results are still available. */
    fun closeResult(result: DirectoryResult?) {
        executor.execute {
            try {
                result?.close()
            } catch (e: Exception) {
                debugLog("Failed to close result", e)
            }
        }
    }

    private fun checkIfCursorStale(result: DirectoryResult?): Boolean {
        if (result == null) {
            return true
        }
        val cursor = result.cursor ?: return true
        if (cursor.isClosed) {
            return true
        }
        debugLog("Long check of cursor staleness")
        val count = cursor.count
        // Do not check if moveToPosition succeeded (returned true), as moveToPosition(-1) always
        // returns false.
        cursor.moveToPosition(-1)
        for (i in 1..count) {
            if (!cursor.moveToNext()) {
                return true
            }
        }
        return false
    }

    /**
     * A function that, for the specified location rooted in the root with the given rootId attempts
     * to obtain a non-null cursor from the content provider client obtained for the given
     * locationUri. It returns a non-null cursor, if it can access the location given by the
     * `locationUri`, or null, if it fails to query the given location for the current user.
     */
    fun queryLocation(rootInfo: RootInfo, locationUri: Uri, queryArgs: Bundle?): Cursor? {
        try {
            Trace.beginSection("documentsui.searchv2.BaseFileLoader#queryLocation")
            return queryLocationTraced(rootInfo, locationUri, queryArgs)
        } finally {
            Trace.endSection()
        }
    }

    /** A queryLocation code run within a trace. */
    private fun queryLocationTraced(
        rootInfo: RootInfo,
        locationUri: Uri,
        queryArgs: Bundle?,
    ): Cursor? {
        val authority = locationUri.authority ?: return null
        val resolver = rootInfo.userId.getContentResolver(context) ?: return null
        DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority).use { client ->
            // TODO(b:440453094): Fix handling of cancel signal is documents providers.
            val cursor = client.query(locationUri, null, queryArgs, cancelNotifier) ?: return null
            return createRootCursorWrapper(rootInfo, locationUri, cursor)
        }
    }

    /**
     * A method to create RootCursorWrapper given a result Cursor from the query. This allows
     * subclass to override the creation, such as specifying max result or authority.
     */
    open fun createRootCursorWrapper(
        rootInfo: RootInfo,
        locationUri: Uri,
        cursor: Cursor,
    ): RootCursorWrapper =
        RootCursorWrapper(
            rootInfo.userId,
            locationUri.authority,
            rootInfo.rootId,
            rootInfo.hasLimitedFunctionalityWhenOffline(),
            cursor,
            ALL_RESULTS,
        )
}
