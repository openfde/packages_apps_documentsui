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
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The different types of callbacks for the consent dialog, one for each option that user can take.
 */
data class ConsentCallbacks(
    /** User chose to enable the feature. */
    val onEnable: () -> Unit,

    /**
     * User chose to not enable the feature. This is the button "Don't ask me again". This button is
     * only presented if the dialog is shown proactively.
     */
    val onCancel: (() -> Unit)? = null,

    /** User chose to "Not now" option, so we can remind later. This is the button "Not now". */
    val onRemindLater: () -> Unit,
)

/** A dialog fragment that asks the user for consent to enable the summary column. */
class SummaryConsentFragment(
    private val onEnableButtonClick: () -> Unit = {},
    private val onCancelButtonClick: (() -> Unit)? = null,
    private val onRemindLaterButtonClick: () -> Unit = {},
) : DocumentsUIDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getString(EXTRA_TITLE)
        val message = Html.fromHtml(args.getString(EXTRA_MESSAGE), Html.FROM_HTML_MODE_LEGACY)

        val positiveButton = context?.getString(R.string.summary_consent_ok_button)
        val remindLaterButton = context?.getString(R.string.summary_consent_remind_later_button)
        val negativeButton = context?.getString(R.string.summary_consent_cancel_button)

        val builder =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveButton) { _, _ -> onEnableButtonClick() }
                .setNegativeButton(remindLaterButton) { _, _ -> onRemindLaterButtonClick() }

        // The "Don't ask me again" button is only displayed if the dialog is shown proactively, as
        // in, not initiated by the user.
        if (onCancelButtonClick != null) {
            builder.setNeutralButton(negativeButton) { _, _ -> onCancelButtonClick() }
        }
        val dialog = builder.create()

        dialog.setOnShowListener {
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.movementMethod = LinkMovementMethod.getInstance()
        }

        return dialog
    }

    companion object {
        const val TAG = "SummaryConsentFragment"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"

        @JvmStatic
        fun show(
            fm: FragmentManager,
            context: Context,
            title: String,
            message: String,
            callbacks: ConsentCallbacks,
        ) {
            val dialog = newInstance(title, message, callbacks)
            dialog.show(fm, TAG)
        }

        @JvmStatic
        private fun newInstance(
            title: String,
            message: String,
            callbacks: ConsentCallbacks,
        ): SummaryConsentFragment {
            val dialog =
                SummaryConsentFragment(
                    callbacks.onEnable,
                    callbacks.onCancel,
                    callbacks.onRemindLater,
                )
            val args = Bundle()
            args.putString(EXTRA_TITLE, title)
            args.putString(EXTRA_MESSAGE, message)
            dialog.arguments = args
            return dialog
        }
    }
}
