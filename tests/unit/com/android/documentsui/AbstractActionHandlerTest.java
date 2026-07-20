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

import static com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;
import static com.android.documentsui.testing.IntentAsserts.assertHasData;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtra;
import static com.android.documentsui.testing.IntentAsserts.assertTargetsComponent;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;

import androidx.core.util.Preconditions;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.filters.MediumTest;

import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.LoadingHandler;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.FocusHandler;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.files.getinfo.GetInfoDialogFragment;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.loaders.LoaderIds;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestPeekViewManager;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.collect.Lists;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A unit test *for* AbstractActionHandler, not an abstract test baseclass.
 */
@RunWith(Parameterized.class)
@MediumTest
public class AbstractActionHandlerTest {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    // TODO(b/433858983): Remove CheckFlagsRule once peek is overridable in FlagUtils.
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private TestActivity mActivity;
    private TestEnv mEnv;
    private AbstractActionHandler<TestActivity> mHandler;
    private TestPeekViewManager mPeekViewManager;
    private TestFeatures mFeatures;
    @Mock private Runnable mMockCloseSelectionBar;
    @Mock private LoadingHandler mMockHandler;
    private TestActionModeAddons mActionModeAddons;

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    /** Helper to stage a file on the mock authority provider to be returned next. */
    private void setupFileOnAuthority(String displayName, String authority) {
        DocumentInfo homeDir = new DocumentInfo();
        homeDir.authority = authority;
        homeDir.documentId = "dir-required-for-file-enumeration";
        homeDir.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        mEnv.state.stack.push(homeDir);

        DocumentInfo file = new DocumentInfo();
        file.authority = authority;
        file.documentId = displayName;
        file.displayName = displayName;

        mEnv.mockProviders.get(authority).setNextChildDocumentsReturns(file);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mFeatures = new TestFeatures();
        mEnv = TestEnv.create(mFeatures);
        mActivity = TestActivity.create(mEnv);
        mActivity.userManager = UserManagers.create();
        mEnv.state.configStore = mTestConfigStore;
        mPeekViewManager = isUsePeekPreviewFlagEnabled() ? new TestPeekViewManager() : null;
        mActionModeAddons = new TestActionModeAddons();

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        }
        mHandler = createHandler(mEnv.injector.focusManager, mEnv.injector.selectionMgr);
        mHandler.reset(new ContentLock());
    }

    private AbstractActionHandler<TestActivity> createHandler(
            FocusHandler focusHandler, SelectionTracker<String> selectionMgr) {
        return new AbstractActionHandler<TestActivity>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mEnv.injector,
                mPeekViewManager,
                mActionModeAddons,
                mMockCloseSelectionBar,
                null,
                mMockHandler,
                focusHandler,
                selectionMgr) {

            @Override
            public void openRoot(RootInfo root) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean openItem(
                    ItemDetails<String> doc, @ViewType int type, @ViewType int fallback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void initLocation(Intent intent) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void launchToDefaultLocation() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Uri getDefaultFallbackUri() {
                return null;
            }
        };
    }

    @Test
    public void testOpenInNewWindow() {
        DocumentStack path = new DocumentStack(Roots.create("123"));
        mHandler.openInNewWindow(path, null);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_HOME_SCREEN_FILES_RO})
    public void testOpenInNewWindowWithShortcut() {
        ShortcutInfo shortcut = TestProvidersAccess.TEST_SHORTCUT;
        DocumentStack path = new DocumentStack(Roots.create("123"));
        mHandler.openInNewWindow(path, shortcut);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        expected.putExtra(Shared.EXTRA_SELECTED_SHORTCUT, (Parcelable) shortcut);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
        assertEquals(shortcut, actual.getParcelableExtra(Shared.EXTRA_SELECTED_SHORTCUT));
        assertEquals(path, actual.getParcelableExtra(Shared.EXTRA_STACK));
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_HOME_SCREEN_FILES_RO})
    public void testOpenInNewWindowWithShortcutNotEmptyStack() {
        ShortcutInfo shortcut = TestProvidersAccess.TEST_SHORTCUT;
        DocumentInfo doc1 = new DocumentInfo();
        doc1.derivedUri = Uri.parse("uri 1");
        DocumentInfo doc2 = new DocumentInfo();
        doc2.derivedUri = Uri.parse("uri 2");
        DocumentStack path = new DocumentStack(Roots.create("123"), doc1, doc2);
        mHandler.openInNewWindow(path, shortcut);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        expected.putExtra(Shared.EXTRA_SELECTED_SHORTCUT, (Parcelable) shortcut);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
        assertEquals(shortcut, actual.getParcelableExtra(Shared.EXTRA_SELECTED_SHORTCUT));
        assertEquals(path, actual.getParcelableExtra(Shared.EXTRA_STACK));
    }

    @Test
    public void testOpensContainerDocuments_OpenFolderInSearch_JumpsToNewLocation()
            throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(TestEnv.FOLDER_1.documentId, TestEnv.FOLDER_2.documentId));
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(mEnv.docs.nextPath.getPath().size(), mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.pop());
    }


    @Test
    public void testOpensContainerDocuments_ClickFolderInSearch_PushToRootDoc_NoFindPathSupport()
            throws Exception {
        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(2, mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.pop());
    }

    @Test
    public void testOpensContainerDocuments_ClickArchiveInSearch_opensArchiveInArchiveProvider()
            throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(TestEnv.FOLDER_1.documentId, TestEnv.FOLDER_2.documentId,
                        TestEnv.FILE_ARCHIVE.documentId));
        mEnv.docs.nextDocuments = Arrays.asList(
                TestEnv.FOLDER_1, TestEnv.FOLDER_2, TestEnv.FILE_ARCHIVE);
        mEnv.docs.nextDocument = TestEnv.FILE_IN_ARCHIVE;

        mHandler.openContainerDocument(TestEnv.FILE_ARCHIVE);

        mEnv.beforeAsserts();

        assertEquals(mEnv.docs.nextPath.getPath().size(), mEnv.state.stack.size());
        assertEquals(TestEnv.FILE_IN_ARCHIVE, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.pop());
    }

    @Test
    public void testLaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        assertTrue(mHandler.launchToDocument(TestEnv.FILE_GIF.derivedUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLaunchToDocuments_convertsTreeUriToDocumentUri() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        final Uri treeBaseUri = DocumentsContract.buildTreeDocumentUri(
                TestProvidersAccess.HOME.authority, TestEnv.FOLDER_0.documentId);
        final Uri treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeBaseUri, TestEnv.FILE_GIF.documentId);
        assertTrue(mHandler.launchToDocument(treeDocUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mEnv.docs.lastUri.assertLastArgument(TestEnv.FILE_GIF.derivedUri);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLoadChildrenDocuments() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(2, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.FILE_APK, mEnv.model.getDocument(modelIds[0]));
        assertEquals(TestEnv.FILE_GIF, mEnv.model.getDocument(modelIds[1]));
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testListFolderWithAcceptedMimeTypesSetV2() throws Exception {
        testListFolderWithAcceptedMimeTypesSetCommon();
    }

    @Test
    @DisableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testListFolderWithAcceptedMimeTypesSetV1() throws Exception {
        testListFolderWithAcceptedMimeTypesSetCommon();
    }

    // Common test that should pass for V1 and V2. Called with specific flags forced to the
    // needed state.
    private void testListFolderWithAcceptedMimeTypesSetCommon() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        // Add MIME type restrictions, which should be ignored by folder loading.
        mEnv.state.acceptMimes = new String[]{"image/*", "audio/*"};
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        Set<String> foundDocuments = new HashSet<>();
        for (String modelId : mEnv.model.getModelIds()) {
            foundDocuments.add(mEnv.model.getDocument(modelId).displayName);
        }
        assertEquals(Set.of(TestEnv.FOLDER_1.displayName, TestEnv.FOLDER_2.displayName),
                foundDocuments);
    }

    @Test
    public void testCrossProfileDocuments_success() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        // Currently mock provider does not have cross profile concept, this will always return
        // the supplied docs without checking for the user. But this should not be a problem for
        // this test case.
        mEnv.mockProviders.get(TestProvidersAccess.OtherUser.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.OtherUser.FILE_PNG);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(1, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.OtherUser.FILE_PNG, mEnv.model.getDocument(modelIds[0]));
    }

    @Test
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    // TODO(b:422900724): Remove the @DisableFlags directive.
    public void testLoadCrossProfileDoc_failsWithQuietModeException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Turn off the other user.
        when(mActivity.userManager.isQuietModeEnabled(TestProvidersAccess.OtherUser.USER_HANDLE))
                .thenReturn(true);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileQuietModeException.class);
    }

    @Test
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    // TODO(b:422900724): Remove the @DisableFlags directive.
    public void testLoadCrossProfileDoc_failsWithNoPermissionException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Disallow sharing across profile
        mEnv.state.canShareAcrossProfile = false;

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);
    }

    @Test
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    // TODO(b:422900724): Remove the @DisableFlags directive.
    public void testLoadCrossProfileDoc_bothError_showNoPermissionException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Disallow sharing
        mEnv.state.canShareAcrossProfile = false;
        // Turn off the other user.
        when(mActivity.userManager.isQuietModeEnabled(TestProvidersAccess.OtherUser.USER_HANDLE))
                .thenReturn(true);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);
    }

    @Test
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    // TODO(b:422900724): Remove the @DisableFlags directive.
    public void testCrossProfileDocuments_reloadSuccessAfterCrossProfileError() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        // Currently mock provider does not have cross profile concept, this will always return
        // the supplied docs without checking for the user. But this should not be a problem for
        // this test case.
        mEnv.mockProviders.get(TestProvidersAccess.OtherUser.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.OtherUser.FILE_PNG);

        // Disallow sharing across profile
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, false);
        } else {
            mEnv.state.canShareAcrossProfile = false;
        }

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch1 = new CountDownLatch(1);
        EventListener<Model.Update> updateEventListener1 = update -> latch1.countDown();
        mEnv.model.addUpdateListener(updateEventListener1);
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);
        latch1.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);

        // Allow sharing across profile.
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }

        CountDownLatch latch2 = new CountDownLatch(1);
        mEnv.model.addUpdateListener(update -> latch2.countDown());
        mHandler.loadDocumentsForCurrentStack();
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch2.await(1, TimeUnit.SECONDS);
        assertEquals(1, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.OtherUser.FILE_PNG, mEnv.model.getDocument(modelIds[0]));
    }

    @Test
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    // TODO(b:422900724): Remove the @DisableFlags directive.
    public void testLoadChildrenDocuments_failsWithNonRecentsAndEmptyStack() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        latch.await(1, TimeUnit.SECONDS);
        assertTrue(listener.getLastValue().hasException());
    }

    @Test
    public void testPreviewItem_throwException() throws Exception {
        try {
            mHandler.previewItem(null);
            fail("Should have thrown UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testJumpToDirectory() {
        mEnv.populateStack();

        DocumentStack stack = new DocumentStack(
                TestProvidersAccess.HOME, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        mHandler.jumpToDirectory(stack);

        stack.pop();
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2));
    }

    @Test
    public void testBlockOperationForShortcutsReturnsTrue() {
        List<Uri> uris = new ArrayList<>(List.of(
                TestProvidersAccess.TEST_SHORTCUT.getUri(),
                TestProvidersAccess.HOME.getUri(),
                TestProvidersAccess.EXTERNALSTORAGE.getUri()
        ));
        assertTrue(mHandler.blockOperationForShortcuts(uris, TestProvidersAccess.USER_ID));
        mEnv.dialogs.assertOperationNotAllowedForShortcutsShown();
    }

    @Test
    public void testBlockOperationForShortcutsReturnsFalse() {
        List<Uri> uris = new ArrayList<>(List.of(
                TestProvidersAccess.HOME.getUri(),
                TestProvidersAccess.EXTERNALSTORAGE.getUri(),
                TestProvidersAccess.IMAGE.getUri()
        ));
        assertFalse(mHandler.blockOperationForShortcuts(uris, TestProvidersAccess.USER_ID));
        mEnv.dialogs.assertOperationNotAllowedForShortcutsNotShown();
    }

    @Test
    public void testBlockOperationForShortcutsReturnsTrueForOtherUser() {
        List<Uri> uris = new ArrayList<>(List.of(
                TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getUri(),
                TestProvidersAccess.HOME.getUri(),
                TestProvidersAccess.EXTERNALSTORAGE.getUri()
        ));
        assertTrue(mHandler.blockOperationForShortcuts(
                uris, TestProvidersAccess.OtherUser.USER_ID));
        mEnv.dialogs.assertOperationNotAllowedForShortcutsShown();
    }

    @Test
    public void testBlockOperationForShortcutsReturnsFalseForOtherUser() {
        // The home screen uri is not included as a shortcut for OtherUser
        List<Uri> uris = new ArrayList<>(List.of(
                TestProvidersAccess.HOME_SCREEN_SHORTCUT.getUri(),
                TestProvidersAccess.HOME.getUri(),
                TestProvidersAccess.EXTERNALSTORAGE.getUri()
        ));
        assertFalse(mHandler.blockOperationForShortcuts(
                uris, TestProvidersAccess.OtherUser.USER_ID));
        mEnv.dialogs.assertOperationNotAllowedForShortcutsNotShown();
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsEnabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    public void testShowPeek() throws Exception {
        mHandler.showPreview(TestEnv.FILE_GIF);
        // The inspector activity is not called.
        mActivity.startActivity.assertNotCalled();
        mPeekViewManager.getPeekDocument().assertCalled();
        mPeekViewManager.getPeekDocument().assertLastArgument(TestEnv.FILE_GIF);
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @DisableFlags({Flags.FLAG_GET_INFO_DIALOG})
    public void testShowInspector() throws Exception {
        mHandler.showPreview(TestEnv.FILE_GIF);

        mActivity.startActivity.assertCalled();
        Intent intent = mActivity.startActivity.getLastValue();
        assertTargetsComponent(intent, InspectorActivity.class);
        assertHasData(intent, TestEnv.FILE_GIF.derivedUri);

        // should only send this under especial circumstances. See test below.
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_TITLE));
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @EnableFlags({Flags.FLAG_GET_INFO_DIALOG, Flags.FLAG_USE_MATERIAL3})
    public void testShowGetInfoDialog() throws Exception {
        // Retrieve the mock FragmentManager created in setUp.
        FragmentManager mockFragmentManager = mActivity.getSupportFragmentManager();
        FragmentTransaction mockFragmentTransaction = mock(FragmentTransaction.class);

        doReturn(mockFragmentTransaction).when(mockFragmentManager).beginTransaction();
        doReturn(mockFragmentTransaction).when(mockFragmentTransaction).add(any(), anyString());
        doReturn(null).when(mockFragmentManager).findFragmentByTag(anyString());

        mHandler.showPreview(TestEnv.FILE_GIF);

        mActivity.startActivity.assertNotCalled();
        verify(mockFragmentTransaction).commit();
        verify(mockFragmentTransaction).add(isA(GetInfoDialogFragment.class), eq("GetInfoDialog"));
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @DisableFlags({Flags.FLAG_GET_INFO_DIALOG})
    public void testShowInspector_DebugDisabled() throws Exception {
        mFeatures.debugSupport = false;

        mHandler.showPreview(TestEnv.FILE_GIF);
        Intent intent = mActivity.startActivity.getLastValue();

        assertHasExtra(intent, Shared.EXTRA_SHOW_DEBUG);
        assertFalse(intent.getExtras().getBoolean(Shared.EXTRA_SHOW_DEBUG));
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @DisableFlags({Flags.FLAG_GET_INFO_DIALOG})
    public void testShowInspector_DebugEnabled() throws Exception {
        mFeatures.debugSupport = true;
        DebugFlags.setDocumentDetailsEnabled(true);

        mHandler.showPreview(TestEnv.FILE_GIF);
        Intent intent = mActivity.startActivity.getLastValue();

        assertHasExtra(intent, Shared.EXTRA_SHOW_DEBUG);
        Assert.assertTrue(intent.getExtras().getBoolean(Shared.EXTRA_SHOW_DEBUG));
        DebugFlags.setDocumentDetailsEnabled(false);
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @DisableFlags({Flags.FLAG_GET_INFO_DIALOG})
    public void testShowInspector_OverridesRootDocumentName() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.PICKLES;
        mEnv.populateStack();

        // Verify test setup is correct, but not an assert related to the logic of our test.
        Preconditions.checkState(mEnv.state.stack.size() == 1);
        Preconditions.checkNotNull(mEnv.state.stack.peek());

        DocumentInfo rootDoc = mEnv.state.stack.peek();
        rootDoc.displayName = "poodles";

        mHandler.showPreview(rootDoc);
        Intent intent = mActivity.startActivity.getLastValue();
        assertEquals(
                TestProvidersAccess.PICKLES.title,
                intent.getExtras().getString(Intent.EXTRA_TITLE));
    }

    @Test
    // TODO(b/433858983): Change to DisableFlags once peek is overridable in FlagUtils.
    @RequiresFlagsDisabled({Flags.FLAG_USE_PEEK_PREVIEW_RO})
    @DisableFlags({Flags.FLAG_GET_INFO_DIALOG})
    public void testShowInspector_OverridesRootDocumentNameX() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.PICKLES;
        mEnv.populateStack();
        mEnv.state.stack.push(TestEnv.FOLDER_2);

        // Verify test setup is correct, but not an assert related to the logic of our test.
        Preconditions.checkState(mEnv.state.stack.size() == 2);
        Preconditions.checkNotNull(mEnv.state.stack.peek());

        DocumentInfo rootDoc = mEnv.state.stack.peek();
        rootDoc.displayName = "poodles";

        mHandler.showPreview(rootDoc);
        Intent intent = mActivity.startActivity.getLastValue();
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_TITLE));
    }

    @Test
    public void testShowDeleteDialog_NoSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.showDeleteDialog();
        mActivity.startService.assertNotCalled();
        assertFalse(mActionModeAddons.finishActionModeCalled);
    }

    @Test
    public void testGetSelectedOrFocused_fromSelection() {
        SelectionTracker<String> selectionMgr = mock(SelectionTracker.class);
        FocusHandler focusHandler = mock(FocusHandler.class);
        mHandler = createHandler(focusHandler, selectionMgr);

        doAnswer(
                        invocation -> {
                            MutableSelection<String> dest = invocation.getArgument(0);
                            dest.clear();
                            dest.add("selectedId");
                            return null;
                        })
                .when(selectionMgr)
                .copySelection(any());

        when(focusHandler.getFocusModelId()).thenReturn("focusedId");

        Selection<String> result = mHandler.getSelectedOrFocused();
        assertThat(result).containsExactly("selectedId");
    }

    @Test
    public void testGetSelectedOrFocused_fromFocus() {
        SelectionTracker<String> selectionMgr = mock(SelectionTracker.class);
        FocusHandler focusHandler = mock(FocusHandler.class);
        mHandler = createHandler(focusHandler, selectionMgr);

        when(focusHandler.getFocusModelId()).thenReturn("focusedId");

        Selection<String> result = mHandler.getSelectedOrFocused();
        assertThat(result).containsExactly("focusedId");
    }

    @Test
    public void testGetFocusedOrSelected_fromFocus() {
        SelectionTracker<String> selectionMgr = mock(SelectionTracker.class);
        FocusHandler focusHandler = mock(FocusHandler.class);
        mHandler = createHandler(focusHandler, selectionMgr);

        doAnswer(
                        invocation -> {
                            MutableSelection<String> dest = invocation.getArgument(0);
                            dest.clear();
                            dest.add("selectedId");
                            return null;
                        })
                .when(selectionMgr)
                .copySelection(any());

        when(focusHandler.getFocusModelId()).thenReturn("focusedId");

        Selection<String> result = mHandler.getFocusedOrSelected();
        assertThat(result).containsExactly("focusedId");
    }

    @Test
    public void testGetFocusedOrSelected_fromSelection() {
        SelectionTracker<String> selectionMgr = mock(SelectionTracker.class);
        FocusHandler focusHandler = mock(FocusHandler.class);
        mHandler = createHandler(focusHandler, selectionMgr);

        doAnswer(
                        invocation -> {
                            MutableSelection<String> dest = invocation.getArgument(0);
                            dest.clear();
                            dest.add("selectedId");
                            return null;
                        })
                .when(selectionMgr)
                .copySelection(any());

        when(focusHandler.getFocusModelId()).thenReturn(null);

        Selection<String> result = mHandler.getFocusedOrSelected();
        assertThat(result).containsExactly("selectedId");
    }

    @Test
    public void testDeleteSelectedDocuments() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);

        List<DocumentInfo> docs = new ArrayList<>();
        docs.add(TestEnv.FILE_PNG);
        mHandler.deleteSelectedDocuments(docs, mEnv.state.stack.peek());

        mActivity.startService.assertCalled();
        if (isUseMaterial3FlagEnabled()) {
            verify(mMockCloseSelectionBar, times(1)).run();
        } else {
            Assert.assertTrue(mActionModeAddons.finishActionModeCalled);
        }
    }

    @Test
    public void testToggleFocusedItemSelection() {
        DocumentInfo doc = mEnv.model.createFile("file1");
        mEnv.model.update();
        String id = ModelId.build(doc.userId, doc.authority, doc.documentId);

        FocusHandler focusHandler = mock(FocusHandler.class);
        SelectionTracker<String> selectionMgr = SelectionHelpers.createTestInstance(List.of(id));
        mHandler = createHandler(focusHandler, selectionMgr);

        when(focusHandler.getFocusModelId()).thenReturn(id);

        // Toggle on
        mHandler.toggleFocusedItemSelection();
        assertThat(selectionMgr.getSelection()).containsExactly(id);

        // Toggle off
        mHandler.toggleFocusedItemSelection();
        assertThat(selectionMgr.getSelection()).isEmpty();
    }

    @SuppressLint("VisibleForTests")
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testLoadDocumentsForCurrentStack_DebouncesLoading() {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        final Runnable[] captured = new Runnable[1];

        doAnswer(
                        invocation -> {
                            captured[0] = invocation.getArgument(0); // Capture the Runnable
                            return null;
                        })
                .when(mMockHandler)
                .postDelayed(any(), eq(200L));

        mHandler.loadDocumentsForCurrentStack();

        // The model should initially NOT set loading state to enable any directories that load
        // instantly (i.e. within the current 200ms timeout) to just show their contents and avoid a
        // flicker of the loading bar.
        assertFalse(mEnv.model.isLoading());

        // Run the delayed Runnable.
        assertNotNull(captured[0]);
        captured[0].run();

        // The model should now show a loading state.
        assertTrue(mEnv.model.isLoading());
    }

    @Test
    @SuppressLint("VisibleForTests")
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testLoadDocumentsForCurrentStack_NewLoaderSupersedesOld() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        setupFileOnAuthority("file1", TestProvidersAccess.HOME.authority);

        // Set up the first query to happen on HOME authority that would return in 10s.
        CountDownLatch firstLoaderStartedLatch = new CountDownLatch(1);
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority).setQueryDelay(10000);
        mEnv.mockProviders
                .get(TestProvidersAccess.HOME.authority)
                .setQueryDelayLatch(firstLoaderStartedLatch);

        // Start the background thread loader for this query.
        mHandler.loadDocumentsForCurrentStack();
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);
        assertTrue("First query never started", firstLoaderStartedLatch.await(2, TimeUnit.SECONDS));

        // Navigate to another root whilst the loader is still running.
        mEnv.state.stack.reset();
        mEnv.state.stack.changeRoot(TestProvidersAccess.HAMMY);
        setupFileOnAuthority("file2", TestProvidersAccess.HAMMY.authority);

        // The first loader should simulate a slow loading DP, whilst the second loader should
        // simulate a fast loader that beats the first one to finish. It should "cancel" the first
        // one causing its results to be ignored when it eventually returns.

        CountDownLatch modelUpdateLatch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> modelUpdateLatch.countDown());
        mHandler.loadDocumentsForCurrentStack();
        mActivity.supportLoaderManager.runAsyncTaskLoader(LoaderIds.MAIN);

        // The second loader should finish immediately.
        assertTrue("Model was never updated", modelUpdateLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, mEnv.model.getItemCount());
        assertEquals("file2", mEnv.model.getDocument(mEnv.model.getModelIds()[0]).displayName);

        // Now release the first slow loader, the results of which should be ignored.
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority).cancelQueryDelay();

        // Wait for all background tasks (including the released slow loader) to finish.
        mEnv.beforeAsserts();

        // Model should STILL have file2, not file1.
        assertEquals(1, mEnv.model.getItemCount());
        assertEquals("file2", mEnv.model.getDocument(mEnv.model.getModelIds()[0]).displayName);
    }
}
