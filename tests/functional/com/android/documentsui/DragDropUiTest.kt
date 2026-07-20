/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.filters.LargeTest
import com.android.documentsui.StubProvider.ROOT_1_ID
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
class DragDropUiTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val overrideFlagsRule = OverrideFlagsRule()
    @get:Rule val testFilesRule = TestFilesRule()

    @Before
    fun setUpTest() {
        mActivityScenario!!.onActivity { activity ->
            // Set a long spring timeout to prevent the drag/drop from accidentially opening the
            // drop target root or directory, to avoid the test flakiness.
            activity!!.setDragSpringTimeoutForTest(60 * 1000)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    fun testDragAndDropToDifferentRoot_fileIsCopied() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot(ROOT_1_ID)

        bots.gesture.dragAndDrop(src.bounds, dst.bounds)

        // Go to the new root and assert the file was copied.
        switchRoot(ROOT_1_ID)
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1)
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    fun testDragAndDropToDifferentRoot_fileIsCopiedAndSelected() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot(ROOT_1_ID)

        bots.gesture.dragAndDrop(src.bounds, dst.bounds)

        // The drop will trigger a copy because it's to a different root so the original file still
        // exists and selected.
        bots.directory.assertSelection(1)

        // Go to the new root and assert the file was copied.
        switchRoot(ROOT_1_ID)
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1)
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    fun testDragAndDropToBrokenRoot_failToDrop() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot("Broken Root Doc")

        bots.gesture.dragAndDrop(src.bounds, dst.bounds)

        // Go to the new root and assert the file was not copied.
        switchRoot("Broken Root Doc")
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1)
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    fun testDragAndDropToBrokenRoot_failToDropButSelectionRemains() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot("Broken Root Doc")

        bots.gesture.dragAndDrop(src.bounds, dst.bounds)

        // The drop will fail because the destination root is broken but the selection remains when
        // use_material3 flag is on.
        bots.directory.assertSelection(1)

        // Go to the new root and assert the file was not copied.
        switchRoot("Broken Root Doc")
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1)
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    fun testDragAndDropToSameRoot_unselectedFileIsMoved() {
        // Select FILE_NAME_2 but we will drag FILE_NAME_1.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 1)

        // Trigger dragging directly without selecting the file. Note: for unselected file, using
        // the selection hotspot (e.g. the thumbnail) to trigger the drag is more stable.
        val src = bots.directory.findSelectionHotspot(TestFilesRule.FILE_NAME_1)
        val dst = bots.directory.findDocument(TestFilesRule.DIR_NAME_1)

        bots.gesture.dragAndDrop(src.visibleBounds, dst.bounds)

        // The file should be moved to the destination directory, so it doesn't exist in the
        // original directory.
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1)

        // Go to the destination directory and assert the file is there.
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1)
    }
}
