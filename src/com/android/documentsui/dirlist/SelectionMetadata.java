/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseApprovedDocumentHandlerEnabled;
import static com.android.documentsui.util.FlagUtils.isUseNewOpenWithEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import androidx.recyclerview.selection.SelectionTracker.SelectionObserver;

import com.android.documentsui.MenuManager;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;

import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

/**
 * A class that aggregates document metadata describing the selection. It can answer questions
 * like: Can the selection be deleted? and Does the selection contain a folder?
 *
 * <p>By collecting information in real-time as the selection changes the need to
 * traverse the entire selection in order to answer questions is eliminated.
 */
public class SelectionMetadata extends SelectionObserver<String>
        implements MenuManager.SelectionDetails {

    private static final String TAG = "SelectionMetadata";
    private final static int FLAG_CAN_DELETE =
            Document.FLAG_SUPPORTS_REMOVE | Document.FLAG_SUPPORTS_DELETE;

    private final Function<String, Cursor> mDocFinder;
    private final Function<String, Integer> mCountOpeningApps;
    private final Function<DocumentInfo, Boolean> mIsContentAvailable;

    private int mDirectoryCount = 0;
    private int mFileCount = 0;

    // Partial files are files that haven't been fully downloaded.
    private int mPartialCount = 0;
    private int mWritableDirectoryCount = 0;
    private int mNoDeleteCount = 0;
    private int mNoRenameCount = 0;

    /** Number of files that are located in mounted archives. */
    private int mInArchiveCount = 0;

    /** Number of archives. */
    private int mArchiveCount = 0;

    /** Number of files that do not support trash. */
    private int mUnsupportedTrashCount = 0;

    /** Number of files that do not support restore from trash. */
    private int mUnsupportedRestoreCount = 0;

    private boolean mSupportsSettings = false;
    private int mUnavailableContentCount = 0;

    // For each selected file, remember the number of installed apps that support opening it. We
    // need this information to respond to hasMultipleOpeningApps.
    HashMap<String, Integer> mOpeningAppCountForFile = new HashMap<>();

    // Number of files for each MIME type in the current selection.
    HashMap<String, Integer> mMimeTypeCounts = new HashMap<>();

    /**
     * Keeps track of different properties about the current selection.
     *
     * @param docFinder A function that returns a cursor for the given document ID.
     * @param countOpeningApps A function that returns the number of installed apps that support
     *     opening a file given its document ID.
     */
    public SelectionMetadata(
            Function<String, Cursor> docFinder,
            Function<String, Integer> countOpeningApps,
            Function<DocumentInfo, Boolean> isContentAvailable) {
        mDocFinder = docFinder;
        mCountOpeningApps = countOpeningApps;
        mIsContentAvailable = isContentAvailable;
    }

    @Override
    public void onItemStateChanged(String modelId, boolean selected) {
        final Cursor cursor = mDocFinder.apply(modelId);
        if (cursor == null) {
            Log.w(TAG, "Model returned null cursor for document: " + modelId
                    + ". Ignoring state changed event.");
            return;
        }
        DocumentInfo doc = DocumentInfo.fromDirectoryCursor(cursor);

        final int delta = selected ? 1 : -1;

        if (isUseApprovedDocumentHandlerEnabled()) {
            if (doc.mimeType != null) {
                int count = mMimeTypeCounts.getOrDefault(doc.mimeType, 0);
                count += delta;
                if (count > 0) {
                    mMimeTypeCounts.put(doc.mimeType, count);
                } else {
                    mMimeTypeCounts.remove(doc.mimeType);
                }
            }
        }
        if (doc.isDirectory()) {
            mDirectoryCount += delta;
        } else {
            mFileCount += delta;
            if (ArchivesProvider.isSupportedArchiveType(doc.mimeType)) {
                mArchiveCount += delta;
            }
            if (isDesktopFileHandlingFlagEnabled() && !isUseNewOpenWithEnabled()) {
                if (selected) {
                    mOpeningAppCountForFile.put(
                            modelId,
                            mCountOpeningApps.apply(modelId));
                } else {
                    mOpeningAppCountForFile.remove(modelId);
                }
            }
        }

        if (doc.isPartial()) {
            mPartialCount += delta;
        }
        if (doc.isCreateSupported()) {
            mWritableDirectoryCount += delta;
        }
        if (!doc.isDeleteSupported()) {
            mNoDeleteCount += delta;
        }
        if (isTrashFlowEnabled() && !doc.isTrashSupported()) {
            mUnsupportedTrashCount += delta;
        }
        if (isTrashFlowEnabled() && !doc.isRestoreSupported()) {
            mUnsupportedRestoreCount += delta;
        }
        if (!doc.isRenameSupported()) {
            mNoRenameCount += delta;
        }
        if (doc.isPartial()) {
            mPartialCount += delta;
        }

        mSupportsSettings = doc.isSettingsSupported() && size() == 1;

        if (ArchivesProvider.AUTHORITY.equals(doc.authority)) {
            mInArchiveCount += delta;
        }
        if (!mIsContentAvailable.apply(doc)) {
            mUnavailableContentCount += delta;
        }
    }

    @Override
    public void onSelectionRefresh() {
        mFileCount = 0;
        mDirectoryCount = 0;
        mPartialCount = 0;
        mWritableDirectoryCount = 0;
        mNoDeleteCount = 0;
        mUnsupportedTrashCount = 0;
        mNoRenameCount = 0;
        mInArchiveCount = 0;
        mArchiveCount = 0;
        mMimeTypeCounts.clear();
        mUnsupportedRestoreCount = 0;
        mUnavailableContentCount = 0;
    }

    @Override
    public boolean containsDirectories() {
        return mDirectoryCount > 0;
    }

    @Override
    public boolean containsFiles() {
        return mFileCount > 0;
    }

    @Override
    public int size() {
        return mDirectoryCount + mFileCount;
    }

    @Override
    public boolean containsPartialFiles() {
        return mPartialCount > 0;
    }

    @Override
    public boolean containsFilesInArchive() {
        return mInArchiveCount > 0;
    }

    @Override
    public boolean containsDocumentsWithUnavailableContent() {
        return mUnavailableContentCount > 0;
    }

    @Override
    public boolean isArchive() {
        return mDirectoryCount == 0 && mFileCount == 1 && mArchiveCount == 1;
    }

    @Override
    public boolean hasMultipleOpeningApps() {
        if (isDesktopFileHandlingFlagEnabled() && !isUseNewOpenWithEnabled()) {
            if (mOpeningAppCountForFile.size() == 1) {
                int openingAppCount = mOpeningAppCountForFile.values().iterator().next();
                return openingAppCount > 1;
            }
        }
        // This method is only used when file handling flag is enabled but new open-with flag is
        // disabled.
        return false;
    }

    @Override
    public boolean canDelete() {
        return size() > 0 && mNoDeleteCount == 0;
    }

    @Override
    public boolean canTrash() {
        if (!isTrashFlowEnabled()) {
            return false;
        }
        return size() > 0 && mUnsupportedTrashCount == 0;
    }

    @Override
    public boolean canRestore() {
        if (!isTrashFlowEnabled()) {
            return false;
        }
        return size() > 0 && mUnsupportedRestoreCount == 0;
    }

    @Override
    public boolean canExtract() {
        return size() > 0 && mInArchiveCount == size();
    }

    @Override
    public boolean canRename() {
        return mNoRenameCount == 0 && size() == 1;
    }

    @Override
    public boolean canViewInOwner() {
        return mSupportsSettings;
    }

    @Override
    public boolean canPasteInto() {
        return mDirectoryCount == 1 && mWritableDirectoryCount == 1 && size() == 1;
    }

    @Override
    public boolean canOpen() {
        return mFileCount == 1
                && mDirectoryCount == 0
                && mPartialCount == 0
                && (mArchiveCount == 0 || !isZipNgFlagEnabled())
                && (mInArchiveCount == 0 || isZipNgFlagEnabled())
                && !canRestore();
    }

    @Override
    public Set<String> mimeTypes() {
        return mMimeTypeCounts.keySet();
    }
}
