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

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.NavigationViewManager
import com.android.documentsui.NavigationViewManager.Breadcrumb
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.State
import com.android.documentsui.rules.InstantTaskExecutorRule
import com.android.documentsui.rules.OverrideFlagsRule
import java.util.function.IntConsumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BreadcrumbControllerTest {

    // Rule to ensure LiveData operations run synchronously on the test thread
    @get:Rule(order = 0) val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val setFlags = OverrideFlagsRule()

    private lateinit var controller: BreadcrumbController
    private lateinit var navBreadcrumb: Breadcrumb
    private lateinit var searchBreadcrumb: BreadcrumbView
    private lateinit var topDivider: View
    private val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)

    @Before
    fun setUpTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // By default, mark all views as invisible.
        navBreadcrumb = TestBreadcrumb(context)
        searchBreadcrumb = BreadcrumbView(context)
        searchBreadcrumb.visibility = View.GONE
        topDivider = View(context)
        topDivider.visibility = View.GONE

        val model = BreadcrumbModel()
        controller =
            BreadcrumbController(lifecycleOwner, model, navBreadcrumb, searchBreadcrumb, topDivider)

        assertEquals(View.GONE, searchBreadcrumb.visibility)
        assertEquals(false, navBreadcrumb.isVisible)
        assertEquals(View.GONE, topDivider.visibility)
    }

    @Test
    fun testSetSearchBreadcrumbVisible() {
        controller.setSearchBreadcrumbVisible(true)
        assertEquals(View.VISIBLE, searchBreadcrumb.visibility)
        assertEquals(false, navBreadcrumb.isVisible)
        assertEquals(View.VISIBLE, topDivider.visibility)

        controller.setSearchBreadcrumbVisible(false)
        assertEquals(View.GONE, searchBreadcrumb.visibility)
        assertEquals(false, navBreadcrumb.isVisible)
        assertEquals(View.GONE, topDivider.visibility)
    }

    @Test
    fun testSetNavBreadcrumbVisible() {
        controller.setNavBreadcrumbVisible(true)
        assertEquals(View.GONE, searchBreadcrumb.visibility)
        assertEquals(true, navBreadcrumb.isVisible)
        assertEquals(View.VISIBLE, topDivider.visibility)

        controller.setNavBreadcrumbVisible(false)
        assertEquals(View.GONE, searchBreadcrumb.visibility)
        assertEquals(false, navBreadcrumb.isVisible)
        assertEquals(View.GONE, topDivider.visibility)
    }

    @Test
    fun testSetNavBreadcrumbVisible_whenSearchBreadcrumbVisible() {
        searchBreadcrumb.visibility = View.VISIBLE
        topDivider.visibility = View.VISIBLE

        controller.setNavBreadcrumbVisible(true)
        assertEquals(View.GONE, searchBreadcrumb.visibility)
        assertEquals(true, navBreadcrumb.isVisible)
        assertEquals(View.VISIBLE, topDivider.visibility)
    }

    @Test
    fun testSetSearchBreadcrumbVisible_whenNavBreadcrumbVisible() {
        navBreadcrumb.show(true)
        topDivider.visibility = View.VISIBLE

        controller.setSearchBreadcrumbVisible(true)
        assertEquals(View.VISIBLE, searchBreadcrumb.visibility)
        assertEquals(false, navBreadcrumb.isVisible)
        assertEquals(View.VISIBLE, topDivider.visibility)
    }

    @Test
    fun testEvents() {
        var folderIndex = -1
        controller.setSearchBreadcrumbVisible(true)
        controller.setSearchBreadcrumbClickConsumer { value -> folderIndex = value }

        val root = RootInfo().apply { title = "root" }
        val stack = DocumentStack()
        stack.changeRoot(root)
        controller.getModel()?.setFromStack(stack)

        assertTrue(searchBreadcrumb.performPathItemClick(0))
        assertEquals(0, folderIndex)

        controller.setSearchBreadcrumbClickConsumer(null)
        folderIndex = -1
        // This event should not be propagated.
        assertTrue(searchBreadcrumb.performPathItemClick(0))
        assertEquals(-1, folderIndex)
    }

    @Test
    fun testClearOnHide() {
        controller.setSearchBreadcrumbVisible(true)
        controller.getModel()?.setPath(arrayOf("Foo", "Bar", "baz.txt"))
        assertEquals(3, searchBreadcrumb.getPathLength())
        controller.setSearchBreadcrumbVisible(false)
        controller.setSearchBreadcrumbVisible(true)
        assertEquals(0, searchBreadcrumb.getPathLength())
    }
}

class TestBreadcrumb(context: Context) : Breadcrumb {
    private val fakeView = View(context)

    init {
        fakeView.visibility = View.GONE
    }

    override fun setup(
        env: NavigationViewManager.Environment?,
        state: State?,
        listener: IntConsumer?,
    ) {}

    override fun show(visibility: Boolean) {
        fakeView.visibility = if (visibility) View.VISIBLE else View.GONE
    }

    override fun postUpdate() {}

    override fun isVisible(): Boolean = fakeView.isVisible
}
