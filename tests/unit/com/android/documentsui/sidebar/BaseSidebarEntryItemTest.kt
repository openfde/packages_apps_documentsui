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

package com.android.documentsui.sidebar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.ActionHandler
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.ShortcutInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestActionHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BaseSidebarEntryItemTest {
    @get:Rule val setFlags = OverrideFlagsRule()
    private var actionHandler: ActionHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        actionHandler = TestActionHandler()
    }

    @Test
    fun testEqualsShortcutItem() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", false)
        val shortcutItem2 = ShortcutItem(shortcutInfo, actionHandler, "", false)

        assertEquals(shortcutItem, shortcutItem2)
    }

    @Test
    fun testEqualsShortcutItemDiffMaybeShowBadge() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", false)
        val shortcutItem2 = ShortcutItem(shortcutInfo, actionHandler, "", true)

        assertEquals(shortcutItem, shortcutItem2)
    }

    @Test
    fun testNotEqualsShortcutItemDiffPackageName() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "name1", false)
        val shortcutItem2 = ShortcutItem(shortcutInfo, actionHandler, "name2", false)

        assertNotEquals(shortcutItem, shortcutItem2)
    }

    @Test
    fun testNotEqualsShortcutItemDiffActionHandler() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, null, "", false)
        val shortcutItem2 = ShortcutItem(shortcutInfo, actionHandler, "", false)

        assertNotEquals(shortcutItem, shortcutItem2)
    }

    @Test
    fun testNotEqualsShortcutItemDiffShortcutInfo() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutInfo2 =
            ShortcutInfo(rootInfo, "diff parent document id", "diff title", "diff title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", true)
        val shortcutItem2 = ShortcutItem(shortcutInfo2, actionHandler, "", true)

        assertNotEquals(shortcutItem, shortcutItem2)
    }

    @Test
    fun testEqualsShortcutItemAndRootItemGetRoot() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", false)
        val rootItem = RootItem(rootInfo, actionHandler, false)

        assertEquals(shortcutItem.itemInfo.root, rootItem.itemInfo.root)
    }

    @Test
    fun testEqualsNavRailShortcutItem() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val item1 = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)
        val item2 = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)

        assertEquals(item1, item2)
    }

    @Test
    fun testEqualsNavRailShortcutItemDiffMaybeShowBadge() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val item1 = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)
        val item2 = NavRailShortcutItem(shortcutInfo, actionHandler, "", true)

        assertEquals(item1, item2)
    }

    @Test
    fun testNotEqualsNavRailShortcutItemDiffPackageName() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val item1 = NavRailShortcutItem(shortcutInfo, actionHandler, "name1", false)
        val item2 = NavRailShortcutItem(shortcutInfo, actionHandler, "name2", false)

        assertNotEquals(item1, item2)
    }

    @Test
    fun testNotEqualsNavRailShortcutItemDiffActionHandler() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val item1 = NavRailShortcutItem(shortcutInfo, null, "", false)
        val item2 = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)

        assertNotEquals(item1, item2)
    }

    @Test
    fun testNotEqualsNavRailShortcutItemDiffShortcutInfo() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val shortcutInfo2 =
            ShortcutInfo(rootInfo, "diff parent document id", "diff title", "diff title", 0)

        val item1 = NavRailShortcutItem(shortcutInfo, actionHandler, "", true)
        val item2 = NavRailShortcutItem(shortcutInfo2, actionHandler, "", true)

        assertNotEquals(item1, item2)
    }

    @Test
    fun testEqualsNavRailShortcutItemAndRootItemGetRoot() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val navRailShortcutItem = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)
        val rootItem = RootItem(rootInfo, actionHandler, false)

        assertEquals(navRailShortcutItem.itemInfo.root, rootItem.itemInfo.root)
    }

    @Test
    fun testEqualsNavRailShortcutItemAndShortcutItem() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "title", 0)

        val navRailShortcutItem = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)
        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", false)

        assertEquals(navRailShortcutItem, shortcutItem)
    }

    @Test
    fun testShortcutItemWithLocalizedTitle() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "aaaa", 0)

        val shortcutItem = ShortcutItem(shortcutInfo, actionHandler, "", false)

        // Use the localized title instead of the folder title.
        assertEquals("aaaa", shortcutItem.title)
    }

    @Test
    fun testNavRailShortcutItemWithLocalizedTitle() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(rootInfo, "parentDocumentId", "title", "aaaa", 0)

        val navRailShortcutItem = NavRailShortcutItem(shortcutInfo, actionHandler, "", false)

        // Use the localized title instead of the folder title.
        assertEquals("aaaa", navRailShortcutItem.title)
    }
}
