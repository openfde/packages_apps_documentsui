/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.files.getinfo

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.DocumentsUIDialogFragment
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Shared
import com.android.documentsui.util.Material3Config
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * A dialog that shows all the metadata aspects related to a file / folder. Is currently invoked
 * when the user clicks "Get info" in the 3-dot / context menus.
 */
class GetInfoDialogFragment : DocumentsUIDialogFragment() {

    /** The Document that the dialog is retrieving information about. */
    private lateinit var doc: DocumentInfo

    /** Whether to show the debug information as a section. */
    private var showDebug: Boolean = false

    /** The summary of the document if available. */
    private var summary: String? = null

    /** Lazily initialized ViewModel. */
    private val viewModel: GetInfoViewModel by viewModels {
        val application = requireActivity().application
        val fileTypeLookup = DocumentsApplication.getFileTypeLookup(requireContext())
        GetInfoViewModel.Factory(application, doc, fileTypeLookup, showDebug, summary)
    }

    override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)
        val arguments = arguments ?: savedInstanceBundle
        doc =
            arguments?.let {
                BundleCompat.getParcelable(it, Shared.EXTRA_DOC, DocumentInfo::class.java)
            }
                ?: run {
                    Log.e(TAG, "Document missing for Get info dialog, dismissing.")
                    DocumentInfo() // Fallback to avoid lateinit crash if dismissed immediately.
                }
        showDebug = arguments?.getBoolean(Shared.EXTRA_SHOW_DEBUG, false) ?: false
        summary = arguments?.getString(Shared.EXTRA_SUMMARY)

        if (doc.documentId == null) {
            // Because we're using `lateinit` on the var (if it doesn't exist, that is bad) we need
            // to double check the initial isn't the `DocumentInfo()` empty constructed version. If
            // it is, just dismiss the dialog.
            dismiss()
        }
    }

    /** Creates the dialog UI. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val dialogView = LayoutInflater.from(builder.context).inflate(R.layout.get_info_m3, null)

        return builder
            .apply {
                setTitle(Material3Config.getRes(R.string.peek_metadata_header_title))
                setView(dialogView)
                setPositiveButton(android.R.string.ok, null)
            }
            .create()
    }

    /** Start observing changes in the ViewModel. */
    override fun onStart() {
        super.onStart()
        val contentLayout = dialog?.findViewById<LinearLayout>(R.id.content_layout)

        contentLayout?.let {
            lifecycleScope.launch {
                viewModel.items.collect { items ->
                    // Rebuild layout only when there are actually items in the list.
                    if (items.isNotEmpty()) {
                        rebuildLayout(it, items)
                    }
                }
            }
        }
    }

    /**
     * Given we know the number of items in the dialog will be small (<50) it's more performant to
     * just blow the list away and rebuild than to smartly update it.
     */
    private fun rebuildLayout(container: LinearLayout, items: List<ListItem>) {
        container.removeAllViews()

        val inflater = LayoutInflater.from(container.context)
        items.forEachIndexed { index, item ->
            // Check if the item is the first in the section.
            val isFirstInSection = if (index == 0) true else items[index - 1] is ListItem.Header

            // Check if this is the last item in the section.
            val isLastInSection =
                if (index == items.size - 1) {
                    true
                } else {
                    items[index + 1] is ListItem.Header
                }

            val view =
                when (item) {
                    is ListItem.Header -> createHeaderView(inflater, container, item)
                    is ListItem.Info ->
                        createInfoView(
                            inflater,
                            container,
                            item.label,
                            item.value,
                            selectable = false,
                            isFirstInSection,
                            isLastInSection,
                        )
                    is ListItem.InfoSelectable ->
                        createInfoView(
                            inflater,
                            container,
                            item.label,
                            item.value,
                            selectable = true,
                            isFirstInSection,
                            isLastInSection,
                        )
                }
            container.addView(view)
        }
    }

    /**
     * Creates a UI row out of ListItem.Header. This represents the title for the items to follow.
     * e.g. "General info".
     */
    private fun createHeaderView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        item: ListItem.Header,
    ): View {
        return inflater.inflate(R.layout.get_info_header_m3, parent, false).apply {
            findViewById<TextView>(R.id.header_title).text = item.title
        }
    }

    /**
     * Creates a UI row out of ListItem.Info or ListItem.InfoSelectable, e.g. "Type". Each row has 4
     * different possible states:
     * - Single: Represents only a single item in the row (all 4 corners have a larger radius).
     * - Top: When the list has >1 item (top 2 corners have a larger radius).
     * - Middle: When the list has >2 items (no corners have larger radius).
     * - Bottom: When the list has >1 item (bottom 2 corners have a larger radius). This is
     *   calculated based on the number of items in each section and the styling applied to each
     *   individual row.
     */
    private fun createInfoView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        label: String,
        value: String,
        selectable: Boolean,
        isFirstInSection: Boolean,
        isLastInSection: Boolean,
    ): View {
        val view = inflater.inflate(R.layout.get_info_item_m3, parent, false)

        view.findViewById<TextView>(R.id.item_label).text = label
        view.findViewById<TextView>(R.id.item_value).apply {
            text = value
            setTextIsSelectable(selectable)
        }

        // Apply the correct background drawable based on the position of
        // the item in the section. The border radius is different based on
        // the position of the item.
        val backgroundRes =
            when {
                isFirstInSection && isLastInSection -> R.drawable.get_info_item_single_m3
                isFirstInSection -> R.drawable.get_info_item_top_m3
                isLastInSection -> R.drawable.get_info_item_bottom_m3
                else -> R.drawable.get_info_item_middle_m3
            }
        view.setBackgroundResource(backgroundRes)

        // Apply the appropriate margins to the item according to the
        // position in the list.
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        val gapBetweenItems = resources.getDimensionPixelSize(R.dimen.space_extra_small_1)
        if (isFirstInSection && isLastInSection) {
            // No padding as it's the only item in the list.
            params.bottomMargin = 0
        } else if (isFirstInSection) {
            params.bottomMargin = gapBetweenItems
        } else if (isLastInSection) {
            params.bottomMargin = resources.getDimensionPixelSize(R.dimen.space_small_1)
        } else {
            params.bottomMargin = gapBetweenItems
        }

        view.layoutParams = params

        return view
    }

    companion object {
        private const val TAG = "GetInfoDialog"

        /**
         * Creates the fragment and converts the supplied `doc` into an appropriate type to pass
         * down to the dialog.
         */
        @JvmStatic
        fun show(fm: FragmentManager, doc: DocumentInfo, showDebug: Boolean, summary: String?) {
            if (fm.isStateSaved) {
                Log.w(TAG, "Skip showing get info dialog because state saved")
                return
            }

            if (fm.findFragmentByTag(TAG) != null) {
                Log.w(TAG, "Skip showing get info dialog because it's already showing.")
                return
            }

            val dialog = GetInfoDialogFragment()
            val args =
                Bundle().apply {
                    putParcelable(Shared.EXTRA_DOC, doc)
                    putBoolean(Shared.EXTRA_SHOW_DEBUG, showDebug)
                    putString(Shared.EXTRA_SUMMARY, summary)
                }
            dialog.arguments = args
            dialog.show(fm, TAG)
        }
    }
}
