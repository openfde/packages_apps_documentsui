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
import android.media.MediaMetadata
import android.os.Bundle
import android.text.format.DateUtils
import com.android.documentsui.R

/** Helper functions to parse audio metadata. */
object AudioUtils {
    fun parseAudioData(context: Context, tags: Bundle): List<ListItem> = buildList {
        tags
            .getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(SharedUtils.createInfo(context, R.string.metadata_artist, it)) }

        tags
            .getString(MediaMetadata.METADATA_KEY_COMPOSER)
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(SharedUtils.createInfo(context, R.string.metadata_composer, it)) }

        tags
            .getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(SharedUtils.createInfo(context, R.string.metadata_album, it)) }

        // Duration is generally stored as a Long, however, it could also be stored as an Integer.
        // Try Long first but fallback to Int if that doesn't give reliable data.
        var millis = tags.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (millis == 0L) {
            millis = tags.getInt(MediaMetadata.METADATA_KEY_DURATION).toLong()
        }

        if (millis > 0) {
            add(
                SharedUtils.createInfo(
                    context,
                    R.string.metadata_duration,
                    DateUtils.formatElapsedTime(millis / 1000L), // formatElapsedTime takes seconds
                )
            )
        }
    }
}
