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

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;

import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.Model;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.State;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Listens for potential "drag-like" events and kick-start dragging as needed. Also allows external
 * direct call to {@code #startDrag(RecyclerView, View)} if explicit start is needed, such as long-
 * pressing on an item via touch. (e.g. InputEventDispatcher#onLongPress(MotionEvent)} via touch.
 */
interface DragStartListener {

    DragStartListener STUB = new DragStartListener() {
        @Override
        public boolean onDragEvent(MotionEvent event) {
            return false;
        }
    };

    boolean onDragEvent(MotionEvent event);

    @VisibleForTesting
    class RuntimeDragStartListener implements DragStartListener {

        private static String TAG = "DragStartListener";

        private final IconHelper mIconHelper;
        private final State mState;
        private final SelectionTracker<String> mSelectionMgr;
        private final SelectionDetails mSelectionDetails;
        private final ViewFinder mViewFinder;
        private final Function<View, String> mIdFinder;
        private final Function<Selection<String>, List<DocumentInfo>> mDocsConverter;
        private final Function<DocumentInfo, Boolean> mIsContentAvailable;
        private final DragAndDropManager mDragAndDropManager;
        private final DocumentsAccess mDocsAccess;

        // use DragStartListener.create
        @VisibleForTesting
        public RuntimeDragStartListener(
                IconHelper iconHelper,
                State state,
                SelectionTracker<String> selectionMgr,
                SelectionDetails selectionDetails,
                ViewFinder viewFinder,
                Function<View, String> idFinder,
                Function<Selection<String>, List<DocumentInfo>> docsConverter,
                Function<DocumentInfo, Boolean> isContentAvailable,
                DragAndDropManager dragAndDropManager,
                DocumentsAccess docsAccess) {

            mIconHelper = iconHelper;
            mState = state;
            mSelectionMgr = selectionMgr;
            mSelectionDetails = selectionDetails;
            mViewFinder = viewFinder;
            mIdFinder = idFinder;
            mDocsConverter = docsConverter;
            mIsContentAvailable = isContentAvailable;
            mDragAndDropManager = dragAndDropManager;
            mDocsAccess = docsAccess;
        }

        @Override
        public final boolean onDragEvent(MotionEvent event) {
            return startDrag(mViewFinder.findView(event.getX(), event.getY()), event);
        }

        /**
         * May be called externally when drag is initiated from other event handling code.
         */
        private boolean startDrag(@Nullable View view, MotionEvent event) {

            if (view == null) {
                if (DEBUG) {
                    Log.d(TAG, "Ignoring drag event, null view.");
                }
                return false;
            }

            @Nullable String modelId = mIdFinder.apply(view);
            if (modelId == null) {
                if (DEBUG) {
                    Log.d(TAG, "Ignoring drag on view not represented in model.");
                }
                return false;
            }

            Selection<String> selection = getSelectionToBeCopied(modelId, event);

            final List<DocumentInfo> srcs = mDocsConverter.apply(selection);

            boolean canDragAndDrop = true;
            final List<Uri> invalidDest = new ArrayList<>(srcs.size() + 1);
            for (DocumentInfo doc : srcs) {
                invalidDest.add(doc.derivedUri);
                // Drag and drop should be disabled if content is not available.
                if (isSyncStateEnabled() && canDragAndDrop && !mIsContentAvailable.apply(doc)) {
                    if (DEBUG) {
                        Log.d(TAG, "Content not available for: " + doc.displayName);
                    }
                    canDragAndDrop = false;
                }
            }

            final DocumentInfo parent = mState.stack.peek();
            // parent is null when we're in Recents
            if (parent != null) {
                invalidDest.add(parent.derivedUri);
            }

            SidebarEntryItemInfo itemInfo;
            if (isHomeScreenFilesFlagEnabled() && mState.shortcut != null) {
                itemInfo = mState.shortcut;
            } else {
                itemInfo = mState.stack.getRoot();
            }

            mDragAndDropManager.startDrag(
                    view,
                    srcs,
                    itemInfo,
                    invalidDest,
                    mSelectionDetails,
                    mIconHelper,
                    parent,
                    canDragAndDrop,
                    mDocsAccess);

            return true;
        }

        /**
         * Given the MotionEvent (for CTRL case) and modelId of the view associated with the
         * coordinates of the event, return a valid selection for drag and drop operation
         */
        @VisibleForTesting
        MutableSelection<String> getSelectionToBeCopied(String modelId, MotionEvent event) {
            MutableSelection<String> selection = new MutableSelection<>();
            // If CTRL-key is held down and there's other existing selection, add item to
            // selection (if not already selected)
            if (Events.isCtrlKeyPressed(event)
                    && mSelectionMgr.hasSelection()
                    && !mSelectionMgr.isSelected(modelId)) {
                mSelectionMgr.select(modelId);
            }

            if (mSelectionMgr.isSelected(modelId)) {
                mSelectionMgr.copySelection(selection);
            } else {
                selection.add(modelId);
                // If the drag starts with an unselected item, clear the existing selection and
                // select the unselected item so the drag only happens with this item.
                mSelectionMgr.clearSelection();
                // We need to update the selection manager because it affects the file operation
                // type, e.g. move or copy (Check `mMustBeCopied` in DragAndDropManager).
                if (isUseMaterial3FlagEnabled()) {
                    mSelectionMgr.select(modelId);
                }
            }
            return selection;
        }
    }

    static DragStartListener create(
            IconHelper iconHelper,
            Model model,
            SelectionTracker<String> selectionMgr,
            SelectionDetails selectionDetails,
            State state,
            Function<View, String> idFinder,
            ViewFinder viewFinder,
            Function<DocumentInfo, Boolean> isContentAvailable,
            DragAndDropManager dragAndDropManager,
            DocumentsAccess docsAccess) {

        return new RuntimeDragStartListener(
                iconHelper,
                state,
                selectionMgr,
                selectionDetails,
                viewFinder,
                idFinder,
                model::getDocuments,
                isContentAvailable,
                dragAndDropManager,
                docsAccess);
    }

    @FunctionalInterface
    interface ViewFinder {
        @Nullable View findView(float x, float y);
    }
}
