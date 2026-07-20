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

package com.android.documentsui.sidebar;

import static android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.TestUserManagerState;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestResolveInfo;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A unit test for RootsFragment.
 */
@RunWith(Parameterized.class)
@MediumTest
public class RootsFragmentTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private DevicePolicyManager mDevicePolicyManager;
    private RootsFragment mRootsFragment;
    private TestEnv mEnv;
    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private TestUserManagerState mTestUserManagerState;
    private Resources mResources;

    private static final String[] EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_TRUE = {
        TestProvidersAccess.RECENTS.title,
        TestProvidersAccess.IMAGE.title,
        TestProvidersAccess.VIDEO.title,
        TestProvidersAccess.AUDIO.title,
        TestProvidersAccess.DOCUMENT.title,
        TestProvidersAccess.DOWNLOADS.title,
        "" /* SpacerItem */,
        TestProvidersAccess.EXTERNALSTORAGE.title,
        TestProvidersAccess.HAMMY.title,
        "" /* SpacerItem */,
        TestProvidersAccess.INSPECTOR.title,
        TestProvidersAccess.PICKLES.title
    };

    private static final String[] EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_TRUE = {
        TestProvidersAccess.RECENTS.title,
        TestProvidersAccess.HOME_SCREEN_SHORTCUT.getTitle(),
        TestProvidersAccess.IMAGE.title,
        TestProvidersAccess.VIDEO.title,
        TestProvidersAccess.AUDIO.title,
        TestProvidersAccess.DOCUMENT.title,
        TestProvidersAccess.DOWNLOADS.title,
        TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getTitle(),
        TestProvidersAccess.TEST_SHORTCUT.getTitle(),
        "" /* SpacerItem */,
        TestProvidersAccess.EXTERNALSTORAGE.title,
        TestProvidersAccess.HAMMY.title,
        TestProvidersAccess.INSPECTOR.title,
        TestProvidersAccess.PICKLES.title
    };

    private static final String[] EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_FALSE = {
        TestProvidersAccess.RECENTS.title,
        TestProvidersAccess.DOWNLOADS.title,
        "" /* SpacerItem */,
        TestProvidersAccess.EXTERNALSTORAGE.title,
        TestProvidersAccess.HAMMY.title,
        "" /* SpacerItem */,
        TestProvidersAccess.INSPECTOR.title,
        TestProvidersAccess.PICKLES.title
    };

    private static final String[]
        EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_FALSE = {
            TestProvidersAccess.RECENTS.title,
            TestProvidersAccess.HOME_SCREEN_SHORTCUT.getTitle(),
            TestProvidersAccess.DOWNLOADS.title,
            TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getTitle(),
            TestProvidersAccess.TEST_SHORTCUT.getTitle(),
            "" /* SpacerItem */,
            TestProvidersAccess.EXTERNALSTORAGE.title,
            TestProvidersAccess.HAMMY.title,
            TestProvidersAccess.INSPECTOR.title,
            TestProvidersAccess.PICKLES.title
        };

    private static final String[]
            EXPECTED_SORTED_RESULT_LOCALIZED_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_TRUE = {
        TestProvidersAccess.RECENTS.title,
        TestProvidersAccess.HOME_SCREEN_GERMAN_TITLE,
        TestProvidersAccess.IMAGE.title,
        TestProvidersAccess.VIDEO.title,
        TestProvidersAccess.AUDIO.title,
        TestProvidersAccess.DOCUMENT.title,
        TestProvidersAccess.DOWNLOADS.title,
        TestProvidersAccess.LIVE_IMAGES_GERMAN_TITLE,
        TestProvidersAccess.TEST_SHORTCUT_GERMAN_TITLE,
        "" /* SpacerItem */,
        TestProvidersAccess.EXTERNALSTORAGE.title,
        TestProvidersAccess.HAMMY.title,
        TestProvidersAccess.INSPECTOR.title,
        TestProvidersAccess.PICKLES.title
    };

    private static final String[]
            EXPECTED_SORTED_RESULT_LOCALIZED_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_FALSE = {
        TestProvidersAccess.RECENTS.title,
        TestProvidersAccess.HOME_SCREEN_GERMAN_TITLE,
        TestProvidersAccess.DOWNLOADS.title,
        TestProvidersAccess.LIVE_IMAGES_GERMAN_TITLE,
        TestProvidersAccess.TEST_SHORTCUT_GERMAN_TITLE,
        "" /* SpacerItem */,
        TestProvidersAccess.EXTERNALSTORAGE.title,
        TestProvidersAccess.HAMMY.title,
        TestProvidersAccess.INSPECTOR.title,
        TestProvidersAccess.PICKLES.title
    };

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    @Parameter(1)
    public boolean showMediaRoots;

    /**
     * Parameterizes values for {@code isPrivateSpaceEnabled} and {@code showMediaRoots} to run all
     * tests against every combination of private space and media roots visibility.
     */
    @Parameters(name = "privateSpaceEnabled={0}, showMediaRoots={1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {true, true},
                    {true, false},
                    {false, true},
                    {false, false}
                });
    }

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mEnv.state.configStore = mTestConfigStore;

        mContext = mock(Context.class);
        mDevicePolicyManager = mock(DevicePolicyManager.class);
        mPackageManager = mock(PackageManager.class);
        mResources = mock(Resources.class);
        when(mContext.getResources()).thenReturn(mResources);
        // Stub the mock to return the parametrize value of media roots visibility.
        when(mResources.getBoolean(R.bool.show_media_roots)).thenReturn(showMediaRoots);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getApplicationContext()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        if (SdkLevel.isAtLeastS() && isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mTestUserManagerState = new TestUserManagerState();
            mTestUserManagerState.canFrowardToProfileIdMap.put(UserId.DEFAULT_USER, true);
        }
        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        mRootsFragment = new RootsFragment();
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testSortLoadResult_WithCorrectOrder_useMaterial3FlagDisabled() {
        List<Item> items = mRootsFragment.sortLoadResult(
                mContext,
                mEnv.state,
                createFakeRootInfoList(),
                createFakeShortcutInfoList(),
                null /* excludePackage */, null /* handlerAppIntent */, new TestProvidersAccess(),
                UserId.DEFAULT_USER,
                Collections.singletonList(UserId.DEFAULT_USER),
                /* maybeShowBadge */ false, mTestUserManagerState);
        assertTrue(assertSortedResult(items, EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_TRUE));
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    @DisableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testSortLoadResult_WithCorrectOrder_showMediaRoots() {
        List<Item> items = mRootsFragment.sortLoadResult(
                mContext,
                mEnv.state,
                createFakeRootInfoList(),
                createFakeShortcutInfoList(),
                null /* excludePackage */, null /* handlerAppIntent */, new TestProvidersAccess(),
                UserId.DEFAULT_USER,
                Collections.singletonList(UserId.DEFAULT_USER),
                /* maybeShowBadge */ false, mTestUserManagerState);
        if (mContext.getResources().getBoolean(R.bool.show_media_roots)) {
            assertTrue(assertSortedResult(items, EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_TRUE));
        } else {
            assertTrue(assertSortedResult(items, EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_FALSE));
        }
    }

    @Test
    @EnableFlags({
        Flags.FLAG_USE_MATERIAL3,
        Flags.FLAG_USE_SEARCH_V2_READ_ONLY,
        Flags.FLAG_USE_LOCAL_SEARCH_PROVIDER
    })
    public void testSortLoadResult_localSearchConfigured() {
        final List<RootInfo> rootInfoList =
                Collections.singletonList(TestProvidersAccess.LOCAL_SEARCH);
        when(mResources.getString(R.string.local_search_provider))
                .thenReturn(TestProvidersAccess.LOCAL_SEARCH.getUri().toString());

        List<Item> items =
                mRootsFragment.sortLoadResult(
                        mContext,
                        mEnv.state,
                        rootInfoList,
                        List.of(),
                        null /* excludePackage */,
                        null /* handlerAppIntent */,
                        new TestProvidersAccess(),
                        UserId.DEFAULT_USER,
                        Collections.singletonList(UserId.DEFAULT_USER),
                        /* maybeShowBadge */ false,
                        mTestUserManagerState);

        assertEquals(0, items.size());
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_LOCAL_SEARCH_PROVIDER})
    public void testSortLoadResult_localSearchNotConfigured() {
        final List<RootInfo> rootInfoList =
                Collections.singletonList(TestProvidersAccess.LOCAL_SEARCH);
        when(mResources.getString(R.string.local_search_provider)).thenReturn("");

        List<Item> items =
                mRootsFragment.sortLoadResult(
                        mContext,
                        mEnv.state,
                        rootInfoList,
                        List.of(),
                        null /* excludePackage */,
                        null /* handlerAppIntent */,
                        new TestProvidersAccess(),
                        UserId.DEFAULT_USER,
                        Collections.singletonList(UserId.DEFAULT_USER),
                        /* maybeShowBadge */ false,
                        mTestUserManagerState);

        assertEquals(1, items.size());
        RootItem item = (RootItem) items.get(0);
        assertEquals(TestProvidersAccess.LOCAL_SEARCH.title, item.root.title);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testSortLoadResult_WithCorrectOrder_showMediaRoots_shortcutsEnabled() {
        List<Item> items = mRootsFragment.sortLoadResult(
                mContext,
                mEnv.state,
                createFakeRootInfoList(),
                createFakeShortcutInfoList(),
                null /* excludePackage */, null /* handlerAppIntent */, new TestProvidersAccess(),
                UserId.DEFAULT_USER,
                Collections.singletonList(UserId.DEFAULT_USER),
                /* maybeShowBadge */ false, mTestUserManagerState);
        if (mContext.getResources().getBoolean(R.bool.show_media_roots)) {
            assertTrue(assertSortedResult(items,
                    EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_TRUE));
        } else {
            assertTrue(assertSortedResult(items,
                    EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_FALSE));
        }
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testSortLoadResult_WithCorrectOrder_localizedShortcutsTitles() {
        List<Item> items =
                mRootsFragment.sortLoadResult(
                        mContext,
                        mEnv.state,
                        createFakeRootInfoList(),
                        createFakeLocalizedShortcutInfoList(),
                        null /* excludePackage */,
                        null /* handlerAppIntent */,
                        new TestProvidersAccess(),
                        UserId.DEFAULT_USER,
                        Collections.singletonList(UserId.DEFAULT_USER),
                        /* maybeShowBadge */ false,
                        mTestUserManagerState);
        if (mContext.getResources().getBoolean(R.bool.show_media_roots)) {
            assertTrue(assertSortedResult(items,
                    EXPECTED_SORTED_RESULT_LOCALIZED_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_TRUE));
        } else {
            assertTrue(assertSortedResult(items,
                    EXPECTED_SORTED_RESULT_LOCALIZED_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_FALSE));
        }
    }

    @Test
    @DisableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testProvidersAndAppsComp_WithCorrectOrder_HomeScreenFlagOff() {
        final String testPackageName = "com.test1";
        final String errorTestPackageName = "com.test2";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<SortableItem> rootList = new ArrayList<>();
        rootList.add(new RootItem(TestProvidersAccess.HAMMY, null /* actionHandler */,
                errorTestPackageName, /* maybeShowBadge= */ false));
        rootList.add(new RootItem(TestProvidersAccess.INSPECTOR, null /* actionHandler */,
                errorTestPackageName, /* maybeShowBadge= */ false));
        rootList.add(new RootItem(TestProvidersAccess.PICKLES, null /* actionHandler */,
                testPackageName, /* maybeShowBadge= */ false));
        Collections.sort(rootList, comp);

        assertEquals(rootList.get(0).getTitle(), TestProvidersAccess.PICKLES.title);
        assertEquals(rootList.get(1).getTitle(), TestProvidersAccess.HAMMY.title);
        assertEquals(rootList.get(2).getTitle(), TestProvidersAccess.INSPECTOR.title);
    }

    @Test
    @DisableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testProvidersAndAppsComp_differentItemTypes_WithCorrectOrder_HomeScreenFlagOff() {
        final String testPackageName = "com.test1";
        final RootsFragment.ItemComparator comp = new RootsFragment.ItemComparator(testPackageName);
        final List<SortableItem> rootList = new ArrayList<>();
        rootList.add(
                new RootItem(
                        TestProvidersAccess.HAMMY,
                        null /* actionHandler */,
                        testPackageName,
                        /* maybeShowBadge= */ false));
        rootList.add(
                new RootItem(
                        TestProvidersAccess.IMAGE,
                        null /* actionHandler */,
                        "diff.package.prefix",
                        /* maybeShowBadge= */ false));

        final ResolveInfo info = TestResolveInfo.create();
        info.activityInfo.packageName = testPackageName;

        rootList.add(
                new AppItem(
                        info,
                        TestProvidersAccess.PICKLES.title,
                        UserId.DEFAULT_USER,
                        null /* actionHandler */));
        rootList.add(
                new RootAndAppItem(
                        TestProvidersAccess.INSPECTOR,
                        info,
                        null /* actionHandler */,
                        /* maybeShowBadge= */ false));

        rootList.sort(comp);

        assertSortedResult(
                rootList,
                new String[] {
                    TestProvidersAccess.HAMMY.title,
                    TestProvidersAccess.INSPECTOR.title,
                    TestProvidersAccess.PICKLES.title,
                    TestProvidersAccess.IMAGE.title
                });
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testProvidersAndAppsComp_differentItemTypes_WithCorrectOrder_HomeScreenFlagOn() {
        final String testPackageName = "com.test1";
        final RootsFragment.SortableItemComparator comp =
                new RootsFragment.SortableItemComparator();
        final List<SortableItem> itemList = new ArrayList<>();

        itemList.add(
                new RootItem(
                        TestProvidersAccess.HAMMY,
                        null /* actionHandler */,
                        "diff.package.prefix",
                        /* maybeShowBadge= */ false));
        itemList.add(
                new ShortcutItem(
                        TestProvidersAccess.TEST_SHORTCUT,
                        null /* actionHandler */,
                        "diff.package.prefix",
                        /* maybeShowBadge= */ false));

        final ResolveInfo info = TestResolveInfo.create();
        info.activityInfo.packageName = testPackageName;

        itemList.add(
                new AppItem(
                        info,
                        TestProvidersAccess.PICKLES.title,
                        UserId.DEFAULT_USER,
                        null /* actionHandler */));
        itemList.add(
                new RootAndAppItem(
                        TestProvidersAccess.HOME,
                        info,
                        null /* actionHandler */,
                        /* maybeShowBadge= */ false));

        itemList.sort(comp);

        // BaseSidebarEntryItems (RootItem, ShortcutItem and RootAndAppItem) should go before
        // AppItems regardless of the package name prefix. These BaseSidebarEntryItems are then
        // ordered by derived type, then title.
        assertSortedResult(
                itemList,
                new String[] {
                    TestProvidersAccess.HAMMY.title,
                    TestProvidersAccess.HOME.title,
                    TestProvidersAccess.TEST_SHORTCUT.getTitle(),
                    TestProvidersAccess.PICKLES.title
                });
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @DisableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testSortLoadResult_WithCorrectOrder_trashAtBottom() {
        List<RootInfo> roots = createFakeRootInfoList();
        roots.add(TestProvidersAccess.TRASH_ROOT);

        List<Item> items =
                mRootsFragment.sortLoadResult(
                        mContext,
                        mEnv.state,
                        roots,
                        createFakeShortcutInfoList(),
                        null,
                        null,
                        new TestProvidersAccess(),
                        UserId.DEFAULT_USER,
                        Collections.singletonList(UserId.DEFAULT_USER),
                        false,
                        mTestUserManagerState);

        String[] baseExpectedResult;
        if (mContext.getResources().getBoolean(R.bool.show_media_roots)) {
            baseExpectedResult = EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_TRUE;
        } else {
            baseExpectedResult = EXPECTED_SORTED_RESULT_SHOW_MEDIA_ROOTS_FALSE;
        }

        // Add Trash, and convert back to array
        List<String> expectedList = new ArrayList<>(Arrays.asList(baseExpectedResult));
        expectedList.add("" /* SpacerItem */);
        expectedList.add(TestProvidersAccess.TRASH_ROOT.title);

        assertTrue(assertSortedResult(items, expectedList.toArray(new String[0])));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({
        Flags.FLAG_USE_MATERIAL3,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_HOME_SCREEN_FILES_RO
    })
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testSortLoadResult_WithCorrectOrder_trashAtBottom_shortcutsEnabled() {
        List<RootInfo> roots = createFakeRootInfoList();
        roots.add(TestProvidersAccess.TRASH_ROOT);

        List<Item> items =
                mRootsFragment.sortLoadResult(
                        mContext,
                        mEnv.state,
                        roots,
                        createFakeShortcutInfoList(),
                        null,
                        null,
                        new TestProvidersAccess(),
                        UserId.DEFAULT_USER,
                        Collections.singletonList(UserId.DEFAULT_USER),
                        false,
                        mTestUserManagerState);

        String[] baseExpectedResult;
        if (mContext.getResources().getBoolean(R.bool.show_media_roots)) {
            baseExpectedResult = EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_TRUE;
        } else {
            baseExpectedResult = EXPECTED_SORTED_RESULT_SHORTCUTS_ENABLED_SHOW_MEDIA_ROOTS_FALSE;
        }

        // Add Trash, and convert back to array
        List<String> expectedList = new ArrayList<>(Arrays.asList(baseExpectedResult));
        expectedList.add("" /* SpacerItem */);
        expectedList.add(TestProvidersAccess.TRASH_ROOT.title);

        assertTrue(assertSortedResult(items, expectedList.toArray(new String[0])));
    }

    private boolean assertSortedResult(List<? extends Item> items, String[] expectedSortedResult) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item instanceof SortableItem) {
                assertEquals(expectedSortedResult[i], ((SortableItem) item).getTitle());
            } else if (item instanceof SpacerItem) {
                assertTrue(expectedSortedResult[i].isEmpty());
            } else {
                return false;
            }
        }
        return true;
    }

    private List<RootInfo> createFakeRootInfoList() {
        final List<RootInfo> fakeRootInfoList = new ArrayList<>();
        fakeRootInfoList.add(TestProvidersAccess.PICKLES);
        fakeRootInfoList.add(TestProvidersAccess.HAMMY);
        fakeRootInfoList.add(TestProvidersAccess.INSPECTOR);
        fakeRootInfoList.add(TestProvidersAccess.DOWNLOADS);
        fakeRootInfoList.add(TestProvidersAccess.AUDIO);
        fakeRootInfoList.add(TestProvidersAccess.VIDEO);
        fakeRootInfoList.add(TestProvidersAccess.RECENTS);
        fakeRootInfoList.add(TestProvidersAccess.IMAGE);
        fakeRootInfoList.add(TestProvidersAccess.EXTERNALSTORAGE);
        fakeRootInfoList.add(TestProvidersAccess.DOCUMENT);
        return fakeRootInfoList;
    }

    private List<ShortcutInfo> createFakeShortcutInfoList() {
        return Arrays.asList(
                TestProvidersAccess.HOME_SCREEN_SHORTCUT,
                TestProvidersAccess.LIVE_IMAGES_SHORTCUT,
                TestProvidersAccess.TEST_SHORTCUT);
    }

    private List<ShortcutInfo> createFakeLocalizedShortcutInfoList() {
        ShortcutInfo homeScreen = TestProvidersAccess.HOME_SCREEN_SHORTCUT.copyShortcutInfo();
        homeScreen.setLocalizedDisplayTitle(TestProvidersAccess.HOME_SCREEN_GERMAN_TITLE);
        ShortcutInfo liveImages = TestProvidersAccess.LIVE_IMAGES_SHORTCUT.copyShortcutInfo();
        liveImages.setLocalizedDisplayTitle(TestProvidersAccess.LIVE_IMAGES_GERMAN_TITLE);
        ShortcutInfo testShortcut = TestProvidersAccess.TEST_SHORTCUT.copyShortcutInfo();
        testShortcut.setLocalizedDisplayTitle(TestProvidersAccess.TEST_SHORTCUT_GERMAN_TITLE);
        return Arrays.asList(homeScreen, liveImages, testShortcut);
    }
}
