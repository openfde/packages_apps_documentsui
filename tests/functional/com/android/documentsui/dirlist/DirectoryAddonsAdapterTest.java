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

import static android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API;
import static android.provider.Flags.FLAG_ENABLE_SYNC_STATE;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.ModelId;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;
import com.android.documentsui.testing.TestModel;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.util.VersionUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class DirectoryAddonsAdapterTest {

    private static final String AUTHORITY = "test_authority";

    private TestEnv mEnv;
    private DirectoryAddonsAdapter mAdapter;
    private ActionHandler mActionHandler;
    private TestConfigStore mTestConfigStore;
    private TestEnvironment mTestEnvironment;

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mEnv = TestEnv.create(AUTHORITY);
        mActionHandler = new TestActionHandler();
        mEnv.clear();
        mTestConfigStore = new TestConfigStore();

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Context testContext = TestContext.createStorageTestContext(context, AUTHORITY);
        mTestEnvironment = new TestEnvironment(testContext, mEnv, mActionHandler);

        mAdapter = new DirectoryAddonsAdapter(
                mTestEnvironment,
                new ModelBackedDocumentsAdapter(
                        mTestEnvironment,
                        new IconHelper(testContext, State.MODE_GRID, /* maybeShowBadge= */ false,
                                null, TestProvidersAccess.OtherUser.USER_ID, null,
                                mTestConfigStore),
                        new TestFileTypeLookup(), mTestConfigStore), mTestConfigStore);

        mEnv.model.addUpdateListener(mAdapter.getModelUpdateListener());
    }

    // Tests that the item count is correct for a directory containing files and subdirs.
    @Test
    public void testItemCount_mixed() {
        mEnv.reset(); // creates a mix of folders and files for us.

        int expectedItemCount = mEnv.model.getItemCount() + (isUseMaterial3FlagEnabled() ? 0 : 1);
        assertEquals(expectedItemCount, mAdapter.getItemCount());
    }

    @Test
    public void testGetPosition() {
        mEnv.model.createFolder("a");  // id will be "1"...derived from insert position.
        mEnv.model.createFile("b");  // id will be "2"
        mEnv.model.update();

        assertEquals(0, mAdapter.getPosition(ModelId.build(mEnv.model.mUserId, AUTHORITY, "1")));
        // adapter inserts a view between item 0 and 1 (this doesn't happen when use_material3 flag
        // is enabled) to force a layout break between folders and files. This is reflected by an
        // offset position.
        int position = isUseMaterial3FlagEnabled() ? 1 : 2;
        assertEquals(
                position, mAdapter.getPosition(ModelId.build(mEnv.model.mUserId, AUTHORITY, "2")));
    }

    // Tests that the item count is correct for a directory containing only subdirs.
    @Test
    public void testItemCount_allDirs() {
        String[] names = {"Trader Joe's", "Alphabeta", "Lucky", "Vons", "Gelson's"};
        for (String name : names) {
            mEnv.model.createFolder(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    // Tests that the item count is correct for a directory containing only files.
    @Test
    public void testItemCount_allFiles() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    @Test
    public void testAddsInfoMessage_WithDirectoryChildren() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    public void testItemCount_none() {
        mEnv.model.update();
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    @Test
    public void testAddsInfoMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    public void testAddsErrorMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_ERROR, "some error");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    public void testOpenTreeMessage_shouldBlockChild() {
        if (!VersionUtils.isAtLeastR()) {
            return;
        }

        mEnv.state.action = State.ACTION_OPEN_TREE;
        mEnv.state.restrictScopeStorage = true;
        DocumentInfo info = new DocumentInfo();
        info.flags += DocumentsContract.Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE;
        mEnv.state.stack.push(info);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    public void testOpenTreeMessage_normalChild() {
        mEnv.state.action = State.ACTION_OPEN_TREE;
        DocumentInfo info = new DocumentInfo();
        mEnv.state.stack.push(info);

        mEnv.model.update();
        // Should only no items message show
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    @Test
    public void testOpenTreeMessage_restrictStorageAccessFalse_blockTreeChild() {
        if (!VersionUtils.isAtLeastR()) {
            return;
        }

        mEnv.state.action = State.ACTION_OPEN_TREE;
        DocumentInfo info = new DocumentInfo();
        info.flags += DocumentsContract.Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE;
        mEnv.state.stack.push(info);

        mEnv.model.update();
        // Should only no items message show
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    private void assertHolderType(int index, int type) {
        assertTrue(mAdapter.getItemViewType(index) == type);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testAddsEmptyTrashBanner_OnTrashPage() {
        mTestEnvironment.setIsOnTrashPage(true);
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }

        // Trigger an update to the model, which should refresh the adapter.
        mEnv.model.update();

        // Verify that the adapter now contains one extra item for the banner.
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        // Verify that the first item in the adapter is the header message (the banner).
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void
            testOnNetworkStateChanged_onRootWithLimitedFunctionalityWhenOffline_modifiesHeader() {
        // Create a file to avoid the no items inflated message showing.
        mEnv.model.createFile("a.txt");

        // Start offline.
        mTestEnvironment.setIsOnline(false);
        // Set root that has limited functionality when offline.
        ((TestModel) mTestEnvironment.getModel()).setHasLimitedFunctionalityWhenOffline(true);
        mEnv.model.update();

        // Check header is shown.
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);

        // Go online.
        mTestEnvironment.setIsOnline(true);
        mAdapter.getNetworkListener().onNetworkStateChanged(true);

        // Check header is removed.
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());

        // Go offline.
        mTestEnvironment.setIsOnline(false);
        mAdapter.getNetworkListener().onNetworkStateChanged(true);

        // Check header is shown.
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testOnNetworkStateChanged_flagDisabled_doesNotShowHeader() {
        // Create a file to avoid the no items inflated message showing.
        mEnv.model.createFile("a.txt");

        mTestEnvironment.setIsOnline(false);
        // Set root that has limited functionality when offline.
        ((TestModel) mTestEnvironment.getModel()).setHasLimitedFunctionalityWhenOffline(true);

        // Clear any default messages set by the model update
        mEnv.model.update();

        // Check header is not shown.
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testUpdate_toRootWithoutLimitedFunctionalityWhenOffline_removesHeader() {
        // Create a file to avoid the no items inflated message showing.
        mEnv.model.createFile("a.txt");

        mTestEnvironment.setIsOnline(false);
        // Start with root that has limited functionality when offline.
        ((TestModel) mTestEnvironment.getModel()).setHasLimitedFunctionalityWhenOffline(true);
        mEnv.model.update();

        // Check header is shown.
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);

        // Move to root that doesn't have limited functionality when offline.
        ((TestModel) mTestEnvironment.getModel()).setHasLimitedFunctionalityWhenOffline(false);

        // Send a model notification.
        mEnv.model.update();

        // Check header is hidden.
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());

        // Send a network notification.
        mAdapter.getNetworkListener().onNetworkStateChanged(false);

        // Check header is still hidden.
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    private static class StubAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return 0;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return null;
        }
    }
}
