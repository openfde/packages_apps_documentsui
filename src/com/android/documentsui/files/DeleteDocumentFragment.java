/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.documentsui.files;

import static com.android.documentsui.base.SharedMinimal.TAG;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsUIDialogFragment;
import com.android.documentsui.Injector;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/** Dialog to delete file or directory. */
public class DeleteDocumentFragment extends DocumentsUIDialogFragment {
    private static final String TAG_DELETE_DOCUMENT = "delete_document";

    private static final String EXTRA_IN_TRASH = "in_trash";

    private List<DocumentInfo> mDocuments;
    private DocumentInfo mSrcParent;
    private boolean mInTrash;

    /**
     * Show the dialog UI.
     *
     * @param fm the fragment manager
     * @param docs the selected documents
     * @param srcParent the parent document of the selection
     */
    public static void show(
            FragmentManager fm,
            List<DocumentInfo> docs,
            @Nullable DocumentInfo srcParent,
            boolean inTrash) {
        if (fm.isStateSaved()) {
            Log.w(TAG, "Skip show delete dialog because state saved");
            return;
        }

        final DeleteDocumentFragment dialog = new DeleteDocumentFragment();
        dialog.mDocuments = docs;
        dialog.mSrcParent = srcParent;
        dialog.mInTrash = inTrash;
        dialog.show(fm, TAG_DELETE_DOCUMENT);
    }

    /**
     * Creates the dialog UI.
     *
     * @param savedInstanceState
     * @return
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mSrcParent = savedInstanceState.getParcelable(Shared.EXTRA_DOC);
            mDocuments = savedInstanceState.getParcelableArrayList(Shared.EXTRA_SELECTION);
            mInTrash = savedInstanceState.getBoolean(EXTRA_IN_TRASH);
        }

        Context context = getActivity();
        Injector<?> injector = ((BaseActivity) getActivity()).getInjector();

        String title = getString(R.string.delete_forever_dialog_title);
        String message = injector.messages.generateDeleteMessage(mDocuments, mInTrash);

        final AlertDialog alertDialog =
                new MaterialAlertDialogBuilder(context)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(
                                android.R.string.ok,
                                (dialog, id) ->
                                        injector.actions.deleteSelectedDocuments(
                                                mDocuments, mSrcParent))
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();

        alertDialog.setOnShowListener(
                (dialogInterface) -> {
                    Button positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positive.setFocusable(true);
                    positive.requestFocus();
                });
        return alertDialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(Shared.EXTRA_DOC, mSrcParent);
        outState.putParcelableArrayList(Shared.EXTRA_SELECTION, (ArrayList) mDocuments);
        outState.putBoolean(EXTRA_IN_TRASH, mInTrash);
    }
}
