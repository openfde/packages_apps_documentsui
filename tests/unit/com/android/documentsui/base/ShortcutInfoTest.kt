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

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.Parcelables
import java.util.Objects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ShortcutInfoTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    @Test
    fun testEqualsSameDocsProviderRootSameUser() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutInfo2 = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        assertEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsDiffLocalizedTitle() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "aaaa", 0)

        val shortcutInfo2 = ShortcutInfo(rootInfo, "parentDocumentId", "title", "bbbb", 0)

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsSameDocsProviderRootDiffUser() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }

        val copyRootInfo = RootInfo.copyRootInfo(rootInfo)
        copyRootInfo.userId = UserId.of(200)
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutInfo2 = ShortcutInfo(copyRootInfo, "parentDocumentId", "title", "title", 0)

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsDiffDocsProviderRoot() {
        val rootInfo1 =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority1"
                rootId = "root1"
            }
        val shortcutInfo = ShortcutInfo(rootInfo1, "parentDocumentId", "title", "title", 0)

        val rootInfo2 =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root2"
            }
        val shortcutInfo2 = ShortcutInfo(rootInfo2, "parentDocumentId", "title", "title", 0)

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsDiffUri() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)
        val shortcutInfo2 = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)
        // Uri is determined by the document id
        shortcutInfo.documentId = "documentId"
        shortcutInfo2.documentId = "documentId2"

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testEqualsGetUri() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)
        val expected = DocumentsContract.buildDocumentUri(rootInfo.authority, "some doc id")

        // Uri is determined by the document id
        shortcutInfo.documentId = "some doc id"

        assertEquals(expected, shortcutInfo.uri)
    }

    @Test
    fun testToStringShortcut() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "aaa"
                rootId = "rrr"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "ppp", "ttt", "ttt", 0)
        shortcutInfo.documentId = "ddd"

        val expected =
            "ShortcutInfo{title=ttt, folderTitle=ttt, documentId=ddd" +
                ", root=Root{userId=" +
                UserId.of(100) +
                ", authority=aaa, rootId=rrr, title=null, isUsb=false, isSd=false, isMtp=false" +
                "} @ " +
                DocumentsContract.buildRootUri("aaa", "rrr") +
                "} @ " +
                DocumentsContract.buildDocumentUri("aaa", "ddd")

        assertEquals(expected, shortcutInfo.toString())
    }

    @Test
    fun testDerivedTypeHomeScreen() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "com.android.externalstorage.documents"
                rootId = "primary"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "primary:", "Home screen", "Home screen", 0)
        assertEquals(SidebarEntryItemInfo.TYPE_HOME_SCREEN, shortcutInfo.derivedType)
    }

    @Test
    fun testDerivedTypeDownloads() {
        // Create a root info for the external storage provider.
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "com.android.externalstorage.documents"
                rootId = "primary"
            }
        // Create a shortcut info that points to the "Download" folder.
        val shortcutInfo = ShortcutInfo(rootInfo, "primary:", "Download", "Download", 0)

        // Assert that the derived type is TYPE_DOWNLOADS.
        assertEquals(SidebarEntryItemInfo.TYPE_DOWNLOADS, shortcutInfo.derivedType)
    }

    @Test
    fun testDerivedTypeShortcutOther() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "aaa"
                rootId = "rrr"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "ppp", "ttt", "ttt", 0)
        assertEquals(SidebarEntryItemInfo.TYPE_SHORTCUT_OTHER, shortcutInfo.derivedType)
    }

    @Test
    fun testParceling() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "aaa"
                rootId = "rrr"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "ppp", "ttt", "ttt", 0)

        Parcelables.assertParcelable(
            shortcutInfo,
            0,
            { left: ShortcutInfo?, right: ShortcutInfo? ->
                (Objects.equals(left, right) && Objects.equals(left?.icon, right?.icon))
            },
        )
    }
}
