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

import android.view.DragEvent
import android.view.View
import androidx.annotation.LayoutRes
import com.android.documentsui.ActionHandler
import com.android.documentsui.R
import com.android.documentsui.base.ShortcutInfo
import com.android.documentsui.base.SidebarEntryItemInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.util.Objects

open class ShortcutItem(
    @LayoutRes layoutId: Int,
    title: String?,
    userId: UserId,
    actionHandler: ActionHandler?,
    packageName: String,
    maybeShowBadge: Boolean,
    var shortcut: ShortcutInfo,
) :
    BaseSidebarEntryItem(
        layoutId,
        title,
        String.format(
            "ShortcutItem{%s/%s/%s}",
            shortcut.root.authority,
            shortcut.root.rootId,
            shortcut.title,
        ),
        userId,
        actionHandler,
        packageName,
        maybeShowBadge,
    ) {

    constructor(
        shortcut: ShortcutInfo,
        actionHandler: ActionHandler?,
        packageName: String,
        maybeShowBadge: Boolean,
    ) : this(
        getRes(R.layout.item_root),
        shortcut.title,
        shortcut.root.userId,
        actionHandler,
        packageName,
        maybeShowBadge,
        shortcut,
    )

    override val itemInfo: SidebarEntryItemInfo = shortcut

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (this === other) {
            return true
        }

        if (other is ShortcutItem) {
            val o = other
            return Objects.equals(shortcut, o.shortcut) &&
                Objects.equals(docInfo, o.docInfo) &&
                Objects.equals(actionHandler, o.actionHandler) &&
                Objects.equals(packageName, o.packageName)
        }

        return false
    }

    public override fun bindView(convertView: View) {
        bindAction(convertView, View.GONE, -1, null)
        bindIconAndTitle(convertView)
        bindSummary(convertView, null)
    }

    override fun isRoot(): Boolean {
        return false
    }

    override fun isShortcut(): Boolean {
        return true
    }

    override fun open() {
        actionHandler?.openShortcut(shortcut)
    }

    override fun dropOn(event: DragEvent?): Boolean {
        return actionHandler?.dropOn(event, shortcut) ?: false
    }

    override fun onActionClick(view: View) {
        // Do nothing, ShortcutItem does not have an action click button or behaviour.
        return
    }

    override fun isDropTarget(): Boolean {
        return itemInfo.supportsCreate()
    }
}
