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

import android.app.ActivityOptions
import android.app.DownloadManager
import android.app.WindowConfiguration
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.LargeTest
import com.android.documentsui.base.Providers
import com.android.documentsui.base.Providers.ROOT_ID_DEVICE
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.bots.Bots
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO
import com.android.documentsui.flags.Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import org.junit.After
import org.junit.Rule
import org.junit.Test

@LargeTest
class DownloadsLaunchActivityTest : ActivityTestJunit4<FilesActivity>() {
    private var storageProvider: DocumentsProviderHelper? = null
    private var primaryRoot: RootInfo? = null

    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

    @get:Rule val testFilesRule: TestFilesRule = TestFilesRule()

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val DOWNLOADS_TITLE = "Download"

    @After
    @Throws(Exception::class)
    fun tearDownTest() {
        storageProvider = null
    }

    @Throws(Exception::class)
    private fun setupStorageAuthorityDocsHelper() {
        // Create DocumentsProviderHelper to create files in Internal storage.
        storageProvider =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                Providers.AUTHORITY_STORAGE,
                context,
                Providers.AUTHORITY_STORAGE,
            )
    }

    override fun setupTestingRoots() {
        setupStorageAuthorityDocsHelper()

        primaryRoot = storageProvider!!.getRoot(ROOT_ID_DEVICE)
    }

    override fun launchActivity() {
        // Don't launch the activity yet.
    }

    @Test
    @EnableFlags(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    @Throws(Exception::class)
    fun testLaunchViaActionViewDownloads() {
        // Set up and launch the activity.
        val intent = Intent(context, FilesActivity::class.java)
        intent.action = DownloadManager.ACTION_VIEW_DOWNLOADS
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (System.getProperty("documentsui_fullscreen") != null) {
            Log.d(TAG, "using launchWindowingMode=FULLSCREEN")

            val options = ActivityOptions.makeBasic()
            options.launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FULLSCREEN

            mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
        } else {
            mActivityScenario = ActivityScenario.launch(intent)
        }

        mActivityScenario?.onActivity({ activity ->
            activityLayoutId = (activity as? BaseActivity)?.layoutId
            bots = Bots(device, automation, context, TIMEOUT, activityLayoutId)
        })

        device!!.waitForIdle()

        // Test that sidebar, breadcrumb and window are correct.
        bots.roots.assertItemSelected(primaryRoot!!.title)
        bots.breadcrumb.assertItemsPresent(primaryRoot!!.title, DOWNLOADS_TITLE)
        bots.main.assertWindowTitle(DOWNLOADS_TITLE)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3)
    // Disable the flag below to force launch activity to Downloads shortcut instead of Recents
    @DisableFlags(FLAG_USE_ALLFILES_ROOT_FOR_RECENTS)
    @Throws(Exception::class)
    fun testLaunchViaFallbackDefaultRootUriTest() {
        // Set up and launch the activity.
        val intent = Intent(context, FilesActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (System.getProperty("documentsui_fullscreen") != null) {
            Log.d(TAG, "using launchWindowingMode=FULLSCREEN")

            val options = ActivityOptions.makeBasic()
            options.launchWindowingMode = WindowConfiguration.WINDOWING_MODE_FULLSCREEN

            mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
        } else {
            mActivityScenario = ActivityScenario.launch(intent)
        }

        mActivityScenario?.onActivity({ activity ->
            activityLayoutId = (activity as? BaseActivity)?.layoutId
            bots = Bots(device, automation, context, TIMEOUT, activityLayoutId)
        })

        device!!.waitForIdle()

        // Test that sidebar, breadcrumb and window are correct.
        bots.roots.assertItemSelected(primaryRoot!!.title)
        bots.breadcrumb.assertItemsPresent(primaryRoot!!.title, DOWNLOADS_TITLE)
        bots.main.assertWindowTitle(DOWNLOADS_TITLE)
    }
}
