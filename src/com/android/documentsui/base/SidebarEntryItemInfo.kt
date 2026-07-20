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

package com.android.documentsui.base

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.IntDef

/**
 * The interface containing common attributes and methods between a shortcut and a root that is
 * required to visually display the sidebar entry items.
 */
// TODO: b/446566923 - Figure out if we need the `Comparable` interface or if we just need the
//  `compareTo()` method.
interface SidebarEntryItemInfo : Comparable<SidebarEntryItemInfo> {
    val root: RootInfo
    val documentId: String?
    val uri: Uri?
    var title: String?
    val derivedType: Int

    // The values of these constants determine the sort order of various roots in the RootsFragment.
    companion object {
        const val TYPE_UNSET: Int = 0
        const val TYPE_RECENTS: Int = 1
        const val TYPE_HOME_SCREEN: Int = 2
        const val TYPE_IMAGES: Int = 3
        const val TYPE_VIDEO: Int = 4
        const val TYPE_AUDIO: Int = 5
        const val TYPE_DOCUMENTS: Int = 6
        const val TYPE_DOWNLOADS: Int = 7
        const val TYPE_LOCAL: Int = 8
        const val TYPE_MTP: Int = 9
        const val TYPE_SD: Int = 10
        const val TYPE_USB: Int = 11
        const val TYPE_TRASH: Int = 12
        const val TYPE_FILES: Int = 13
        const val TYPE_ROOT_OTHER: Int = 14
        const val TYPE_SHORTCUT_OTHER: Int = 15
    }

    @IntDef(
        TYPE_UNSET,
        TYPE_RECENTS,
        TYPE_HOME_SCREEN,
        TYPE_IMAGES,
        TYPE_VIDEO,
        TYPE_AUDIO,
        TYPE_DOCUMENTS,
        TYPE_DOWNLOADS,
        TYPE_LOCAL,
        TYPE_MTP,
        TYPE_SD,
        TYPE_USB,
        TYPE_TRASH,
        TYPE_FILES,
        TYPE_ROOT_OTHER,
        TYPE_SHORTCUT_OTHER,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SidebarEntryItemType

    // For sidebar item appearance/view
    fun loadDrawerIcon(context: Context, maybeShowBadge: Boolean): Drawable?
    fun supportsCreate(): Boolean
    fun supportsEject(): Boolean
    fun isEjecting(): Boolean
    fun supportsInspect(): Boolean
    fun hasSettings(): Boolean

    /** Returns true if this root can be a target for a drag and drop operation. */
    fun isValidDropTarget(): Boolean
}
