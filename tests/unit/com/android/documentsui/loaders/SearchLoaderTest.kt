/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui.loaders

import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.provider.Flags.FLAG_ENABLE_SYNC_STATE
import androidx.core.os.BundleCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.Model
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.RootInfo
import com.android.documentsui.flags.Flags.FLAG_CLOUD_FEATURES
import com.android.documentsui.flags.Flags.FLAG_USE_LOCAL_SEARCH_PROVIDER
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.testing.TestFeatures
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import com.android.documentsui.util.FlagUtils
import com.google.common.truth.Expect
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever

private const val TOTAL_FILE_COUNT = 8

fun createQueryArgs(vararg mimeTypes: String): Bundle {
    val args = Bundle()
    args.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, arrayOf(*mimeTypes))
    return args
}

data class SemanticSearchProviderTestParams(
    val testName: String,
    val flagEnabled: Boolean,
    val resourceUri: String,
    val semanticSearchError: Boolean,
    val expectedSemanticSearch: Boolean,
    val flagsToAdd: Int = 0,
    val flagsToRemove: Int = 0,
) {
    val expectedDisplayName: String =
        if (expectedSemanticSearch) "found-me-on-semantic-search" else "found-me-on-downloads"

    override fun toString(): String = testName
}

@RunWith(Enclosed::class)
class SearchLoaderTest {

    // Collection of tests that are parametrized by query, duration, and MIME type.
    @RunWith(Parameterized::class)
    @SmallTest
    class ParametrizedTests(private val testParams: LoaderTestParams) : BaseLoaderTest() {
        lateinit var executor: ExecutorService
        val contentLock = ContentLock()
        val contentObserver = LockingContentObserver(contentLock) {}

        companion object {
            @JvmStatic
            @Parameters(name = "with parameters {0}")
            fun data() =
                listOf(
                    // Specifying ALL_RESULTS as the limit should return as many results as the root
                    // allows,
                    // for TestDocumentsProvider this is 23 (to match FileSystemProvider's legacy
                    // default),
                    // which our TOTAL_FILE_COUNT is well below (so expect all files to be
                    // returned).
                    LoaderTestParams(
                        TOTAL_FILE_COUNT,
                        "sample",
                        null,
                        ALL_RESULTS,
                        Bundle(),
                        TOTAL_FILE_COUNT,
                    ),
                    // Specifying a positive, non-zero search result limit should work.
                    LoaderTestParams(TOTAL_FILE_COUNT, "sample", null, 2, Bundle(), 2),
                    // Specifying a zero search results limit should return zero files.
                    LoaderTestParams(TOTAL_FILE_COUNT, "sample", null, 0, Bundle(), 0),
                    LoaderTestParams(TOTAL_FILE_COUNT, "txt", null, ALL_RESULTS, Bundle(), 2),
                    LoaderTestParams(TOTAL_FILE_COUNT, "foozig", null, ALL_RESULTS, Bundle(), 0),
                    // The first file is at NOW, the second at NOW - 1h; expect 2.
                    LoaderTestParams(
                        TOTAL_FILE_COUNT,
                        "sample",
                        Duration.ofMinutes(60 + 1),
                        ALL_RESULTS,
                        Bundle(),
                        2,
                    ),
                    LoaderTestParams(
                        TOTAL_FILE_COUNT,
                        "sample",
                        null,
                        ALL_RESULTS,
                        createQueryArgs("image/*"),
                        2,
                    ),
                    LoaderTestParams(
                        TOTAL_FILE_COUNT,
                        "sample",
                        null,
                        ALL_RESULTS,
                        createQueryArgs("image/*", "video/*"),
                        6,
                    ),
                    LoaderTestParams(
                        TOTAL_FILE_COUNT,
                        "sample",
                        null,
                        ALL_RESULTS,
                        createQueryArgs("application/pdf"),
                        0,
                    ),
                )
        }

        @get:Rule val setFlags = OverrideFlagsRule()

        @get:Rule val expect: Expect = Expect.create()

        @Before
        fun setUpTest() {
            executor = Executors.newSingleThreadExecutor()
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testLoadInBackground() {
            val mockProvider = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
            val docs = createDocuments(testParams.fakeFileCount)
            mockProvider!!.setNextChildDocumentsReturns(*docs)
            val queryOptions =
                QueryOptions(
                    testParams.fakeFileCount + 1,
                    testParams.maxResultsPerRoot,
                    testParams.lastModifiedDelta,
                    null,
                    true,
                    arrayOf("*/*"),
                    testParams.otherArgs,
                )

            val rootInfoList = listOf(TestProvidersAccess.DOWNLOADS)

            val loader =
                SearchLoader(
                    activity,
                    rootInfoList,
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    testParams.query,
                    queryOptions,
                    environment.state.sortModel,
                    executor,
                )
            val directoryResult = loader.loadInBackground()
            expect.that(getFileCount(directoryResult)).isEqualTo(testParams.expectedCount)
        }
    }

    // Collection of semantic search provider tests.
    @RunWith(Parameterized::class)
    @SmallTest
    class SemanticSearchProviderTests(private val testParams: SemanticSearchProviderTestParams) :
        BaseLoaderTest() {
        @get:Rule val setFlags = OverrideFlagsRule()

        @get:Rule val expect: Expect = Expect.create()

        lateinit var executor: ExecutorService
        val contentLock = ContentLock()
        val contentObserver = LockingContentObserver(contentLock) {}

        companion object {
            private val SEMANTIC_SEARCH_PROVIDER: RootInfo = TestProvidersAccess.LOCAL_SEARCH

            @JvmStatic
            @Parameters(name = "{0}")
            fun data(): Collection<SemanticSearchProviderTestParams> =
                listOf(
                    SemanticSearchProviderTestParams(
                        testName = "happy_path_should_use_semantic_search_provider",
                        flagEnabled = true,
                        resourceUri = SEMANTIC_SEARCH_PROVIDER.uri.toString(),
                        semanticSearchError = false,
                        expectedSemanticSearch = true,
                    ),
                    SemanticSearchProviderTestParams(
                        testName = "flag_disabled_should_fall_back_to_default",
                        flagEnabled = false,
                        resourceUri = SEMANTIC_SEARCH_PROVIDER.uri.toString(),
                        semanticSearchError = false,
                        expectedSemanticSearch = false,
                    ),
                    SemanticSearchProviderTestParams(
                        testName = "semantic_search_unsupported_should_fall_back_to_default",
                        flagEnabled = true,
                        resourceUri = SEMANTIC_SEARCH_PROVIDER.uri.toString(),
                        semanticSearchError = false,
                        expectedSemanticSearch = false,
                        flagsToRemove = DocumentsContract.Root.FLAG_SUPPORTS_SEARCH,
                    ),
                    SemanticSearchProviderTestParams(
                        testName = "semantic_search_empty_should_fall_back_to_default",
                        flagEnabled = true,
                        resourceUri = SEMANTIC_SEARCH_PROVIDER.uri.toString(),
                        semanticSearchError = false,
                        expectedSemanticSearch = false,
                        flagsToAdd = DocumentsContract.Root.FLAG_EMPTY,
                    ),
                    SemanticSearchProviderTestParams(
                        testName = "semantic_search_fails_should_fall_back_to_default",
                        flagEnabled = true,
                        resourceUri = SEMANTIC_SEARCH_PROVIDER.uri.toString(),
                        semanticSearchError = true,
                        expectedSemanticSearch = false,
                    ),
                )
        }

        private var originalFlags: Int = 0

        @Before
        fun setUpTest() {
            executor = Executors.newSingleThreadExecutor()
            originalFlags = SEMANTIC_SEARCH_PROVIDER.flags
        }

        @After
        fun tearDownTest() {
            SEMANTIC_SEARCH_PROVIDER.flags = originalFlags
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testSemanticSearchProvider() {
            // OverrideFlagsRule will restore the flag to its original state after the test.
            FlagUtils.getInstance()
                .setOverride(FLAG_USE_LOCAL_SEARCH_PROVIDER, testParams.flagEnabled)
            activity.resources.setLocalSearchProvider(testParams.resourceUri)

            // Set up a document to be returned by the SEMANTIC_SEARCH provider when it is used,
            // or an error if it should fail.
            if (testParams.semanticSearchError) {
                environment.mockProviders[SEMANTIC_SEARCH_PROVIDER.authority]
                    ?.setThrownRuntimeMessage("Semantic search failed!")
            } else {
                val semanticSearchDoc = environment.model.createFile("found-me-on-semantic-search")
                environment.mockProviders[SEMANTIC_SEARCH_PROVIDER.authority]
                    ?.setNextChildDocumentsReturns(semanticSearchDoc)
            }
            // Set up a document to be returned by the DOWNLOADS provider which acts as the fallback
            // result when the LOCAL_SEARCH provider is unused.
            val downloadedDoc = environment.model.createFile("found-me-on-downloads")
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setNextChildDocumentsReturns(downloadedDoc)
            }

            if (testParams.flagsToAdd != 0) {
                SEMANTIC_SEARCH_PROVIDER.flags =
                    SEMANTIC_SEARCH_PROVIDER.flags or testParams.flagsToAdd
            }
            if (testParams.flagsToRemove != 0) {
                SEMANTIC_SEARCH_PROVIDER.flags =
                    SEMANTIC_SEARCH_PROVIDER.flags and testParams.flagsToRemove.inv()
            }

            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.DOWNLOADS),
                    SEMANTIC_SEARCH_PROVIDER,
                    TestFileTypeLookup(),
                    contentObserver,
                    "found-me",
                    QueryOptions(
                        maxResults = 10,
                        maxResultsPerRoot = ALL_RESULTS,
                        maxLastModifiedDelta = null,
                        maxQueryTime = null,
                        showHidden = false,
                        acceptableMimeTypes = null,
                        otherQueryArgs = Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(getFileCount(result)).isEqualTo(1)

            val document = getDocuments(result)[0]
            expect.that(document.authority).isEqualTo(TestProvidersAccess.DOWNLOADS.authority)
            expect.that(document.displayName).isEqualTo(testParams.expectedDisplayName)

            if (testParams.expectedSemanticSearch) {
                val queryArgs =
                    environment.mockProviders[SEMANTIC_SEARCH_PROVIDER.authority]?.lastQueryArgs
                val extraUri =
                    queryArgs?.let { BundleCompat.getParcelable(it, EXTRA_URI, Uri::class.java) }
                expect.that(extraUri).isNotNull()

                val expectedExtraUri =
                    DocumentsContract.buildDocumentUri(
                        TestProvidersAccess.DOWNLOADS.authority,
                        TestProvidersAccess.DOWNLOADS.documentId,
                    )
                expect.that(extraUri).isEqualTo(expectedExtraUri)
            }
        }
    }

    // Collection of plain tests that do not use parameters.
    @SmallTest
    class PlainTests : BaseLoaderTest() {
        @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
        @get:Rule val setFlags = OverrideFlagsRule()

        @get:Rule val expect: Expect = Expect.create()

        lateinit var executor: ExecutorService
        val contentLock = ContentLock()
        val contentObserver = LockingContentObserver(contentLock) {}

        @Before
        fun setUpTest() {
            executor = Executors.newSingleThreadExecutor()
        }

        @After
        fun tearDownTest() {
            for (provider in environment.mockProviders) {
                provider.value.setQueryDelay(0)
            }
        }

        fun generateDocuments(
            count: Int,
            suffixOffset: Int,
            extensions: Array<String>,
        ): Array<DocumentInfo> {
            return Array(count) { i ->
                val suffix = String.format(Locale.US, "%05d", 2 * i + suffixOffset)
                val ext = extensions[i % extensions.size]
                environment.model.createFile("document-$suffix.$ext")
            }
        }

        private fun createLoader(
            sortModel: SortModel,
            queryDelayMs: Long = 100L,
            firstPassWaitMs: Long = 200L,
            closeLatch: CountDownLatch? = null,
            initialFile: String = "file-01.txt",
        ): SearchLoader {
            val downloads = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]!!
            downloads.apply {
                setNextChildDocumentsReturns(environment.model.createFile(initialFile))
                setQueryDelay(queryDelayMs)
                setCloseLatch(closeLatch)
            }
            return SearchLoader(
                activity,
                listOf(TestProvidersAccess.DOWNLOADS),
                null,
                TestFileTypeLookup(),
                contentObserver,
                "file",
                QueryOptions(
                    10,
                    ALL_RESULTS,
                    null,
                    Duration.ofMillis(firstPassWaitMs),
                    false,
                    null,
                    Bundle(),
                ),
                sortModel,
                Executors.newFixedThreadPool(3),
            )
        }

        /**
         * Checks that the merging, filtering and sorting of results works correctly. We set up two
         * providers: home and pickles storage. They get files with names that have a zipper like
         * pattern, when sorted. Here we are checking if merging two cursors, with filtering
         * produces the expected result.
         */
        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testValidateMergeFilterSort() {
            val fileCount = 200
            val maxCount = fileCount / 2
            environment.mockProviders.apply {
                // Pickles documents have IDs 0, 2, 4, .., 398. Half of the documents are images,
                // the other half are documents (PDFs).
                get(TestProvidersAccess.PICKLES.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(fileCount, 0, arrayOf("png", "pdf"))
                )
                // Home documents have IDs 1, 3, 5, ... 399. Half of the documents are images,
                // the other half are videos.
                get(TestProvidersAccess.HOME.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(fileCount, 1, arrayOf("png", "avi"))
                )
            }

            // Set up the sort model so that results are sorted by their name.
            val sortModel = SortModel.createModel()
            sortModel.setDefaultDimension(SortModel.SORT_DIMENSION_ID_TITLE)
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.PICKLES, TestProvidersAccess.HOME),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document-",
                    QueryOptions(
                        maxCount,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result?.cursor?.getCount()).isEqualTo(maxCount)
            // We expect a perfect mix of documents from PICKLES and HOME. Pickles has odd indices
            // Home has even indices. However, the filtering cursor should take out all non-images
            // leaving us with 0, 4, 8, ..., from PICKLES and 1, 5, 9, ... from HOME.
            val model = Model(TestFeatures())
            model.update(result)
            val names = model.modelIds.map { model.getDocument(it)!!.displayName }
            expect
                .that(names)
                .isEqualTo(
                    (0 until maxCount).map {
                        val index = String.format(Locale.US, "%05d", 4 * (it / 2) + (it % 2))
                        "document-$index.png"
                    }
                )
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testExtraArgs() {
            environment.mockProviders.apply {
                get(TestProvidersAccess.PICKLES.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png", "avi"))
                )
            }
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.PICKLES, TestProvidersAccess.HOME),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document",
                    QueryOptions(
                        10,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            val extras = result.cursor.extras
            expect.that(extras).isNotNull()
            expect.that(extras.containsKey(DocumentsContract.EXTRA_LOADING)).isTrue()
            expect.that(extras.getBoolean(DocumentsContract.EXTRA_LOADING)).isFalse()
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testShowOrHideHiddenFiles() {
            val commonSearchString = "verdant"
            val doc1 = environment.model.createFile(".test$commonSearchString")
            val doc2 = environment.model.createFile("test$commonSearchString")
            doc1.documentId = ".test"
            doc2.documentId = "parent_folder/.hidden_folder/test"
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setNextChildDocumentsReturns(doc1, doc2)
            }

            val hideHiddenLoader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.DOWNLOADS),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    commonSearchString,
                    QueryOptions(10, ALL_RESULTS, null, null, false, null, Bundle()),
                    environment.state.sortModel,
                    executor,
                )

            var result: DirectoryResult = hideHiddenLoader.loadInBackground()!!
            assertEquals(0, result.cursor.getCount())

            val showHiddenLoader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.DOWNLOADS),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    commonSearchString,
                    QueryOptions(10, ALL_RESULTS, null, null, true, null, Bundle()),
                    environment.state.sortModel,
                    executor,
                )
            result = showHiddenLoader.loadInBackground()!!
            assertEquals(2, result.cursor.getCount())
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testCompletesInPresenceOfExceptions() {
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setThrownRuntimeMessage("Testing exception throwing")
            }

            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.DOWNLOADS),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "query",
                    QueryOptions(10, ALL_RESULTS, null, null, true, null, Bundle()),
                    environment.state.sortModel,
                    executor,
                )
            val queryDuration = measureTime {
                val result = loader.loadInBackground()
                val cursor = result?.cursor
                expect.that(cursor).isNotNull()
                expect.that(cursor!!.count).isEqualTo(0)
                // Expect that no cursor is still loading.
                expect.that(cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING)).isFalse()
            }
            // The no results should be due to the task terminating immediately, not because
            // it timed out. We give it 100 milliseconds.
            expect.that(queryDuration).isLessThan(100.milliseconds)
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testDeliversFastAndSlowResults() {
            val commonSearchString = UUID.randomUUID().toString()
            val doc1 = environment.model.createFile("downloads$commonSearchString")
            val doc2 = environment.model.createFile("pickles$commonSearchString")
            val doc3 = environment.model.createFile("home$commonSearchString")
            // The barrier awaits for 2 callers. One from the test thread and one from
            // the thread that runs the loader.
            val barrier = CyclicBarrier(2)
            var result: DirectoryResult? = null
            val firstPassWaitMs = 500L
            val passDeltaMs = 200L

            // Wait times for the above firstPassWaitMs and passDeltaMs are going to be:
            //  DOWNLOADS: 300ms
            //  PICKLES:   700ms
            //  HOME:      900ms
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setQueryDelay(firstPassWaitMs - passDeltaMs)
                setNextChildDocumentsReturns(doc1)
            }
            environment.mockProviders[TestProvidersAccess.PICKLES.authority]?.apply {
                setQueryDelay(firstPassWaitMs + passDeltaMs)
                setNextChildDocumentsReturns(doc2)
            }
            environment.mockProviders[TestProvidersAccess.HOME.authority]?.apply {
                setQueryDelay(firstPassWaitMs + 2 * passDeltaMs)
                setNextChildDocumentsReturns(doc3)
            }

            val loaderCallbacks: LoaderManager.LoaderCallbacks<DirectoryResult> =
                object : LoaderManager.LoaderCallbacks<DirectoryResult> {

                    override fun onCreateLoader(id: Int, args: Bundle?): Loader<DirectoryResult?> {
                        return SearchLoader(
                            activity,
                            listOf(
                                TestProvidersAccess.DOWNLOADS,
                                TestProvidersAccess.PICKLES,
                                TestProvidersAccess.HOME,
                            ),
                            semanticSearchRootInfo = null,
                            TestFileTypeLookup(),
                            contentObserver,
                            commonSearchString,
                            QueryOptions(
                                10,
                                ALL_RESULTS,
                                null,
                                Duration.ofMillis(firstPassWaitMs),
                                false,
                                null,
                                Bundle(),
                            ),
                            environment.state.sortModel,
                            Executors.newFixedThreadPool(3),
                        )
                    }

                    override fun onLoadFinished(
                        loader: Loader<DirectoryResult>,
                        data: DirectoryResult?,
                    ) {
                        result = data
                        barrier.await()
                    }

                    override fun onLoaderReset(loader: Loader<DirectoryResult>) {
                        loader.reset()
                    }
                }

            activity.supportLoaderManager
                .restartLoader(LoaderIds.TEST, null, loaderCallbacks)
                .startLoading()
            // Wait for the Downloads result.
            barrier.await()
            expect.that(getFileCount(result)).isEqualTo(1)

            // Now wait for the PICKLES result.
            barrier.await()
            // Expect that both the old and the new results are returned.
            expect.that(getFileCount(result)).isEqualTo(2)

            barrier.await()
            expect.that(getFileCount(result)).isEqualTo(3)
        }

        @Test
        fun testForceReload() {
            // Mock SortModel to return a cursor with a controllable getCount().
            val mockSortModel = Mockito.mock(SortModel::class.java)
            val fakeCount = AtomicReference<Int?>(null)
            whenever(mockSortModel.sortCursor(Mockito.any(Cursor::class.java), Mockito.any()))
                .thenAnswer { invocation ->
                    val cursor = invocation.getArgument<Cursor>(0)
                    object : CursorWrapper(cursor) {
                        override fun getCount(): Int = fakeCount.get() ?: cursor.count
                    }
                }

            val loader = createLoader(mockSortModel)
            val listener = TestLoadCompletedListener(1)
            loader.registerListener(1, listener)

            // First load.
            loader.startLoading()
            listener.await()
            val firstResult = listener.result!!
            expect.that(firstResult.fileNames.toTypedArray()).isEqualTo(arrayOf("file-01.txt"))

            // Simulate app suspension.
            loader.stopLoading()

            val downloads = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]!!
            downloads.setNextChildDocumentsReturns(environment.model.createFile("file-02.txt"))
            listener.reset()

            // Set fake count to make the loader think the cursor is stale.
            fakeCount.set(5)

            // Simulate app restoration.
            loader.startLoading()

            // Traverse old cursor; this should still work.
            var count = 0
            firstResult.cursor!!.moveToPosition(-1)
            while (firstResult.cursor!!.moveToNext()) {
                count++
            }
            expect.that(count).isEqualTo(1)
            fakeCount.set(null) // Restore getCount() behavior.

            listener.await()
            expect
                .that(listener.result!!.fileNames.toTypedArray())
                .isEqualTo(arrayOf("file-02.txt"))
        }

        @Test
        fun testOnReset() {
            val resultsDelayMs = 100L
            val loader =
                createLoader(
                    environment.state.sortModel,
                    queryDelayMs = resultsDelayMs,
                    firstPassWaitMs = 2 * resultsDelayMs,
                )
            val listener = TestLoadCompletedListener(1)
            loader.registerListener(1, listener)

            // Start loading and ensure a result is delivered.
            loader.startLoading()
            listener.await()

            expect.that(listener.loadCount).isEqualTo(1)
            expect.that(listener.result).isNotNull()
            expect
                .that(listener.result!!.fileNames.toTypedArray())
                .isEqualTo(arrayOf("file-01.txt"))

            // Reset the loader.
            loader.reset()

            // After reset, force a load again.
            val downloads = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]!!
            downloads.setNextChildDocumentsReturns(environment.model.createFile("file-01.txt"))
            loader.startLoading()
            Thread.sleep(2 * resultsDelayMs + 50)

            // The load count should be 2 because we started loading again after reset.
            expect.that(listener.loadCount).isEqualTo(2)
            expect.that(listener.result).isNotNull()
            expect
                .that(listener.result!!.fileNames.toTypedArray())
                .isEqualTo(arrayOf("file-01.txt"))
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        @Ignore("b/474184546: Currently failing, re-enable")
        fun testCancelLoad() {
            val queryDelayMs = 200L
            // shorter than query delay
            val firstPassWaitMs = 100L
            val loader =
                createLoader(
                    environment.state.sortModel,
                    queryDelayMs = queryDelayMs,
                    firstPassWaitMs = firstPassWaitMs,
                )
            // Expect only one task to deliver results. AsyncTaskLoader cancels the other task.
            val listener = TestLoadCompletedListener(1)
            loader.registerListener(1, listener)

            loader.startLoading()

            // Immediately cancel the task. This must result in the first search task never
            // delivering results.
            val wasCancelled = loader.cancelLoad()
            expect.that(wasCancelled).isTrue()

            // Check if the results are delivered.
            try {
                listener.await(queryDelayMs + 50, TimeUnit.MILLISECONDS)
                // Should never come here; fail if we do.
                fail("Results must not be delivered after cancellation")
            } catch (e: InterruptedException) {
                // As expected.
            }

            expect.that(listener.result).isNull()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3)
        fun testHasLimitedFunctionalityWhenOffline_isSet_whenOnlyRootIsCloud_andNoDocs() {
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.getCloudRoot()),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document",
                    QueryOptions(
                        10,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            expect.that(result.cursor.count).isEqualTo(0)
            // The cloud root has limited functionality when offline.
            expect.that(result.hasLimitedFunctionalityWhenOffline).isTrue()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3)
        fun testHasLimitedFunctionalityWhenOffline_isSet_whenOnlyRootIsCloud_andDocsReturned() {
            // Have the cloud root return documents.
            environment.mockProviders.apply {
                get(TestProvidersAccess.getCloudRoot().authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png"))
                )
            }
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.getCloudRoot()),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document",
                    QueryOptions(
                        10,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            expect.that(result.cursor.count).isEqualTo(2)
            // The cloud root has limited functionality when offline.
            expect.that(result.hasLimitedFunctionalityWhenOffline).isTrue()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3)
        fun testHasLimitedFunctionalityWhenOffline_isNotSet_whenMultipleRoots_butNoCloudDocs() {
            // Have only the Downloads root return documents.
            environment.mockProviders.apply {
                get(TestProvidersAccess.DOWNLOADS.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png"))
                )
            }
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.getCloudRoot(), TestProvidersAccess.DOWNLOADS),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document",
                    QueryOptions(
                        10,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            expect.that(result.cursor.count).isEqualTo(2)
            // No cloud files present.
            expect.that(result.hasLimitedFunctionalityWhenOffline).isFalse()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3)
        fun testHasLimitedFunctionalityWhenOffline_isSet_whenMultipleRoots_andCloudDocs() {
            // Have the cloud root return docs.
            environment.mockProviders.apply {
                get(TestProvidersAccess.getCloudRoot().authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(1, 1, arrayOf("png"))
                )
                get(TestProvidersAccess.DOWNLOADS.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png"))
                )
            }
            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.getCloudRoot(), TestProvidersAccess.DOWNLOADS),
                    semanticSearchRootInfo = null,
                    TestFileTypeLookup(),
                    contentObserver,
                    "document",
                    QueryOptions(
                        10,
                        ALL_RESULTS,
                        null,
                        null,
                        false,
                        arrayOf("image/png"),
                        Bundle(),
                    ),
                    environment.state.sortModel,
                    executor,
                )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            expect.that(result.cursor.count).isEqualTo(3)
            // No cloud files present.
            expect.that(result.hasLimitedFunctionalityWhenOffline).isTrue()
        }

        @Test
        @EnableFlags(FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY)
        fun testLockedCursorLockDoesNotStopLoaderReset() {
            val closeLatch = CountDownLatch(1)
            val loader =
                createLoader(
                    environment.state.sortModel,
                    queryDelayMs = 0L,
                    firstPassWaitMs = 100L,
                    closeLatch = closeLatch,
                )
            val listener = TestLoadCompletedListener(1)
            loader.registerListener(1, listener)

            // Start loading and ensure a result is delivered.
            loader.startLoading()
            listener.await()

            expect.that(listener.loadCount).isEqualTo(1)
            expect.that(listener.result).isNotNull()
            expect
                .that(listener.result!!.fileNames.toTypedArray())
                .isEqualTo(arrayOf("file-01.txt"))

            // Reset the loader. This is called on the main test thread just like reset is called on
            // the main UI thread. It must be quick even if the loader closes slowly.
            val totalLoadTime = measureTime { loader.reset() }

            // We expect this operation to take around 10 or fewer milliseconds. For example, on
            // Intel i3 12th Gen, the operation takes less than 0.2ms. However, we give it full 5s,
            // similar to the timeout used by bots.
            expect.that(totalLoadTime).isLessThan(5000.milliseconds)
            closeLatch.countDown()
        }
    }
}
