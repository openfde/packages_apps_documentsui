/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.documentsui.compose.data

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.DocumentsContract
import android.util.Log
import com.android.documentsui.compose.data.model.Root
import com.android.documentsui.compose.data.model.RootType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** A data source that provides information about document providers. */
interface DocumentsProviderDataSource {
    val rootsFlow: Flow<List<Root>>
}

/** Implementation of [DocumentsProviderDataSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsProviderDataSourceImpl
@Inject
constructor(private val application: Application, private val ioDispatcher: CoroutineDispatcher) :
    DocumentsProviderDataSource {
    /** A flow that emits the list of available document providers, updating on package changes. */
    val availableProvidersFlow: Flow<List<ResolveInfo>> =
        callbackFlow {
                fun refreshProviders() {
                    val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
                    val providers =
                        application.packageManager.queryIntentContentProviders(intent, 0)
                    trySend(providers)
                }

                // Register a BroadcastReceiver to listen for package changes and refresh the list.
                val packageChangeReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            refreshProviders()
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addAction(Intent.ACTION_PACKAGE_CHANGED)
                        addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                        addDataScheme("package")
                    }
                application.registerReceiver(packageChangeReceiver, filter)

                refreshProviders()

                awaitClose { application.unregisterReceiver(packageChangeReceiver) }
            }
            .flowOn(ioDispatcher)

    /** A flow that emits a list of available document providers, filtered by a set of criteria. */
    val filteredProvidersFlow: Flow<List<ResolveInfo>> =
        availableProvidersFlow.map { providers ->
            providers.filter { resolveInfo ->
                val info = resolveInfo.providerInfo
                info.authority != null &&
                    info.exported &&
                    info.grantUriPermissions &&
                    android.Manifest.permission.MANAGE_DOCUMENTS == info.readPermission &&
                    android.Manifest.permission.MANAGE_DOCUMENTS == info.writePermission &&
                    (info.applicationInfo.flags and ApplicationInfo.FLAG_STOPPED) == 0
            }
        }

    /** A flow that emits a list of all roots from all available document providers. */
    override val rootsFlow: Flow<List<Root>> =
        filteredProvidersFlow.flatMapLatest { providers ->
            if (providers.isEmpty()) {
                flowOf(emptyList())
            } else {
                val rootFlows =
                    providers.map { resolveInfo ->
                        flow { emit(getRootsForProvider(resolveInfo)) }.flowOn(ioDispatcher)
                    }
                // TODO: combine waits for every flow to finish. Change this to incrementally add
                // new roots as they load.
                combine(rootFlows) { rootsLists -> rootsLists.flatMap { it } }
            }
        }

    private fun getRootsForProvider(resolveInfo: ResolveInfo): List<Root> {
        val roots = mutableListOf<Root>()
        val info = resolveInfo.providerInfo
        try {
            val rootsUri = DocumentsContract.buildRootsUri(info.authority)
            Log.d("DocumentProviderRepository", "Querying roots: $rootsUri")
            val cursor = application.contentResolver.query(rootsUri, null, null, null, null)
            cursor?.use {
                val rootIdIndex = it.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_ROOT_ID)
                val documentIdIndex =
                    it.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                val titleIndex = it.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_TITLE)
                while (it.moveToNext()) {
                    val rootId = it.getString(rootIdIndex)
                    val documentId = it.getString(documentIdIndex)
                    val title = it.getString(titleIndex)

                    val type =
                        when (info.authority) {
                            "com.android.providers.media.documents" -> {
                                if (rootId == "files_root") RootType.RECENTS else RootType.GENERIC
                            }
                            "com.android.providers.downloads.documents" -> {
                                if (rootId == "downloads") RootType.DOWNLOADS else RootType.GENERIC
                            }
                            // TODO: Distinquish between USB and SD card roots.
                            "com.android.externalstorage.documents" -> {
                                if (rootId == "primary") RootType.PRIMARY else RootType.USB
                            }
                            else -> RootType.GENERIC
                        }
                    roots.add(Root(info.authority, rootId, documentId, title, type))
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentProviderRepository", "Failed to load roots for ${info.authority}", e)
        }
        return roots
    }
}
