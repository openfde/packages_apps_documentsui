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

import android.app.Application
import android.content.ContentProvider
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.media.MediaMetadata
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.os.Process
import android.provider.DocumentsContract
import android.provider.Settings
import android.test.mock.MockContentProvider
import android.test.mock.MockContentResolver
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.Shared
import com.android.documentsui.base.UserId
import com.android.documentsui.rules.MainDispatcherRule
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class GetInfoViewModelTest {

    @Mock private lateinit var application: Application
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var lookup: Lookup<String, String>

    private lateinit var contentResolver: MockContentResolver

    private val settingsProvider: ContentProvider =
        object : MockContentProvider() {
            override fun call(
                authority: String,
                method: String,
                arg: String?,
                extras: Bundle?,
            ): Bundle {
                return Bundle()
            }
        }

    // Runs the main test methods using a StandardTestDispatcher. This will allow for the usage of
    // methods like `first()` to suspend to enable the ioTestDispatcher to eagerly evaluate any
    // outstanding flows.
    private val testDispatcher = StandardTestDispatcher()

    // Use an UnconfinedTestDispatcher here to ensure the work performed in the ViewModel in the
    // background are done eagerly. This removes any guesswork on the final state, all flows emit
    // their values synchronously when evaluated.
    private val ioTestDispatcher = UnconfinedTestDispatcher(testDispatcher.scheduler)

    /** Creates the GetInfoViewModel passing the default arguments that are members of test. */
    private fun createGetInfoViewModel(
        doc: DocumentInfo,
        showDebug: Boolean = false,
        summary: String? = null,
    ) = GetInfoViewModel(application, doc, lookup, showDebug, summary, ioTestDispatcher)

    private fun createDocumentInfo(
        documentId: String = "testId",
        displayName: String = "test.pdf",
        mimeType: String = "application/pdf",
        size: Long = 1024 * 1024 * 10,
        lastModified: Long = 1234567890L,
        authority: String = AUTHORITY,
        flags: Int = 0,
        summary: String? = null,
    ): DocumentInfo {
        return DocumentInfo().apply {
            this.documentId = documentId
            this.displayName = displayName
            this.mimeType = mimeType
            this.size = size
            this.lastModified = lastModified
            this.userId = UserId.DEFAULT_USER
            this.authority = authority
            this.flags = flags
            this.summary = summary
            this.derivedUri = DocumentsContract.buildDocumentUri(authority, documentId)
        }
    }

    @get:Rule private val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    /**
     * A helper class that matches either a ListItem exactly or just the label of an ListItem.Info.
     */
    sealed class ExpectedItem {
        abstract val order: Int

        abstract fun matches(actual: ListItem): Boolean

        abstract fun contentToString(): String

        /** Strict match for label and value. */
        data class Exact(override val order: Int, val expectedItem: ListItem) : ExpectedItem() {
            override fun matches(actual: ListItem): Boolean = actual == expectedItem

            override fun contentToString() = expectedItem.toString()
        }

        /** Partial match of just the label for a ListItem.Info or InfoSelectable. */
        data class InfoLabel(override val order: Int, val label: String) : ExpectedItem() {
            override fun matches(actual: ListItem): Boolean {
                return (actual is ListItem.Info && actual.label == label) ||
                    (actual is ListItem.InfoSelectable && actual.label == label)
            }

            override fun contentToString() = "ListItem(label=$label, value=any())"
        }
    }

    @Before
    fun setUp() {
        openMocks(this)

        val configuration = Configuration()
        configuration.setLocales(LocaleList(Locale.US))

        `when`(application.resources).thenReturn(resources)
        `when`(application.applicationContext).thenReturn(application)

        `when`(resources.configuration).thenReturn(configuration)

        // Prevent NullPointerException when Formatter asks for internal string resources.
        `when`(resources.getString(anyInt())).thenReturn("MockString")
        `when`(resources.getString(anyInt(), any())).thenReturn("MockString")
        `when`(resources.getText(anyInt())).thenReturn("MockString")

        // Mock content resolver for DateFormat.
        contentResolver = MockContentResolver()
        contentResolver.addProvider(Settings.AUTHORITY, settingsProvider)
        `when`(application.contentResolver).thenReturn(contentResolver)
        `when`(application.userId).thenReturn(Process.myUserHandle().identifier)

        // Mock string resources to return static strings.
        `when`(resources.getString(R.string.peek_metadata_general_info_title))
            .thenReturn("General info")
        `when`(resources.getString(R.string.sort_dimension_name)).thenReturn("Name")
        `when`(resources.getString(R.string.peek_metadata_type)).thenReturn("Type")
        `when`(resources.getString(R.string.peek_metadata_size)).thenReturn("Size")
        `when`(resources.getString(R.string.peek_metadata_date_modified)).thenReturn("Modified")
        `when`(resources.getString(R.string.sort_dimension_summary)).thenReturn("Summary")
        `when`(resources.getString(R.string.directory_items)).thenReturn("Items")
        `when`(resources.getString(R.string.datetime_format_12)).thenReturn("MMM d, yyyy")
        `when`(resources.getString(R.string.datetime_format_24)).thenReturn("MMM d, yyyy")
        `when`(resources.getString(R.string.get_info_unknown_file_type)).thenReturn("Unknown")
        `when`(resources.getString(R.string.debug_stream_types)).thenReturn("Stream types")

        // Mock some debug fields to validate in test (they are all synchronous and they fall back
        // to "MockString" anyway, so let's avoid using a whole bunch of them that effectively will
        // do the same validation.
        `when`(resources.getString(R.string.inspector_debug_section)).thenReturn("Debug Info")
        `when`(resources.getString(R.string.debug_user_id)).thenReturn("User ID")

        // Mock lookup to return folder type and a default for the remaining types.
        `when`(lookup.lookup(any())).thenReturn("File Type")
        `when`(lookup.lookup(eq(DocumentsContract.Document.MIME_TYPE_DIR))).thenReturn("Folder")

        // Mock Audio strings.
        `when`(resources.getString(R.string.inspector_metadata_section)).thenReturn("Metadata")
        `when`(resources.getString(R.string.metadata_duration)).thenReturn("Duration")
        `when`(resources.getString(R.string.metadata_artist)).thenReturn("Artist")
        `when`(resources.getString(R.string.metadata_album)).thenReturn("Album")

        // Mock metadata fields
        `when`(resources.getString(R.string.metadata_dimensions)).thenReturn("Dimensions")

        // Mock Dimensions Format: Matches Video Test (1920x1080)
        `when`(
                resources.getString(
                    eq(R.string.metadata_dimensions_format),
                    eq(1920),
                    eq(1080),
                    anyFloat(),
                )
            )
            .thenReturn("1920 x 1080 (2.07 MP)")

        // Mock GPS/Address fields
        `when`(resources.getString(R.string.metadata_coordinates)).thenReturn("Coordinates")
        `when`(resources.getString(eq(R.string.metadata_coordinates_format), any(), any()))
            .thenAnswer { invocation ->
                val lat = invocation.arguments[1]
                val lon = invocation.arguments[2]
                "$lat, $lon"
            }
        `when`(resources.getString(R.string.metadata_address)).thenReturn("Address")
    }

    /**
     * Builds up an expected list based on the items of the actual list and expectations. This is
     * primarily used to ensure the error message on the equality assertion is descriptive.
     */
    private fun buildExpectedList(
        actualList: List<ListItem>,
        expectedSize: Int,
        vararg expectations: ExpectedItem,
    ): List<ListItem> {
        return List(expectedSize) { index ->
            val actualItem = actualList.getOrNull(index)
            val expectation = expectations.firstOrNull { it.order == index }

            when (expectation) {
                // No expectation on this item index, return the actual item.
                null -> actualItem

                // Exact match expectation, return the expectation.
                is ExpectedItem.Exact -> expectation.expectedItem

                // Partial match expectation, return the actualItem if the labels match, otherwise
                // return "<value ignored>" to ensure the assertion fails this row and the error
                // message is descriptive.
                is ExpectedItem.InfoLabel -> {
                    if (
                        (actualItem is ListItem.Info && actualItem.label == expectation.label) ||
                            (actualItem is ListItem.InfoSelectable &&
                                actualItem.label == expectation.label)
                    ) {
                        actualItem
                    } else {
                        // Create a "Target" item that will definitely cause a mismatch in the diff.
                        // We use a dummy value because we only cared about the label.
                        ListItem.Info(expectation.label, "<value ignored>")
                    }
                }
            }!!
        }
    }

    /** Wait for the expectations to be correct, otherwise fail with an assertion. */
    private suspend fun TestScope.waitAndAssertOrderedItems(
        viewModel: GetInfoViewModel,
        expectedSize: Int,
        vararg expectations: ExpectedItem,
    ) {
        // Subscribe to the items `StateFlow`. All the flows that combine to the items flow don't
        // actually start until at least 1 subscriber.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.items.collect()
        }

        val timeoutMs = 5000L
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val list = viewModel.items.value

            if (list.size == expectedSize) {
                val expectedList = buildExpectedList(list, expectedSize, *expectations)
                if (list == expectedList) {
                    return
                }
            }

            // In runTest, virtual time controls (like withTimeout or advanceTimeBy) fast-forward
            // instantly if no time-based delays are scheduled. Since the ViewModel's flow pipeline
            // (combine, flowOn) relies on multiple dispatch cycles to propagate data rather than
            // delays, virtual time controls can aggressively skip to the timeout before the
            // dispatcher has finished processing.
            //
            // By using a wall-clock loop with `yield()`, we bypass virtual time skipping and
            // manually pump the event loop. This gives the UnconfinedTestDispatcher the
            // execution cycles it needs to process the flow pipeline without premature cancellation
            // which allows for an appropriate error message to be displayed (and not a vague 60s
            // timeout reached error).
            yield()
        }

        val actualList = viewModel.items.value
        val expectedList = buildExpectedList(actualList, expectedSize, *expectations)
        assertEquals("Timed out waiting for expected state.", expectedList, actualList)
    }

    /**
     * Helper to quickly setup a DocumentsProvider that returns a Bundle on the getDocumentMetadata
     * call.
     */
    private fun setupMetadataProvider(metadataBuilder: Bundle.() -> Unit) {
        val provider =
            object : MockContentProvider() {
                override fun call(
                    auth: String,
                    method: String,
                    arg: String?,
                    extras: Bundle?,
                ): Bundle {
                    if (method == DocumentsContract.METHOD_GET_DOCUMENT_METADATA) {
                        return Bundle().apply(metadataBuilder)
                    }
                    return Bundle()
                }
            }
        contentResolver.addProvider(AUTHORITY, provider)
    }

    @Test
    fun testStandardFile_WithDebug() =
        runTest(testDispatcher) {
            val doc = createDocumentInfo()

            val streamTypesProvider =
                object : MockContentProvider() {
                    override fun getStreamTypes(
                        url: Uri,
                        mimeTypeFilter: String,
                    ): Array<out String?> {
                        return arrayOf("fake/type")
                    }
                }
            contentResolver.addProvider(AUTHORITY, streamTypesProvider)

            val viewModel = createGetInfoViewModel(doc, showDebug = true)
            waitAndAssertOrderedItems(
                viewModel,
                5 + DEBUG_ITEM_COUNT,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "test.pdf")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "File Type")),
                ExpectedItem.InfoLabel(3, "Size"),
                ExpectedItem.InfoLabel(4, "Modified"),
                ExpectedItem.Exact(5, ListItem.Header("Debug Info")),
                ExpectedItem.Exact(
                    6,
                    ListItem.Info("User ID", UserId.CURRENT_USER.identifier.toString()),
                ),
                ExpectedItem.Exact(
                    5 + DEBUG_ITEM_COUNT - 1,
                    ListItem.Info("Stream types", "[fake/type]"),
                ),
            )
        }

    @Test
    fun testStandardFile_NoDebug() =
        runTest(testDispatcher) {
            val doc = createDocumentInfo()

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                5,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "test.pdf")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "File Type")),
                ExpectedItem.InfoLabel(3, "Size"),
                ExpectedItem.InfoLabel(4, "Modified"),
            )
        }

    @Test
    fun testDirectory() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "testDirectoryId",
                    displayName = "My Folder",
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    size = 0,
                )

            val childrenProvider =
                object : MockContentProvider() {
                    override fun query(
                        uri: Uri,
                        projection: Array<out String>?,
                        selection: String?,
                        selectionArgs: Array<out String>?,
                        sortOrder: String?,
                    ): Cursor {
                        val cursor =
                            MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        cursor.addRow(arrayOf("child1"))
                        cursor.addRow(arrayOf("child2"))
                        cursor.addRow(arrayOf("child3"))
                        return cursor
                    }
                }
            contentResolver.addProvider(AUTHORITY, childrenProvider)

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                5,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "My Folder")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "Folder")),
                ExpectedItem.InfoLabel(3, "Modified"),
                ExpectedItem.Exact(4, ListItem.Info("Items", "3")),
            )
        }

    @Test
    fun testPartialFile() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "",
                    displayName = "downloading.tmp",
                    mimeType = "application/octet-stream",
                    size = 500,
                    flags = DocumentsContract.Document.FLAG_PARTIAL,
                    summary = "OriginalFilename.pdf",
                )

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                6,
                ExpectedItem.Exact(5, ListItem.InfoSelectable("Summary", "OriginalFilename.pdf")),
            )
        }

    @Test
    fun testNoLastModified() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    displayName = "test.pdf",
                    mimeType = "application/pdf",
                    size = 100,
                    lastModified = -1,
                )

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(viewModel, 4, ExpectedItem.InfoLabel(3, "Size"))
        }

    @Test
    fun testGeneratedDescription_Provided() =
        runTest(testDispatcher) {
            val doc = createDocumentInfo(size = 100)

            val viewModel = createGetInfoViewModel(doc, summary = "Generated Description")
            waitAndAssertOrderedItems(
                viewModel,
                6,
                ExpectedItem.Exact(5, ListItem.InfoSelectable("Summary", "Generated Description")),
            )
        }

    @Test
    fun testGeneratedDescription_FallbackToPartial() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    size = 100,
                    flags = DocumentsContract.Document.FLAG_PARTIAL,
                    summary = "Legacy Summary",
                )

            val viewModel = createGetInfoViewModel(doc, summary = null)
            waitAndAssertOrderedItems(
                viewModel,
                6,
                ExpectedItem.Exact(5, ListItem.InfoSelectable("Summary", "Legacy Summary")),
            )
        }

    @Test
    fun testAudioMetadata() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "audio",
                    displayName = "song.mp3",
                    mimeType = "audio/mpeg",
                    size = 5000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val audio =
                    Bundle().apply {
                        putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist Name")
                        putString(MediaMetadata.METADATA_KEY_ALBUM, "Album Name")
                        putLong(MediaMetadata.METADATA_KEY_DURATION, 60000L)
                    }
                putBundle(Shared.METADATA_KEY_AUDIO, audio)
            }

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                9,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(6, ListItem.Info("Artist", "Artist Name")),
                ExpectedItem.Exact(7, ListItem.Info("Album", "Album Name")),
                ExpectedItem.Exact(8, ListItem.Info("Duration", "01:00")),
            )
        }

    @Test
    fun testAudioMetadata_NullBundle() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "audio",
                    displayName = "song.mp3",
                    mimeType = "audio/mpeg",
                    size = 5000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            val provider =
                object : MockContentProvider() {
                    override fun call(
                        auth: String,
                        method: String,
                        arg: String?,
                        extras: Bundle?,
                    ): Bundle? {
                        return null
                    }
                }
            contentResolver.addProvider(AUTHORITY, provider)
            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(viewModel, 5)
        }

    @Test
    fun testAudioMetadata_DurationInt() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "audio",
                    displayName = "song.mp3",
                    mimeType = "audio/mpeg",
                    size = 5000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val audio = Bundle().apply { putInt(MediaMetadata.METADATA_KEY_DURATION, 60000) }
                putBundle(Shared.METADATA_KEY_AUDIO, audio)
            }

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                7,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(6, ListItem.Info("Duration", "01:00")),
            )
        }

    @Test
    fun testAudioMetadata_EmptyStrings_Hidden() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "audio_empty.mp3",
                    displayName = "song.mp3",
                    mimeType = "audio/mpeg",
                    size = 5000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val audio =
                    Bundle().apply {
                        // Empty values should not be shown.
                        putString(MediaMetadata.METADATA_KEY_ARTIST, "")
                        putString(MediaMetadata.METADATA_KEY_ALBUM, "")
                        putString(MediaMetadata.METADATA_KEY_COMPOSER, "")
                    }
                putBundle(Shared.METADATA_KEY_AUDIO, audio)
            }

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                5,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "song.mp3")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "File Type")),
                ExpectedItem.InfoLabel(3, "Size"),
                ExpectedItem.InfoLabel(4, "Modified"),
            )
        }

    @Test
    fun testVideoMetadata() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video.mp4",
                    displayName = "video.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val video =
                    Bundle().apply {
                        putInt(ExifInterface.TAG_IMAGE_WIDTH, 1920)
                        putInt(ExifInterface.TAG_IMAGE_LENGTH, 1080)
                        putInt(MediaMetadata.METADATA_KEY_DURATION, 60000)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                8,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(6, ListItem.Info("Dimensions", "1920 x 1080 (2.07 MP)")),
                ExpectedItem.Exact(7, ListItem.Info("Duration", "01:00")),
            )
        }

    @Test
    fun testVideoMetadata_LongDuration() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video_long.mp4",
                    displayName = "video_long.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val video =
                    Bundle().apply {
                        putInt(ExifInterface.TAG_IMAGE_WIDTH, 1920)
                        putInt(ExifInterface.TAG_IMAGE_LENGTH, 1080)
                        putLong(MediaMetadata.METADATA_KEY_DURATION, 60000L)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)
            waitAndAssertOrderedItems(
                viewModel,
                8,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(6, ListItem.Info("Dimensions", "1920 x 1080 (2.07 MP)")),
                // Strict check ensures Long -> Int conversion didn't break format
                ExpectedItem.Exact(7, ListItem.Info("Duration", "01:00")),
            )
        }

    @Test
    fun testVideoMetadata_Coordinates_Double() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video_coords.mp4",
                    displayName = "video.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            // Mock the format specifically for this test if needed, or rely on the setUp() mock
            // which currently returns "10.0, 20.0" for any coordinate input.
            // We use 10.0 and 20.0 to match the hardcoded mock in your setUp() method.
            setupMetadataProvider {
                val video =
                    Bundle().apply {
                        // Test Double path in VideoUtils.getVideoCoords
                        putDouble(Shared.METADATA_VIDEO_LATITUDE, 10.0)
                        putDouble(Shared.METADATA_VIDEO_LONGITUDE, 20.0)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)

            // Expected items:
            // 5 Standard (Header, Name, Type, Size, Modified)
            // + 3 Video (Dimensions, Coordinates, Duration)
            // Note: "Address" is not expected here because Geocoder is not shadowed/mocked
            // to return a positive result in this environment, so getAddress returns null.
            waitAndAssertOrderedItems(
                viewModel,
                7,
                ExpectedItem.Exact(5, ListItem.Header("Metadata")),
                ExpectedItem.Exact(6, ListItem.Info("Coordinates", "10.0, 20.0")),
            )
        }

    @Test
    fun testVideoMetadata_Coordinates_Float() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video_coords_float.mp4",
                    displayName = "video.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val video =
                    Bundle().apply {
                        // Test Float path in VideoUtils.getVideoCoords
                        // Use Float values specifically to trigger the fallback logic in parsing
                        putFloat(Shared.METADATA_VIDEO_LATITUDE, 10.0f)
                        putFloat(Shared.METADATA_VIDEO_LONGITUDE, 20.0f)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)

            waitAndAssertOrderedItems(
                viewModel,
                7,
                ExpectedItem.Exact(5, ListItem.Header("Metadata")),
                ExpectedItem.Exact(6, ListItem.Info("Coordinates", "10.0, 20.0")),
            )
        }

    @Test
    fun testVideoMetadata_DimensionsAsString() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video_strings.mp4",
                    displayName = "video.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val video =
                    Bundle().apply {
                        // Test getIntTag fallback logic where tags are stored as Strings
                        putString(ExifInterface.TAG_IMAGE_WIDTH, "1920")
                        putString(ExifInterface.TAG_IMAGE_LENGTH, "1080")
                        putInt(MediaMetadata.METADATA_KEY_DURATION, 60000)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)

            waitAndAssertOrderedItems(
                viewModel,
                8,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                // Verify calculation still works with String inputs
                ExpectedItem.Exact(6, ListItem.Info("Dimensions", "1920 x 1080 (2.07 MP)")),
            )
        }

    @Test
    fun testVideoMetadata_InvalidCoordinates() =
        runTest(testDispatcher) {
            val doc =
                createDocumentInfo(
                    documentId = "video_invalid_coords.mp4",
                    displayName = "video.mp4",
                    mimeType = "video/mp4",
                    size = 10000000,
                    flags = DocumentsContract.Document.FLAG_SUPPORTS_METADATA,
                )

            setupMetadataProvider {
                val video =
                    Bundle().apply { // Test 0.0/0.0 exclusion logic
                        putDouble(Shared.METADATA_VIDEO_LATITUDE, 0.0)
                        putDouble(Shared.METADATA_VIDEO_LONGITUDE, 0.0)
                    }
                putBundle(Shared.METADATA_KEY_VIDEO, video)
            }

            val viewModel = createGetInfoViewModel(doc)

            // Should NOT have coordinates item (Size should just contain the standard items)
            waitAndAssertOrderedItems(viewModel, 5)
        }

    companion object {
        // Constant for the number of debug items added (Header + 5 infos + 17 flags + 1 async).
        const val DEBUG_ITEM_COUNT = 24

        // Constant mock authority used throughout all the tests.
        const val AUTHORITY = "com.example.authority"
    }
}
