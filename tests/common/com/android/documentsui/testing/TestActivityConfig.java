/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.testing;

import androidx.annotation.Nullable;

import com.android.documentsui.ActivityConfig;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.State;

import java.util.HashSet;
import java.util.Set;

public class TestActivityConfig extends ActivityConfig {

    public boolean nextSelectType = false;
    public boolean nextDocumentEnabled = false;
    public boolean nextManagedModeEnabled = false;
    public boolean nextDragAndDropEnabled = false;
    public Set<String> documentsWithUnavailableContent = new HashSet<>();

    /** Whether the document can be selected in this context. */
    public boolean canSelectType(
            String docMimeType,
            int docFlags,
            @Nullable Integer syncStateFlags,
            State state,
            boolean isOnline) {
        return nextSelectType;
    }

    /** Whether the document is enabled in this context. */
    public boolean isDocumentEnabled(
            String docMimeType,
            int docFlags,
            @Nullable Integer syncStateFlags,
            State state,
            boolean isOnline) {
        return nextDocumentEnabled;
    }

    @Override
    /* Whether the document has content available. */
    public boolean isContentAvailable(DocumentInfo doc, State state, boolean isOnline) {
        return !documentsWithUnavailableContent.contains(doc.documentId);
    }

    /**
     * When managed mode is enabled, active downloads will be visible in the UI.
     * Presumably this should only be true when in the downloads directory.
     */
    public boolean managedModeEnabled(DocumentStack stack) {
        return nextManagedModeEnabled;
    }

    /**
     * Whether drag n' drop is allowed in this context
     */
    public boolean dragAndDropEnabled() {
        return nextDragAndDropEnabled;
    }
}
