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

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.SelectionHelpers;
import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class KeyInputHandlerTest {

    private static final List<String> ITEMS = TestData.create(100);

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private KeyInputHandler mInputHandler;
    private SelectionTracker<String> mSelectionHelper;
    private TestFocusHandler mFocusHandler;
    private TestCallbacks mCallbacks;

    @Before
    public void setUp() {
        mSelectionHelper = SelectionHelpers.createTestInstance(ITEMS);
        mFocusHandler = new TestFocusHandler();
        mCallbacks = new TestCallbacks();

        mInputHandler =
                new KeyInputHandler(
                        mSelectionHelper,
                        SelectionHelpers.CAN_SET_ANYTHING,
                        mFocusHandler,
                        mCallbacks);
    }

    private void testArrowKey(int expectedSelectionSize, boolean shift) {
        // Start with item #11 selected but not focused...
        mSelectionHelper.select("11");

        // ...and item #7 focused (as we later pass details to onKey) but not selected.
        TestItemDetails details = new TestItemDetails();
        details.at(7);

        // Set it up so that hitting the up-arrow key will move focus to item #6.
        mFocusHandler.focusPos = 6;
        mFocusHandler.handleKey = true;

        // Hit the up-arrow key.
        KeyEvent event =
                new KeyEvent(
                        0,
                        0,
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_UP,
                        0,
                        shift ? KeyEvent.META_SHIFT_ON : 0);
        mInputHandler.onKey(details, event.getKeyCode(), event);

        // Check the assertion.
        Selection<String> selection = mSelectionHelper.getSelection();
        assertEquals(selection.toString(), expectedSelectionSize, selection.size());
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testArrowKey_nonShiftClearsSelection() {
        // 0 because we should end up with {} selected (the empty set).
        testArrowKey(0, false);
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testArrowKey_nonShiftPreservesSelection() {
        // 1 because we should end up with {#11} selected.
        testArrowKey(1, false);
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testArrowKey_ShiftExtendsSelection() {
        // 3 because we should end up with {#6, #7, #11} selected.
        testArrowKey(3, true);
    }

    private static final class TestCallbacks
            extends KeyInputHandler.Callbacks<ItemDetails<String>> {

        private @Nullable ItemDetails<String> mActivated;

        @Override
        public boolean onItemActivated(ItemDetails<String> item, KeyEvent e) {
            mActivated = item;
            return false;
        }

        private void assertActivated(ItemDetails<String> expected) {
            assertEquals(expected, mActivated);
        }

        @Override
        public boolean onFocusItem(ItemDetails<String> details, int keyCode, KeyEvent event) {
            return true;
        }
    }
}
