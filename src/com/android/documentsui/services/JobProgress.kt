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

package com.android.documentsui.services

import android.content.Context
import android.icu.text.MessageFormat
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.text.BidiFormatter
import androidx.annotation.IdRes
import androidx.core.os.ParcelCompat
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.util.Locale

/**
 * Represents the current progress on an individual job owned by the FileOperationService.
 * JobProgress objects are broadcast from the service to activities in order to update the UI.
 */
data class JobProgress
@JvmOverloads
constructor(
    @JvmField val id: String,
    @JvmField @FileOperationService.OpType val operationType: Int,
    @JvmField @Job.State val state: Int,
    @JvmField val filename: String?,
    @JvmField val numFiles: Int,
    @JvmField val hasFailures: Boolean,
    @JvmField val failedDocs: ArrayList<DocumentInfo> = ArrayList(),
    @JvmField val failedUris: ArrayList<Uri> = ArrayList(),
    @JvmField val failedPaths: ArrayList<String> = ArrayList(),
    @JvmField val destination: DocumentStack? = null,
    @JvmField val currentBytes: Long = -1,
    @JvmField val requiredBytes: Long = -1,
    @JvmField val msRemaining: Long = -1,
) : Parcelable {

    val isIndeterminate
        get() =
            state == Job.STATE_SET_UP &&
                (currentBytes == -1L || requiredBytes == -1L || requiredBytes == 0L)

    fun toPercent(): Float =
        when (state) {
            Job.STATE_CREATED,
            Job.STATE_STARTED -> 0f
            Job.STATE_COMPLETED,
            Job.STATE_CANCELED -> 100f
            else -> 100f * currentBytes / requiredBytes
        }

    val isFinal
        get() =
            when (state) {
                Job.STATE_COMPLETED,
                Job.STATE_CANCELED -> true
                else -> false
            }

    @IdRes
    private fun getProgressMessageStringId(): Int {
        val plural = numFiles != 1 || filename.isNullOrEmpty()
        if (plural) {
            return when (operationType) {
                FileOperationService.OPERATION_COPY -> R.string.copy_in_progress
                FileOperationService.OPERATION_MOVE -> R.string.move_in_progress
                FileOperationService.OPERATION_DELETE -> R.string.delete_in_progress
                FileOperationService.OPERATION_COMPRESS -> R.string.compress_in_progress
                FileOperationService.OPERATION_EXTRACT,
                FileOperationService.OPERATION_UNPACK -> R.string.extract_in_progress
                FileOperationService.OPERATION_TRASH -> R.string.trash_in_progress
                FileOperationService.OPERATION_RESTORE -> R.string.restore_in_progress
                else -> 0
            }
        } else {
            return when (operationType) {
                FileOperationService.OPERATION_COPY -> R.string.copy_specific_file_in_progress
                FileOperationService.OPERATION_MOVE -> R.string.move_specific_file_in_progress
                FileOperationService.OPERATION_DELETE -> R.string.delete_specific_file_in_progress
                FileOperationService.OPERATION_COMPRESS ->
                    R.string.compress_specific_file_in_progress
                FileOperationService.OPERATION_EXTRACT,
                FileOperationService.OPERATION_UNPACK -> R.string.extract_specific_file_in_progress
                FileOperationService.OPERATION_TRASH -> R.string.trash_specific_file_in_progress
                FileOperationService.OPERATION_RESTORE -> R.string.restore_specific_file_in_progress
                else -> 0
            }
        }
    }

    fun getProgressMessage(context: Context): String {
        val args =
            mutableMapOf<String, Any?>(
                "filename" to filename,
                "count" to numFiles,
                "directory" to BidiFormatter.getInstance().unicodeWrap(destination?.title),
            )
        return MessageFormat(
                context.getString(getRes(getProgressMessageStringId())),
                Locale.getDefault(),
            )
            .format(args)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeString(id)
            writeInt(operationType)
            writeInt(state)
            writeString(filename)
            writeInt(numFiles)
            writeBoolean(hasFailures)
            writeTypedList(failedDocs)
            writeTypedList(failedUris)
            writeStringList(failedPaths)
            writeParcelable(destination, flags)
            writeLong(currentBytes)
            writeLong(requiredBytes)
            writeLong(msRemaining)
        }
    }

    companion object CREATOR : Parcelable.Creator<JobProgress> {
        override fun createFromParcel(parcel: Parcel): JobProgress {
            return JobProgress(
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readInt(),
                parcel.readString(),
                parcel.readInt(),
                parcel.readBoolean(),
                parcel.createTypedArrayList(DocumentInfo.CREATOR)!!,
                parcel.createTypedArrayList(Uri.CREATOR)!!,
                parcel.createStringArrayList()!!,
                ParcelCompat.readParcelable(
                    parcel,
                    DocumentStack::class.java.classLoader,
                    DocumentStack::class.java,
                ),
                parcel.readLong(),
                parcel.readLong(),
                parcel.readLong(),
            )
        }

        override fun newArray(size: Int): Array<JobProgress?> {
            return arrayOfNulls(size)
        }
    }
}
