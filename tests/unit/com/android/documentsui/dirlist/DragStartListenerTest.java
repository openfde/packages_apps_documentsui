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

package com.android.documentsui.dirlist;

import static android.provider.Flags.FLAG_ENABLE_SYNC_STATE;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.Build;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.DocsSelectionHelper;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.SelectionHelpers;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.DragStartListener.RuntimeDragStartListener;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestDocumentsAccess;
import com.android.documentsui.testing.TestDragAndDropManager;
import com.android.documentsui.testing.TestEvents;
import com.android.documentsui.testing.TestSelectionDetails;
import com.android.documentsui.testing.Views;

import com.android.documentsui.util.FlagUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragStartListenerTest {
    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static int sIsUnavailableFlag = 1;

    private RuntimeDragStartListener mListener;
    private TestEvents.Builder mEvent;
    private DocsSelectionHelper mSelectionMgr;
    private TestItemDetailsLookup mDocLookup;
    private SelectionDetails mSelectionDetails;
    private String mViewModelId;
    private TestDragAndDropManager mManager;
    private List<DocumentInfo> mSrcs;
    private DocumentInfo mDoc;
    private DocumentsAccess mDocsAccess;

    @Before
    public void setUp() throws Exception {
        mSelectionMgr = SelectionHelpers.createTestInstance();
        mManager = new TestDragAndDropManager();
        mSelectionDetails = new TestSelectionDetails();
        mDocLookup = new TestItemDetailsLookup();
        mSrcs = new ArrayList<>();
        mDoc = new DocumentInfo();
        mDoc.authority = Providers.AUTHORITY_STORAGE;
        mDoc.documentId = "id";
        mDoc.derivedUri = DocumentsContract.buildDocumentUri(mDoc.authority, mDoc.documentId);
        mDocsAccess = new TestDocumentsAccess();

        State state = new State();
        state.stack.push(mDoc);

        mListener =
                new DragStartListener.RuntimeDragStartListener(
                        null, // icon helper
                        state,
                        mSelectionMgr,
                        mSelectionDetails,
                        // view finder
                        (float x, float y) -> {
                            return Views.createTestView(x, y);
                        },
                        // model id finder
                        (View view) -> {
                            return mViewModelId;
                        },
                        // docInfo Converter
                        (Selection<String> selection) -> {
                            return mSrcs;
                        },
                        // isContentAvailable
                        (DocumentInfo docInfo) -> {
                            boolean fakeIsContentAvailable =
                                    docInfo.syncStateFlags == null
                                            || docInfo.syncStateFlags != sIsUnavailableFlag;
                            return fakeIsContentAvailable;
                        },
                        mManager,
                        mDocsAccess);

        mViewModelId = "1234";

        mDocLookup.initAt(1).setInItemDragRegion(true);
        mEvent = TestEvents.builder()
                .action(MotionEvent.ACTION_MOVE)
                .mouse()
                .primary();
    }

    @Test
    public void testMouseEvent() {
        MotionEvent e = mEvent.build();
        // Assert it is a mouse drag event.
        assertTrue(Events.isMousyEvent(e));
        assertTrue(e.getActionMasked() == MotionEvent.ACTION_MOVE);
        assertTrue(e.isButtonPressed(MotionEvent.BUTTON_PRIMARY));
    }

    @Test
    public void testTouchEventIsNotMousy() {
        MotionEvent e = TestEvents.builder().touch().build();
        assertFalse(Events.isMousyEvent(e));
    }

    @Test
    public void testTouchpadEventIsMousy() {
        MotionEvent e = TestEvents.builder().touchpad().build();
        assertTrue(Events.isMousyEvent(e));
    }

    @Test
    public void testDragStarted_OnMouseMove() {
        mSrcs.add(mDoc);

        assertTrue(mListener.onDragEvent(mEvent.build()));

        mManager.startDragHandler.assertCalled();
        mManager.startDragHandler.assertLastArgument(mSrcs);
        assertTrue(mManager.mLastCanDragAndDrop);
    }

    @Test
    public void testDragNotStarted_NonModelBackedView() {
        mViewModelId = null;
        assertFalse(mListener.onDragEvent(mEvent.build()));
        mManager.startDragHandler.assertNotCalled();
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDragStarted_ContentNotAvailable() {
        // Set one of the source files to be unavailable.
        DocumentInfo unavailableDoc = new DocumentInfo();
        unavailableDoc.syncStateFlags = sIsUnavailableFlag;
        mSrcs.add(unavailableDoc);
        mSrcs.add(mDoc);

        assertTrue(mListener.onDragEvent(mEvent.build()));

        // Ensure that the files cannot be dragged and dropped.
        mManager.startDragHandler.assertCalled();
        mManager.startDragHandler.assertLastArgument(mSrcs);
        assertFalse(mManager.mLastCanDragAndDrop);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testDragStarted_ContentNotAvailable_FeatureFlagDisabled() {
        // Set one of the source files to be unavailable.
        DocumentInfo unavailableDoc = new DocumentInfo();
        unavailableDoc.syncStateFlags = sIsUnavailableFlag;
        mSrcs.add(unavailableDoc);
        mSrcs.add(mDoc);

        assertTrue(mListener.onDragEvent(mEvent.build()));

        // Ensure that the files can still be dragged and dropped.
        mManager.startDragHandler.assertCalled();
        mManager.startDragHandler.assertLastArgument(mSrcs);
        assertTrue(mManager.mLastCanDragAndDrop);
    }

    @Test
    public void testDragStart_nonSelectedItem() {
        Selection<String> selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 1);
        assertTrue(selection.contains("1234"));
    }

    @Test
    public void testDragStart_selectedItem() {
        MutableSelection<String> selection = new MutableSelection<>();
        selection.add("1234");
        selection.add("5678");
        mSelectionMgr.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 2);
        assertTrue(selection.contains("1234"));
        assertTrue(selection.contains("5678"));
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testDragStart_newNonSelectedItem() {
        MutableSelection<String> selection = new MutableSelection<>();
        selection.add("5678");
        mSelectionMgr.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 1);
        assertTrue(selection.contains("1234"));
        // After this, selection should be cleared
        assertFalse(mSelectionMgr.hasSelection());
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testDragStart_newNonSelectedItem_updateSelection() {
        MutableSelection<String> selection = new MutableSelection<>();
        selection.add("5678");
        mSelectionMgr.replaceSelection(selection);

        selection =
                mListener.getSelectionToBeCopied(
                        "1234", mEvent.action(MotionEvent.ACTION_MOVE).build());
        assertTrue(selection.size() == 1);
        assertTrue(selection.contains("1234"));
        // Selection should be updated to the SelectionManager.
        assertTrue(mSelectionMgr.hasSelection());
        assertTrue(mSelectionMgr.isSelected("1234"));
    }

    @Test
    public void testCtrlDragStart_newNonSelectedItem() {
        MutableSelection<String> selection = new MutableSelection<>();
        selection.add("5678");
        mSelectionMgr.replaceSelection(selection);

        selection = mListener.getSelectionToBeCopied("1234",
                mEvent.action(MotionEvent.ACTION_MOVE).ctrl().build());
        assertTrue(selection.size() == 2);
        assertTrue(selection.contains("1234"));
        assertTrue(selection.contains("5678"));
    }
}
