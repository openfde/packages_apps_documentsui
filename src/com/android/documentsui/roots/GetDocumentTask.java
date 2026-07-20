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

package com.android.documentsui.roots;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.TimeoutTask;
import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.UserId;

import java.util.function.Consumer;

/**
 * A {@link CheckedTask} that takes in an authority, document id and a user id then
 * queries SAF to obtain the {@link DocumentInfo} and call supplied callback to handle the
 * {@link DocumentInfo}.
 */
public class GetDocumentTask extends TimeoutTask<Void, DocumentInfo> {

    private static final String TAG = "GetDocumentTask";

    private final String mAuthority;
    private final String mDocumentId;
    private final UserId mUserId;
    private final Consumer<DocumentInfo> mCallback;
    private final DocumentsAccess mDocs;

    public GetDocumentTask(
            String authority,
            String documentId,
            UserId userId,
            Activity activity,
            long timeout,
            DocumentsAccess docs,
            Consumer<DocumentInfo> callback) {
        super(activity::isDestroyed, timeout);
        mAuthority = authority;
        mDocumentId = documentId;
        mUserId = userId;
        mDocs = docs;
        mCallback = callback;
    }

    @Override
    public @Nullable DocumentInfo run(Void... args) {
        return mDocs.getDocument(mAuthority, mDocumentId, mUserId);
    }

    @Override
    public void finish(@Nullable DocumentInfo documentInfo) {
        if (documentInfo == null) {
            Log.e(TAG,
                    "Cannot find document info for authority: " + mAuthority
                    + ", documentId: " + mDocumentId + ", userId: " + mUserId);
        }

        mCallback.accept(documentInfo);
    }
}
