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

package com.android.documentsui.files;

import static android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API;
import static android.provider.Flags.FLAG_ENABLE_SYNC_STATE;

import static com.android.documentsui.flags.Flags.FLAG_USE_FILE_SUMMARY;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isDesktopUxPhase2FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isVisualSignalsFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;
import static kotlinx.coroutines.test.TestCoroutineDispatchersKt.StandardTestDispatcher;

import static android.os.Process.myUid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.testing.TestLifecycleOwner;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.documentsui.Injector;
import com.android.documentsui.R;
import com.android.documentsui.SelectionHelpers;
import com.android.documentsui.approveddochandlers.ApprovedDocHandlers;
import com.android.documentsui.approveddochandlers.ApprovedDocMenuController;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.SummaryProviderManager;
import com.android.documentsui.dirlist.SummaryProviderState;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuInflater;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestPackageManager;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResources;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import com.android.documentsui.rules.InstantTaskExecutorRule;
import com.android.documentsui.rules.MainDispatcherRule;

import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.test.TestCoroutineScheduler;
import kotlinx.coroutines.test.TestDispatcher;

import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    private TestMenu testMenu;

    /* Directory Context Menu items */
    private TestMenuItem dirShare;
    private TestMenuItem dirOpen;
    private TestMenuItem dirOpenWith;
    private TestMenuItem dirCutToClipboard;
    private TestMenuItem dirCopyToClipboard;
    private TestMenuItem mDirCompress;
    private TestMenuItem dirPasteFromClipboard;
    private TestMenuItem dirCreateDir;
    private TestMenuItem dirSelectAll;
    private TestMenuItem mDirDeselectAll;
    private TestMenuItem dirRename;
    private TestMenuItem dirDelete;
    private TestMenuItem dirViewInOwner;
    private TestMenuItem dirPasteIntoFolder;
    private TestMenuItem dirInspect;
    private TestMenuItem dirOpenInNewWindow;
    private TestMenuItem mDirExtractHere;
    private TestMenuItem mDirBrowse;
    private TestMenuItem mDirMoveToTrash;
    private TestMenuItem mDirRestoreFromTrash;

    /* Root List Context Menu items */
    private TestMenuItem rootEjectRoot;
    private TestMenuItem rootOpenInNewWindow;
    private TestMenuItem rootPasteIntoFolder;
    private TestMenuItem rootSettings;
    private TestMenuItem mRootManageDevice;
    private TestMenuItem mRootInspector;

    /* Action Mode menu items */
    private TestMenuItem actionModeOpen;
    private TestMenuItem actionModeOpenWith;
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
    private TestMenuItem actionModeInspector;
    private TestMenuItem actionModeSort;
    private TestMenuItem mActionExtractHere;
    private TestMenuItem mActionBrowse;
    private TestMenuItem mActionModeTrash;
    private TestMenuItem mActionModeRestoreFromTrash;
    private TestMenuItem mActionModeSelect;
    private TestMenuItem mActionModeOpenInNewWindow;
    private TestMenuItem mActionModeCut;
    private TestMenuItem mActionModeCopy;
    private TestMenuItem mActionModePasteIntoFolder;

    /* Option Menu items */
    private TestMenuItem optionSearch;
    private TestMenuItem optionDebug;
    private TestMenuItem optionNewWindow;
    private TestMenuItem optionCreateDir;
    private TestMenuItem optionSelectAll;
    private TestMenuItem optionSettings;
    private TestMenuItem mOptionManageDevice;
    private TestMenuItem optionInspector;
    private TestMenuItem optionSort;
    private TestMenuItem mOptionLauncher;
    private TestMenuItem mOptionShowHiddenFiles;
    private TestMenuItem mOptionExtractAll;
    private TestMenuItem mOptionPaste;

    /* Sub Option Menu items */
    private TestMenuItem subOptionGrid;
    private TestMenuItem subOptionList;

    private TestFeatures features;
    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails dirDetails;
    private TestSearchViewManager testSearchManager;
    private RootInfo testRootInfo;
    private ShortcutInfo mTestShortcutInfo;
    private DocumentInfo testDocInfo;
    private State state = new State();
    private MenuManager mgr;
    private TestActivity activity = TestActivity.create(TestEnv.create());
    private SelectionTracker<String> selectionManager;
    private SummaryProviderManager mSummaryProviderManager;
    private TestPackageManager mPackageManager;
    private ActivityInfo activityInfo;
    private ResolveInfo resolveInfo = new ResolveInfo();
    private TestResources testResources;
    private ApprovedDocHandlers mApprovedDocHandlers;
    private ApprovedDocMenuController mApprovedDocMenuController;
    private TestActionHandler mActionHandler;
    private TestLifecycleOwner mLifecycleOwner;

    private int mFilesCount;

    private TestCoroutineScheduler testScheduler = new TestCoroutineScheduler();
    private TestDispatcher testDispatcher = StandardTestDispatcher(testScheduler, null);

    @Rule
    public final MainDispatcherRule mainDispatcherRule = new MainDispatcherRule(testDispatcher);

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        if (isVisualSignalsFlagEnabled()) {
            // The job progress indicator toolbar icon registers itself as a broadcast receiver to
            // receive updates, so we need to stub that functionality out.
            doReturn(null).when(activity).registerReceiver(any(), any(), anyInt());
        }

        testMenu = TestMenu.create();

        // The context menu on anything in DirectoryList (including no selection).
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
        dirPasteIntoFolder = testMenu.findItem(R.id.dir_menu_paste_into_folder);
        dirInspect = testMenu.findItem(R.id.dir_menu_inspect);
        dirOpenInNewWindow = testMenu.findItem(R.id.dir_menu_open_in_new_window);
        mDirExtractHere = testMenu.findItem(R.id.dir_menu_extract_here);
        mDirBrowse = testMenu.findItem(R.id.dir_menu_browse);
        mDirMoveToTrash = testMenu.findItem(R.id.dir_menu_move_to_trash);
        mDirRestoreFromTrash = testMenu.findItem(R.id.dir_menu_restore_from_trash);

        rootEjectRoot = testMenu.findItem(R.id.root_menu_eject_root);
        rootOpenInNewWindow = testMenu.findItem(R.id.root_menu_open_in_new_window);
        rootPasteIntoFolder = testMenu.findItem(R.id.root_menu_paste_into_folder);
        rootSettings = testMenu.findItem(R.id.root_menu_settings);
        mRootManageDevice = testMenu.findItem(R.id.root_menu_manage_device);
        mRootInspector = testMenu.findItem(R.id.root_menu_inspect);

        // Menu actions (including overflow) when action mode *is* active.
        actionModeOpen = testMenu.findItem(R.id.action_menu_open);
        actionModeOpenWith = testMenu.findItem(R.id.action_menu_open_with);
        actionModeShare = testMenu.findItem(R.id.action_menu_share);
        actionModeDelete = testMenu.findItem(R.id.action_menu_delete);
        mActionModeSelect = testMenu.findItem(R.id.action_menu_select);
        actionModeSelectAll = testMenu.findItem(R.id.action_menu_select_all);
        mActionModeDeselectAll = testMenu.findItem(R.id.action_menu_deselect_all);
        actionModeCopyTo = testMenu.findItem(R.id.action_menu_copy_to);
        actionModeExtractTo = testMenu.findItem(R.id.action_menu_extract_to);
        actionModeMoveTo = testMenu.findItem(R.id.action_menu_move_to);
        actionModeCompress = testMenu.findItem(R.id.action_menu_compress);
        actionModeRename = testMenu.findItem(R.id.action_menu_rename);
        actionModeInspector = testMenu.findItem(R.id.action_menu_inspect);
        actionModeViewInOwner = testMenu.findItem(R.id.action_menu_view_in_owner);
        actionModeSort = testMenu.findItem(R.id.action_menu_sort);
        mActionExtractHere = testMenu.findItem(R.id.action_menu_extract_here);
        mActionBrowse = testMenu.findItem(R.id.action_menu_browse);
        mActionModeTrash = testMenu.findItem(R.id.action_menu_move_to_trash);
        mActionModeRestoreFromTrash = testMenu.findItem(R.id.action_menu_restore_from_trash);
        mActionModeOpenInNewWindow = testMenu.findItem(R.id.action_menu_open_in_new_window);
        mActionModeCut = testMenu.findItem(R.id.action_menu_cut_to_clipboard);
        mActionModeCopy = testMenu.findItem(R.id.action_menu_copy_to_clipboard);
        mActionModePasteIntoFolder = testMenu.findItem(R.id.action_menu_paste_into_folder);

        // Menu actions (including overflow) when action mode is not active.
        optionSearch = testMenu.findItem(R.id.option_menu_search);
        optionDebug = testMenu.findItem(R.id.option_menu_debug);
        optionNewWindow = testMenu.findItem(R.id.option_menu_new_window);
        optionCreateDir = testMenu.findItem(R.id.option_menu_create_dir);
        optionSelectAll = testMenu.findItem(R.id.option_menu_select_all);
        optionSettings = testMenu.findItem(R.id.option_menu_settings);
        mOptionManageDevice = testMenu.findItem(R.id.option_menu_manage_device);
        optionInspector = testMenu.findItem(R.id.option_menu_inspect);
        optionSort = testMenu.findItem(R.id.option_menu_sort);
        mOptionLauncher = testMenu.findItem(R.id.option_menu_launcher);
        mOptionShowHiddenFiles = testMenu.findItem(R.id.option_menu_show_hidden_files);
        mOptionExtractAll = testMenu.findItem(R.id.option_menu_extract_all);
        mOptionPaste = testMenu.findItem(R.id.option_menu_paste_from_clipboard);

        // Menu actions on root title row.
        subOptionGrid = testMenu.findItem(R.id.sub_menu_grid);
        subOptionList = testMenu.findItem(R.id.sub_menu_list);

        features = new TestFeatures();

        // These items by default are visible
        testMenu.findItem(R.id.dir_menu_select_all).setVisible(true);
        testMenu.findItem(R.id.option_menu_select_all).setVisible(true);
        testMenu.findItem(R.id.sub_menu_list).setVisible(true);

        selectionDetails = new TestSelectionDetails();
        dirDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        selectionManager = SelectionHelpers.createTestInstance(TestData.create(1));
        selectionManager.select("0");

        selectionDetails.size = 1;
        selectionDetails.mimeTypes = new HashSet<>(Collections.singleton("text/plain"));

        mFilesCount = 10;

        mPackageManager = TestPackageManager.create();
        activityInfo = spy(new ActivityInfo());
        activityInfo.packageName = "com.test.package";
        activityInfo.name = "test.class";
        resolveInfo.activityInfo = activityInfo;

        testResources = TestResources.create();
        testResources.stringArrays.put(
                R.array.approved_document_handlers,
                new String[] {
                    "com.test.package",
                    "com.test.package1",
                    "com.test.package2",
                    "com.test.package3",
                    "com.test.package4"
                });

        mLifecycleOwner = new TestLifecycleOwner(Lifecycle.State.STARTED, testDispatcher);

        activity.resources = testResources;
        activity.packageMgr = mPackageManager;
        mActionHandler = new TestActionHandler();
        ((Injector) activity.injector).actions = mActionHandler;

        mApprovedDocHandlers =
                new ApprovedDocHandlers(
                        activity.getApplicationContext(), activity.injector, testDispatcher);
        mApprovedDocMenuController = new ApprovedDocMenuController(
                CoroutineScopeKt.CoroutineScope(testDispatcher),
                mApprovedDocHandlers, activity.injector, testDispatcher);

        mSummaryProviderManager =
                spy(
                        new SummaryProviderManager(
                                activity,
                                CoroutineScopeKt.CoroutineScope(Dispatchers.getUnconfined()),
                                null));
        // Disable the part that kicks off the coroutine.
        doNothing().when(mSummaryProviderManager).start();
        activity.injector.setSummaryProviderManager(mSummaryProviderManager);

        mgr =
                new MenuManager(
                        features,
                        testSearchManager,
                        state,
                        dirDetails,
                        activity,
                        selectionManager,
                        this::getApplicationNameFromAuthority,
                        this::getUriFromModelId,
                        this::getFilesCount,
                        activity.injector,
                        mApprovedDocMenuController);

        activity.injector.menuManager = mgr;

        testRootInfo = new RootInfo();
        testDocInfo = new DocumentInfo();
        state.stack.push(testDocInfo);
        if (isHomeScreenFilesFlagEnabled()) {
            mTestShortcutInfo = new ShortcutInfo();
        }
    }

    private Uri getUriFromModelId(String id) {
        return Uri.EMPTY;
    }

    private String getApplicationNameFromAuthority(UserId userId, String authority) {
        return "TestApp";
    }

    private int getFilesCount() {
        return mFilesCount;
    }

    private boolean shouldShowCopyCutMenus() {
        return isDesktopUxPhase2FlagEnabled()
                && !(activity.getResources().getBoolean(R.bool.show_copy_to_move_to_menus));
    }

    private TestMenuItem findItemByTitle(TestMenu menu, String title) {
        for (int i = 0; i < menu.size(); i++) {
            TestMenuItem item = menu.getItem(i);
            if (String.valueOf(item.getTitle()).equals(title)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Creates a {@link ResolveInfo} representing an app that can handle a document.
     * This will be displayed as a regular menu item in the menu.
     */
    private ResolveInfo createResolveInfo(String packageName, String name, String label) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = spy(new ActivityInfo());
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = name;
        doReturn(label).when(info.activityInfo).loadLabel(any());
        return info;
    }

    /**
     * Creates a {@link ResolveInfo} representing an app that can handle a document.
     * This will be displayed as an action button with an icon.
     */
    private ResolveInfo createButtonResolveInfo(String packageName, String name, String label) {
        ResolveInfo info = createResolveInfo(packageName, name, label);
        info.activityInfo.metaData = new android.os.Bundle();
        info.activityInfo.metaData.putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, true);
        doReturn(Mockito.mock(android.graphics.drawable.Drawable.class))
                .when(info.activityInfo).loadIcon(any());
        return info;
    }

    /**
     * Counts all menu items (both regular menu items and action buttons) with a matching prefix.
     */
    private int countAllMenuItems(TestMenu menu, String prefix) {
        return countMenuItems(menu, prefix, /* countButtonsOnly= */ false);
    }

    /**
     * Counts only menu items displayed as action buttons (icons) with a matching prefix.
     */
    private int countActionButtons(TestMenu menu, String prefix) {
        return countMenuItems(menu, prefix, /* countButtonsOnly= */ true);
    }

    /**
     * Counts menu items with a matching prefix.
     *
     * @param countButtonsOnly If true, only counts items displayed as action buttons (icons). If
     *     false, counts all matching items (buttons and regular items).
     */
    private int countMenuItems(TestMenu menu, String prefix, boolean countButtonsOnly) {
        int foundCount = 0;
        for (int i = 0; i < menu.size(); i++) {
            TestMenuItem item = menu.getItem(i);
            String title = String.valueOf(item.getTitle());
            if (title != null && title.startsWith(prefix)) {
                if (!countButtonsOnly
                        || item.getShowAsAction() == android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM) {
                    foundCount++;
                }
            }
        }
        return foundCount;
    }

    @Test
    public void testActionMenu() {
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        dirDetails.canCreateDoc = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertEnabledAndVisible();
        actionModeDelete.assertEnabledAndVisible();
        actionModeShare.assertEnabledAndVisible();
        actionModeCompress.assertEnabledAndVisible();
        actionModeExtractTo.assertDisabledAndInvisible();
        actionModeViewInOwner.assertDisabledAndInvisible();
        mOptionExtractAll.assertDisabledAndInvisible();
        mActionExtractHere.assertDisabledAndInvisible();
        mActionBrowse.assertDisabledAndInvisible();
        mActionModeTrash.assertDisabledAndInvisible();
        mActionModeRestoreFromTrash.assertDisabledAndInvisible();

        if (shouldShowCopyCutMenus()) {
            mActionModeCopy.assertEnabledAndVisible();
            mActionModeCut.assertEnabledAndVisible();
        } else {
            actionModeCopyTo.assertEnabledAndVisible();
            actionModeMoveTo.assertEnabledAndVisible();
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_showSortAndSelect() {
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        dirDetails.canCreateDoc = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        // Select is only shown in picker mode.
        mActionModeSelect.assertDisabledAndInvisible();
        actionModeSort.assertEnabledAndVisible();
        actionModeSelectAll.assertEnabledAndVisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_hideSortAndSelect() {
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        dirDetails.canCreateDoc = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        mActionModeSelect.assertDisabledAndInvisible();
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
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ZIP_NG_RO})
    public void testActionMenu_containsDocumentsWithUnavailableContent() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;
        selectionDetails.canDelete = true;
        selectionDetails.containDirectories = false;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canRestore = false;
        selectionDetails.canRename = true;
        selectionDetails.isArchive = true;
        selectionDetails.size = 1;
        features.archiveCreation = true;
        features.inspector = true;
        dirDetails.canCreateDoc = true;
        dirDetails.canCreateDirectory = true;
        dirDetails.isInArchive = false;

        mgr.updateActionMenu(testMenu, selectionDetails);

        // Items that are normally enabled are disabled (but visible) when the content is
        // unavailable.
        actionModeOpenWith.assertDisabledAndVisible();
        actionModeDelete.assertEnabledAndVisible();
        actionModeShare.assertDisabledAndVisible();
        actionModeRename.assertEnabledAndVisible();
        mActionModeSelect.assertDisabledAndInvisible();
        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
        actionModeCompress.assertDisabledAndVisible();
        actionModeInspector.assertEnabledAndVisible();
        actionModeSort.assertDisabledAndInvisible();
        mActionExtractHere.assertDisabledAndVisible();
        mActionBrowse.assertDisabledAndVisible();
        if (shouldShowCopyCutMenus()) {
            mActionModeCopy.assertDisabledAndVisible();
            mActionModeCut.assertDisabledAndVisible();
        } else {
            actionModeCopyTo.assertDisabledAndVisible();
            actionModeMoveTo.assertDisabledAndVisible();
        }

        selectionDetails.canExtract = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertDisabledAndVisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API, FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({
        Flags.FLAG_CLOUD_FEATURES,
        Flags.FLAG_USE_MATERIAL3,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO
    })
    public void testActionMenu_containsDocumentsWithUnavailableContent_trash() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.canTrash = true;
        selectionDetails.canRestore = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        // These trash items should be disabled but remain visible as the actions are only
        // temporarily unavailable.
        mActionModeTrash.assertDisabledAndVisible();
        mActionModeRestoreFromTrash.assertDisabledAndVisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_ZIP_NG_RO, Flags.FLAG_USE_MATERIAL3})
    @DisableFlags({Flags.FLAG_CLOUD_FEATURES})
    public void testActionMenu_containsDocumentsWithUnavailableContent_cloudFeaturesDisabled() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;
        selectionDetails.canDelete = true;
        selectionDetails.containDirectories = false;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canRestore = false;
        selectionDetails.canRename = true;
        selectionDetails.isArchive = true;
        selectionDetails.size = 1;
        features.archiveCreation = true;
        features.inspector = true;
        dirDetails.canCreateDoc = true;
        dirDetails.canCreateDirectory = true;
        dirDetails.isInArchive = false;

        mgr.updateActionMenu(testMenu, selectionDetails);

        // Items that are normally enabled should remain enabled when the content is unavailable
        // but the feature flag is off.
        actionModeOpenWith.assertEnabledAndVisible();
        actionModeDelete.assertEnabledAndVisible();
        actionModeShare.assertEnabledAndVisible();
        actionModeRename.assertEnabledAndVisible();
        mActionModeSelect.assertDisabledAndInvisible();
        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertDisabledAndInvisible();
        actionModeCompress.assertEnabledAndVisible();
        actionModeInspector.assertEnabledAndVisible();
        actionModeSort.assertDisabledAndInvisible();
        mActionExtractHere.assertEnabledAndVisible();
        mActionBrowse.assertEnabledAndVisible();
        if (shouldShowCopyCutMenus()) {
            mActionModeCopy.assertEnabledAndVisible();
            mActionModeCut.assertEnabledAndVisible();
        } else {
            actionModeCopyTo.assertEnabledAndVisible();
            actionModeMoveTo.assertEnabledAndVisible();
        }

        selectionDetails.canExtract = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertEnabledAndVisible();
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
        if (isZipNgFlagEnabled()) {
            mActionExtractHere.assertEnabledAndVisible();
            mActionBrowse.assertEnabledAndVisible();
        } else {
            mActionExtractHere.assertDisabledAndInvisible();
            mActionBrowse.assertDisabledAndInvisible();
        }
        if (isUseMaterial3FlagEnabled()) {
            mActionModeOpenInNewWindow.assertDisabledAndInvisible();
        }

        // On archive in read-only directory (but not a nested archive)
        dirDetails.canCreateDirectory = false;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionExtractHere.assertDisabledAndInvisible();
        if (isZipNgFlagEnabled()) {
            mActionBrowse.assertEnabledAndVisible();
        } else {
            mActionBrowse.assertDisabledAndInvisible();
        }

        // On nested archive
        selectionDetails.containsFilesInArchive = true;
        dirDetails.isInArchive = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionExtractHere.assertDisabledAndInvisible();
        mActionBrowse.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_ContainsPartial() {
        selectionDetails.containPartial = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertDisabledAndInvisible();
        actionModeShare.assertDisabledAndInvisible();
        actionModeCompress.assertDisabledAndInvisible();
        actionModeExtractTo.assertDisabledAndInvisible();
        actionModeViewInOwner.assertDisabledAndInvisible();
        mOptionExtractAll.assertDisabledAndInvisible();
        mActionExtractHere.assertDisabledAndInvisible();
        mActionBrowse.assertDisabledAndInvisible();

        if (shouldShowCopyCutMenus()) {
            mActionModeCut.assertDisabledAndInvisible();
            mActionModeCopy.assertDisabledAndInvisible();
        } else {
            actionModeMoveTo.assertDisabledAndInvisible();
            actionModeCopyTo.assertDisabledAndInvisible();
        }
    }

    @Test
    public void testActionMenu_CreateArchives_ReflectsFeatureState() {
        features.archiveCreation = false;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_CreateArchive() {
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertEnabledAndVisible();
    }

    @Test
    public void testActionMenu_NoCreateArchive() {
        dirDetails.canCreateDoc = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_cantRename() {
        selectionDetails.canRename = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_cantDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeDelete.assertDisabledAndInvisible();
        // We shouldn't be able to move files if we can't delete them
        if (shouldShowCopyCutMenus()) {
            mActionModeCut.assertDisabledAndInvisible();
        } else {
            actionModeMoveTo.assertDisabledAndInvisible();
        }
    }

    @Test
    public void testActionsMenu_canViewInOwner() {
        activity.resources.strings.put(R.string.menu_view_in_owner, "Insert name here! %s");
        selectionDetails.canViewInOwner = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeViewInOwner.assertEnabledAndVisible();
    }

    @Test
    public void testActionsMenu_cantViewInOwner_noSelection() {
        // Simulate empty selection
        selectionManager = SelectionHelpers.createTestInstance();
        mgr =
                new MenuManager(
                        features,
                        testSearchManager,
                        state,
                        dirDetails,
                        activity,
                        selectionManager,
                        this::getApplicationNameFromAuthority,
                        this::getUriFromModelId,
                        this::getFilesCount,
                        activity.injector,
                        mApprovedDocMenuController);

        selectionDetails.canViewInOwner = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeViewInOwner.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_changeToCanDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.canDelete = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeDelete.assertEnabledAndVisible();
        if (shouldShowCopyCutMenus()) {
            mActionModeCut.assertEnabledAndVisible();
        } else {
            actionModeMoveTo.assertEnabledAndVisible();
        }
    }

    @Test
    public void testActionMenu_ContainsDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        // We can't share directories
        actionModeShare.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_RemovesDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.containDirectories = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeShare.assertEnabledAndVisible();
    }

    @Test
    public void testActionMenu_CantExtract() {
        selectionDetails.canExtract = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_CanExtract_hidesCopyToAndCompressAndShare() {
        features.archiveCreation = true;
        selectionDetails.canExtract = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertEnabledAndVisible();
        if (shouldShowCopyCutMenus()) {
            mActionModeCut.assertDisabledAndInvisible();
        } else {
            actionModeMoveTo.assertDisabledAndInvisible();
        }
        actionModeCompress.assertDisabledAndInvisible();
    }

    @Test
    public void testActionMenu_CanOpenWith() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        if (isUseMaterial3FlagEnabled() && isDesktopFileHandlingFlagEnabled()) {
            actionModeOpen.assertEnabledAndVisible();
        }
        actionModeOpenWith.assertEnabledAndVisible();
    }

    @Test
    public void testActionMenu_NoOpenWith() {
        selectionDetails.canOpen = false;
        selectionDetails.hasMultipleOpeningApps = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        if (isUseMaterial3FlagEnabled() && isDesktopFileHandlingFlagEnabled()) {
            actionModeOpen.assertDisabledAndInvisible();
        }
        actionModeOpenWith.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testActionMenu_OpenWith_SingleOpeningApp() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        if (isUseMaterial3FlagEnabled()) {
            actionModeOpen.assertDisabledAndInvisible();
        }
        actionModeOpenWith.assertEnabledAndVisible();
    }

    /**
     * Open-with is disabled on files with a single opening app (because ResolverActivity would auto
     * launch that opening app).
     */
    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    @DisableFlags({Flags.FLAG_USE_NEW_OPEN_WITH})
    public void testActionMenu_NoOpenWith_SingleOpeningAppDesktop() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        if (isUseMaterial3FlagEnabled()) {
            actionModeOpen.assertEnabledAndVisible();
        }
        actionModeOpenWith.assertDisabledAndInvisible();
    }

    /** The new open-with doesn't automatically launch so we can use it for single opening apps. */
    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO, Flags.FLAG_USE_NEW_OPEN_WITH})
    public void testActionMenu_OpenWith_SingleOpeningAppDesktop() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeOpenWith.assertEnabledAndVisible();
    }

    @Test
    public void testActionMenu_Inspector_EnabledForSingleSelection() {
        features.inspector = true;
        selectionDetails.size = 1;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeInspector.assertEnabledAndVisible();
    }

    @Test
    public void testActionMenu_Inspector_DisabledForMultiSelection() {
        features.inspector = true;
        selectionDetails.size = 2;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeInspector.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_CanDeselectAll() {
        selectionDetails.size = 1;
        mFilesCount = 1;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeSelectAll.assertDisabledAndInvisible();
        mActionModeDeselectAll.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_onWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.containDirectories = true;
        selectionDetails.canPasteInto = true;
        dirDetails.hasItemsToPaste = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        mActionModeOpenInNewWindow.assertEnabledAndVisible();
        mActionModePasteIntoFolder.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_OnNonWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.containDirectories = true;
        selectionDetails.canPasteInto = false;

        mgr.updateActionMenu(testMenu, selectionDetails);

        mActionModeOpenInNewWindow.assertEnabledAndVisible();
        mActionModePasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testActionMenu_OnMultipleDirectories() {
        selectionDetails.size = 3;
        selectionDetails.containDirectories = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        mActionModeOpenInNewWindow.assertDisabledAndInvisible();
        mActionModePasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    public void testOptionMenu() {
        mgr.updateOptionMenu(testMenu);

        optionCreateDir.assertDisabledAndInvisible();
        optionDebug.assertDisabledAndInvisible();
        optionSort.assertEnabledAndVisible();
        mOptionLauncher.assertDisabledAndInvisible();
        mOptionShowHiddenFiles.assertEnabledAndVisible();
        assertTrue(testSearchManager.updateMenuCalled());
    }

    @Test
    public void testOptionMenu_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu);

        optionCreateDir.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testOptionMenu_HasManageDevice() {
        dirDetails.hasRootSettings = true;
        mgr.updateOptionMenu(testMenu);

        mOptionManageDevice.assertEnabledAndVisible();
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testOptionMenu_HasRootSettings() {
        dirDetails.hasRootSettings = true;
        mgr.updateOptionMenu(testMenu);

        optionSettings.assertEnabledAndVisible();
    }

    @Test
    public void testOptionMenu_Inspector_VisibleAndEnabled() {
        features.inspector = true;
        dirDetails.canInspectDirectory = true;
        mgr.updateOptionMenu(testMenu);
        optionInspector.assertEnabledAndVisible();
    }

    @Test
    public void testOptionMenu_Inspector_InvisibleAndDisabled() {
        features.inspector = true;
        dirDetails.canInspectDirectory = false;
        mgr.updateOptionMenu(testMenu);
        optionInspector.assertDisabledAndInvisible();
    }

    @Test
    public void testOptionMenu_ExtractAll() {
        dirDetails.isInArchive = true;
        mgr.updateOptionMenu(testMenu);
        if (isZipNgFlagEnabled()) {
            mOptionExtractAll.assertEnabledAndVisible();
        } else {
            mOptionExtractAll.assertDisabledAndInvisible();
        }
        if (isUseMaterial3FlagEnabled()) {
            optionNewWindow.assertDisabledAndInvisible();
        } else {
            optionNewWindow.assertEnabledAndVisible();
        }
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
    @EnableFlags(Flags.FLAG_DESKTOP_UX_PHASE_2_RO)
    public void testOptionMenu_EmptyArea_NoItemToPaste() {
        assumeTrue("Skip because show_copy_to_move_to_menus is true", shouldShowCopyCutMenus());

        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = true;

        mgr.updateOptionMenu(testMenu);

        mOptionPaste.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_UX_PHASE_2_RO)
    public void testOptionMenu_EmptyArea_CantCreateDoc() {
        assumeTrue("Skip because show_copy_to_move_to_menus is true", shouldShowCopyCutMenus());

        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = false;

        mgr.updateOptionMenu(testMenu);

        mOptionPaste.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_UX_PHASE_2_RO)
    public void testOptionMenu_EmptyArea_CanPaste() {
        assumeTrue("Skip because show_copy_to_move_to_menus is true", shouldShowCopyCutMenus());

        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = true;

        mgr.updateOptionMenu(testMenu);

        mOptionPaste.assertEnabledAndVisible();
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
    public void testInflateContextMenu_Files() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = false;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(getRes(R.menu.file_context_menu), inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Dirs() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = false;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(getRes(R.menu.dir_context_menu), inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Mixed() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(getRes(R.menu.mixed_context_menu), inflater.lastInflatedMenuId);
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea() {
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
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_EmptyArea_CanDeselectAll() {
        selectionDetails.size = 1;
        mFilesCount = 1;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertDisabledAndInvisible();
        mDirDeselectAll.assertEnabledAndVisible();
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
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_CanPaste() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirPasteFromClipboard.assertEnabledAndVisible();
        dirCreateDir.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_EmptyArea_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;

        mgr.updateContextMenuForContainer(testMenu, selectionDetails);

        dirSelectAll.assertEnabledAndVisible();
        dirPasteFromClipboard.assertDisabledAndInvisible();
        dirCreateDir.assertEnabledAndVisible();
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
        selectionDetails.size = 1;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpen.assertDisabledAndInvisible();
        dirCutToClipboard.assertDisabledAndInvisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndInvisible();
        dirRename.assertDisabledAndInvisible();
        dirCreateDir.assertEnabledAndVisible();
        dirDelete.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testContextMenu_containsDocumentsWithUnavailableContent() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.containPartial = false;
        selectionDetails.canDelete = true;
        features.inspector = true;
        selectionDetails.size = 1;
        selectionDetails.canExtract = false;
        features.archiveCreation = true;
        dirDetails.canCreateDoc = true;
        dirDetails.canInspectDirectory = true;

        mgr.updateContextMenu(testMenu, selectionDetails);

        // Items that are normally enabled are disabled (but visible) when the content is
        // unavailable.
        dirCutToClipboard.assertDisabledAndVisible();
        dirCopyToClipboard.assertDisabledAndVisible();
        dirDelete.assertEnabledAndVisible();
        dirInspect.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndVisible();

        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);

        // Items that are normally disabled will remain invisible.
        dirCutToClipboard.assertDisabledAndInvisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API, FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({
        Flags.FLAG_CLOUD_FEATURES,
        Flags.FLAG_USE_MATERIAL3,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO
    })
    public void testContextMenu_containsDocumentsWithUnavailableContent_trash() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.canTrash = true;
        selectionDetails.canRestore = true;

        mgr.updateContextMenu(testMenu, selectionDetails);

        // These trash items should be disabled but remain visible as the actions are only
        // temporarily unavailable.
        mDirMoveToTrash.assertDisabledAndVisible();
        mDirRestoreFromTrash.assertDisabledAndVisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags({Flags.FLAG_CLOUD_FEATURES})
    public void testContextMenu_containsDocumentsWithUnavailableContent_cloudFeaturesDisabled() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.containPartial = false;
        selectionDetails.canDelete = true;
        features.inspector = true;
        selectionDetails.size = 1;
        selectionDetails.canExtract = false;
        features.archiveCreation = true;
        dirDetails.canCreateDoc = true;
        dirDetails.canInspectDirectory = true;

        mgr.updateContextMenu(testMenu, selectionDetails);

        // Items that are normally enabled should remain enabled when the content is unavailable
        // but the feature flag is off.
        dirCutToClipboard.assertEnabledAndVisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        dirDelete.assertEnabledAndVisible();
        dirInspect.assertEnabledAndVisible();
        mDirCompress.assertEnabledAndVisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ZIP_NG_RO})
    public void testContextMenuForFiles_containsDocumentsWithUnavailableContent() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.containDirectories = false;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canRestore = false;
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;
        selectionDetails.canRename = true;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        // Items that are normally enabled are disabled (but visible) when the content is
        // unavailable.
        dirShare.assertDisabledAndVisible();
        dirOpenWith.assertDisabledAndVisible();
        dirRename.assertEnabledAndVisible();

        selectionDetails.isArchive = true;
        dirDetails.canCreateDirectory = true;
        dirDetails.isInArchive = false;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        mDirExtractHere.assertDisabledAndVisible();
        mDirBrowse.assertDisabledAndVisible();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ZIP_NG_RO})
    @DisableFlags({Flags.FLAG_CLOUD_FEATURES})
    public void testContextMenuForFiles_containsDocumentsWithUnavailableContent_disabled() {
        selectionDetails.containsDocumentsWithUnavailableContent = true;
        selectionDetails.containDirectories = false;
        selectionDetails.containPartial = false;
        selectionDetails.canExtract = false;
        selectionDetails.canRestore = false;
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;
        selectionDetails.canRename = true;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        // Items that are normally enabled should remain enabled when the content is unavailable
        // but the feature flag is off.
        dirShare.assertEnabledAndVisible();
        dirOpenWith.assertEnabledAndVisible();
        dirRename.assertEnabledAndVisible();

        selectionDetails.isArchive = true;
        dirDetails.canCreateDirectory = true;
        dirDetails.isInArchive = false;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        mDirExtractHere.assertEnabledAndVisible();
        mDirBrowse.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    @DisableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_OnFile_CanOpen() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpen.assertDisabledAndInvisible();
        dirOpenWith.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_OnFile_CanOpenDesktop() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = true;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpen.assertEnabledAndVisible();
        dirOpenWith.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnFile_NoOpen() {
        selectionDetails.canOpen = false;
        selectionDetails.hasMultipleOpeningApps = true;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpen.assertDisabledAndInvisible();
        dirOpenWith.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_OnFile_OpenWith_SingleOpeningApp() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpenWith.assertEnabledAndVisible();
    }

    /**
     * Open-with is disabled on files with a single opening app (because ResolverActivity would auto
     * launch that opening app).
     */
    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    @DisableFlags({Flags.FLAG_USE_NEW_OPEN_WITH})
    public void testContextMenu_OnFile_NoOpenWith_SingleOpeningAppDesktop() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpenWith.assertDisabledAndInvisible();
    }

    /** The new open-with doesn't automatically launch so we can use it for single opening apps. */
    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO, Flags.FLAG_USE_NEW_OPEN_WITH})
    public void testContextMenu_OnFile_OpenWith_SingleOpeningAppDesktop() {
        selectionDetails.canOpen = true;
        selectionDetails.hasMultipleOpeningApps = false;

        mgr.updateContextMenuForFiles(testMenu, selectionDetails);

        dirOpenWith.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMultipleFiles() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpen.assertDisabledAndInvisible();
        mDirCompress.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = true;
        selectionDetails.containDirectories = true;
        dirDetails.hasItemsToPaste = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertEnabledAndVisible();
        dirCutToClipboard.assertDisabledAndInvisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndInvisible();
        dirPasteIntoFolder.assertEnabledAndVisible();
        dirRename.assertDisabledAndInvisible();
        dirDelete.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnNonWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = false;
        selectionDetails.containDirectories = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertEnabledAndVisible();
        dirCutToClipboard.assertDisabledAndInvisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndInvisible();
        dirPasteIntoFolder.assertDisabledAndInvisible();
        dirRename.assertDisabledAndInvisible();
        dirDelete.assertDisabledAndInvisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_CanInspectContainer() {
        features.inspector = true;
        dirDetails.canInspectDirectory = true;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);
        dirInspect.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_CantInspectRecents() {
        features.inspector = true;

        dirDetails.isInRecents = true;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);
        dirInspect.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_CantInspectTrash() {
        features.inspector = true;

        dirDetails.isTrashTopLevel = true;
        mgr.updateContextMenuForContainer(testMenu, selectionDetails);
        dirInspect.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnWritableDirectory_NothingToPaste() {
        selectionDetails.canPasteInto = true;
        selectionDetails.size = 1;
        dirDetails.hasItemsToPaste = false;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirPasteIntoFolder.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMultipleDirectories() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertDisabledAndInvisible();
        mDirCompress.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMixedDocs() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertEnabledAndVisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndInvisible();
        dirDelete.assertEnabledAndVisible();
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
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
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnMixedDocs_hasUndeletableFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertDisabledAndInvisible();
        dirCopyToClipboard.assertEnabledAndVisible();
        mDirCompress.assertDisabledAndInvisible();
        dirDelete.assertDisabledAndInvisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_CanInspectSingleSelection() {
        features.inspector = true;
        selectionDetails.size = 1;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirInspect.assertEnabledAndVisible();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void testContextMenu_OnArchive() {
        selectionDetails.size = 1;
        selectionDetails.containFiles = true;
        selectionDetails.isArchive = true;
        selectionDetails.containsFilesInArchive = false;
        dirDetails.isInArchive = false;
        dirDetails.canCreateDirectory = true;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        if (isZipNgFlagEnabled()) {
            mDirExtractHere.assertEnabledAndVisible();
            mDirBrowse.assertEnabledAndVisible();
        } else {
            mDirExtractHere.assertDisabledAndInvisible();
            mDirBrowse.assertDisabledAndInvisible();
        }

        // On archive in read-only directory (but not a nested archive)
        selectionDetails.containsFilesInArchive = false;
        dirDetails.isInArchive = false;
        dirDetails.canCreateDirectory = false;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        mDirExtractHere.assertDisabledAndInvisible();
        if (isZipNgFlagEnabled()) {
            mDirBrowse.assertEnabledAndVisible();
        } else {
            mDirBrowse.assertDisabledAndInvisible();
        }

        // On nested archive
        selectionDetails.containsFilesInArchive = true;
        dirDetails.isInArchive = true;
        dirDetails.canCreateDirectory = false;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        mDirExtractHere.assertDisabledAndInvisible();
        mDirBrowse.assertDisabledAndInvisible();
    }

    private void testRootContextMenu() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;

        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
        rootOpenInNewWindow.assertEnabledAndVisible();
        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRootContextMenu_material3() {
        testRootContextMenu();
        mRootManageDevice.assertDisabledAndInvisible();
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRootContextMenu_non_material3() {
        testRootContextMenu();
        rootSettings.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRootContextMenu_HasManageDevice() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        mRootManageDevice.assertEnabledAndVisible();
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testRootContextMenu_HasRootSettings() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootSettings.assertEnabledAndVisible();
    }

    @Test
    public void testRootContextMenu_NonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_NothingToPaste() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_PasteIntoWritableRoot() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = true;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertEnabledAndVisible();
    }

    @Test
    public void testRootContextMenu_Eject() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertEnabledAndVisible();
    }

    @Test
    public void testRootContextMenu_EjectInProcess() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        testRootInfo.ejecting = true;
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
    }

    @Test
    public void testRootContextMenu_InspectDisabled() {
        mgr.updateSidebarItemContextMenu(testMenu, testRootInfo, testDocInfo);

        mRootInspector.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_ShortcutSupportsCreate() {
        mTestShortcutInfo.getRoot().flags = Root.FLAG_SUPPORTS_CREATE;

        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
        rootOpenInNewWindow.assertEnabledAndVisible();
        rootPasteIntoFolder.assertDisabledAndInvisible();
        mRootManageDevice.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_HasManageDevice() {
        mTestShortcutInfo.getRoot().flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, null);

        mRootManageDevice.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_NonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_NothingToPaste() {
        mTestShortcutInfo.getRoot().flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, testDocInfo);

        rootPasteIntoFolder.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_PasteIntoWritableRoot() {
        mTestShortcutInfo.getRoot().flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = true;
        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, testDocInfo);

        rootPasteIntoFolder.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_ShortcutDoesNotEject() {
        mTestShortcutInfo.getRoot().flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateSidebarItemContextMenu(testMenu, mTestShortcutInfo, testDocInfo);

        rootEjectRoot.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testShortcutContextMenu_InspectEnabled() {
        ShortcutInfo homeScreenShortcut =
                TestProvidersAccess.HOME_SCREEN_SHORTCUT.copyShortcutInfo();
        mgr.updateSidebarItemContextMenu(testMenu, homeScreenShortcut, testDocInfo);

        mRootInspector.assertEnabledAndVisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testActionMenu_canTrash_enabled() {
        selectionDetails.canTrash = false;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeTrash.assertDisabledAndInvisible();

        selectionDetails.canTrash = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeTrash.assertEnabledAndVisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testActionMenu_canTrash_disabled() {
        selectionDetails.canTrash = false;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeTrash.assertDisabledAndInvisible();

        selectionDetails.canTrash = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        // If the flag is disabled, the menu item will not be visible
        mActionModeTrash.assertDisabledAndInvisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testActionMenu_canRestoreFromTrash_enabled() {
        selectionDetails.canRestore = false;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeRestoreFromTrash.assertDisabledAndInvisible();

        selectionDetails.canRestore = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeRestoreFromTrash.assertEnabledAndVisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testActionMenu_canRestoreFromTrash_disabled() {
        selectionDetails.canRestore = false;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeRestoreFromTrash.assertDisabledAndInvisible();

        selectionDetails.canRestore = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        mActionModeRestoreFromTrash.assertDisabledAndInvisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testContextMenu_canTrash_enabled() {
        selectionDetails.canTrash = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirMoveToTrash.assertDisabledAndInvisible();

        selectionDetails.canTrash = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirMoveToTrash.assertEnabledAndVisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testContextMenu_canTrash_disabled() {
        selectionDetails.canTrash = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirMoveToTrash.assertDisabledAndInvisible();

        selectionDetails.canTrash = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        // If the flag is disabled, the menu item will not be visible
        mDirMoveToTrash.assertDisabledAndInvisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testContextMenu_canRestoreFromTrash_enabled() {
        selectionDetails.canRestore = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirRestoreFromTrash.assertDisabledAndInvisible();

        selectionDetails.canRestore = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirRestoreFromTrash.assertEnabledAndVisible();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testContextMenu_canRestoreFromTrash_disabled() {
        selectionDetails.canRestore = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirRestoreFromTrash.assertDisabledAndInvisible();

        selectionDetails.canRestore = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        mDirRestoreFromTrash.assertDisabledAndInvisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_useApprovedDocumentHandlerEnabled() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        // Check that the approved document handler menu item is enabled and visible.
        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNotNull("Approved document handler menu item not found.", item);
        item.assertEnabledAndVisible();
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_useApprovedDocumentHandlerDisabled() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        // Check that the approved document handler menu item is not added.
        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNull("Approved document handler menu item should not be added.", item);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_useApprovedDocumentHandlerEnabled() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        // Check that the approved document handler menu item is enabled and visible.
        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNotNull("Approved document handler menu item not found.", item);
        item.assertEnabledAndVisible();
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_removesApprovedDocumentHandler_whenNoFileSelected() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNotNull("Approved document handler menu item should be added.", item);

        mActionHandler.returnNullApprovedHandlerIntent = true;
        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        // Check that the approved document handler menu item is removed.
        item = findItemByTitle(testMenu, "Test App");
        assertNull("Approved document handler menu item should be removed.", item);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_removesApprovedDocumentHandler_whenNoFileSelected() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNotNull("Approved document handler menu item should be added.", item);

        mActionHandler.returnNullApprovedHandlerIntent = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        item = findItemByTitle(testMenu, "Test App");
        assertNull("Approved document handler menu item should be removed.", item);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_useApprovedDocumentHandlerDisabled() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        testResources.stringArrays.put(
                R.array.approved_document_handlers, new String[] {"com.test.package"});
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        // Check that the approved document handler menu item is not added.
        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNull("Approved document handler menu item should not be added.", item);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_approvedDocumentHandler_noDuplicates() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        // First update triggers the load.
        mgr.updateContextMenu(testMenu, selectionDetails);
        // Advance scheduler to complete load and trigger observer update.
        testScheduler.advanceUntilIdle();
        // Call update again to ensure no duplicates.
        mgr.updateContextMenu(testMenu, selectionDetails);

        int foundCount = countAllMenuItems(testMenu, "Test App");
        assertEquals(
                "Approved document handler menu item should be present exactly once.",
                1,
                foundCount);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_approvedDocumentHandler_limitsNumberOfMenuItems() {
        ResolveInfo info1 = createResolveInfo("com.test.package1", "class1", "App 1");
        ResolveInfo info2 = createResolveInfo("com.test.package2", "class2", "App 2");
        ResolveInfo info3 = createResolveInfo("com.test.package3", "class3", "App 3");
        ResolveInfo info4 = createResolveInfo("com.test.package4", "class4", "App 4");

        mPackageManager.queryIntentActivitiesResults.put(
                "text/plain", Arrays.asList(info1, info2, info3, info4));

        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        int foundCount = countAllMenuItems(testMenu, "App ");
        assertEquals(
                "Should limit the number of approved document handlers as menu items",
                ApprovedDocMenuController.NUMBER_OF_MENU_ITEMS,
                foundCount);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_approvedDocumentHandler_limitsNumberOfButtons() {
        ResolveInfo info1 = createButtonResolveInfo("com.test.package1", "class1", "App 1");
        ResolveInfo info2 = createButtonResolveInfo("com.test.package2", "class2", "App 2");
        ResolveInfo info3 = createResolveInfo("com.test.package3", "class3", "App 3");
        ResolveInfo info4 = createResolveInfo("com.test.package4", "class4", "App 4");

        mPackageManager.queryIntentActivitiesResults.put(
                "text/plain", Arrays.asList(info1, info2, info3, info4));

        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        int foundCount = countAllMenuItems(testMenu, "App ");
        int buttonCount = countActionButtons(testMenu, "App ");

        assertEquals(
                "Should limit the total number of approved document handlers in the menu including"
                    + " buttons",
                ApprovedDocMenuController.NUMBER_OF_BUTTONS +
                    ApprovedDocMenuController.NUMBER_OF_MENU_ITEMS, foundCount);

        assertEquals(
                "Should limit the number of action buttons to NUMBER_OF_BUTTONS",
                ApprovedDocMenuController.NUMBER_OF_BUTTONS,
                buttonCount);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_approvedDocumentHandler_limitsNumberOfMenuItems() {
        ResolveInfo info1 = createResolveInfo("com.test.package1", "class1", "App 1");
        ResolveInfo info2 = createResolveInfo("com.test.package2", "class2", "App 2");
        ResolveInfo info3 = createResolveInfo("com.test.package3", "class3", "App 3");
        ResolveInfo info4 = createResolveInfo("com.test.package4", "class4", "App 4");

        mPackageManager.queryIntentActivitiesResults.put(
                "text/plain", Arrays.asList(info1, info2, info3, info4));

        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        int foundCount = countAllMenuItems(testMenu, "App ");
        assertEquals(
                "Should limit the number of approved document handlers as menu items",
                ApprovedDocMenuController.NUMBER_OF_MENU_ITEMS,
                foundCount);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testActionMenu_approvedDocumentHandler_limitsNumberOfButtons() {
        ResolveInfo info1 = createButtonResolveInfo("com.test.package1", "class1", "App 1");
        ResolveInfo info2 = createButtonResolveInfo("com.test.package2", "class2", "App 2");
        ResolveInfo info3 = createResolveInfo("com.test.package3", "class3", "App 3");
        ResolveInfo info4 = createResolveInfo("com.test.package4", "class4", "App 4");

        mPackageManager.queryIntentActivitiesResults.put(
                "text/plain", Arrays.asList(info1, info2, info3, info4));

        mgr.updateActionMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        int foundCount = countAllMenuItems(testMenu, "App ");
        int buttonCount = countActionButtons(testMenu, "App ");

        assertEquals(
                "Should limit the total number of approved document handlers in the menu including"
                        + " buttons",
                ApprovedDocMenuController.NUMBER_OF_BUTTONS +
                    ApprovedDocMenuController.NUMBER_OF_MENU_ITEMS, foundCount);

        assertEquals(
                "Should limit the number of action buttons to NUMBER_OF_BUTTONS",
                ApprovedDocMenuController.NUMBER_OF_BUTTONS,
                buttonCount);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testContextMenu_approvedDocumentHandler_disabledWhileUpdating() {
        doReturn("Test App").when(activityInfo).loadLabel(any());
        mPackageManager.queryIntentActivitiesResults.put("text/plain", Arrays.asList(resolveInfo));

        // Initial load to populate cache.
        mgr.updateContextMenu(testMenu, selectionDetails);
        testScheduler.advanceUntilIdle();

        TestMenuItem item = findItemByTitle(testMenu, "Test App");
        assertNotNull("Item should be present after initial load", item);
        item.assertEnabledAndVisible();

        ArgumentCaptor<LauncherApps.Callback> callbackCaptor =
                ArgumentCaptor.forClass(LauncherApps.Callback.class);
        verify(activity.launcherApps)
                .registerCallback(callbackCaptor.capture(), any());
        LauncherApps.Callback callback = callbackCaptor.getValue();

        // Reset interactions on the mock item after initial setup
        reset(item);

        // Mark as outdated via callback.
        callback.onPackageChanged("com.test.package", Process.myUserHandle());

        // Let the entire sequence of updates complete.
        testScheduler.advanceUntilIdle();

        // Verify the calls to setEnabled on the mock item.
        item = findItemByTitle(testMenu, "Test App"); // Get the same item instance
        assertNotNull("Item should still be present", item);

        InOrder inOrder = inOrder(item);
        // It should have been disabled first due to the outdated cache
        inOrder.verify(item).setEnabled(false);
        // Then re-enabled after the background refresh
        inOrder.verify(item).setEnabled(true);

        // Finally, check the end state
        assertTrue("Item should be visible at the end", item.isVisible());
        assertTrue("Item should be enabled at the end", item.isEnabled());
    }
}
