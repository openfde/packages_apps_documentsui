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

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

import androidx.recyclerview.selection.SelectionTracker;

import com.android.documentsui.base.Events;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Procedure;
import com.android.documentsui.dirlist.FocusHandler;

/**
 * Handle common input events.
 */
public class SharedInputHandler {

    private static final String TAG = "SharedInputHandler";

    private final Activity mActivity;
    private final FocusHandler mFocusManager;
    private final Procedure mSearchCanceler;
    private final Procedure mDirPopper;
    private final Runnable mSearchKeyboardShortcutExecutor;
    private final Features mFeatures;
    private final SelectionTracker<String> mSelectionMgr;
    private final DrawerController mDrawer;

    public SharedInputHandler(
            Activity activity,
            FocusHandler focusHandler,
            SelectionTracker<String> selectionMgr,
            Procedure searchCanceler,
            Procedure dirPopper,
            Features features,
            DrawerController drawer,
            Runnable searchKeyboardShortcutExecutor) {
        mActivity = activity;
        mFocusManager = focusHandler;
        mSearchCanceler = searchCanceler;
        mSelectionMgr = selectionMgr;
        mDirPopper = dirPopper;
        mFeatures = features;
        mDrawer = drawer;
        mSearchKeyboardShortcutExecutor = searchKeyboardShortcutExecutor;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // Unhandled ESC keys end up being rethrown back at us as BACK keys. So by returning
            // true, we make sure it always does no-op.
            case KeyEvent.KEYCODE_ESCAPE:
                return onEscape();

            case KeyEvent.KEYCODE_DEL:
                return onDelete();

            // This is the Android back button, not backspace.
            case KeyEvent.KEYCODE_BACK:
                // isCanceled=true indicates the back press event is already consumed by the system,
                // e.g. close the soft keyboard, our app level shouldn't do anything.
                if (event.isCanceled()) {
                    return true;
                }
                return onBack();

            case KeyEvent.KEYCODE_TAB:
                return onTab();

            case KeyEvent.KEYCODE_SEARCH:
                mSearchKeyboardShortcutExecutor.run();
                return true;

            default:
                // Instead of duplicating the switch-case in #isNavigationKeyCode, best just to
                // leave it here.
                if (Events.isNavigationKeyCode(keyCode)) {
                    // Forward all unclaimed navigation keystrokes to the directory list.
                    // This causes any stray navigation keystrokes to focus the content pane,
                    // which is probably what the user is trying to do.
                    mFocusManager.focusDirectoryList();
                    return true;
                }
                return false;
        }
    }

    private boolean onTab() {
        if (!mFeatures.isSystemKeyboardNavigationEnabled()) {
            // Tab toggles focus on the navigation drawer.
            // This should only be called in pre-O devices, since O has built-in keyboard
            // navigation
            // support.
            mFocusManager.advanceFocusArea();
            return true;
        }

        return false;
    }

    private boolean onDelete() {
        // An EditText (as used by the PickActivity's SaveFragment) will capture (its KeyEvent
        // handler returns true) KeyEvent.KEYCODE_DEL events (hitting the backspace key) if the
        // backspace deletes a character. But it will not capture (handler returns false) if the
        // caret was at the start of the EditText (and so there is no "previous character" to
        // delete). This includes when the EditText's contents are empty, but also happens for
        // non-empty contents (if the caret is at the start).
        //
        // This onDelete method (via SharedInputHandler.onKeyDown) can therefore see a KEYCODE_DEL
        // for which it would be surprising to run mDirPopper (navigating to the parent directory
        // of the current one). To avoid that, return early if an EditText was focused.
        Window window = (mActivity != null) ? mActivity.getWindow() : null;
        View view = (window != null) ? window.getCurrentFocus() : null;
        if ((view instanceof EditText) && isUseMaterial3FlagEnabled()) {
            return true;
        }

        mDirPopper.run();
        return true;
    }

    private boolean onBack() {
        if (mDrawer.isPresent() && mDrawer.isOpen()) {
            mDrawer.setOpen(false);
            return true;
        }

        if (mSearchCanceler.run()) {
            return true;
        }

        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) {
                Log.d(TAG, "Back pressed. Clearing existing selection.");
            }
            mSelectionMgr.clearSelection();
            return true;
        }

        return mDirPopper.run();
    }

    private boolean onEscape() {
        if (mSearchCanceler.run()) {
            return true;
        }

        if (isUseMaterial3FlagEnabled()) {
            mFocusManager.clearFocus();
        }

        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) {
                Log.d(TAG, "ESC pressed. Clearing existing selection.");
            }
            mSelectionMgr.clearSelection();
            return true;
        }

        return true;
    }
}
