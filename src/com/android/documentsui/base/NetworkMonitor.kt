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

import android.content.Context
import android.net.NetworkCapabilities
import android.util.Log
import com.android.documentsui.util.FlagUtils.Companion.isSyncStateEnabled
import com.google.common.annotations.VisibleForTesting

/**
 * Monitors network changes and updates its `NetworkListener`s on the UI thread whenever the
 * isOnline state changes.
 */
interface NetworkMonitor {
    interface NetworkListener {
        /** Called on the UI thread when the network state changes. */
        fun onNetworkStateChanged(isOnline: Boolean)
    }

    val isOnline: Boolean

    fun init()

    fun teardown()

    fun addNetworkListener(listener: NetworkListener)

    fun removeNetworkListener(listener: NetworkListener)

    companion object {
        private const val TAG = "NetworkMonitor"

        private var testInstance: NetworkMonitor? = null

        /** Used for tests to set a NetworkMonitor, with a custom isCurrentlyOnlineFun. */
        @VisibleForTesting
        @JvmStatic
        fun setTestInstance(instance: NetworkMonitor?) {
            testInstance = instance
        }

        /** Creates and initializes a NetworkMonitor instance. */
        @JvmStatic
        fun create(context: Context): NetworkMonitor {
            testInstance?.let {
                Log.i(TAG, "NetworkMonitor.create() returning testInstance")
                return it
            }
            return if (isSyncStateEnabled()) {
                NetworkMonitorImpl(context).apply { init() }
            } else {
                NetworkMonitorStub()
            }
        }

        /**
         * Creates and initializes a NetworkMonitor instance with a custom isCurrentlyOnline
         * function for testing, since NetworkCapabilities can't be mocked.
         */
        @JvmStatic
        @VisibleForTesting
        fun create(
            context: Context,
            isCurrentlyOnlineFun: (NetworkCapabilities) -> Boolean,
        ): NetworkMonitor {
            return if (isSyncStateEnabled()) {
                NetworkMonitorImpl(context, isCurrentlyOnlineFun).apply { init() }
            } else {
                NetworkMonitorStub()
            }
        }
    }
}
