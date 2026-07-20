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

/** `ListItem` stores the data relating to each row in the "Get info" dialog box. */
sealed class ListItem {
    // Represents a Section Header (e.g., "General")
    data class Header(val title: String) : ListItem()

    // Represents a Data Row (e.g., "Type", "Image")
    data class Info(val label: String, val value: String) : ListItem()

    // Represents a Data Row where the value is selectable.
    data class InfoSelectable(val label: String, val value: String) : ListItem()
}
