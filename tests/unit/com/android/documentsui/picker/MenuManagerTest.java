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

package com.android.documentsui.picker;

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.flags.Flags.FLAG_USE_FILE_SUMMARY;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.MatrixCursor;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.documentsui.DirectoryResult;
import com.android.documentsui.Injector;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.SummaryProviderManager;
import com.android.documentsui.dirlist.SummaryProviderState;
import com.android.documentsui.picker.TestActivity;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestPackageManager;
import com.android.documentsui.testing.TestResources;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private TestMenu testMenu;

    /* Directory Context Menu items */
    private TestMenuItem dirShare;
    private TestMenuItem dirOpen;
    private TestMenuItem dirOpenWith;
    private TestMenuItem dirCutToClipboard;
    private TestMenuItem dirCopyToClipboard;
    private TestMenuItem dirPasteFromClipboard;
    private TestMenuItem mDirCompress;
    private TestMenuItem dirCreateDir;
    private TestMenuItem dirSelectAll;
    private TestMenuItem mDirDeselectAll;
    private TestMenuItem dirRename;
    private TestMenuItem dirDelete;
    private TestMenuItem dirViewInOwner;
    private TestMenuItem dirOpenInNewWindow;
    private TestMenuItem dirPasteIntoFolder;
    private TestMenuItem mDirExtractHere;
    private TestMenuItem mDirBrowse;
    private TestMenuItem mDirInspect;

    /* Root List Context Menu items */
    private TestMenuItem rootEjectRoot;
    private TestMenuItem rootOpenInNewWindow;
    private TestMenuItem rootPasteIntoFolder;
    private TestMenuItem rootSettings;
    private TestMenuItem mRootManageDevice;

    /* Action Mode menu items */
    private TestMenuItem actionModeOpen;
    private TestMenuItem actionModeOpenWith;
    private TestMenuItem actionModeSelect;
    private TestMenuItem actionModeShare;
    private TestMenuItem actionModeDelete;
    private TestMenuItem actionModeSelectAll;
    private TestMenuItem mActionModeDeselectAll;
    private TestMenuItem actionModeCopyTo;
    private TestMenuItem actionModeExtractTo;
    private TestMenuItem actionModeMoveTo;
    private TestMenuItem actionModeCompress;
    private TestMenuItem actionModeRename;
    private TestMenuItem actionModeViewInOwner;
    private TestMenuItem actionModeSort;
    private TestMenuItem mActionExtractHere;
    private TestMenuItem mActionBrowse;
    private TestMenuItem mActionInspect;

    /* Option Menu items */
    private TestMenuItem optionSearch;
    private TestMenuItem optionDebug;
    private TestMenuItem optionNewWindow;
    private TestMenuItem optionCreateDir;
    private TestMenuItem optionSelectAll;
    private TestMenuItem optionSettings;
    private TestMenuItem mOptionManageDevice;
    private TestMenuItem optionSort;
    private TestMenuItem mOptionLauncher;
    private TestMenuItem mOptionShowHiddenFiles;
    private TestMenuItem mOptionExtractAll;
    private TestMenuItem mOptionInspect;

    private TestMenuItem subOptionGrid;
    private TestMenuItem subOptionList;

    private TestFeatures mFeatures;
    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails dirDetails;
    private TestSearchViewManager testSearchManager;
    private State state = new State();
    private RootInfo testRootInfo;
    private DocumentInfo testDocInfo;
    private MenuManager mgr;
    private SummaryProviderManager mSummaryProviderManager;

    private int mFilesCount;

    private final TestActivity mActivity =
            TestActivity.create(TestEnv.create());
    private TestPackageManager mPackageManager;
    private ActivityInfo activityInfo;
    private ResolveInfo resolveInfo = new ResolveInfo();
    private TestResources testResources;

    @Before
    public void setUp() {
        testMenu = TestMenu.create();
        dirShare = testMenu.findItem(R.id.dir_menu_share);
        dirOpen = testMenu.findItem(R.id.dir_menu_open);
        dirOpenWith = testMenu.findItem(R.id.dir_menu_open_with);
        dirCutToClipboard = testMenu.findItem(R.id.dir_menu_cut_to_clipboard);
        dirCopyToClipboard = testMenu.findItem(R.id.dir_menu_copy_to_clipboard);
        mDirCompress = testMenu.findItem(R.id.dir_menu_compress);
        dirPasteFromClipboard = testMenu.findItem(R.id.dir_menu_paste_from_clipboard);
        dirCreateDir = testMenu.findItem(R.id.dir_menu_create_dir);
        dirSelectAll = testMenu.findItem(R.id.dir_menu_select_all);
        mDirDeselectAll = testMenu.findItem(R.id.dir_menu_deselect_all);
        dirRename = testMenu.findItem(R.id.dir_menu_rename);
        dirDelete = testMenu.findItem(R.id.dir_menu_delete);
        dirViewInOwner = testMenu.findItem(R.id.dir_menu_view_in_owner);
        dirOpenInNewWindow = testMenu.findItem(R.id.dir_menu_open_in_new_window);
        dirPasteIntoFolder = testMenu.findItem(R.id.dir_menu_paste_into_folder);
        mDirExtractHere = testMenu.findItem(R.id.dir_menu_extract_here);
        mDirBrowse = testMenu.findItem(R.id.dir_menu_browse);
        mDirInspect = testMenu.findItem(R.id.dir_menu_inspect);

        rootEjectRoot = testMenu.findItem(R.id.root_menu_eject_root);
        rootOpenInNewWindow = testMenu.findItem(R.id.root_menu_open_in_new_window);
        rootPasteIntoFolder = testMenu.findItem(R.id.root_menu_paste_into_folder);
        rootSettings = testMenu.findItem(R.id.root_menu_settings);
        mRootManageDevice = testMenu.findItem(R.id.root_menu_manage_device);

        actionModeOpenWith = testMenu.findItem(R.id.action_menu_open_with);
        actionModeSelect = testMenu.findItem(R.id.action_menu_select);
        actionModeShare = testMenu.findItem(R.id.action_menu_share);
        actionModeDelete = testMenu.findItem(R.id.action_menu_delete);
        actionModeSelectAll = testMenu.findItem(R.id.action_menu_select_all);
        mActionModeDeselectAll = testMenu.findItem(R.id.action_menu_deselect_all);
        actionModeCopyTo = testMenu.findItem(R.id.action_menu_copy_to);
        actionModeExtractTo = testMenu.findItem(R.id.action_menu_extract_to);
        actionModeMoveTo = testMenu.findItem(R.id.action_menu_move_to);
        actionModeCompress = testMenu.findItem(R.id.action_menu_compress);
        actionModeRename = testMenu.findItem(R.id.action_menu_rename);
        actionModeViewInOwner = testMenu.findItem(R.id.action_menu_view_in_owner);
        actionModeSort = testMenu.findItem(R.id.action_menu_sort);
        mActionExtractHere = testMenu.findItem(R.id.action_menu_extract_here);
        mActionBrowse = testMenu.findItem(R.id.action_menu_browse);
        mActionInspect = testMenu.findItem(R.id.action_menu_inspect);

        optionSearch = testMenu.findItem(R.id.option_menu_search);
        optionDebug = testMenu.findItem(R.id.option_menu_debug);
        optionNewWindow = testMenu.findItem(R.id.option_menu_new_window);
        optionCreateDir = testMenu.findItem(R.id.option_menu_create_dir);
        optionSelectAll = testMenu.findItem(R.id.option_menu_select_all);
        optionSettings = testMenu.findItem(R.id.option_menu_settings);
        mOptionManageDevice = testMenu.findItem(R.id.option_menu_manage_device);
        optionSort = testMenu.findItem(R.id.option_menu_sort);
        mOptionLauncher = testMenu.findItem(R.id.option_menu_launcher);
        mOptionShowHiddenFiles = testMenu.findItem(R.id.option_menu_show_hidden_files);
        mOptionExtractAll = testMenu.findItem(R.id.option_menu_extract_all);
        mOptionInspect = testMenu.findItem(R.id.option_menu_inspect);

        // Menu actions on root title row.
        subOptionGrid = testMenu.findItem(R.id.sub_menu_grid);
        subOptionList = testMenu.findItem(R.id.sub_menu_list);

        mFeatures = new TestFeatures();

        selectionDetails = new TestSelectionDetails();
        selectionDetails.size = 1;
        selectionDetails.mimeTypes = new HashSet<>(Collections.singleton("text/plain"));

        mPackageManager = TestPackageManager.create();
        activityInfo = spy(new ActivityInfo());
        activityInfo.packageName = "com.test.package";
        activityInfo.name = "test.class";
        resolveInfo.activityInfo = activityInfo;

        testResources = TestResources.create();

        mActivity.resources = testResources;
        mActivity.packageMgr = mPackageManager;
        TestActionHandler testActionHandler = new TestActionHandler();
        testActionHandler.throwAtCreateApprovedHandlerIntent = true;
        ((Injector) mActivity.injector).actions = testActionHandler;

        dirDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        mSummaryProviderManager =
                spy(
                        new SummaryProviderManager(
                                ApplicationProvider.getApplicationContext(),
                                CoroutineScopeKt.CoroutineScope(Dispatchers.getUnconfined()),
                                null));
        // Disable the part that kicks off the coroutine.
        doNothing().when(mSummaryProviderManager).start();
        mActivity.injector.setSummaryProviderManager(mSummaryProviderManager);
        mgr =
                new MenuManager(
                        testSearchManager,
                        state,
                        dirDetails,
                        this::getFilesCount,
                        mActivity,
                        mFeatures,
                        mActivity.injector,
                        null);
        selectionDetails.size = 1;
        mFilesCount = 10;

        testRootInfo = new RootInfo();
        testDocInfo = new DocumentInfo();
        state.action = ACTION_CREATE;
        state.allowMultiple = true;
    }

    private int getFilesCount() {
        return mFilesCount;
    }

    @Test
    public void testActionMenu() {
        mgr.updateActionMenu(testMenu, selectionDetails);
        actionModeDelete.assertDisabledAndInvisible();
        actionModeShare.assertDisabledAndInvisible();
        actionModeRename.assertDisabledAndInvisible();
        actionModeViewInOwner.assertDisabledAndInvisible();
        mOptionExtractAll.assertDisabledAndInvisible();
        mActionExtractHere.assertDisabledAndInvisible();
        mActionBrowse.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_showSortAndSelect() {
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertDisabledAndInvisible();
        actionModeSort.assertEnabledAndVisible();
        actionModeSelectAll.assertEnabledAndVisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_hideSortAndSelect() {
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertDisabledAndInvisible();
        actionModeSort.assertDisabledAndInvisible();
        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    // Disable M3 flag for the test since the de/select menu options are disabled by default in M3.
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_hideSelectAndDeselectAll_NoFilesInDirectory() {
        mFilesCount = 0;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_OnArchive() {
        selectionDetails.size = 1;
        selectionDetails.containFiles = true;
        selectionDetails.isArchive = true;
        selectionDetails.containsFilesInArchive = false;
        dirDetails.isInArchive = false;
        dirDetails.canCreateDirectory = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionExtractHere.assertDisabledAndInvisible();
        mActionBrowse.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_selectAction() {
        state.action = ACTION_OPEN;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_selectAction_useMaterial3Enabled() {
        state.action = ACTION_OPEN;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_selectActionTitle() {
        state.action = ACTION_OPEN;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertTitle(R.string.menu_select);
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_getContentAction() {
        state.action = ACTION_GET_CONTENT;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_getContentAction_useMaterial3Enabled() {
        state.action = ACTION_GET_CONTENT;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_getContentActionTitle() {
        state.action = ACTION_GET_CONTENT;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelect.assertTitle(R.string.menu_select);
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_notAllowMultiple() {
        state.allowMultiple = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_AllowMultiple() {
        state.allowMultiple = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelectAll.assertEnabledAndVisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_CanDeselectAll() {
        state.allowMultiple = true;
        selectionDetails.size = 1;
        mFilesCount = 1;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_CanDelete() {
        selectionDetails.canDelete = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeDelete.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_CanRename() {
        selectionDetails.canRename = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_CanCompress() {
        mFeatures.archiveCreation = true;
        dirDetails.canCreateDoc = true;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testActionMenu_CanInspect() {
        mFeatures.inspector = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        mActionInspect.assertEnabledAndVisible();
    }

    @Test
    public void testOptionMenu() {
        mgr.updateOptionMenu(testMenu);

        optionCreateDir.assertDisabledAndInvisible();
        optionDebug.assertDisabledAndInvisible();
        optionSort.assertEnabledAndVisible();
        mOptionLauncher.assertDisabledAndInvisible();
        mOptionShowHiddenFiles.assertEnabledAndVisible();
        mOptionExtractAll.assertDisabledAndInvisible();
        optionNewWindow.assertDisabledAndInvisible();

        if (!isUseMaterial3FlagEnabled()) {
            assertTrue(testSearchManager.showMenuCalled());
        }
    }

    @Test
    public void testOptionMenu_ExtractAll() {
        dirDetails.isInArchive = true;
        mgr.updateOptionMenu(testMenu);
        mOptionExtractAll.assertDisabledAndInvisible();
        dirDetails.isInArchive = false;
        mgr.updateOptionMenu(testMenu);
        mOptionExtractAll.assertDisabledAndInvisible();
    }

    @Test
    public void testOptionMenu_SelectAll_NoFilesInDirectory() {
        mFilesCount = 0;
        mgr.updateOptionMenu(testMenu);
        optionSelectAll.assertDisabledAndInvisible();
    }

    @Test
    public void testOptionMenu_SelectAll_WithFilesInDirectory() {
        mgr.updateOptionMenu(testMenu);
        optionSelectAll.assertEnabledAndVisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testOptionMenu_notPicking() {
        state.action = ACTION_OPEN;
        state.derivedMode = State.MODE_LIST;
        mgr.updateOptionMenu(testMenu);
        mgr.updateSubMenu(testMenu);

        optionCreateDir.assertDisabledAndInvisible();
        subOptionGrid.assertEnabledAndVisible();
        subOptionList.assertDisabledAndInvisible();
        mOptionExtractAll.assertDisabledAndInvisible();
        assertFalse(testSearchManager.showMenuCalled());
    }

    @Test
    public void testOptionMenu_canCreateDirectory() {
        dirDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu);

        optionCreateDir.assertEnabledAndVisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testOptionMenu_inRecents() {
        dirDetails.isInRecents = true;
        mgr.updateOptionMenu(testMenu);
        mgr.updateSubMenu(testMenu);

        subOptionGrid.assertDisabledAndInvisible();
        subOptionList.assertDisabledAndInvisible();
        mOptionExtractAll.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testOptionMenu_inRecents_showListGridSwitch() {
        dirDetails.isInRecents = true;
        state.derivedMode = State.MODE_GRID;
        mgr.updateOptionMenu(testMenu);

        subOptionGrid.assertDisabledAndInvisible();
        subOptionList.assertEnabledAndVisible();
    }

    @Test
    public void testOptionMenu_onlyContainer() {
        state.allowMultiple = true;
        mgr.updateModel(getTestModel(true));
        mgr.updateOptionMenu(testMenu);

        optionSelectAll.assertDisabledAndInvisible();
    }

    @Test
    public void testOptionMenu_containerAndFile() {
        state.allowMultiple = true;
        mgr.updateModel(getTestModel(false));
        mgr.updateOptionMenu(testMenu);

        optionSelectAll.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testOptionMenu_Inspector_VisibleAndEnabled() {
        mFeatures.inspector = true;
        dirDetails.canInspectDirectory = true;
        mgr.updateOptionMenu(testMenu);
        mOptionInspect.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testOptionMenu_Inspector_InvisibleAndDisabled() {
        mFeatures.inspector = true;
        dirDetails.canInspectDirectory = false;
        mgr.updateOptionMenu(testMenu);
        mOptionInspect.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea() {
        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = false;
        dirDetails.canCreateDirectory = false;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        mDirDeselectAll.assertDisabledAndInvisible();
        dirPasteFromClipboard.assertDisabledAndInvisible();
        dirCreateDir.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_NoItemToPaste() {
        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirPasteFromClipboard.assertDisabledAndInvisible();
        dirCreateDir.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_CantCreateDoc() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = false;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirPasteFromClipboard.assertDisabledAndInvisible();
        dirCreateDir.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_canPaste() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirCreateDir.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();

        if (isUseMaterial3FlagEnabled()) {
            dirPasteFromClipboard.assertDisabledAndInvisible();
        } else {
            dirPasteFromClipboard.assertEnabledAndVisible();
        }
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirPasteFromClipboard.assertDisabledAndInvisible();
        dirCreateDir.assertEnabledAndVisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_CanDeselectAll() {
        selectionDetails.size = 1;
        mFilesCount = 1;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertDisabledAndInvisible();
        mDirDeselectAll.assertEnabledAndVisible();
    }

    @Test
    public void testContextMenu_EmptyArea_SelectAndDeselectAllWithNoFilesInDirectory() {
        mFilesCount = 0;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertDisabledAndInvisible();
        mDirDeselectAll.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnFile() {
        mFeatures.archiveCreation = true;
        dirDetails.canCreateDoc = true;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        mFeatures.inspector = true;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        // We don't want share in pickers.
        dirShare.assertDisabledAndInvisible();
        // We don't want openWith in pickers.
        dirOpenWith.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();

        if (isUseMaterial3FlagEnabled()) {
            dirCutToClipboard.assertDisabledAndInvisible();
            dirCopyToClipboard.assertDisabledAndInvisible();
            mDirCompress.assertEnabledAndVisible();
            dirRename.assertEnabledAndVisible();
            dirDelete.assertEnabledAndVisible();
            mDirInspect.assertEnabledAndVisible();
        } else {
            dirCutToClipboard.assertEnabledAndVisible();
            dirCopyToClipboard.assertEnabledAndVisible();
            mDirCompress.assertDisabledAndInvisible();
            dirRename.assertDisabledAndInvisible();
            dirDelete.assertEnabledAndVisible();
            mDirInspect.assertEnabledAndVisible();
        }
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnDirectory() {
        selectionDetails.canPasteInto = true;
        mFeatures.archiveCreation = true;
        dirDetails.canCreateDoc = true;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        mFeatures.inspector = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        // We don't want openInNewWindow in pickers
        dirOpenInNewWindow.assertDisabledAndInvisible();
        // Doesn't matter if directory is selected, we don't want pasteInto for PickerActivity
        dirPasteIntoFolder.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();

        if (isUseMaterial3FlagEnabled()) {
            dirCutToClipboard.assertDisabledAndInvisible();
            dirCopyToClipboard.assertDisabledAndInvisible();
            mDirCompress.assertEnabledAndVisible();
            dirRename.assertEnabledAndVisible();
            dirDelete.assertEnabledAndVisible();
            mDirInspect.assertEnabledAndVisible();
        } else {
            dirCutToClipboard.assertEnabledAndVisible();
            dirCopyToClipboard.assertEnabledAndVisible();
            mDirCompress.assertDisabledAndInvisible();
            dirRename.assertDisabledAndInvisible();
            dirDelete.assertEnabledAndVisible();
            mDirInspect.assertEnabledAndVisible();
        }
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMixedDocs() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirCompress.assertDisabledAndInvisible();
        dirDelete.assertEnabledAndVisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
        mDirInspect.assertDisabledAndInvisible();

        if (isUseMaterial3FlagEnabled()) {
            dirCutToClipboard.assertDisabledAndInvisible();
            dirCopyToClipboard.assertDisabledAndInvisible();
        } else {
            dirCutToClipboard.assertEnabledAndVisible();
            dirCopyToClipboard.assertEnabledAndVisible();
        }
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMixedDocs_hasPartialFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.containPartial = true;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertDisabledAndInvisible();
        dirCopyToClipboard.assertDisabledAndInvisible();
        mDirCompress.assertDisabledAndInvisible();
        dirDelete.assertEnabledAndVisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMixedDocs_hasUndeletableFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirCompress.assertDisabledAndInvisible();
        dirDelete.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();

        if (isUseMaterial3FlagEnabled()) {
            dirCutToClipboard.assertDisabledAndInvisible();
            dirCopyToClipboard.assertDisabledAndInvisible();
        } else {
            dirCutToClipboard.assertDisabledAndInvisible();
            dirCopyToClipboard.assertEnabledAndVisible();
        }
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnArchive() {
        selectionDetails.size = 1;
        selectionDetails.containFiles = true;
        selectionDetails.isArchive = true;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testContextMenu_CantInspectRecents() {
        mFeatures.inspector = true;

        dirDetails.isInRecents = true;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);
        mDirInspect.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testContextMenu_CantInspectTrash() {
        mFeatures.inspector = true;

        dirDetails.isTrashTopLevel = true;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);
        mDirInspect.assertDisabledAndInvisible();
    }

    private void testRootContextMenu() {
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
        rootOpenInNewWindow.assertDisabledAndInvisible();
        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testRootContextMenu_material3() {
        testRootContextMenu();
        mRootManageDevice.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootContextMenu_non_material3() {
        testRootContextMenu();
        rootSettings.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testRootContextMenu_hasManageDevice() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        mRootManageDevice.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootContextMenu_hasRootSettings() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootSettings.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_nonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_nothingToPaste() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_canEject() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(FLAG_USE_FILE_SUMMARY)
    public void testOptionMenu_ShowSummaryColumn_disabledWhenFlagIsOff() {
        mSummaryProviderManager.setStateForTest(SummaryProviderState.FlagDisabled.INSTANCE);
        TestMenuItem showSummary = testMenu.createMenuItem(R.id.option_show_summary);
        mgr.updateOptionMenu(testMenu);
        showSummary.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(FLAG_USE_FILE_SUMMARY)
    public void testOptionMenu_ShowSummaryColumn_enabledWhenFlagIsOn() {
        TestMenuItem showSummary = testMenu.createMenuItem(R.id.option_show_summary);

        boolean isUserEnabled = false;
        mSummaryProviderManager.setStateForTest(new SummaryProviderState.Available(isUserEnabled));
        mgr.updateOptionMenu(testMenu);
        showSummary.assertEnabledAndVisible();
        showSummary.assertTitle(R.string.option_show_summary_column);

        isUserEnabled = true;
        mSummaryProviderManager.setStateForTest(new SummaryProviderState.Available(isUserEnabled));
        mgr.updateOptionMenu(testMenu);
        showSummary.assertEnabledAndVisible();
        showSummary.assertTitle(R.string.option_hide_summary_column);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_notUseApprovedDocumentHandler() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateContextMenu(testMenu, selectionDetails);

        // Check that the approved document handler menu item is not added.
        boolean found = false;
        for (int i = 0; i < testMenu.size(); i++) {
            TestMenuItem item = testMenu.getItem(i);
            if (String.valueOf(item.getTitle()).equals("Test App")) {
                found = true;
            }
        }
        assertFalse("Approved document handler menu item should not be added.", found);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_notUseApprovedDocumentHandler() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateActionMenu(testMenu, selectionDetails);

        // Check that the approved document handler menu item is not added.
        boolean found = false;
        for (int i = 0; i < testMenu.size(); i++) {
            TestMenuItem item = testMenu.getItem(i);
            if (String.valueOf(item.getTitle()).equals("Test App")) {
                found = true;
            }
        }
        assertFalse("Approved document handler menu item should not be added.", found);
    }

    @SuppressLint("VisibleForTests")
    private Model getTestModel(boolean onlyDirectory) {
        String[] COLUMNS = new String[]{
                RootCursorWrapper.COLUMN_AUTHORITY,
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_FLAGS,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_MIME_TYPE
        };
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < 3; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        }

        if (!onlyDirectory) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, "4");
            row.add(Document.COLUMN_MIME_TYPE, "image/jpg");
        }

        DirectoryResult r = new DirectoryResult();
        r.setCursor(c);
        Model model = new Model(new TestFeatures());
        model.update(r);

        return model;
    }
}
