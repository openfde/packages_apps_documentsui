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

package com.android.documentsui.dirlist

import android.net.Uri
import android.platform.test.annotations.EnableFlags
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.BaseActivity
import com.android.documentsui.DocumentsProviderHelper
import com.android.documentsui.R
import com.android.documentsui.TestSummaryProvider
import com.android.documentsui.base.Providers.ROOT_ID_DEVICE
import com.android.documentsui.base.UserId
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_FILE_SUMMARY
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
@EnableFlags(FLAG_USE_FILE_SUMMARY, FLAG_USE_MATERIAL3)
class FilesSummaryUiTest : ActivityTestJunit4<FilesActivity>() {
    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

    private lateinit var summaryHelper: DocumentsProviderHelper
    private val fileName = "recent_file.txt"
    private val summary = "This is a summary for $fileName"
    private val consentTitle = "RANDOM TITLE"
    private val consentMessage = "RANDOM MESSAGE"
    private var file: Uri? = null
    private var primaryRootTitle: String = ""
    private lateinit var localStorageHelper: DocumentsProviderHelper
    private var summaryProviderManager: SummaryProviderManager? = null

    @Before
    fun prepareSummaryProvider() {
        val summaryProviderUri = "content://${TestSummaryProvider.AUTHORITY}/root/summary-root"

        // Initialize helper for the summary provider.
        summaryHelper =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                TestSummaryProvider.AUTHORITY,
                context,
                TestSummaryProvider.AUTHORITY,
            )

        // Helper to interact with the local storage.
        localStorageHelper = DocumentsProviderHelper.setupStorageAuthorityDocsHelper(context)

        val primaryRoot = localStorageHelper.getRoot(ROOT_ID_DEVICE)
        primaryRootTitle = primaryRoot.title
        // Find the Download folder.
        val download = localStorageHelper.findFile(primaryRoot.documentId, "Download")
        assertNotNull(download)

        // Prepare a file in the Download folder.
        file = localStorageHelper.createDocument(download?.documentId, "text/plain", fileName)
        val fileInfo = localStorageHelper.findDocument(download?.documentId, fileName)
        assertNotNull(fileInfo)
        localStorageHelper.writeDocument(file, "ham and cheese".toByteArray())

        // Prepare the summary for the file.
        summaryHelper.setSummaryProviderIsEmpty(false) // Enable the provider.
        val summaries = mutableMapOf<String, String?>()
        summaries[fileInfo.documentId] = summary
        summaryHelper.setProviderSummaries(summaries)

        mActivityScenario!!.onActivity { activity ->
            val baseActivity = (activity as BaseActivity)
            baseActivity.setLocalSummaryProvider(Uri.parse(summaryProviderUri))
            baseActivity.injector.summaryProviderManager?.setConsentMessage(
                consentTitle,
                consentMessage,
                showConsent = true,
            )
            summaryProviderManager = baseActivity.injector.summaryProviderManager
        }
    }

    @After
    fun cleanupProviderAndFile() {
        file?.let { localStorageHelper.deleteDocument(it) }
        summaryHelper.clearDocumentSummaries()
        localStorageHelper.cleanUp()
        summaryHelper.cleanUp()
    }

    @Test
    fun testSummaryInGridAndListModes() {
        // Navigate to the Download folder in the primary root.
        switchRoot(primaryRootTitle)
        bots.directory.openDocument("Download")

        // Enable the summary column.
        bots.main.clickToolbarOverflowItem(context!!.getString(R.string.option_show_summary_column))
        device!!.waitForIdle()
        bots.main.assertDialogTitle(consentTitle)
        bots.main.assertDialogMessage(consentMessage)
        bots.main.clickDialogOkButton(false)

        // Grid view doesn't display the summary, so we don't check it.

        // List view.
        bots.main.switchToListMode()
        device!!.waitForIdle()
        bots.directory.assertDocumentSummary(fileName, summary)

        // Also verify that files list reacts to SummaryProviderManager.isEnabled changing without
        // any user action (e.g. external provider disables itself or SummaryProviderManager
        // fetching
        // external provider's state takes longer than the first render of files list).
        summaryProviderManager!!.userSwitchSummaryDisabled()
        runBlocking { summaryProviderManager!!.isEnabledFlow.first { it == false } }
        onView(withText(fileName)).check(matches(isDisplayed()))
        // Make sure summary isn't displayed. Sometimes it's entirely missing from the hierarchy but
        // sometimes it's just invisible because the recycled view holder already had the summary
        // text filled in.
        onView(allOf(withText(containsString(summary)), isDisplayed())).check(doesNotExist())

        // File List automatically refreshes to display summary.
        summaryProviderManager!!.userSwitchSummaryEnabled()
        runBlocking { summaryProviderManager!!.isEnabledFlow.first { it == true } }
        bots.directory.assertDocumentSummary(fileName, summary)
    }
}
