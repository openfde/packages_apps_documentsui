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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import com.android.documentsui.base.NetworkMonitorImpl.Companion.defaultIsCurrentlyOnline
import com.google.common.annotations.VisibleForTesting

/**
 * Default implementation of [NetworkMonitor].
 *
 * The `isCurrentlyOnlineFun` is a function used to determine if the network is currently online
 * based on the `NetworkCapabilities`. Only test code will should set this method.
 */
open class NetworkMonitorImpl(
    context: Context,
    private val isCurrentlyOnlineFun: (NetworkCapabilities) -> Boolean,
) : NetworkMonitor {

    /** Secondary constructor that defaults to using [defaultIsCurrentlyOnline]. */
    constructor(context: Context) : this(context, ::defaultIsCurrentlyOnline)

    override var isOnline: Boolean = false

    private val handler = android.os.Handler(Looper.getMainLooper())

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager?

    private val networkListeners = ArrayList<NetworkMonitor.NetworkListener>()

    @VisibleForTesting
    @JvmField
    val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                onStateChanged(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                onStateChanged(isCurrentlyOnlineFun(networkCapabilities))
            }
        }

    override fun init() {
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager service not available.")
            isOnline = false
        } else {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            isOnline = if (capabilities != null) isCurrentlyOnlineFun(capabilities) else false
        }
    }

    override fun teardown() {
        if (connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    override fun addNetworkListener(listener: NetworkMonitor.NetworkListener) {
        networkListeners.add(listener)
    }

    override fun removeNetworkListener(listener: NetworkMonitor.NetworkListener) {
        networkListeners.remove(listener)
    }

    private fun onStateChanged(newIsOnline: Boolean) {
        if (newIsOnline != isOnline) {
            isOnline = newIsOnline
            for (listener in networkListeners) {
                handler.post { listener.onNetworkStateChanged(isOnline) }
            }
        }
    }

    companion object {
        private const val TAG = "NetworkMonitorImpl"

        fun defaultIsCurrentlyOnline(capabilities: NetworkCapabilities): Boolean {
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
