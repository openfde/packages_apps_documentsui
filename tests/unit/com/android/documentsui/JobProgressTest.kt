/*
 * Copyright 2026 The Android Open Source Project
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

import android.icu.text.MessageFormat
import android.platform.test.annotations.EnableFlags
import androidx.core.net.toUri
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.RootInfo
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.MutableJobProgress
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

data class JobProgressTestParams(
    val typeName: String,
    @FileOperationService.OpType val operationType: Int,
    val messageId: Int,
    val pluralMessageId: Int,
) {
    override fun toString() = typeName
}

@SmallTest
@RunWith(Parameterized::class)
class JobProgressTest(private val testParams: JobProgressTestParams) {
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()
    private var context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testRoot =
        RootInfo().apply {
            title = "root"
            authority = "root-authority"
            documentId = "root-doc-id"
            rootId = "root-root-id"
        }

    private val testDoc = DocumentInfo().apply { derivedUri = testRoot.documentUri }

    private val rootStack = DocumentStack(testRoot, testDoc)

    private fun getIcuString(stringId: Int, formatArgs: Map<String, Any>): String {
        return MessageFormat(context.getString(stringId), Locale.getDefault()).format(formatArgs)
    }

    @Test
    fun testSingleProgressMessage() {
        val progress =
            MutableJobProgress(
                id = "id",
                operationType = testParams.operationType,
                state = Job.STATE_SET_UP,
                filename = "file.txt",
                numFiles = 1,
                hasFailures = false,
                destination = rootStack,
            )

        assertThat(progress.toJobProgress().getProgressMessage(context))
            .isEqualTo(
                getIcuString(
                    testParams.messageId,
                    mapOf("filename" to "file.txt", "directory" to "root"),
                )
            )
    }

    @Test
    fun testPluralProgressMessage() {
        val progress =
            MutableJobProgress(
                id = "id",
                operationType = testParams.operationType,
                state = Job.STATE_SET_UP,
                filename = "file.txt",
                numFiles = 3,
                hasFailures = false,
                destination = rootStack,
            )

        assertThat(progress.toJobProgress().getProgressMessage(context))
            .isEqualTo(
                getIcuString(testParams.pluralMessageId, mapOf("count" to 3, "directory" to "root"))
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.FLAG_USE_MATERIAL3)
    fun testSingleProgressMessageToShortcut() {
        val docInfo =
            DocumentInfo().apply {
                derivedUri = "shortcut-uri".toUri()
                displayName = "shortcut"
            }
        val docStack = DocumentStack(testRoot, docInfo)
        val progress =
            MutableJobProgress(
                id = "id",
                operationType = testParams.operationType,
                state = Job.STATE_SET_UP,
                filename = "file.txt",
                numFiles = 1,
                hasFailures = false,
                destination = docStack,
            )

        assertThat(progress.toJobProgress().getProgressMessage(context))
            .isEqualTo(
                getIcuString(
                    testParams.messageId,
                    mapOf("filename" to "file.txt", "directory" to "shortcut"),
                )
            )
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data() =
            listOf(
                JobProgressTestParams(
                    "copy",
                    FileOperationService.OPERATION_COPY,
                    R.string.copy_specific_file_in_progress,
                    R.string.copy_in_progress,
                ),
                JobProgressTestParams(
                    "move",
                    FileOperationService.OPERATION_MOVE,
                    R.string.move_specific_file_in_progress,
                    R.string.move_in_progress,
                ),
                JobProgressTestParams(
                    "delete",
                    FileOperationService.OPERATION_DELETE,
                    R.string.delete_specific_file_in_progress,
                    R.string.delete_in_progress,
                ),
                JobProgressTestParams(
                    "compress",
                    FileOperationService.OPERATION_COMPRESS,
                    R.string.compress_specific_file_in_progress,
                    R.string.compress_in_progress,
                ),
                JobProgressTestParams(
                    "extract",
                    FileOperationService.OPERATION_EXTRACT,
                    R.string.extract_specific_file_in_progress,
                    R.string.extract_in_progress,
                ),
                JobProgressTestParams(
                    "unpack",
                    FileOperationService.OPERATION_UNPACK,
                    R.string.extract_specific_file_in_progress,
                    R.string.extract_in_progress,
                ),
                JobProgressTestParams(
                    "trash",
                    FileOperationService.OPERATION_TRASH,
                    R.string.trash_specific_file_in_progress,
                    R.string.trash_in_progress,
                ),
                JobProgressTestParams(
                    "restore",
                    FileOperationService.OPERATION_RESTORE,
                    R.string.restore_specific_file_in_progress,
                    R.string.restore_in_progress,
                ),
            )
    }
}
