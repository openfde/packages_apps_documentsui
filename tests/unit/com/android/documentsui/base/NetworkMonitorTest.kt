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
import android.net.NetworkCapabilities
import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(AndroidJUnit4::class)
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
@RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
@EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
class NetworkMonitorTest {

    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var context: Context

    @Mock private lateinit var applicationContext: Context

    @Mock private lateinit var connectivityManager: ConnectivityManager

    @Mock private lateinit var activeNetwork: android.net.Network

    // Use an empty NetworkCapabilities because we can't mock NetworkCapabilities. It will be
    // ignored as the test isCurrentlyConnectedFunction is used instead.
    private val capabilities = NetworkCapabilities()

    @Before
    fun setUp() {
        reset(applicationContext) // Reset the mock
        `when`(context.applicationContext).thenReturn(applicationContext)
        `when`(applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(connectivityManager)
        `when`(connectivityManager.activeNetwork).thenReturn(activeNetwork)
        `when`(connectivityManager.getNetworkCapabilities(any())).thenReturn(capabilities)
    }

    @Test
    fun testCreate_connectivityManager_isNull() {
        // Return null instead of a ConnectivityManager.
        `when`(applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(null)
        // There should be no crash.
        val networkMonitor = NetworkMonitor.create(context)
        assertFalse(networkMonitor.isOnline)
    }

    @Test
    fun testCreate_network_isNull() {
        // Return null instead of a Network.
        `when`(connectivityManager.activeNetwork).thenReturn(null)
        // There should be no crash.
        val networkMonitor = NetworkMonitor.create(context)
        assertFalse(networkMonitor.isOnline)
    }

    @Test
    fun testCreate_registersCallbackAndGetsOnlineStatus() {
        // Set online.
        val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ -> true }

        val networkMonitor =
            NetworkMonitor.create(context, isCurrentlyConnectedFunction) as NetworkMonitorImpl
        verify(connectivityManager).registerDefaultNetworkCallback(networkMonitor.networkCallback)
        assertTrue(networkMonitor.isOnline)
    }

    @Test
    fun testCreate_registersCallbackAndGetsOfflineStatus() {
        // Set offline.
        val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ -> false }

        val networkMonitor =
            NetworkMonitor.create(context, isCurrentlyConnectedFunction) as NetworkMonitorImpl

        verify(connectivityManager).registerDefaultNetworkCallback(networkMonitor.networkCallback)
        assertFalse(networkMonitor.isOnline)
    }

    @Test
    fun testTeardown_unregistersCallback() {
        val networkMonitor = NetworkMonitor.create(context, { _ -> true }) as NetworkMonitorImpl
        networkMonitor.teardown()
        verify(connectivityManager).unregisterNetworkCallback(networkMonitor.networkCallback)
    }

    @Test
    fun testCallback_onCapabilitiesChanged_callsListeners() {
        val latch = CountDownLatch(1)
        val listener =
            object : NetworkMonitor.NetworkListener {
                override fun onNetworkStateChanged(isOnline: Boolean) {
                    if (isOnline) {
                        latch.countDown()
                    }
                }
            }

        // Set offline.
        var isOnline = false
        val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ -> isOnline }

        val networkMonitor =
            NetworkMonitor.create(context, isCurrentlyConnectedFunction) as NetworkMonitorImpl
        networkMonitor.addNetworkListener(listener)

        // Set online.
        isOnline = true

        // Call onCapabilitiesChanged method.
        networkMonitor.networkCallback.onCapabilitiesChanged(activeNetwork, capabilities)

        // Check that that status is online.
        assertTrue(networkMonitor.isOnline)

        // Wait for the listener to be called.
        latch.await(300, TimeUnit.MILLISECONDS)
    }

    @Test
    fun testCallback_onLost_callsListeners() {
        val latch = CountDownLatch(1)
        val listener =
            object : NetworkMonitor.NetworkListener {
                override fun onNetworkStateChanged(isOnline: Boolean) {
                    if (!isOnline) {
                        latch.countDown()
                    }
                }
            }

        // Set online.
        val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ -> true }

        val networkMonitor =
            NetworkMonitor.create(context, isCurrentlyConnectedFunction) as NetworkMonitorImpl
        networkMonitor.addNetworkListener(listener)

        // Call onLost method.
        networkMonitor.networkCallback.onLost(activeNetwork)
        assertFalse(networkMonitor.isOnline)

        // Wait for the listener to be called.
        latch.await(300, TimeUnit.MILLISECONDS)
    }

    @Test
    fun testCallback_whenStatusUnchanged_doesNotCallListeners() {
        val listener = mock(NetworkMonitor.NetworkListener::class.java)

        // Set offline.
        val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ -> false }

        val networkMonitor =
            NetworkMonitor.create(context, isCurrentlyConnectedFunction) as NetworkMonitorImpl
        networkMonitor.addNetworkListener(listener)

        // Call onLost method.
        networkMonitor.networkCallback.onLost(activeNetwork)

        // Check that that status is still offline.
        assertFalse(networkMonitor.isOnline)

        // Ensure that the listener was not called.
        verify(listener, never()).onNetworkStateChanged(any(Boolean::class.java))
    }
}
