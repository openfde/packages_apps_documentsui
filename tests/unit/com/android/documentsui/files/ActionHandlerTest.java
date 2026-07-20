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

import static com.android.documentsui.testing.IntentAsserts.assertHasAction;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraIntent;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraList;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraUri;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.view.DragEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.DragAndDropManager.Permissions;
import com.android.documentsui.ModelId;
import com.android.documentsui.R;
import com.android.documentsui.TestActionModeAddons;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestActivityConfig;
import com.android.documentsui.testing.TestDocumentClipper;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestPeekViewManager;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.ui.TestDialogController;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import kotlin.Triple;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
import java.util.List;
import java.util.function.BiFunction;

@RunWith(Parameterized.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestActionModeAddons mActionModeAddons;
    private TestDialogController mDialogs;
    private ActionHandler<TestActivity> mHandler;
    private TestDocumentClipper mClipper;
    private TestDragAndDropManager mDragAndDropManager;
    private TestPeekViewManager mPeekViewManager;
    private TestFeatures mFeatures;
    private TestConfigStore mTestConfigStore;
    private boolean refreshAnswer = false;
    @Mock private Runnable mMockCloseSelectionBar;
    @Mock private BiFunction<Activity, DragEvent, Permissions> mMockRequestPermissionsHandler;

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

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

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mFeatures = new TestFeatures();
        mEnv = TestEnv.create(mFeatures);
        mActivity = TestActivity.create(mEnv);
        mActivity.userManager = UserManagers.create();
        mActionModeAddons = new TestActionModeAddons();
        mDialogs = new TestDialogController();
        mClipper = new TestDocumentClipper();
        mDragAndDropManager = new TestDragAndDropManager();
        mPeekViewManager = isUsePeekPreviewFlagEnabled() ? new TestPeekViewManager() : null;
        mTestConfigStore = new TestConfigStore();
        mEnv.state.configStore = mTestConfigStore;

        DragAndDropManager.REQUEST_PERMISSIONS_HANDLER_FOR_TESTING.set(
                mMockRequestPermissionsHandler);

        isPrivateSpaceEnabled &= SdkLevel.isAtLeastS();
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        }

        mEnv.providers.configurePm(mActivity.packageMgr);
        ((TestActivityConfig) mEnv.injector.config).nextDocumentEnabled = true;
        mEnv.injector.dialogs = mDialogs;

        mHandler = createHandler();

        mEnv.selectDocument(TestEnv.FILE_GIF);
    }

    @After
    public void tearDown() {
        DragAndDropManager.REQUEST_PERMISSIONS_HANDLER_FOR_TESTING.set(null);
    }

    private void assertSelectionContainerClosed() {
        assertSelectionContainerClosed(/* wantedNumberOfInvocations */ 1);
    }

    private void assertSelectionContainerClosed(int wantedNumberOfInvocations) {
        if (isUseMaterial3FlagEnabled()) {
            verify(mMockCloseSelectionBar, times(wantedNumberOfInvocations)).run();
        } else {
            assertTrue(mActionModeAddons.finishActionModeCalled);
        }
    }

    @Test
    public void testOpenSelectedInNewWindow() {
        mHandler.openSelectedInNewWindow();

        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    @DisableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testOpenFileFlags() {
        mHandler.onDocumentOpened(TestEnv.FILE_GIF,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_PREVIEW,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_REGULAR, false);

        int expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expectedFlags, actual.getFlags());
    }

    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testOpenFileFlagsDesktop() {
        mHandler.onDocumentOpened(TestEnv.FILE_GIF,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_PREVIEW,
                com.android.documentsui.files.ActionHandler.VIEW_TYPE_REGULAR, false);

        int expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK;
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expectedFlags, actual.getFlags());
    }

    @Test
    public void testSpringOpenDirectory() {
        mHandler.springOpenDirectory(TestEnv.FOLDER_0);
        assertSelectionContainerClosed();
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.peek());
    }

    @Test
    public void testCutSelectedDocuments() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedShown();
        mDialogs.assertOperationUnsupportedNotShown();
        mClipper.clipForCut.assertCalled();
    }

    @Test
    public void testCutSelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    public void testCutSelectedDocuments_ContainsNonMovableItem() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_READ_ONLY);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCut.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testCutSelectedDocuments_ContainsUnavailableDocument() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_PDF.documentId);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCut.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testCutSelectedDocuments_ContainsUnavailableDocument_AndAvailableDocument() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mEnv.selectDocument(TestEnv.FILE_APK);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_PDF.documentId);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCut.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testCutSelectedDocuments_ContainsUnavailableDocument_FeatureFlagDisabled() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_PDF.documentId);

        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedShown();
        mDialogs.assertOperationUnsupportedNotShown();
        mClipper.clipForCut.assertCalled();
    }

    @Test
    public void testCopySelectedDocuments() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedShown();
        mDialogs.assertOperationUnsupportedNotShown();
        mClipper.clipForCopy.assertCalled();
    }

    @Test
    public void testCopySelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testCopySelectedDocuments_ContainsUnavailableDocument() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_PDF.documentId);

        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCopy.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testCopySelectedDocuments_ContainsUnavailableDocument_AndAvailableDocument() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mEnv.selectDocument(TestEnv.FILE_JPG);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_JPG.documentId);

        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
        mDialogs.assertShowOperationUnsupported();
        mClipper.clipForCopy.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testCopySelectedDocuments_ContainsUnavailableDocument_FeatureFlagDisabled() {
        mEnv.populateStack();
        mEnv.selectDocument(TestEnv.FILE_PDF);

        ((TestActivityConfig) mEnv.injector.config)
                .documentsWithUnavailableContent.add(TestEnv.FILE_PDF.documentId);

        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedShown();
        mDialogs.assertOperationUnsupportedNotShown();
        mClipper.clipForCopy.assertCalled();
    }

    @Test
    public void testRestoreSelectedDocumentsFromTrashFromTrash() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);

        List<DocumentInfo> docs = new ArrayList<>();
        docs.add(TestEnv.FILE_PNG);
        mHandler.restoreSelectedDocumentsFromTrash(docs);

        mActivity.startService.assertCalled();
        assertSelectionContainerClosed();
    }

    /** Verifies that trashable documents are trashed when the trash feature is enabled. */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testRunDeleteOrTrashHandler_trashesTrashableDocuments_whenTrashIsEnabled() {
        mEnv.populateStack();
        mEnv.selectionMgr.clearSelection();

        ActionHandler<TestActivity> handlerSpy = spy(mHandler);
        doNothing().when(handlerSpy).trashSelectedDocuments();

        final DocumentInfo trashableDoc =
                mEnv.model.createDocument(
                        "trashable-doc",
                        "plain/text",
                        DocumentsContract.Document.FLAG_SUPPORTS_TRASH);
        mEnv.model.update();
        mEnv.selectDocument(trashableDoc);

        handlerSpy.runDeleteOrTrashHandler();

        verify(handlerSpy).trashSelectedDocuments();
    }

    /** Verifies that non-trashable documents are deleted when the trash feature is enabled. */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testRunDeleteOrTrashHandler_deletesNonTrashableDocuments_whenTrashIsEnabled() {
        mEnv.populateStack();
        mEnv.selectionMgr.clearSelection();

        ActionHandler<TestActivity> handlerSpy = spy(mHandler);
        doNothing().when(handlerSpy).showDeleteDialog();

        final DocumentInfo nonTrashableDoc =
                mEnv.model.createDocument("non-trashable-doc", "plain/text", 0);
        mEnv.model.update();
        mEnv.selectDocument(nonTrashableDoc);

        handlerSpy.runDeleteOrTrashHandler();

        verify(handlerSpy).showDeleteDialog();
    }

    /**
     * Verifies that all documents are deleted when the trash feature is disabled, regardless of
     * their individual trash support.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @DisableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testRunDeleteOrTrashHandler_deletesAllDocuments_whenTrashIsDisabled() {
        mEnv.populateStack();
        mEnv.selectionMgr.clearSelection();

        ActionHandler<TestActivity> handlerSpy = spy(mHandler);
        doNothing().when(handlerSpy).showDeleteDialog();

        // Test with a document that supports trash
        final DocumentInfo trashableDoc =
                mEnv.model.createDocument(
                        "trashable-doc",
                        "plain/text",
                        DocumentsContract.Document.FLAG_SUPPORTS_TRASH);
        mEnv.model.update();
        mEnv.selectDocument(trashableDoc);

        handlerSpy.runDeleteOrTrashHandler();

        verify(handlerSpy).showDeleteDialog();

        // Test with a document that does not support trash
        final DocumentInfo nonTrashableDoc =
                mEnv.model.createDocument("non-trashable-doc", "plain/text", 0);
        mEnv.model.update();
        mEnv.selectDocument(nonTrashableDoc);

        handlerSpy.runDeleteOrTrashHandler();

        verify(handlerSpy, times(2)).showDeleteDialog();
    }

    @Test
    public void testShareSelectedDocuments_ShowsChooser() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testShareSelectedDocuments_Single() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_ArchivedFile() {
        mEnv = TestEnv.create(ArchivesProvider.AUTHORITY);
        mHandler = createHandler();

        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = mActivity.startActivity.getLastValue();
        assertNull(intent);
    }

    @Test
    public void testShareSelectedDocuments_Multiple() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testShareSelectedDocuments_overShareLimit() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectMultipleFiles(500);
        mHandler.shareSelectedDocuments();

        Intent intent = mActivity.startActivity.getLastValue();
        assertNull(intent);
        mDialogs.assertShareOverLimitShown();
    }

    @Test
    public void testShareSelectedDocuments_VirtualFiles() {
        if (!mEnv.features.isVirtualFilesSharingEnabled()) {
            return;
        }

        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_RegularAndVirtualFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);

        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        if (mEnv.features.isVirtualFilesSharingEnabled()) {
            assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
            assertHasExtraList(intent, Intent.EXTRA_STREAM, 3);
        }else {
            assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
        }
    }

    @Test
    public void testShareSelectedDocuments_OmitsPartialFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PARTIAL);
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testDocumentPicked_DefaultsToView() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_QuickViewable() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_OpenableOrNot() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        if (isZipNgFlagEnabled()) {
            mActivity.assertActivityStarted(Intent.ACTION_VIEW);
        } else {
            mDialogs.assertViewInArchivesShownUnsupported();
        }
    }

    @Test
    public void testDocumentPicked_PreviewsWhenResourceSet() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;
        TestEnv.FILE_APK.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesPartialFiles() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;
        TestEnv.FILE_PARTIAL.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_PARTIAL, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Recent_ManagesApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.RECENTS;
        TestEnv.FILE_APK.authority = TestProvidersAccess.DOWNLOADS.authority;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Home_SendsActionViewForApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_OpensArchives() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;
        mEnv.docs.nextDocument = TestEnv.FILE_ARCHIVE;

        final boolean result = mHandler.openDocument(TestEnv.FILE_ARCHIVE,
                ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FILE_ARCHIVE, mEnv.state.stack.peek());
        assertEquals(false, result);
    }

    @Test
    public void testDocumentPicked_OpensDirectories() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        final boolean result = mHandler.openDocument(TestEnv.FOLDER_1,
                ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.peek());
        assertEquals(false, result);
    }

    @Test
    public void testDocumentPicked_NoApplicationFound() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;
        mActivity.throwOnStartActivity = true;

        mHandler.openDocument(TestEnv.FILE_PDF, ActionHandler.VIEW_TYPE_REGULAR,
                ActionHandler.VIEW_TYPE_NONE);

        mDialogs.assertNoAppFoundShown();
    }

    @Test
    public void testDocumentContextMenuOpen() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        // Test normal picking on mobile will quick view
        mHandler.openDocument(
                TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);

        // Test normal picking on desktop will view
        mHandler.openDocument(
                TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_REGULAR, ActionHandler.VIEW_TYPE_NONE);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);

        // And verify open via context menu will view
        mHandler.openDocumentViewOnly(TestEnv.FILE_GIF);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    @DisableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testShowChooser() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testShowChooserDesktop() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(Intent.ACTION_VIEW, actual.getAction());
        assertEquals("ComponentInfo{android/com.android.internal.app.ResolverActivity}",
                actual.getComponent().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testShowChooser_NoApplicationFound() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;
        mActivity.packageMgr.dontResolveActivity = true;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);

        mDialogs.assertNoAppFoundShown();
    }

    @Test
    public void testInitLocation_LaunchToStackLocation() {
        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent intent = LauncherActivity.createLaunchIntent(mActivity);
        intent.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        mHandler.initLocation(intent);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_RestoresIfStackIsLoaded() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.DOWNLOADS);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mHandler.initLocation(mActivity.getIntent());
        mActivity.restoreRootAndDirectory.assertCalled();
    }

    @Test
    public void testInitLocation_LoadsRootDocIfStackOnlyHasRoot() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HAMMY);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.HAMMY.getUri());
    }

    @Test
    public void testInitLocation_forceDefaultsToRoot() throws Exception {
        mActivity.resources.strings.put(R.string.default_root_uri,
                TestProvidersAccess.DOWNLOADS.getUri().toString());

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_BrowseRootWithoutRootId() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(DocumentsContract.buildRootsUri(TestProvidersAccess.HAMMY.authority));

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.HAMMY.getUri());
    }

    @Test
    public void testInitLocation_BrowseRootWrongAuthority_ShowDefault() throws Exception {
        ActionHandler<TestActivity> spyHandler = spy(mHandler);
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(DocumentsContract.buildRootsUri("com.test.wrongauthority"));
        mActivity.resources.strings.put(R.string.default_root_uri,
                TestProvidersAccess.HOME.getUri().toString());

        spyHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.HOME.getUri());
        // Assert that the root is picked correctly by specifically calling
        // launchToDefaultLocation().
        verify(spyHandler, times(1)).launchToDefaultLocation();
    }

    @Test
    public void testInitLocation_BrowseRoot() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestProvidersAccess.PICKLES.getUri());

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.PICKLES.getUri());
    }

    @Test
    public void testInitLocation_LaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestEnv.FOLDER_1.derivedUri);
        mHandler.initLocation(intent);

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    @DisableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testInitLocation_LaunchToDownloads() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(DownloadManager.ACTION_VIEW_DOWNLOADS);

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testInitLocation_LaunchToFolderOnHomeScreen() throws Exception {
        Uri mediaStoreUri = Uri.parse("content://media/external/file/1");

        // Set the intent data to be the media store uri.
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(mediaStoreUri);

        // Set the path to be:
        // external storage provider root --> home screen folder --> folder 0.
        mEnv.docs.nextPath =
                new Path(
                        TestProvidersAccess.HOME_SCREEN_SHORTCUT.getRoot().rootId,
                        Arrays.asList(
                                TestProvidersAccess.HOME_SCREEN_SHORTCUT.getDocumentId(),
                                TestEnv.FOLDER_0.documentId));
        // Needed to get the correct results when calling LoadDocStackTask.
        mEnv.docs.nextIsDocumentsUri = true;
        DocumentInfo homeScreenDoc = new DocumentInfo();
        homeScreenDoc.derivedUri = TestProvidersAccess.HOME_SCREEN_SHORTCUT.getUri();
        mEnv.docs.nextDocuments = Arrays.asList(homeScreenDoc, TestEnv.FOLDER_0);
        // Mock the media store uri to convert to FOLDER_0's uri.
        mEnv.docs.mNextDocumentUri = TestEnv.FOLDER_0.derivedUri;

        mHandler.initLocation(intent);
        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(
                mEnv.state.stack,
                TestProvidersAccess.HOME_SCREEN_SHORTCUT.getRoot(),
                Arrays.asList(homeScreenDoc, TestEnv.FOLDER_0));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_HOME_SCREEN_FILES_RO})
    public void testInitLocation_LaunchToZipFolderOnHomeScreen() throws Exception {
        Uri mediaStoreUri = Uri.parse("content://media/external/file/1");

        // Set the intent data to be the media store uri.
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(mediaStoreUri, "application/zip");

        // Set the path to be:
        // external storage provider root --> home screen folder --> whatsinthere.zip.
        mEnv.docs.nextPath =
                new Path(
                        TestProvidersAccess.HOME_SCREEN_SHORTCUT.getRoot().rootId,
                        Arrays.asList(
                                TestProvidersAccess.HOME_SCREEN_SHORTCUT.getDocumentId(),
                                TestEnv.FILE_ARCHIVE.documentId));
        // Needed to get the correct results when calling LoadDocStackTask.
        mEnv.docs.nextIsDocumentsUri = true;
        DocumentInfo homeScreenDoc = new DocumentInfo();
        homeScreenDoc.derivedUri = TestProvidersAccess.HOME_SCREEN_SHORTCUT.getUri();
        mEnv.docs.nextDocuments = Arrays.asList(homeScreenDoc, TestEnv.FILE_ARCHIVE);
        // Mock the media store uri to convert to FILE_ARCHIVE's uri.
        mEnv.docs.mNextDocumentUri = TestEnv.FILE_ARCHIVE.derivedUri;

        mHandler.initLocation(intent);
        mEnv.beforeAsserts();

        // The expected behaviour is that the activity will launch to the home screen document and
        // select the zip file in the directory list.
        DocumentStackAsserts.assertEqualsTo(
                mEnv.state.stack,
                TestProvidersAccess.HOME_SCREEN_SHORTCUT.getRoot(),
                Arrays.asList(homeScreenDoc));
        assertEquals(TestEnv.FILE_ARCHIVE.derivedUri, mHandler.getToSelect());

        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_OnReadOnlyRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        RootInfo root = new RootInfo(); // root by default has no SUPPORT_CREATE flag
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, null, null,
                null, null, null, true);
        assertFalse(mHandler.dropOn(event, root));
        verifyNoMoreInteractions(mMockRequestPermissionsHandler);
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_OnLibraryRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, null, null,
                null, null, null, true);
        assertFalse(mHandler.dropOn(event, TestProvidersAccess.RECENTS));
        verifyNoMoreInteractions(mMockRequestPermissionsHandler);
    }

    // Ignoring the test because it uses hidden api DragEvent#obtain() and changes to the api is
    // causing failure on older base builds
    // TODO: b/343206763 remove dependence on hidden api
    @Ignore
    @Test
    public void testDragAndDrop_DropsOnWritableRoot() throws Exception {
        assumeTrue(VersionUtils.isAtLeastS());
        // DragEvent gets recycled in Android, so it is possible that by the time the callback is
        // called, event.getLocalState() and event.getClipData() returns null. This tests to ensure
        // our Clipper is getting the original CipData passed in.
        Object localState = new Object();
        ClipData clipData = ClipDatas.createTestClipData();
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, 0, 0, 0, 0, localState,
                null, clipData, null, null, true);

        final Permissions permissions = mock(Permissions.class);
        doReturn(permissions).when(mMockRequestPermissionsHandler).apply(mActivity, event);

        mHandler.dropOn(event, TestProvidersAccess.DOWNLOADS);
        event.recycle();

        final Triple<Permissions, ClipData, SidebarEntryItemInfo> actual =
                mDragAndDropManager.dropOnRootHandler.getLastValue();

        assertNotNull(actual);
        assertSame(permissions, actual.getFirst());
        assertSame(clipData, actual.getSecond());
        assertSame(TestProvidersAccess.DOWNLOADS, actual.getThird());
    }

    @Test
    public void testRefresh_nullUri() throws Exception {
        refreshAnswer = true;
        mHandler.refreshDocument(null, (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh_emptyStack() throws Exception {
        refreshAnswer = true;
        assertTrue(mEnv.state.stack.isEmpty());
        mHandler.refreshDocument(new DocumentInfo(), (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh() throws Exception {
        refreshAnswer = false;
        mEnv.populateStack();
        mHandler.refreshDocument(mEnv.model.getDocument(
                ModelId.build(mEnv.model.mUserId, TestProvidersAccess.HOME.authority, "1")),
                (boolean answer) -> {
                    refreshAnswer = answer;
                });

        mEnv.beforeAsserts();
        if (mEnv.features.isContentRefreshEnabled()) {
            assertTrue(refreshAnswer);
        } else {
            assertFalse(refreshAnswer);
        }
    }

    @Test
    public void testAuthentication() throws Exception {
        PendingIntent intent = PendingIntent.getActivity(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);

        mHandler.startAuthentication(intent);
        assertEquals(intent.getIntentSender(), mActivity.startIntentSender.getLastValue().first);
        assertEquals(AbstractActionHandler.CODE_AUTHENTICATION,
                mActivity.startIntentSender.getLastValue().second.intValue());
    }

    @Test
    public void testOnActivityResult_onOK() throws Exception {
        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION, Activity.RESULT_OK,
                null);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testOnActivityResult_onNotOK() throws Exception {
        mHandler.onActivityResult(0, Activity.RESULT_OK, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();

        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION,
                Activity.RESULT_CANCELED, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
    }

    @Test
    public void testViewInOwner() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);

        mHandler.viewInOwner();
        mActivity.assertActivityStarted(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
    }

    @Test
    public void testOpenSettings() {
        mHandler.openSettings(TestProvidersAccess.HAMMY);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS);
    }

    /**
     * Verifies that the "Empty Trash" confirmation dialog does not appear if the trash is empty.
     * When the method is called on an empty trash, no service should be started, and the UI state
     * should remain unchanged.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testShowEmptyTrashConfirmationDialog_NoDialog() {
        // Clear the environment to ensure trash is empty
        mEnv.clear();
        mEnv.state.stack.changeRoot(TestProvidersAccess.TRASH_ROOT);

        mHandler.showEmptyTrashConfirmationDialog();

        // Assert that no actions were taken
        mActivity.startService.assertNotCalled();
        assertFalse(mActionModeAddons.finishActionModeCalled);
    }

    /**
     * Verifies that items currently in the trash are permanently deleted. This test first moves a
     * file to the trash and then calls the permanent delete method, asserting that the correct
     * deletion service is triggered.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testPermanentlyDeleteTrashDocuments() {
        // Add a file and move it to the trash
        mEnv.populateStack();
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mHandler.trashSelectedDocuments();
        mActivity.startService.assertCalled();
        assertSelectionContainerClosed();
        mEnv.state.stack.changeRoot(TestProvidersAccess.TRASH_ROOT);

        // reset
        mActivity.startService.reset();

        // Call the method to permanently delete items.
        mHandler.permanentlyDeleteTrashDocuments();

        // Assert that the deletion service was started.
        mActivity.startService.assertCalled();
        // Total number of invocations is 2 now.
        assertSelectionContainerClosed(/* wantedNumberOfInvocations */ 2);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_singleFile() {
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);
        Intent intent = mHandler.createApprovedHandlerIntent(mEnv.selectionMgr.getSelection());

        assertNotNull(intent);
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals(TestEnv.FILE_PNG.getDocumentUri(),
                intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue(intent.hasCategory(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_multipleFiles() {
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mEnv.selectDocument(TestEnv.FILE_PDF);
        Intent intent = mHandler.createApprovedHandlerIntent(mEnv.selectionMgr.getSelection());

        assertNotNull(intent);
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.getAction());
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertEquals(2, uris.size());
        assertTrue(uris.contains(TestEnv.FILE_PNG.getDocumentUri()));
        assertTrue(uris.contains(TestEnv.FILE_PDF.getDocumentUri()));
        assertTrue(intent.hasCategory(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_noSharableFiles() {
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PARTIAL);
        Intent intent = mHandler.createApprovedHandlerIntent(mEnv.selectionMgr.getSelection());
        assertNull(intent);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_virtualFile() {
        mFeatures.virtualFilesSharing = true;
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        Intent intent = mHandler.createApprovedHandlerIntent(mEnv.selectionMgr.getSelection());

        assertNotNull(intent);
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals(TestEnv.FILE_VIRTUAL.getDocumentUri(),
                intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertTrue(intent.hasCategory(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_success() {
        mFeatures.virtualFilesSharing = true;
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        ComponentName testComponent = new ComponentName("com.test", "com.test.Activity");
        Intent intent = mHandler.createApprovedHandlerIntent(testComponent);

        assertNotNull(intent);
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals(testComponent, intent.getComponent());
        assertTrue(intent.hasCategory(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER})
    public void testCreateApprovedHandlerIntent_failure() {
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PARTIAL);
        ComponentName testComponent = new ComponentName("com.test", "com.test.Activity");

        Intent intent = mHandler.createApprovedHandlerIntent(testComponent);

        assertNull(intent);
        mActivity.startActivity.assertNotCalled();
    }

    /** Verifies that the permanent delete action does nothing if the trash is already empty. */
    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testPermanentlyDeleteTrashDocuments_NoItems() {
        // Ensure the environment and trash are empty
        mEnv.clear();

        mEnv.state.stack.changeRoot(TestProvidersAccess.TRASH_ROOT);

        mHandler.permanentlyDeleteTrashDocuments();

        // Assert that no deletion service was started
        mActivity.startService.assertNotCalled();
    }

    @Test
    @EnableFlags({Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3})
    public void testRenameOnShortcut() {
        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.TEST_SHORTCUT.getUri();
        docInfo.userId = TestProvidersAccess.USER_ID;
        assertNull(mHandler.renameDocument("new name", docInfo));
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }

    private ActionHandler<TestActivity> createHandler() {
        return new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mActionModeAddons,
                mMockCloseSelectionBar,
                mClipper,
                null, // clip storage, not utilized unless we venture into *jumbo* clip territory.
                mDragAndDropManager,
                mPeekViewManager,
                mEnv.injector);
    }
}
