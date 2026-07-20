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

import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.UnitUtils.Companion.dpToPx
import kotlin.math.roundToInt

/**
 * Base class for all DocumentsUI dialogs, it's based on the [DialogFragment] class but adds some
 * style customizations (e.g. width).
 */
abstract class DocumentsUIDialogFragment : DialogFragment() {
    companion object {
        // This is the default inset between dialog and its own window.
        private const val DEFAULT_HORIZONTAL_INSET_DP = 24f
    }

    override fun onStart() {
        super.onStart()

        if (!isUseMaterial3FlagEnabled()) {
            return
        }

        customizeWindowWidth()
        setButtonsFocusStyle()
    }

    private fun customizeWindowWidth() {
        dialog?.window?.let { w ->
            val params = WindowManager.LayoutParams()
            params.copyFrom(w.attributes)
            val parentWindowWidth = resources.displayMetrics.widthPixels

            // Dialog width should be certain ratio of the whole window width, at the same time,
            // the width should not be greater than the max width.
            val dialogWidthRatio = resources.getInteger(R.integer.dialog_width_percentage) / 100f
            // The maximum dialog window width = maximum dialog width + 2 * INSET.
            val dialogWindowMaxWidth =
                resources.getDimensionPixelSize(R.dimen.dialog_max_width) +
                    2 * dpToPx(requireContext(), DEFAULT_HORIZONTAL_INSET_DP)
            params.width =
                ((parentWindowWidth * dialogWidthRatio).roundToInt()).coerceAtMost(
                    dialogWindowMaxWidth
                )

            w.attributes = params
        }
    }

    private fun setButtonsFocusStyle() {
        // Button IDs are from the Material library m3_alert_dialog_actions.xml.
        val buttonIds =
            intArrayOf(
                android.R.id.button1, // positive button
                android.R.id.button2, // negative button
                android.R.id.button3, // neutral button
            )

        for (buttonId in buttonIds) {
            val button: Button? = dialog?.findViewById(buttonId)
            FocusManager.setButtonFocusStyle(button)
        }
    }
}
