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
package com.android.documentsui

import android.view.MenuItem
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.base.EventHandler
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class SelectionBarControllerTest {
    private lateinit var appBar: MaterialToolbar
    private lateinit var selectionBar: MaterialToolbar
    private lateinit var selectionManager: DocsSelectionHelper
    private lateinit var selectionBarController: SelectionBarController
    private lateinit var menuManager: MenuManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)

        appBar = MaterialToolbar(context)
        selectionBar = MaterialToolbar(context)
        // By default toolbar is visible but selection bar is not.
        appBar.visibility = View.VISIBLE
        selectionBar.visibility = View.GONE
        menuManager = mock(MenuManager::class.java)
        selectionManager = SelectionHelpers.createTestInstance()
        selectionBarController =
            SelectionBarController(
                appBar,
                selectionBar,
                null, // FocusManager
                menuManager,
                selectionManager,
            )
    }

    @Test
    fun testOnSelectionChanged_showHideSelectionBar() {
        assertThat(selectionManager.selection).hasSize(0)

        // Let selection bar controller observe selection manager.
        selectionManager.addObserver(selectionBarController)

        // Select one file and it should notify selection bar controller.
        selectionManager.select("file1")
        assertThat(selectionManager.selection).hasSize(1)

        // Assert selection bar should show and app bar should hide.
        assertEquals(selectionBar.visibility, View.VISIBLE)
        assertEquals(appBar.visibility, View.GONE)

        // Remove all selections.
        selectionManager.clearSelection()
        assertThat(selectionManager.selection).hasSize(0)

        // Assert selection bar should hide and app bar should show.
        assertEquals(selectionBar.visibility, View.GONE)
        assertEquals(appBar.visibility, View.VISIBLE)
    }

    @Test
    fun testSelectionBar_isConfiguredCorrectly_onSelection() {
        // Let selection bar controller observe selection manager.
        selectionManager.addObserver(selectionBarController)

        // Select one file.
        selectionManager.select("file1")

        // Assert title is updated for a single item.
        val expectedTitleSingle =
            selectionBar.context.resources.getQuantityString(
                getRes(R.plurals.elements_selected),
                1,
                1,
            )
        assertThat(selectionBar.title).isEqualTo(expectedTitleSingle)

        // Assert navigation icon and description are set.
        assertThat(selectionBar.navigationIcon).isNotNull()
        assertThat(selectionBar.navigationContentDescription)
            .isEqualTo(selectionBar.context.getString(R.string.clear_selection))

        // Select a second file.
        selectionManager.select("file2")

        // Assert title is updated for multiple items.
        val expectedTitleMultiple =
            selectionBar.context.resources.getQuantityString(
                getRes(R.plurals.elements_selected),
                2,
                2,
            )
        assertThat(selectionBar.title).isEqualTo(expectedTitleMultiple)
    }

    @Test
    fun testActionMenu_isInflatedAndUpdated_onSelection() {
        // Provide context using the updateSelection method.
        val selectionDetails = mock(MenuManager.SelectionDetails::class.java)
        val menuItemClicker = mock(EventHandler::class.java) as EventHandler<MenuItem>
        selectionBarController.updateSelection(selectionDetails, menuItemClicker)

        // Let selection bar controller observe selection manager.
        selectionManager.addObserver(selectionBarController)

        // Select an item to trigger the update.
        selectionManager.select("file1")

        // Verify the menu was inflated.
        assertThat(selectionBar.menu).isNotNull()
        assertThat(selectionBar.menu.hasVisibleItems()).isTrue()

        // Verify the MenuManager was called to update the menu with the correct details.
        verify(menuManager).updateActionMenu(selectionBar.menu, selectionDetails)
    }

    @Test
    fun testCloseSelectionBar_clearsSelection() {
        selectionManager.addObserver(selectionBarController)
        selectionManager.select("file1")

        // Confirm selection is active and UI is in selection mode.
        assertThat(selectionManager.hasSelection()).isTrue()
        assertEquals(View.VISIBLE, selectionBar.visibility)
        assertEquals(View.GONE, appBar.visibility)

        // Call the method to clear the selection.
        selectionBarController.closeSelectionBar()

        // Assert that the selection is now empty.
        assertThat(selectionManager.hasSelection()).isFalse()

        // Assert that the UI has returned to the normal state.
        assertEquals(View.GONE, selectionBar.visibility)
        assertEquals(View.VISIBLE, appBar.visibility)
    }
}
