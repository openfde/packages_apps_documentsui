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

package com.android.documentsui.actions

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.util.HumanReadables
import java.util.concurrent.TimeoutException
import org.hamcrest.Matcher

/**
 * An action that waits until a view matching the given matcher exists as a direct child in the
 * given RecyclerView. It will attempt to scroll through the RecyclerView's items to find a match.
 * This differs from WaitUntilVisible which should be used only if the view already exists in the
 * RecyclerView. Typical use:
 *
 *  <pre>
 *   onView(withId(R.id.rec_view_id)).perform(WaitUntilExistsInRecyclerView(matcher, 500L))
 *  </pre>
 */
class WaitUntilExistsInRecyclerView(
    private val matcher: Matcher<View>,
    private val timeoutMs: Long = 500L,
) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return isAssignableFrom(RecyclerView::class.java)
    }

    override fun getDescription(): String {
        return "wait up to ${timeoutMs}ms for the view matching $matcher to exist in RecyclerView"
    }

    override fun perform(uiController: UiController, view: View) {
        val endTime = System.currentTimeMillis() + timeoutMs

        do {
            try {
                // Try to scroll to the child.
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(matcher)
                    .perform(uiController, view)
                return
            } catch (_: Exception) {
                // If not found, wait a bit and retry until timeout.
                uiController.loopMainThreadForAtLeast(50L)
            }
        } while (System.currentTimeMillis() < endTime)

        throw PerformException.Builder()
            .withActionDescription(description)
            .withCause(
                TimeoutException("Waited ${timeoutMs}ms for view matching $matcher in RecyclerView")
            )
            .withViewDescription(HumanReadables.describe(view))
            .build()
    }
}
