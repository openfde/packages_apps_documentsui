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
package com.android.documentsui.rules

import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.DocumentsProviderHelper
import com.android.documentsui.base.Providers
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import org.junit.Assert

/**
 * Rule that uses the DocumentsProvider interface to interact with ExternalStorageProvider to create
 * test files for a test.
 *
 * All test files are cleaned up at the end of the Rule's lifetime unless the caller explicitly
 * created them outside TEMPORARY_FILES_DIR_NAME (eg. via createTestFiles()).
 */
class ExternalStorageProviderTestFilesRule(private val skipCreation: Boolean = false) :
    TestFilesRule(skipCreation) {
    override fun createDocumentsProviderHelper(): DocumentsProviderHelper {
        return DocumentsProviderHelper(
            UserId.DEFAULT_USER,
            Providers.AUTHORITY_STORAGE,
            InstrumentationRegistry.getInstrumentation().context,
            Providers.AUTHORITY_STORAGE,
        )
    }

    override fun createDefault() {
        val root0: RootInfo = docsHelper.getRoot(Providers.ROOT_ID_DEVICE)

        // All files are stored under a single directory to make it easier to have a known starting
        // state (by deleting that directory).
        val temporaryDirectory = docsHelper.createFolder(root0, TEMPORARY_FILES_DIR_NAME)
        docsHelper.createDocument(temporaryDirectory, "image/png", FILE_NAME_2)
        docsHelper.createDocument(temporaryDirectory, "text/plain", FILE_NAME_4)
        docsHelper.createDocument(temporaryDirectory, "audio/mpeg", FILE_NAME_5)
        docsHelper.createDocument(temporaryDirectory, "video/mp4", FILE_NAME_6)
        docsHelper.createDocument(temporaryDirectory, "application/octet-stream", FILE_NAME_7)
        docsHelper.createDocument(temporaryDirectory, "application/zip", FILE_NAME_8)
    }

    override fun cleanupTemporaryFiles() {
        val deviceRoot = docsHelper.getRoot(Providers.ROOT_ID_DEVICE)
        val testFolder = docsHelper.findFile(deviceRoot.documentId, TEMPORARY_FILES_DIR_NAME)

        if (testFolder != null) {
            docsHelper.deleteDocument(testFolder.derivedUri)
        }
    }

    @JvmOverloads
    public fun createRandomFile(mimeType: String, parentDirectory: String? = null): String {
        val randomFileName = System.currentTimeMillis().toString()
        val root = docsHelper.getRoot(Providers.ROOT_ID_DEVICE)

        val testFolder =
            if (parentDirectory == null) {
                docsHelper.findFile(root.documentId, TEMPORARY_FILES_DIR_NAME)
            } else {
                docsHelper.findFile(root.documentId, parentDirectory)
            }

        Assert.assertNotNull("Could not find temporary folder to create random file", testFolder)
        docsHelper.createDocument(testFolder!!.derivedUri, mimeType, randomFileName)

        return randomFileName
    }

    companion object {
        @JvmField val TEMPORARY_FILES_DIR_NAME: String = "ESP_TEST_DIR"
    }
}
