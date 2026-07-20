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
package com.android.documentsui.base

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.android.documentsui.roots.ProvidersAccess
import com.android.documentsui.util.FlagUtils.Companion.isSearchV2Enabled

/**
 * For MediaDocumentsProvider URIs converts them to an external storage URI. For all other URIs this
 * function returns the original, unmodified URI. For SearchV2 code only.
 */
fun tryGetExternalStorageUri(context: Context, uri: Uri): Uri {
    if (!isSearchV2Enabled()) {
        return uri
    }
    if (Providers.AUTHORITY_MEDIA != uri.authority) {
        return uri
    }
    try {
        // Converts Media URI to standard MediaStore URI. If successful, this results in
        // the canonical URI for the item within the MediaStore database itself.
        val mediaUri = MediaStore.getMediaUri(context, uri) ?: return uri
        // Takes the MediaStore URI and converts it back into a document URI. However, this
        // step creates a URI for the ExternalStorageProvider, from which we can extract a
        // true path in the devices internal storage.
        return MediaStore.getDocumentUri(context, mediaUri) ?: uri
    } catch (_: Exception) {
        // This branch catches either IllegalArgumentException or SecurityException, or any
        // type of exception thrown by the underlying database. The first type of exception
        // happens if a malformed URI is passed to this method. The second type, while it should
        // not occur, it is thrown if the app does not have permissions to access the URI.
        return uri
    }
}

/**
 * Encapsulates functionality needed to extract full path of a DocumentInfo. Typical use would be to
 * create this class once, and call the `getDocumentStack` for each document for which we wish to
 * have the path represented by a DocumentStack object.
 */
open class PathExtractor(
    private val context: Context,
    private val providerAccess: ProvidersAccess,
) {
    companion object {
        private const val TAG = "PathExtractor"
    }

    /**
     * Extracts a full stack for the given DocumentInfo. If the stack extraction fails, this method
     * throws NoSuchElementException. It is the responsibility of the caller to correctly handle
     * both success and failure cases.
     *
     * @throws NoSuchElementException if the stack extraction fails.
     */
    fun getDocumentStack(docInfo: DocumentInfo): DocumentStack {
        // MediaDocumentsProvider URIs need special treatment to obtain real path.
        val uri = tryGetExternalStorageUri(context, docInfo.derivedUri)
        val authority = uri.authority ?: docInfo.authority
        val userId = docInfo.userId
        try {
            val path = getDocumentPath(uri)
            if (path === null || path.path === null || path.path.isEmpty()) {
                throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}")
            }
            val rootInfo = providerAccess.getRootOneshot(userId, authority, path.rootId)
            val docInfoArray =
                Array(path.path.size) { getDocumentInfo(authority, userId, path.path[it]) }
            return DocumentStack(rootInfo, *docInfoArray)
        } catch (e: IllegalArgumentException) {
            throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}: $e")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "$uri does not support path extraction: $e")
            return approximateDocumentStack(getRootInfo(authority, userId), docInfo)
        }
    }

    /** Fallback method if we cannot get the path for the given DocumentInfo. */
    private fun approximateDocumentStack(rootInfo: RootInfo, docInfo: DocumentInfo): DocumentStack {
        // We create a root dir as the rest of the code is not prepared to have a document stack
        // that consists of the root and file, with no directory represented by DocumentInfo.
        val rootDir =
            DocumentInfo().apply {
                userId = rootInfo.userId
                authority = rootInfo.authority
                displayName = rootInfo.title
                documentId = rootInfo.documentId
                deriveFields()
            }
        if (docInfo.authority == Providers.AUTHORITY_MEDIA) {
            return DocumentStack(providerAccess.getRecentsRoot(docInfo.userId), rootDir, docInfo)
        }
        return DocumentStack(rootInfo, rootDir, docInfo)
    }

    /** Attempts to get the root of a document provider for the given authority and userId. */
    private fun getRootInfo(authority: String, userId: UserId): RootInfo {
        var cursor: Cursor? = null
        val uri = DocumentsContract.buildRootsUri(authority)
        try {
            cursor = getCursorForUri(uri)
            if (cursor != null && cursor.moveToFirst()) {
                return RootInfo.fromRootsCursor(userId, authority, cursor)
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Can't find matching root for uri=$uri")
    }

    /**
     * Extracts the document info for the document identified by `pathItemId` and `authority` that
     * manages documents. This is done for the user with the given `userId`.
     */
    private fun getDocumentInfo(
        authority: String,
        userId: UserId,
        pathItemId: String,
    ): DocumentInfo {
        var cursor: Cursor? = null
        val itemUri = DocumentsContract.buildDocumentUri(authority, pathItemId)
        try {
            cursor = getCursorForUri(itemUri)
            if (cursor != null && cursor.moveToFirst()) {
                return DocumentInfo.fromCursor(cursor, userId, authority)
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Failed to obtain cursor for $itemUri")
    }

    /** Fetches DocumentsContract.Path for the given `documentUri` */
    protected open fun getDocumentPath(documentUri: Uri) =
        DocumentsContract.findDocumentPath(context.contentResolver, documentUri)

    /** Fetches a cursor for the given Uri. */
    protected open fun getCursorForUri(uri: Uri) =
        context.contentResolver.query(uri, null, null, null, null)
}
