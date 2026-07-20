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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.EnableFlags;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.util.VersionUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class RenameDocumentUiTest extends ActivityTestJunit4<FilesActivity> {

    private final String newName = "kitties.log";

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
    }

    @Test
    public void testRenameEnabled_SingleSelection() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.main.openOverflowMenu();
        bots.main.assertMenuEnabled(R.string.menu_rename, true);

        // Dismiss more options window
        device.pressBack();
    }

    @Test
    public void testNoRenameSupport_SingleSelection() throws Exception {
        if (VersionUtils.isAtLeastR()) {
            bots.directory.selectDocument(TestFilesRule.FILE_NAME_NO_RENAME, 1);
            bots.main.openOverflowMenu();
            bots.main.assertMenuEnabled(R.string.menu_rename, false);

            // Dismiss more options window
            device.pressBack();
        }
    }

    @Test
    public void testOneHasRenameSupport_MultipleSelection() throws Exception {
        if (VersionUtils.isAtLeastR()) {
            bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
            bots.directory.selectDocument(TestFilesRule.FILE_NAME_NO_RENAME, 2);
            bots.main.openOverflowMenu();
            bots.main.assertMenuEnabled(R.string.menu_rename, false);

            // Dismiss more options window
            device.pressBack();
        }
    }

    @Test
    public void testRenameDisabled_MultipleSelection() throws Exception {
        if (VersionUtils.isAtLeastR()) {
            bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
            bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 2);
            bots.main.openOverflowMenu();
            bots.main.assertMenuEnabled(R.string.menu_rename, false);

            // Dismiss more options window
            device.pressBack();
        }
    }

    @Test
    public void testRenameFile_OkButton() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        device.waitForIdle();
        bots.main.setDialogText(newName);

        device.waitForIdle();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ true);

        bots.directory.waitForDocument(newName);
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRenameFile_Enter() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        device.waitForIdle();
        bots.main.setDialogText(newName);

        device.waitForIdle();
        bots.keyboard.pressEnter();

        bots.directory.waitForDocument(newName);
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRenameWithoutChangeIsNoOp() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        device.waitForIdle();
        bots.keyboard.pressEnter();

        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRenameFile_Cancel() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        bots.main.setDialogText(newName);

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ true);

        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsAbsent(newName);
        bots.directory.assertDocumentsCount(4);

        bots.directory.assertSelection(1);
    }

    @Test
    public void testRenameFile_TrimTrailingSpaces() throws Exception {
        String inputNameText = " " + newName + " ";
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        device.waitForIdle();
        bots.main.setDialogText(inputNameText);

        device.waitForIdle();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ true);

        // Ensure that only the end of the filename gets trimmed
        bots.directory.waitForDocument(" " + newName);
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRenameFile_ToExistingFileAndTrimTrailingSpaces() throws Exception {
        String nameWithSpace = TestFilesRule.FILE_NAME_2 + " ";

        renameWithConflict(nameWithSpace);
        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ true);

        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_2);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRenameDir() throws Exception {
        String oldName = "Dir1";
        String newName = "Dir123";
        bots.directory.selectDocument(oldName, 1);

        clickRename();

        bots.main.setDialogText(newName);

        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsAbsent(oldName);
        bots.directory.assertDocumentsVisible(newName);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRename_EmptyFileName() throws Exception {
        String emptyName = "";
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();
        bots.main.setDialogText(emptyName);
        bots.keyboard.pressEnter();
        assertTrue(bots.main.findRenameErrorMessage(R.string.missing_rename_error).exists());

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ true);
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRename_CreateHiddenFile() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();
        bots.main.setDialogText(TestFilesRule.HIDDEN_FILE_NAME);
        assertTrue(bots.main.findRenameErrorMessage(R.string.hidden_file_rename_warning).exists());
        bots.keyboard.pressEnter();
        bots.directory.assertDocumentsAbsent(TestFilesRule.HIDDEN_FILE_NAME);
        bots.directory.assertDocumentsCount(3);
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRename_ContainsInvalidCharacters() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();
        bots.main.setDialogText(TestFilesRule.INVALID_FILE_NAME);
        assertTrue(
                bots.main
                        .findUiObjectWithResIdAndSuffix(R.string.rename_invalid_character, "/")
                        .exists());
        bots.keyboard.pressEnter();
        assertTrue(
                bots.main
                        .findUiObjectWithResIdAndSuffix(R.string.rename_invalid_character, "/")
                        .exists());
        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ true);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRename_NameExists() throws Exception {
        renameWithConflict(TestFilesRule.FILE_NAME_2);

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ true);

        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_2);
        bots.directory.assertDocumentsCount(4);
    }

    @Test
    public void testRename_RecoverAfterConflict() throws Exception {
        renameWithConflict(TestFilesRule.FILE_NAME_2);
        device.waitForIdle();

        bots.main.setDialogText(newName);

        device.waitForIdle();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ true);

        bots.directory.waitForDocument(newName);
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsCount(4);
    }

    private void renameWithConflict(String newName) throws Exception {
        // Check that document with the new name exists
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_2);
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        clickRename();

        bots.main.assertDialogText(TestFilesRule.FILE_NAME_1);
        assertFalse(bots.main.findRenameErrorMessage(R.string.name_conflict).exists());
        bots.main.setDialogText(newName);
        bots.keyboard.pressEnter();
        assertTrue(bots.main.findRenameErrorMessage(R.string.name_conflict).exists());
    }

    private void clickRename() throws UiObjectNotFoundException {
        if (!bots.main.waitForActionModeBarToAppear()) {
            throw new UiObjectNotFoundException("ActionMode bar not found");
        }
        bots.main.clickActionbarOverflowItem("Rename");
        device.waitForIdle();
    }
}