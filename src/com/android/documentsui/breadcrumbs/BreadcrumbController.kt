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
package com.android.documentsui.breadcrumbs

import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.android.documentsui.NavigationViewManager.Breadcrumb
import com.android.documentsui.NavigationViewManager.Environment
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.State
import java.util.function.IntConsumer

/**
 * Manage the 2 breadcrumb in DocumentsUI:
 * * navigation breadcrumb: the breadcrumb shows for normal root/folder navigation.
 * * search breadcrumb: the breadcrumb shows when a file is selected in Recent root or search
 *   result.
 */
class BreadcrumbController(
    lifeCycleOwner: LifecycleOwner,
    private val model: BreadcrumbModel?,
    // Breadcrumb for the navigation.
    val navBreadcrumb: Breadcrumb,
    // Breadcrumb for the search (only available when search_v2 flag is ON).
    private val searchBreadcrumb: BreadcrumbView?,
    // The divider shows on top of the breadcrumb, used by both breadcrumbs, only available in
    // drawer layout with use_material3 flag ON.
    private val topDivider: View?,
) {

    private var searchBreadcrumbClickConsumer: IntConsumer? = null

    init {
        if (model != null && searchBreadcrumb != null) {
            model.pathData.observe(lifeCycleOwner, Observer { newPath -> onModelUpdated(newPath) })
            searchBreadcrumb.setClickConsumer { index ->
                searchBreadcrumbClickConsumer?.accept(index)
            }
        }
    }

    /** Setup navigation breadcrumb. */
    fun setupNavBreadcrumb(env: Environment, state: State, listener: IntConsumer) {
        navBreadcrumb.setup(env, state, listener)
    }

    private fun onModelUpdated(newValue: List<String>) {
        if (DEBUG) {
            Log.d(TAG, "Controller onModelUpdated event with $newValue")
        }
        searchBreadcrumb?.setPath(newValue.toTypedArray())
    }

    /** Registers the consumer of path item clicks for search breadcrumb. */
    fun setSearchBreadcrumbClickConsumer(consumer: IntConsumer?) {
        searchBreadcrumbClickConsumer = consumer
    }

    /** Sets whether the search breadcrumb should be visible or not. */
    fun setSearchBreadcrumbVisible(visible: Boolean) {
        if (!visible) {
            // When hiding the breadcrumb view, clear its state. When showing it, we wish to start
            // with clean state, which is then updated to some selected path.
            searchBreadcrumb?.setPath(arrayOf())
            model?.setPath(arrayOf())
            setSearchBreadcrumbClickConsumer(null)
        }
        setViewVisibility(searchBreadcrumb, visible)
        // Hide the other breadcrumb to make sure only one breadcrumb shows.
        if (visible) {
            setNavBreadcrumbVisible(false)
        } else {
            // We want to call `updateTopDividerVisibility` anyway, putting it in the "else" because
            // the above `setNavBreadcrumbVisible` already calls `updateTopDividerVisibility`.
            updateTopDividerVisibility()
        }
    }

    /** Sets whether the navigation breadcrumb should be visible or not. */
    fun setNavBreadcrumbVisible(visible: Boolean) {
        navBreadcrumb.show(visible)
        if (visible) {
            navBreadcrumb.postUpdate()
            // Hide the other breadcrumb to make sure only one breadcrumb shows.
            setSearchBreadcrumbVisible(false)
        } else {
            // We want to call `updateTopDividerVisibility` anyway, putting it in the "else" because
            // the above `setSearchBreadcrumbVisible` already calls `updateTopDividerVisibility`.
            updateTopDividerVisibility()
        }
    }

    /** We show top divider when either navigation breadcrumb or search breadcrumb is visible. */
    private fun updateTopDividerVisibility() {
        val showTopDivider = navBreadcrumb.isVisible || (searchBreadcrumb?.isVisible == true)
        setViewVisibility(topDivider, showTopDivider)
    }

    /**
     * Provides access to the model, so that it can be explicitly assigned values to it. This is due
     * to the fact that we wish this model to be a bridge between two existing models in the
     * DocumentsUI: the directory path, and the path to the selected file.
     */
    fun getModel() = model
}

private fun setViewVisibility(view: View?, visible: Boolean) {
    view?.visibility =
        if (visible) {
            VISIBLE
        } else {
            GONE
        }
}
