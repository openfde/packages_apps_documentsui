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

package com.android.documentsui;

import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.State;

/**
 * Provides support for specializing the DirectoryFragment to the "host" Activity.
 * Feel free to expand the role of this class to handle other specializations.
 */
public abstract class ActivityConfig {

    /**
     * Subtly different from isDocumentEnabled. The reason may be illuminated as follows. A folder
     * is enabled such that it may be double clicked, even in settings when the folder itself cannot
     * be selected. This may also be true of container types.
     */
    public boolean canSelectType(DocumentInfo doc, State state, boolean isOnline) {
        return true;
    }

    /** Returns whether a document is enabled. */
    public boolean isDocumentEnabled(DocumentInfo doc, State state, boolean isOnline) {
        if (!isSyncStateEnabled()) {
            return true;
        }
        if (doc.isDirectory()) {
            // Directories are always enabled.
            return true;
        }
        return isContentAvailable(doc, state, isOnline);
    }

    /**
     * Returns whether the document has content available, either locally or that can be downloaded.
     * Content is assumed to always be available if the system is online or the document is not on a
     * root that has limited functionality when offline.
     *
     * <p>However, is this is not the case, then files are only available if their content is
     * available locally and directories are assumed unavailable because they may contain files that
     * don't have available content.
     */
    public boolean isContentAvailable(DocumentInfo doc, State state, boolean isOnline) {
        if (!isSyncStateEnabled()) {
            return true;
        }

        if (!doc.rootHasLimitedFunctionalityWhenOffline) {
            // Documents on roots that are not affected by the online status are always available.
            return true;
        }

        if (isOnline) {
            // The content should be downloadable and thus available.
            return true;
        }

        if (doc.isDirectory()) {
            // Directories may contain files that don't have available content.
            return false;
        }

        return doc.isContentAvailableLocally();
    }

    /**
     * When managed mode is enabled, there will be special UI behaviors: 1) active downloads will be
     * visible in the UI. 2) Android/[data|obb|sandbox] directories will not be hidden.
     */
    public boolean managedModeEnabled(DocumentStack stack) {
        return false;
    }

    /**
     * Whether drag n' drop is allowed in this context
     */
    public boolean dragAndDropEnabled() {
        return false;
    }
}
