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

package com.android.documentsui.demoapp.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.documentsui.demoapp.R
import com.android.documentsui.demoapp.components.NoteSection
import com.android.documentsui.demoapp.components.ResultSection
import com.android.documentsui.demoapp.components.TodoSection
import com.android.documentsui.demoapp.models.PickerState
import com.android.documentsui.demoapp.models.PickerStateSaver
import com.android.documentsui.demoapp.utils.rememberDocumentLauncher

private const val AUTHORITY_MEDIA = "com.android.providers.media.documents"

/**
 * A screen that demonstrates using the Media Documents Provider (MDP) for picking different types
 * of media.
 *
 * This screen provides buttons to launch the system file picker specifically targeting images,
 * audio, and video files using the `com.android.providers.media.documents` authority. It displays
 * the result of the picker operation using [ResultSection].
 *
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun MdpScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pickerState by
        rememberSaveable(stateSaver = PickerStateSaver) { mutableStateOf(PickerState.Idle) }

    val openDocumentLauncher = rememberDocumentLauncher { state -> pickerState = state }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        NoteSection(stringResource(R.string.note_mdp))

        TodoSection(stringResource(R.string.todo_config_options))

        Button(
            onClick = {
                val uri = DocumentsContract.buildDocumentUri(AUTHORITY_MEDIA, "images_root")
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    }
                try {
                    openDocumentLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    pickerState =
                        PickerState.Error(
                            "${context.getString(R.string.error_no_activity)}\n${e.message}"
                        )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_pick_mdp_images))
        }

        Button(
            onClick = {
                val uri = DocumentsContract.buildDocumentUri(AUTHORITY_MEDIA, "audio_root")
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    }
                try {
                    openDocumentLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    pickerState =
                        PickerState.Error(
                            "${context.getString(R.string.error_no_activity)}\n${e.message}"
                        )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_pick_mdp_audio))
        }

        Button(
            onClick = {
                val uri = DocumentsContract.buildDocumentUri(AUTHORITY_MEDIA, "videos_root")
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "video/*"
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    }
                try {
                    openDocumentLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    pickerState =
                        PickerState.Error(
                            "${context.getString(R.string.error_no_activity)}\n${e.message}"
                        )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_pick_mdp_videos))
        }

        ResultSection(pickerState)
    }
}
