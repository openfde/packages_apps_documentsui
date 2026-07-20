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

import android.content.Context
import android.net.Uri
import android.os.DeadObjectException
import android.provider.DocumentsContract
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.Features
import com.android.documentsui.clipping.UrisSupplier
import com.android.documentsui.util.FlagUtils.Companion.isVisualSignalsFlagEnabled
import kotlinx.coroutines.runBlocking

/**
 * A variant of [DeleteJob] that recurses into folder structures to delete them if the visual
 * signals flag is enabled.
 *
 * @see DeleteJob constructor for most param descriptions.
 */
class DeleteJobKt(
    service: Context,
    listener: Listener,
    id: String,
    stack: DocumentStack,
    srcs: UrisSupplier,
    parentUri: Uri?,
    features: Features,
) : DeleteJob(service, listener, id, stack, srcs, parentUri, features) {

    override fun performDelete(doc: DocumentInfo, parent: DocumentInfo?) {
        if (isVisualSignalsFlagEnabled()) {
            deleteIteratively(doc, parent)
        } else {
            super.performDelete(doc, parent)
        }
    }

    @Throws(ResourceException::class)
    private fun deleteIteratively(doc: DocumentInfo, parent: DocumentInfo?) {
        val queryColumns =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
            )
        runBlocking {
            try {
                DocumentTraversalHelper(doc, getClient(doc), queryColumns, appContext)
                    .recursePostOrder()
                    .collect { (current, currentParent) ->
                        deleteDocument(current, currentParent ?: parent)
                    }
            } catch (e: ResourceException) {
                if (e.cause is DeadObjectException) {
                    releaseClient(doc)
                }
                throw e
            }
        }
    }
}
