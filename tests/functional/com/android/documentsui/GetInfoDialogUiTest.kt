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

import android.platform.test.annotations.EnableFlags
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_GET_INFO_DIALOG
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
@EnableFlags(FLAG_GET_INFO_DIALOG, FLAG_USE_MATERIAL3)
class GetInfoDialogUiTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    @get:Rule val testFilesRule = TestFilesRule()

    @Before
    fun setUpTest() {
        bots.roots.closeDrawer()
    }

    @Test
    fun testGetInfo_showsBasicFileMetadata() {
        val fileName = TestFilesRule.FILE_NAME_1
        val mimeType = "text/plain"

        // Select the document > 3-dot menu > Get info action.
        bots.directory.selectDocument(fileName, 1)
        bots.main.clickActionItem("Get info")

        // Ensure the information in the dialog that appears matches what we expect (along with the
        // labels that describe the information.
        // Name.
        onView(withText(fileName)).check(matches(isDisplayed()))

        // Type.
        onView(withText(context!!.getString(R.string.peek_metadata_type)))
            .check(matches(isDisplayed()))
        val fileTypeLookup = DocumentsApplication.getFileTypeLookup(context!!)
        val expectedTypeLabel =
            fileTypeLookup.lookup(mimeType)
                ?: context!!.getString(R.string.get_info_unknown_file_type)
        onView(withText(expectedTypeLabel)).check(matches(isDisplayed()))

        // Size.
        onView(withText("0 B")).check(matches(isDisplayed()))
    }

    @Test
    fun testGetInfo_showsCorrectExternalStorageProviderName() {
        val storageProvider = DocumentsProviderHelper.setupStorageAuthorityDocsHelper(context)
        val primaryRoot = storageProvider.getRoot("primary")

        // Open the root where the default test files are created.
        switchRoot(primaryRoot!!.title)

        // Open the 3-dot menu and click "Get info" option on the root.
        bots.main.openOverflowMenu()
        bots.menu.clickMenuItem("Get info")

        device!!.waitForIdle()

        // Ensure the information in the dialog that appears matches what we expect (along with the
        // labels that describe the information.
        // Name.
        onView(withText(primaryRoot.title)).check(matches(isDisplayed()))

        // Type.
        onView(withText(context!!.getString(R.string.peek_metadata_type)))
            .check(matches(isDisplayed()))
        onView(withText("Folder")).check(matches(isDisplayed()))
    }
}
