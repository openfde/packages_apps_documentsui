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

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import com.android.documentsui.R
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.util.Locale

/** A group of helper functions used to format the file metadata in the Get info dialog. */
object DefaultInfoFormatter {
    fun formatFileSize(context: Context, size: Long): String {
        return Formatter.formatFileSize(context, size)
    }

    fun formatDate(context: Context, date: Long): String {
        val res = context.resources
        val formatRes =
            if (DateFormat.is24HourFormat(context)) {
                getRes(R.string.datetime_format_24)
            } else {
                getRes(R.string.datetime_format_12)
            }
        val format =
            DateFormat.getBestDateTimePattern(Locale.getDefault(), res.getString(formatRes))
        return DateFormat.format(format, date).toString()
    }
}
