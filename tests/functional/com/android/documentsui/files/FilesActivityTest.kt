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

package com.android.documentsui.files

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.R
import com.android.documentsui.StubProvider
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.util.Material3Config.Companion.getRes
import org.junit.Rule
import org.junit.Test

@LargeTest
@EnableFlags(FLAG_USE_MATERIAL3)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class FilesActivityTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val overrideFlagsRule = OverrideFlagsRule()
    @get:Rule
    val testFilesRule =
        TestFilesRule().createTestFiles { docsHelper ->
            var root = docsHelper.getRoot(StubProvider.ROOT_0_ID)
            docsHelper.createDocument(root, "text/plain", "rename_me.txt")
        }

    @Test
    fun testShortcutForRename() {
        // Select a test file.
        bots.directory.selectDocument("rename_me.txt", 1)

        // Press Ctrl+Enter.
        device!!.pressKeyCode(KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON)

        // Verify that the rename dialog is shown.
        device!!.waitForIdle()
        val renameTitle = context!!.getString(getRes(R.string.menu_rename))
        onView(withText(renameTitle)).inRoot(isDialog()).check(matches(isDisplayed()))
    }
}
