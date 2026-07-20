/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui.dirlist

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
import android.provider.DocumentsContract.Document.COLUMN_SUMMARY
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.android.documentsui.ModelId
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Providers
import com.android.documentsui.base.UserId
import com.android.documentsui.loaders.QueryOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Maps from the ModelId to the summary of the document. */
typealias Summaries = Map<String, String>

/**
 * Manages document summaries, fetching from the summary provider and observing changes. Provides
 * reactive updates when the underlying data changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class SummariesViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    /**
     * Data used to open and monitor the cursor. We push a new data here everytime we need to
     * recreate and monitor a new cursor.
     */
    private val queryInfoFlow = MutableStateFlow<QueryInfo?>(null)

    /** Emits the latest map of ModelId to summary strings. */
    open val summaries: StateFlow<Summaries> =
        queryInfoFlow
            .flatMapLatest { info ->
                if (info == null) {
                    flowOf(emptyMap())
                } else {
                    monitor(info)
                }
            }
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap(),
            )

    /**
     * Emits the latest map of ModelId to summary strings. This is provided for easier integration
     * with Java code.
     */
    open val summariesLiveData: LiveData<Summaries> = summaries.asLiveData()

    /** Updates the summaries for the given set of documents and begins monitoring for changes. */
    fun update(
        summaryAuthorityUri: Uri?,
        parentDoc: DocumentInfo?,
        modelIds: List<String>,
        options: QueryOptions?,
        query: String?,
    ) {
        val info = calculateQueryInfo(summaryAuthorityUri, parentDoc, modelIds, options, query)
        queryInfoFlow.value = info
    }

    private fun monitor(queryInfo: QueryInfo): Flow<Summaries> = callbackFlow {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    launch(ioDispatcher) { send(performQuery(queryInfo)) }
                }
            }

        val contentResolver = queryInfo.userId.getContentResolver(getApplication())
        val cursor =
            try {
                contentResolver.query(
                    queryInfo.parentUri,
                    summaryProjection,
                    queryInfo.queryArgs,
                    null,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open monitoring cursor for ${queryInfo.parentUri}", e)
                null
            }

        if (cursor != null) {
            cursor.registerContentObserver(observer)
            // Initial emit of the summaries from this cursor.
            send(processCursor(cursor, queryInfo.docIdToModelId))
        } else {
            send(emptyMap())
        }

        awaitClose { cursor?.close() }
    }

    private suspend fun performQuery(queryInfo: QueryInfo): Summaries {
        val contentResolver = queryInfo.userId.getContentResolver(getApplication())
        return try {
            contentResolver
                .query(queryInfo.parentUri, summaryProjection, queryInfo.queryArgs, null)
                ?.use { cursor -> processCursor(cursor, queryInfo.docIdToModelId) } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query summaries for ${queryInfo.parentUri}", e)
            emptyMap()
        }
    }

    /**
     * Processes a [Cursor] and returns a map of ModelId to summary.
     *
     * @param cursor The cursor containing document data.
     * @param docIdToModelId A mapping from document IDs to ModelIds.
     * @return A map where keys are ModelIds and values are their corresponding summaries.
     */
    internal fun processCursor(cursor: Cursor, docIdToModelId: Map<String, String>): Summaries {
        val loadedSummaries = mutableMapOf<String, String>()
        val idIdx = cursor.getColumnIndex(COLUMN_DOCUMENT_ID)
        val summaryIdx = cursor.getColumnIndex(COLUMN_SUMMARY)

        if (idIdx == -1 || summaryIdx == -1) {
            return emptyMap()
        }

        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val docId = cursor.getStringOrNull(idIdx)
            val summary = cursor.getStringOrNull(summaryIdx)
            if (summary.isNullOrEmpty() || docId == null) {
                continue
            }
            val modelId = docIdToModelId[docId]
            if (modelId != null) {
                loadedSummaries[modelId] = summary
            }
        }
        return loadedSummaries
    }

    /**
     * Calculates the necessary information to perform a summary query.
     *
     * @param summaryAuthorityUri The root URI of the summary provider.
     * @param parentDoc Information about the parent document being queried.
     * @param modelIds The list of ModelIds for which summaries are requested.
     * @param options Query options such as limits and timestamps.
     * @param query An optional display name query string.
     * @return A [QueryInfo] object containing the URI, arguments, and ID mapping, or null if
     *   parameters are invalid.
     */
    private fun calculateQueryInfo(
        summaryAuthorityUri: Uri?,
        parentDoc: DocumentInfo?,
        modelIds: List<String>,
        options: QueryOptions?,
        query: String?,
    ): QueryInfo? {
        val summaryAuthority = summaryAuthorityUri?.authority ?: return null

        val isRecents = parentDoc?.authority == null && parentDoc?.documentId.isNullOrEmpty()
        val userId = parentDoc?.userId ?: UserId.CURRENT_USER

        val parentUri =
            if (isRecents) {
                DocumentsContract.buildChildDocumentsUri(
                    summaryAuthority,
                    DocumentsContract.getRootId(summaryAuthorityUri),
                )
            } else if (parentDoc != null) {
                DocumentsContract.buildChildDocumentsUri(summaryAuthority, parentDoc.documentId)
            } else {
                return null
            }

        val contextUri =
            if (isRecents) {
                DocumentsContract.buildDocumentUri(
                    Providers.AUTHORITY_MEDIA,
                    Providers.ROOT_ID_FILES,
                )
            } else if (parentDoc != null) {
                DocumentsContract.buildDocumentUri(parentDoc.authority, parentDoc.documentId)
            } else {
                return null
            }

        val docIdToModelId = mutableMapOf<String, String>()
        for (modelId in modelIds) {
            val docId = ModelId.getDocumentId(modelId) ?: continue
            docIdToModelId[docId] = modelId
        }

        val queryArgs = Bundle()
        if (options != null) {
            val rejectBefore = options.getRejectBeforeTimestamp()
            if (rejectBefore > 0L) {
                queryArgs.putLong(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER, rejectBefore)
            }
            if (!query.isNullOrEmpty()) {
                queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, query)
            }
            if (options.maxResultsPerRoot > ALL_RESULTS) {
                queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, options.maxResultsPerRoot)
            }
            queryArgs.putAll(options.otherQueryArgs)
        }
        queryArgs.putParcelable(EXTRA_URI, contextUri)

        return QueryInfo(userId, parentUri, queryArgs, docIdToModelId)
    }

    /**
     * Encapsulates all data required to execute and monitor a summary query.
     *
     * @property userId The [UserId] of the user that owns the documents.
     * @property parentUri The child documents URI used for the query.
     * @property queryArgs The [Bundle] of arguments for the content resolver query.
     * @property docIdToModelId A mapping from provider document IDs to internal ModelIds.
     */
    private data class QueryInfo(
        val userId: UserId,
        val parentUri: Uri,
        val queryArgs: Bundle,
        val docIdToModelId: Map<String, String>,
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SummariesViewModel(application, Dispatchers.IO) as T
        }
    }

    companion object {
        private const val TAG = "SummariesViewModel"
        private const val EXTRA_URI = "uri"
        private const val ALL_RESULTS = -1
        internal val summaryProjection = arrayOf(COLUMN_DOCUMENT_ID, COLUMN_SUMMARY)
    }
}
