/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.State
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags
import com.android.documentsui.loaders.TrashFileLoader
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.ActivityManagers
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestImmediateExecutor
import com.android.documentsui.testing.TestProvidersAccess
import com.android.documentsui.testing.UserManagers
import com.android.modules.utils.build.SdkLevel
import com.google.common.collect.Lists
import junit.framework.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when` as whenever

@RunWith(Parameterized::class)
@MediumTest
@RequiresFlagsEnabled(FLAG_ENABLE_DOCUMENTS_TRASH_API)
@EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
internal class TrashFileLoaderTest {
    private lateinit var mEnv: TestEnv
    private lateinit var mActivity: TestActivity
    private lateinit var mTestConfigStore: TestConfigStore

    @get:Rule val setFlags = OverrideFlagsRule()

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @JvmField @Parameterized.Parameter(0) var isPrivateSpaceEnabled: Boolean = false

    @Before
    fun setUp() {
        mEnv = TestEnv.create()
        mActivity = TestActivity.create(mEnv)
        mActivity.activityManager = ActivityManagers.create(false)
        mActivity.userManager = UserManagers.create()
        mTestConfigStore = TestConfigStore()
        mEnv.state.configStore = mTestConfigStore

        mEnv.state.action = State.ACTION_BROWSE
        mEnv.state.acceptMimes = arrayOf<String>("*/*")
        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker()
            mEnv.state.canForwardToProfileIdMap.put(UserId.DEFAULT_USER, true)
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true)
        } else {
            mEnv.state.canShareAcrossProfile = true
        }
        // Set the stack to the trash root. This is needed to ensure that the loader correctly
        // identifies the trash root.
        mEnv.state.stack.reset(DocumentStack(TestProvidersAccess.TRASH_ROOT, DocumentInfo()))
    }

    /** Tests that the loader correctly fetches trashed documents. */
    @Test
    fun testFetchTrashFiles() {
        val doc1 = mEnv.model.createFile("test1")
        val doc2 = mEnv.model.createFile("test2")
        val folder1 = mEnv.model.createFolder("folder1")
        mEnv.mockProviders
            .get(TestProvidersAccess.HOME.authority)!!
            .setNextTrashDocumentsReturns(doc1, doc2, folder1)

        val loader = createTrashFileLoader()

        val result = loader.loadInBackground()!!
        Assert.assertEquals(3, result.cursor.getCount())
    }

    /**
     * Verifies that the loader correctly returns hidden files (those with a '.' prefix) from the
     * trash, regardless of the 'show hidden files' setting.
     */
    @Test
    fun testShowHiddenFilesInTrash() {
        val doc1 = mEnv.model.createFile(".test")
        val doc2 = mEnv.model.createFile("test")
        doc1.documentId = ".test"
        doc2.documentId = "parent_folder/.hidden_folder/test"
        doc1.lastModified = System.currentTimeMillis()
        doc2.lastModified = System.currentTimeMillis()
        mEnv.mockProviders
            .get(TestProvidersAccess.HOME.authority)!!
            .setNextTrashDocumentsReturns(doc1, doc2)

        val loader = createTrashFileLoader()

        Assert.assertTrue(loader.mState.shouldShowHiddenFiles())
        var result = loader.loadInBackground()!!
        Assert.assertEquals(2, result.cursor.getCount())

        loader.mState.setIsShowHiddenFiles(true)
        result = loader.loadInBackground()!!
        Assert.assertEquals(2, result.cursor.getCount())
    }

    /**
     * Asserts that documents in the trash do not support 'move' or 'remove' operations, only
     * 'delete', by checking the document flags returned by the loader.
     */
    @Test
    fun testTrashDocumentsNotRenameAndMove() {
        val doc =
            mEnv.model.createFile(
                "freddy.jpg",
                (DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
                    DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                    DocumentsContract.Document.FLAG_SUPPORTS_REMOVE),
            )
        mEnv.mockProviders
            .get(TestProvidersAccess.HOME.authority)!!
            .setNextTrashDocumentsReturns(doc)

        val loader = createTrashFileLoader()

        val result = loader.loadInBackground()!!

        val cursor = result.cursor
        Assert.assertEquals(1, cursor.getCount())
        // Iterate through the cursor to verify that the returned document has the correct flags.
        // The document should support deletion but not moving or removing.
        for (i in 0..<cursor.getCount()) {
            cursor.moveToNext()
            val flags =
                cursor.getInt(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
            Assert.assertTrue((flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0)
            Assert.assertEquals(0, (flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE))
            Assert.assertEquals(0, (flags and DocumentsContract.Document.FLAG_SUPPORTS_REMOVE))
        }
    }

    /** Test case to verify that the loader returns an empty cursor when the trash is empty. */
    @Test
    fun testEmptyTrash() {
        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)!!.setNextTrashDocumentsReturns()

        val loader = createTrashFileLoader()
        val result = loader.loadInBackground()!!
        Assert.assertEquals(0, result.cursor.count)
    }

    /** Test case to verify that the loader correctly merges results from multiple providers. */
    @Test
    fun testMultipleProviders() {
        val doc1 = mEnv.model.createFile("test1")
        val doc2 = mEnv.model.createFile("test2")

        // Setup provider 1
        mEnv.mockProviders
            .get(TestProvidersAccess.HOME.authority)!!
            .setNextTrashDocumentsReturns(doc1)

        // Setup provider 2
        mEnv.mockProviders
            .get(TestProvidersAccess.DOWNLOADS.authority)!!
            .setNextTrashDocumentsReturns(doc2)

        val loader = createTrashFileLoader()

        val result = loader.loadInBackground()!!
        Assert.assertEquals(2, result.cursor.count)
    }

    /**
     * Test case to verify that the loader can gracefully handle a faulty provider without crashing
     * and still load results from other providers.
     */
    @Test
    fun testProviderError() {
        val doc1 = mEnv.model.createFile("test1")
        val doc2 = mEnv.model.createFile("test2")

        // Setup provider 1 to throw an error
        val homeProvider = mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)!!
        homeProvider.setNextTrashDocumentsReturns(doc1)
        homeProvider.setThrownRuntimeMessage("throwing exception")

        // Setup provider 2
        mEnv.mockProviders
            .get(TestProvidersAccess.DOWNLOADS.authority)!!
            .setNextTrashDocumentsReturns(doc2)

        val loader = createTrashFileLoader()

        val result = loader.loadInBackground()!!
        // Should load results from the second provider
        Assert.assertEquals(1, result.cursor.count)
    }

    /**
     * Test case to verify that the loader returns a CrossProfileNoPermissionException when there is
     * no permission to interact with the user profile.
     */
    @Test
    fun testLoaderOnUserWithoutPermission() {
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap[TestProvidersAccess.OtherUser.USER_ID] = false
        } else {
            mEnv.state.canShareAcrossProfile = false
        }
        val loader = createTrashFileLoader(mEnv, TestProvidersAccess.OtherUser.USER_ID)
        val result = loader.loadInBackground()!!

        Assert.assertNull(result.cursor)
        Assert.assertTrue(result.exception is CrossProfileNoPermissionException)
    }

    /**
     * Test case to verify that the loader returns a CrossProfileQuietModeException when the user
     * profile is in quiet mode.
     */
    @Test
    fun testCrossProfileQuietMode() {
        whenever(mActivity.userManager.isQuietModeEnabled(any())).thenReturn(true)

        val loader = createTrashFileLoader()
        val result = loader.loadInBackground()!!

        Assert.assertNull(result.cursor)
        Assert.assertTrue(result.exception is CrossProfileQuietModeException)
    }

    /**
     * Verifies that the loader correctly identifies which roots to ignore based on their support
     * for querying trashed documents.
     */
    @Test
    fun testShouldIgnoreRoot() {
        val loader = createTrashFileLoader()

        // Verify that the HOME root, which supports query trash, is NOT ignored.
        val homeRoots =
            mEnv.providers.getRootsForAuthorityBlocking(
                TestProvidersAccess.OtherUser.USER_ID,
                TestProvidersAccess.HOME.authority,
            )
        val homeRoot = homeRoots.first()
        Assert.assertFalse(loader.shouldIgnoreRoot(homeRoot))

        // Verify that the PICKLES root, which does NOT support query trash, IS ignored.
        val pickleRoots =
            mEnv.providers.getRootsForAuthorityBlocking(
                TestProvidersAccess.OtherUser.USER_ID,
                TestProvidersAccess.PICKLES.authority,
            )
        val pickleRoot = pickleRoots.first()
        Assert.assertTrue(loader.shouldIgnoreRoot(pickleRoot))
    }

    private fun createTrashFileLoader(
        env: TestEnv = mEnv,
        userId: UserId = TestProvidersAccess.USER_ID,
    ): TrashFileLoader {
        return TrashFileLoader(
            mActivity,
            env.providers,
            env.state,
            TestImmediateExecutor.createLookup(),
            TestFileTypeLookup(),
            userId,
        )
    }

    companion object {
        /**
         * Provides the test parameters for the parameterized test. This allows each test method to
         * be run with two configurations: one where private space is enabled, and one where it is
         * disabled.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "privateSpaceEnabled={0}")
        fun data(): Iterable<*> {
            return Lists.newArrayList<Boolean?>(true, false)
        }
    }
}
