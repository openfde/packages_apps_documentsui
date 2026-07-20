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

import android.content.Intent
import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root.FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE
import android.provider.Flags.FLAG_ENABLE_SYNC_STATE
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import com.android.documentsui.filters.HugeLongTest
import com.android.documentsui.flags.Flags
import com.android.documentsui.picker.PickActivity
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.util.FlagUtils.Companion.isZipNgFlagEnabled
import com.google.common.collect.Lists
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
@RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
@EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
@HugeLongTest
class SyncStateUiTest : ActivityTestJunit4<BaseActivity>() {
    @JvmField @Parameterized.Parameter(0) var isListView: Boolean = false
    @JvmField @Parameterized.Parameter(1) var isInPicker: Boolean = false
    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()
    private lateinit var cloudProviderDocsHelper: DocumentsProviderHelper
    private val LONG_TICK_VISIBLE_DURATION_MS = 10000
    private val SHORT_TICK_VISIBLE_DURATION_MS = 100

    override fun setupTestingRoots() {
        super.setupTestingRoots()
        val rootHasLimitedFunctionalityWhenOffline: Boolean =
            (TestCloudProvider.ROOT_FLAGS and FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE) != 0
        cloudProviderDocsHelper =
            DocumentsProviderHelper(
                userId,
                TestCloudProvider.AUTHORITY,
                context,
                TestCloudProvider.AUTHORITY,
                rootHasLimitedFunctionalityWhenOffline,
            )
        val cloudRoot = cloudProviderDocsHelper.getRoot(TestCloudProvider.ROOT_ID)
        this.initialRoot = cloudRoot
    }

    @Before
    fun setUpTest() {
        if (isListView) {
            bots.main.switchToListMode()
        } else {
            bots.main.switchToGridMode()
        }
    }

    override fun launchActivity() {
        val cloudRoot = cloudProviderDocsHelper.getRoot(TestCloudProvider.ROOT_ID)
        if (isInPicker) {
            val getContentIntent = Intent(context, PickActivity::class.java)
            getContentIntent.setAction(Intent.ACTION_GET_CONTENT)
            getContentIntent.addCategory(Intent.CATEGORY_OPENABLE)
            getContentIntent.setType("*/*")

            // Launch picker in the TestCloudProvider root.
            getContentIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, cloudRoot.uri)
            mActivityScenario = ActivityScenario.launchActivityForResult(getContentIntent)
        } else {
            // Launch browser in the TestCloudProvider root.
            this.initialRoot = cloudRoot
            super.launchActivity()
        }
    }

    @After
    fun tearDownTest() {
        cloudProviderDocsHelper.cleanUpProvider()
    }

    @Test
    fun testBanner_isShownOffline() {
        setIsOnline(true)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()

        setIsOnline(true)

        bots.main.assertOfflineBannerDoesNotExist()
    }

    @Test
    fun testBanner_isNotShownOffline_whenInRootThatDoesNotHaveLimitedFunctionality() {
        setIsOnline(false)

        // Open root that does not have limited functionality when offline.
        switchRoot(DemoProvider.NAME)

        bots.main.assertOfflineBannerDoesNotExist()
    }

    @Test
    fun testBanner_isShownOffline_andHasNoDisplayedFiles() {
        setIsOnline(true)

        // Open dir1, it should have no files.
        bots.directory.openDocument(TestCloudProvider.DIR_DISPLAY_NAME)
        bots.directory.waitAndAssertPlaceholderMessageText(context!!.getString(R.string.empty))

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    @Test
    fun testBanner_isShownOffline_whenSearchingLocally() {
        setIsOnline(true)

        // Search for the file.
        bots.search.doSearch(TestCloudProvider.DISPLAY_NAME_0)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    @Test
    fun testBanner_isShownOffline_whenSearchingLocally_andHasNoDisplayedFiles() {
        setIsOnline(true)

        // Search for a non-existent file and get empty results.
        bots.search.doSearch("Shouldn't match to anything")
        bots.directory.waitAndAssertNoResultsMessage(TestCloudProvider.NAME)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    @Test
    fun testBanner_isShownOffline_whenSearchingEverywhere() {
        setIsOnline(true)

        // Open a root that does not have limited functionality when offline.
        switchRoot(DemoProvider.NAME)

        // Search for the file.
        bots.search.doSearch(TestCloudProvider.DISPLAY_NAME_0)
        bots.search.clickDropdownTrigger(R.id.search_location_trigger)

        // Click Everywhere, to search everywhere.
        bots.search.clickMenuItem(R.string.search_location_everywhere)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    @Test
    fun testBanner_isNotShownOffline_whenSearchingEverywhere_andHasNoDisplayedTestCloudProvider() {
        setIsOnline(true)

        // Open a root that does not have the TestCloudProvider file we are searching for.
        switchRoot(DemoProvider.NAME)

        // Search for a file that only exists in the demo root.
        bots.search.doSearch(DemoProvider.DIR_ERROR_AND_INFO)
        bots.search.clickDropdownTrigger(R.id.search_location_trigger)

        // Click Everywhere, to search everywhere.
        bots.search.clickMenuItem(R.string.search_location_everywhere)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        // No TestCloudProvider files are in the search results, so no banner should be shown.
        bots.main.assertOfflineBannerDoesNotExist()
    }

    @Test
    fun testBanner_isNotShownOffline_whenSearchingInARootThatDoesNotHaveLimitedFunctionality() {
        setIsOnline(true)

        // Open a root that does not have limited functionality when offline.
        switchRoot(DemoProvider.NAME)

        // Search for a file in the root.
        bots.search.doSearch(DemoProvider.MSG_ERROR_AND_INFO)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        // No TestCloudProvider files are searched for here, so no banner should be shown.
        bots.main.assertOfflineBannerDoesNotExist()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
        Flags.FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
        Flags.FLAG_USE_SEARCH_V2_READ_ONLY,
    )
    fun testBanner_isShownOffline_inRecent() {
        setIsOnline(true)

        // Create a new file to ensure one exists in Recent.
        cloudProviderDocsHelper.createDocument(
            cloudProviderDocsHelper.getRoot(TestCloudProvider.ROOT_ID),
            "text/plain",
            "recentFile",
        )

        // Open Recent.
        switchRoot("Recent")

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    @Test
    fun testNullSyncState_noIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.nullifySyncState(TestCloudProvider.DOC_ID_0)

        // No icons should be shown online.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)

        setIsOnline(false)

        // No icons should be shown offline and the document should remain enabled.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testAvailableLocallyState_noIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // No icons should be shown online.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)

        setIsOnline(false)

        // No icons should be shown offline and the document should remain enabled.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testNoAvailableLocallyState_noIconsOnline_disabledOffline() {
        setIsOnline(true)

        // No sync state flags are set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // No icons should be shown online.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)

        setIsOnline(false)

        // The document should be disabled when offline.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)

        // Set a sync state flag that is not "available locally".
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        // The document should still be disabled and no icons should be shown.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testNoAvailableLocallyState_butDirectory_enabledOffline() {
        // The "available locally" sync state flag is not set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        setIsOnline(false)

        // The document should be enabled when offline.
        bots.directory.assertDocumentEnabled(TestCloudProvider.DIR_DISPLAY_NAME)
    }

    @Test
    fun testNoAvailableLocallyState_butVirtual_enabledOffline() {
        // The "available locally" sync state flag is not set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        setIsOnline(false)

        if (isInPicker) {
            // Virtual files are always disabled in the picker.
        } else {
            // The document should be enabled when offline in the browser.
            bots.directory.assertDocumentEnabled(TestCloudProvider.VIRTUAL_DISPLAY_NAME)
        }
    }

    @Test
    fun testLocalChangesState_uploadIcon() {
        setIsOnline(true)

        // Set the "local changes" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )
    }

    @Test
    fun testUploadErrorState_errorIcon() {
        setIsOnline(true)

        // Set the "upload error" state as well as the "available locally" state to prevent the file
        // becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_ERROR or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )
    }

    @Test
    fun testDownloadErrorState_errorIcon() {
        setIsOnline(true)

        // Set the "download error" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_ERROR or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )
    }

    @Test
    fun testUploadInProgressState_progressIcon() {
        setIsOnline(true)

        // Set the "upload progress" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
    }

    @Test
    fun testUploadEndsWithAvailableLocally_progressIcon_thenTickIcon() {
        // Set a long tick time out to ensure that the test sees the tick.
        setTickVisibleDuration(LONG_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "upload in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End upload with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The progress icon should be replaced with a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithAvailableLocally_progressIcon_thenTickIcon() {
        // Set a long tick time out to ensure that the test sees the tick.
        setTickVisibleDuration(LONG_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The progress icon should be replaced with a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithAvailableLocally_tickIconDisappears() {
        // Set a short tick time out to ensure that the test sees the tick disappear.
        setTickVisibleDuration(SHORT_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The tick should disappear after a short time.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithErrorFlag_progressIcon_thenErrorIcon() {
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download in error state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_ERROR,
        )

        // The progress icon should be replaced with an error icon, rather than a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )
    }

    @Test
    fun testDownloadEndsWithNoFlags_iconsNotShownOnUnrelatedDocuments() {
        setIsOnline(true)

        // Set the "download in progress" state for doc0.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear only on doc0.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            android.R.id.progress,
        )

        // End download.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // A tick should only be shown on doc0.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDifferentDocumentsCanShowDifferentIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_1,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS,
        )
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.VIRTUAL_ID,
            Document.SYNC_STATE_FLAG_DOWNLOAD_ERROR,
        )
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DIR_ID,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            android.R.id.progress,
        )
        if (isInPicker) {
            // The virtual doc will be disabled in the picker and thus won't show any sync error
            // icons.
            bots.directory.assertDocumentDisabled(TestCloudProvider.VIRTUAL_DISPLAY_NAME)
            bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.VIRTUAL_DISPLAY_NAME)
        } else {
            bots.directory.assertObjectsEventuallyAppearOnDocument(
                TestCloudProvider.VIRTUAL_DISPLAY_NAME,
                R.id.sync_error_icon,
            )
        }
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DIR_DISPLAY_NAME,
            R.id.upload_icon,
        )
    }

    @Test
    fun testIconStillShownWhenNavigateOutAndBackIntoRoot() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        // The upload icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )

        // Navigate out of TestCloudProvider and back in.
        bots.roots.openRoot(StubProvider.ROOT_1_ID)
        bots.roots.openRoot(TestCloudProvider.NAME)

        // The upload icon should still be present.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )
    }

    @Test
    fun testCxtMenu_availableLocallyState_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the items that require content to be available are visible and enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredContextMenuItems(isDir = false)
        )

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredContextMenuItems(isDir = false)
        )
    }

    @Test
    fun testActionMenu_availableLocallyState_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(
            *getContentRequiredToolbarActionMenuItems()
        )

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredListActionMenuItems(isDir = false)
        )

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(
            *getContentRequiredToolbarActionMenuItems()
        )

        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredListActionMenuItems(isDir = false)
        )
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_contentRequiredActionsDisabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the items that require content to be available are visible and enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredContextMenuItems(isDir = false)
        )

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Disabled documents cannot be selected in the picker.
        if (!isInPicker) {
            // Reopen the context menu.
            bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

            // Check that the menu items are still visible but those that require content required
            // are disabled offline.
            bots.menu.assertListMenuItemsVisibleAndDisabled(
                *getContentRequiredContextMenuItems(isDir = false)
            )
        }
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_contentRequiredActionsDisabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(
            *getContentRequiredToolbarActionMenuItems()
        )

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredListActionMenuItems(isDir = false)
        )

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Disabled documents cannot be selected in the picker.
        if (!isInPicker) {
            // Check that the menu items are still visible but those that require content required
            // are disabled offline.
            bots.menu.assertToolbarMenuItemsVisibleAndDisabled(
                *getContentRequiredToolbarActionMenuItems()
            )

            bots.main.openOverflowMenu()
            bots.menu.assertListMenuItemsVisibleAndDisabled(
                *getContentRequiredListActionMenuItems(isDir = false)
            )
        }
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_andFolder_contentRequiredActionsDisabled_openEnabled() {
        assumeFalse("Folders can't be selected in picker mode and so don't have menus", isInPicker)
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DIR_DISPLAY_NAME)

        // Check that the items that require content to be available are visible and enabled.
        val openContextMenuItems = intArrayOf(R.string.menu_open_in_new_window)
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *openContextMenuItems,
            *getContentRequiredContextMenuItems(isDir = true),
        )

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DIR_DISPLAY_NAME)

        // Check that the menu items are still visible but those that require content required
        // are disabled offline. However, open menu items are an exception for folders and should
        // remain enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(*openContextMenuItems)
        bots.menu.assertListMenuItemsVisibleAndDisabled(
            *getContentRequiredContextMenuItems(isDir = true)
        )
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_folder_contentRequiredActionsEnabled_openEnabled() {
        assumeFalse("Folders can't be selected in picker mode and so don't have menus", isInPicker)
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        // Open the action menu.
        bots.directory.selectDocument(TestCloudProvider.DIR_DISPLAY_NAME, 1)
        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        val openListActionMenuItems = intArrayOf(R.string.menu_open_in_new_window)
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *openListActionMenuItems,
            *getContentRequiredListActionMenuItems(isDir = true),
        )

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Check that the menu items are still visible but those that require content required
        // are disabled offline. However, open menu items are an exception for folders and
        // should remain enabled.
        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(*openListActionMenuItems)
        bots.menu.assertListMenuItemsVisibleAndDisabled(
            *getContentRequiredListActionMenuItems(isDir = true)
        )
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_butVirtual_contentRequiredActionsEnabledOffline() {
        assumeFalse(
            "Virtual files can't be selected in picker mode and so don't have menus",
            isInPicker,
        )
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME)

        // Check that the items that require content to be available are visible and enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredContextMenuItems(isDir = false)
        )

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredContextMenuItems(isDir = false)
        )
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_butVirtual_contentRequiredActionsEnabledOffline() {
        assumeFalse(
            "Virtual files can't be selected in picker mode and so don't have menus",
            isInPicker,
        )
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(
            *getContentRequiredToolbarActionMenuItems()
        )

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredListActionMenuItems(isDir = false)
        )

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(
            *getContentRequiredToolbarActionMenuItems()
        )

        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *getContentRequiredListActionMenuItems(isDir = false)
        )
    }

    @Test
    fun testSearchLocally_noAvailableLocallyStateAndDownloadInProgress() {
        setIsOnline(true)

        // Set the "download progress" state but do not set the "available locally" state so the
        // file becomes disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // Search for the file.
        bots.search.doSearch(TestCloudProvider.DISPLAY_NAME_0)

        // A progress icon should be shown when online.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        setIsOnline(false)

        // The document should be disabled and no icons should be shown.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)

        // Disabled documents cannot be selected in the picker.
        if (!isInPicker) {
            // Open the context menu.
            bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

            // Check that the items that require content are visible but are disabled offline.
            bots.menu.assertListMenuItemsVisibleAndDisabled(
                *getContentRequiredContextMenuItems(isDir = false)
            )
        }
    }

    @Test
    fun testSearchEverywhere_noAvailableLocallyStateAndDownloadInProgress() {
        setIsOnline(true)

        // Open a root that does not have the file we are searching for.
        switchRoot("Paging Root")

        // Set the "download progress" state but do not set the "available locally" state so the
        // file becomes disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // Search for the file.
        bots.search.doSearch(TestCloudProvider.DISPLAY_NAME_0)
        bots.search.clickDropdownTrigger(R.id.search_location_trigger)

        // Click Everywhere, to search everywhere.
        bots.search.clickMenuItem(R.string.search_location_everywhere)

        // A progress icon should be shown when online.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        setIsOnline(false)

        // The document should be disabled and no icons should be shown.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)

        // Disabled documents cannot be selected in the picker.
        if (!isInPicker) {
            // Open the context menu.
            bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

            // Check that the items that require content are visible but are disabled offline.
            bots.menu.assertListMenuItemsVisibleAndDisabled(
                *getContentRequiredContextMenuItems(isDir = false)
            )
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
        Flags.FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
        Flags.FLAG_USE_SEARCH_V2_READ_ONLY,
    )
    fun testRecent_noAvailableLocallyState() {
        setIsOnline(true)

        // Create a new file to ensure one exists in Recent. This won't have any sync state flags
        // set.
        val recentFileName = "recentFileInTest"
        cloudProviderDocsHelper.createDocument(
            cloudProviderDocsHelper.getRoot(TestCloudProvider.ROOT_ID),
            "text/plain",
            recentFileName,
        )
        // Open Recent.
        switchRoot("Recent")

        // No icons should be shown online and the document should remain enabled.
        bots.directory.assertDocumentEnabled(recentFileName)
        bots.directory.assertDocumentSyncIconsNotVisible(recentFileName)

        setIsOnline(false)

        // The document should be disabled and no icons should be shown.
        bots.directory.assertDocumentDisabled(recentFileName)
        bots.directory.assertDocumentSyncIconsNotVisible(recentFileName)

        // Disabled documents cannot be selected in the picker.
        if (!isInPicker) {
            // Open the context menu.
            bots.directory.rightClickDocument(recentFileName)

            // Check that the items that require content are visible but are disabled offline.
            bots.menu.assertListMenuItemsVisibleAndDisabled(
                *getContentRequiredContextMenuItems(isDir = false, isRecentRoot = true)
            )
        }
    }

    fun getContentRequiredContextMenuItems(
        isDir: Boolean,
        isRecentRoot: Boolean = false,
    ): IntArray {
        return getContentRequiredMenuItems(isActionMenu = false, isDir, isRecentRoot)
    }

    fun getContentRequiredToolbarActionMenuItems(): IntArray {
        if (isInPicker) {
            return intArrayOf()
        }

        return intArrayOf(R.id.action_menu_share)
    }

    fun getContentRequiredListActionMenuItems(
        isDir: Boolean,
        isRecentRoot: Boolean = false,
    ): IntArray {
        return getContentRequiredMenuItems(isActionMenu = true, isDir, isRecentRoot)
    }

    fun getContentRequiredMenuItems(
        isActionMenu: Boolean,
        isDir: Boolean,
        isRecentRoot: Boolean = false,
    ): IntArray {
        @StringRes
        val zipMenuId = if (isZipNgFlagEnabled()) R.string.menu_zip else R.string.menu_compress
        if (isInPicker) {
            return if (isRecentRoot) intArrayOf() else intArrayOf(zipMenuId)
        }
        val copyMenuId =
            if (isActionMenu && !bots.main.isUseCopyCutFlow) {
                R.string.menu_copy
            } else {
                R.string.menu_copy_to_clipboard
            }
        var items = intArrayOf(copyMenuId)
        if (!isRecentRoot) {
            items = items + zipMenuId
        }
        if (!isActionMenu && !isDir) {
            items = items + R.string.menu_share
        }
        if (!isDir) {
            items = items + R.string.menu_open_with
        }
        return items
    }

    companion object {
        /**
         * Provides the test parameters for the parameterized test. This allows each test to run
         * either in list or grid view and either in picker or browser mode.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "isListView={0}, isInPicker={1}")
        fun data(): Iterable<Array<Any>> {
            return Lists.newArrayList(
                arrayOf(true, true),
                arrayOf(true, false),
                arrayOf(false, true),
                arrayOf(false, false),
            )
        }
    }
}
