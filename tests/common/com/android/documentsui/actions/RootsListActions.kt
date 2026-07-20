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

package com.android.documentsui.actions

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import com.android.documentsui.R
import com.android.documentsui.sidebar.BaseSidebarEntryItem
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.utils.inFixedLayout
import org.hamcrest.Description
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher

/**
 * Show roots list. On small/medium screens this opens the drawer, on large screens it's a no-op.
 */
fun showRootsList(context: Context, @LayoutRes layoutId: Int?) {
    if (inFixedLayout(context, layoutId)) {
        return
    }
    Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
}

/** Wait for the roots list drawer to close, if the layout has a drawer. */
fun waitForRootsListDrawerToClose(context: Context, @LayoutRes layoutId: Int?) {
    if (inFixedLayout(context, layoutId)) {
        return
    }
    Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.waitForClose())
}

/** Perform the specified action on the item with the specified label in the roots list. */
fun actionOnRootItem(label: String, action: ViewAction) {
    // The material3 layout for tablets has two views with roots_list ID (one for the drawer
    // layout, another for the nav rail. We have to specify the ancestor to disambiguate which
    // roots_list we want.
    if (isUseMaterial3FlagEnabled()) {
        val rootsListMatcher =
            Matchers.allOf(
                ViewMatchers.withId(R.id.roots_list),
                ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.container_roots)),
                ViewMatchers.isCompletelyDisplayed(),
            )
        val itemMatcher = ViewMatchers.hasDescendant(ViewMatchers.withText(label))

        Espresso.onView(rootsListMatcher)
            .perform(RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(itemMatcher, action))
    } else {
        Espresso.onData(ListViewItemMatcher(label))
            .inAdapterView(ViewMatchers.withId(R.id.roots_list))
            .perform(action)
    }
}

/** Matcher used for finding a BaseSidebarEntryItem in the roots list. */
internal class ListViewItemMatcher internal constructor(private val mLabel: String?) :
    TypeSafeMatcher<Any?>() {
    override fun matchesSafely(item: Any?): Boolean {
        if (item !is BaseSidebarEntryItem) {
            return false
        }
        val sidebarEntryItem = item
        return sidebarEntryItem.title == mLabel
    }

    override fun describeTo(description: Description) {
        description.appendText("with root item title: $mLabel")
    }
}
