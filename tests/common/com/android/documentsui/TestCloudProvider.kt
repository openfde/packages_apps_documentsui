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

package com.android.documentsui

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/**
 * Test "cloud" provider that sets the FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE root flag and the
 * COLUMN_CONTENT_SYNC_STATE_FLAGS for documents.
 */
internal class TestCloudProvider : TestRootProvider(NAME, ROOT_ID, ROOT_FLAGS, ROOT_ID) {
    companion object {
        const val ROOT_ID = "cloud-root"
        const val AUTHORITY = "com.android.documentsui.cloudprovider"
        const val NAME = "Test Cloud Provider"

        // Do not guard FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE with isSyncStateEnabled() as the
        // FlagUtils lib is not available for some reason and calling this will lead to a
        // NoClassDefFoundError. Accessing this static constant without the check is safe because
        // it will be available during compilation and inserted inline.
        const val ROOT_FLAGS =
            Root.FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE or
                Root.FLAG_SUPPORTS_SEARCH or
                Root.FLAG_SUPPORTS_RECENTS

        const val DOC_ID_0 = "cloudDocId0"
        const val DISPLAY_NAME_0 = "cloudDisplayName0"
        const val DOC_ID_1 = "cloudDocId1"
        const val DISPLAY_NAME_1 = "cloudDisplayName1"
        const val VIRTUAL_ID = "cloudVirtualId"
        const val VIRTUAL_DISPLAY_NAME = "cloudVirtual"
        const val DIR_ID = "cloudDirDocId"
        const val DIR_DISPLAY_NAME = "cloudDirDisplayName"
        const val SET_SYNC_STATE = "setSyncState"
        const val NULLIFY_SYNC_STATE = "nullifySyncState"
        const val CLEAN_UP = "cleanUp"
        const val METHOD_DOC_ID_EXTRA = "documentId"
        const val METHOD_STATE_EXTRA = "syncState"
        private const val TAG = "TestCloudProvider"
        private val NOTIFY_URI = DocumentsContract.buildRootsUri(AUTHORITY)

        // documentId -> Doc
        private val DEFAULT_DOCUMENTS =
            mapOf(
                DOC_ID_0 to Doc(DISPLAY_NAME_0, 0, 0, "text/plain"),
                DOC_ID_1 to Doc(DISPLAY_NAME_1, 0, 0, "text/plain"),
                VIRTUAL_ID to
                    Doc(VIRTUAL_DISPLAY_NAME, 0, Document.FLAG_VIRTUAL_DOCUMENT, "text/plain"),
                DIR_ID to Doc(DIR_DISPLAY_NAME, 0, 0, Document.MIME_TYPE_DIR),
            )
    }

    data class Doc(
        val displayName: String,
        var syncState: Int?,
        var flags: Int,
        val mimeType: String,
    )

    private val documents = mutableMapOf<String, Doc>().apply { putAll(DEFAULT_DOCUMENTS) }

    private fun setSyncState(documentId: String?, syncState: Int?) {
        if (documentId == null) {
            Log.d(TAG, "documentId is null")
            return
        }
        if (!documents.containsKey(documentId)) {
            Log.d(TAG, "$documentId doesn't exist in provider")
            return
        }
        documents[documentId]!!.syncState = syncState
        context?.contentResolver?.notifyChange(NOTIFY_URI, null)
        Log.d(TAG, "Updated sync state of $documentId to $syncState")
    }

    override fun call(authority: String, method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = super.call(method, arg, extras)
        if (result != null) {
            return result
        }
        when (method) {
            SET_SYNC_STATE -> {
                setSyncState(
                    extras!!.getString(METHOD_DOC_ID_EXTRA),
                    extras.getInt(METHOD_STATE_EXTRA, 0),
                )
                return null
            }
            NULLIFY_SYNC_STATE -> {
                setSyncState(extras!!.getString(METHOD_DOC_ID_EXTRA), null)
                return null
            }
            CLEAN_UP -> {
                documents.clear()
                documents.putAll(DEFAULT_DOCUMENTS)
                return null
            }
        }
        return null
    }

    /**
     * Allow test classes to dynamically add files (eg. with random but known filenames) at runtime.
     */
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String? {
        val documentId = "docId${documents.count() + 1}"
        documents[documentId] = Doc(displayName, 0, 0, mimeType)
        // Notify observers that there was a change to the directory list.
        context?.contentResolver?.notifyChange(NOTIFY_URI, null)
        return documentId
    }

    override fun queryRecentDocuments(rootId: String, projection: Array<String>?): Cursor {
        return buildCursorForDocumentList(projection)
    }

    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor? {
        val c = createDocCursor(projection)
        if (documentId == ROOT_ID) {
            // Return a folder for the case when the root is queried. The cases for when a file is
            // queried are not covered. Set FLAG_DIR_SUPPORTS_CREATE so that the Zip context menu
            // action is enabled.
            addFolder(c, documentId, Document.FLAG_DIR_SUPPORTS_CREATE)
        }
        return c
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        // Only return files for the root.
        if (parentDocumentId != ROOT_ID) {
            return createDocCursor(projection)
        }
        val cursor = buildCursorForDocumentList(projection)
        // Set the notificationUri so that the notifyChange calls trigger observers of this cursor.
        cursor.setNotificationUri(context?.contentResolver, NOTIFY_URI)
        return cursor
    }

    private fun buildCursorForDocumentList(
        projection: Array<String>?,
        inclusionCondition: (Doc) -> Boolean = { _ -> true },
    ): Cursor {
        // Do not guard with isSyncStateEnabled() as the FlagUtils lib is not available for some
        // reason and calling this will lead to a NoClassDefFoundError. Accessing this static
        // constant without the check is safe because it will be available during compilation and
        // inserted inline.
        val cursor =
            MatrixCursor(
                projection
                    ?: arrayOf(
                        Document.COLUMN_DOCUMENT_ID,
                        Document.COLUMN_DISPLAY_NAME,
                        Document.COLUMN_CONTENT_SYNC_STATE_FLAGS,
                        Document.COLUMN_FLAGS,
                        Document.COLUMN_MIME_TYPE,
                        Document.COLUMN_LAST_MODIFIED,
                    )
            )

        // This list is also used for queryRecentDocuments(), which requires that a "recent" last
        // modified time is specified: let's use 3 days.
        val lastModified =
            LocalDate.now()
                .minusDays(3)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        for ((documentId, doc) in documents) {
            if (inclusionCondition(doc)) {
                val row = cursor.newRow()
                row.add(Document.COLUMN_DOCUMENT_ID, documentId)
                row.add(Document.COLUMN_DISPLAY_NAME, doc.displayName)
                row.add(Document.COLUMN_CONTENT_SYNC_STATE_FLAGS, doc.syncState)
                row.add(Document.COLUMN_FLAGS, doc.flags)
                row.add(Document.COLUMN_MIME_TYPE, doc.mimeType)
                row.add(Document.COLUMN_LAST_MODIFIED, lastModified)
                Log.d(TAG, "Add sync state for $documentId: ${doc.syncState}")
            }
        }
        return cursor
    }

    /** Simple implementation that returns items that match the query in the top level directory. */
    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<String>?,
    ): Cursor? {
        val cursor =
            buildCursorForDocumentList(projection) { doc -> doc.displayName.contains(query ?: "") }
        // Set the notificationUri so that the notifyChange calls trigger observers of this cursor.
        cursor.setNotificationUri(context?.contentResolver, NOTIFY_URI)
        return cursor
    }
}
