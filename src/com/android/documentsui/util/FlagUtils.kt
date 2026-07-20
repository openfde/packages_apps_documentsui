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

package com.android.documentsui.util

import android.content.flags.Flags.enableContentProviderClientAnrOnCancel
import android.provider.Flags.enableDocumentsTrashApi
import android.provider.Flags.enableSyncState
import android.util.Log
import com.android.documentsui.flags.Flags
import com.android.modules.utils.build.SdkLevel

/** Wrap aconfig generated flags to allow us to override flags in tests. */
class FlagUtils
private constructor(private val overrides: MutableMap<String, Boolean> = mutableMapOf()) {
    companion object {
        private const val TAG = "FlagUtils"
        @Volatile private var instance: FlagUtils = FlagUtils()
        private val overridableFlags =
            listOf(
                Flags.FLAG_CLOUD_FEATURES,
                Flags.FLAG_DESKTOP_FILE_HANDLING_RO,
                Flags.FLAG_DESKTOP_UX_PHASE_2_RO,
                Flags.FLAG_ENABLE_TRASH_FLOW_RO,
                Flags.FLAG_SINGLE_CLICK_TO_SELECT,
                Flags.FLAG_USE_MATERIAL3,
                // TODO(b/433858983): Make peek overridable once all flag evaluations use FlagUtils.
                // Tests need to use RequiresFlagsEnabled and CheckFlagsRule until then.
                // Flags.FLAG_USE_PEEK_PREVIEW_RO,
                Flags.FLAG_USE_SEARCH_V2_READ_ONLY,
                Flags.FLAG_VISUAL_SIGNALS_RO,
                Flags.FLAG_ZIP_NG_RO,
                Flags.FLAG_HOME_SCREEN_FILES_RO,
                Flags.FLAG_USE_FILE_SUMMARY,
                Flags.FLAG_USE_LOCAL_SEARCH_PROVIDER,
                Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
                Flags.FLAG_DRAGS_FROM_OTHER_APPS,
                Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER,
                Flags.FLAG_USE_NEW_OPEN_WITH,
                Flags.FLAG_GET_INFO_DIALOG,
                Flags.FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
            )

        @JvmStatic
        fun getInstance(): FlagUtils {
            return instance
        }

        @JvmStatic
        fun isUseMaterial3FlagEnabled(): Boolean {
            // Material3 flag is special, since it has resources behind the flag we can never enable
            // it when it was disabled at build time (i.e. at build time the assets were stripped).
            if (!Flags.useMaterial3()) {
                return false
            }
            return getInstance().overrides.getOrDefault(Flags.FLAG_USE_MATERIAL3, false)
        }

        @JvmStatic
        fun isZipNgFlagEnabled(): Boolean {
            val flag = getInstance().overrides.getOrDefault(Flags.FLAG_ZIP_NG_RO, Flags.zipNgRo())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isSearchV2Enabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.useSearchV2ReadOnly())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        private fun isCloudFeaturesFlagEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_CLOUD_FEATURES, Flags.cloudFeatures())
        }

        @JvmStatic
        fun isDesktopFileHandlingFlagEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_DESKTOP_FILE_HANDLING_RO, Flags.desktopFileHandlingRo())
        }

        @JvmStatic
        fun isDesktopUxPhase2FlagEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_DESKTOP_UX_PHASE_2_RO, Flags.desktopUxPhase2Ro())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isSingleClickToSelectEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_SINGLE_CLICK_TO_SELECT, Flags.singleClickToSelect())
        }

        @JvmStatic
        fun isVisualSignalsFlagEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_VISUAL_SIGNALS_RO, Flags.visualSignalsRo())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isUsePeekPreviewFlagEnabled(): Boolean {
            // TODO(b/433858983): Make peek overridable once all flag evaluations use FlagUtils
            // i.e. once the Flags.usePeekPreviewRo() condition in FilesActivity is removed.
            return Flags.usePeekPreviewRo() && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isTrashFlowEnabled(): Boolean {
            // TODO(b/457843307): Replace with isAtLeastC when the new SDK is finalised.
            if (!SdkLevel.isAtLeastB()) {
                return false
            }

            // If API flag is not enabled, then trash flow will be disabled
            if (!enableDocumentsTrashApi()) {
                return false
            }

            // Trash feature will be available only when use_material_3 flag is enabled
            if (!isUseMaterial3FlagEnabled()) {
                return false
            }

            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.enableTrashFlowRo())
        }

        @JvmStatic
        fun isSyncStateEnabled(): Boolean {
            // An SDK check shouldn't be required (go/android-api-flagging-faq#api-finalization)
            // because enableSyncState() should default to false when it doesn't exist on the
            // current SDK version on the device. However, this doesn't work on Android U
            // (API level 34) and a NoSuchMethodError will be thrown if enableSyncState() is called.
            // This sync state API is targeting API level 37, however the version bump hasn't
            // occurred yet so guard to ensure that the API level is at least 36 (Android B).
            // TODO(b/458129770): Replace with isAtLeastC when the new SDK is finalised.
            if (!SdkLevel.isAtLeastB()) {
                Log.w(TAG, "SDK version is too low for the sync state feature")
                return false
            }

            // The API flag that guards the static sync state constants needs to be enabled. If this
            // function doesn't exist on the current SDK version on the device, it will default to
            // false (on Android versions that are not U).
            // TODO(b/469214605): After API 37 finalisation, guard with SDK version instead of
            // enableSyncState.
            if (!enableSyncState()) {
                Log.w(TAG, "enableSyncState() returns false")
                return false
            }

            // The use_material_3 flag needs to be enabled.
            if (!isUseMaterial3FlagEnabled()) {
                return false
            }

            // The cloud_features flag needs to be enabled.
            return isCloudFeaturesFlagEnabled()
        }

        @JvmStatic
        fun isMovingContentIntoPrivateSpaceEnabled(): Boolean {
            return SdkLevel.isAtLeastB() &&
                android.multiuser.Flags.enableMovingContentIntoPrivateSpace()
        }

        @JvmStatic
        fun isSupportVisibleBackgroundUserFlagEnabled(): Boolean {
            return Flags.supportVisibleBackgroundUser()
        }

        @JvmStatic
        fun isHomeScreenFilesFlagEnabled(): Boolean {
            return isUseMaterial3FlagEnabled() &&
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.homeScreenFilesRo())
        }

        @JvmStatic
        fun isUseFileSummaryEnabled(): Boolean {
            return isUseMaterial3FlagEnabled() &&
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_USE_FILE_SUMMARY, Flags.useFileSummary())
        }

        @JvmStatic
        fun isUseLocalSearchProviderEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(
                        Flags.FLAG_USE_LOCAL_SEARCH_PROVIDER,
                        Flags.useLocalSearchProvider(),
                    )
            return flag && isSearchV2Enabled()
        }

        @JvmStatic
        fun isUseAllfilesRootForRecentsEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(
                        Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
                        Flags.useAllfilesRootForRecents(),
                    )
            return flag && isSearchV2Enabled()
        }

        @JvmStatic
        fun isDragsFromOtherAppsEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_DRAGS_FROM_OTHER_APPS, Flags.dragsFromOtherApps())
        }

        @JvmStatic
        fun isUseApprovedDocumentHandlerEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(
                        Flags.FLAG_USE_APPROVED_DOCUMENT_HANDLER,
                        Flags.useApprovedDocumentHandler(),
                    )
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isUseNewOpenWithEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_USE_NEW_OPEN_WITH, Flags.useNewOpenWith())
            return flag && isDesktopFileHandlingFlagEnabled()
        }

        @JvmStatic
        fun isGetInfoDialogEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_GET_INFO_DIALOG, Flags.getInfoDialog())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isContentProviderClientAnrOnCancelEnabled(): Boolean {
            // TODO(b/457843307): Replace with isAtLeastC when the new SDK is
            // finalised.
            return SdkLevel.isAtLeastB() && enableContentProviderClientAnrOnCancel()
        }

        @JvmStatic
        fun isIncludeRemoteRootsInRecentsEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(
                        Flags.FLAG_INCLUDE_REMOTE_ROOTS_IN_RECENTS,
                        Flags.includeRemoteRootsInRecents(),
                    )
            return flag && isSearchV2Enabled()
        }
    }

    fun setOverride(flag: String, state: Boolean) {
        if (!overridableFlags.contains(flag)) {
            throw Exception("Flag not supported: $flag")
        }
        if (flag == Flags.FLAG_USE_MATERIAL3 && state && !Flags.useMaterial3()) {
            throw Exception("Cannot enable FLAG_USE_MATERIAL3 when it's disabled at build time.")
        }
        overrides[flag] = state
        Log.d(TAG, "override flag $flag to $state")
    }

    fun restoreOverrideState(state: Map<String, Boolean>) {
        overrides.clear()
        overrides.putAll(state)
        Log.d(TAG, "restore flag overrides to $overrides")
    }

    fun copyOverrideState(): Map<String, Boolean> {
        return overrides.toMap()
    }
}
