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

package com.android.documentsui.demoapp.utils

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.android.documentsui.demoapp.models.PickerState

/**
 * Creates and remembers a launcher for `ActivityResultContracts.StartActivityForResult` that
 * handles document picking results.
 *
 * This function simplifies the process of launching an intent to pick a document and handling the
 * result callback by converting the activity result into a [PickerState].
 *
 * @param onResult A callback invoked with the resulting [PickerState].
 * @return A [ManagedActivityResultLauncher] that can be used to launch the picker intent.
 */
@Composable
fun rememberDocumentLauncher(
    onResult: (PickerState) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                onResult(PickerState.Success(result.data?.data))
            }
            Activity.RESULT_CANCELED -> {
                onResult(PickerState.Cancelled)
            }
            else -> {
                onResult(PickerState.Error("Unknown result code: ${result.resultCode}"))
            }
        }
    }
}

/**
 * Creates and remembers a launcher for `ActivityResultContracts.StartActivityForResult` that
 * handles document creation results.
 *
 * This function simplifies the process of launching an intent to create a document and handling the
 * result callback by converting the activity result into a [PickerState].
 *
 * @param onResult A callback invoked with the resulting [PickerState].
 * @return A [ManagedActivityResultLauncher] that can be used to launch the create document intent.
 */
@Composable
fun rememberCreateDocumentLauncher(
    onResult: (PickerState) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                onResult(PickerState.Created(result.data?.data))
            }
            Activity.RESULT_CANCELED -> {
                onResult(PickerState.Cancelled)
            }
            else -> {
                onResult(PickerState.Error("Unknown result code: ${result.resultCode}"))
            }
        }
    }
}
