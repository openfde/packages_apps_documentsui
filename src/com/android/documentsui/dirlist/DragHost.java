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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isDragsFromOtherAppsEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.selection.SelectionTracker;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.AbstractDragHost;
import com.android.documentsui.ActionHandler;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.State;
import com.android.documentsui.ui.DialogController;

import com.google.android.material.snackbar.BaseTransientBottomBar.Duration;
import com.google.android.material.snackbar.Snackbar;

import java.util.function.Predicate;

/**
 * Drag host for items in {@link DirectoryFragment}.
 */
class DragHost<T extends Activity & AbstractActionHandler.CommonAddons> extends AbstractDragHost {

    @VisibleForTesting
    interface SnackbarFactory {
        Snackbar make(View view, @StringRes int resId, @Duration int duration);
    }

    private static final String TAG = "dirlist.DragHost";

    private final T mActivity;
    private final SelectionTracker<String> mSelectionMgr;
    private final ActionHandler mActions;
    private final State mState;
    private final DialogController mDialogs;
    private final DocumentsAccess mDocs;
    private final Predicate<View> mIsDocumentView;
    private final Lookup<View, DocumentHolder> mHolderLookup;
    private final Lookup<View, DocumentInfo> mDestinationLookup;
    private SnackbarFactory mSnackbarFactory;
    private Drawable mRegularDirListBackground;

    DragHost(
            T activity,
            DragAndDropManager dragAndDropManager,
            SelectionTracker<String> selectionMgr,
            ActionHandler actions,
            State state,
            DialogController dialogs,
            DocumentsAccess docs,
            Predicate<View> isDocumentView,
            Lookup<View, DocumentHolder> holderLookup,
            Lookup<View, DocumentInfo> destinationLookup) {
        super(dragAndDropManager);

        mActivity = activity;
        mSelectionMgr = selectionMgr;
        mActions = actions;
        mState = state;
        mDialogs = dialogs;
        mDocs = docs;
        mIsDocumentView = isDocumentView;
        mHolderLookup = holderLookup;
        mDestinationLookup = destinationLookup;
        mSnackbarFactory = Snackbar::make;
    }

    void dragStopped(boolean result) {
        if (result) {
            if (!isUseMaterial3FlagEnabled()) {
                mSelectionMgr.clearSelection();
            }
        }
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    @Override
    public void setDropTargetHighlight(View v, boolean highlight) {
        if (v.getId() == getRes(R.id.dir_list)) {
            if (highlight) {
                if (mRegularDirListBackground == null) {
                    mRegularDirListBackground = v.getBackground();
                }
                // Highlight the border of the directory list container.
                v.setBackgroundResource(getRes(R.drawable.dir_list_drag_hover_background));
            } else {
                // Set it back to the regular, non-highlighted directory list container.
                v.setBackground(mRegularDirListBackground);
                mRegularDirListBackground = null;
            }
        }
    }

    @Override
    public void onViewHovered(View v) {
        if (mIsDocumentView.test(v)) {
            mActions.springOpenDirectory(mDestinationLookup.lookup(v));
        }
        mActivity.setRootsDrawerOpen(false);
    }

    @Override
    public void onDragEntered(View v) {
        mActivity.setRootsDrawerOpen(false);
        mDragAndDropManager.updateState(v, mState.stack.getRoot(), mDestinationLookup.lookup(v));
    }

    @Override
    public boolean canHandleDragEvent(View view) {
        boolean dragInitiatedFromDocsUI = mDragAndDropManager.isDragFromSameApp();
        Metrics.logDragInitiated(dragInitiatedFromDocsUI);
        if (!isDragsFromOtherAppsEnabled() && !dragInitiatedFromDocsUI) {
            mSnackbarFactory
                    .make(view, getRes(R.string.drag_from_another_app), Snackbar.LENGTH_LONG)
                    .show();
            return false;
        }
        return true;
    }

    boolean canSpringOpen(View v) {
        DocumentInfo doc = mDestinationLookup.lookup(v);
        return (doc != null) && mDragAndDropManager.canSpringOpen(mState.stack.getRoot(), doc);
    }

    boolean handleDropEvent(View v, DragEvent event) {
        mActivity.setRootsDrawerOpen(false);

        ClipData clipData = event.getClipData();
        assert (clipData != null);

        DocumentInfo dst = mDestinationLookup.lookup(v);
        if (dst == null) {
            if (DEBUG) {
                Log.d(TAG, "Invalid destination. Ignoring.");
            }
            return false;
        }

        // If destination is already at top of stack, no need to pass it in
        DocumentStack dstStack = dst.equals(mState.stack.peek())
                ? mState.stack
                : new DocumentStack(mState.stack, dst);

        return mDragAndDropManager.drop(
                DragAndDropManager.requestPermissions(mActivity, event),
                event.getClipData(),
                event.getLocalState(),
                dstStack,
                mActions,
                mDocs,
                mDialogs::showFileOperationStatus);
    }

    @VisibleForTesting
    void setSnackbarFactoryForTesting(SnackbarFactory snackbarFactory) {
        mSnackbarFactory = snackbarFactory;
    }
}
