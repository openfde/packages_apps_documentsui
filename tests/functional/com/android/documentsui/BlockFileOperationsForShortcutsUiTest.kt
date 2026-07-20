/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import android.view.KeyEvent
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiObjectNotFoundException
import com.android.documentsui.base.Providers
import com.android.documentsui.base.Providers.ROOT_ID_DEVICE
import com.android.documentsui.base.UserId
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_ENABLE_TRASH_FLOW_RO
import com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.roots.ShortcutResourceValues
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.sidebar.RootsFragment
import com.android.documentsui.util.FlagUtils.Companion.isHomeScreenFilesFlagEnabled
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This class tests that the file operations such as moving, cutting, deleting or renaming triggered
 * by action menu clicks or keyboard controls get blocked when the operation is performed for a
 * system-defined shortcut.
 */
@LargeTest
class BlockFileOperationsForShortcutsUiTest : ActivityTestJunit4<FilesActivity>() {
    private val SHORTCUT_ID: String = "A Shortcut"

    private var storageDocsHelper: DocumentsProviderHelper? = null

    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

    @get:Rule val testFilesRule: TestFilesRule = TestFilesRule()

    @Before
    @Throws(Exception::class)
    fun setUpTest() {
        if (isHomeScreenFilesFlagEnabled()) {
            storageDocsHelper = setupStorageAuthorityDocsHelper()
            val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)

            // Set up the shortcut resources and pre create the shortcut folder.
            // Mock the resource values for shortcuts
            val resource =
                ShortcutResourceValues(
                    primaryRoot!!.authority,
                    primaryRoot.rootId,
                    primaryRoot.documentId,
                    SHORTCUT_ID,
                    SHORTCUT_ID,
                    R.drawable.ic_root_homescreen,
                )
            setUpShortcuts(listOf(resource))
        }
    }

    @After
    @Throws(Exception::class)
    fun tearDownTest() {
        storageDocsHelper = null
    }

    @Throws(java.lang.Exception::class)
    private fun setupStorageAuthorityDocsHelper(): DocumentsProviderHelper {
        // Create DocumentsProviderHelper to create files in Internal storage.
        val storageDocsHelper =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                Providers.AUTHORITY_STORAGE,
                context,
                Providers.AUTHORITY_STORAGE,
            )

        return storageDocsHelper
    }

    private fun setUpShortcuts(resources: List<ShortcutResourceValues>) {
        // Reset and refresh the shortcut resources
        val providers = DocumentsApplication.getProvidersCache(context)
        providers.setShortcutResources(resources)
        val roots = providers.getRootsBlocking()
        val shortcuts = providers.loadShortcutsForUser(userId)
        for (shortcut in shortcuts) {
            // Create the shortcut folders if they don't exist yet. In the actual code, this is
            // done by the loaders but we are not calling the loaders in the tests.
            shortcut.documentId =
                getOrCreateFolderDocId(
                    storageDocsHelper!!,
                    shortcut.parentDirDocumentId!!,
                    shortcut.folderTitle!!,
                )
        }

        mActivityScenario!!.onActivity({ activity: FilesActivity? ->
            val fragment = RootsFragment.get(activity!!.getSupportFragmentManager())
            fragment.loadFinished(roots, shortcuts, activity, activity.mState)
        })
    }

    @Throws(Exception::class)
    private fun getOrCreateFolderDocId(
        docsHelper: DocumentsProviderHelper,
        parentDocId: String,
        folderName: String,
    ): String {
        val info = docsHelper.findDocument(parentDocId, folderName)
        if (info == null) {
            val folderUri = docsHelper.createFolder(parentDocId, folderName)
            return DocumentsContract.getDocumentId(folderUri)
        } else {
            return info.documentId
        }
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(Exception::class)
    fun testMoveDocumentBlocked() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)
        bots.directory.findDocument(SHORTCUT_ID)
        device!!.waitForIdle()

        bots.directory.selectDocument(SHORTCUT_ID, 1)
        device!!.waitForIdle()

        val menuId =
            if (bots.main.isUseCopyCutFlow()) {
                R.string.menu_cut_to_clipboard
            } else {
                R.string.menu_move
            }
        bots.main.clickActionbarOverflowItem(context!!.getResources().getString(menuId))
        device!!.waitForIdle()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.resources.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(java.lang.Exception::class)
    fun testRenameOnShortcutFolder() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)
        bots.directory.selectDocument(SHORTCUT_ID, 1)
        clickRename()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
    }

    @Throws(UiObjectNotFoundException::class)
    private fun clickRename() {
        if (!bots.main.waitForActionModeBarToAppear()) {
            throw UiObjectNotFoundException("ActionMode bar not found")
        }
        bots.main.clickActionbarOverflowItem("Rename")
        device!!.waitForIdle()
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(Exception::class)
    fun testCutDocumentBlocked() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)

        bots.directory.findDocument(SHORTCUT_ID)
        device!!.waitForIdle()

        bots.directory.rightClickDocument(SHORTCUT_ID)
        device!!.waitForIdle()

        bots.menu.clickMenuItem(context!!.resources.getString(R.string.menu_cut_to_clipboard))
        device!!.waitForIdle()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.resources.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
        bots.directory.assertDocumentsVisible(SHORTCUT_ID)
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @DisableFlags(FLAG_ENABLE_TRASH_FLOW_RO)
    fun testDeleteDocumentBlocked() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)

        bots.directory.findDocument(SHORTCUT_ID)
        device!!.waitForIdle()

        bots.directory.selectDocument(SHORTCUT_ID, 1)
        device!!.waitForIdle()

        bots.main.clickDelete()
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.resources.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
        bots.directory.assertDocumentsVisible(SHORTCUT_ID)
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3, FLAG_ENABLE_TRASH_FLOW_RO)
    fun testTrashDocumentBlocked() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)

        bots.directory.findDocument(SHORTCUT_ID)
        device!!.waitForIdle()

        bots.directory.selectDocument(SHORTCUT_ID, 1)
        device!!.waitForIdle()

        bots.main.clickDelete()
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.resources.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
        bots.directory.assertDocumentsVisible(SHORTCUT_ID)
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(java.lang.Exception::class)
    fun testKeyboardCutDocumentShortcutFolderSelected() {
        val primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
        switchRoot(primaryRoot!!.title)

        bots.directory.selectDocument(SHORTCUT_ID, 1)
        device!!.waitForIdle()
        bots.keyboard.pressKey(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON)

        device!!.waitForIdle()

        assertNotNull(
            bots.directory.getSnackbar(
                context!!.getString(R.string.toast_op_not_allowed_for_shortcuts)
            )
        )
        bots.directory.assertDocumentsVisible(SHORTCUT_ID)
    }
}
