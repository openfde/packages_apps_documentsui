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

package com.android.documentsui.approveddochandlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.MimeTypes
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.UserId
import com.android.documentsui.util.CrossProfileUtils
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data class representing an approved document handler app.
 *
 * @property componentName The component name of the handler activity.
 * @property label The user-visible label of the handler.
 * @property isButton Whether the handler registered to be displayed as a button.
 * @property icon The drawable icon for the handler.
 * @property isEnabled Whether the handler should be enabled.
 */
data class ApprovedDocHandler(
    val componentName: ComponentName,
    val label: String,
    val isButton: Boolean,
    val icon: Drawable?,
    val isEnabled: Boolean = true,
)

/**
 * Data class representing the status of an approved document handler.
 *
 * @property isSupported Whether this handler supports the action and MIME type combination.
 * @property isOutdated Whether this handler information is outdated. This is set to true when the
 *   package is added, changed, or replaced, indicating that the handler info needs to be
 *   re-fetched.
 * @property handler The handler info, if supported.
 */
data class HandlerStatus(
    val isSupported: Boolean,
    val isOutdated: Boolean,
    val handler: ApprovedDocHandler? = null,
)

/**
 * A class for discovering approved document handlers.
 *
 * This class queries the system for activities within the approved document handler apps, that can
 * handle the current selection.
 *
 * This class and all methods in this class will only be called when the flag
 * `isUseApprovedDocumentHandlerEnabled` is true.
 */
class ApprovedDocHandlers(
    private val applicationContext: Context,
    private val injector: Injector<*>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /**
     * A cache to store the results of handler queries. The first key is the user, the second key is
     * a combination of intent action and MIME type, and the value is a map of [ComponentName] to
     * [HandlerStatus].
     */
    private val cache =
        MutableStateFlow<Map<UserId, Map<String, Map<ComponentName, HandlerStatus>>>>(emptyMap())

    private val _updateEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updateEvents: SharedFlow<Unit> = _updateEvents

    /** A set of package names that are approved document handlers, loaded from resources. */
    private val approvedPackages: Set<String> = getPackageNames().toSet()

    /**
     * A set of keys of the cache that are currently being loaded. The key is a combination of
     * intent action and MIME type.
     */
    private val loadingKeys = Collections.synchronizedSet(mutableSetOf<String>())

    private val isMonitoring = AtomicBoolean(false)

    private sealed interface CacheUpdateOp {
        data class Remove(val packageName: String, val userId: UserId) : CacheUpdateOp

        data class MarkOutdated(val packageName: String, val userId: UserId) : CacheUpdateOp

        data class Add(
            val userId: UserId,
            val key: String,
            val handlers: List<ApprovedDocHandler>,
        ) : CacheUpdateOp
    }

    companion object {
        private const val TAG = "ApprovedDocHandlers"
        public const val AS_BUTTON_METADATA_KEY = "android.approvedtarget.as_button"
    }

    /**
     * Starts monitoring the approved document handler packages for changes.
     *
     * This method is called when the approved document handlers is requested and cached the first
     * time, then it keeps monitoring in the background for changes to the approved document handler
     * packages and updates the cache accordingly.
     */
    private fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            viewModelScope.launch(ioDispatcher) {
                createPackageChangeFlow().collect { op -> applyCacheUpdate(op) }
            }
        }
    }

    /**
     * Applies a [CacheUpdateOp] to the cache sequentially.
     *
     * @param op The [CacheUpdateOp] to apply.
     */
    private fun applyCacheUpdate(op: CacheUpdateOp) {
        when (op) {
            is CacheUpdateOp.Remove -> {
                updateCacheForPackage(op.packageName, op.userId) { status ->
                    status.copy(isSupported = false, isOutdated = false)
                }
            }
            is CacheUpdateOp.MarkOutdated -> {
                updateCacheForPackage(op.packageName, op.userId) { status ->
                    status.copy(
                        isOutdated = true,
                        handler = status.handler?.copy(isEnabled = false),
                    )
                }
            }
            is CacheUpdateOp.Add -> {
                cache.update { currentCache ->
                    val newHandlersMap = mutableMapOf<ComponentName, HandlerStatus>()
                    approvedPackages.forEach { packageName ->
                        newHandlersMap[ComponentName(packageName, "")] =
                            HandlerStatus(isSupported = false, isOutdated = false)
                    }
                    op.handlers.forEach { handler ->
                        newHandlersMap[handler.componentName] =
                            HandlerStatus(isSupported = true, isOutdated = false, handler = handler)
                    }
                    val userCache = currentCache[op.userId] ?: emptyMap()
                    val updatedUserCache = userCache + (op.key to newHandlersMap)
                    currentCache + (op.userId to updatedUserCache)
                }
            }
        }
        _updateEvents.tryEmit(Unit)
    }

    private fun updateCacheForPackage(
        packageName: String,
        userId: UserId,
        updateStatus: (HandlerStatus) -> HandlerStatus,
    ) {
        cache.update { currentCache ->
            val userCache = currentCache[userId] ?: return@update currentCache
            val updatedUserCache = userCache.mapValues { (_, handlers) ->
                handlers.mapValues { (component, status) ->
                    if (component.packageName == packageName) {
                        updateStatus(status)
                    } else {
                        status
                    }
                }
            }
            currentCache + (userId to updatedUserCache)
        }
    }

    /**
     * Creates a [Flow] that emits a [CacheUpdateOp] when an approved document handler package is
     * added, removed, changed, or replaced.
     *
     * @return A [Flow] that emits a [CacheUpdateOp].
     */
    private fun createPackageChangeFlow(): Flow<CacheUpdateOp> = callbackFlow {
        val callback =
            object : LauncherApps.Callback() {
                override fun onPackageAdded(packageName: String, userHandle: UserHandle) {
                    if (packageName in approvedPackages) {
                        trySend(CacheUpdateOp.MarkOutdated(packageName, UserId.of(userHandle)))
                    }
                }

                override fun onPackageChanged(packageName: String, userHandle: UserHandle) {
                    if (packageName in approvedPackages) {
                        trySend(CacheUpdateOp.MarkOutdated(packageName, UserId.of(userHandle)))
                    }
                }

                override fun onPackageRemoved(packageName: String, userHandle: UserHandle) {
                    if (packageName in approvedPackages) {
                        trySend(CacheUpdateOp.Remove(packageName, UserId.of(userHandle)))
                    }
                }

                override fun onPackagesAvailable(
                    packageNames: Array<out String>,
                    userHandle: UserHandle,
                    replacing: Boolean,
                ) {
                    // Handled by individual package events.
                }

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>,
                    userHandle: UserHandle,
                    replacing: Boolean,
                ) {
                    // Handled by individual package events.
                }
            }
        CrossProfileUtils.registerPackageUpdateCallback(applicationContext, callback)
        awaitClose {
            CrossProfileUtils.unregisterPackageUpdateCallback(applicationContext, callback)
        }
    }

    /**
     * Retrieves the package names of approved document handlers from RRO.
     *
     * @return An array of package name strings.
     */
    private fun getPackageNames(): Array<String> {
        val approvedDocHandlers: Array<String> =
            applicationContext.resources.getStringArray(R.array.approved_document_handlers)
                ?: emptyArray()
        if (DEBUG) {
            Log.d(
                TAG,
                "ApprovedDocHandlers ${R.array.approved_document_handlers} : " +
                    approvedDocHandlers.contentToString(),
            )
        }
        return approvedDocHandlers
    }

    /**
     * Creates and configures an [Intent] for finding approved document handlers.
     *
     * For a single selected item, it creates an [Intent.ACTION_SEND] intent. For multiple items, it
     * uses [Intent.ACTION_SEND_MULTIPLE]. The MIME type is set based on the selection, and a
     * specific category is added to filter for approved handlers.
     *
     * @param selectionDetails The details of the current document selection.
     * @return A configured [Intent] ready to be used for querying the [PackageManager].
     */
    private fun createIntentForSelection(selectionDetails: MenuManager.SelectionDetails): Intent {
        val intent =
            if (selectionDetails.size() == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = selectionDetails.mimeTypes()?.firstOrNull() ?: "*/*"
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    if (
                        selectionDetails.mimeTypes() == null ||
                            selectionDetails.mimeTypes().isEmpty()
                    ) {
                        type = "*/*"
                    } else if (selectionDetails.mimeTypes().isNotEmpty()) {
                        type = MimeTypes.findCommonMimeType(selectionDetails.mimeTypes().toList())
                    }
                }
            }
        intent.addCategory(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER)
        return intent
    }

    /**
     * Queries the [PackageManager] for activities that can handle the given [Intent].
     *
     * @param intent The [Intent] to query for.
     * @return A list of [ApprovedDocHandler] objects.
     */
    private fun queryApprovedHandlers(
        intent: Intent,
        pm: PackageManager,
    ): List<ApprovedDocHandler> {
        val resolveInfos =
            pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.GET_META_DATA,
            )
        return convertToHandlers(resolveInfos, pm)
    }

    /**
     * Retrieves a Flow of approved document handlers that can handle the given selection, and
     * updates the cache with the results.
     *
     * @param selectionDetails The details about the current selection of documents.
     * @return A Flow emitting a list of [ApprovedDocHandler]s that can handle the selection.
     */
    fun getApprovedDocHandlersFlow(
        selectionDetails: MenuManager.SelectionDetails
    ): Flow<List<ApprovedDocHandler>> {
        if (approvedPackages.isEmpty()) {
            return flowOf(emptyList())
        }

        val user = injector.actions?.selectedUser ?: UserId.CURRENT_USER
        startMonitoring()

        val intent = createIntentForSelection(selectionDetails)
        val pm = user.getPackageManager(applicationContext)
        val key = "${intent.action}:${intent.type}"
        val loadingKey = "$user:$key"

        return flow {
            cache.collect { currentCache ->
                val handlersMap = currentCache[user]?.get(key)
                if (handlersMap != null && handlersMap.values.none { it.isOutdated }) {
                    emit(handlersMap.values.mapNotNull { if (it.isSupported) it.handler else null })
                    return@collect
                }

                // If it's outdated or not in the cache, query the package manager.
                // If it's already loading, don't query again, just wait for the cache to update.
                if (loadingKeys.add(loadingKey)) {
                    viewModelScope.launch(ioDispatcher) {
                        try {
                            val approvedHandlers = queryApprovedHandlers(intent, pm)
                            applyCacheUpdate(CacheUpdateOp.Add(user, key, approvedHandlers))
                        } finally {
                            loadingKeys.remove(loadingKey)
                        }
                    }
                }

                emit(
                    handlersMap?.values?.mapNotNull { if (it.isSupported) it.handler else null }
                        ?: emptyList()
                )
            }
        }
    }

    /**
     * Converts a list of [ResolveInfo] objects to a list of [ApprovedDocHandler] objects.
     *
     * @param resolveInfos The list of [ResolveInfo] objects to convert.
     * @return A list of [ApprovedDocHandler] objects.
     */
    private fun convertToHandlers(
        resolveInfos: List<ResolveInfo>,
        pm: PackageManager,
    ): List<ApprovedDocHandler> {
        return buildList {
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo
                if (activityInfo.packageName in approvedPackages) {
                    val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
                    val label = activityInfo.loadLabel(pm)?.toString()
                    if (label == null) {
                        Log.w(
                            TAG,
                            "Approved doc handler ${componentName.flattenToString()} has no " +
                                "label, skipping.",
                        )
                        continue
                    }
                    val isButton: Boolean =
                        resolveInfo.activityInfo.metaData?.getBoolean(AS_BUTTON_METADATA_KEY) ==
                            true
                    val icon: Drawable? = if (isButton) activityInfo.loadIcon(pm) else null
                    add(ApprovedDocHandler(componentName, label, isButton, icon, true))
                }
            }
        }
    }
}
