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

package com.android.documentsui.services

import android.content.ContentProviderClient
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.buildChildDocumentsUri
import com.android.documentsui.MetricConsts
import com.android.documentsui.Metrics
import com.android.documentsui.base.DocumentInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Provides helper functions to traverse the tree rooted at a given document.
 *
 * @param root the root of the tree to traverse
 * @param client the [ContentProviderClient] to use to query children
 * @param queryColumns metadata fields to load for each document traversed
 */
class DocumentTraversalHelper(
    private val root: DocumentInfo,
    private val client: ContentProviderClient,
    private var queryColumns: Array<String>,
    private val appContext: Context,
) {

    companion object {
        private const val LOADING_TIMEOUT = 60000L
    }

    init {
        queryColumns += arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE)
    }

    /*
     * Iterates through the directory tree in post-order traversal, that is, it iterates through the
     * children of a node before returning that node. Returns a pair of the document and its parent.
     */
    fun recursePostOrder(): Flow<Pair<DocumentInfo, DocumentInfo?>> =
        recursePostOrderInternal(root, null)

    private fun recursePostOrderInternal(
        doc: DocumentInfo,
        parent: DocumentInfo?,
    ): Flow<Pair<DocumentInfo, DocumentInfo?>> = flow {
        if (doc.mimeType == Document.MIME_TYPE_DIR) {
            try {
                queryChildren(doc.derivedUri).use { cursor ->
                    while (cursor.moveToNext()) {
                        val child = DocumentInfo.fromCursor(cursor, doc.userId, doc.authority)
                        emitAll(recursePostOrderInternal(child, doc))
                    }
                }
            } catch (e: RemoteException) {
                Metrics.logFileOperationFailure(
                    appContext,
                    MetricConsts.SUBFILEOP_QUERY_CHILDREN,
                    doc.derivedUri,
                )
                throw ResourceException(
                    "Failed to query children of %s due to an exception.",
                    doc.derivedUri,
                    e,
                )
            }
        }
        emit(Pair(doc, parent))
    }

    @Throws(RemoteException::class)
    private suspend fun queryChildren(dirDocUri: Uri): Cursor {
        val queryUri =
            buildChildDocumentsUri(
                dirDocUri.getAuthority(),
                DocumentsContract.getDocumentId(dirDocUri),
            )
        var cursor = client.query(queryUri, queryColumns, null, null, null)
        while (cursor!!.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING)) {
            coroutineScope {
                val waitForComplete =
                    this.launch {
                        delay(LOADING_TIMEOUT)
                        throw RemoteException("Timed out waiting on update for $queryUri")
                    }
                cursor.registerContentObserver(
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean, uri: Uri?) {
                            waitForComplete.cancel()
                        }
                    }
                )
                waitForComplete.join()
            }

            // Make another query
            cursor = client.query(queryUri, queryColumns, null, null, null)
        }

        return cursor
    }
}
