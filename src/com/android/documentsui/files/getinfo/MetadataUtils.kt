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

package com.android.documentsui.files.getinfo

import android.content.Context
import android.os.Bundle
import com.android.documentsui.base.Shared

/**
 * Helper functions to parse file metadata (EXIF, Audio, Video) from a Bundle returned from a
 * getDocumentMetadata DocumentsProvider call.
 */
object MetadataUtils {
    suspend fun parseMetadata(context: Context, metadata: Bundle): List<ListItem> = buildList {
        // TODO(b/458494432): Parse EXIF.

        val video = metadata.getBundle(Shared.METADATA_KEY_VIDEO)
        if (video != null) {
            addAll(VideoUtils.parseVideoData(context, video))
        }

        val audio = metadata.getBundle(Shared.METADATA_KEY_AUDIO)
        if (audio != null) {
            addAll(AudioUtils.parseAudioData(context, audio))
        }
    }
}
