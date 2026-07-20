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

package com.android.documentsui.dirlist;

import static com.android.documentsui.flags.Flags.FLAG_DRAGS_FROM_OTHER_APPS;
import static com.android.documentsui.util.Material3Config.getRes;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.DragEvent;
import android.view.View;

import androidx.recyclerview.selection.SelectionTracker;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.DragAndDropManager.Permissions;
import com.android.documentsui.R;
import com.android.documentsui.SelectionHelpers;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.files.TestActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.DragEvents;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDocumentsAccess;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.Views;
import com.android.documentsui.ui.TestDialogController;

import com.google.android.material.snackbar.Snackbar;

import kotlin.Triple;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.function.BiFunction;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragHostTest {
    private static final List<String> ITEMS = TestData.create(100);

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestActionHandler mActionHandler;
    private TestDialogController mDialogs;
    private TestDocumentsAccess mDocs;
    private DragHost<?> dragHost;
    private TestDragAndDropManager mDragAndDropManager;
    private SelectionTracker<String> mSelectionMgr;
    private boolean mIsDocumentView;
    private DocumentHolder mNextDocumentHolder;
    private DocumentInfo mNextDocumentInfo;

    @Mock private BiFunction<Activity, DragEvent, Permissions> mMockRequestPermissionsHandler;

    @Before
    public void setUp() throws Exception {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mDialogs = new TestDialogController();
        mDocs = new TestDocumentsAccess();
        mDragAndDropManager = new TestDragAndDropManager();
        mSelectionMgr = SelectionHelpers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();

        DragAndDropManager.REQUEST_PERMISSIONS_HANDLER_FOR_TESTING.set(
                mMockRequestPermissionsHandler);

        dragHost = new DragHost<>(
                mActivity,
                mDragAndDropManager,
                mSelectionMgr,
                mActionHandler,
                mEnv.state,
                mDialogs,
                mDocs,
                (View v) -> mIsDocumentView,
                (View v) -> mNextDocumentHolder,
                (View v) -> mNextDocumentInfo
        );
    }

    @After
    public void tearDown() {
        DragAndDropManager.REQUEST_PERMISSIONS_HANDLER_FOR_TESTING.set(null);
    }

    @Test
    @DisableFlags(FLAG_DRAGS_FROM_OTHER_APPS)
    public void testCanHandleDragEventFromOtherAppsWithFlagDisabled() {
        testCanHandleDragEvent(/* isDragFromSameApp= */ false, /* expectHandled= */ false);
    }

    @Test
    @EnableFlags(FLAG_DRAGS_FROM_OTHER_APPS)
    public void testCanHandleDragEventFromOtherAppsWithFlagEnabled() {
        testCanHandleDragEvent(/* isDragFromSameApp= */ false, /* expectHandled= */ true);
    }

    @Test
    @DisableFlags(FLAG_DRAGS_FROM_OTHER_APPS)
    public void testCanHandleDragEventFromSameAppWithFlagDisabled() {
        testCanHandleDragEvent(/* isDragFromSameApp= */ true, /* expectHandled= */ true);
    }

    @Test
    @EnableFlags(FLAG_DRAGS_FROM_OTHER_APPS)
    public void testCanHandleDragEventFromSameAppWithFlagEnabled() {
        testCanHandleDragEvent(/* isDragFromSameApp= */ true, /* expectHandled= */ true);
    }

    private void testCanHandleDragEvent(boolean isDragFromSameApp, boolean expectHandled) {
        final View view = Views.createTestView();
        final Snackbar snackbar = mock(Snackbar.class);
        final DragHost.SnackbarFactory snackbarFactory = mock(DragHost.SnackbarFactory.class);

        doReturn(snackbar).when(snackbarFactory).make(any(), anyInt(), anyInt());
        dragHost.setSnackbarFactoryForTesting(snackbarFactory);
        mDragAndDropManager.isDragFromSameAppHandler.nextReturn(isDragFromSameApp);

        assertEquals(expectHandled, dragHost.canHandleDragEvent(view));

        if (!expectHandled) {
            final ArgumentCaptor<Integer> durationArg = ArgumentCaptor.forClass(Integer.class);
            final ArgumentCaptor<Integer> resIdArg = ArgumentCaptor.forClass(Integer.class);
            final ArgumentCaptor<View> viewArg = ArgumentCaptor.forClass(View.class);

            verify(snackbarFactory)
                    .make(viewArg.capture(), resIdArg.capture(), durationArg.capture());

            assertEquals(Snackbar.LENGTH_LONG, (int) durationArg.getValue());
            assertEquals(getRes(R.string.drag_from_another_app), (int) resIdArg.getValue());
            assertEquals(view, viewArg.getValue());

            verify(snackbar).show();
        }

        verifyNoMoreInteractions(snackbar, snackbarFactory);
    }

    @Test
    public void testHandleDrop_onValidView() {
        final ClipData data = ClipDatas.createTestClipData();
        final DragEvent dropEvent = DragEvents.createTestDropEvent(data);
        final View view = Views.createTestView();
        mNextDocumentInfo = TestEnv.FOLDER_0;
        mDragAndDropManager.dropOnDocumentHandler.nextReturn(true);

        final Permissions permissions = mock(Permissions.class);
        doReturn(permissions).when(mMockRequestPermissionsHandler).apply(mActivity, dropEvent);

        assertTrue(dragHost.handleDropEvent(view, dropEvent));
        mDragAndDropManager.dropOnDocumentHandler.assertCalled();

        final Triple<Permissions, ClipData, DocumentStack> actual =
                mDragAndDropManager.dropOnDocumentHandler.getLastValue();

        assertNotNull(actual);
        assertSame(permissions, actual.getFirst());
        assertSame(data, actual.getSecond());
        assertEquals(new DocumentStack(mEnv.state.stack, mNextDocumentInfo), actual.getThird());
    }

    @Test
    public void testHandleDrop_notOnValidView() {
        final ClipData data = ClipDatas.createTestClipData();
        final DragEvent dropEvent = DragEvents.createTestDropEvent(data);
        final View view = Views.createTestView();

        assertFalse(dragHost.handleDropEvent(view, dropEvent));
        mDragAndDropManager.dropOnDocumentHandler.assertNotCalled();
        verifyNoMoreInteractions(mMockRequestPermissionsHandler);
    }

    @Test
    public void testDragHoverHighlightsDirListBorder() {
        final Drawable mockDrawable = Mockito.mock(Drawable.class);
        final View view = Views.createTestView();
        doReturn(getRes(R.id.dir_list)).when(view).getId();
        doReturn(mockDrawable).when(view).getBackground();

        // Highlight the container.
        dragHost.setDropTargetHighlight(view, true);
        verify(view).setBackgroundResource(R.drawable.dir_list_drag_hover_background);

        // Turn highlighting off and revert back to regular list directory.
        dragHost.setDropTargetHighlight(view, false);
        verify(view).setBackground(mockDrawable);
    }
}
