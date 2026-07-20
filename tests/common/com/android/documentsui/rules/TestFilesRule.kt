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

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.DocumentsProviderHelper
import com.android.documentsui.StubProvider
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import org.junit.rules.ExternalResource

/**
 * Rule that uses the DocumentsProvider interface to create test files for a test.
 *
 * The class uses StubProvider (which stores its files within its private app cache) as the backing
 * DocumentsProvider. Use ExternalStorageProviderTestFilesRule to create the test files in the
 * user's profile directory on the device.
 *
 * Note that for StubProvider, test files created are not automatically cleaned up: that's left up
 * to the test.
 *
 * When `skipCreation` is false, this essentially falls back to providing a `docsHelper`.
 */
open class TestFilesRule(private val skipCreation: Boolean = false) : ExternalResource() {
    // Only needed so that the file creation function could throw.
    fun interface CreateFilesFunction {
        @Throws(Exception::class) fun apply(helper: DocumentsProviderHelper)
    }

    lateinit var docsHelper: DocumentsProviderHelper

    // The creation operations are deferred as the rules are instantiated prior to the environment
    // being ready.
    private val deferredOperations = mutableListOf<() -> Unit>()

    open fun createDocumentsProviderHelper(): DocumentsProviderHelper {
        val helper =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                StubProvider.DEFAULT_AUTHORITY,
                InstrumentationRegistry.getInstrumentation().context,
                StubProvider.DEFAULT_AUTHORITY,
            )

        helper.clear(null, null)
        helper.configure(null, Bundle.EMPTY)

        return helper
    }

    // Sub-classes can implement this to ensure any temporary files don't remain between tests.
    open fun cleanupTemporaryFiles() {}

    override fun before() {
        docsHelper = createDocumentsProviderHelper()
        cleanupTemporaryFiles()

        if (skipCreation) {
            require(deferredOperations.isEmpty()) {
                "Have deferred operations yet requested to skip creation."
            }
            return
        }

        if (deferredOperations.isEmpty()) {
            createDefault()
        } else {
            deferredOperations.forEach { it() }
        }
    }

    /**
     * Run file/folder create operations encapsulated in a provided function. Technically this lets
     * running any DocumentsProviderHelper functions, but should only be used to create files.
     */
    fun createTestFiles(createTestFiles: CreateFilesFunction): TestFilesRule {
        deferredOperations.add { createTestFiles.apply(docsHelper) }
        return this
    }

    /** Returns `Uri` of the file with `filename` within the `root`. */
    fun getUriInRoot(root: String, fileName: String): Uri? {
        return docsHelper.findDocument(getRoot(root).documentId, fileName).derivedUri
    }

    /** Returns`RootInfo` for the for the specified `root`. */
    fun getRoot(root: String): RootInfo {
        return docsHelper.getRoot(root)
    }

    /** Creates a default set of files for testing. */
    open fun createDefault() {
        val root0: RootInfo = docsHelper.getRoot(StubProvider.ROOT_0_ID)
        val root1: RootInfo = docsHelper.getRoot(StubProvider.ROOT_1_ID)

        docsHelper.createFolder(root0, DIR_NAME_1)
        docsHelper.createDocument(root0, "text/plain", FILE_NAME_1)
        docsHelper.createDocument(root0, "image/png", FILE_NAME_2)
        docsHelper.createDocumentWithFlags(
            root0.documentId,
            "text/plain",
            FILE_NAME_NO_RENAME,
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
        )

        docsHelper.createDocument(root1, "text/plain", FILE_NAME_3)
        docsHelper.createDocument(root1, "text/plain", FILE_NAME_4)
    }

    override fun after() {
        cleanupTemporaryFiles()
        docsHelper.cleanUp()
    }

    companion object {
        @JvmField val DIR_NAME_1: String = "Dir1"

        @JvmField val CHILD_DIR_1: String = "ChildDir1"

        @JvmField val FILE_NAME_1: String = "file1.log"

        @JvmField val FILE_NAME_2: String = "file12.png"

        @JvmField val FILE_NAME_3: String = "anotherFile0.log"

        @JvmField val FILE_NAME_4: String = "poodles.text"

        @JvmField val FILE_NAME_5: String = "audio.mp3"

        @JvmField val FILE_NAME_6: String = "video.mp4"

        @JvmField val FILE_NAME_7: String = "random.exe"

        @JvmField val FILE_NAME_8: String = "test.zip"

        @JvmField val FILE_NAME_NO_RENAME: String = "NO_RENAMEfile.txt"

        @JvmField val HIDDEN_FILE_NAME: String = ".txt"

        @JvmField val INVALID_FILE_NAME: String = "inva/*d.txt"
    }
}
