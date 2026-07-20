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

import android.content.pm.ResolveInfo
import android.os.Build
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.android.documentsui.ModelId
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestModelRule
import com.android.documentsui.testing.TestPackageManager
import com.android.documentsui.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val TestAuthority = "com.example.test"
const val TestUserId = 0

@SmallTest
@RunWith(AndroidJUnit4::class)
class SelectionMetadataTest {
    companion object {
        const val IS_UNAVAILABLE_FLAG = 13
    }

    val testPackageManager: TestPackageManager = TestPackageManager.create()

    @get:Rule(order = 0) val setFlags = OverrideFlagsRule()
    @get:Rule(order = 1) val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 2) val testModelRule = TestModelRule(TestAuthority, TestUserId)

    @Before
    fun setUp() {
        testModelRule.createFile("noOpeningApp.pdf", "application/pdf")
        testModelRule.createFile("oneOpeningApp.txt", "text/plain")
        testModelRule.createFile("twoOpeningApp.jpg", "image/jpg")
        testModelRule.createFile("twoOpeningApp.png", "image/png")
        testModelRule.createFile("duplicateMimeType.png", "image/png")
        testModelRule.createFile("unavailableDocument1.png", "image/png", 0, IS_UNAVAILABLE_FLAG)
        testModelRule.createFile("unavailableDocument2.png", "image/png", 0, IS_UNAVAILABLE_FLAG)

        testPackageManager.queryIntentActivitiesResults.put("application/pdf", emptyList())
        testPackageManager.queryIntentActivitiesResults.put("text/plain", listOf(ResolveInfo()))
        testPackageManager.queryIntentActivitiesResults.put(
            "image/jpg",
            listOf(ResolveInfo(), ResolveInfo()),
        )
        testPackageManager.queryIntentActivitiesResults.put(
            "image/png",
            listOf(ResolveInfo(), ResolveInfo()),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
    @DisableFlags(Flags.FLAG_USE_NEW_OPEN_WITH)
    fun testHasMultipleOpeningApps_NoSelection() {
        val sm = createSelectionMetadata()

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
    @DisableFlags(Flags.FLAG_USE_NEW_OPEN_WITH)
    fun testHasMultipleOpeningApps_OneSelection_NoOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged("noOpeningApp.pdf", true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
    @DisableFlags(Flags.FLAG_USE_NEW_OPEN_WITH)
    fun testHasMultipleOpeningApps_OneSelection_OneOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
    @DisableFlags(Flags.FLAG_USE_NEW_OPEN_WITH)
    fun testHasMultipleOpeningApps_OneSelection_TwoOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("noOpeningApp.pdf"), true)
        sm.onItemStateChanged(makeId("noOpeningApp.pdf"), false)

        assertEquals(sm.hasMultipleOpeningApps(), true)
    }

    @Test
    @EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
    @DisableFlags(Flags.FLAG_USE_NEW_OPEN_WITH)
    fun testHasMultipleOpeningApps_TwoSelection() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_noSelection() {
        val sm = createSelectionMetadata()
        assertEquals(true, sm.mimeTypes().isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_selectOneFile() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        assertEquals(setOf("text/plain"), sm.mimeTypes())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_selectMultipleFiles_differentMimeTypes() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        assertEquals(setOf("text/plain", "image/jpg"), sm.mimeTypes())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_selectMultipleFiles_sameMimeType() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)
        sm.onItemStateChanged(makeId("duplicateMimeType.png"), true)
        assertEquals(setOf("text/plain", "image/jpg", "image/png"), sm.mimeTypes())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_deselectFile_withRemainingOfSameMimeType() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)
        sm.onItemStateChanged(makeId("duplicateMimeType.png"), true)

        sm.onItemStateChanged(makeId("twoOpeningApp.png"), false)
        assertEquals(setOf("text/plain", "image/jpg", "image/png"), sm.mimeTypes())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_deselectLastFileOfMimeType() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), false)
        assertEquals(setOf("image/jpg", "image/png"), sm.mimeTypes())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_deselectAll() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), false)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), false)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), false)
        assertEquals(true, sm.mimeTypes().isEmpty())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    @DisableFlags(Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_flagDisabled() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)

        assertEquals(true, sm.mimeTypes().isEmpty())
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_MATERIAL3)
    @EnableFlags(Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER)
    fun testMimeTypes_notMaterial3() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)

        assertEquals(true, sm.mimeTypes().isEmpty())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
    fun testContainsDocumentsWithUnavailableContent_disabledDocument_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertTrue(sm.containsDocumentsWithUnavailableContent())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
    fun testContainsDocumentsWithUnavailableContent_disabledDocuments_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("unavailableDocument2.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertTrue(sm.containsDocumentsWithUnavailableContent())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_noDisabledDocuments_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertFalse(sm.containsDocumentsWithUnavailableContent())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_cloudFeaturesDisabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertFalse(sm.containsDocumentsWithUnavailableContent())
    }

    fun makeId(docId: String): String {
        return ModelId.build(UserId.of(TestUserId), TestAuthority, docId)!!
    }

    fun createSelectionMetadata(): SelectionMetadata {
        return SelectionMetadata(
            { modelId -> testModelRule.model.getItem(modelId) },
            { modelId ->
                val doc = testModelRule.model.getDocument(modelId)
                FileUtils.countOpeningApps(doc, testPackageManager)
            },
            { doc ->
                // Hack the `doc.syncStateFlags` to set whether the document is unavailable in this
                // test.
                doc.syncStateFlags != IS_UNAVAILABLE_FLAG
            },
        )
    }
}
