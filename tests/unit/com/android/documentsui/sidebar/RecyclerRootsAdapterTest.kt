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

import android.content.Context
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent
import android.view.View
import android.view.View.OnDragListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.ActionHandler
import com.android.documentsui.ActivityConfig
import com.android.documentsui.BaseActivity
import com.android.documentsui.Injector
import com.android.documentsui.R
import com.android.documentsui.base.Features
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.State
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.sidebar.RecyclerRootsAdapter.Companion.TYPE_HEADER
import com.android.documentsui.sidebar.RecyclerRootsAdapter.Companion.TYPE_NAV_RAIL_ROOT
import com.android.documentsui.sidebar.RecyclerRootsAdapter.Companion.TYPE_ROOT
import com.android.documentsui.sidebar.RecyclerRootsAdapter.Companion.TYPE_SPACER
import com.android.documentsui.testing.TestProvidersAccess
import com.android.documentsui.testing.TestResolveInfo
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Expect
import kotlin.jvm.java
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SmallTest
class RecyclerRootsAdapterTest {

    @Mock private lateinit var activity: BaseActivity
    @Mock private lateinit var dragListener: OnDragListener

    private lateinit var parent: RecyclerView
    private lateinit var adapter: RecyclerRootsAdapter
    private lateinit var items: MutableList<Item>

    @get:Rule val expect = Expect.create()
    @get:Rule val setFlags = OverrideFlagsRule()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)
        parent = RecyclerView(context)
        parent.layoutManager = LinearLayoutManager(context)
        items = mutableListOf()
        adapter = RecyclerRootsAdapter(activity, items, dragListener)
    }

    @Test
    fun testOnBindViewHolderRootItem() {
        val mockItem = mock(RootItem::class.java)
        items.add(mockItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        verify(mockItem).bindView(viewHolder.itemView)
        expect.that(viewHolder.itemView.getTag(R.id.item_position_tag)).isEqualTo(0)
    }

    // TODO: b/447240163 - Figure out how to use a mock item rather than a spy item for
    //  ShortcutItem tests.
    @Test
    fun testOnBindViewHolderShortcutItem() {
        val shortcutInfo = TestProvidersAccess.HOME_SCREEN_SHORTCUT
        val mockAction = mock(ActionHandler::class.java)
        val shortcutItem = ShortcutItem(shortcutInfo, mockAction, "", false)
        val spyItem = spy(shortcutItem)
        items.add(spyItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        verify(spyItem).bindView(viewHolder.itemView)
        expect.that(viewHolder.itemView.getTag(R.id.item_position_tag)).isEqualTo(0)
    }

    @Test
    fun testOnBindViewHolderNavRailShortcutItem() {
        val shortcutInfo = TestProvidersAccess.HOME_SCREEN_SHORTCUT
        val mockAction = mock(ActionHandler::class.java)
        val item = NavRailShortcutItem(shortcutInfo, mockAction, "", false)
        val spyItem = spy(item)
        items.add(spyItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        verify(spyItem).bindView(viewHolder.itemView)
        expect.that(viewHolder.itemView.getTag(R.id.item_position_tag)).isEqualTo(0)
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3)
    fun testOnBindViewHolderRootAndAppItemHidesActionIcon() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val resolveInfo = TestResolveInfo.create()
        resolveInfo.activityInfo.packageName = "com.documentsui.test"
        val rootAndAppItem = RootAndAppItem(rootInfo, resolveInfo, null, false)
        items.add(rootAndAppItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        val actionIcon = viewHolder.itemView.findViewById<MaterialButton?>(getRes(R.id.action_icon))
        expect.that(actionIcon?.visibility).isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    @DisableFlags(Flags.FLAG_HOME_SCREEN_FILES_RO)
    fun testOnBindViewHolderRootAndAppItemHasActionIcon() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val resolveInfo = TestResolveInfo.create()
        resolveInfo.activityInfo.packageName = "com.documentsui.test"
        val rootAndAppItem = RootAndAppItem(rootInfo, resolveInfo, null, false)
        items.add(rootAndAppItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        val actionIcon = viewHolder.itemView.findViewById<MaterialButton?>(getRes(R.id.action_icon))
        expect.that(actionIcon?.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun testOnBindViewHolder_withSpaceItem() {
        val mockItem = SpacerItem()
        items.add(mockItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 2)

        adapter.onBindViewHolder(viewHolder, 0)

        expect.that(viewHolder.itemView.isEnabled).isFalse()
    }

    @Test
    fun testOnBindViewHolder_withSelectedItem() {
        val mockItem = mock(RootItem::class.java)
        whenever(mockItem.isSelected).thenReturn(true)
        items.add(mockItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        expect.that(viewHolder.itemView.isActivated).isTrue()
        expect.that(viewHolder.itemView.isSelected).isTrue()
    }

    @Test
    fun testOnBindViewHolder_withUnselectedItem() {
        val mockItem = mock(RootItem::class.java)
        whenever(mockItem.isSelected).thenReturn(false)
        items.add(mockItem)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(viewHolder, 0)

        expect.that(viewHolder.itemView.isActivated).isFalse()
        expect.that(viewHolder.itemView.isSelected).isFalse()
    }

    @Test
    fun testOnBindViewHolder_onClick() {
        // simply mock doesn't work, why?
        val mockItem =
            spy(RootItem(mock(RootInfo::class.java), mock(ActionHandler::class.java), false))

        adapter.onClick(mockItem)

        verify(mockItem).open()
    }

    @Test
    fun testOnBindViewHolder_onLongClick() {
        val mockItem = mock(RootItem::class.java)
        whenever(activity.displayState)
            .thenReturn(State().apply { action = State.ACTION_GET_CONTENT })

        adapter.onLongClick(mockItem)

        verify(mockItem).showAppDetails()
    }

    @Test
    fun testOnBindViewHolder_onTabKey() {
        val features = mock(Features::class.java)
        whenever(features.isSystemKeyboardNavigationEnabled()).thenReturn(true)
        val injector =
            Injector<ActionHandler>(
                features,
                mock(ActivityConfig::class.java),
                null,
                null,
                null,
                null,
                null,
            )
        whenever(activity.getInjector()).thenReturn(injector)
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)

        expect.that(adapter.onKey(KeyEvent.KEYCODE_TAB, event)).isFalse()
    }

    @Test
    fun testOnBindViewHolder_onArrowKey() {
        val leftArrowEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        expect.that(adapter.onKey(KeyEvent.KEYCODE_DPAD_LEFT, leftArrowEvent)).isTrue()

        val rightArrowEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        expect.that(adapter.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, rightArrowEvent)).isTrue()
    }

    @Test
    fun testGetItemViewType_withRootItem() {
        items.add(mock(RootItem::class.java))
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_ROOT)
    }

    @Test
    fun testGetItemViewType_withShortcutItem() {
        items.add(mock(ShortcutItem::class.java))
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_ROOT)
    }

    @Test
    fun testGetItemViewType_withNavRailRootItem() {
        items.add(mock(NavRailRootItem::class.java))
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_NAV_RAIL_ROOT)
    }

    @Test
    fun testGetItemViewType_withNavRailShortcutItem() {
        items.add(mock(NavRailShortcutItem::class.java))
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_NAV_RAIL_ROOT)
    }

    @Test
    fun testGetItemViewType_withSpacerItem() {
        items.add(SpacerItem())
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_SPACER)
    }

    @Test
    fun testGetItemViewType_withHeaderItem() {
        items.add(HeaderItem("Test"))
        expect.that(adapter.getItemViewType(0)).isEqualTo(TYPE_HEADER)
    }

    @Test
    fun testSetItemSelected_updatesSelectedItemAndNotifies() {
        val mockItem1 = mock(RootItem::class.java)
        val mockItem2 = mock(RootItem::class.java)
        val observer: AdapterDataObserver = mock(AdapterDataObserver::class.java)
        adapter.registerAdapterDataObserver(observer)
        items.add(mockItem1)
        items.add(mockItem2)

        // Select item 0.
        adapter.setItemSelected(0, true)
        verify(mockItem1).isSelected = true
        verify(observer).onItemRangeChanged(0, 1, null)

        // Select item 1, item 0 should be unselected.
        adapter.setItemSelected(1, true)

        verify(mockItem1).isSelected = false
        verify(mockItem2).isSelected = true
        // Verify both item 1 and item 2 have changed.
        verify(observer, times(2)).onItemRangeChanged(0, 1, null)
        verify(observer).onItemRangeChanged(1, 1, null)
    }

    @Test
    fun testGetItem() {
        val mockItem1 = mock(RootItem::class.java)
        val mockItem2 = mock(RootItem::class.java)
        items.add(mockItem1)
        items.add(mockItem2)

        expect.that(adapter.itemCount).isEqualTo(2)
        expect.that(adapter.getItem(0)).isEqualTo(mockItem1)
        expect.that(adapter.getItem(1)).isEqualTo(mockItem2)
    }
}
