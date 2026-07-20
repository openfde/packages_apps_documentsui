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

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.view.KeyEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.base.Events;

import javax.annotation.Nullable;

// TODO(b/69058726): Migrate to RecyclerView-Selection
/**
 * Class that handles keyboard events on RecyclerView items. The input handler must be attached
 * directly to a RecyclerView item since, unlike DOM, events don't appear bubble up.
 */
public final class KeyInputHandler extends KeyboardEventListener<ItemDetails<String>> {

    private final SelectionTracker<String> mSelectionHelper;
    private final SelectionPredicate<String> mSelectionPredicate;
    private final FocusHandler mFocusHandler;
    private final Callbacks<ItemDetails<String>> mCallbacks;

    public KeyInputHandler(
            SelectionTracker<String> selectionHelper,
            SelectionPredicate<String> selectionPredicate,
            FocusHandler focusHandler,
            Callbacks<ItemDetails<String>> callbacks) {

        mSelectionHelper = selectionHelper;
        mSelectionPredicate = selectionPredicate;
        mFocusHandler = focusHandler;
        mCallbacks = callbacks;
    }

    @Override
    public boolean onKey(@Nullable ItemDetails<String> details, int keyCode, KeyEvent event) {
        // Only handle key-down events. This is simpler, consistent with most other UIs, and
        // enables the handling of repeated key events from holding down a key.
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        // Ignore tab key events.  Those should be handled by the top-level key handler.
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            return false;
        }

        // Ignore events sent to Addon Holders.
        if (details instanceof DocumentItemDetails) {
            int itemType = ((DocumentItemDetails) details).getItemViewType();
            if (itemType == DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE
                    || itemType == DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE
                    || itemType == DocumentsAdapter.ITEM_TYPE_SECTION_BREAK) {
                return false;
            }
        }

        if (mCallbacks.onFocusItem(details, keyCode, event)) {
            // Handle range selection adjustments. Extending the selection will adjust the
            // bounds of the in-progress range selection. Each time an unshifted navigation
            // event is received, the range selection is restarted.
            if (shouldExtendSelection(details, event)) {
                if (!mSelectionHelper.isRangeActive()) {
                    // Start a range selection if one isn't active
                    mSelectionHelper.startRange(details.getPosition());
                }
                int focusedPosition = mFocusHandler.getFocusedPosition();
                if (focusedPosition != RecyclerView.NO_POSITION) {
                    mSelectionHelper.extendRange(focusedPosition);
                }
            } else {
                mSelectionHelper.endRange();
                if (!isUseMaterial3FlagEnabled()) {
                    mSelectionHelper.clearSelection();
                }
            }
            return true;
        }

        // we don't yet have a mechanism to handle opening/previewing multiple documents at once
        //
        // For UseMaterial3, the intention is to activate what's focused, not what's selected. At
        // most one document can be focused - that's what in the "details" argument - and so we can
        // skip this check.
        //
        // For !UseMaterial3, the mCallbacks.onItemActivated(details, etc) call still activates
        // what's focused, not what's selected. That may or may not be a bug, but we leave the
        // !UseMaterial3 behavior unchanged for now, returning early if the selection size is > 1.
        if (!isUseMaterial3FlagEnabled() && (mSelectionHelper.getSelection().size() > 1)) {
            return false;
        }

        return mCallbacks.onItemActivated(details, event);
    }

    private boolean shouldExtendSelection(ItemDetails<String> item, KeyEvent event) {
        if (!Events.isNavigationKeyCode(event.getKeyCode()) || !event.isShiftPressed()) {
            return false;
        }

        return mSelectionPredicate.canSetStateForKey(item.getSelectionKey(), true);
    }

    public static abstract class Callbacks<T extends ItemDetails<?>> {
        public abstract boolean onItemActivated(T item, KeyEvent e);
        public abstract boolean onFocusItem(T details, int keyCode, KeyEvent event);
    }
}
