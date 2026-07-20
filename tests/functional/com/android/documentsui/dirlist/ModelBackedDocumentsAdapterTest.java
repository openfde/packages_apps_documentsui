/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.documentsui.dirlist.DirectoryFragment.TICK_VISIBLE_DURATION_MS;
import static com.android.documentsui.flags.Flags.FLAG_CLOUD_FEATURES;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.Model;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;
import com.android.documentsui.testing.TestHandler;
import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ModelBackedDocumentsAdapterTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String AUTHORITY = "test_authority";
    private static final String TEST_MODEL_ID_0 = "10|test_authority|test_doc_id_0";
    private static final String TEST_MODEL_ID_1 = "10|test_authority|test_doc_id_1";

    private TestEnv mEnv;
    private ActionHandler mActionHandler;
    private TestConfigStore mTestConfigStore;
    private ModelBackedDocumentsAdapter mAdapter;
    private TestHandler mTestHandler;

    @Mock private DocumentHolder mMockDocHolder;

    @Before
    public void setUp() {
        Context testContext = ApplicationProvider.getApplicationContext();
        mEnv = TestEnv.create(AUTHORITY);
        mActionHandler = new TestActionHandler();
        mTestConfigStore = new TestConfigStore();
        mTestHandler = new TestHandler();

        DocumentsAdapter.Environment env = new TestEnvironment(testContext, mEnv, mActionHandler);

        mAdapter =
                new ModelBackedDocumentsAdapter(
                        env,
                        new IconHelper(
                                testContext,
                                State.MODE_GRID,
                                /* maybeShowBadge= */ false,
                                null,
                                TestProvidersAccess.OtherUser.USER_ID,
                                null,
                                mTestConfigStore),
                        new TestFileTypeLookup(),
                        mTestConfigStore,
                        mTestHandler);
    }

    private void dispatchNextTask() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mTestHandler.dispatchNextMessage();
                        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    // Tests that the item count is correct.
    @Test
    public void testItemCount() {
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    /**
     * Tests that an item remains in the "just finished sync" state for TICK_VISIBLE_DURATION_MS
     * after it transitions from syncing to not syncing. And is removed from that state through a
     * removal task.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3})
    public void testJustFinishedSync_stateRemainsForDuration() {
        // The document at position 0 has ID `TEST_MODEL_ID_0`.
        mEnv.model.setModelIds(new String[] {TEST_MODEL_ID_0});

        // Item is not syncing.
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(false));

        // Item is syncing.
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder, times(2))
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(false));

        // Item has finished syncing and is in "just finished sync" state.
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_0);
        long timeBefore = SystemClock.uptimeMillis();
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        long timeAfter = SystemClock.uptimeMillis();
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(true));

        // A task to remove the item from the "just finished sync" state has been scheduled to run
        // TICK_VISIBLE_DURATION_MS after the syncing finished.
        assertEquals(1, mAdapter.mJustFinishedSyncingRemovalTasks.size());
        assertNotNull(mAdapter.mJustFinishedSyncingRemovalTasks.get(TEST_MODEL_ID_0));
        assertTrue(
                mTestHandler.executeTimeOfNextMessage() >= (timeBefore + TICK_VISIBLE_DURATION_MS));
        assertTrue(
                mTestHandler.executeTimeOfNextMessage() <= (timeAfter + TICK_VISIBLE_DURATION_MS));

        // Dispatch the task that will remove the item from the just finished sync" state.
        dispatchNextTask();

        // Item is no longer in "just finished sync" state.
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder, times(3))
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(false));
    }

    /**
     * Tests that items that entered the "just finished sync" state exit that state in different
     * removal tasks.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3})
    public void testJustFinishedSync_multipleSyncs() {
        // The document at position 0 has ID `TEST_MODEL_ID_0` and the document at position 1 has ID
        // `TEST_MODEL_ID_1`.
        mEnv.model.setModelIds(new String[] {TEST_MODEL_ID_0, TEST_MODEL_ID_1});

        // Item 0 is syncing and Item 1 is in "just finished sync" state.
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_0);
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_1);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_1);
        long timeBeforeItem1ExitedSync = SystemClock.uptimeMillis();
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        long timeAfterItem1ExitedSync = SystemClock.uptimeMillis();

        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(false));
        mAdapter.onBindViewHolder(mMockDocHolder, 1);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_1),
                        any(),
                        /* justFinishedSync= */ eq(true));

        // One task to remove Item 1 from the "just finished sync" state has been scheduled to run
        // TICK_VISIBLE_DURATION_MS after the syncing finished.
        assertEquals(1, mAdapter.mJustFinishedSyncingRemovalTasks.size());
        assertNotNull(mAdapter.mJustFinishedSyncingRemovalTasks.get(TEST_MODEL_ID_1));
        assertTrue(
                mTestHandler.executeTimeOfNextMessage()
                        >= (timeBeforeItem1ExitedSync + TICK_VISIBLE_DURATION_MS));
        assertTrue(
                mTestHandler.executeTimeOfNextMessage()
                        <= (timeAfterItem1ExitedSync + TICK_VISIBLE_DURATION_MS));

        // Item 0 is also now in "just finished sync" state.
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_0);
        long timeBeforeItem0ExitedSync = SystemClock.uptimeMillis();
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        long timeAfterItem0ExitedSync = SystemClock.uptimeMillis();
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(true));

        // Another task to remove Item 0 from the "just finished sync" state has been scheduled.
        assertEquals(2, mAdapter.mJustFinishedSyncingRemovalTasks.size());
        assertNotNull(mAdapter.mJustFinishedSyncingRemovalTasks.get(TEST_MODEL_ID_0));

        // Dispatch the task that will remove Item 1 from the just finished sync" state.
        dispatchNextTask();

        // Item 0 is still in the "just finished sync" state but Item 1 no longer is.
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder, times(2))
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(true));
        mAdapter.onBindViewHolder(mMockDocHolder, 1);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_1),
                        any(),
                        /* justFinishedSync= */ eq(false));

        // The task to remove Item 0 from the "just finished sync" state should be scheduled to run
        // TICK_VISIBLE_DURATION_MS after the syncing finished.
        assertTrue(
                mTestHandler.executeTimeOfNextMessage()
                        >= (timeBeforeItem0ExitedSync + TICK_VISIBLE_DURATION_MS));
        assertTrue(
                mTestHandler.executeTimeOfNextMessage()
                        <= (timeAfterItem0ExitedSync + TICK_VISIBLE_DURATION_MS));

        // Dispatch the task that will remove Item 0 from the just finished sync" state.
        dispatchNextTask();

        // Item 0 is no longer in "just finished sync" state.
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder, times(2))
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(false));
    }

    /**
     * Tests that the removal task for an item in the "just finished sync" state is cancelled and
     * replaced with a new one when it finishes another sync.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3})
    public void testJustFinishedSync_stateNotExitedWhenAnotherSyncFinished() {
        // The document at position 0 has ID `TEST_MODEL_ID_0`.
        mEnv.model.setModelIds(new String[] {TEST_MODEL_ID_0});
        // Item exits a sync.
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);

        // One task to remove Item 1 from the "just finished sync" state has been scheduled.
        assertEquals(1, mAdapter.mJustFinishedSyncingRemovalTasks.size());
        Runnable originalTask = mAdapter.mJustFinishedSyncingRemovalTasks.get(TEST_MODEL_ID_0);
        assertNotNull(originalTask);

        // Item exits a second sync and remains in the "just finished sync" state.
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mAdapter.onBindViewHolder(mMockDocHolder, 0);
        verify(mMockDocHolder)
                .bind(
                        any(DocumentInfo.class),
                        eq(TEST_MODEL_ID_0),
                        any(),
                        /* justFinishedSync= */ eq(true));

        // The existing task should be removed and replaced with a new task.
        assertEquals(1, mAdapter.mJustFinishedSyncingRemovalTasks.size());
        Runnable newTask = mAdapter.mJustFinishedSyncingRemovalTasks.get(TEST_MODEL_ID_0);
        assertNotEquals(newTask, originalTask);
    }

    // Tests that the task to remove the item from the "just finished sync" also sends an item
    // changed notification.
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3})
    public void testJustFinishedSync_removalTaskSendsItemChangedNotification() {
        // The document at position 0 has ID `TEST_MODEL_ID_0`.
        mEnv.model.setModelIds(new String[] {TEST_MODEL_ID_0});

        final boolean[] notificationSent = {false};
        mAdapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        notificationSent[0] = true;
                    }
                });

        // Item exits a sync.
        mEnv.model.addSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);
        mEnv.model.removeSyncInProgressModelId(TEST_MODEL_ID_0);
        mAdapter.getModelUpdateListener().accept(Model.Update.UPDATE);

        // Dispatch the task that will remove the item from the "just finished sync" state.
        dispatchNextTask();

        assertTrue(notificationSent[0]);
    }
}