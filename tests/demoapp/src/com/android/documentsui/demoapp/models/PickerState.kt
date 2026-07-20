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

package com.android.documentsui.demoapp.models

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.saveable.Saver

/** Represents the various states of a document picker operation. */
sealed interface PickerState {
    /** Represents the initial idle state before any picker action has been initiated. */
    object Idle : PickerState

    /**
     * Represents a successful picker operation where a document was selected.
     *
     * @property uri The URI of the selected document, or null if no URI was returned.
     */
    data class Success(val uri: Uri?) : PickerState

    /**
     * Represents a successful create document operation.
     *
     * @property uri The URI of the created document, or null if no URI was returned.
     */
    data class Created(val uri: Uri?) : PickerState

    /**
     * Represents an error state during the picker operation.
     *
     * @property message A descriptive error message.
     */
    data class Error(val message: String) : PickerState

    /** Represents a state where the picker operation was cancelled by the user. */
    object Cancelled : PickerState
}

/**
 * A [Saver] implementation for saving and restoring [PickerState] across configuration changes.
 *
 * It persists the state type and any associated data (URI or error message) into a [Bundle].
 */
val PickerStateSaver =
    Saver<PickerState, Bundle>(
        save = { state ->
            Bundle().apply {
                when (state) {
                    PickerState.Idle -> putInt("type", 0)
                    is PickerState.Success -> {
                        putInt("type", 1)
                        putParcelable("uri", state.uri)
                    }
                    is PickerState.Created -> {
                        putInt("type", 2)
                        putParcelable("uri", state.uri)
                    }
                    PickerState.Cancelled -> putInt("type", 3)
                    is PickerState.Error -> {
                        putInt("type", 4)
                        putString("message", state.message)
                    }
                }
            }
        },
        restore = { bundle ->
            when (bundle.getInt("type")) {
                1 -> PickerState.Success(bundle.getParcelable("uri", Uri::class.java))
                2 -> PickerState.Created(bundle.getParcelable("uri", Uri::class.java))
                3 -> PickerState.Cancelled
                4 -> PickerState.Error(bundle.getString("message") ?: "Unknown error")
                else -> PickerState.Idle
            }
        },
    )
