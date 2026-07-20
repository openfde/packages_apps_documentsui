/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.documentsui.base.UserId

/**
 * An overarching class for sidebar items that can be sorted. This is inherited by
 * BaseSidebarEntryItem and AppItem.
 */
abstract class SortableItem(layoutId: Int, val title: String?, stringId: String?, userId: UserId?) :
    Item(layoutId, stringId, userId) {
    abstract val packageName: String
    abstract val itemType: Int

    companion object {
        const val BASE_SIDEBAR_ENTRY_ITEM = 1
        const val APP_ITEM = 2
        const val TYPE_UNKNOWN = 3
    }
}
