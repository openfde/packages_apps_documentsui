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

package com.android.documentsui.services

import android.content.ContentProviderClient
import android.provider.DocumentsContract.buildDocumentUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.StubProvider
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.RootInfo
import com.android.documentsui.rules.TestFilesRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DocumentTraversalHelperTest {
    val context = InstrumentationRegistry.getInstrumentation().context
    lateinit var client: ContentProviderClient

    lateinit var root: RootInfo
    lateinit var rootFolder: DocumentInfo

    @get:Rule
    val testFilesRule =
        TestFilesRule().createTestFiles { docsHelper ->
            root = docsHelper.getRoot(StubProvider.ROOT_0_ID)
            val rootUri = buildDocumentUri(root.authority, root.documentId)
            client = context.contentResolver.acquireContentProviderClient(rootUri)!!
            rootFolder = DocumentInfo.fromUri(context.contentResolver, rootUri, root.userId)

            docsHelper.createDocument(root, "text/plain", "top_level.txt")
            docsHelper.createDocument(root, "image/png", "top_level.png")

            docsHelper.createFolder(root, "empty_dir")

            val dir1 = docsHelper.createFolder(root, "dir1")
            docsHelper.createDocument(dir1, "text/plain", "nested_file.log")
            docsHelper.createDocument(dir1, "image/png", "nested_file.png")
            val nestedDir = docsHelper.createFolder(dir1, "nested_dir")
            docsHelper.createDocument(nestedDir, "text/plain", "double_nested_file.txt")
        }

    @Test
    fun testRoot() = runTest {
        val helper = DocumentTraversalHelper(rootFolder, client, emptyArray(), context)
        val seen = arrayListOf<String>()
        helper.recursePostOrder().collect { (current, parent) ->
            val currentUri = current.derivedUri.toString()

            if (parent == null) {
                // Parent should only be null for the root folder.
                assertThat(currentUri).isEqualTo(rootFolder.derivedUri.toString())
            } else {
                assertThat(currentUri.startsWith(parent.derivedUri.toString())).isTrue()
            }

            // Check none of the paths we've seen is a descendant of the current uri.
            assertThat(seen.any { pastUri -> currentUri.startsWith(pastUri) }).isFalse()
            seen.add(currentUri)
        }
        assertThat(seen.size).isEqualTo(9)
    }

    @Test
    fun testEmptyDir() = runTest {
        val emptyDir = testFilesRule.docsHelper.findDocument(root.documentId, "empty_dir")
        val helper = DocumentTraversalHelper(emptyDir, client, emptyArray(), context)
        val descendants = helper.recursePostOrder().toList()
        assertThat(descendants.size).isEqualTo(1)
        assertThat(descendants[0].component1().derivedUri).isEqualTo(emptyDir.derivedUri)
    }

    @Test
    fun testFile() = runTest {
        val file = testFilesRule.docsHelper.findDocument(root.documentId, "top_level.txt")
        val helper = DocumentTraversalHelper(file, client, emptyArray(), context)
        val descendants = helper.recursePostOrder().toList()
        assertThat(descendants.size).isEqualTo(1)
        assertThat(descendants[0].component1().derivedUri).isEqualTo(file.derivedUri)
    }
}
