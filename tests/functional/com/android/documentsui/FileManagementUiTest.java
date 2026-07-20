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
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.EnableFlags;
import android.view.KeyEvent;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@LargeTest
public class FileManagementUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                final Uri dir1 =
                                        docsHelper.createFolder(root, TestFilesRule.DIR_NAME_1);
                                docsHelper.createFolder(dir1, "ChildDir1");
                                docsHelper.createDocument(root, "text/plain", "file0.log");
                                docsHelper.createDocument(root, "image/png", "file1.png");
                                docsHelper.createDocument(root, "text/csv", "file2.csv");
                                docsHelper.createDocument(root, "text/plain", "anotherFile0.log");
                                docsHelper.createDocument(root, "text/plain", "poodles.text");
                            });

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testCreateDirectory() throws Exception {
        // Disable the root notification because it triggers root list update which then triggers
        // the fragment recreation, which impacts the focus behavior.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, bundle);

        final String newFolderName = "Kung fu Panda";
        bots.main.clickToolbarOverflowItem(context.getString(R.string.menu_create_dir));
        device.waitForIdle();

        bots.main.setDialogText(newFolderName);
        device.waitForIdle();

        // Pressing enter to commit the new folder creation and close the dialog. Note:
        // pressEnter() doesn't work here, we need an actual keyboard press to trigger focus
        // change.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_ENTER);

        bots.directory.waitForDocument(newFolderName);

        // Focus the newly created directory on S/T doesn't work reliably somehow, the
        // requestFocus() returns false on both versions. Wrapping the requestFocus() call inside
        // view.post() fixes the issue on S, but still fail on T, hence we only check U+ here.
        if (SdkLevel.isAtLeastU()) {
            bots.directory.assertDocumentHasFocus(newFolderName);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testCreateDirectoryTrimTrailingSpaces() throws Exception {
        // Disable the root notification because it triggers root list update which then triggers
        // the fragment recreation, which impacts the focus behavior.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, bundle);

        final String newFolderName = "Kung fu Panda  ";
        bots.main.clickToolbarOverflowItem(context.getString(R.string.menu_create_dir));
        device.waitForIdle();

        bots.main.setDialogText(newFolderName);
        device.waitForIdle();

        // Pressing enter to commit the new folder creation and close the dialog. Note:
        // pressEnter() doesn't work here, we need an actual keyboard press to trigger focus
        // change.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_ENTER);

        String trimmedName = newFolderName.trim();
        bots.directory.waitForDocument(trimmedName);

        // Focus the newly created directory on S/T doesn't work reliably somehow, the
        // requestFocus() returns false on both versions. Wrapping the requestFocus() call inside
        // view.post() fixes the issue on S, but still fail on T, hence we only check U+ here.
        if (SdkLevel.isAtLeastU()) {
            bots.directory.assertDocumentHasFocus(trimmedName);
        }
    }

    @Test
    public void testDeleteDocument() throws Exception {
        bots.directory.selectDocument("file1.png", 1);
        device.waitForIdle();
        bots.main.clickDelete();

        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        bots.directory.assertDocumentsAbsent("file1.png");
    }

    @HugeLongTest
    @Test
    public void testKeyboard_CutDocument() throws Exception {
        bots.directory.selectDocument("file1.png", 1);
        device.waitForIdle();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON);

        device.waitForIdle();

        if (isUseMaterial3FlagEnabled()) {
            // file1.png is still selected.
            bots.directory.assertSelection(1);
        }

        // Keep using the old openRoot. The copy action triggers a system popup and a DocsUI
        // snackbar. The new openRoot is too fast and ends up clicking on the popup/snackbar.
        bots.roots.openRoot(ROOT_1_ID);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);

        bots.directory.waitForDocument("file1.png");
        bots.directory.assertDocumentsVisible("file1.png");

        switchRoot(ROOT_0_ID);
        bots.directory.assertDocumentsAbsent("file1.png");
    }

    @DesktopTest(cujs = {"b/434068359"})
    @HugeLongTest
    @Test
    public void testKeyboard_CopyDocument() throws Exception {
        bots.directory.selectDocument("file1.png", 1);
        device.waitForIdle();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON);

        device.waitForIdle();

        if (isUseMaterial3FlagEnabled()) {
            // file1.png is still selected.
            bots.directory.assertSelection(1);
        }

        // Keep using the old openRoot. The copy action triggers a system popup and a DocsUI
        // snackbar. The new openRoot is too fast and ends up clicking on the popup/snackbar.
        bots.roots.openRoot(ROOT_1_ID);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);

        bots.directory.waitForDocument("file1.png");

        switchRoot(ROOT_0_ID);
        bots.directory.waitForDocument("file1.png");
    }

    @HugeLongTest
    @Test
    public void testKeyboard_PasteDocumentWhileSelectionActive() throws Exception {
        bots.directory.selectDocument("file1.png", 1);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON);

        if (isUseMaterial3FlagEnabled()) {
            bots.directory.clearSelection();
        }

        device.waitForIdle();
        bots.directory.openDocument("Dir1");
        bots.directory.selectDocument("ChildDir1", 1);

        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
        device.waitForIdle();

        bots.directory.waitForDocument("file1.png");
    }

    @Test
    public void testDeleteDocument_Cancel() throws Exception {
        bots.directory.selectDocument("file1.png", 1);
        device.waitForIdle();
        bots.main.clickDelete();

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ false);

        bots.directory.waitForDocument("file1.png");
    }

    @HugeLongTest
    @Test
    public void testCopyLargeAmountOfFiles() throws Exception {
        // Suppress root notification. We're gonna create tons of files and it will soon crash
        // DocsUI because too many root refreshes are queued in an executor.
        Bundle conf = new Bundle();
        conf.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, conf);

        final Uri test = mDocsHelper.createFolder(rootDir0, "test");
        final Uri target = mDocsHelper.createFolder(rootDir0, "target");
        String nameOfLastFile = "";
        for (int i = 0; i <= Shared.MAX_DOCS_IN_INTENT; ++i) {
            final String name = i + ".txt";
            final Uri doc =
                    mDocsHelper.createDocument(test, "text/plain", name);
            mDocsHelper.writeDocument(doc, Integer.toString(i).getBytes());
            nameOfLastFile = nameOfLastFile.compareTo(name) < 0 ? name : nameOfLastFile;
        }

        // Use keyboard shortcuts in place of manually clicking/tapping.
        openFolder("test");
        bots.directory.waitForDocument("0.txt");
        bots.keyboard.pressKey(
                KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON);
        bots.keyboard.pressKey(
                KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON);

        switchRoot(ROOT_0_ID);
        openFolder("target");
        bots.directory.pasteFilesFromClipboard();

        // Switch to list mode to avoid files being partially in view
        bots.main.switchToListMode();

        // Use these 2 events as a signal that many files have already been copied. Only considering
        // Android devices a more reliable way is to wait until notification goes away, but ARC++
        // uses Chrome OS notifications so it isn't even an option.
        bots.directory.waitForDocument("0.txt");
        bots.directory.waitForDocument(nameOfLastFile, true);

        final int expectedCount = Shared.MAX_DOCS_IN_INTENT + 1;
        List<DocumentInfo> children = mDocsHelper.listChildren(target, -1);
        if (children.size() == expectedCount) {
            return;
        }

        // Files weren't copied fast enough, so gonna do some polling until they all arrive or copy
        // seems stalled.
        int maxRetries = 10;
        int retries = 0;
        while (retries++ <= maxRetries) {
            Thread.sleep(200);
            List<DocumentInfo> newChildren = mDocsHelper.listChildren(target, -1);
            if (newChildren.size() == expectedCount) {
                return;
            }

            if (newChildren.size() > expectedCount && retries >= maxRetries) {
                // Should never happen
                fail("Something wrong with this test case. Copied file count "
                        + newChildren.size() + " exceeds expected number " + expectedCount);
            }

            if (newChildren.size() <= children.size() && retries >= maxRetries) {
                fail("Only copied " + children.size()
                        + " files, expected to copy " + expectedCount + " files.");
            }

            children = newChildren;
        }
    }
}
