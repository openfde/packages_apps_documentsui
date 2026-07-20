/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.documentsui.roots

import android.content.ContentResolver.wrap
import android.content.Context
import android.database.Cursor
import android.provider.DocumentsContract
import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.base.DocumentInfo.getCursorString
import com.android.documentsui.base.ShortcutInfo
import com.android.documentsui.base.UserId

class ShortcutsLoader(
    context: Context,
    private val providers: ProvidersCache,
    private val userId: UserId,
) : AsyncTaskLoader<MutableCollection<ShortcutInfo>>(context) {
    private var result: MutableCollection<ShortcutInfo>? = null
    private val TAG: String = "ShortcutsLoader"

    override fun loadInBackground(): MutableCollection<ShortcutInfo> {
        val loadedShortcuts = providers.loadShortcutsForUser(userId)
        for (shortcut in loadedShortcuts) {
            shortcut.documentId = findOrCreateDirectory(shortcut)
        }
        return loadedShortcuts
    }

    private fun findOrCreateDirectory(shortcut: ShortcutInfo): String? {
        val resolver = userId.getContentResolver(context)
        var cursor: Cursor?
        val authority: String = shortcut.root.authority
        val parentDocUri =
            DocumentsContract.buildDocumentUri(authority, shortcut.parentDirDocumentId)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUri(authority, shortcut.parentDirDocumentId)
        try {
            // Do a document query freshness test using information from the parent root.
            val client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority)
            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
            cursor = client.query(childrenUri, projection, null, null, null)
            while (cursor!!.moveToNext()) {
                val title = getCursorString(cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (shortcut.folderTitle.equals(title)) {
                    return getCursorString(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query document", e)
            return null
        }

        // If the folder does not exist, try to create the folder
        try {
            val client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority)
            val folderUri =
                DocumentsContract.createDocument(
                    wrap(client),
                    parentDocUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    shortcut.folderTitle!!,
                )
            if (folderUri != null) {
                Log.i(TAG, "Successfully created folder " + folderUri)
                return DocumentsContract.getDocumentId(folderUri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create folder", e)
        }
        return null
    }

    override fun deliverResult(result: MutableCollection<ShortcutInfo>?) {
        if (isReset()) {
            return
        }

        this.result = result

        if (isStarted()) {
            super.deliverResult(result)
        }
    }

    override fun onStartLoading() {
        if (result != null) {
            deliverResult(result)
        }
        if (takeContentChanged() || result == null) {
            forceLoad()
        }
    }

    override fun onStopLoading() {
        cancelLoad()
    }

    override fun onReset() {
        super.onReset()

        // Ensure the loader is stopped
        onStopLoading()

        result = null
    }
}
