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

package com.android.documentsui.sidebar

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class RootItemViewTest {
    companion object {
        private val STATE_HIGHLIGHTED = getRes(R.attr.state_highlighted)
        private val STATE_ERROR = getRes(R.attr.state_error)
    }

    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    private fun createRootItemView() =
        RootItemView(ApplicationProvider.getApplicationContext(), null)

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testDefaultViewState() {
        val view = createRootItemView()
        assertThat(view.drawableState).asList().containsNoneOf(STATE_HIGHLIGHTED, STATE_ERROR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testDefaultViewState_material3() {
        val view = createRootItemView()
        assertThat(view.drawableState).asList().containsNoneOf(STATE_HIGHLIGHTED, STATE_ERROR)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testHighlightState() {
        val view = createRootItemView()
        view.setHighlight(true)
        assertThat(view.drawableState).asList().contains(STATE_HIGHLIGHTED)
        assertThat(view.drawableState).asList().doesNotContain(STATE_ERROR)

        view.setHighlight(false)
        assertThat(view.drawableState).asList().containsNoneOf(STATE_HIGHLIGHTED, STATE_ERROR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testHighlightState_material3() {
        val view = createRootItemView()
        view.setHighlight(true)
        assertThat(view.drawableState).asList().contains(STATE_HIGHLIGHTED)
        assertThat(view.drawableState).asList().doesNotContain(STATE_ERROR)

        view.setHighlight(false)
        assertThat(view.drawableState).asList().containsNoneOf(STATE_HIGHLIGHTED, STATE_ERROR)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testErrorState() {
        val view = createRootItemView()
        view.setHighlight(true)
        view.setError(true)
        assertThat(view.drawableState).asList().contains(STATE_HIGHLIGHTED)
        assertThat(view.drawableState).asList().doesNotContain(STATE_ERROR)

        view.setError(false)
        assertThat(view.drawableState).asList().contains(STATE_HIGHLIGHTED)
        assertThat(view.drawableState).asList().doesNotContain(STATE_ERROR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testErrorState_material3() {
        val view = createRootItemView()
        view.setHighlight(true)
        view.setError(true)
        assertThat(view.drawableState).asList().contains(STATE_ERROR)
        assertThat(view.drawableState).asList().doesNotContain(STATE_HIGHLIGHTED)

        view.setError(false)
        assertThat(view.drawableState).asList().contains(STATE_HIGHLIGHTED)
        assertThat(view.drawableState).asList().doesNotContain(STATE_ERROR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUnhighlightWhenInError() {
        val view = createRootItemView()
        view.setHighlight(true)
        view.setError(true)
        assertThat(view.drawableState).asList().contains(STATE_ERROR)
        assertThat(view.drawableState).asList().doesNotContain(STATE_HIGHLIGHTED)

        view.setHighlight(false)
        assertThat(view.drawableState).asList().containsNoneOf(STATE_HIGHLIGHTED, STATE_ERROR)
    }
}
