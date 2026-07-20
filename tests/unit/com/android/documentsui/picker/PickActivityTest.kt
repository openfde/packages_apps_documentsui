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

package com.android.documentsui.picker

import android.annotation.SuppressLint
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_SHIFT_ON
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.Injector
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class PickActivityTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    private lateinit var activity: TestPickActivity
    private lateinit var actions: ActionHandler<PickActivity>
    private lateinit var injector: Injector<ActionHandler<PickActivity>>

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity = TestPickActivity() }

        actions = mock(ActionHandler::class.java) as ActionHandler<PickActivity>
        injector = mock(Injector::class.java) as Injector<ActionHandler<PickActivity>>
        injector.actions = actions

        activity.setInjector(injector)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testCtrlSpaceTogglesSelection() {
        val event = KeyEvent(0, 0, ACTION_DOWN, KEYCODE_SPACE, 0, META_CTRL_ON)
        val handled = activity.onKeyShortcut(event)
        assertThat(handled).isTrue()
        verify(actions).toggleFocusedItemSelection()
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testCtrlSpaceDoesNothing() {
        val event = KeyEvent(0, 0, ACTION_DOWN, KEYCODE_SPACE, 0, META_CTRL_ON)
        val handled = activity.onKeyShortcut(event)
        assertThat(handled).isFalse()
        verify(actions, never()).toggleFocusedItemSelection()
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testCtrlShiftSpaceDoesNothing() {
        val event = KeyEvent(0, 0, ACTION_DOWN, KEYCODE_SPACE, 0, META_CTRL_ON or META_SHIFT_ON)
        val handled = activity.onKeyShortcut(event)
        assertThat(handled).isFalse()
        verify(actions, never()).toggleFocusedItemSelection()
    }

    class TestPickActivity : PickActivity() {
        fun onKeyShortcut(event: KeyEvent): Boolean {
            // Since PickActivity is unit-tested in a setup without any Window object,
            // android.app.Activity.onKeyShortcut() can throw an exception instead of returning
            // false. We catch exceptions here and turn them into return values.
            return try {
                onKeyShortcut(event.keyCode, event)
            } catch (_: Exception) {
                false
            }
        }

        fun setInjector(injector: Injector<ActionHandler<PickActivity>>) {
            @SuppressLint("VisibleForTests")
            mInjector = injector
        }
    }
}
