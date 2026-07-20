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
package com.android.documentsui.base

import android.content.AttributionSource
import android.content.Context
import android.content.IContentProvider
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.TestContentResolver
import com.android.documentsui.flags.Flags
import com.android.documentsui.roots.ProvidersAccess
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestProvidersAccess
import junit.framework.AssertionFailedError
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

private val columnNames =
    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Root.COLUMN_TITLE)

private fun createDisplayNameCursor(pathItemName: String) =
    MatrixCursor(columnNames, 1).apply { addRow(arrayOf(pathItemName, pathItemName)) }

private fun toPath(stack: DocumentStack): String {
    val names = mutableListOf<String>()
    names.add(stack.root!!.title)
    for (i in 0 until stack.size()) {
        names.add(stack[i].displayName)
    }
    return names.joinToString("/")
}

@RunWith(AndroidJUnit4::class)
@SmallTest
class PathExtractorTest {
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    /** Testable variant of PathExtractor with methods that use content resolver stubbed out. */
    class TestablePathExtractor(context: Context, providersAccess: ProvidersAccess) :
        PathExtractor(context, providersAccess) {
        var documentPath: DocumentsContract.Path = DocumentsContract.Path("root.id", listOf("Root"))
        var displayNameCursorMap = mapOf<String, MatrixCursor>()
        var exception: Exception? = null

        /** Fetches DocumentsContract.Path for the given `documentUri` */
        override fun getDocumentPath(documentUri: Uri): DocumentsContract.Path {
            if (exception != null) {
                throw exception!!
            }
            return documentPath
        }

        /** Fetches a cursor for the given Uri that provides access to the title column. */
        override fun getCursorForUri(uri: Uri): Cursor {
            for (item in displayNameCursorMap) {
                if (uri.toString().endsWith(item.key)) {
                    return item.value
                }
            }
            throw AssertionFailedError("Display name cursor request for unknown $uri")
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var pathExtractor: TestablePathExtractor

    @Before
    fun setUp() {
        pathExtractor = TestablePathExtractor(context, TestProvidersAccess())
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testSuccessfullyGetPath() {
        // Setup.
        val docInfo =
            DocumentInfo().apply {
                userId = UserId.DEFAULT_USER
                authority = Providers.AUTHORITY_STORAGE
                documentId = "file.txt.id"
                displayName = "file.txt"
                mimeType = "text/plain"
                deriveFields()
            }
        val pathIds = listOf("foo.id", docInfo.documentId)
        val rootId = Providers.ROOT_ID_DEVICE

        // When fetching DocumentsContract.Path.
        pathExtractor.documentPath = DocumentsContract.Path(rootId, pathIds)
        // Map from path ID to cursor with the display name.
        pathExtractor.displayNameCursorMap =
            mapOf(
                "content://${Providers.AUTHORITY_STORAGE}/document/foo.id" to
                    createDisplayNameCursor("Foo"),
                "content://${Providers.AUTHORITY_STORAGE}/document/file.txt.id" to
                    createDisplayNameCursor("file.txt"),
            )

        // The actual test.
        val stack = pathExtractor.getDocumentStack(docInfo)
        Assert.assertEquals("Device/Foo/file.txt", toPath(stack))
    }

    // TODO(b/444316005): Special case, where Recent view uses Media document provider. Remove.
    @Test
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testApproximatePathForMediaFile() {
        val docInfo =
            DocumentInfo().apply {
                userId = UserId.DEFAULT_USER
                authority = Providers.AUTHORITY_MEDIA
                documentId = "file.mp3.id"
                displayName = "file.mp3"
                mimeType = "audio/mp3"
                deriveFields()
            }
        pathExtractor.exception = UnsupportedOperationException("no-supported-for-media")
        pathExtractor.displayNameCursorMap =
            mapOf(
                "content://com.android.providers.media.documents/root" to
                    createDisplayNameCursor("Audio")
            )

        val stack = pathExtractor.getDocumentStack(docInfo)
        Assert.assertEquals("Recents/Audio/file.mp3", toPath(stack))
    }

    @Test
    @DisableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY)
    fun testTryGetExternalStorageUriForMediaUriAndSearchV2Disabled() {
        val mediaUri = "content://com.android.providers.media.documents/document/file%3A6".toUri()
        Assert.assertEquals(mediaUri, tryGetExternalStorageUri(context, mediaUri))
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testTryGetExternalStorageUriForMediaUriAndSearchV2Enabled() {
        val client = mock<IContentProvider>()
        val resolver = TestContentResolver(client, context)
        val spyContext = spy(context)
        whenever(spyContext.contentResolver) doReturn (resolver)

        // The first call to the client is to resolve media URI.
        val mediaUri = "content://com.android.providers.media.documents/document/file%3A6".toUri()
        whenever(
            client.call(
                any<AttributionSource>(),
                any<String>(),
                eq("get_media_uri"),
                isNull(),
                any<Bundle>(),
            )
        ) doReturn
            (Bundle().apply { putParcelable("uri", "content://media/external/file/6".toUri()) })
        // The second call to the client is to resolve document URI.
        val externalStorageUri = "content://com.android.externalstorage.documents/file%3A6".toUri()
        whenever(
            client.call(
                any<AttributionSource>(),
                any<String>(),
                eq("get_document_uri"),
                isNull(),
                any<Bundle>(),
            )
        ) doReturn (Bundle().apply { putParcelable("uri", externalStorageUri) })

        Assert.assertEquals(externalStorageUri, tryGetExternalStorageUri(spyContext, mediaUri))
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testTryGetExternalStorageUriForNonmediaUriAndSearchV2Enabled() {
        val downloadsUri =
            "content://com.android.providers.downloads.documents/document/msf%3A6".toUri()
        Assert.assertEquals(downloadsUri, tryGetExternalStorageUri(context, downloadsUri))
    }

    @Test(expected = NoSuchElementException::class)
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testIllegalArgumentException_isWrapped() {
        // Setup.
        val docInfo = createTestDocumentInfo()
        // When getDocumentPath throws an IllegalArgumentException.
        pathExtractor.exception = IllegalArgumentException("test")
        // We need to provide a cursor for the root.
        pathExtractor.displayNameCursorMap =
            mapOf("content://download.id/root" to createDisplayNameCursor("Downloads"))

        // The actual test.
        // This should throw NoSuchElementException, which wraps the original exception.
        pathExtractor.getDocumentStack(docInfo)
    }

    @Test(expected = RuntimeException::class)
    @EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
    fun testRuntimeException_isPropagated() {
        // Setup.
        val docInfo = createTestDocumentInfo()
        // When getDocumentPath throws a RuntimeException.
        pathExtractor.exception = RuntimeException("test")
        // We need to provide a cursor for the root.
        pathExtractor.displayNameCursorMap =
            mapOf("content://download.id/root" to createDisplayNameCursor("Downloads"))

        // The actual test.
        // This should throw RuntimeException, as it is not caught.
        pathExtractor.getDocumentStack(docInfo)
    }

    private fun createTestDocumentInfo(): DocumentInfo {
        return DocumentInfo().apply {
            userId = UserId.DEFAULT_USER
            authority = "download.id"
            documentId = "file.txt.id"
            displayName = "file.txt"
            mimeType = "text/plain"
            deriveFields()
        }
    }
}
