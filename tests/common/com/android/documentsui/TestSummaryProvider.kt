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

package com.android.documentsui

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.core.os.BundleCompat

internal class TestSummaryProvider :
    TestRootProvider("Test Summary Provider", ROOT_ID, 0, ROOT_ID) {
    /**
     * isEmpty is used for the FLAG_EMPTY which tells if the provider is disabled (aka empty) or
     * not.
     */
    private var isEmpty = false

    /** Maps the DocumentID to the desired summary. */
    private val summaries = HashMap<String, String>()

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate(): $AUTHORITY/$ROOT_ID")
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        Log.d(TAG, "queryRoots(): $AUTHORITY/$ROOT_ID")
        val result =
            MatrixCursor(
                projection
                    ?: arrayOf(
                        DocumentsContract.Root.COLUMN_ROOT_ID,
                        DocumentsContract.Root.COLUMN_FLAGS,
                        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Root.COLUMN_TITLE,
                    )
            )
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        // Use the zz prefix so it displays at the bottom in the navigation list.
        row.add(DocumentsContract.Root.COLUMN_TITLE, "zz - $ROOT_ID")
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)

        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            if (isEmpty) DocumentsContract.Root.FLAG_EMPTY else 0,
        )
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        Log.d(TAG, "queryDocument(): $AUTHORITY/$ROOT_ID")

        if (documentId == ROOT_ID && projection?.singleOrNull() == Document.COLUMN_DOCUMENT_ID) {
            // Mark the root as enabled.
            isEmpty = false

            // Notify SummaryProviderManager that the provider has changed.
            context?.contentResolver?.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
        }

        val cursor =
            MatrixCursor(
                projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SUMMARY)
            )
        val summary = summaries[documentId]
        if (summary != null) {
            val row = cursor.newRow()
            row.add(Document.COLUMN_DOCUMENT_ID, documentId)
            row.add(Document.COLUMN_SUMMARY, summary)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        Log.d(TAG, "queryChildDocuments(): $AUTHORITY/$ROOT_ID")

        val cursor =
            MatrixCursor(
                projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SUMMARY)
            )
        summaries.forEach { (documentId, summary) ->
            val row = cursor.newRow()
            row.add(Document.COLUMN_DOCUMENT_ID, documentId)
            row.add(Document.COLUMN_SUMMARY, summary)
            Log.d(TAG, "Add summary for $documentId: $summary")
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Log.d(TAG, "call(): $AUTHORITY/$ROOT_ID, method: $method")
        when (method) {
            "configure" -> configure(extras)
            "setIsEmpty" -> {
                isEmpty = extras?.getBoolean("isEmpty") ?: false
                context
                    ?.contentResolver
                    ?.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
                return null
            }
        }
        return super.call(method, arg, extras)
    }

    private fun configure(extras: Bundle?) {
        if (extras == null) return

        if (extras.containsKey(EXTRA_IS_EMPTY)) {
            isEmpty = extras.getBoolean(EXTRA_IS_EMPTY)
            Log.d(TAG, "Set isEmpty to $isEmpty")
            context?.contentResolver?.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
        }

        if (extras.containsKey(EXTRA_SUMMARIES)) {
            summaries.clear()

            val summariesSerializable = mutableMapOf<String, String>()
            for ((key, value) in
                BundleCompat.getSerializable(extras, EXTRA_SUMMARIES, HashMap::class.java)
                    as HashMap<*, *>) {
                if (key is String && value is String) {
                    summariesSerializable[key] = value
                } else {
                    Log.w(
                        TAG,
                        "Invalid type for summaries. " +
                            "Expected String, String. Found: ${key?.javaClass?.name}, " +
                            "${value?.javaClass?.name}",
                    )
                }
            }

            summaries.putAll(summariesSerializable)
            Log.d(TAG, "Applied summaries: $summaries")
        }
    }

    companion object {
        const val AUTHORITY = "com.android.documentsui.summaryprovider"
        const val TAG = "TestSummaryProvider"
        private const val ROOT_ID = "summary-root"
        const val EXTRA_SUMMARIES = "com.android.documentsui.test.summaryprovider.SUMMARIES"
        const val EXTRA_IS_EMPTY = "com.android.documentsui.test.summaryprovider.IS_EMPTY"
    }
}
