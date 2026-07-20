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
import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Lookup
import com.android.documentsui.files.getinfo.SharedUtils.createInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The ViewModel that backs the "Get info" dialog. There are a number of items in the list that are
 * fetched asynchronously. So to avoid blocking the UI thread, build and maintain the list here,
 * ensuring that updates are posted back so the UI thread can update the dialog appropriately.
 */
class GetInfoViewModel(
    application: Application,
    private val doc: DocumentInfo,
    private val fileTypeLookup: Lookup<String, String>,
    private val showDebug: Boolean,
    private val summary: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    /**
     * Asynchronously fetches the directory item count. Will emit a placeholder first to reserve the
     * UI spot, then performs the DocumentsProvider query on the IO dispatcher for the final count.
     */
    private val directoryCountFlow: Flow<List<ListItem>> =
        flow {
                if (!doc.isDirectory) {
                    emit(emptyList())
                    return@flow
                }

                val context = getApplication<Application>()

                // The result is a single count, so emit a placeholder first so the items don't get
                // pushed down when the value is ready.
                emit(listOf(createInfo(context, R.string.directory_items, "--")))

                val count = getDirectoryChildCount(doc)
                emit(listOf(createInfo(context, R.string.directory_items, count.toString())))
            }
            .flowOn(ioDispatcher)

    /**
     * Asynchronously fetches the stream types from the ContentProvider. Will emit a placeholder
     * first to reserve the UI spot, then performs the DocumentsProvider query on the IO dispatcher
     * for the array of streamable types. This represents the mime types that a file could be
     * converted to if copied off the target DocumentsProvider.
     */
    private val streamTypesFlow: Flow<List<ListItem>> =
        flow {
                if (!showDebug) {
                    emit(emptyList())
                    return@flow
                }
                val context = getApplication<Application>()

                // The result is a single result, so emit a placeholder first so the items don't get
                // pushed down when the value is ready.
                emit(listOf(createInfo(context, R.string.debug_stream_types, "--")))

                val streamTypes = getStreamTypes(doc)
                emit(listOf(createInfo(context, R.string.debug_stream_types, streamTypes)))
            }
            .flowOn(ioDispatcher)

    /**
     * Asynchronously fetches the file metadata (EXIF, Audio, Video) from the ContentProvider. Emits
     * a placeholder (empty list) initially, then the parsed items once loaded.
     */
    private val metadataFlow: Flow<List<ListItem>> =
        flow {
                if (!doc.isMetadataSupported) {
                    emit(emptyList())
                    return@flow
                }

                val context = getApplication<Application>()
                val resolver = doc.userId.getContentResolver(context)

                try {
                    val metadata = DocumentsContract.getDocumentMetadata(resolver, doc.derivedUri)
                    if (metadata != null) {
                        val items = MetadataUtils.parseMetadata(context, metadata)
                        emit(items)
                    } else {
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load metadata for ${doc.derivedUri}", e)
                    emit(emptyList())
                }
            }
            .flowOn(ioDispatcher)

    /**
     * A StateFlow that defines the final list for the dialog. The data is combined using the
     * statically available information the `DocumentInfo` with the asynchronously fetched
     * information retrieved using the Flows.
     */
    val items: StateFlow<List<ListItem>> =
        combine(directoryCountFlow, streamTypesFlow, metadataFlow) {
                dirCountItems,
                streamTypes,
                metadataItems ->
                buildItemList(dirCountItems, streamTypes, metadataItems, summary)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = buildItemList(emptyList(), emptyList(), emptyList(), summary),
            )

    /**
     * Builds the list of items in the dialog. Asynchronously fetched items are passed in via
     * parameters and the method is called when new values are emitted.
     */
    private fun buildItemList(
        dirCountItems: List<ListItem>,
        streamTypes: List<ListItem>,
        metadataItems: List<ListItem>,
        summary: String?,
    ): List<ListItem> {
        val context = getApplication<Application>()

        return buildList {
            // Add the "General info" header.
            add(createHeader(context, R.string.peek_metadata_general_info_title))

            // Add the "Name" field along with the value.
            add(createInfo(context, R.string.sort_dimension_name, doc.displayName ?: ""))

            // Add the "Type" field along with the value, "Unknown" if not available.
            add(
                createInfo(
                    context,
                    R.string.peek_metadata_type,
                    fileTypeLookup.lookup(doc.mimeType)
                        ?: context.resources.getString(R.string.get_info_unknown_file_type),
                )
            )

            // If the size is known format and display it.
            if (doc.size >= 0 && !doc.isDirectory) {
                add(
                    createInfo(
                        context,
                        R.string.peek_metadata_size,
                        DefaultInfoFormatter.formatFileSize(context, doc.size),
                    )
                )
            }

            // If the last modified date is known, format and display it.
            if (doc.lastModified > 0) {
                add(
                    createInfo(
                        context,
                        R.string.peek_metadata_date_modified,
                        DefaultInfoFormatter.formatDate(context, doc.lastModified),
                    )
                )
            }

            val description = summary ?: if (doc.isPartial) doc.summary else null

            if (!description.isNullOrEmpty()) {
                add(
                    SharedUtils.createInfoSelectable(
                        context,
                        R.string.sort_dimension_summary,
                        description,
                    )
                )
            }

            addAll(dirCountItems)

            // Add Metadata info (EXIF, Audio, Video).
            if (metadataItems.isNotEmpty()) {
                add(createHeader(context, R.string.inspector_metadata_section))
                addAll(metadataItems)
            }

            // Add synchronous debug info.
            if (showDebug) {
                addAll(getDebugInfo(context, streamTypes))
            }
        }
    }

    private fun getDebugInfo(context: Context, streamTypes: List<ListItem>): List<ListItem> =
        buildList {
            add(createHeader(context, R.string.inspector_debug_section))

            add(createInfo(context, R.string.debug_user_id, doc.userId))
            add(createInfo(context, R.string.debug_content_uri, doc.derivedUri))
            add(createInfo(context, R.string.debug_document_id, doc.documentId))
            add(createInfo(context, R.string.debug_raw_mimetype, doc.mimeType))
            add(createInfo(context, R.string.debug_raw_size, doc.size))
            add(createInfo(context, R.string.debug_is_archive, doc.isArchive))
            add(createInfo(context, R.string.debug_is_blocked_from_tree, doc.isBlockedFromTree))
            add(createInfo(context, R.string.debug_is_container, doc.isContainer))
            add(createInfo(context, R.string.debug_is_partial, doc.isPartial))
            add(createInfo(context, R.string.debug_is_virtual, doc.isVirtual))
            add(createInfo(context, R.string.debug_supports_create, doc.isCreateSupported))
            add(createInfo(context, R.string.debug_supports_delete, doc.isDeleteSupported))
            add(createInfo(context, R.string.debug_supports_trash, doc.isTrashSupported))
            add(
                createInfo(
                    context,
                    R.string.debug_supports_restore_from_trash,
                    doc.isRestoreSupported,
                )
            )
            add(createInfo(context, R.string.debug_supports_metadata, doc.isMetadataSupported))
            add(createInfo(context, R.string.debug_supports_move, doc.isMoveSupported))
            add(createInfo(context, R.string.debug_supports_remove, doc.isRemoveSupported))
            add(createInfo(context, R.string.debug_supports_rename, doc.isRenameSupported))
            add(createInfo(context, R.string.debug_supports_settings, doc.isSettingsSupported))
            add(createInfo(context, R.string.debug_supports_thumbnail, doc.isThumbnailSupported))
            add(createInfo(context, R.string.debug_supports_weblink, doc.isWeblinkSupported))
            add(createInfo(context, R.string.debug_supports_write, doc.isWriteSupported))
            addAll(streamTypes)
        }

    private fun getDirectoryChildCount(doc: DocumentInfo): Int {
        val childrenUri = DocumentsContract.buildChildDocumentsUri(doc.authority, doc.documentId)
        val resolver = doc.userId.getContentResolver(getApplication())

        return try {
            resolver
                .query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null,
                )
                ?.use { cursor -> cursor.count } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load file count for ${doc.derivedUri}", e)
            0
        }
    }

    private fun getStreamTypes(doc: DocumentInfo): String {
        val resolver = doc.userId.getContentResolver(getApplication())

        return try {
            resolver.getStreamTypes(doc.derivedUri, "*/*")?.contentToString() ?: "[]"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load stream types for ${doc.derivedUri}", e)
            "[]"
        }
    }

    private fun createHeader(context: Context, labelRes: Int): ListItem.Header {
        return ListItem.Header(context.resources.getString(labelRes))
    }

    class Factory(
        private val application: Application,
        private val doc: DocumentInfo,
        private val fileTypeLookup: Lookup<String, String>,
        private val showDebug: Boolean,
        private val summary: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GetInfoViewModel(application, doc, fileTypeLookup, showDebug, summary) as T
        }
    }

    companion object {
        private const val TAG = "GetInfoViewModel"
    }
}
