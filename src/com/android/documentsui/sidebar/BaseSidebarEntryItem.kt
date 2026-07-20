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

package com.android.documentsui.sidebar

import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.android.documentsui.ActionHandler
import com.android.documentsui.FocusManager
import com.android.documentsui.IconUtils
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.SidebarEntryItemInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.button.MaterialButton

abstract class BaseSidebarEntryItem(
    @LayoutRes layoutId: Int,
    title: String?,
    stringId: String?,
    userId: UserId?,
    val actionHandler: ActionHandler?,
    override val packageName: String,
    val maybeShowBadge: Boolean,
) : SortableItem(layoutId, title, stringId, userId) {
    var docInfo: DocumentInfo? = null
    abstract val itemInfo: SidebarEntryItemInfo
    override val itemType: Int
        get() = BASE_SIDEBAR_ENTRY_ITEM

    protected fun bindAction(view: View, visibility: Int, iconId: Int, description: String?) {
        if (isUseMaterial3FlagEnabled()) {
            val actionIcon = view.findViewById<MaterialButton?>(getRes(R.id.action_icon))

            actionIcon!!.visibility = visibility
            actionIcon.setOnClickListener(
                if (visibility == View.VISIBLE) {
                    View.OnClickListener { view: View? -> this.onActionClick(view!!) }
                } else {
                    null
                }
            )
            if (isUseMaterial3FlagEnabled()) {
                if (visibility == View.VISIBLE) {
                    FocusManager.setButtonFocusStyle(actionIcon)
                } else {
                    actionIcon.onFocusChangeListener = null
                }
            } else {
                actionIcon.onFocusChangeListener =
                    if (visibility == View.VISIBLE) {
                        View.OnFocusChangeListener { view: View?, hasFocus: Boolean ->
                            this.onActionIconFocusChange(view, hasFocus)
                        }
                    } else {
                        null
                    }
            }
            if (description != null) {
                actionIcon.setContentDescription(description)
            }
            if (iconId > 0) {
                actionIcon.setIconResource(iconId)
            }
        } else {
            val actionIcon = view.findViewById<View?>(getRes(R.id.action_icon)) as ImageView?
            val verticalDivider = view.findViewById<View?>(getRes(R.id.vertical_divider))
            val actionIconArea = view.findViewById<View?>(getRes(R.id.action_icon_area))

            verticalDivider!!.visibility = visibility
            actionIconArea!!.visibility = visibility
            actionIconArea.setOnClickListener(
                if (visibility == View.VISIBLE) {
                    View.OnClickListener { view: View? -> this.onActionClick(view!!) }
                } else {
                    null
                }
            )
            if (description != null) {
                actionIconArea.setContentDescription(description)
            }
            if (iconId > 0) {
                actionIcon!!.setImageDrawable(
                    IconUtils.applyTintColor(view.context, iconId, getRes(R.color.item_action_icon))
                )
            }
        }
    }

    protected abstract fun onActionClick(view: View)

    /**
     * When the action icon is focused, adding a focus ring indicator using Stroke.
     *
     * TODO(b/381957932): Remove this once Material Button supports focus ring.
     */
    protected fun onActionIconFocusChange(view: View?, hasFocus: Boolean) {
        val actionIcon = view as MaterialButton
        if (hasFocus) {
            val focusRingWidth =
                actionIcon.resources.getDimensionPixelSize(getRes(R.dimen.focus_ring_width))
            actionIcon.strokeWidth = focusRingWidth
        } else {
            actionIcon.strokeWidth = 0
        }
    }

    protected fun bindIconAndTitle(view: View) {
        bindIcon(view, itemInfo.loadDrawerIcon(view.context, maybeShowBadge))
        bindTitle(view)
    }

    private fun bindIcon(view: View, drawable: Drawable?) {
        val icon = view.findViewById<View?>(android.R.id.icon) as ImageView?
        icon!!.setImageDrawable(drawable)
    }

    private fun bindTitle(view: View) {
        val titleView = view.findViewById<View?>(android.R.id.title) as TextView?
        titleView!!.text = title
    }

    protected fun bindSummary(view: View, summary: String?) {
        val summaryView = view.findViewById<View?>(android.R.id.summary) as TextView?
        summaryView!!.text = summary
        summaryView.visibility = if (TextUtils.isEmpty(summary)) View.GONE else View.VISIBLE
    }

    abstract override fun isDropTarget(): Boolean

    public override fun createContextMenu(
        menu: Menu,
        inflater: MenuInflater,
        menuManager: MenuManager,
    ) {
        inflater.inflate(getRes(R.menu.root_context_menu), menu)
        menuManager.updateSidebarItemContextMenu(menu, itemInfo, docInfo)
    }
}
