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
package com.android.documentsui.dirlist

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.util.Log
import android.view.MenuItem
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.android.documentsui.ConsentCallbacks
import com.android.documentsui.R
import com.android.documentsui.SummaryConsentFragment
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Menus
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.prefs.LocalPreferences
import com.android.documentsui.util.FlagUtils.Companion.isUseFileSummaryEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the state of the file summary provider, detailing its availability and whether it's
 * currently active for the user.
 *
 * This sealed interface covers all possible conditions, from the feature being globally disabled to
 * being available and toggled by the user.
 */
sealed interface SummaryProviderState {
    /**
     * The initial state before the provider's status has been determined. The UI should typically
     * wait or show a loading state.
     */
    object Initializing : SummaryProviderState

    /**
     * The state when the feature is disabled via a global feature flag (`isUseFileSummaryEnabled`).
     * In this state, all summary-related UI and logic should be hidden and inactive.
     */
    object FlagDisabled : SummaryProviderState

    /**
     * The state when the summary provider APK is not installed or its component is disabled on the
     * device. The feature is unavailable and cannot be enabled by the user. All summary-related UI
     * should be hidden.
     */
    object ProviderUnavailable : SummaryProviderState

    /**
     * The state when the provider is installed and the feature flag is enabled. This means the
     * feature is available for the user to toggle on or off.
     *
     * @property isUserEnabled Indicates the user's current choice. This is determined by a
     *   combination of the provider's reported status (via `Root.FLAG_EMPTY`) and the user's
     *   preference stored in `LocalPreferences`. If `true`, the summary is active. If `false`, the
     *   summary is inactive, but the user can choose to enable it through the UI.
     */
    data class Available(val isUserEnabled: Boolean) : SummaryProviderState
}

/** Interface used to launch the dialog, used to override the dialog for tests. */
fun interface ConsentDialogLauncher {
    fun show(fm: FragmentManager, title: String, message: String, callbacks: ConsentCallbacks)
}

/**
 * Manages the state of the local summary provider.
 *
 * This class is responsible for determining if the summary provider is enabled and notifying
 * listeners of any state changes.
 */
open class SummaryProviderManager(
    private val context: Context,
    private val scope: CoroutineScope,
    val authorityUri: Uri?,
    /** The function that shows the consent dialog, used to override for tests. */
    private val dialogLauncher: ConsentDialogLauncher,
) {
    /** Secondary constructor used by Java to ignore the [dialogLauncher] parameter. */
    constructor(
        context: Context,
        scope: CoroutineScope,
        authorityUri: Uri?,
    ) : this(
        context,
        scope,
        authorityUri,
        ConsentDialogLauncher { fm, t, m, callbacks ->
            SummaryConsentFragment.show(fm, context, t, m, callbacks)
        },
    ) {}

    private val _state = MutableStateFlow<SummaryProviderState>(SummaryProviderState.Initializing)
    val state: StateFlow<SummaryProviderState> = _state

    // Override for tests.
    private var overrideConsentTitle: String? = null
    private var overrideConsentMessage: String? = null
    private var overrideShowConsentDialog: Boolean? = null

    val authority: String? = authorityUri?.authority
    var rootDocumentId: String? = null

    private var contentObserver: ContentObserver? = null
    private val contentResolver = context.contentResolver

    /** isEnabledFlow emits a boolean indicating whether summary is enabled or disabled */
    val isEnabledFlow: StateFlow<Boolean> =
        state
            .map { currentState ->
                currentState is SummaryProviderState.Available && currentState.isUserEnabled
            }
            .stateIn(
                scope = scope,
                // We want this flow to start running ASAP since there are places that just look at
                // its value without collecting.
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    /**
     * isEnabledLiveData is the LiveData version of isEnabledFlow, provided here to make it easier
     * for java code to observe the enabled/disabled state of summary.
     */
    val isEnabledLiveData: LiveData<Boolean> = isEnabledFlow.asLiveData()

    companion object {
        private const val TAG = "SummaryProviderManager"
    }

    /** Starts monitoring the summary provider's state. */
    open fun start() {
        if (!isUseFileSummaryEnabled()) {
            _state.value = SummaryProviderState.FlagDisabled
            return
        }
        Log.d(TAG, "Authority: $authority - $authorityUri")
        if (authority.isNullOrEmpty() || authorityUri == Uri.EMPTY) {
            _state.value = SummaryProviderState.ProviderUnavailable
            return
        }

        startContentObserver()

        // Fetch the initial state.
        scope.launch { updateState() }
    }

    /**
     * The ContentObserver listens for changes to the provider's roots URI. The summary provider
     * (Context Engine) should notify this URI when its state changes (e.g., when the user grants or
     * revokes consent).
     */
    private fun startContentObserver() {
        try {
            // Stop any potentially existing/running observer.
            stop()
            val rootsUri = DocumentsContract.buildRootsUri(authority!!)
            val contentObserver =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        scope.launch {
                            if (DEBUG) Log.d(TAG, "ContentObserver.onChange: uri=$uri")
                            updateState()
                        }
                    }
                }
            this.contentObserver = contentObserver
            contentResolver.registerContentObserver(rootsUri, true, contentObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start observer for summary provider: $authority", e)
        }
    }

    /** Stops monitoring the summary provider's state. */
    fun stop() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    /**
     * Checks the current state of the summary provider and updates the state flow. This involves
     * querying the provider's roots and checking its flags.
     */
    private suspend fun updateState() {
        if (authority.isNullOrEmpty()) {
            _state.value = SummaryProviderState.ProviderUnavailable
            return
        }

        withContext(Dispatchers.IO) {
            val rootsUri = DocumentsContract.buildRootsUri(authority)
            val projection =
                arrayOf(Root.COLUMN_FLAGS, Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID)

            try {
                val rootId = DocumentsContract.getRootId(authorityUri)
                val cursor = contentResolver.query(rootsUri, projection, null, null, null)
                if (cursor == null) {
                    Log.w(TAG, "Summary provider $authority returned null, assuming disabled")
                    _state.value = SummaryProviderState.ProviderUnavailable
                    return@withContext
                }
                val userHasEnabledInSettings = LocalPreferences.isSummaryEnabled(context)

                cursor.use {
                    while (it.moveToNext()) {
                        val currentRootId =
                            it.getString(it.getColumnIndexOrThrow(Root.COLUMN_ROOT_ID))
                        if (currentRootId != rootId) {
                            continue
                        }
                        val flags = it.getInt(it.getColumnIndexOrThrow(Root.COLUMN_FLAGS))
                        // The FLAG_EMPTY is used by the provider to signal that it's available,
                        // however it's disabled. User can enable by choosing to display the summary
                        // column in the menu.
                        val providerHasConsent = (flags and Root.FLAG_EMPTY) == 0
                        val isEffectivelyEnabled = providerHasConsent && userHasEnabledInSettings
                        _state.value =
                            SummaryProviderState.Available(isUserEnabled = isEffectivelyEnabled)
                        rootDocumentId =
                            it.getString(it.getColumnIndexOrThrow(Root.COLUMN_DOCUMENT_ID))
                        return@withContext
                    }
                }
                Log.w(TAG, "Root $rootId not found in $authority, assuming disabled")
                _state.value = SummaryProviderState.ProviderUnavailable
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query summary provider: $authority, assuming disabled", e)
                _state.value = SummaryProviderState.ProviderUnavailable
            }
        }
    }

    private suspend fun notifyProvider() {
        if (authority.isNullOrEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            val rootDocUri = DocumentsContract.buildDocumentUri(authority, rootDocumentId)
            val projection = arrayOf(Root.COLUMN_DOCUMENT_ID)
            try {
                val cursor = contentResolver.query(rootDocUri, projection, null, null, null)
                cursor?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failure notifying the provider: $authority for enabling it", e)
            }
        }
    }

    /**
     * Returns true if the summary provider is currently enabled. This is a convenience method for
     * one-time checks. For UI components that need to react to state changes, it's better to
     * collect the `state` flow.
     */
    fun isEnabled(): Boolean = isEnabledFlow.value

    @VisibleForTesting
    fun userSwitchSummaryEnabled() {
        LocalPreferences.setSummaryConsent(context, LocalPreferences.CONSENT_ACCEPTED)
        _state.value = SummaryProviderState.Available(isUserEnabled = true)
        // We only notify for enablement, so the provider can fully enable itself.
        scope.launch { notifyProvider() }
    }

    @VisibleForTesting
    fun userSwitchSummaryDisabled() {
        LocalPreferences.setSummaryConsent(context, LocalPreferences.CONSENT_UNKNOWN)
        _state.value = SummaryProviderState.Available(isUserEnabled = false)
    }

    /** Returns true if we should show the consent dialog proactively at start up. */
    fun shouldShowStartupConsent(): Boolean {
        if (!isUseFileSummaryEnabled()) {
            return false
        }

        // Check if it's configured to request consent.
        val showConsent =
            overrideShowConsentDialog
                ?: context.resources.getBoolean(R.bool.show_summary_consent_dialog)
        if (!showConsent) {
            return false
        }

        // If the user has already enabled the feature, we don't need to ask for consent.
        if (LocalPreferences.isSummaryEnabled(context)) {
            return false
        }

        val status = LocalPreferences.getSummaryConsent(context)

        return when (status) {
            LocalPreferences.CONSENT_UNKNOWN -> true
            LocalPreferences.CONSENT_REJECTED -> false
            LocalPreferences.CONSENT_DEFERRED -> false // TODO: Define when to ask again.
            else -> false
        }
    }

    /** Shows the consent dialog proactively at start up. */
    fun showStartupConsent(fragmentManager: FragmentManager) {
        val title = overrideConsentTitle ?: context.getString(R.string.summary_consent_title)
        val message = overrideConsentMessage ?: context.getString(R.string.summary_consent_message)
        dialogLauncher.show(
            fragmentManager,
            title,
            message,
            ConsentCallbacks(
                onEnable = { userSwitchSummaryEnabled() },
                onCancel = {
                    LocalPreferences.setSummaryConsent(context, LocalPreferences.CONSENT_REJECTED)
                },
                onRemindLater = {
                    LocalPreferences.setSummaryConsent(context, LocalPreferences.CONSENT_DEFERRED)
                },
            ),
        )
    }

    /**
     * Handles the click on the "Show summary column" menu item. If the summary is already enabled,
     * it disables it. Otherwise, it shows the consent dialog.
     */
    fun onShowSummaryMenuClicked(fragmentManager: FragmentManager) {
        if (LocalPreferences.isSummaryEnabled(context)) {
            // Disabling the summary column.
            userSwitchSummaryDisabled()
            return
        }

        val showConsent =
            overrideShowConsentDialog
                ?: context.resources.getBoolean(R.bool.show_summary_consent_dialog)

        if (!showConsent) {
            // This means that the summary feature doesn't require consent.
            userSwitchSummaryEnabled()
            return
        }

        // Enabling the summary column.
        val title = overrideConsentTitle ?: context.getString(R.string.summary_consent_title)
        val message = overrideConsentMessage ?: context.getString(R.string.summary_consent_message)
        dialogLauncher.show(
            fragmentManager,
            title,
            message,
            ConsentCallbacks(
                onEnable = { userSwitchSummaryEnabled() },
                // The "Don't ask me again" button is only displayed if the dialog is shown
                // proactively.
                onCancel = null,
                onRemindLater = {
                    // When going via menu, we set timestamp to 0 so it's stale immediately.
                    LocalPreferences.setSummaryConsent(
                        context,
                        LocalPreferences.CONSENT_DEFERRED,
                        0,
                    )
                },
            ),
        )
    }

    fun updateMenuState(menuItem: MenuItem?) {
        // This menu item maybe not be available before the flag is enabled, but if it exists we
        // force it be hidden when the flag is disabled.
        if (menuItem == null) {
            return
        }
        val currentState = state.value
        when (currentState) {
            is SummaryProviderState.Initializing -> Menus.setEnabledAndVisible(menuItem, false)
            is SummaryProviderState.FlagDisabled -> Menus.setEnabledAndVisible(menuItem, false)
            is SummaryProviderState.ProviderUnavailable ->
                Menus.setEnabledAndVisible(menuItem, false)

            is SummaryProviderState.Available -> {
                Menus.setEnabledAndVisible(menuItem, true)
                if (currentState.isUserEnabled) {
                    menuItem.setTitle(R.string.option_hide_summary_column)
                } else {
                    menuItem.setTitle(R.string.option_show_summary_column)
                }
            }
        }
    }

    /** Force the consent dialog content to avoid relying on the RRO. */
    @VisibleForTesting
    fun setConsentMessage(title: String, message: String, showConsent: Boolean) {
        overrideConsentTitle = title
        overrideConsentMessage = message
        overrideShowConsentDialog = showConsent
    }

    @VisibleForTesting
    fun setShowConsentDialogForTest(show: Boolean) {
        overrideShowConsentDialog = show
    }

    /** Force the local state for unit tests. */
    @VisibleForTesting
    fun setStateForTest(state: SummaryProviderState) {
        _state.value = state
    }
}

/** Whether the given root should show the summary column. It defaults to false. */
fun displaySummaryForRoot(
    summaryProviderManager: SummaryProviderManager?,
    root: RootInfo?,
    currentDocument: DocumentInfo?,
): Boolean {
    if (!isUseFileSummaryEnabled()) {
        // The condition before this flag was to display for Downloads and Recents.
        return root != null && (root.isRecents || root.isDownloads)
    }
    // Defaults to false.
    if (root == null || summaryProviderManager == null) {
        return false
    }
    // When displaying zip even within a root that shows summary, we don't display the summary.
    if (currentDocument?.isInArchive == true) {
        return false
    }
    if (summaryProviderManager.isEnabled() && (root.isLocalProvider || root.isRecents)) {
        return true
    }
    return false
}
