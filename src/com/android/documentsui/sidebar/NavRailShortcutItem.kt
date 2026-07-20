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

import android.view.View
import androidx.annotation.LayoutRes
import com.android.documentsui.ActionHandler
import com.android.documentsui.R
import com.android.documentsui.base.ShortcutInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.util.Material3Config.Companion.getRes

open class NavRailShortcutItem(
    @LayoutRes layoutId: Int,
    title: String?,
    userId: UserId,
    actionHandler: ActionHandler?,
    packageName: String,
    maybeShowBadge: Boolean,
    shortcut: ShortcutInfo,
) : ShortcutItem(layoutId, title, userId, actionHandler, packageName, maybeShowBadge, shortcut) {

    constructor(
        shortcut: ShortcutInfo,
        actionHandler: ActionHandler?,
        packageName: String,
        maybeShowBadge: Boolean,
    ) : this(
        getRes(R.layout.nav_rail_item_root),
        shortcut.title,
        shortcut.root.userId,
        actionHandler,
        packageName,
        maybeShowBadge,
        shortcut,
    )

    override fun bindView(convertView: View) {
        bindIconAndTitle(convertView)
    }
}
