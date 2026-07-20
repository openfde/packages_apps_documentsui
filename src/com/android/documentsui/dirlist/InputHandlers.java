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

import static androidx.core.util.Preconditions.checkArgument;

import static com.android.documentsui.ActionHandler.VIEW_TYPE_NONE;
import static com.android.documentsui.ActionHandler.VIEW_TYPE_PREVIEW;
import static com.android.documentsui.ActionHandler.VIEW_TYPE_REGULAR;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseNewOpenWithEnabled;

import android.view.KeyEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.documentsui.ActionHandler;

/**
 * Helper class dedicated to building gesture input handlers. The construction
 * of the various input handlers is non trivial. To keep logic clear,
 * code flexible, and DirectoryFragment small(er), the construction has been
 * isolated here in a separate class.
 */
final class InputHandlers {

    private ActionHandler mActions;
    private SelectionTracker<String> mSelectionHelper;
    private SelectionPredicate<String> mSelectionPredicate;
    private FocusHandler mFocusHandler;
    private RecyclerView mRecView;

    InputHandlers(
            ActionHandler actions,
            SelectionTracker<String> selectionHelper,
            SelectionPredicate<String> selectionPredicate,
            FocusHandler focusHandler,
            RecyclerView recView) {

        checkArgument(actions != null);
        checkArgument(selectionHelper != null);
        checkArgument(selectionPredicate != null);
        checkArgument(focusHandler != null);
        checkArgument(recView != null);

        mActions = actions;
        mSelectionHelper = selectionHelper;
        mSelectionPredicate = selectionPredicate;
        mFocusHandler = focusHandler;
        mRecView = recView;
    }

    KeyInputHandler createKeyHandler() {
        KeyInputHandler.Callbacks<ItemDetails<String>> callbacks =
                new KeyInputHandler.Callbacks<ItemDetails<String>>() {
                    @Override
                    public boolean onItemActivated(ItemDetails<String> item, KeyEvent e) {
                        if (isUseMaterial3FlagEnabled() && !e.hasNoModifiers()) {
                            return false;
                        }

                        switch (e.getKeyCode()) {
                            case KeyEvent.KEYCODE_ENTER:
                            case KeyEvent.KEYCODE_DPAD_CENTER:
                            case KeyEvent.KEYCODE_BUTTON_A:
                                // This was reverted as desktop file handling was rolling out until
                                // we have default file opening apps out-of-the box.
                                // Since the default file opening app uses a build flag, we're using
                                // another flag that's rolling out in the same cycle to flag protect
                                // the revert^2.
                                if (isDesktopFileHandlingFlagEnabled()
                                        && isUseNewOpenWithEnabled()) {
                                    return mActions.openItem(
                                            item, VIEW_TYPE_REGULAR, VIEW_TYPE_NONE);
                                }
                                return mActions.openItem(
                                        item, VIEW_TYPE_REGULAR, VIEW_TYPE_PREVIEW);
                            case KeyEvent.KEYCODE_SPACE:
                                return mActions.openItem(item, VIEW_TYPE_PREVIEW, VIEW_TYPE_NONE);
                        }

                        return false;
                    }

                    @Override
                    public boolean onFocusItem(
                            ItemDetails<String> details, int keyCode, KeyEvent event) {
                        ViewHolder holder =
                                mRecView.findViewHolderForAdapterPosition(details.getPosition());
                        if (holder instanceof DocumentHolder) {
                            return mFocusHandler.handleKey((DocumentHolder) holder, keyCode, event);
                        }
                        return false;
                    }
                };

        return new KeyInputHandler(mSelectionHelper, mSelectionPredicate, mFocusHandler, callbacks);
    }
}
