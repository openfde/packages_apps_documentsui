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

import android.content.ContentProviderClient
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.RemoteException
import android.provider.DocumentsContract
import android.util.Log
import androidx.tracing.Trace
import com.android.documentsui.ContentLock
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.archives.ArchivesProvider
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.SharedMinimal.redact
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.util.FlagUtils.Companion.isSyncStateEnabled

/**
 * A specialization of the BaseFileLoader that loads the children of a single folder. To list a
 * directory you need to provide:
 * - The current application context
 * - A content lock for which a locking content observer is built
 * - A list of user IDs on behalf of which the search is conducted
 * - The root info of the listed directory
 * - The document info of the listed directory, may be null.
 * - a lookup from file extension to file type
 * - The model capable of sorting results
 *
 * Typically, here we expect mListedDir to be not null, as this is the directory we are listing.
 * However, when profile is switched while using the app as a file picker, it is possible that the
 * listing directory is null. If this is the case, we assume that we should be listing the location
 * specified by the mRoot.
 */
class FolderLoader(
    context: Context,
    mimeTypeLookup: Lookup<String, String>,
    contentLock: ContentLock,
    private val root: RootInfo,
    private val listedDir: DocumentInfo?,
    private val options: QueryOptions,
    private val sortModel: SortModel,
) : BaseFileLoader(context, mimeTypeLookup) {

    // An observer registered on the cursor to force a reload if the cursor reports a change.
    private val observer = LockingContentObserver(contentLock, this::onContentChanged)

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        try {
            Trace.beginSection("documentsui.searchv2.FolderLoader#loadInBackground")
            return loadInBackgroundInternal()
        } finally {
            Trace.endSection()
        }
    }

    fun loadInBackgroundInternal(): DirectoryResult? {
        synchronized(this) {
            if (isLoadInBackgroundCanceled) {
                throw OperationCanceledException()
            }
            cancelNotifier = CancellationSignal()
        }
        val rejectBeforeTimestamp = options.getRejectBeforeTimestamp()
        val folderChildrenUri =
            if (listedDir == null) {
                DocumentsContract.buildChildDocumentsUri(root.authority, root.documentId)
            } else {
                DocumentsContract.buildChildDocumentsUri(listedDir.authority, listedDir.documentId)
            }
        val result = DirectoryResult()
        result.queryOptions = options
        // If we are listing an archive, in the current approach, we cache the client as part of
        // DirectoryResult. This way, when the loader is closed, we can close the archive client.
        if (listedDir != null && listedDir.isInArchive) {
            result.setClient(openArchive(folderChildrenUri))
        }
        var cursor: Cursor? = null
        try {
            cursor = queryLocation(root, folderChildrenUri, options.otherQueryArgs)
        } catch (e: Exception) {
            result.exception = e
        } finally {
            synchronized(this) { cancelNotifier = null }
        }
        if (cursor == null) {
            cursor = emptyCursor()
            result.setClient(null)
        }
        cursor.registerContentObserver(observer)

        val filteredCursor = FilteringCursorWrapper(cursor)
        filteredCursor.filterHiddenFiles(options.showHidden)
        filteredCursor.filterMimes(computeAcceptableMimeTypes(options), null)
        if (rejectBeforeTimestamp > 0L) {
            filteredCursor.filterLastModified(rejectBeforeTimestamp)
        }
        val sortedCursor = sortModel.sortCursor(filteredCursor, mimeTypeLookup)

        result.doc = listedDir ?: DocumentInfo()
        result.cursor = sortedCursor
        if (isSyncStateEnabled()) {
            // Set if this is a root that has limited functionality when offline.
            result.hasLimitedFunctionalityWhenOffline = root.hasLimitedFunctionalityWhenOffline()
        }
        return result
    }

    /**
     * Helper function that attempts to open an archive and return a long-lasting content provider
     * client to the soon-to-be scanned archive. This must be done before attempting to acquire the
     * cursor, as we depend on archive content to be read (see acquireArchive method).
     */
    private fun openArchive(folderChildrenUri: Uri): ContentProviderClient? {
        // If we are opening an archive, we need, in the current approach, to have a long-lived
        // ContentProviderClient for it. This is so that the archive can be closed, once the
        // loader results are closed.
        var client: ContentProviderClient? = null
        try {
            val resolver = root.userId.getContentResolver(context)
            client = resolver.acquireUnstableContentProviderClient(folderChildrenUri.authority!!)
            ArchivesProvider.acquireArchive(client, folderChildrenUri)
            debugLog("Acquired archive ${redact(folderChildrenUri)}")
        } catch (e: RemoteException) {
            Log.e(TAG, "Cannot acquire archive ${redact(folderChildrenUri)}", e)
            client?.close()
        }
        return client
    }
}
