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

package com.android.documentsui.demoapp.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.documentsui.demoapp.R
import com.android.documentsui.demoapp.models.PickerState

/**
 * A section that displays the result of a document picker operation.
 *
 * This component renders the current [PickerState], showing a success message with the URI, an
 * error message, or other status messages (e.g., cancelled, created). It visually distinguishes
 * error states.
 *
 * @param state The current [PickerState] to display.
 */
@Composable
fun ResultSection(state: PickerState) {
    val resultMessage =
        when (state) {
            is PickerState.Idle -> stringResource(R.string.status_none)
            is PickerState.Success ->
                stringResource(R.string.status_success, state.uri?.toString() ?: "null")
            is PickerState.Created ->
                stringResource(R.string.status_created, state.uri?.toString() ?: "null")
            is PickerState.Error -> state.message
            is PickerState.Cancelled -> stringResource(R.string.status_cancelled)
        }

    val resultColor =
        if (state is PickerState.Error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.result_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = resultMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = resultColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        TodoSection(stringResource(R.string.todo_render_result))
    }
}
