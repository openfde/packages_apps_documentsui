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
package com.android.documentsui.testing;

import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.ContentResolver;
import android.content.pm.ProviderInfo;
import android.os.Process;
import android.os.UserHandle;
import android.provider.DocumentsContract.Root;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.documentsui.InspectorProvider;
import com.android.documentsui.R;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.ProvidersAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class TestProvidersAccess implements ProvidersAccess {
    private static final String TAG = "TestProvidersAccess";

    public static final UserHandle USER_HANDLE = Process.myUserHandle();
    public static final UserId USER_ID = UserId.of(USER_HANDLE);

    public static final RootInfo DOWNLOADS;
    public static final RootInfo HOME;
    public static final RootInfo HAMMY;
    public static final RootInfo PICKLES;
    public static final RootInfo PEPPER;
    public static final RootInfo RECENTS;
    public static final RootInfo TRASH_ROOT;
    public static final RootInfo INSPECTOR;
    public static final RootInfo IMAGE;
    public static final RootInfo AUDIO;
    public static final RootInfo VIDEO;
    public static final RootInfo DOCUMENT;
    public static final RootInfo EXTERNALSTORAGE;

    public static final RootInfo NO_TREE_ROOT;
    public static final RootInfo SD_CARD;
    public static final RootInfo LOCAL_SEARCH;
    public static final ShortcutInfo HOME_SCREEN_SHORTCUT;
    public static final ShortcutInfo LIVE_IMAGES_SHORTCUT;
    public static final ShortcutInfo TEST_SHORTCUT;
    private static final int HOME_SCREEN_ICON_RES_ID =
            getRes(R.drawable.ic_root_homescreen);
    private static final int LIVE_IMAGES_ICON_RES_ID = 12;
    private static final int TEST_ICON_RES_ID = 15;
    // Some DocumentsProviders including DOWNLOADS have Root DOCUMENT_ID == ROOT_ID
    private static final String DOWNLOADS_DOC_ID = Providers.ROOT_ID_DOWNLOADS;
    private static final String HOME_SCREEN_DOC_ID = "primary%3AHome screen";
    public static final String LIVE_IMAGES_DOC_ID = "images_root%3ALive images";
    public static final String TEST_SHORTCUT_DOC_ID = "pepper%3ATest Shortcut";
    public static final String HOME_SCREEN_GERMAN_TITLE = "Startbildschirm";
    public static final String LIVE_IMAGES_GERMAN_TITLE = "Live-Bilder";
    public static final String TEST_SHORTCUT_GERMAN_TITLE = "Testverknüpfung";

    static {
        UserId userId = TestProvidersAccess.USER_ID;

        DOWNLOADS = new RootInfo() {{
            flags = Root.FLAG_SUPPORTS_CREATE;
        }};
        DOWNLOADS.userId = userId;
        DOWNLOADS.authority = Providers.AUTHORITY_DOWNLOADS;
        DOWNLOADS.rootId = Providers.ROOT_ID_DOWNLOADS;
        DOWNLOADS.title = "Downloads";
        DOWNLOADS.derivedType = SidebarEntryItemInfo.TYPE_DOWNLOADS;
        DOWNLOADS.flags =
                Root.FLAG_LOCAL_ONLY
                        | Root.FLAG_SUPPORTS_CREATE
                        | Root.FLAG_SUPPORTS_RECENTS
                        | RootInfo.FLAG_QUERY_SUPPORTS_TRASH;
        // This DocumentsProvider supports limiting the results returned
        DOWNLOADS.queryArgs = ContentResolver.QUERY_ARG_LIMIT;
        DOWNLOADS.documentId = DOWNLOADS_DOC_ID;

        HOME = new RootInfo();
        HOME.userId = userId;
        HOME.authority = Providers.AUTHORITY_STORAGE;
        HOME.rootId = Providers.ROOT_ID_HOME;
        HOME.title = "Home";
        HOME.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
        HOME.flags =
                Root.FLAG_LOCAL_ONLY
                        | Root.FLAG_SUPPORTS_CREATE
                        | Root.FLAG_SUPPORTS_IS_CHILD
                        | Root.FLAG_SUPPORTS_RECENTS
                        | RootInfo.FLAG_QUERY_SUPPORTS_TRASH;

        HAMMY = new RootInfo();
        HAMMY.userId = userId;
        HAMMY.authority = "yummies";
        HAMMY.rootId = "hamsandwich";
        HAMMY.title = "Ham Sandwich";
        HAMMY.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
        HAMMY.flags = Root.FLAG_LOCAL_ONLY;

        PICKLES = new RootInfo();
        PICKLES.userId = userId;
        PICKLES.authority = "yummies";
        PICKLES.rootId = "pickles";
        PICKLES.title = "Pickles";
        PICKLES.summary = "Yummy pickles";

        PEPPER = new RootInfo();
        PEPPER.userId = userId;
        PEPPER.authority = "peppery";
        PEPPER.rootId = "pepper";
        PEPPER.title = "Pepper";
        PEPPER.flags = Root.FLAG_SUPPORTS_CREATE;

        RECENTS = new RootInfo() {
            {
                // Special root for recents
                derivedType = SidebarEntryItemInfo.TYPE_RECENTS;
                flags = Root.FLAG_LOCAL_ONLY;
                availableBytes = -1;
            }
        };
        RECENTS.userId = userId;
        RECENTS.title = "Recents";

        TRASH_ROOT = new RootInfo() {
            {
                // Special root for trash
                rootId = Providers.TRASH_ROOT_ID;
                derivedType = SidebarEntryItemInfo.TYPE_TRASH;
                flags = Root.FLAG_LOCAL_ONLY;
                availableBytes = -1;
            }
        };
        TRASH_ROOT.userId = userId;
        TRASH_ROOT.title = "Trash";

        INSPECTOR = new RootInfo();
        INSPECTOR.userId = userId;
        INSPECTOR.authority = InspectorProvider.AUTHORITY;
        INSPECTOR.rootId = InspectorProvider.ROOT_ID;
        INSPECTOR.title = "Inspector";
        INSPECTOR.flags = Root.FLAG_LOCAL_ONLY
                | Root.FLAG_SUPPORTS_CREATE;

        IMAGE = new RootInfo();
        IMAGE.userId = userId;
        IMAGE.authority = Providers.AUTHORITY_MEDIA;
        IMAGE.rootId = Providers.ROOT_ID_IMAGES;
        IMAGE.title = "Images";
        IMAGE.derivedType = SidebarEntryItemInfo.TYPE_IMAGES;

        AUDIO = new RootInfo();
        AUDIO.userId = userId;
        AUDIO.authority = Providers.AUTHORITY_MEDIA;
        AUDIO.rootId = Providers.ROOT_ID_AUDIO;
        AUDIO.title = "Audio";
        AUDIO.derivedType = SidebarEntryItemInfo.TYPE_AUDIO;

        VIDEO = new RootInfo();
        VIDEO.userId = userId;
        VIDEO.authority = Providers.AUTHORITY_MEDIA;
        VIDEO.rootId = Providers.ROOT_ID_VIDEOS;
        VIDEO.title = "Videos";
        VIDEO.derivedType = SidebarEntryItemInfo.TYPE_VIDEO;

        DOCUMENT = new RootInfo();
        DOCUMENT.userId = userId;
        DOCUMENT.authority = Providers.AUTHORITY_MEDIA;
        DOCUMENT.rootId = Providers.ROOT_ID_DOCUMENTS;
        DOCUMENT.title = "Documents";
        DOCUMENT.derivedType = SidebarEntryItemInfo.TYPE_DOCUMENTS;

        EXTERNALSTORAGE = new RootInfo();
        EXTERNALSTORAGE.userId = userId;
        EXTERNALSTORAGE.authority = Providers.AUTHORITY_STORAGE;
        EXTERNALSTORAGE.rootId = Providers.ROOT_ID_DEVICE;
        EXTERNALSTORAGE.title = "Device";
        EXTERNALSTORAGE.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
        EXTERNALSTORAGE.flags = Root.FLAG_LOCAL_ONLY
                | Root.FLAG_SUPPORTS_IS_CHILD;

        NO_TREE_ROOT = new RootInfo();
        NO_TREE_ROOT.userId = userId;
        NO_TREE_ROOT.authority = "no.tree.authority";
        NO_TREE_ROOT.rootId = "1";
        NO_TREE_ROOT.title = "No Tree Title";
        NO_TREE_ROOT.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
        NO_TREE_ROOT.flags = Root.FLAG_LOCAL_ONLY;

        SD_CARD = new RootInfo();
        SD_CARD.userId = userId;
        SD_CARD.authority = Providers.AUTHORITY_STORAGE;
        SD_CARD.rootId = Providers.ROOT_ID_DOCUMENTS;
        SD_CARD.title = "SD card";
        SD_CARD.derivedType = SidebarEntryItemInfo.TYPE_SD;
        SD_CARD.flags = Root.FLAG_LOCAL_ONLY
                | Root.FLAG_SUPPORTS_IS_CHILD;

        LOCAL_SEARCH = new RootInfo();
        LOCAL_SEARCH.userId = userId;
        LOCAL_SEARCH.authority = "com.android.documentsui.testing.localsearch";
        LOCAL_SEARCH.rootId = "local_search";
        LOCAL_SEARCH.title = "Local Search";
        LOCAL_SEARCH.derivedType = RootInfo.TYPE_LOCAL;
        LOCAL_SEARCH.flags = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_SEARCH;

        HOME_SCREEN_SHORTCUT =
                new ShortcutInfo(
                        EXTERNALSTORAGE,
                        "primary:",
                        Providers.HOME_SCREEN_SHORTCUT_TITLE,
                        Providers.HOME_SCREEN_SHORTCUT_TITLE,
                        HOME_SCREEN_ICON_RES_ID);
        HOME_SCREEN_SHORTCUT.setDocumentId(HOME_SCREEN_DOC_ID);

        LIVE_IMAGES_SHORTCUT =
                new ShortcutInfo(
                        IMAGE,
                        "something/to/image:",
                        "Live images",
                        "Live images",
                        LIVE_IMAGES_ICON_RES_ID);
        LIVE_IMAGES_SHORTCUT.setDocumentId(LIVE_IMAGES_DOC_ID);

        TEST_SHORTCUT =
                new ShortcutInfo(
                        PEPPER,
                        "some parent dir",
                        "shortcut in pepper",
                        "shortcut in pepper",
                        TEST_ICON_RES_ID);
        TEST_SHORTCUT.setDocumentId(TEST_SHORTCUT_DOC_ID);
    }

    public static class OtherUser {
        public static final UserHandle USER_HANDLE = UserHandle.of(
                TestProvidersAccess.USER_ID.getIdentifier() + 1);
        public static final UserId USER_ID = UserId.of(OtherUser.USER_HANDLE);

        public static final RootInfo DOWNLOADS;
        public static final RootInfo HOME;
        public static final RootInfo IMAGE;
        public static final RootInfo PICKLES;
        public static final RootInfo MTP_ROOT;
        public static final ShortcutInfo LIVE_IMAGES_SHORTCUT;

        static {
            UserId userId = OtherUser.USER_ID;

            DOWNLOADS = new RootInfo();
            DOWNLOADS.userId = userId;
            DOWNLOADS.authority = Providers.AUTHORITY_DOWNLOADS;
            DOWNLOADS.rootId = Providers.ROOT_ID_DOWNLOADS;
            DOWNLOADS.title = "Downloads";
            DOWNLOADS.derivedType = SidebarEntryItemInfo.TYPE_DOWNLOADS;
            DOWNLOADS.flags = Root.FLAG_LOCAL_ONLY
                    | Root.FLAG_SUPPORTS_CREATE
                    | Root.FLAG_SUPPORTS_RECENTS;
            DOWNLOADS.documentId = DOWNLOADS_DOC_ID;

            HOME = new RootInfo();
            HOME.userId = userId;
            HOME.authority = Providers.AUTHORITY_STORAGE;
            HOME.rootId = Providers.ROOT_ID_HOME;
            HOME.title = "Home";
            HOME.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
            HOME.flags = Root.FLAG_LOCAL_ONLY
                    | Root.FLAG_SUPPORTS_CREATE
                    | Root.FLAG_SUPPORTS_IS_CHILD
                    | Root.FLAG_SUPPORTS_RECENTS;

            IMAGE = new RootInfo();
            IMAGE.userId = userId;
            IMAGE.authority = Providers.AUTHORITY_MEDIA;
            IMAGE.rootId = Providers.ROOT_ID_IMAGES;
            IMAGE.title = "Images";
            IMAGE.derivedType = SidebarEntryItemInfo.TYPE_IMAGES;

            PICKLES = new RootInfo();
            PICKLES.userId = userId;
            PICKLES.authority = "yummies";
            PICKLES.rootId = "pickles";
            PICKLES.title = "Pickles";
            PICKLES.summary = "Yummy pickles";

            MTP_ROOT = new RootInfo();
            MTP_ROOT.userId = userId;
            MTP_ROOT.authority = Providers.AUTHORITY_MTP;
            MTP_ROOT.rootId = Providers.ROOT_ID_DOCUMENTS;
            MTP_ROOT.title = "MTP";
            MTP_ROOT.derivedType = SidebarEntryItemInfo.TYPE_MTP;
            MTP_ROOT.flags =
                    Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD;

            LIVE_IMAGES_SHORTCUT =
                    new ShortcutInfo(
                            IMAGE,
                            "something/to/image:",
                            "Live images",
                            "Live images",
                            LIVE_IMAGES_ICON_RES_ID);
            LIVE_IMAGES_SHORTCUT.setDocumentId(LIVE_IMAGES_DOC_ID);
        }
    }

    public static class AnotherUser {
        public static final UserHandle USER_HANDLE = UserHandle.of(
                TestProvidersAccess.USER_ID.getIdentifier() + 2);
        public static final UserId USER_ID = UserId.of(AnotherUser.USER_HANDLE);

        public static final RootInfo DOWNLOADS;
        public static final RootInfo HOME;
        public static final RootInfo IMAGE;
        public static final RootInfo PICKLES;
        public static final RootInfo MTP_ROOT;

        static {
            UserId userId = AnotherUser.USER_ID;

            DOWNLOADS = new RootInfo();
            DOWNLOADS.userId = userId;
            DOWNLOADS.authority = Providers.AUTHORITY_DOWNLOADS;
            DOWNLOADS.rootId = Providers.ROOT_ID_DOWNLOADS;
            DOWNLOADS.title = "Downloads";
            DOWNLOADS.derivedType = SidebarEntryItemInfo.TYPE_DOWNLOADS;
            DOWNLOADS.flags = Root.FLAG_LOCAL_ONLY
                    | Root.FLAG_SUPPORTS_CREATE
                    | Root.FLAG_SUPPORTS_RECENTS;
            DOWNLOADS.documentId = DOWNLOADS_DOC_ID;

            HOME = new RootInfo();
            HOME.userId = userId;
            HOME.authority = Providers.AUTHORITY_STORAGE;
            HOME.rootId = Providers.ROOT_ID_HOME;
            HOME.title = "Home";
            HOME.derivedType = SidebarEntryItemInfo.TYPE_LOCAL;
            HOME.flags = Root.FLAG_LOCAL_ONLY
                    | Root.FLAG_SUPPORTS_CREATE
                    | Root.FLAG_SUPPORTS_IS_CHILD
                    | Root.FLAG_SUPPORTS_RECENTS;

            IMAGE = new RootInfo();
            IMAGE.userId = userId;
            IMAGE.authority = Providers.AUTHORITY_MEDIA;
            IMAGE.rootId = Providers.ROOT_ID_IMAGES;
            IMAGE.title = "Images";
            IMAGE.derivedType = SidebarEntryItemInfo.TYPE_IMAGES;

            PICKLES = new RootInfo();
            PICKLES.userId = userId;
            PICKLES.authority = "yummies";
            PICKLES.rootId = "pickles";
            PICKLES.title = "Pickles";
            PICKLES.summary = "Yummy pickles";
            PICKLES.flags = Root.FLAG_SUPPORTS_CREATE;

            MTP_ROOT = new RootInfo();
            MTP_ROOT.userId = userId;
            MTP_ROOT.authority = Providers.AUTHORITY_MTP;
            MTP_ROOT.rootId = Providers.ROOT_ID_DOCUMENTS;
            MTP_ROOT.title = "MTP";
            MTP_ROOT.derivedType = SidebarEntryItemInfo.TYPE_MTP;
            MTP_ROOT.flags = Root.FLAG_SUPPORTS_CREATE
                    | Root.FLAG_LOCAL_ONLY
                    | Root.FLAG_SUPPORTS_IS_CHILD;
        }
    }

    public final Map<String, Collection<RootInfo>> roots = new HashMap<>();
    public final Map<UserId, Collection<ShortcutInfo>> shortcuts = new HashMap<>();
    public @Nullable ProviderInfo nextProviderInfo;
    private @Nullable RootInfo nextRoot;

    public TestProvidersAccess() {
        add(DOWNLOADS);
        add(HOME);
        add(HAMMY);
        add(PICKLES);
        add(EXTERNALSTORAGE);
        add(NO_TREE_ROOT);
        add(LOCAL_SEARCH);
        add(HOME_SCREEN_SHORTCUT);
        add(TEST_SHORTCUT);
        add(LIVE_IMAGES_SHORTCUT);
        add(OtherUser.LIVE_IMAGES_SHORTCUT);
        add(getCloudRoot());
    }

    private void add(RootInfo root) {
        if (!roots.containsKey(root.authority)) {
            roots.put(root.authority, new ArrayList<>());
        }
        roots.get(root.authority).add(root);
    }

    private void add(ShortcutInfo shortcut) {
        if (!shortcuts.containsKey(shortcut.getRoot().userId)) {
            shortcuts.put(shortcut.getRoot().userId, new ArrayList<>());
        }
        shortcuts.get(shortcut.getRoot().userId).add(shortcut);
    }

    public void configurePm(TestPackageManager pm) {
        pm.addStubContentProviderForRoot(TestProvidersAccess.DOWNLOADS);
        pm.addStubContentProviderForRoot(TestProvidersAccess.HOME);
        pm.addStubContentProviderForRoot(TestProvidersAccess.HAMMY);
        pm.addStubContentProviderForRoot(TestProvidersAccess.PICKLES);
        pm.addStubContentProviderForRoot(TestProvidersAccess.EXTERNALSTORAGE);
        pm.addStubContentProviderForRoot(TestProvidersAccess.NO_TREE_ROOT);
        pm.addStubContentProviderForRoot(TestProvidersAccess.LOCAL_SEARCH);
        pm.addStubContentProviderForRoot(TestProvidersAccess.getCloudRoot());
    }

    @Override
    public RootInfo getRootOneshot(UserId userId, String authority, String rootId) {
        if (roots.containsKey(authority)) {
            for (RootInfo root : roots.get(authority)) {
                if (rootId.equals(root.rootId) && root.userId.equals(userId)) {
                    return root;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<RootInfo> getMatchingRootsBlocking(State state) {
        List<RootInfo> allRoots = new ArrayList<>();
        for (String authority : roots.keySet()) {
            allRoots.addAll(roots.get(authority));
        }
        return ProvidersAccess.getMatchingRoots(allRoots, state);
    }

    @Override
    public Collection<RootInfo> getRootsForAuthorityBlocking(UserId userId, String authority) {
        return roots.get(authority);
    }

    @Override
    public Collection<ShortcutInfo> getShortcutsForUser(UserId userId) {
        return shortcuts.get(userId);
    }

    @Override
    public Collection<RootInfo> getRootsBlocking() {
        List<RootInfo> result = new ArrayList<>();
        for (Collection<RootInfo> vals : roots.values()) {
            result.addAll(vals);
        }
        return result;
    }

    @Override
    public RootInfo getDefaultRootBlocking(State state) {
        return DOWNLOADS;
    }

    @Override
    public RootInfo getRecentsRoot(UserId userId) {
        return RECENTS;
    }

    @Override
    public RootInfo getTrashRoot(@NonNull UserId userId) {
        return TRASH_ROOT;
    }

    @Override
    public String getApplicationName(UserId userId, String authority) {
        return "Test Application";
    }

    @Override
    public String getPackageName(UserId userId, String authority) {
        return "com.android.documentsui";
    }

    @Override
    public @Nullable ProviderInfo getProviderInfo(UserId userId, String authority) {
        return nextProviderInfo;
    }

    /**
     * Returns the CLOUD root. This cannot be created statically as it is flag dependent and flags
     * may not be fully initialised during static creation.
     */
    public static RootInfo getCloudRoot() {
        RootInfo cloud = new RootInfo();
        cloud.userId = TestProvidersAccess.USER_ID;
        cloud.authority = "cloud.provider.authority";
        cloud.rootId = Providers.ROOT_ID_DEVICE;
        cloud.title = "Cloud";
        cloud.derivedType = SidebarEntryItemInfo.TYPE_ROOT_OTHER;
        if (isSyncStateEnabled()) {
            cloud.flags = Root.FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE;
        } else {
            Log.w(
                    TAG,
                    "Sync State is not enabled, not setting"
                            + " FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE");
            cloud.flags = 0;
        }
        return cloud;
    }
}
