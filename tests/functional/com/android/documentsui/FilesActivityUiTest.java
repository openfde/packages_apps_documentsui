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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;
import static com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO;
import static com.android.documentsui.flags.Flags.FLAG_SINGLE_CLICK_TO_SELECT;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.Material3Config.getRes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Locale;
import java.util.UUID;

@LargeTest
public class FilesActivityUiTest extends ActivityTestJunit4<FilesActivity> {
    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                final Uri dir1 =
                                        docsHelper.createFolder(root, TestFilesRule.DIR_NAME_1);
                                docsHelper.createFolder(dir1, TestFilesRule.CHILD_DIR_1);
                                docsHelper.createDocument(root, "text/plain", "file0.log");
                                docsHelper.createDocument(root, "image/png", "file1.png");
                                docsHelper.createDocument(root, "text/csv", "file2.csv");
                                docsHelper.createDocument(root, "text/plain", "anotherFile0.log");
                                docsHelper.createDocument(root, "text/plain", "poodles.text");
                            });

    // Recents is a strange meta root that gathers entries from other providers.
    // It is special cased in a variety of ways, which is why we just want
    // to be able to click on it.
    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testClickRecent() throws Exception {
        switchRoot("Recent");

        boolean showSearchBar = context.getResources().getBoolean(R.bool.show_search_bar);
        if (showSearchBar) {
            bots.main.assertSearchBarShow();
        } else {
            bots.main.assertSearchBarGone();
            bots.search.assertIconVisible(true);
            bots.main.assertWindowTitle("Recent");
        }
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testClickRecentM3() throws Exception {
        switchRoot("Recent");

        bots.main.assertSearchBarGone();
        boolean showDockedSearch = context.getResources().getBoolean(
                getRes(R.bool.show_docked_search));
        if (showDockedSearch) {
            bots.main.assertDockedSearchBarShow();
        } else {
            bots.main.assertOptionsMenuSearchShow();
        }
        bots.main.assertWindowTitle("Recent");
    }

    private DocumentsProviderHelper setupStorageAuthorityDocsHelper() throws Exception {
        return DocumentsProviderHelper.setupStorageAuthorityDocsHelper(context);
    }

    private void cleanupFile(String fileName, String primaryRootTitle,
            @Nullable String parentDirName) throws UiObjectNotFoundException {
        switchRoot(primaryRootTitle);
        if (parentDirName != null) {
            bots.directory.openDocument(parentDirName);
        }

        bots.directory.waitForDocument(fileName, /* withScroll= */ true);
        bots.directory.selectDocument(fileName, 1);

        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        bots.directory.findDocument(fileName).waitUntilGone(5000);
        assertFalse(bots.directory.hasDocuments(fileName));
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootClick_SetsWindowTitle() throws Exception {
        switchRoot("Images");
        bots.main.assertWindowTitle("Images");
    }

    @Test
    public void testFilesListed() throws Exception {
        bots.directory.assertDocumentsVisible("file0.log", "file1.png", "file2.csv");
    }

    @Test
    public void testFilesList_LiveUpdate() throws Exception {
        // Minimize the chances of the files being invisible.
        bots.main.switchToListMode();

        // Create a file with a unique name.
        RootInfo root = mTestFilesRule.docsHelper.getRoot(ROOT_0_ID);
        String newFileName = "mxuadkjf.txt";
        mTestFilesRule.docsHelper.createDocument(root, "text/plain", newFileName);

        bots.directory.waitForDocument(newFileName);
        // Documents should be present, but may not necessary be visible on small screen.
        bots.directory.assertDocumentsPresent("file0.log", "file1.png", "file2.csv", newFileName);
    }

    @DesktopTest(cujs = {"b/434068747"})
    @Test
    public void testNavigate_byBreadcrumb() throws Exception {
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.CHILD_DIR_1);  // wait for known content
        bots.directory.assertDocumentsVisible(TestFilesRule.CHILD_DIR_1);

        device.waitForIdle();
        bots.breadcrumb.assertItemsPresent(TestFilesRule.DIR_NAME_1, "TEST_ROOT_0");

        bots.breadcrumb.clickItem("TEST_ROOT_0");
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
    }

    @Test
    public void testNavigate_inFixedLayout_whileHasSelection() throws Exception {
        if (bots.main.inFixedLayout()) {
            switchRoot(mTestFilesRule.getRoot(ROOT_0_ID).title);
            bots.directory.selectDocument("file0.log", 1);

            // ensure no exception is thrown while navigating to a different root
            switchRoot(mTestFilesRule.getRoot(ROOT_1_ID).title);
        }
    }

    @Test
    public void testNavigationToInspector() throws Exception {
        if(!features.isInspectorEnabled()) {
            return;
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                InspectorActivity.class.getName(), null, false);
        bots.directory.selectDocument("file0.log", 1);
        bots.main.clickActionItem("Get info");
        monitor.waitForActivityWithTimeout(TIMEOUT);
    }

    @Test
    @HugeLongTest
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_UpdatesSortHeader() throws Exception {

        // switch to separate display modes for two separate roots. Each
        // mode has its own distinct sort header. This should be remembered
        // by files app.
        switchRoot("Images");
        bots.main.switchToGridMode();
        switchRoot("Videos");
        bots.main.switchToListMode();

        // Now switch back and assert the correct mode sort header mode
        // is restored when we load the root with that display mode.
        switchRoot("Images");
        bots.sort.assertHeaderHide();
        if (bots.main.inFixedLayout()) {
            switchRoot("Videos");
            bots.sort.assertHeaderShow();
        } else {
            switchRoot("Videos");
            bots.sort.assertHeaderHide();
        }
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_NonM3PerRootViewModeState() throws Exception {
        // Assign different view modes across "Images" and "Videos" roots.
        // Images root --> grid mode
        // Videos root --> list mode
        switchRoot("Images");
        bots.main.switchToGridMode();
        bots.main.assertInGridMode();
        switchRoot("Videos");
        bots.main.switchToListMode();
        bots.main.assertInListMode();

        // Assert that the different roots maintain their respective view modes.
        switchRoot("Images");
        bots.main.assertInGridMode();
        switchRoot("Videos");
        bots.main.assertInListMode();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_M3GlobalViewModeState() throws Exception {
        switchRoot("Recent");
        bots.main.switchToGridMode();
        bots.main.assertInGridMode();

        // Switch to a different root and assert still in grid mode.
        switchRoot(ROOT_0_ID);
        bots.main.assertInGridMode();

        // Switch back to list mode and assert still in list mode on a different root.
        bots.main.switchToListMode();
        bots.main.assertInListMode();
        switchRoot("Recent");
        bots.main.assertInListMode();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testClearSelectionInRecentsResetsActions() throws Exception {
        // Ensure Downloads exists and get the location of the main root (e.g. "Pixel Tablet").
        DocumentsProviderHelper storageDocsHelper = setupStorageAuthorityDocsHelper();
        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);
        DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");

        // Create a file in "Download".
        final String fileName = "recent_" + System.currentTimeMillis() + ".txt";
        storageDocsHelper.createDocument(info.documentId, "text/plain", fileName);

        // Navigate to "Download" and ensure the file exists (this should ensure it also exists in
        // Recent).
        switchRoot(primaryRoot.title);
        bots.directory.openDocument("Download");
        bots.directory.waitForDocument(fileName, /* withScroll= */ true);

        // Open Recent and wait for the document to appear.
        switchRoot("Recent");
        bots.directory.waitForDocument(fileName);

        try {
            // The search option menu shows up when no items are selected, use this as a proxy for
            // the options menu being refreshed (it should only show when a file is selected).
            onView(withId(R.id.action_menu_share)).check(doesNotExist());

            // Select the first document in Recents that has a selectable region. We have previously
            // staged a file so there should be at least 1, but depending on the device and the
            // cleanup of the device this might not always be the case. The document that gets
            // selected doesn't really matter, just that one is selected.
            bots.directory.selectFirstDocument();
            onView(withId(R.id.action_menu_share)).check(matches(isDisplayed()));

            // Deselect the file and ensure the share menu disappears (this ensures the menu is
            // refreshed).
            bots.directory.clearSelection();
            device.wait(Until.gone(By.desc("Share")), /* timeout= */ 5000);
        } finally {
            cleanupFile(fileName, primaryRoot.title, "Download");
        }
    }

    @Test
    public void testRecentsShowsZipFiles() throws Exception {
        DocumentsProviderHelper storageDocsHelper = setupStorageAuthorityDocsHelper();
        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);

        String createdFileName = null;
        try {
            DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");
            assertNotNull(info);
            switchRoot("Downloads");

            // Create a zip file in "Download" folder. Since we are creating a file in the Download
            // folder, create a unique name that has little to no chance of colliding with actual
            // user files.
            createdFileName = "a_zip_test_" + UUID.randomUUID() + ".zip";
            storageDocsHelper.createDocument(info.documentId, "application/zip", createdFileName);
            bots.directory.waitForDocument(createdFileName);

            // Open Recent and wait for the newly created files to appear. We limit searches to just
            // this week to make the test run more efficiently.
            switchRoot("Recent");

            // Verify that just created zip file appears among recent files. It should appear on top
            // so no scrolling.
            assertTrue(bots.directory.findDocument(createdFileName).exists());
        } finally {
            if (createdFileName != null) {
                cleanupFile(createdFileName, primaryRoot.title, "Download");
            }
        }
    }

    @Test
    @EnableFlags(FLAG_SINGLE_CLICK_TO_SELECT)
    public void testSingleClickToSelect_enabled() throws Exception {
        doTestSingleClickToSelect(true);
    }

    @Test
    @DisableFlags(FLAG_SINGLE_CLICK_TO_SELECT)
    public void testSingleClickToSelect_disabled() throws Exception {
        doTestSingleClickToSelect(false);
    }

    private void doTestSingleClickToSelect(boolean flagEnabled) throws Exception {
        final String label = TestFilesRule.DIR_NAME_1;
        UiObject2 ancestorObject = bots.directory.findItemAndSelectionHotspot(label)[0];
        UiObject2 labelObject = ancestorObject.findObject(By.text(label));
        labelObject.click();

        if (flagEnabled) {
            bots.directory.assertSelection(1);
        } else {
            bots.directory.assertNoSelection();
        }
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testSetContainerPadding_gestureNav() {
        mActivityScenario.onActivity(
                activity -> {
                    final int systemBarsBottom = 50;
                    dispatchWindowInsets(activity, systemBarsBottom, 0);
                    assertBottomPadding(activity, /* isGestureNav= */ true, systemBarsBottom);
                });
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testSetContainerPadding_3ButtonNav() {
        mActivityScenario.onActivity(
                activity -> {
                    final int systemBarsBottom = 100;
                    dispatchWindowInsets(activity, systemBarsBottom, systemBarsBottom);
                    assertBottomPadding(activity, /* isGestureNav= */ false, systemBarsBottom);
                });
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_HOME_SCREEN_FILES_RO})
    public void testOnConfigurationChanged_LocaleResetsSelection() throws Exception {
        final String[] frenchDownloads = new String[1];
        device.waitForIdle();
        bots.directory.selectDocument("file0.log", 1);
        bots.directory.assertSelection(1);

        mActivityScenario.onActivity(
                activity -> {
                    Configuration newConfig =
                            new Configuration(activity.getResources().getConfiguration());
                    newConfig.setLocale(Locale.FRENCH);

                    // Create a new context with the new configuration to get the correct string
                    Context frenchContext = activity.createConfigurationContext(newConfig);
                    frenchDownloads[0] =
                            frenchContext.getResources().getString(R.string.downloads_label);
                    activity.onConfigurationChanged(newConfig);
                });

        // Wait for the UI to update after the async refresh. The most reliable
        // signal is waiting for the root item with the new locale's text to appear.
        device.wait(Until.hasObject(By.text(frenchDownloads[0])), TIMEOUT);
        bots.directory.assertNoSelection(); // Selection should be cleared
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_HOME_SCREEN_FILES_RO})
    public void testConfigurationChange_ResizeAppPreservesSelection() throws Exception {
        final String[] downloads = new String[1];
        device.waitForIdle();
        bots.directory.selectDocument("file0.log", 1);
        bots.directory.assertSelection(1);

        // This simulates a minor config change where the activity is not recreated,
        // and only onConfigurationChanged is called.
        mActivityScenario.onActivity(
                activity -> {
                    Configuration newConfig =
                            new Configuration(activity.getResources().getConfiguration());
                    newConfig.screenHeightDp += 10;
                    activity.onConfigurationChanged(newConfig);
                    downloads[0] = activity.getResources().getString(R.string.downloads_label);
                });
        device.wait(Until.hasObject(By.text(downloads[0])), TIMEOUT);
        bots.directory.assertSelection(1); // Selection should be preserved
    }

    private void dispatchWindowInsets(
            Activity activity, int systemBarsBottom, int tappableElementBottom) {
        WindowInsetsCompat insets =
                new WindowInsetsCompat.Builder()
                        .setInsets(
                                WindowInsetsCompat.Type.systemBars(),
                                Insets.of(0, 0, 0, systemBarsBottom))
                        .setInsets(
                                WindowInsetsCompat.Type.navigationBars(),
                                Insets.of(0, 0, 0, systemBarsBottom))
                        .setInsets(
                                WindowInsetsCompat.Type.tappableElement(),
                                Insets.of(0, 0, 0, tappableElementBottom))
                        .build();
        ViewCompat.dispatchApplyWindowInsets(
                activity.findViewById(getRes(R.id.coordinator_layout)), insets);
    }

    private void assertBottomPadding(
            Activity activity, boolean isGestureNav, int systemBarsBottom) {
        View root = activity.findViewById(getRes(R.id.coordinator_layout));
        View pickerSaverContainer = activity.findViewById(getRes(R.id.container_save));
        View drawerRootsList =
                activity.findViewById(getRes(R.id.container_roots))
                        .findViewById(getRes(R.id.roots_list));
        assertNotNull(root);
        assertNotNull(pickerSaverContainer);
        assertNotNull(drawerRootsList);

        // Verify root container's bottom padding.
        assertEquals(0, root.getPaddingBottom());

        // Verify picker/saver container's bottom padding.
        int layoutPaddingBottom =
                activity.getResources().getDimensionPixelSize(R.dimen.layout_padding_bottom);
        int expectedBottomPaddingForPicker =
                isGestureNav ? systemBarsBottom : systemBarsBottom + layoutPaddingBottom;
        assertEquals(expectedBottomPaddingForPicker, pickerSaverContainer.getPaddingBottom());

        // Verify navigation drawer and nav rail.
        int drawerPaddingBottom =
                activity.getResources().getDimensionPixelSize(R.dimen.drawer_padding_bottom);
        int expectedBottomPaddingForNav =
                isGestureNav ? systemBarsBottom : systemBarsBottom + drawerPaddingBottom;
        assertEquals(expectedBottomPaddingForNav, drawerRootsList.getPaddingBottom());
        if (bots.roots.inNavRailLayout()) {
            View navRailContainer = activity.findViewById(getRes(R.id.nav_rail_container_roots));
            assertNotNull(navRailContainer);
            View navRailRootsList = navRailContainer.findViewById(R.id.roots_list);
            assertNotNull(navRailRootsList);
            assertEquals(expectedBottomPaddingForNav, navRailRootsList.getPaddingBottom());
        }
    }
}
