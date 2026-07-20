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

package com.android.documentsui.files

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentManager
import com.android.documentsui.DocumentsUIDialogFragment
import com.android.documentsui.Injector
import com.android.documentsui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Dialog shown to users when performing a empty trash */
class EmptyTrashDialogFragment : DocumentsUIDialogFragment() {

    companion object {
        private const val TAG = "EmptyTrashDialog"

        /**
         * Create and show the dialog UI.
         *
         * @param fm the fragment manager
         */
        @JvmStatic
        fun show(fm: FragmentManager) {
            if (fm.isStateSaved) {
                Log.w(TAG, "Skip showing empty trash dialog because state saved")
                return
            }

            if (fm.findFragmentByTag(TAG) != null) {
                Log.w(TAG, "Skipping to show empty trash dialog because it's already showing.")
                return
            }

            val dialog = EmptyTrashDialogFragment()
            dialog.show(fm, TAG)
        }
    }

    /**
     * Creates the dialog UI.
     *
     * @param savedInstanceState bundle with required arg: selectedDoc.
     * @return an AlertDialog instance.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val injector: Injector<*> = (activity as FilesActivity).injector
        val builder =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.empty_trash_dialog_title))
                .setMessage(getString(R.string.empty_trash_dialog_message))
                .setPositiveButton(getString(R.string.empty_trash_dialog_action_button)) {
                    dialog,
                    which ->
                    injector.actions.permanentlyDeleteTrashDocuments()
                }
                .setNegativeButton(getString(android.R.string.cancel), null)

        return builder.create()
    }
}
