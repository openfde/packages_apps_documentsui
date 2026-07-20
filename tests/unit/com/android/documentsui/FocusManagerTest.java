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

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.Material3Config.getRes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.ColorStateList;
import android.platform.test.annotations.EnableFlags;
import android.view.View;
import android.widget.Button;

import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestGridLayoutManager;
import com.android.documentsui.testing.TestModel;
import com.android.documentsui.testing.TestRecyclerView;
import com.android.documentsui.testing.Views;

import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FocusManagerTest {

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private static final String TEST_AUTHORITY = "test_authority";

    private static final List<String> ITEMS = TestData.create(10);

    private FocusManager mManager;
    private TestRecyclerView mView;
    private TestGridLayoutManager mTestGridLayoutManager;
    private SelectionTracker<String> mSelectionMgr;
    private TestFeatures mFeatures;

    @Before
    public void setUp() throws Exception {
        mView = TestRecyclerView.create(ITEMS);
        mTestGridLayoutManager = TestGridLayoutManager.create();
        mView.setLayoutManager(mTestGridLayoutManager);

        mSelectionMgr = SelectionHelpers.createTestInstance(ITEMS);
        mFeatures = new TestFeatures();
        mManager = new FocusManager(mFeatures, mSelectionMgr, null, null, 0).reset(mView,
                new TestModel(UserId.DEFAULT_USER, TEST_AUTHORITY, mFeatures));
    }

    @Test
    public void testFocus() {
        mManager.focusDocument(Integer.toString(3));
        mView.assertItemViewFocused(3);
     }

    @Test
    public void testPendingFocus() {
       mManager.focusDocument(Integer.toString(10));
       List<String> mutableItems = TestData.create(11);
       mView.setItems(mutableItems);
       mManager.onLayoutCompleted();
       // Should only be called once
       mView.assertItemViewFocused(10);
    }

    @Test
    public void testFocusDirectoryList_noItemsToFocus() {
        mView = TestRecyclerView.create(new ArrayList<>());
        mManager = new FocusManager(
                mFeatures, SelectionHelpers.createTestInstance(), null, null, 0)
                .reset(mView, new TestModel(UserId.DEFAULT_USER, TEST_AUTHORITY, mFeatures));
        assertFalse(mManager.focusDirectoryList());
    }

    @Test
    public void testFocusDirectoryList_noVisibleItems() {
        mTestGridLayoutManager.setFirstVisibleItemPosition(RecyclerView.NO_POSITION);
        assertFalse(mManager.focusDirectoryList());
    }

    @Test
    public void testFocusDirectoryList_hasSelection() {
        mSelectionMgr.select("0");
        assertFalse(mManager.focusDirectoryList());
    }

    @Test
    public void testFocusDirectoryList_invalidContentScope() {
        mManager = new FocusManager(
                mFeatures, SelectionHelpers.createTestInstance(), null, null, 0);
        // pass if no exception is thrown.
        mManager.focusDirectoryList();
    }

    @Test
    public void testOnFocusChange_invalidContentScope() {
        mManager = new FocusManager(
                mFeatures, SelectionHelpers.createTestInstance(), null, null, 0);
        // pass if no exception is thrown.
        mManager.onFocusChange(Views.createTestView(), true);
    }

    @Test
    public void testClearFocus_invalidContentScope() {
        mManager = new FocusManager(
                mFeatures, SelectionHelpers.createTestInstance(), null, null, 0);
        // pass if no exception is thrown.
        mManager.clearFocus();
    }

    @Test
    public void testFocusDocument_invalidContentScope() {
        mManager = new FocusManager(
                mFeatures, SelectionHelpers.createTestInstance(), null, null, 0);
        // pass if no exception is thrown.
        mManager.focusDocument(Integer.toString(0));
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testSetButtonFocus_normalButton() {
        Button button = mock(Button.class);

        FocusManager.setButtonFocusStyle(button);

        // Verify the focus listener is not added for normal button.
        verify(button, never()).setOnFocusChangeListener(any());
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testSetButtonFocus_materialButton() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Material button requires a material theme.
        context.setTheme(getRes(R.style.DocumentsTheme));
        context.getTheme().applyStyle(getRes(R.style.DocumentsDefaultTheme), false);

        MaterialButton button = spy(new MaterialButton(context));
        ColorStateList originalColor = mock(ColorStateList.class);
        int originalWidth = 50; // a random big number
        button.setStrokeColor(originalColor);
        button.setStrokeWidth(originalWidth);
        when(button.hasWindowFocus()).thenReturn(true);

        FocusManager.setButtonFocusStyle(button);

        ArgumentCaptor<View.OnFocusChangeListener> focusChangeCaptor =
                ArgumentCaptor.forClass(View.OnFocusChangeListener.class);
        verify(button).setOnFocusChangeListener(focusChangeCaptor.capture());

        // Verify button style has changed when it's focused.
        focusChangeCaptor.getValue().onFocusChange(button, /* hasFocus= */ true);
        assertNotEquals(originalColor, button.getStrokeColor());
        assertNotEquals(originalWidth, button.getStrokeWidth());

        // Verify button style will be restored when it's not focused.
        focusChangeCaptor.getValue().onFocusChange(button, /* hasFocus= */ false);
        assertEquals(originalColor, button.getStrokeColor());
        assertEquals(originalWidth, button.getStrokeWidth());
    }
}
