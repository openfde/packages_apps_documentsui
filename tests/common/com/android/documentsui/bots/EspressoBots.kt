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

package com.android.documentsui.bots

import android.content.Context
import android.view.View
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.documentsui.R
import com.android.documentsui.actions.WaitUntilExistsInRecyclerView
import com.android.documentsui.actions.WaitUntilVisible
import com.android.documentsui.actions.actionOnRootItem
import com.android.documentsui.actions.rightClick
import com.android.documentsui.actions.showRootsList
import com.android.documentsui.actions.waitForRootsListDrawerToClose
import org.hamcrest.Matcher
import org.hamcrest.Matchers

/** Open the root with the given label. */
fun openRoot(context: Context, label: String, @LayoutRes layoutId: Int?) {
    showRootsList(context, layoutId)
    actionOnRootItem(label, click())
    waitForRootsListDrawerToClose(context, layoutId)
}

/**
 * Return a matcher for the document root container (item_root) with a TextView descendant with the
 * given label.
 */
fun documentMatcher(label: String?): Matcher<View> {
    return Matchers.allOf(withId(R.id.item_root), ViewMatchers.hasDescendant(withText(label)))
}

/** Wait for the document with the given label to exist. */
fun waitForDocument(label: String?, timeout: Long) {
    // Wait for the document to exist in the directory list.
    onView(withId(R.id.dir_list))
        .perform(WaitUntilExistsInRecyclerView(documentMatcher(label), timeout))
}

/**
 * Find the document with the given label, scrolling if necessary to ensure that the entire document
 * view is visible.
 */
fun findDocument(label: String?, timeout: Long): ViewInteraction {
    return onView(documentMatcher(label))
        .perform(WaitUntilVisible(timeout))
        .perform(ViewActions.scrollCompletelyTo())
}

/** Perform the specified action on the item with the specified label in the directory list. */
fun actionOnDocumentItem(label: String?, action: ViewAction, timeout: Long) {
    waitForDocument(label, timeout)
    val dirListMatcher = Matchers.allOf(withId(R.id.dir_list), isDisplayed())

    onView(dirListMatcher)
        .perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                documentMatcher(label),
                action,
            )
        )
}

/** Open the root with the given label, right click on it, and click the given menu option. */
fun rightClickRootAndClickMenuOption(
    context: Context,
    label: String,
    menuOption: String?,
    @LayoutRes layoutId: Int?,
) {
    showRootsList(context, layoutId)
    actionOnRootItem(label, rightClick())
    onView(withText(menuOption)).inRoot(RootMatchers.isPlatformPopup()).perform(click())
}
