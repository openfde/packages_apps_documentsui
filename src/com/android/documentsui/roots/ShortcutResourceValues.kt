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

package com.android.documentsui.roots

import java.util.Objects

class ShortcutResourceValues(
    val authority: String,
    val rootId: String,
    val parentDocumentId: String,
    val folderTitle: String,
    var localizedDisplayTitle: String,
    val iconReference: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other.javaClass != javaClass) {
            return false
        }
        val o = other as ShortcutResourceValues
        return Objects.equals(authority, o.authority) &&
            Objects.equals(rootId, o.rootId) &&
            Objects.equals(iconReference, o.iconReference) &&
            Objects.equals(parentDocumentId, o.parentDocumentId) &&
            Objects.equals(folderTitle, o.folderTitle)
    }

    override fun toString(): String {
        return "authority=$authority, rootId=$rootId, iconRef=$iconReference, " +
            "parentDocumentId=$parentDocumentId, title=$folderTitle, " +
            "localizedDisplayTitle=$localizedDisplayTitle"
    }

    companion object {
        const val INVALID_ICON_REF = -1
    }
}
