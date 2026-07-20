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
package com.android.documentsui.ui

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_FAILURE
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS
import com.android.documentsui.services.FileOperationService.OPERATION_COPY
import com.android.documentsui.services.FileOperationService.OPERATION_DELETE
import com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT
import com.android.documentsui.services.FileOperationService.OPERATION_MOVE
import com.android.documentsui.services.FileOperationService.OPERATION_RESTORE
import com.android.documentsui.services.FileOperationService.OPERATION_TRASH
import com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class MessageBuilderTest() {
    @get:Rule val setFlags = OverrideFlagsRule()

    private val context =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private val messageBuilder = MessageBuilder(context)

    @Test
    fun generateDeleteMessage() {
        val isInTrash = false
        assertThat(messageBuilder.generateDeleteMessage(listOf(file("File")), isInTrash))
            .isEqualTo("“File” will be deleted forever. This can’t be undone.")
        assertThat(messageBuilder.generateDeleteMessage(listOf(directory("Dir")), isInTrash))
            .isEqualTo("“Dir” will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(file("File 1"), file("File 2")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(directory("Directory 1"), directory("Directory 2")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(file("File 1"), directory("Directory 1")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items will be deleted forever. This can’t be undone.")
    }

    @Test
    fun generateInTrashItemsDeleteMessage() {
        val isInTrash = true
        assertThat(messageBuilder.generateDeleteMessage(listOf(file("File")), isInTrash))
            .isEqualTo("“File” will be deleted forever. This can’t be undone.")
        assertThat(messageBuilder.generateDeleteMessage(listOf(directory("Dir")), isInTrash))
            .isEqualTo("“Dir” will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(file("File 1"), file("File 2")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items in trash will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(directory("Directory 1"), directory("Directory 2")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items in trash will be deleted forever. This can’t be undone.")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(file("File 1"), directory("Directory 1")),
                    isInTrash,
                )
            )
            .isEqualTo("2 items in trash will be deleted forever. This can’t be undone.")
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO)
    fun generateListMessage() {
        data class Params(
            val dialog: Int,
            val op: Int,
            val titleWant1: String,
            val titleWant2: String,
        )
        val params =
            listOf(
                Params(
                    dialog = DIALOG_TYPE_CONVERTED,
                    op = OPERATION_UNKNOWN,
                    titleWant1 = "This file was converted to another format:",
                    titleWant2 = "These files were converted to another format:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COPY,
                    titleWant1 = "This file wasn’t copied:",
                    titleWant2 = "These files weren’t copied:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_MOVE,
                    titleWant1 = "This file wasn’t moved:",
                    titleWant2 = "These files weren’t moved:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COMPRESS,
                    titleWant1 = "This file wasn’t zipped:",
                    titleWant2 = "These files weren’t zipped:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_EXTRACT,
                    titleWant1 = "This file wasn’t extracted:",
                    titleWant2 = "These files weren’t extracted:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_DELETE,
                    titleWant1 = "This file wasn’t deleted:",
                    titleWant2 = "These files weren’t deleted:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_TRASH,
                    titleWant1 = "This file wasn’t trashed:",
                    titleWant2 = "These files weren’t trashed:",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_RESTORE,
                    titleWant1 = "This file wasn’t restored:",
                    titleWant2 = "These files weren’t restored:",
                ),
            )

        val expectedMessage1 = "<p>&#8226; &#34;File 1&#34;<br></p>"
        val expectedMessage2 =
            "<p>&#8226; &#34;File 1&#34;<br>" +
                "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                "&#8226; &#34;File 3&#34;<br></p>"

        for (p in params) {
            val listDialogContent1 =
                messageBuilder.generateListDialogContent(
                    p.dialog,
                    p.op,
                    listOf(file("File 1")),
                    null,
                    null,
                )

            assertThat(listDialogContent1.title).isEqualTo(p.titleWant1)
            assertThat(listDialogContent1.message).isEqualTo(expectedMessage1)

            val listDialogContent2 =
                messageBuilder.generateListDialogContent(
                    p.dialog,
                    p.op,
                    listOf(file("File 1")),
                    listOf("content://random-uri/File+2".toUri()),
                    listOf("/Dir/File 3"),
                )

            assertThat(listDialogContent2.title).isEqualTo(p.titleWant2)
            assertThat(listDialogContent2.message).isEqualTo(expectedMessage2)
        }
    }

    @Test
    @EnableFlags(FLAG_ZIP_NG_RO)
    fun getListContent() {
        assertThat(MessageBuilder.getListContent(listOf(file("File 1")), null, null))
            .isEqualTo("<p>&#8226; &#34;File 1&#34;<br></p>")

        assertThat(
                MessageBuilder.getListContent(
                    listOf(file("File 1")),
                    listOf("content://random-uri/File+2".toUri()),
                    listOf("/Dir/File 3"),
                )
            )
            .isEqualTo(
                "<p>&#8226; &#34;File 1&#34;<br>" +
                    "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                    "&#8226; &#34;File 3&#34;<br></p>"
            )
    }

    @Test
    @DisableFlags(FLAG_ZIP_NG_RO)
    fun generateListMessageOld() {
        data class Params(
            val dialog: Int,
            val op: Int,
            val titleWant1: String? = null,
            val titleWant2: String? = null,
            val messageWant1: String,
            val messageWant2: String,
        )
        val params =
            listOf(
                Params(
                    dialog = DIALOG_TYPE_CONVERTED,
                    op = OPERATION_UNKNOWN,
                    messageWant1 =
                        "This file was converted to another format: " +
                            "<p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files were converted to another format: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COPY,
                    messageWant1 = "This file wasn’t copied: <p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files weren’t copied: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_MOVE,
                    messageWant1 = "This file wasn’t moved: <p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files weren’t moved: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COMPRESS,
                    messageWant1 =
                        "This file wasn’t compressed: <p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files weren’t compressed: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_EXTRACT,
                    messageWant1 =
                        "This file wasn’t extracted: <p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files weren’t extracted: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_DELETE,
                    messageWant1 = "This file wasn’t deleted: <p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "These files weren’t deleted: " +
                            "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_TRASH,
                    titleWant1 = "This file wasn’t trashed:",
                    titleWant2 = "These files weren’t trashed:",
                    messageWant1 = "<p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_RESTORE,
                    titleWant1 = "This file wasn’t restored:",
                    titleWant2 = "These files weren’t restored:",
                    messageWant1 = "<p>&#8226; &#34;File 1&#34;<br></p>",
                    messageWant2 =
                        "<p>&#8226; &#34;File 1&#34;<br>" +
                            "&#8226; &#34;content://random-uri/File+2&#34;<br>" +
                            "&#8226; &#34;File 3&#34;<br></p>",
                ),
            )

        for (p in params) {
            val listDialogContent1 =
                messageBuilder.generateListDialogContent(
                    p.dialog,
                    p.op,
                    listOf(file("File 1")),
                    null,
                    null,
                )

            assertThat(listDialogContent1.title).isEqualTo(p.titleWant1)
            assertThat(listDialogContent1.message).isEqualTo(p.messageWant1)

            val listDialogContent2 =
                messageBuilder.generateListDialogContent(
                    p.dialog,
                    p.op,
                    listOf(file("File 1")),
                    listOf("content://random-uri/File+2".toUri()),
                    listOf("/Dir/File 3"),
                )

            assertThat(listDialogContent2.title).isEqualTo(p.titleWant2)
            assertThat(listDialogContent2.message).isEqualTo(p.messageWant2)
        }
    }
}

fun file(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    return doc
}

fun directory(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    doc.mimeType = MIME_TYPE_DIR
    return doc
}
