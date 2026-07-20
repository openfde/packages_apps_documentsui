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

/**
 * Stub implementation of [NetworkMonitor] that does nothing. Used when the cloudFeatures flag is
 * off.
 */
open class NetworkMonitorStub : NetworkMonitor {
    override val isOnline: Boolean = true

    override fun init() {}

    override fun teardown() {}

    override fun addNetworkListener(listener: NetworkMonitor.NetworkListener) {}

    override fun removeNetworkListener(listener: NetworkMonitor.NetworkListener) {}
}
