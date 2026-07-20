/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.provider.DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY;
import static android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API;
import static android.provider.Flags.FLAG_ENABLE_SYNC_STATE;

import static com.android.documentsui.flags.Flags.FLAG_CLOUD_FEATURES;
import static com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentUris;
import android.content.pm.ProviderInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.documentsui.DragAndDropManager.Permissions;
import com.android.documentsui.DragAndDropManager.RuntimeDragAndDropManager;
import com.android.documentsui.DragAndDropManager.State;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.KeyEvents;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDocumentClipper;
import com.android.documentsui.testing.TestDocumentsAccess;
import com.android.documentsui.testing.TestDrawable;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.TestIconHelper;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestSelectionDetails;
import com.android.documentsui.testing.Views;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragAndDropManagerTests {

    private static final Uri MEDIA_STORE_URI_0 = Uri.parse("content://media/files/1");
    private static final Uri NON_MEDIA_STORE_URI_0 = Uri.parse("content://non-media/files/1");
    private static final String PLURAL_FORMAT = "%1$d items";
    private static final boolean CAN_DRAG_AND_DROP = true;
    private static final boolean CANNOT_DRAG_AND_DROP = false;

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestDragShadowBuilder mShadowBuilder;
    private View mStartDragView;
    private View mUpdateShadowView;
    private TestActionHandler mActions;
    private TestDocumentsAccess mDocs;

    private TestDocumentClipper mClipper;
    private TestSelectionDetails mDetails;
    private ClipData mClipData;

    private TestIconHelper mIconHelper;
    private Drawable mDefaultIcon;

    private TestEventListener<ClipData> mStartDragListener;
    private TestEventListener<Void> mShadowUpdateListener;
    private TestEventListener<Integer> mFlagListener;

    private TestEventListener<Integer> mCallbackListener;
    private FileOperations.Callback mCallback = new FileOperations.Callback() {
        @Override
        public void onOperationResult(@Status int status,
                @FileOperationService.OpType int opType, int docCount) {
            mCallbackListener.accept(status);
        }
    };

    private DragAndDropManager mManager;

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private Permissions mMockPermissions;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActivity.resources.plurals.put(R.plurals.elements_dragged, PLURAL_FORMAT);
        mEnv.providers.configurePm(mActivity.packageMgr);

        mShadowBuilder = TestDragShadowBuilder.create();

        mStartDragView = Views.createTestView();
        mUpdateShadowView = Views.createTestView();

        mActions = new TestActionHandler(mEnv);
        mDocs = new TestDocumentsAccess();

        mClipper = new TestDocumentClipper();
        mDetails = new TestSelectionDetails();
        mDetails.canDelete = true;
        ClipDescription description = new ClipDescription("", new String[]{});
        description.setExtras(new PersistableBundle());
        mClipData = ClipDatas.createTestClipData(description);
        mClipper.nextClip = mClipData;

        mDefaultIcon = new TestDrawable();
        mIconHelper = TestIconHelper.create();
        mIconHelper.nextDocumentIcon = new TestDrawable();

        mStartDragListener = new TestEventListener<>();
        mShadowUpdateListener = new TestEventListener<>();
        mCallbackListener = new TestEventListener<>();
        mFlagListener = new TestEventListener<>();

        mManager = new RuntimeDragAndDropManager(mActivity, mClipper, mEnv.mExecutor,
                mShadowBuilder, mDefaultIcon, mEnv.providers) {
            @Override
            void startDragAndDrop(View v, ClipData clipData, DragShadowBuilder builder,
                    Object localState, int flag) {
                assertSame(mStartDragView, v);
                assertSame(mShadowBuilder, builder);
                assertNotNull(localState);

                mFlagListener.accept(flag);
                mStartDragListener.accept(clipData);
            }

            @Override
            void updateDragShadow(View v) {
                assertSame(mUpdateShadowView, v);

                mShadowUpdateListener.accept(null);
            }
        };
    }

    @Test
    public void testStartDrag_SetsCorrectClipData() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mStartDragListener.assertLastArgument(mClipper.nextClip);
    }

    @Test
    public void testStartDrag_SetsCorrectClipData_NullParent() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        mStartDragListener.assertLastArgument(mClipper.nextClip);
    }

    @Test
    public void testStartDrag_BuildsCorrectShadow_SingleDoc() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mShadowBuilder.title.assertLastArgument(TestEnv.FILE_APK.displayName);
        mShadowBuilder.icon.assertLastArgument(mIconHelper.nextDocumentIcon);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testStartDrag_BuildsCorrectShadow_MultipleDocs_M3Disabled() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mShadowBuilder.title.assertLastArgument(mActivity.getResources().getQuantityString(
                R.plurals.elements_dragged, 2, 2));
        mShadowBuilder.icon.assertLastArgument(mDefaultIcon);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testStartDrag_BuildsCorrectShadow_MultipleDocs() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mShadowBuilder.title.assertLastArgument(TestEnv.FILE_APK.displayName);
        mShadowBuilder.icon.assertLastArgument(mIconHelper.nextDocumentIcon);
        mShadowBuilder.count.assertLastArgument(2);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testStartDrag_CannotDragAndDrop_StillStartsDrag() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        mStartDragListener.assertLastArgument(mClipper.nextClip);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testStartDrag_DragFromRecents_SetsCorrectClipData() {
        TestDocumentClipper spyClipper = spy(mClipper);
        DragAndDropManager newManager = createNewManagerWithSpyClipper(spyClipper);

        DocumentInfo recentsDoc = new DocumentInfo();
        Uri mediaUri = Uri.parse("media uri");
        recentsDoc.derivedUri = mediaUri;
        mDocs.mNextMediaUri = mediaUri;

        Uri docUri = TestEnv.FOLDER_0.derivedUri;
        mDocs.mNextDocumentUri = docUri;

        newManager.startDrag(
                mStartDragView,
                List.of(recentsDoc),
                TestProvidersAccess.RECENTS,
                List.of(),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        // Verify that the uri passed in when dragging from the Recents root should be the doc uri
        // and not the media uri.
        verify(spyClipper, times(1))
                .getClipDataForDocuments(List.of(docUri), FileOperationService.OPERATION_UNKNOWN);
    }

    @Test
    public void testCanSpringOpen_ReturnsFalse_RootNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        assertFalse(mManager.canSpringOpen(TestProvidersAccess.HAMMY, TestEnv.FOLDER_2));
    }

    @Test
    public void testInArchiveUris_HasCorrectFlagPermission() {
        mDetails.containsFilesInArchive = true;
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_IN_ARCHIVE),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FILE_ARCHIVE.derivedUri, TestEnv.FILE_IN_ARCHIVE.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FILE_ARCHIVE,
                CAN_DRAG_AND_DROP,
                mDocs);

        mFlagListener.assertLastArgument(View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE);
    }

    @Test
    public void testCanSpringOpen_ReturnsFalse_DocIsInvalidDestination() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        assertFalse(mManager.canSpringOpen(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1));
    }

    @Test
    public void testCanSpringOpen() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        assertTrue(mManager.canSpringOpen(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2));
    }

    @Test
    public void testDefaultToUnknownState() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mShadowBuilder.state.assertLastArgument(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testUpdateStateToNotAllowed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateStateToNotAllowed(mUpdateShadowView);

        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    public void testUpdateState_UpdatesToNotAllowed_RootNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_2);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    public void testUpdateState_UpdatesToUnknown_RootDocIsNull() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, null);

        assertEquals(DragAndDropManager.STATE_UNKNOWN, state);
        assertStateUpdated(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_SameRoot_LeftCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testIsDragFromSameApp_afterStartDrag() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        assertTrue(mManager.isDragFromSameApp());
    }

    @Test
    public void testIsDragFromSameApp_beforeStartDrag() {
        assertFalse(mManager.isDragFromSameApp());
    }

    @Test
    public void testIsDragFromSameApp_afterStartDrag_afterDragEnded() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);
        assertTrue(mManager.isDragFromSameApp());

        mManager.dragEnded();
        assertFalse(mManager.isDragFromSameApp());
    }


    @Test
    public void testUpdateState_UpdatesToCopy_SameRoot_RightCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToMove_DifferentRoot_LeftCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_DifferentRoot_RightCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot_LeftCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot_LeftCtrlReleased_Canceled() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        // Press Ctrl -> State becomes COPY.
        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        assertEquals(DragAndDropManager.STATE_COPY, mShadowBuilder.state.getLastValue().intValue());

        // Release Ctrl with CANCELED flag, and CTRL modifier still on.
        event =
                KeyEvents.createTestEvent(
                        KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON);
        event.setFlags(KeyEvent.FLAG_CANCELED);

        mManager.onKeyEvent(event);

        // State should be back to MOVE.
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot_RightCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot_LeftCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot_RightCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateStateWithNullRootInfo() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state =
                mManager.updateState(mUpdateShadowView, /* destItemInfo= */ null, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_UNKNOWN, state);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToCopyDiffRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.TEST_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        docInfo.flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.TEST_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToCopySameRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.PEPPER,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.TEST_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        docInfo.flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.TEST_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToMoveDiffRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        docInfo.flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.TEST_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToMoveSameRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.PEPPER,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        docInfo.flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.TEST_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToStateNotAllowed_ShortcutDoesNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        docInfo.flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;

        // LIVE_IMAGES_SHORTCUT does not support create.
        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.LIVE_IMAGES_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testUpdateStateForShortcut_UpdatesToStateNotAllowed_DocsDoesNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.TEST_SHORTCUT.getUri();
        docInfo.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.TEST_SHORTCUT, docInfo);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    public void testResetState_UpdatesToUnknown() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateStateToNotAllowed(mUpdateShadowView);

        mManager.resetState(mUpdateShadowView);

        assertStateUpdated(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testDrop_Rejects_RootNotSupportCreate_DropOnRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_1);

        assertFalse(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        TestProvidersAccess.HAMMY,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Rejects_InvalidRoot() {
        RootInfo root = new RootInfo();
        root.authority = TestProvidersAccess.HOME.authority;
        root.documentId = TestEnv.FOLDER_0.documentId;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                root,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HOME, TestEnv.FOLDER_0);

        assertFalse(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        root,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Fails_NotGetRootDoc() throws Exception {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.DOWNLOADS,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        mCallbackListener.assertLastArgument(FileOperations.Callback.STATUS_FAILED);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Copies_DifferentRoot_DropOnRoot() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.DOWNLOADS,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Moves_SameRoot_DropOnRoot() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.DOWNLOADS,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_MOVE);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Copies_SameRoot_DropOnRoot_ReleasesCtrlBeforeGettingRootDocument()
            throws Exception{
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.DOWNLOADS,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Rejects_RootNotSupportCreate_DropOnDocument() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.HAMMY, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertFalse(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Copies_DifferentRoot_DropOnDocument() throws Exception {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        mEnv.beforeAsserts();

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Moves_SameRoot_DropOnDocument() throws Exception {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        mEnv.beforeAsserts();

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_MOVE);

        verify(mMockPermissions).release();
    }

    @Test
    public void testDrop_Copies_SameRoot_ReadOnlyFile_DropOnDocument() throws Exception {
        mDetails.canDelete = false;
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_READ_ONLY),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_READ_ONLY.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        mEnv.beforeAsserts();

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testDrop_Copies_DropOnShortcut() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.PEPPER, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.TEST_SHORTCUT,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.PEPPER, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testDrop_Copies_DropOnShortcut_FailedRootDoesNotSupportCreate() {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.EXTERNALSTORAGE, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);

        assertFalse(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        TestProvidersAccess.HOME_SCREEN_SHORTCUT,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        verify(mMockPermissions).release();
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testDrop_Copies_DropOnShortcut_FailedDocIsInvalidDestination() {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.EXTERNALSTORAGE, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);

        assertFalse(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        TestProvidersAccess.HOME_SCREEN_SHORTCUT,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        verify(mMockPermissions).release();
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testDrop_MoveAShortcut_OperationBlocked() throws Exception {
        TestActionHandler spyActionHandler = spy(mActions);
        TestDocumentClipper spyClipper = spy(mClipper);
        DragAndDropManager newManager = createNewManagerWithSpyClipper(spyClipper);

        newManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.EXTERNALSTORAGE,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        newManager.onKeyEvent(event);

        // Mock the shortcut URI item for the clip data.
        Mockito.when(mClipData.getItemCount()).thenReturn(1);
        Mockito.when(mClipData.getItemAt(0)).thenReturn(
                new ClipData.Item(TestProvidersAccess.LIVE_IMAGES_SHORTCUT.getUri()));

        DocumentInfo docInfo = new DocumentInfo();
        docInfo.derivedUri = TestProvidersAccess.DOWNLOADS.getUri();
        newManager.drop(
                mMockPermissions,
                mClipData,
                newManager,
                new DocumentStack(TestProvidersAccess.DOWNLOADS, docInfo),
                spyActionHandler,
                mDocs,
                mCallback);

        mEnv.beforeAsserts();
        // Verify that the block operation was called.
        verify(spyActionHandler).blockOperationForShortcuts(any(), any());
        // If the file operation is blocked, copyFromClipData should never have been called.
        verify(spyClipper, never()).copyFromClipData(
                any(), any(), anyInt(), any());

        verify(mMockPermissions).release();
    }

    @SuppressLint("VisibleForTests")
    private DragAndDropManager createNewManagerWithSpyClipper(TestDocumentClipper spyClipper) {
        DragAndDropManager newManager = new RuntimeDragAndDropManager(
                mActivity, spyClipper, mEnv.mExecutor, mShadowBuilder, mDefaultIcon,
                mEnv.providers) {
            @Override
            void startDragAndDrop(View v, ClipData clipData, DragShadowBuilder builder,
                    Object localState, int flag) {
                assertSame(mStartDragView, v);
                assertSame(mShadowBuilder, builder);
                assertNotNull(localState);

                mFlagListener.accept(flag);
                mStartDragListener.accept(clipData);
            }

            @Override
            void updateDragShadow(View v) {
                assertSame(mUpdateShadowView, v);

                mShadowUpdateListener.accept(null);
            }
        };
        return newManager;
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testDrop_Trashes_DropOnTrashRoot() throws Exception {
        mActions.nextRootDocument = null;
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_TRASH),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_SUPPORTS_TRASH.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.TRASH_ROOT, null);

        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.TRASH_ROOT,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        final DocumentStack expect = new DocumentStack(TestProvidersAccess.TRASH_ROOT);
        mClipper.trashFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_TRASH);

        verify(mMockPermissions).release();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testDrop_Rejects_DropOnDocumentInTrash() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.TRASH_ROOT, TestEnv.FILE_JPG);

        final DocumentStack stack =
                new DocumentStack(TestProvidersAccess.TRASH_ROOT, TestEnv.FILE_JPG);
        assertFalse(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        verify(mMockPermissions).release();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testUpdateState_UpdatesToTrash_WhenDropOnTrashRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_TRASH),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_SUPPORTS_TRASH.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state =
                mManager.updateState(mUpdateShadowView, TestProvidersAccess.TRASH_ROOT, null);

        assertEquals(DragAndDropManager.STATE_TRASH, state);
        assertStateUpdated(DragAndDropManager.STATE_TRASH);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testUpdateState_UpdatesToNotAllowed_WhenDropOnDocumentInTrash() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state =
                mManager.updateState(
                        mUpdateShadowView, TestProvidersAccess.TRASH_ROOT, TestEnv.FOLDER_0);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testUpdateState_UpdatesToNotAllowed_WhenFileDoesNotSupportTrash() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        final @State int state =
                mManager.updateState(
                        mUpdateShadowView, TestProvidersAccess.TRASH_ROOT, TestEnv.FILE_JPG);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testCanSpringOpen_ReturnsFalse_ForTrashRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CAN_DRAG_AND_DROP,
                mDocs);

        assertFalse(mManager.canSpringOpen(TestProvidersAccess.TRASH_ROOT, TestEnv.FILE_APK));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testDrop_Restores_DropOnValidRoot() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        // Start dragging a trashed file.
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE),
                TestProvidersAccess.TRASH_ROOT,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE.derivedUri),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        // Update state to a valid destination.
        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HOME, TestEnv.FOLDER_1);

        // Drop the item.
        mManager.drop(
                mMockPermissions,
                mClipData,
                mManager,
                TestProvidersAccess.HOME,
                mActions,
                mDocs,
                mCallback,
                mManager.getInvalidDestinations());

        mEnv.beforeAsserts();
        final DocumentStack expect = new DocumentStack(TestProvidersAccess.HOME, TestEnv.FOLDER_1);
        // Verify that restoreFromTrashClipData is called with the correct parameters.
        mClipper.restoreFromClipData.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_RESTORE);

        verify(mMockPermissions).release();
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Copies_DropOnDocument_fromOtherApps() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_COPY,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Copies_DropOnShortcut_fromOtherApps() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_COPY,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.DOWNLOADS),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Moves_DropOnDocument_fromOtherApps() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_MOVE,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Moves_DropOnShortcut_fromOtherApps() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_MOVE,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Rejects_DropOnDocument_fromOtherApps_withNonDocumentUri()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, NON_MEDIA_STORE_URI_0));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Rejects_DropOnShortcut_fromOtherApps_withNonDocumentUri()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, NON_MEDIA_STORE_URI_0));
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Rejects_DropOnDocument_fromOtherApps_withNullPermissions()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ null,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Rejects_DropOnShortcut_fromOtherApps_withNullPermissions()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ true,
                /* permissions= */ null,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Rejects_DropOnDocument_fromOtherApps_withSameParent() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.singletonList(TestEnv.FILE_TXT),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Rejects_DropOnShortcut_fromOtherApps_withSameParent() throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.HOME),
                /* dstDocChildList= */ Collections.singletonList(TestEnv.FILE_TXT),
                /* forceUriPermissions= */ true,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags(Flags.FLAG_DRAGS_FROM_OTHER_APPS)
    public void testDrop_Rejects_DropOnDocument_fromOtherApps_withUnforcedUriPermissions()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_0),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ false,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    @Test
    @EnableFlags({
        Flags.FLAG_DRAGS_FROM_OTHER_APPS,
        Flags.FLAG_ENABLE_TRASH_FLOW_RO,
        Flags.FLAG_USE_MATERIAL3
    })
    public void testDrop_Rejects_DropOnShortcut_fromOtherApps_withUnforcedUriPermissions()
            throws Exception {
        testDrop_fromOtherApps(
                /* expectedOpType= */ FileOperationService.OPERATION_UNKNOWN,
                /* dstStack= */ new DocumentStack(TestProvidersAccess.DOWNLOADS),
                /* dstDocChildList= */ Collections.emptyList(),
                /* forceUriPermissions= */ false,
                /* permissions= */ mMockPermissions,
                /* uriList= */ List.of(MEDIA_STORE_URI_0, TestEnv.FILE_TXT.derivedUri));
    }

    private void testDrop_fromOtherApps(
            @OpType int expectedOpType,
            DocumentStack dstStack,
            List<DocumentInfo> dstDocChildList,
            boolean forceUriPermissions,
            @Nullable Permissions permissions,
            List<Uri> uriList)
            throws Exception {
        final boolean dropOnRoot = dstStack.isEmpty();

        final DocumentStack expectedDstStack =
                dropOnRoot ? new DocumentStack(dstStack.getRoot(), TestEnv.FOLDER_0) : dstStack;

        // Set up action handler.
        mActions.nextRootDocument = expectedDstStack.peek();

        final List<ClipData.Item> itemList =
                uriList.stream().map(ClipData.Item::new).collect(Collectors.toList());

        // Set up clip data.
        Mockito.when(mClipData.getItemCount()).thenReturn(itemList.size());
        Mockito.when(mClipData.getItemAt(anyInt())).thenAnswer(i -> itemList.get(i.getArgument(0)));

        // Set up content provider.
        mEnv.mockProviders
                .get(EXTERNAL_STORAGE_PROVIDER_AUTHORITY)
                .setNextChildDocumentsReturns(
                        dstDocChildList.toArray(new DocumentInfo[dstDocChildList.size()]));
        mEnv.providers.nextProviderInfo = new ProviderInfo();
        mEnv.providers.nextProviderInfo.forceUriPermissions = forceUriPermissions;

        // Set up documents access.
        if (!uriList.isEmpty() && Providers.isMediaStoreUri(uriList.get(0))) {
            mDocs.nextIsDocumentsUri = true;
            mDocs.mNextDocumentUri =
                    DocumentsContract.buildDocumentUri(
                            EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                            Long.toString(ContentUris.parseId(uriList.get(0))));
        }

        // Perform and verify state update.
        assertEquals(
                DragAndDropManager.STATE_UNKNOWN,
                mManager.updateState(
                        mUpdateShadowView,
                        dstStack.getRoot(),
                        !dstStack.isEmpty() ? dstStack.peek() : null));

        // Perform and verify drop.
        assertTrue(
                dropOnRoot
                        ? mManager.drop(
                                permissions,
                                mClipData,
                                mManager,
                                dstStack.getRoot(),
                                mActions,
                                mDocs,
                                mCallback,
                                mManager.getInvalidDestinations())
                        : mManager.drop(
                                permissions,
                                mClipData,
                                mManager,
                                dstStack,
                                mActions,
                                mDocs,
                                mCallback));

        mEnv.beforeAsserts();

        // Verify expected clipper interactions.
        if (expectedOpType == FileOperationService.OPERATION_UNKNOWN) {
            mClipper.copyFromClip.assertNotCalled();
            mClipper.opType.assertNotCalled();
        } else {
            final Pair<DocumentStack, ClipData> actual = mClipper.copyFromClip.getLastValue();
            assertNotNull(actual);

            final DocumentStack actualDstStack = actual.first;
            assertNotNull(actualDstStack);
            assertEquals(expectedDstStack, actualDstStack);

            final ClipData actualClipData = actual.second;
            assertNotNull(actualClipData);
            assertEquals(uriList.size(), actualClipData.getItemCount());

            for (int i = 0; i < uriList.size(); ++i) {
                final ClipData.Item actualItem = actualClipData.getItemAt(i);
                assertNotNull(actualItem);

                final Uri actualUri = actualItem.getUri();
                assertNotNull(actualUri);

                Uri expectedUri = uriList.get(i);
                if (Providers.isMediaStoreUri(expectedUri)) {
                    expectedUri =
                            DocumentsContract.buildDocumentUri(
                                    EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                                    Long.toString(ContentUris.parseId(expectedUri)));
                }
                assertEquals(expectedUri, actualUri);
            }

            mClipper.opType.assertLastArgument(expectedOpType);
        }

        // Verify expected permissions interactions.
        if (permissions != null) {
            verify(permissions).release();
        }
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testUpdateState_UpdatesToRestore_WhenDropOnValidRoot() {
        // Start dragging a trashed file.
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE),
                TestProvidersAccess.TRASH_ROOT,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE.derivedUri),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        // Update the state by hovering over a valid destination.
        final @State int state =
                mManager.updateState(mUpdateShadowView, TestProvidersAccess.HOME, TestEnv.FOLDER_1);

        // Verify the state is updated to STATE_RESTORES_FROM_TRASH.
        assertEquals(DragAndDropManager.STATE_RESTORES_FROM_TRASH, state);
        assertStateUpdated(DragAndDropManager.STATE_RESTORES_FROM_TRASH);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testDrop_Rejects_RestoreToDifferentAuthority() {
        // Start dragging a trashed file from HOME provider.
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE),
                TestProvidersAccess.TRASH_ROOT,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE.derivedUri),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        final DocumentStack stack =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        assertFalse(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        verify(mMockPermissions).release();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDrop_onRoot_Fails_WhenCannotDragAndDrop() {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertFalse(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        TestProvidersAccess.DOWNLOADS,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        verify(mMockPermissions).release();
    }

    @Test
    @DisableFlags(FLAG_CLOUD_FEATURES)
    public void testDrop_onRoot_Succeeds_WhenCannotDragAndDrop_FeatureFlagDisabled()
            throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertTrue(
                mManager.drop(
                        mMockPermissions,
                        mClipData,
                        mManager,
                        TestProvidersAccess.DOWNLOADS,
                        mActions,
                        mDocs,
                        mCallback,
                        mManager.getInvalidDestinations()));

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_MOVE);

        verify(mMockPermissions).release();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDrop_onTarget_Fails_WhenCannotDragAndDrop() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack =
                new DocumentStack(
                        TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertFalse(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        verify(mMockPermissions).release();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(FLAG_CLOUD_FEATURES)
    public void testDrop_onTarget_Succeeds_WhenCannotDragAndDrop_FeatureFlagDisabled()
            throws Exception {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack =
                new DocumentStack(
                        TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(
                mManager.drop(
                        mMockPermissions, mClipData, mManager, stack, mActions, mDocs, mCallback));

        mEnv.beforeAsserts();

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);

        verify(mMockPermissions).release();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testUpdateState_UpdatesToNotAllowed_WhenRestoringToDifferentAuthority() {
        // Start dragging a trashed file from HOME provider.
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE),
                TestProvidersAccess.TRASH_ROOT,
                Arrays.asList(TestEnv.FILE_SUPPORTS_RESTORE.derivedUri),
                mDetails,
                mIconHelper,
                null,
                CAN_DRAG_AND_DROP,
                mDocs);

        // Try to drop on a different authority (DOWNLOADS).
        final @State int state =
                mManager.updateState(
                        mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testUpdateState_UpdatesToNotAllowed_WhenCannotDragAndDrop() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        final @State int state =
                mManager.updateState(
                        mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(FLAG_CLOUD_FEATURES)
    public void testUpdateState_UpdatesToMove_WhenCannotDragAndDrop_FeatureFlagDisabled() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(
                        TestEnv.FOLDER_0.derivedUri,
                        TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0,
                CANNOT_DRAG_AND_DROP,
                mDocs);

        final @State int state =
                mManager.updateState(
                        mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    private void assertStateUpdated(@State int expected) {
        mShadowBuilder.state.assertLastArgument(expected);
        mShadowUpdateListener.assertCalled();
    }

    public static class TestDragShadowBuilder extends DragShadowBuilder {

        public TestEventListener<String> title;
        public TestEventListener<Drawable> icon;
        public TestEventListener<Integer> state;
        public TestEventListener<Integer> count;

        private TestDragShadowBuilder() {
            super(null);
        }

        @Override
        void updateTitle(String title) {
            this.title.accept(title);
        }

        @Override
        void updateIcon(Drawable icon) {
            this.icon.accept(icon);
        }

        @Override
        void onStateUpdated(@State int state) {
            this.state.accept(state);
        }

        @Override
        void updateDragFileCount(int count) {
            this.count.accept(count);
        }

        public static TestDragShadowBuilder create() {
            TestDragShadowBuilder builder =
                    Mockito.mock(TestDragShadowBuilder.class, Mockito.CALLS_REAL_METHODS);

            builder.title = new TestEventListener<>();
            builder.icon = new TestEventListener<>();
            builder.state = new TestEventListener<>();
            builder.count = new TestEventListener<>();

            return builder;
        }
    }
}
