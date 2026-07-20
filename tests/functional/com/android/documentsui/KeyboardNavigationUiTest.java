/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import static junit.framework.Assert.fail;

import android.os.Build;
import android.platform.test.annotations.EnableFlags;
import android.view.KeyEvent;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class KeyboardNavigationUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                docsHelper.createDocument(root, "image/png", "files1.png");
                            });

    // Tests that pressing tab switches focus between the roots and directory listings.
    @Ignore
    @Test
    public void testKeyboard_tab() throws Exception {
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.roots.assertHasFocus();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.directory.assertHasFocus();
    }

    // Tests that arrow keys do not switch focus away from the dir list.
    @Ignore
    @Test
    public void testKeyboard_arrowsDirList() throws Exception {
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_LEFT);
            bots.directory.assertHasFocus();
        }
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            bots.directory.assertHasFocus();
        }
    }

    @Ignore
    @Test
    public void testKeyboard_tabFocuses() throws Exception {
        bots.roots.closeDrawer();
        if (bots.main.inFixedLayout()) {
            // Tablet devices need to press one more tab since it focuses on root list first
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        }
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.directory.assertFirstDocumentHasFocus();

        // This should not cause any exceptions
        bots.keyboard.pressKey(KeyEvent.KEYCODE_F);
    }

    // Tests that arrow keys do not switch focus away from the roots list.
    @Test
    public void testKeyboard_arrowsRootsList() throws Exception {

        // Open the drawer so we can ensure root list available even for phones
        bots.roots.openDrawer();

        // Press tab to move the focus into the Root list.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        // When M3 is on, the root container "coordinator_layout" becomes focusable, so the first
        // tab press will focus on it instead of the root list, so one more tab is required.
        if (isUseMaterial3FlagEnabled()) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        }
        bots.roots.assertHasFocus();

        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            bots.roots.assertHasFocus();
        }
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_LEFT);
            bots.roots.assertHasFocus();
        }
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testKeyboard_controlSpaceTogglesSelection() throws Exception {
        for (int i = 0; !bots.directory.anyDocumentHasFocus(); i++) {
            if (i > 99) {
                fail("could not focus a document");
            }
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_LEFT_ON);
        }

        bots.directory.assertNoSelection();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_LEFT_ON);
        bots.directory.assertSelection(1);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_LEFT_ON);
        bots.directory.assertNoSelection();
    }
}
