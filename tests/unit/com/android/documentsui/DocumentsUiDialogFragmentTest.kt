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

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.platform.test.annotations.EnableFlags
import android.util.DisplayMetrics
import android.view.Window
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.internal.policy.PhoneWindow
import com.google.common.truth.Truth
import kotlin.math.roundToInt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.Spy

@RunWith(AndroidJUnit4::class)
@SmallTest
@EnableFlags(Flags.FLAG_USE_MATERIAL3)
class DocumentsUIDialogFragmentTest {
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    @Mock private lateinit var mockDialog: Dialog

    @Mock private lateinit var mockResources: Resources

    @Spy private var context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var window: Window
    private lateinit var fragment: TestDocumentsUIDialogFragment

    companion object {
        // Use 1 for density to simply the test: 1dp = 1px.
        private const val TEST_DENSITY = 1f

        // Should be equal to the constant in DocumentsUIDialogFragment.
        private const val DEFAULT_HORIZONTAL_INSET = 24
        private const val MAX_DIALOG_WIDTH = 512
        private const val MAX_DIALOG_WINDOW_WIDTH =
            MAX_DIALOG_WIDTH + 2 * DEFAULT_HORIZONTAL_INSET // 560
    }

    @Before
    fun setUpTest() {
        MockitoAnnotations.openMocks(this)

        // We need a real Window object here because the ".attributes" assignment won't work on a
        // mocked Window object, and ".attributes" getter is final thus can't be mocked.
        window = PhoneWindow(context, null, null)
        window.attributes = WindowManager.LayoutParams()
        `when`(mockDialog.window).thenReturn(window)
        `when`(context.resources).thenReturn(mockResources)

        fragment = TestDocumentsUIDialogFragment(context, mockDialog)
    }

    private fun setupScreenDimensions(parentWindowWidth: Int, dialogPercentage: Int) {
        val displayMetrics =
            DisplayMetrics().apply {
                widthPixels = parentWindowWidth
                density = TEST_DENSITY
            }
        `when`(mockResources.displayMetrics).thenReturn(displayMetrics)
        `when`(mockResources.getInteger(R.integer.dialog_width_percentage))
            .thenReturn(dialogPercentage)
        `when`(mockResources.getDimensionPixelSize(R.dimen.dialog_max_width))
            .thenReturn(MAX_DIALOG_WIDTH)
    }

    @Test
    fun onStart_dialogWidthOnExpandedScreen() {
        val parentWindowWidth = 1600 // >= 900
        val dialogWidthPercentage = 50
        setupScreenDimensions(parentWindowWidth, dialogWidthPercentage)

        fragment.onStart()

        // Because 1600 * 50/100 is larger than MAX_DIALOG_WIDTH_WITH_INSET (560).
        val expectedWidth = MAX_DIALOG_WINDOW_WIDTH
        Truth.assertThat(window.attributes.width).isEqualTo(expectedWidth)
    }

    @Test
    fun onStart_dialogWidthOnMediumScreen() {
        val parentWindowWidth = 700 // >= 600 and < 900
        val dialogWidthPercentage = 50
        setupScreenDimensions(parentWindowWidth, dialogWidthPercentage)

        fragment.onStart()

        val expectedWidth = (700 * 50 / 100f).roundToInt()
        Truth.assertThat(window.attributes.width).isEqualTo(expectedWidth)
    }

    @Test
    fun onStart_dialogWidthOnSmallScreen() {
        val parentWindowWidth = 500 // < 600
        val dialogWidthPercentage = 100
        setupScreenDimensions(parentWindowWidth, dialogWidthPercentage)

        fragment.onStart()

        val expectedWidth = (500 * 100 / 100f).roundToInt()
        Truth.assertThat(window.attributes.width).isEqualTo(expectedWidth)
    }
}

/** A concrete implementation of the abstract [DocumentsUIDialogFragment] for testing purposes. */
class TestDocumentsUIDialogFragment(
    private var fakeContext: Context,
    private var fakeDialog: Dialog,
) : DocumentsUIDialogFragment() {
    override fun getContext(): Context = fakeContext

    override fun getDialog(): Dialog = fakeDialog
}
