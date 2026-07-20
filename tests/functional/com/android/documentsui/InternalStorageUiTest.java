/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_UX_PHASE_2_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static org.junit.Assert.assertNull;

import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

/**
 * A Ui test will do tests in the internal storage root. It is implemented because some operations
 * is failed and its result will different from the test on the StubProvider. b/115304092 is a
 * example which only happen on root from ExternalStorageProvider.
 */
@LargeTest
public class InternalStorageUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private static final String fileName = "!Test3345678";
    private static final String newFileName = "!9527Test";
    private RootInfo rootPrimary;

    @Override
    protected void setupTestingRoots() throws RemoteException {
        mDocsHelper =
                new DocumentsProviderHelper(
                        userId, Providers.AUTHORITY_STORAGE, context, Providers.AUTHORITY_STORAGE);

        // Set initial root activity will launch into
        rootPrimary = mDocsHelper.getRoot(Providers.ROOT_ID_DEVICE);
        setInitialRoot(rootPrimary);
    }

    @Before
    public void setUpTest() throws Exception {
        deleteTestFiles();
    }

    @After
    public void tearDownTest() throws Exception {
        deleteTestFiles();
    }

    @HugeLongTest
    @Test
    public void testRenameFile() throws Exception {
        createTestFiles();

        var originalFile = bots.directory.findDocument(fileName);

        bots.directory.selectDocument(fileName, 1);
        device.waitForIdle();

        bots.main.clickRename();

        bots.main.setDialogText(newFileName);
        device.waitForIdle();

        bots.keyboard.pressEnter();

        originalFile.waitUntilGone(3000);
        bots.directory.assertDocumentsVisible(newFileName);
        // Snackbar will not show if no exception.
        assertNull(bots.directory.getSnackbar(context.getString(R.string.rename_error)));
    }

    private void createTestFiles() {
        mDocsHelper.createFolder(rootPrimary, fileName);
    }

    private void deleteTestFiles() throws Exception {
        for (var doc : mDocsHelper.listAllChildren(rootPrimary)) {
            if (Arrays.asList(fileName, newFileName).contains(doc.displayName)) {
                mDocsHelper.deleteDocumentIfExists(doc.getDocumentUri());
            }
        }
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_DESKTOP_UX_PHASE_2_RO})
    public void testShowHideNonDesktopFolders() throws Exception {
        String[] desktopFolders = {"Android", "Music"};
        // Reset show/hide state to hide hidden files before the test.
        bots.main.hideHiddenFilesIfNeeded();

        // By default non-desktop folders like Android/Music don't show.
        bots.directory.assertDocumentsAbsent(desktopFolders);

        bots.main.showHiddenFiles();
        // Assert these folder are now showing.
        bots.directory.assertDocumentsPresent(desktopFolders);

        bots.main.hideHiddenFiles();
        // Assert these folder are gone.
        bots.directory.assertDocumentsAbsent(desktopFolders);
    }
}
