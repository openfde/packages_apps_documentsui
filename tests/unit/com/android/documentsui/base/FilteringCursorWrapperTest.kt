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

package com.android.documentsui.base

import android.database.MatrixCursor
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.flags.Flags.FLAG_DESKTOP_UX_PHASE_2_RO
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class FilteringCursorWrapperTest {
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    private lateinit var wrapper: FilteringCursorWrapper
    private lateinit var cursor: MatrixCursor

    @Before
    fun setUp() {
        cursor = MatrixCursor(arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID))
        cursor.addRow(arrayOf("file1.txt", "primary:folder1/file1.txt"))
        cursor.addRow(arrayOf("Android", "primary:Android"))
        cursor.addRow(arrayOf("Android1", "primary:Android1"))
        cursor.addRow(arrayOf("file2.txt", "primary:Android/file2.txt"))
        cursor.addRow(arrayOf("file3.txt", "primary:Alarms/file3.txt"))
        cursor.addRow(arrayOf(".hidden_file", "primary:folder2/.hidden_file"))
        cursor.addRow(arrayOf("another_file", "primary:folder2/another_file"))
        cursor.addRow(arrayOf(null, "primary:folder2/.dotfile_without_name"))
        cursor.addRow(arrayOf(null, "primary:folder2/file4.txt"))
        cursor.addRow(arrayOf(".dotfile_without_id", null))
        cursor.addRow(arrayOf("file5.txt", null))
        cursor.addRow(arrayOf("file1_inside_dot_folder", "primary:.folder/file_inside_dot_folder"))
        cursor.addRow(
            arrayOf("file2_inside_dot_folder", "primary:folder2/.dot_folder/file_inside_dot_folder")
        )
    }

    @Test
    @Suppress("ktlint:standard:comment-wrapping")
    fun testFilterHiddenFiles_showHiddenFiles() {
        wrapper = FilteringCursorWrapper(cursor)
        wrapper.filterHiddenFiles(/* showHiddenFiles= */ true)

        assertEquals(13, wrapper.count)
    }

    @Test
    @DisableFlags(FLAG_DESKTOP_UX_PHASE_2_RO)
    @Suppress("ktlint:standard:comment-wrapping")
    fun testFilterHiddenFiles_filterDotFiles() {
        wrapper = FilteringCursorWrapper(cursor)
        wrapper.filterHiddenFiles(/* showHiddenFiles= */ false)

        assertEquals(9, wrapper.count)

        wrapper.moveToFirst()
        assertEquals("file1.txt", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("Android", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("Android1", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("file2.txt", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("file3.txt", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("another_file", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("primary:folder2/file4.txt", wrapper.getString(1))
        wrapper.moveToNext()
        assertEquals("file5.txt", wrapper.getString(0))
        // When Desktop UX phase 2 is off, it doesn't filter the files under root dot folder.
        wrapper.moveToNext()
        assertEquals("file1_inside_dot_folder", wrapper.getString(0))
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_DESKTOP_UX_PHASE_2_RO)
    @Suppress("ktlint:standard:comment-wrapping")
    fun testFilterHiddenFiles_filterNonDesktopFolders() {
        wrapper = FilteringCursorWrapper(cursor)
        wrapper.filterHiddenFiles(/* showHiddenFiles= */ false)

        assertEquals(5, wrapper.count)

        wrapper.moveToFirst()
        assertEquals("file1.txt", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("Android1", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("another_file", wrapper.getString(0))
        wrapper.moveToNext()
        assertEquals("primary:folder2/file4.txt", wrapper.getString(1))
        wrapper.moveToNext()
        assertEquals("file5.txt", wrapper.getString(0))
    }
}
