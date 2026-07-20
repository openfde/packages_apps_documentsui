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

package com.android.documentsui.dirlist

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.test.mock.MockContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.ModelId
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.rules.MainDispatcherRule
import com.android.documentsui.testing.TestDocumentsProvider
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestProvidersAccess
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class SummariesViewModelTest {
    lateinit var environment: TestEnv
    private val rootInfo: RootInfo = TestProvidersAccess.DOWNLOADS
    private val testDispatcher = StandardTestDispatcher()
    private val ioTestDispatcher = UnconfinedTestDispatcher(testDispatcher.scheduler)
    @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    /** Test provider that provides summaries. */
    private lateinit var summaryProvider: TestDocumentsProvider
    private lateinit var contentResolver: MockContentResolver
    private lateinit var mockApp: Application
    private val authority = rootInfo.authority
    private val authorityUri = rootInfo.root.uri
    private val parentDoc =
        DocumentInfo().apply {
            this.authority = this@SummariesViewModelTest.authority
            documentId = "parentDocId"
        }

    @Before
    fun setUp() {
        environment = TestEnv.create(rootInfo.authority)
        summaryProvider = environment.mockProviders[authority]!!
        summaryProvider.lastQueryArgs = null
        contentResolver = environment.contentResolver

        mockApp = mock(Application::class.java)
        `when`(mockApp.contentResolver).thenReturn(contentResolver)
        `when`(mockApp.applicationContext).thenReturn(mockApp)
    }

    /** Helper to create model Ids from document IDs. */
    private fun createModelId(docId: String): String {
        return ModelId.build(UserId.DEFAULT_USER, authority, docId)!!
    }

    @Test
    fun testUpdate_fetchesCorrectSummaries() =
        runTest(testDispatcher) {
            val childDocNames = listOf("doc1", "doc2", "doc3")
            // Sets up the docs to be returned by queryChildDocuments.
            val childDocs =
                childDocNames
                    .map { name ->
                        environment.model.createFile("$name.txt").apply {
                            this.authority = this@SummariesViewModelTest.authority
                        }
                    }
                    .toTypedArray()
            val (doc1, _, doc3) = childDocs
            val modelIds = childDocs.map { createModelId(it.documentId) }
            val expectedSummaries =
                mapOf(
                    doc1.documentId to "Summary for doc1.",
                    doc3.documentId to "Summary for doc3.",
                )

            summaryProvider.setDocumentSummaries(expectedSummaries)
            summaryProvider.setNextChildDocumentsReturns(*childDocs)

            val viewModel = SummariesViewModel(mockApp, ioTestDispatcher)
            viewModel.update(authorityUri, parentDoc, modelIds, null, null)

            // Wait for the flow to emit the first non-empty result.
            // In this case, since we use UnconfinedTestDispatcher for IO, it should be fast.
            val result = viewModel.summaries.first { it.isNotEmpty() }

            assertThat(result).isNotNull()
            assertThat(result).hasSize(2)
            assertThat(result).containsEntry(createModelId(doc1.documentId), "Summary for doc1.")
            assertThat(result).containsEntry(createModelId(doc3.documentId), "Summary for doc3.")
            assertThat(result).doesNotContainKey(createModelId("doc2"))

            // Check that query args were passed
            val queryArgs = summaryProvider.lastQueryArgs
            assertThat(queryArgs).isNotNull()
            val extraUri = queryArgs?.getParcelable<Uri>("uri")

            assertThat(extraUri)
                .isEqualTo(DocumentsContract.buildDocumentUri(authority, parentDoc.documentId))
        }

    @Test
    fun testUpdate_whenProviderThrowsException_returnsEmptyMap() =
        runTest(testDispatcher) {
            val modelIds = listOf(createModelId("doc1"))
            summaryProvider.setThrownRuntimeMessage("Faked failure")

            val viewModel = SummariesViewModel(mockApp, ioTestDispatcher)

            // Collect the flow in the background to trigger the query.
            backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
                viewModel.summaries.collect {}
            }

            viewModel.update(authorityUri, parentDoc, modelIds, null, null)

            // Ensure all coroutines have run.
            testDispatcher.scheduler.advanceUntilIdle()

            // The initial value is empty, and it should stay empty on failure.
            // But now we ensured that the query was actually attempted.
            val result = viewModel.summaries.value

            assertThat(result).isNotNull()
            assertThat(result).isEmpty()
            assertThat(summaryProvider.lastQueryArgs).isNotNull()
        }

    @Test
    fun testUpdate_reactiveUpdate() =
        runTest(testDispatcher, timeout = 5.seconds) {
            val childDocs =
                arrayOf(
                    environment.model.createFile("doc1.txt").apply {
                        this.authority = this@SummariesViewModelTest.authority
                    }
                )
            val doc1 = childDocs[0]
            val modelIds = listOf(createModelId(doc1.documentId))

            summaryProvider.setDocumentSummaries(mapOf(doc1.documentId to "Initial Summary"))
            summaryProvider.setNextChildDocumentsReturns(*childDocs)

            val viewModel = SummariesViewModel(mockApp, ioTestDispatcher)

            // Keep a persistent subscription.
            backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
                viewModel.summaries.collect {}
            }

            viewModel.update(authorityUri, parentDoc, modelIds, null, null)

            // Wait for initial update.
            viewModel.summaries.first { it.isNotEmpty() }
            assertThat(viewModel.summaries.value)
                .containsEntry(createModelId(doc1.documentId), "Initial Summary")

            // Trigger a reactive update.
            summaryProvider.setDocumentSummaries(mapOf(doc1.documentId to "Updated Summary"))
            summaryProvider.dispatchContentChanged()

            val modelId = createModelId(doc1.documentId)
            viewModel.summaries.first {
                it.containsKey(modelId) && it[modelId] == "Updated Summary"
            }
        }

    @Test
    fun testUpdate_whenProviderThrowsExceptionOnReactiveUpdate_returnsEmptyMap() =
        runTest(testDispatcher) {
            val childDocs =
                arrayOf(
                    environment.model.createFile("doc1.txt").apply {
                        this.authority = this@SummariesViewModelTest.authority
                    }
                )
            val doc1 = childDocs[0]
            val modelIds = listOf(createModelId(doc1.documentId))

            summaryProvider.setDocumentSummaries(mapOf(doc1.documentId to "Initial Summary"))
            summaryProvider.setNextChildDocumentsReturns(*childDocs)

            val viewModel = SummariesViewModel(mockApp, ioTestDispatcher)

            // Keep a persistent subscription.
            backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
                viewModel.summaries.collect {}
            }

            viewModel.update(authorityUri, parentDoc, modelIds, null, null)

            // Wait for initial update.
            viewModel.summaries.first { it.isNotEmpty() }

            // Set provider to throw for the next query.
            summaryProvider.setThrownRuntimeMessage("Faked failure on reactive update")
            summaryProvider.dispatchContentChanged()

            testDispatcher.scheduler.advanceUntilIdle()

            // It should have emitted an empty map (or whatever the catch block returns).
            // Actually, performQuery returns emptyMap() on catch.
            assertThat(viewModel.summaries.value).isEmpty()
        }
}
