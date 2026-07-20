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
package com.android.documentsui.breadcrumbs

import android.annotation.SuppressLint
import android.platform.test.annotations.EnableFlags
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.google.common.truth.Truth.assertThat
import java.util.function.IntConsumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class TestOnClickConsumer : IntConsumer {

    var acceptedValue = -1

    override fun accept(value: Int) {
        acceptedValue = value
    }
}

@EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class BreadcrumbViewTest {

    private lateinit var view: BreadcrumbView

    @SuppressLint("InflateParams")
    @Before
    fun setUpTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        view = BreadcrumbView(context)
    }

    @Test
    fun testSetPath() {
        view.visibility = View.VISIBLE
        view.setPath(arrayOf("Root", "Folder01", "Folder02"))
        val testOnClickConsumer = TestOnClickConsumer()
        view.setClickConsumer(testOnClickConsumer)

        assertThat(view.performPathItemClick(0)).isTrue()
        assertThat(testOnClickConsumer.acceptedValue).isEqualTo(0)
        assertThat(view.performPathItemClick(1)).isTrue()
        assertThat(testOnClickConsumer.acceptedValue).isEqualTo(1)
        assertThat(view.performPathItemClick(2)).isTrue()
        assertThat(testOnClickConsumer.acceptedValue).isEqualTo(2)

        view.setPath(arrayOf("NewRoot"))
        assertThat(view.performPathItemClick(0)).isTrue()
        assertThat(testOnClickConsumer.acceptedValue).isEqualTo(0)
        assertThat(view.performPathItemClick(1)).isFalse()

        view.setPath(arrayOf())
        assertThat(view.performPathItemClick(0)).isFalse()
    }

    @Test
    fun testClear() {
        view.visibility = View.VISIBLE
        view.setPath(arrayOf("Root", "Folder01", "Folder02"))
        view.clear()
        assertThat(view.performPathItemClick(0)).isFalse()
    }
}
