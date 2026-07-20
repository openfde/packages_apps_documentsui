/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.documentsui.compose.data.model

/**
 * Represents a single root from a document provider. A root is a top-level directory within a
 * document provider.
 */
data class Root(
    /** The authority of the document provider that this root belongs to. */
    val authority: String,
    /** The unique ID of the root. */
    val rootId: String,
    /** The document ID of the root. This can be used to query for the root's contents. */
    val documentId: String,
    /** The user-visible display name of the root. */
    val displayName: String,
    /** The type of the root. */
    val type: RootType,
)

/**
 * Roots types might have special an icon or label in the UI and are used to sort the roots in the
 * UI. The UI adds a divider between roots in different 100s.
 */
enum class RootType(val order: Int) {
    RECENTS(1),
    DOWNLOADS(2),
    PRIMARY(100),
    USB(101),
    GENERIC(110),
    TRASH(200),
}
