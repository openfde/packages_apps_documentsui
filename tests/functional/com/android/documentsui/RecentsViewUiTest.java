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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_UX_PHASE_2_RO;
import static com.android.documentsui.flags.Flags.FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS;
import static com.android.documentsui.flags.Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.actions.WaitForCheckState;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.bots.DirectoryListBot;
import com.android.documentsui.bots.EspressoBotsKt;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.rules.ExternalStorageProviderTestFilesRule;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class RecentsViewUiTest extends ActivityTestJunit4<FilesActivity> {
    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final ExternalStorageProviderTestFilesRule mTestFilesRule =
            new ExternalStorageProviderTestFilesRule();

    private DocumentsProviderHelper mCloudDocsHelper;

    @Before
    public void setUpTest() {
        mCloudDocsHelper =
                new DocumentsProviderHelper(
                        userId, TestCloudProvider.AUTHORITY, context, TestCloudProvider.AUTHORITY);
    }

    @After
    public void tearDownTest() throws RemoteException {
        mCloudDocsHelper.cleanUpProvider();
    }

    /**
     * Ensure that Recents shows all file types if the flag is enabled.
     *
     * Note that this feature requires the enable_media_documents_provider_allfiles_root flag to be
     * enabled in MediaProvider. We cannot force that from these tests. Don't force our flag on (as
     * the test will fail if the MediaProvider feature is disabled), but do test the feature if the
     * flag is on (we will ramp the two flags simultaneously).
     */
    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsContainsAllFileTypes() throws Exception {
        bots.roots.openRoot("Recent");

        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_4, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_5, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_6, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_7, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_8, true).exists());
    }

    @Test
    @DisableFlags({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS})
    public void testRecentsDoesNotContainAllFileTypes() throws Exception {
        bots.roots.openRoot("Recent");

        // When the flag is disabled, DocumentsUI Recents should continue using the old combination
        // of multiple MediaDocumentsProvider and DownloadStorageProvider roots, which means that
        // some file types will not be available.
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_4, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_5, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_6, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_7, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_8, true).exists());
    }

    @Test
    @DisableFlags({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsDoesNotContainEntriesFromAllFilesRootWithSearchV1() throws Exception {
        bots.roots.openRoot("Recent");

        // When SearchV2 is disabled, the old loaders are used: check that they're not picking up
        // anything from the new "all files" root if it's enabled in MediaProvider. If they were,
        // we could see two copies of each file.
        onView(withId(R.id.dir_list))
                .check(
                        matches(
                                DirectoryListBot.withDisplayedFilenameCount(
                                        TestFilesRule.FILE_NAME_2, 1)));
    }

    /**
     * When the feature is enabled (see comment on testRecentsContainsAllFileTypes() for detail on
     * when this is true), we should now see a "Rename" option on files in Recents.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentFilesShowRenameOptions() throws Exception {
        bots.roots.openRoot("Recent");
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_2);

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put("Rename", true);

        bots.menu.assertPresentMenuItems(menuItems);
    }

    /**
     * When the feature is disabled, files from MediaDocumentsProvider in Recents should not have
     * the "Rename" option (as files from MediaDocumentsProvider's images, video and audio Roots
     * don't support renaming).
     */
    @Test
    @DisableFlags({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS})
    public void testRecentFilesDoNotShowRenameOptions() throws Exception {
        bots.roots.openRoot("Recent");
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_2);

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put("Rename", false);
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchInRecents() throws Exception {
        // Create a new, randomly named file so that we can ensure (as much as possible) that
        // search here is really working (as opposed to the test finding the contents of the
        // Recents view _before_ searching) by just finding this file only. This is created in
        // a temporary directory that is cleaned up by TestFilesRule. Use a MIME type that is only
        // found by the new Recents implementation.
        final String testFileName = mTestFilesRule.createRandomFile("application/octet-stream");

        bots.roots.openRoot("Recent");
        bots.search.doSearch(testFileName);

        bots.directory.waitForDocument(testFileName);
        onView(withId(R.id.dir_list))
                .check(matches(DirectoryListBot.withDisplayedFilenameCount(testFileName, 1)));
    }

    @Test
    @DisableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchInRecentsDoesNotContainEntriesFromAllFilesRootWithSearchV1()
            throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        bots.roots.openRoot("Recent");

        // Even pre-M3, DocsUI can run in large screen or small layout, and the way to activate
        // Search in Recent view differs between the two.
        if (bots.search.getSearchIcon() != null) {
            bots.search.expand();
        } else {
            onView(allOf(withId(R.id.searchbar_title), isDisplayed())).perform(click());
        }

        bots.search.setInputText(testFileName);
        bots.keyboard.pressEnter();

        // When SearchV2 is disabled, the old loaders are used: check that they're not picking up
        // anything from the new "all files" root if it's enabled in MediaProvider. If they were,
        // we could see two copies of the file.
        bots.directory.waitForDocument(testFileName);
        onView(withId(R.id.dir_list))
                .check(matches(DirectoryListBot.withDisplayedFilenameCount(testFileName, 1)));
    }

    /** When using the new Search stack, files in Recents are deletable. */
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testDeleteFromRecentsWithSearchV2() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the file can be successfully deleted.
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();
        fileInRecents = bots.directory.findDocument(testFileName, true);
        assertFalse(fileInRecents.exists());
    }

    /** When using the new Search stack, files in Recents are movable. */
    @Test
    @Ignore
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testMoveToInRecentsWithSearchV2() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the random test file is not yet in Downloads.
        bots.roots.openRoot("Downloads");
        try {
            bots.directory.findDocument(testFileName, true);
            fail("File " + testFileName + " must not be present in Downloads yet");
        } catch (UiObjectNotFoundException e) {
            // Working as intended.
        }

        // Move the file to Downloads.
        bots.roots.openRoot("Recent");
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.doMove(
                () -> {
                    bots.roots.openRoot("Downloads");
                });

        // Check: the random test file is now in Downloads.
        bots.roots.openRoot("Downloads");
        UiObject fileInDownloads = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInDownloads.exists());
    }

    /**
     * When using the old search stack, files in Recents are not movable, because
     * MultiRootDocumentsLoader applies NotMovableMaskCursor to all queries, which dynamically
     * rewrites the Document flags to remove FLAG_SUPPORTS_DELETE, FLAG_SUPPORTS_REMOVE etc.
     */
    @Test
    @DisableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_DESKTOP_UX_PHASE_2_RO})
    public void testMoveToInRecentsWithSearchV1() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the "Move to" item is not visible in the context menu.
        bots.roots.openRoot("Recent");
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.openOverflowMenu();

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put(context.getResources().getString(R.string.menu_move), false);
        bots.menu.assertPresentMenuItems(menuItems);
    }

    private Uri createFileInDownloads(String fileName, String mimeType) throws Exception {
        DocumentsProviderHelper storageHelper = mTestFilesRule.docsHelper;
        RootInfo primaryRoot = storageHelper.getRoot(Providers.ROOT_ID_DEVICE);
        DocumentInfo download = storageHelper.findFile(primaryRoot.documentId, "Download");
        assertNotNull(download);
        return storageHelper.createDocument(download.documentId, mimeType, fileName);
    }

    private void deleteFileByUri(Uri fileUri) {
        if (fileUri != null) {
            mTestFilesRule.docsHelper.deleteDocument(fileUri);
        }
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testPathOfSearchResultInRecents() throws Exception {
        // Create a file with a unique name.
        Uri fileUri = null;
        try {
            String newFileName = "0-aardvark.txt";
            fileUri = createFileInDownloads(newFileName, "text/plain");

            // Move to the Recent view and wait for things to quiet down.
            bots.roots.openRoot("Recent");
            device.waitForIdle();

            // Select the newly created file and check the expected path.
            bots.directory.selectDocument(newFileName, 1);

            try {
                // We either have the old behaviro, where the breadcrumb could not get the real path
                // so it showed a path starting with "Recent".
                onView(withId(R.id.breadcrumb_path_holder))
                        .check(bots.breadcrumb.pathStartsWith("Recent"));
            } catch (AssertionFailedError e) {
                // Or we have the new behavior. We use a negative match, because it is hard to
                // predict root title correctly. So we require that we match Download and the
                // newFileName, but the first component must not match "Recent".
                onView(withId(R.id.breadcrumb_path_holder))
                        .check(
                                bots.breadcrumb.pathMatches(
                                        "^(?!Recent$).*", "Download", newFileName));
            }
        } finally {
            deleteFileByUri(fileUri);
        }
    }

    @Test
    public void testSearchRecentUsingVideoChips() throws Exception {
        Uri fileUri = null;
        try {
            String fileName = Long.toHexString(System.currentTimeMillis()) + ".mp4";
            fileUri = createFileInDownloads(fileName, "video/mp4");

            // Move to the Recent view and wait for things to quiet down.
            bots.roots.openRoot("Recent");
            bots.search
                    .clickChip(R.string.chip_title_videos)
                    .perform(new WaitForCheckState(true, 1000L));
            device.waitForIdle();

            // Search debounce delay + extra buffer to let the search settle
            int delayMs = SearchViewManager.SEARCH_DELAY_MS + 250;
            SystemClock.sleep(delayMs);
            // Select the newly created file and check the expected path.
            bots.directory.assertDocumentsPresent(fileName);
        } finally {
            deleteFileByUri(fileUri);
        }
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testDirectoryChangeOnRecentsBreadcrumbClick() throws Exception {
        Uri fileUri = null;
        try {
            String fileName = Long.toHexString(System.currentTimeMillis()) + ".zip";
            fileUri = createFileInDownloads(fileName, "application/zip");
            switchRoot("Recent");

            bots.directory.selectFirstDocument();
            bots.directory.assertSelection(1);

            // Until MediaStore code is propagated to every test device we can either have
            // the old style path Downloads > fileName, in which case the test ends.
            try {
                bots.breadcrumb.pathEqualsTo("Downloads", fileName);
            } catch (AssertionFailedError e) {
                // Or, we have a new style path DeviceName > Download > fileName, in which case
                // clicking on the breadcrumb changes the directory.
                // Click the "Download" item of the path, which should take us to the directory
                // listing.
                onView(
                                allOf(
                                        withText("Download"),
                                        isDescendantOfA(withId(R.id.breadcrumb_path_holder))))
                        .perform(click());

                // Check that we are in the "Download" directory.
                bots.main.assertWindowTitle("Download");
            }

        } finally {
            deleteFileByUri(fileUri);
        }
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testBreadcrumbV2HiddenWhenChangingRoot() throws Exception {
        // Validates that b/475686340 is fixed.
        switchRoot("Recent");
        bots.directory.selectFirstDocument();
        // The selectFirstDocument posts a long click, which results in 20 selection events.
        // We need to give it some time to clear up.
        device.waitForIdle();

        // Verify that breadcrumb v2 shows the path.
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.horizontal_breadcrumb, View.GONE);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.breadcrumb_view_v2, View.VISIBLE);

        // Change root, and check that breadcrumb v2 is hidden, while breadcrumb v1 is visible.
        switchRoot(ROOT_0_ID);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.breadcrumb_view_v2, View.GONE);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.horizontal_breadcrumb, View.VISIBLE);
    }

    /**
     * Ensure that Recents shows files from remote (eg. cloud) Roots when the "all files" and
     * "include remote roots in recents" flags are enabled.
     *
     * <p>Note that this feature requires the enable_media_documents_provider_allfiles_root flag to
     * be enabled in MediaProvider. We cannot force that from these tests. Don't force our flag on
     * (as the test will fail if the MediaProvider feature is disabled), but do test the feature if
     * the flag is on (we will ramp the two flags simultaneously).
     *
     * @throws Exception
     */
    @Test
    @RequiresFlagsEnabled({
        FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
        FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
        FLAG_USE_MATERIAL3,
        FLAG_USE_SEARCH_V2_READ_ONLY
    })
    public void testRecentsContainsRemoteRootItemsWhenAllFilesIsEnabled() throws Exception {
        createTestCloudProviderFileAndAssertPresenceInRecents(true);
    }

    /**
     * Ensure that Recents shows files from remote (eg. cloud) Roots when the "all files" flag is
     * not enabled, but the "include remote roots in recents" flag is.
     *
     * @throws Exception
     */
    @Test
    @DisableFlags({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS})
    @EnableFlags({
        FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
        FLAG_USE_MATERIAL3,
        FLAG_USE_SEARCH_V2_READ_ONLY
    })
    public void testRecentsContainsRemoteRootItemsWhenAllFilesIsDisabled() throws Exception {
        createTestCloudProviderFileAndAssertPresenceInRecents(true);
    }

    /**
     * Ensure Recents does not show files from remote (eg. cloud) Roots when its flag is disabled.
     *
     * @throws Exception
     */
    @Test
    @DisableFlags({FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS})
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsDoesNotContainRemoteRootItemsWhenFlagIsDisabled() throws Exception {
        createTestCloudProviderFileAndAssertPresenceInRecents(false);
    }

    /**
     * Ensure Recents does not show files from remote (eg. cloud) Roots when Searchv2 is disabled.
     *
     * @throws Exception
     */
    @Test
    @DisableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsDoesNotContainRemoteRootItemsWhenSearchV2IsDisabled() throws Exception {
        createTestCloudProviderFileAndAssertPresenceInRecents(false);
    }

    private void createTestCloudProviderFileAndAssertPresenceInRecents(boolean shouldBePresent)
            throws Exception {
        // Create a file with a random name in TestCloudProvider so we can ensure we're seeing it.
        final RootInfo cloudRoot = mCloudDocsHelper.getRoot(TestCloudProvider.ROOT_ID);
        final String fileName = Long.toHexString(System.currentTimeMillis()) + ".txt";
        mCloudDocsHelper.createDocument(cloudRoot, "text/plain", fileName);

        // Is the file present in the root of the provider?
        bots.roots.openRoot("Test Cloud Provider");
        assertTrue(bots.directory.findDocument(fileName, true).exists());

        // Is the file also present in recents?
        bots.roots.openRoot("Recent");
        assertEquals(shouldBePresent, bots.directory.findDocument(fileName, true).exists());
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testReSelectingRootClosesSearch() throws Exception {
        EspressoBotsKt.openRoot(context, "Recent", getActivityLayoutId());
        bots.search.doSearch("query");
        device.waitForIdle();
        // Dropdown options should be visible when search is active.
        bots.search.findDropdownTrigger(R.id.search_location_trigger).check(matches(isDisplayed()));

        // Re-select the Recent view; this should cancel the search.
        EspressoBotsKt.openRoot(context, "Recent", getActivityLayoutId());
        device.waitForIdle();
        onView(withId(R.id.search_location_trigger)).check(matches(not(isDisplayed())));
        bots.search.findChip(R.string.chip_title_images).check(matches(isDisplayed()));
        bots.search.assertSearchIsClosed();
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSelectionBeRestoredAfterConfigurationChange() throws Exception {
        switchRoot("Recent");
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 1);

        // Recreate activity scenario to simulate configuration changes.
        mActivityScenario.recreate();

        // Assert the selection is restored and breadcrumb for the selected file is displayed.
        bots.directory.assertSelection(1);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.breadcrumb_view_v2, View.VISIBLE);
        if (bots.main.inDrawerLayout()) {
            onView(withId(R.id.breadcrumb_top_divider)).check(matches(isDisplayed()));
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_ALLFILES_ROOT_FOR_RECENTS)
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSelectionBeRestoredAfterConfigurationChange_allFilesRoot() throws Exception {
        switchRoot("Recent");
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 1);

        // Recreate activity scenario to simulate configuration changes.
        mActivityScenario.recreate();

        // Assert the selection is restored and breadcrumb for the selected file is displayed.
        bots.directory.assertSelection(1);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.breadcrumb_view_v2, View.VISIBLE);
        // The breadcrumb full path is only supported when the use_allfiles_root_for_recents flag
        // is ON.
        onView(withId(R.id.breadcrumb_path_holder))
                .check(
                        bots.breadcrumb.pathMatches(
                                // The root path could be either "Device label" or
                                // "Virtual SD Card" (on emulator with SD cards).
                                ".*",
                                ExternalStorageProviderTestFilesRule.TEMPORARY_FILES_DIR_NAME,
                                TestFilesRule.FILE_NAME_2));
        if (bots.main.inDrawerLayout()) {
            onView(withId(R.id.breadcrumb_top_divider)).check(matches(isDisplayed()));
        }
    }
}

