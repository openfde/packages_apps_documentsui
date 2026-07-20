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

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_FILE_HANDLING_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_NEW_OPEN_WITH;
import static com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO;

import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.util.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class ContextMenuUiTest extends ActivityTestJunit4<FilesActivity> {
    private static final String EXTRACT = "Extract";
    private static final String BROWSE = "Browse";
    private static final String SHARE = "Share";
    private static final String OPEN = "Open";
    private static final String OPEN_WITH = "Open with";
    private static final String CUT = "Cut";
    private static final String COPY = "Copy";
    private static final String RENAME = "Rename";
    private static final String DELETE_FOREVER = "Delete forever";
    private static final String OPEN_IN_NEW_WINDOW = "Open in new window";
    private static final String SELECT_ALL = "Select all";
    private static final String NEW_FOLDER = "New folder";

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
                                docsHelper.createFolder(dir1, "ChildDir1");
                                docsHelper.createDocument(root, "text/plain", "file0.log");
                                docsHelper.createDocument(root, "image/png", "file1.png");
                                docsHelper.createDocument(root, "text/csv", "file2.csv");
                                docsHelper.createDocument(root, "application/zip", "archive.zip");
                                docsHelper.createDocument(root, "text/plain", "anotherFile0.log");
                                docsHelper.createDocument(root, "text/plain", "poodles.text");
                            });

    private Map<String, Boolean> menuItems;

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
        menuItems = new HashMap<>();

        menuItems.put(EXTRACT, false);
        menuItems.put(BROWSE, false);
        menuItems.put(SHARE, false);
        menuItems.put(OPEN, false);
        menuItems.put(OPEN_WITH, false);
        menuItems.put(CUT, false);
        menuItems.put(COPY, false);
        menuItems.put(RENAME, false);
        menuItems.put(DELETE_FOREVER, false);
        menuItems.put(OPEN_IN_NEW_WINDOW, false);
        menuItems.put(SELECT_ALL, false);
        menuItems.put(NEW_FOLDER, false);
    }

    @Test
    @DisableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_onFile() throws Exception {
        menuItems.put(SHARE, true);
        menuItems.put(OPEN, false);
        menuItems.put(OPEN_WITH, true);
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(RENAME, true);
        menuItems.put(DELETE_FOREVER, true);

        bots.directory.rightClickDocument("file1.png");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    /*
     * Test files with <2 opening apps don't get open-with when file handling flag is enabled but
     * new open-with is disabled.
     *
     * PNGs usually have 2+ opening apps. This test is repeated with CSV below.
     */
    @Test
    @EnableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
    @DisableFlags({FLAG_USE_NEW_OPEN_WITH})
    public void testContextMenu_onFilePngDesktop_oldOpenWith() throws Exception {
        RootInfo root = mDocsHelper.getRoot(ROOT_0_ID);
        DocumentInfo doc = mDocsHelper.findFile(root.documentId, "file1.png");
        int pngOpeningApps = FileUtils.countOpeningApps(doc, context.getPackageManager());

        menuItems.put(SHARE, true);
        menuItems.put(OPEN, true);
        // On desktop, "open with" is only shown when the file has multiple opening apps.
        // Ideally we would mock this, but we can't in these functional tests.
        menuItems.put(OPEN_WITH, pngOpeningApps > 1);
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(RENAME, true);
        menuItems.put(DELETE_FOREVER, true);

        bots.directory.rightClickDocument("file1.png");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    /*
     * Repeating the OnFile test again with a CSV to test the behaviour when there are no opening
     * apps. Obviously we cannot enforce this but this is likely on most devices.
     *
     * The test will still pass even if the device has 2+ opening apps for CSV, it just doesn't
     * verify that we are hiding "open with" when it needs to be.
     */
    @Test
    @EnableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
    @DisableFlags({FLAG_USE_NEW_OPEN_WITH})
    public void testContextMenu_onFileCsvDesktop_oldOpenWith() throws Exception {
        RootInfo root = mDocsHelper.getRoot(ROOT_0_ID);
        DocumentInfo doc = mDocsHelper.findFile(root.documentId, "file2.csv");
        int csvOpeningApps = FileUtils.countOpeningApps(doc, context.getPackageManager());

        menuItems.put(SHARE, true);
        menuItems.put(OPEN, true);
        // On desktop, "open with" is only shown when the file has multiple opening apps.
        // Ideally we would mock this, but we can't in these functional tests.
        menuItems.put(OPEN_WITH, csvOpeningApps > 1);
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(RENAME, true);
        menuItems.put(DELETE_FOREVER, true);

        bots.directory.rightClickDocument("file2.csv");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @EnableFlags({FLAG_DESKTOP_FILE_HANDLING_RO, FLAG_USE_NEW_OPEN_WITH})
    public void testContextMenu_onFileDesktop_newOpenWith() throws Exception {
        menuItems.put(SHARE, true);
        menuItems.put(OPEN, true);
        // New open-with doesn't auto-launch so it shows for all files (not just files with 2+
        // opening apps). There is no guarantee that CSV has <2 opening apps but it's likely.
        // Ideally we would mock the number of opening apps but it's not possible in these
        // functional tests.
        menuItems.put(OPEN_WITH, true);
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(RENAME, true);
        menuItems.put(DELETE_FOREVER, true);

        bots.directory.rightClickDocument("file2.csv");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void testContextMenu_onArchive_shouldHaveBrowseMenuItem() throws Exception {
        menuItems.clear();
        menuItems.put(EXTRACT, true);
        menuItems.put(BROWSE, true);

        bots.directory.rightClickDocument("archive.zip");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @DisableFlags({FLAG_ZIP_NG_RO})
    public void testContextMenu_onArchive_shouldNotHaveBrowseMenuItem() throws Exception {
        menuItems.clear();
        menuItems.put(EXTRACT, false);
        menuItems.put(BROWSE, false);

        bots.directory.rightClickDocument("archive.zip");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onDir() throws Exception {
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(OPEN_IN_NEW_WINDOW, true);
        menuItems.put(RENAME, true);
        menuItems.put(DELETE_FOREVER, true);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onMixedFileDir() throws Exception {
        menuItems.put(CUT, true);
        menuItems.put(COPY, true);
        menuItems.put(DELETE_FOREVER, true);
        bots.directory.selectDocument("anotherFile0.log", 1);
        bots.directory.selectDocument("Dir1", 2);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onEmptyArea() throws Exception {
        menuItems.put(SELECT_ALL, true);
        menuItems.put(NEW_FOLDER, true);
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect dirBounds = bots.directory.findDocument(TestFilesRule.DIR_NAME_1).getBounds();

        bots.main.switchToGridMode();
        // right side of dir1 area
        bots.directory.rightClick(new Point(dirListBounds.right - 1, dirBounds.centerY()));
        bots.menu.assertPresentMenuItems(menuItems);
    }
}
