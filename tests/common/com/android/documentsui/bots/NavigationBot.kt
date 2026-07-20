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

package com.android.documentsui.bots

import android.annotation.LayoutRes
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.AllRoots
import com.android.documentsui.BaseActivity
import com.android.documentsui.DocumentsApplication.getProvidersCache
import com.android.documentsui.base.UserId

/**
 * A bot responsible for programmatic navigation within the DocumentsUI application. Bypasses UI
 * interactions to provide faster and more stable navigation for functional tests.
 */
class NavigationBot(device: UiDevice, context: Context, timeout: Long, @LayoutRes layoutId: Int?) :
    Bots.BaseBot(device, context, timeout, layoutId) {

    private var cachedRoots: AllRoots? = null

    /** Read and store all roots and shortcuts currently available in the providers cache. */
    private fun cacheRoots() {
        val providersCache = getProvidersCache(mContext)
        val roots = providersCache.getRootsBlocking()
        val shortcuts = providersCache.getShortcutsForUser(UserId.DEFAULT_USER)
        cachedRoots = AllRoots(roots, shortcuts)
    }

    /**
     * Programmatically switches the root to the one with the given label.
     *
     * @param label The label of the root to switch to.
     * @param scenario The active ActivityScenario required to call the activity methods.
     * @param cachedRoots The pre-cached collection of roots and shortcuts.
     * @throws AssertionError if the root with the given label is not found.
     */
    fun switchRoot(label: String, scenario: ActivityScenario<out BaseActivity>) {
        if (cachedRoots == null) {
            cacheRoots()
        }
        val rootsAndShortcuts =
            checkNotNull(cachedRoots) { "cachedRoots is null. Cannot load roots and shortcuts." }

        // Try standard roots first
        val roots = rootsAndShortcuts.roots.filter { it.title == label }
        val shortcuts = rootsAndShortcuts.shortcuts.filter { it.folderTitle == label }

        if (roots.isNotEmpty()) {
            scenario.onActivity { activity -> activity.onRootPicked(roots[0]) }
        } else if (shortcuts.isNotEmpty()) {
            scenario.onActivity { activity -> activity.onShortcutPicked(shortcuts[0]) }
        } else {
            throw AssertionError("Root with label '$label' not found across all providers.")
        }

        // Ensure UI thread finishes updating prior to returning to avoid race conditions
        mDevice.waitForIdle()
        mBots.main.waitForWindowTitle(label)
    }

    /**
     * Programmatically opens and enters a new folder with the given label.
     *
     * @param label The label of the folder to open.
     * @param scenario The active ActivityScenario required to call the activity methods.
     * @throws AssertionError if the folder with the given label is not found in the current
     *   directory.
     */
    fun openFolder(label: String, scenario: ActivityScenario<out BaseActivity>) {
        scenario.onActivity { activity ->
            val model = activity.injector.model
            val folder =
                model.modelIds
                    .mapNotNull { model.getDocument(it) }
                    .find { it.isDirectory && it.displayName == label }

            if (folder != null) {
                activity.injector.actions.openContainerDocument(folder)
            } else {
                throw AssertionError("Folder with label '$label' not found in current directory.")
            }
        }

        // Ensure UI thread finishes updating prior to returning to avoid race conditions
        mDevice.waitForIdle()
        mBots.main.waitForWindowTitle(label)
    }
}
