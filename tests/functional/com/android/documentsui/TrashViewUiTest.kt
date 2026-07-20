/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API
import android.provider.MediaStore
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.StubProvider.ROOT_0_ID
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.testing.TestProvidersAccess.TRASH_ROOT
import com.android.documentsui.util.VersionUtils
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for verifying the functionality of the Trash view, including moving items to trash,
 * viewing them, and using the "Empty Trash" feature.
 */
@LargeTest
@RequiresFlagsEnabled(FLAG_ENABLE_DOCUMENTS_TRASH_API)
@EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
class TrashViewUiTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val setFlags = OverrideFlagsRule()

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val mTestFilesRule: TestFilesRule =
        TestFilesRule().createTestFiles { docsHelper: DocumentsProviderHelper ->
            createTempFiles(docsHelper)
        }

    @Before
    @Throws(Exception::class)
    fun setUpTest() {
        // TODO(b/457843307): Verify after the SDK is finalized. This test depends on StubProvider,
        //  which currently encounters a NoSuchMethodError when the platform flag is used.
        assumeTrue(VersionUtils.isGreaterThanB())

        if (SdkLevel.isAtLeastR()) {
            MediaStore.waitForIdle(context!!.contentResolver)
        }

        setNotificationAccess(true)
    }

    /**
     * Creates a temporary directory and populates it with test files.
     *
     * @param docsHelper The helper for interacting with the document provider.
     */
    private fun createTempFiles(docsHelper: DocumentsProviderHelper) {
        val root = docsHelper.getRoot(ROOT_0_ID)
        val dirUri = docsHelper.createFolder(root.documentId, TestFilesRule.DIR_NAME_1)
        val prefix = "file-${System.nanoTime()}-"

        for (i in 0 until TEST_FILE_COUNT) {
            val fileName = "$prefix$i.txt"
            docsHelper.createDocument(dirUri, "text/plain", fileName)
        }
    }

    /**
     * Verifies that when files are moved to the trash, they correctly appear in the Trash view and
     * the "Empty Trash" banner is displayed.
     */
    @Test
    fun testMoveToTrashDisplaysItemsInTrashView() {
        val trashedFileNames = moveFilesToTrash()

        bots.roots.openRoot(TRASH_ROOT.title)

        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())
    }

    /**
     * Verifies that clicking the "Empty trash now" button and confirming the dialog permanently
     * deletes all items from the trash.
     */
    @Test
    fun testEmptyTrashPermanentlyDeletesAllItems() {
        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Then, ensure the "Empty Trash" banner is visible.
        bots.main.assertEmptyTrashBannerIsVisible()

        // Check that the "Empty Trash" button is enabled.
        bots.main.assertEmptyTrashNowButtonEnabled(true)

        // Trigger the "Empty Trash" flow and confirm.
        bots.main.clickEmptyTrashNowButton()
        device!!.waitForIdle()
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the previously trashed files are now gone.
        bots.directory.assertDocumentsAbsent(*trashedFileNames.toTypedArray())

        // Verify that Empty trash bin button is disabled.
        bots.main.assertEmptyTrashNowButtonEnabled(false)
    }

    /** Tests that permanently deleting selected items from the Trash view works correctly. */
    @Test
    fun testTrashPermanentlyDeleteItem() {
        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Select the first two files to permanently delete.
        val filesToPermanentlyDelete = trashedFileNames.take(2)
        filesToPermanentlyDelete.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the permanent delete button in the toolbar.
        bots.main.clickDelete()
        device!!.waitForIdle()

        // Confirm the permanent delete dialog.
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the selected files are now gone.
        bots.directory.assertDocumentsAbsent(*filesToPermanentlyDelete.toTypedArray())

        // Verify that the remaining files are still in the trash.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())
    }

    /** Tests permanently deleting items from within a trashed folder. */
    @Test
    fun testPermanentlyDeleteItemsFromTrashedFolder() {
        val trashedFolderName = moveFolderToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed folder is visible in the UI.
        bots.directory.assertDocumentsPresent(trashedFolderName)

        // Open the trashed folder.
        bots.directory.openDocument(trashedFolderName)
        device!!.waitForIdle()

        val dirDocumentId =
            mDocsHelper!!
                .getAllTrashItems(ROOT_0_ID)
                .first { it.displayName == trashedFolderName }
                .documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        val trashedFileNames = documents.map { it.displayName }
        assert(trashedFileNames.size == TEST_FILE_COUNT) {
            "Expected $TEST_FILE_COUNT files in the folder, but found ${trashedFileNames.size}"
        }

        // Check that the files are visible inside the folder.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Select the first two files to permanently delete.
        val filesToPermanentlyDelete = trashedFileNames.take(2)
        filesToPermanentlyDelete.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the permanent delete button in the toolbar.
        bots.main.clickDelete()
        device!!.waitForIdle()

        // Confirm the permanent delete dialog.
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the selected files are now gone from the folder.
        bots.directory.assertDocumentsAbsent(*filesToPermanentlyDelete.toTypedArray())

        // Verify that the remaining files are still in the folder.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())

        // Go back to the trash root and verify the folder is still there.
        device!!.pressBack()
        device!!.waitForIdle()
        bots.directory.assertDocumentsPresent(trashedFolderName)
    }

    /** Tests that restoring selected items from the Trash view works correctly. */
    @Test
    fun testRestoreFromTrash() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService which invokes RestoreJob, which we cannot easily force from test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Select the first two files to restore.
        val filesToRestore = trashedFileNames.take(2)
        filesToRestore.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the restore button in the toolbar.
        bots.main.clickActionItem("Restore")
        device!!.waitForIdle()

        // Verify that the selected files are now gone from the trash.
        bots.directory.assertDocumentsAbsent(*filesToRestore.toTypedArray())

        // Verify that the remaining files are still in the trash.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())

        // Go back to the original directory and verify the files are restored.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.directory.assertDocumentsPresent(*filesToRestore.toTypedArray())
    }

    /** Verifies that opening a file from within a trashed folder shows the restore dialog. */
    @Test
    fun testRestoreFileFromTrashedFolder() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService which invokes RestoreJob, which we cannot easily force from test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        val trashedFolderName = moveFolderToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // Verify the trashed folder is visible.
        bots.directory.assertDocumentsPresent(trashedFolderName)

        // Open the trashed folder.
        bots.directory.openDocument(trashedFolderName)
        device!!.waitForIdle()

        val dirDocumentId =
            mDocsHelper!!
                .getAllTrashItems(ROOT_0_ID)
                .first { it.displayName == trashedFolderName }
                .documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        val trashedFileNames = documents.map { it.displayName }
        assert(trashedFileNames.isNotEmpty()) { "Trashed folder should not be empty" }

        // Select the first two files to restore.
        val filesToRestore = trashedFileNames.take(2)
        filesToRestore.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the restore button in the toolbar.
        bots.main.clickActionItem("Restore")

        // Verify that the selected files are now gone from the trash.
        bots.directory.assertDocumentsAbsent(*filesToRestore.toTypedArray())

        // Verify that the remaining files are still in the trash.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())

        // Go back to the original directory and verify the files are restored.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(trashedFolderName)
        bots.directory.assertDocumentsPresent(*filesToRestore.toTypedArray())
    }

    /** Verifies that attempting to open a trashed item shows a dialog to restore it. */
    @Test
    fun testOpenTrashedItemShowsRestoreDialog() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService which invokes RestoreJob, which we cannot easily force from test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // Check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Attempt to open the first trashed file.
        bots.directory.openDocument(trashedFileNames.first())
        device!!.waitForIdle()

        // Verify that the restore dialog is shown.
        bots.main.assertDialogTitle(R.string.file_open_in_trash_dialog_title)
        bots.main.assertDialogMessage(R.string.file_open_in_trash_dialog_message)

        // Click "Cancel" and ensure the file remains in the trash.
        bots.main.clickDialogCancelButton(false)
        device!!.waitForIdle()
        bots.directory.assertDocumentsPresent(trashedFileNames.first())

        // Attempt to open the file again.
        bots.directory.openDocument(trashedFileNames.first())
        device!!.waitForIdle()

        // This time, click "Restore file".
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the file is no longer in the trash.
        bots.directory.assertDocumentsAbsent(trashedFileNames.first())

        // Verify that the file is now back in its original location.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.directory.assertDocumentsPresent(trashedFileNames.first())
    }

    /** Verifies that opening a file from within a trashed folder shows the restore dialog. */
    @Test
    fun testOpenItemFromTrashedFolderShowsRestoreDialog() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService which invokes RestoreJob, which we cannot easily force from test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        val trashedFolderName = moveFolderToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // Verify the trashed folder is visible.
        bots.directory.assertDocumentsPresent(trashedFolderName)

        // Open the trashed folder.
        bots.directory.openDocument(trashedFolderName)
        device!!.waitForIdle()

        val dirDocumentId =
            mDocsHelper!!
                .getAllTrashItems(ROOT_0_ID)
                .first { it.displayName == trashedFolderName }
                .documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        val trashedFileNames = documents.map { it.displayName }
        assert(trashedFileNames.isNotEmpty()) { "Trashed folder should not be empty" }

        val fileToOpen = trashedFileNames.first()

        // Verify the file is visible inside the trashed folder.
        bots.directory.assertDocumentsPresent(fileToOpen)

        // Attempt to open the file.
        bots.directory.openDocument(fileToOpen)
        device!!.waitForIdle()

        // Verify that the restore dialog is shown.
        bots.main.assertDialogTitle(R.string.file_open_in_trash_dialog_title)
        bots.main.assertDialogMessage(R.string.file_open_in_trash_dialog_message)

        // Click "Cancel" and ensure the file is still in the folder.
        bots.main.clickDialogCancelButton(false)
        device!!.waitForIdle()
        bots.directory.assertDocumentsPresent(fileToOpen)

        // Attempt to open the file again.
        bots.directory.openDocument(fileToOpen)
        device!!.waitForIdle()

        // This time, click "Restore file".
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify the file is no longer in the trashed folder.
        bots.directory.assertDocumentsAbsent(fileToOpen)

        // Go back to the trash root. The folder should still be there.
        device!!.pressBack()
        device!!.waitForIdle()
        bots.directory.assertDocumentsPresent(trashedFolderName)

        // Now, check that the restored file is in its original location.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.directory.assertDocumentsPresent(fileToOpen)
    }

    /**
     * Verifies that restoring a file from the hidden .trash-storage directory works correctly. This
     * test reproduces the scenario in b/475737649 where restoration from the hidden directory fails
     * to physically move files back, despite reporting success.
     */
    @Test
    fun testRestoreFromHiddenTrashStorage() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService which invokes RestoreJob, which we cannot easily force from test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        val trashedFileNames = moveFilesToTrash()

        // Navigate to the hidden .trash-storage directory within the root.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        // Enable "Show hidden files" from the overflow menu to reveal hidden folders.
        bots.main.showHiddenFilesIfNeeded()
        device!!.waitForIdle()
        bots.directory.openDocument(".trash-storage")
        device!!.waitForIdle()

        // Navigate into the subfolder corresponding to the original parent directory.
        // For this test environment, the files are moved from DIR_NAME_1.
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        device!!.waitForIdle()

        // Identify the trashed file and verify it is present in the hidden folder.
        val fileToRestore = trashedFileNames.first()
        bots.directory.assertDocumentsPresent(fileToRestore)

        // Select the file and click "Restore" from the menu.
        bots.directory.selectDocument(fileToRestore, 1)
        bots.main.clickActionItem("Restore")
        device!!.waitForIdle()

        // Verify that the file is physically removed from the hidden trash folder.
        bots.directory.assertDocumentsAbsent(fileToRestore)

        // Navigate back to the original directory and verify the file is restored.
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.directory.assertDocumentsPresent(fileToRestore)
    }

    /**
     * Navigates into the test directory, selects a subset of files, and moves them to the trash.
     *
     * @return A list of filenames that were moved to the trash.
     */
    @Throws(Exception::class)
    private fun moveFilesToTrash(): List<String> {
        bots.roots.openRoot(StubProvider.ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)

        val rootInfo = mDocsHelper!!.getRoot(ROOT_0_ID)
        val dirDocumentId =
            mDocsHelper!!.findDocument(rootInfo.documentId, TestFilesRule.DIR_NAME_1)!!.documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        assert(documents.size == TEST_FILE_COUNT) { "Documents size should be $TEST_FILE_COUNT" }

        val filesToTrash = documents.take(3).map { it.displayName }

        bots.directory.assertDocumentsPresent(*filesToTrash.toTypedArray())

        filesToTrash.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        bots.main.clickToolbarItem(R.id.action_menu_move_to_trash)
        device!!.waitForIdle()

        return filesToTrash
    }

    /**
     * Moves the test directory and all its contents to the trash.
     *
     * @return The name of the folder moved to the trash.
     */
    @Throws(Exception::class)
    private fun moveFolderToTrash(): String {
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()

        // Select and move the entire directory to the trash.
        bots.directory.selectDocument(TestFilesRule.DIR_NAME_1, 1)
        bots.main.clickToolbarItem(R.id.action_menu_move_to_trash)

        bots.directory.assertDocumentsAbsent(TestFilesRule.DIR_NAME_1)

        device!!.waitForIdle()

        return TestFilesRule.DIR_NAME_1
    }

    companion object {
        private const val TEST_FILE_COUNT = 10
    }
}
