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

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.conditions.HasChildCountCondition.hasMoreThanOneChild;
import static com.android.documentsui.conditions.HasChildCountCondition.hasNoChildren;
import static com.android.documentsui.conditions.HasChildCountCondition.hasOneChild;
import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_UX_PHASE_2_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Rect;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.documentsui.actions.WaitForCheckState;
import com.android.documentsui.base.Providers;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
public class SearchViewUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    // UI timeout to wait for elements to appear, set to 5 seconds.
    private final int mTimeout = 5000;

    private String getDeviceLabel() {
        return Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
    }

    @Before
    public void setUpTest() throws UiObjectNotFoundException, RemoteException {
        bots.roots.closeDrawer();

        // wait for a file to be present in default dir.
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
    }

    @After
    public void tearDownTest() {
        // manually close activity to avoid SearchFragment show when Activity close. ref b/142840883
        Assert.assertNotNull(device);
        device.waitForIdle();
        device.pressBack();
        device.pressBack();
        device.pressBack();
    }

    private void assertDefaultContentOfTestDir0() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_2);
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_NO_RENAME);
        bots.directory.assertDocumentsCount(4);
    }

    private void assertDefaultContentOfTestDir1() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_3);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_4);
        bots.directory.assertDocumentsCount(2);
    }

    @Test
    public void testSearchIconVisible() throws Exception {
        // The default root (root 0) supports search
        bots.search.assertIsExpanded(false);
    }

    @Test
    @HugeLongTest
    public void testSearchIconHidden() throws Exception {
        switchRoot(ROOT_1_ID);
        device.waitForIdle();

        // Confirm expected directory has loaded
        assertDefaultContentOfTestDir1();
        bots.search.assertIsVisible(false);
    }

    @Test
    public void testSearchView_ExpandsOnClick() throws Exception {
        bots.search.expand();
        device.waitForIdle();

        bots.search.assertIsExpanded(true);
        bots.search.assertInputFocused(true);

        // FIXME: Matchers fail the not-present check if we've ever clicked this.
        // bots.search.assertIconVisible(false);
    }

    @Test
    public void testSearchView_ShouldHideOptionMenuOnExpanding() throws Exception {
        bots.search.expand();
        device.waitForIdle();

        bots.search.assertIsExpanded(true);
        bots.search.assertInputFocused(true);
        device.waitForIdle();

        assertFalse(bots.menu.hasMenuItem("Grid view"));
        assertFalse(bots.menu.hasMenuItem("List view"));
        assertEquals(
                !bots.search.isFullBarSearchViewEnabled(),
                bots.menu.hasMenuItemByDesc("More options"));
    }

    @Test
    public void testSearchView_CollapsesOnBack() throws Exception {
        bots.search.expand();
        closeSoftKeyboard();

        device.pressBack();

        bots.search.assertIsExpanded(false);
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testSearchFragment_DismissedOnCloseAfterCancel() throws Exception {
        bots.search.doSearch("query text");

        device.pressBack();
        device.waitForIdle();

        bots.search.assertIsExpanded(false);
        bots.search.assertSearchHistoryVisible(false);
    }

    @Test
    public void testSearchView_ClearsSearchOnBack() throws Exception {
        bots.search.doSearch("file2");

        device.pressBack();

        // Wait for a file in the default directory to be listed.
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);

        bots.search.assertIsExpanded(false);
    }

    @Test
    public void testSearchView_ClearsAutoSearchOnBack() throws Exception {
        bots.search.doSearch("chocolate");
        // Wait for auto search result, it should be no results and show holder message.
        bots.directory.waitForHolderMessage();

        device.pressBack();

        bots.search.assertIsExpanded(false);
    }

    @Test
    public void testSearchView_StateAfterSearch() throws Exception {
        bots.search.doSearch("file1");
        device.waitForIdle();

        bots.search.assertInputEquals("file1");
    }

    @Test
    public void testSearch_ResultsFound() throws Exception {
        bots.search.doSearch("file1");

        bots.directory.assertDocumentsCountOnList(true, 2);
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1, TestFilesRule.FILE_NAME_2);
    }

    @Test
    public void testSearch_NoResults() throws Exception {
        bots.search.doSearch("chocolate");

        device.waitForIdle(3000);

        bots.directory.waitAndAssertNoResultsMessage("TEST_ROOT_0");
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnBack() throws Exception {
        bots.search.doSearch(TestFilesRule.FILE_NAME_1);

        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchNoResults_ClearsOnBack() throws Exception {
        bots.search.doSearch("chocolate bunny");

        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnDirectoryChange() throws Exception {
        // Skipping this test for phones since currently there's no way to open the drawer on
        // phones after doing a search (it's a back button instead of a hamburger button)
        if (!bots.main.inFixedLayout()) {
            return;
        }

        bots.search.doSearch(TestFilesRule.FILE_NAME_1);

        switchRoot(ROOT_1_ID);
        device.waitForIdle();
        assertDefaultContentOfTestDir1();

        switchRoot(ROOT_0_ID);
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testSearchHistory_showAfterSearchViewClear() throws Exception {
        bots.search.doSearch("chocolate");

        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();

        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testSearchView_focusClearedAfterSelectingSearchHistory() throws Exception {
        String queryText = "history";
        bots.search.doSearch(queryText);
        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();
        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);

        bots.search.clickSearchHistory(queryText);
        bots.search.assertInputFocused(false);
        bots.search.assertSearchHistoryVisible(false);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchDropdowns() throws Exception {
        bots.search.doSearch("foo");
        // Verify that menu triggers (chips) are showing.
        bots.main.assertLocationTriggerShows();
        bots.main.assertLastModifiedTriggerShows();
        bots.main.assertFileTypeTriggerShows();
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2FileTypeDropdown() throws Exception {
        // Start search with term "file1" limiting results to images only.
        bots.search.doSearch("file");
        // Select images files only.
        bots.search.clickDropdownTrigger(R.id.search_file_type_trigger);
        bots.search.clickMenuItem(R.string.chip_title_images);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);

        // There should be only file12.png left.
        device.waitForIdle();
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), 5000);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedDropdown() throws Exception {
        // Start search with term "file1" limiting results modified in the last 30 days.
        bots.search.doSearch("file");
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_30_days);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.assertDocumentsCountOnList(true, 3);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2SearchLocationDropdown() throws Exception {
        // Open a root that does not have the file we are searching for.
        switchRoot("Paging Root");
        // Start search with term "file1.log", but rather than searching locally, search everywhere.
        bots.search.doSearch("file1.log");
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);

        // Click Everywhere, to search everywhere.
        bots.search.clickMenuItem(R.string.search_location_everywhere);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.waitForDocument("file1.log");
        bots.directory.assertDocumentsCountOnList(true, 1);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2RootNameIsAdjusted() throws Exception {
        // The test starts in TEST_ROOT_0
        bots.search.doSearch("-no-such-file-");
        device.waitForIdle();

        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        // Check that the text in the dropdown window.
        bots.menu
                .findListMenuItem(context.getString(R.string.search_location_everywhere))
                .check(matches(isDisplayed()));
        onView(withText("TEST_ROOT_0")).inRoot(isPlatformPopup()).check(matches(isDisplayed()));
        // Click the "Everywhere" entry to hide the popup. This is needed for the bots to be able
        // to open the new root. But we also test that user choices are remembered.
        bots.search.clickMenuItem(R.string.search_location_everywhere);

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move to a different root.
        switchRoot("Paging Root");
        // Make sure the directory is loaded.
        bots.directory.waitForDocument("00000");

        // Start search, again.
        bots.search.doSearch("-no-such-file-");
        device.waitForIdle();

        // Verify that the location shows the name of the new root.
        bots.search
                .findDropdownTrigger(R.id.search_location_trigger)
                .check(matches(withText("Paging Root")));

        // Click location trigger, and check that the root folder option is updated to Downloads.
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        // Verify the dropdown menu to be updated.
        bots.menu
                .findListMenuItem(context.getString(R.string.search_location_everywhere))
                .check(matches(isDisplayed()));
        onView(withText("Paging Root")).inRoot(isPlatformPopup()).check(matches(isDisplayed()));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedDropdownVisibility() throws Exception {
        // Starts in TEST_ROOT_0. Start search and expect last modified dropdown to be visible.
        bots.search.doSearch("-no-such-file-");
        device.waitForIdle();
        bots.search.findDropdownTrigger(R.id.search_last_modified_trigger).check(
                matches(isDisplayed()));

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move to the Recents view and expect the last modified to be gone.
        switchRoot("Recent");
        bots.search.doSearch("-no-such-file-");
        device.waitForIdle();
        onView(withId(R.id.search_last_modified_trigger))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move Downloads, repeat search, and expect the last modified trigger to be again visible.
        switchRoot("Downloads");
        bots.search.doSearch("-no-such-file-");
        device.waitForIdle();
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(
                        matches(
                                allOf(
                                        isDisplayed(),
                                        withText(R.string.search_last_modified_any_time))));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedOptionKeepsUserChoice() throws Exception {
        // Move to the Recents view and expect the last modified to be gone.
        switchRoot("Recent");
        bots.search.doSearch("1");
        device.waitForIdle();

        // Click Everywhere, so that the recency choices are revealed.
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        bots.search.clickMenuItem(R.string.search_location_everywhere);

        // Now simulate the user choosing "Last week" for the modified option.
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_7_days);

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        device.waitForIdle();

        // Repeat searching: it should still show last week modified option in recents.
        bots.search.doSearch("2");
        // Click Everywhere, so that the recency choices are revealed.
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        bots.search.clickMenuItem(R.string.search_location_everywhere);
        device.waitForIdle();

        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(
                        matches(
                                allOf(
                                        isDisplayed(),
                                        withText(R.string.search_last_modified_7_days))));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    @Ignore("b/454313609") // TODO(b/454313609): Re-enable once the test is fixed.
    public void testSearchV2LastUsedChipCopiedToFileTypeDropdown() throws Exception {
        // Click "Images" chip and wait until the chip becomes selected.
        bots.search.clickChip(R.string.chip_title_images)
                .perform(new WaitForCheckState(true, mTimeout));

        // Start search. Search text is not important.
        final String query = "irrelevant";
        bots.search.doSearch(query);

        // Verify that File Type trigger shows "Images" text.
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_images)));

        // Clear the search text, to go back to directory listing, and wait for chips to show.
        bots.search.clickSearchViewClearButton();

        // Select Documents and Audio chips, in this order.
        bots.search.clickChip(R.string.chip_title_documents)
                .perform(new WaitForCheckState(true, mTimeout));
        bots.search.clickChip(R.string.chip_title_audio)
                .perform(new WaitForCheckState(true, mTimeout));

        bots.search.doSearch(query);
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_audio)));

        // Clear the query again, to go back to chips.
        bots.search.clickSearchViewClearButton();
        // Uncheck Audio, and expect now Documents to be checked in file type dropdowns.
        bots.search.clickChip(R.string.chip_title_audio)
                .perform(new WaitForCheckState(false, mTimeout));

        // Enter the search query again, and verify that Documents file type is selected.
        bots.search.doSearch(query);
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_documents)));
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testSearchView_TogglingASearchChipClearsSelection() throws Exception {
        // Get the label of the device (this will be used to navigate to the ExternalStorageProvider
        // as the custom roots added for test do not show the search chips).
        String deviceLabel = getDeviceLabel();

        // Open the root and select the DCIM folder for selection.
        switchRoot(deviceLabel);
        bots.directory.selectDocument("DCIM", 1);

        // Click on the Images search chips.
        bots.search.clickChip(R.string.chip_title_images);

        // Ensure the selection has cleared and the "1 file selected" text is not displayed.
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), mTimeout);
        device.wait(Until.gone(By.text("1 selected")), mTimeout);
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3) // TODO(b/412895530): Enable for `use_material3` once fixed.
    public void testSelectionWhileSearchingHidesSearchBar() throws UiObjectNotFoundException {
        String pkg = bots.directory.mTargetPackage;

        // The `mTestFilesRule` creates more than 1 file, so ensure that that is the case before
        // proceeding.
        UiObject2 directoryList = device.findObject(By.res(pkg + ":id/dir_list"));
        directoryList.wait(hasMoreThanOneChild(), mTimeout);

        // Click the search icon and wait until the only result is the file that was searched for.
        bots.search.doSearch(TestFilesRule.FILE_NAME_1);
        directoryList.wait(hasOneChild(), mTimeout);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);

        // Select the document. This implicitly verifies that the bar at the top shows the text "1
        // selected" which, in all conditions, occludes the search input box.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Deselect the document and click the clear button on the search view (if the selection bar
        // at the top is visible this won't be possible).
        bots.directory.clearSelection();
        bots.search.clickSearchViewClearButton();
        device.wait(Until.findObject(By.res(pkg + ":id/history_list")), mTimeout);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testUnhandledRootsReturnEmptyCursors() throws UiObjectNotFoundException {
        String pkg = bots.directory.mTargetPackage;

        // Use Paging Root, as it throws:
        //     java.lang.UnsupportedOperationException: Search not supported
        switchRoot("Paging Root");
        bots.search.doSearch("00");
        UiObject2 directoryList = device.findObject(By.res(pkg + ":id/dir_list"));
        directoryList.wait(hasNoChildren(), mTimeout);
        device.wait(Until.gone(By.displayId(R.id.progressbar)), mTimeout);
    }

    /**
     * Checks that we do not start searching until a non-null, not empty query is entered. This test
     * is limited to Search V2, as V1 shows a view with past search queries that hides the directory
     * listing. So while both searches behave in the same way, we can reliably verify it only in V2.
     */
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testEmptyQueryShowsDirectoryListing() throws UiObjectNotFoundException {
        // Assert that we are in the correct location.
        bots.breadcrumb.assertItemsPresent(ROOT_0_ID);
        // Check the default content.
        assertDefaultContentOfTestDir0();
        // Open search, make sure query input field has focus.
        bots.search.expand();
        bots.search.assertInputFocused(true);
        // Try to hide the virtual keyboard; on small devices it can hide some files.
        Espresso.closeSoftKeyboard();
        // Check that the content of the current directory has not changed.
        assertDefaultContentOfTestDir0();
        // Enter an empty query.
        bots.search.setInputText("");
        Espresso.closeSoftKeyboard();
        // Check that the content of the current directory has not changed.
        assertDefaultContentOfTestDir0();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchVisibleInSearchableRootsOnly() throws UiObjectNotFoundException {
        // Starting in ROOT_ID_0, which is searchable.
        assertNotNull("Icon should be visible in ROOT_0_ID", bots.search.getSearchIcon());
        // Broken root cannot be searched.
        switchRoot("Broken Root Doc");
        assertNull("Icon should not be visible ini Broken Root Doc", bots.search.getSearchIcon());
        // Device root should be searchable.
        String deviceLabel = getDeviceLabel();
        switchRoot(deviceLabel);
        assertNotNull("Icon should be visible in " + deviceLabel, bots.search.getSearchIcon());
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchViewCollapsedOnSmallScreen() {
        assertNotNull(mActivityScenario);
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity);
            Rect bounds = TestUtils.Companion.getActivityBounds(activity);
            Assume.assumeTrue(
                    "Skipping test: window size " + bounds.width() + "dp x " + bounds.height()
                            + "dp  is larger than 900dp x 600dp",
                    bounds.width() < 900.0 && bounds.height() < 600.0
            );
        });

        String pkg = bots.directory.mTargetPackage;
        UiObject2 searchBar = device.findObject(By.res(pkg + ":id/search_bar"));
        assertNull(searchBar);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchViewExpandedOnLargeScreen() {
        assertNotNull(mActivityScenario);
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity);
            Rect bounds = TestUtils.Companion.getActivityBounds(activity);
            Assume.assumeTrue(
                    "Skipping test: window size " + bounds.width() + "dp x " + bounds.height()
                            + "dp  is smaller than 1000dp x 700dp",
                    bounds.width() >= 1000.0 && bounds.height() >= 700.0
            );
        });

        String pkg = bots.directory.mTargetPackage;
        UiObject2 searchBar = device.findObject(By.res(pkg + ":id/docked_search_text"));
        assertNotNull(searchBar);
        assertTrue(searchBar.isEnabled());

        // In an expanded search view, when searching, we should see "Search results".
        try {
            bots.search.doSearch("a");
            device.waitForIdle();
            onView(withText(R.string.search_results)).check(matches(isDisplayed()));
        } catch (UiObjectNotFoundException e) {
            fail("Failed to execute a search for 'a' due to " + e.getMessage());
        }
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testPathOfSearchResultSingleSelection() throws Exception {
        bots.search.doSearch("file");
        device.waitForIdle();

        // Click file1.log; check that one element is selected.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.directory.assertSelection(1);

        // Verify that the breadcrumb path shows the correct information.
        onView(withId(R.id.breadcrumb_path_holder))
                .check(bots.breadcrumb.pathEqualsTo("TEST_ROOT_0", TestFilesRule.FILE_NAME_1));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testDirectoryChangedOnSearchBreadcrumbClick() throws Exception {
        bots.search.doSearch("file");
        bots.directory.findDocument(TestFilesRule.DIR_NAME_1).waitUntilGone(mTimeout);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        // Click the first item of the path, which should take us to the directory listing.
        onView(allOf(withText("TEST_ROOT_0"), isDescendantOfA(withId(R.id.breadcrumb_path_holder))))
                .perform(click());
        // Wait for the directory, previously filtered out by search, to re-appear.
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testPathOfSearchResultMultipleSelection() throws Exception {
        bots.search.doSearch("file");
        device.waitForIdle();

        // Click file1.log and NO_RENAMEfile.txt which should stop breadcrumb path.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_NO_RENAME, 2);

        onView(withId(R.id.breadcrumb_path_holder)).check(bots.breadcrumb.pathEqualsTo());
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_DESKTOP_UX_PHASE_2_RO})
    public void testSearchResultHidesNonDesktopFolders() throws Exception {
        DocumentsProviderHelper rootStorageDocsHelper = new DocumentsProviderHelper(userId,
                Providers.AUTHORITY_STORAGE, context,
                Providers.AUTHORITY_STORAGE);
        String testFileName = "showHideTest-" + UUID.randomUUID() + ".txt";
        Uri androidFolderUri = DocumentsContract.buildDocumentUri(Providers.AUTHORITY_STORAGE,
                Providers.ROOT_ID_DEVICE + ":Android");
        Uri testFileUri = null;
        try {
            // Create a test file inside the Android folder.
            testFileUri = rootStorageDocsHelper.createDocument(androidFolderUri, "text/plain",
                    testFileName);

            // Reset show/hide state to hide hidden files before the test.
            bots.main.hideHiddenFilesIfNeeded();

            // Open device root: the internal storage.
            String deviceRootLabel = getDeviceLabel();
            switchRoot(deviceRootLabel);

            // Search the test file.
            bots.search.doSearch(testFileName);

            // Assert there's no search result because the Android folder and its content are
            // hidden.
            bots.directory.waitAndAssertNoResultsMessage(deviceRootLabel);

            // Now show hidden files, the test file should show. (Close the search first before
            // showing hidden files because the 3-dot menu is not visible on drawer/nav_rail layout
            // when search is active.)
            bots.search.closeSearch();
            // Wait for the search to be canceled so the context menu is fully updated before
            // clicking.
            device.waitForIdle();
            bots.main.showHiddenFiles();
            bots.search.doSearch(testFileName);
            bots.directory.waitForDocument(testFileName);

            // Now hide hidden files, the test file should disappear. (Close the search first before
            // hiding hidden files because the 3-dot menu is not visible on drawer/nav_rail layout
            // when search is active.)
            bots.search.closeSearch();
            // Wait for the search to be canceled so the context menu is fully updated before
            // clicking.
            device.waitForIdle();
            bots.main.hideHiddenFiles();
            bots.search.doSearch(testFileName);
            bots.directory.waitAndAssertNoResultsMessage(deviceRootLabel);
        } finally {
            // Delete the created test file if it exists.
            if (testFileUri != null) {
                try {
                    DocumentsContract.deleteDocument(context.getContentResolver(), testFileUri);
                } catch (Exception e) {
                    // Ignore cleanup errors.
                }
            }
        }
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testKeyboardShortcutFocusesDockedSearchBar() throws Exception {
        Assume.assumeTrue(
                "Skipping test: docked search bar is not shown.", bots.search.showsDockedSearch());

        bots.search.assertInputFocused(false);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_SEARCH);
        bots.search.assertInputFocused(true);
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testTabNavigationWithDockedSearchBar() throws Exception {
        Assume.assumeTrue(
                "Skipping test: docked search bar is not shown.", bots.search.showsDockedSearch());

        // The button next to the docked search bar is the list or grid button, switch to list mode
        // before the test so we can assert the next focused view is the grid button.
        bots.main.switchToListMode();

        // Click the docked search bar.
        bots.search.expand();
        closeSoftKeyboard();

        // Assert it should get the focus.
        bots.search.assertInputFocused(true);

        // Press tab to move the focus.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);

        // Assert that the focus should go to the grid button.
        bots.search.assertInputFocused(false);
        onView(withId(R.id.sub_menu_grid)).check(matches(hasFocus()));
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testTabNavigationWithDockedSearchBar_withQuery() throws Exception {
        Assume.assumeTrue(
                "Skipping test: docked search bar is not shown.", bots.search.showsDockedSearch());

        // The button next to the docked search bar is the list or grid button, switch to list mode
        // before the test so we can assert the next focused view is the grid button.
        bots.main.switchToListMode();

        // Click the docked search bar and type something. (Do ont use doSearch() here because
        // pressEnter() will change the focus.
        bots.search.expand();
        bots.search.setInputText("a");
        closeSoftKeyboard();

        // Assert it should get the focus.
        bots.search.assertInputFocused(true);

        // Press tab to move the focus.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);

        // Assert that the focus should go to the close search button.
        bots.search.assertInputFocused(false);
        onView(withId(R.id.docked_search_clear)).check(matches(hasFocus()));

        // Press tab to move the focus.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);

        // Assert that the focus should go to the grid button.
        onView(withId(R.id.sub_menu_grid)).check(matches(hasFocus()));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testRecreatePreservesSearchState() throws Exception {
        String[] expectedMatches =
                new String[] {
                    TestFilesRule.FILE_NAME_1,
                    TestFilesRule.FILE_NAME_2,
                    TestFilesRule.FILE_NAME_NO_RENAME,
                };

        // Search and expect 3 files to match.
        bots.search.doSearch("file");
        device.waitForIdle();
        bots.directory.assertDocumentsPresent(expectedMatches);
        // Verify that only dropdown options are shown, and not chips.
        bots.search.findDropdownTrigger(R.id.search_location_trigger).check(matches(isDisplayed()));
        onView(withId(R.id.search_chip_group)).check(matches(not(isDisplayed())));

        // Relaunch the app, and expect the same result. Also, this must never crash.
        mActivityScenario.recreate();
        // Close the keyboard because it might appear after activity recreation.
        closeSoftKeyboard();
        device.waitForIdle();
        bots.directory.assertDocumentsPresent(expectedMatches);
        bots.search.assertInputEquals("file");
        // Verify that only dropdown and chips states are preserved.
        bots.search.findDropdownTrigger(R.id.search_location_trigger).check(matches(isDisplayed()));
        onView(withId(R.id.search_chip_group)).check(matches(not(isDisplayed())));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testOptionsChangeTriggersSearch() throws Exception {
        // Check that we have .log, .png, and .txt files visible.
        bots.directory.assertDocumentsPresent(
                TestFilesRule.FILE_NAME_1,
                TestFilesRule.FILE_NAME_2,
                TestFilesRule.FILE_NAME_NO_RENAME);

        // Trigger search for images only.
        bots.search
                .clickChip(R.string.chip_title_images)
                .perform(new WaitForCheckState(true, mTimeout));

        bots.directory.findDocument(TestFilesRule.FILE_NAME_NO_RENAME).waitUntilGone(mTimeout);
        bots.directory.assertDocumentsPresent(TestFilesRule.FILE_NAME_2);

        // Uncheck images chip.
        bots.search
                .clickChip(R.string.chip_title_images)
                .perform(new WaitForCheckState(false, mTimeout));
        // Wait for other files to re-appear (just checking one of the files that is gone).
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_NO_RENAME);

        // Start a regular search.
        bots.search.doSearch("file");
        // Wait for search to complete ("Dir1" should disappear).
        bots.directory.findDocument(TestFilesRule.DIR_NAME_1).waitUntilGone(mTimeout);

        // Check that .log, .png, and .txt files are again visible.
        bots.directory.assertDocumentsPresent(
                TestFilesRule.FILE_NAME_1,
                TestFilesRule.FILE_NAME_2,
                TestFilesRule.FILE_NAME_NO_RENAME);

        // Trigger a type dropdown and select images.
        bots.search.clickDropdownTrigger(R.id.search_file_type_trigger);
        bots.search.clickMenuItem(R.string.chip_title_images);

        // Wait for .txt file to be gone and check that png file is present.
        bots.directory.findDocument(TestFilesRule.FILE_NAME_NO_RENAME).waitUntilGone(mTimeout);
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_NO_RENAME);
        bots.directory.assertDocumentsPresent(TestFilesRule.FILE_NAME_2);
    }

    /** Change the dark/light theme and wait for the device to settle. */
    private void changeNightMode(String mode) {
        CountDownLatch latch = new CountDownLatch(1);
        ActivityLifecycleCallback callback =
                (activity, stage) -> {
                    if (activity instanceof FilesActivity && stage == Stage.RESUMED) {
                        latch.countDown();
                    }
                };
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);

        try {
            try (ParcelFileDescriptor ignored =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand("cmd uimode night " + mode)) {
                // Use try-with-resources to auto-close the ParcelFileDescriptor and prevent a file
                // descriptor leak. The command output is ignored.
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                latch.await(mTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
        }

        device.waitForIdle();
    }

    @Test
    public void testSearchRetainsFocusOnConfigurationChange() throws UiObjectNotFoundException {
        try {
            changeNightMode("yes");
            bots.search.expand();

            changeNightMode("no");
            bots.search.assertIsExpanded(true);
        } finally {
            changeNightMode("auto");
        }
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testClickingRootAfterSearchListsRootsFiles() throws UiObjectNotFoundException {
        // Verify that the files in the current root we expect to see later are here at the start.
        bots.directory.assertDocumentsPresent(
                TestFilesRule.FILE_NAME_1,
                TestFilesRule.FILE_NAME_2,
                TestFilesRule.FILE_NAME_NO_RENAME);
        // Change to the Recent view and run a search. Any root would do, but we are certain
        // Recent root exists.
        switchRoot("Recent");
        // Run any search, the results do not matter.
        bots.search.doSearch("foo");
        device.waitForIdle();
        // Now change back to TEST_ROOT_0. This must result in a regular file listing.
        switchRoot(ROOT_0_ID);
        // Give the app time to list the directory.
        device.waitForIdle();
        bots.directory.assertDocumentsPresent(
                TestFilesRule.FILE_NAME_1,
                TestFilesRule.FILE_NAME_2,
                TestFilesRule.FILE_NAME_NO_RENAME);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedOptionIsSticky() throws Exception {
        // Enters a search query and checks that the search_last_modified_trigger shown "Any time"
        // text.
        bots.search.doSearch("query1");
        device.waitForIdle();
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_any_time)));

        // Then selects in the search_last_modified_menu the
        // search_last_modified_7_days_option.
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_7_days);
        device.waitForIdle();

        // Next, it closes the search, and enters a new query.
        bots.search.closeSearch();
        device.waitForIdle();
        bots.search.doSearch("query2");
        device.waitForIdle();

        // It then checks that the search_last_modified_7_days_options is selected.
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_7_days)));

        // It closes the search again.
        bots.search.closeSearch();
        device.waitForIdle();

        // It selects the Recent root.
        switchRoot("Recent");

        // Enter the another search query and checks that the search_last_modified_trigger shows
        // "Last month" text.
        bots.search.doSearch("another query");
        device.waitForIdle();
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_30_days)));

        // Next the search changes the search_last_modified_menu to have last_modified_2_days
        // option selected.
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_2_days);
        device.waitForIdle();

        // it closes the search.
        bots.search.closeSearch();
        device.waitForIdle();

        // Then enters another search query and verifies that the search_last_modified_trigger
        // shows search_last_modified_2_days string.
        bots.search.doSearch("yet another query");
        device.waitForIdle();
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_2_days)));

        // Go back to Downloads and verify that we are back to Any time.
        switchRoot("Downloads");
        bots.search.doSearch("last query");
        device.waitForIdle();
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_any_time)));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testRootReselectionDoesNotClobberDocumentStack() throws Exception {
        // Validates that b/474153259 is fixed.

        // Select Dir1 folder, and click on the root. This triggers onRootPicked, which
        // before the fix would clobber the stack.
        bots.directory.selectDocument(TestFilesRule.DIR_NAME_1, 1);
        switchRoot(ROOT_0_ID);
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);

        // Clear the selection, and enter the new directory. Check the breadcrumb.
        bots.directory.clearSelection();
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1);
        bots.breadcrumb.assertItemsPresent(ROOT_0_ID, TestFilesRule.DIR_NAME_1);

        // Open TEST_ROOT_0 again. This should show the root directory, which includes the test
        // folder.
        switchRoot(ROOT_0_ID);
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchDropdownsHiddenWhenChangingRoot() throws Exception {
        bots.search.doSearch(TestFilesRule.FILE_NAME_1);
        // Wait for the search to be triggered and then completed.
        int delayMs = SearchViewManager.SEARCH_DELAY_MS + 250;
        SystemClock.sleep(delayMs);
        // Select the first item. We are guaranteed to have at least one of them due to using
        // a name of a known, existing file.
        bots.directory.selectFirstDocument();
        // Move to recents without clearing the query or the selection.
        switchRoot("Recent");
        // Here we just check one dropdown for being hidden as they all work in sync.
        bots.main.assertLocationTriggerHidden();
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testDropdownOptionsPreserved() throws Exception {
        bots.search.doSearch("file");
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        bots.search.clickMenuItem(R.string.search_location_everywhere);
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_365_days);
        bots.search.clickDropdownTrigger(R.id.search_file_type_trigger);
        bots.search.clickMenuItem(R.string.chip_title_images);
        device.waitForIdle();

        // Relaunch the app, and expect query and dropdown option to keep their state.
        mActivityScenario.recreate();
        // Close the keyboard because it mgiht appear after activity recreation.
        closeSoftKeyboard();
        device.waitForIdle();
        bots.search.assertInputEquals("file");
        bots.search
                .findDropdownTrigger(R.id.search_location_trigger)
                .check(matches(withText(R.string.search_location_everywhere)));
        bots.search
                .findDropdownTrigger(R.id.search_last_modified_trigger)
                .check(matches(withText(R.string.search_last_modified_365_days)));
        bots.search
                .findDropdownTrigger(R.id.search_file_type_trigger)
                .check(matches(withText(R.string.chip_title_images)));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSelectionPreservedOnSearchResult() throws Exception {
        bots.search.doSearch("file");

        // Select one file from the search result.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Relaunch the app, and expect the previously selected file is still selected.
        mActivityScenario.recreate();
        // Close the keyboard because it might appear after activity recreation.
        closeSoftKeyboard();

        bots.directory.assertSelection(1);
        bots.breadcrumb.waitForBreadcrumbVisibility(R.id.breadcrumb_view_v2, View.VISIBLE);
        onView(withId(R.id.breadcrumb_path_holder))
                .check(bots.breadcrumb.pathEqualsTo(ROOT_0_ID, TestFilesRule.FILE_NAME_1));
    }
}
