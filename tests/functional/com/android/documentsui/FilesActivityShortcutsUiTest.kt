/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.test.filters.LargeTest
import com.android.documentsui.base.Providers
import com.android.documentsui.base.Providers.ROOT_ID_DEVICE
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.roots.ShortcutResourceValues
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.sidebar.RootsFragment
import com.android.documentsui.util.FlagUtils.Companion.isHomeScreenFilesFlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This class provides functional tests for FilesActivity which requires a new set of shortcuts on
 * the sidebar. The shortcut setup will be done prior to the activity launching.
 */
@LargeTest
class FilesActivityShortcutsUiTest : ActivityTestJunit4<FilesActivity>() {
    private val SHORTCUT_ID: String = "Test Shortcut"

    private var storageDocsHelper: DocumentsProviderHelper? = null
    private var primaryRoot: RootInfo? = null

    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

    @get:Rule val testFilesRule: TestFilesRule = TestFilesRule()

    @Before
    @Throws(Exception::class)
    fun setUpTest() {
        if (isHomeScreenFilesFlagEnabled()) {
            storageDocsHelper = setupStorageAuthorityDocsHelper()
            primaryRoot = storageDocsHelper?.getRoot(ROOT_ID_DEVICE)
            val folder1Id =
                getOrCreateFolderDocId(storageDocsHelper!!, primaryRoot!!.documentId, "Folder 1")
            val folder2Id = getOrCreateFolderDocId(storageDocsHelper!!, folder1Id, "Folder 2")
            val folder3Id = getOrCreateFolderDocId(storageDocsHelper!!, folder2Id, "Folder 3")
            getOrCreateFolderDocId(storageDocsHelper!!, folder3Id, "Folder 4")

            // Set up the shortcut resources and pre create the shortcut folder.
            // Mock the resource values for shortcuts
            val resource =
                listOf(
                    ShortcutResourceValues(
                        primaryRoot!!.authority,
                        primaryRoot!!.rootId,
                        primaryRoot!!.documentId,
                        SHORTCUT_ID,
                        SHORTCUT_ID,
                        R.drawable.ic_root_homescreen,
                    ),
                    ShortcutResourceValues(
                        primaryRoot!!.authority,
                        primaryRoot!!.rootId,
                        folder1Id,
                        "Folder 2",
                        "Folder 2",
                        R.drawable.ic_root_smartphone,
                    ),
                    ShortcutResourceValues(
                        primaryRoot!!.authority,
                        primaryRoot!!.rootId,
                        folder2Id,
                        "Folder 3",
                        "Folder 3",
                        R.drawable.ic_root_smartphone,
                    ),
                )
            setUpShortcuts(resource)
        }
    }

    @After
    @Throws(Exception::class)
    fun tearDownTest() {
        storageDocsHelper = null
        primaryRoot = null
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
    fun testClickShortcutFolderPreExisting() {
        switchRoot(SHORTCUT_ID)
        bots.main.assertSearchBarGone()

        val showDockedSearch = context!!.resources.getBoolean(getRes(R.bool.show_docked_search))

        if (showDockedSearch) {
            bots.main.assertDockedSearchBarShow()
        } else {
            bots.main.assertOptionsMenuSearchShow()
        }

        bots.main.assertWindowTitle(SHORTCUT_ID)
        storageDocsHelper!!.assertHasDirectory(primaryRoot!!.documentId, SHORTCUT_ID)

        bots.roots.assertItemSelected(SHORTCUT_ID)
        bots.roots.assertItemNotSelected(primaryRoot!!.title)
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(Exception::class)
    fun testNavigateOnShortcutToChildFolderSelectionRemains() {
        // We will have a chain of folders like so: storage -> 1 -> 2 (shortcut) -> 3 -> 4
        // The breadcrumb path however will be: 2 (shortcut) -> 3 -> 4
        switchRoot("Folder 2")
        bots.main.assertWindowTitle("Folder 2")
        bots.breadcrumb.assertItemsPresent("Folder 2")
        bots.roots.assertItemSelected("Folder 2")

        // Open "Folder 3" from directory list, sidebar selection should remain on Folder 2.
        bots.directory.openDocument("Folder 3")
        bots.main.assertWindowTitle("Folder 3")
        bots.breadcrumb.assertItemsPresent("Folder 2", "Folder 3")
        bots.roots.assertItemSelected("Folder 2")

        // Open "Folder 4" from directory list, sidebar selection should remain on Folder 2.
        bots.directory.openDocument("Folder 4")
        bots.main.assertWindowTitle("Folder 4")
        bots.breadcrumb.assertItemsPresent("Folder 2", "Folder 3", "Folder 4")
        bots.roots.assertItemSelected("Folder 2")

        // Open "Folder 3" from breadcrumb bar, sidebar selection should remain on Folder 2.
        bots.breadcrumb.clickItem("Folder 3")
        bots.main.assertWindowTitle("Folder 3")
        bots.breadcrumb.assertItemsPresent("Folder 2", "Folder 3")

        // Shortcut item no longer selected after clicking on the parent folder in the breadcrumb
        bots.roots.assertItemSelected("Folder 2")
        bots.roots.assertItemNotSelected(primaryRoot!!.title)
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(Exception::class)
    fun testBreadcrumbItemsUpdatedSwitchBetweenShortcuts() {
        switchRoot("Folder 3")
        bots.main.assertWindowTitle("Folder 3")
        bots.breadcrumb.assertItemsPresent("Folder 3")
        bots.roots.assertItemSelected("Folder 3")

        switchRoot("Folder 2")
        bots.main.assertWindowTitle("Folder 2")
        bots.breadcrumb.assertItemsPresent("Folder 2")
        bots.roots.assertItemSelected("Folder 2")
        bots.roots.assertItemNotSelected("Folder 3")
    }
}
