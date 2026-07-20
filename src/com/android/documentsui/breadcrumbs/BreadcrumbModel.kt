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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.SharedMinimal.DEBUG

/** The tag for breadcrumb v2 logs. */
const val TAG = "Breadcrumb2"

/**
 * A model of a breadcrumb. A breadcrumb can be thought of as a list of strings representing a path
 * in the file system. Typically such path would consist of the root element (say "My laptop")
 * followed by a number of directories (say, "Documents", "Taxes"). The model is updated by calling
 * the `setPath` method. If the set path differs from the current path, the model posts an update to
 * all observers registered on `pathData`.
 *
 * This class is not thread safe.
 */
class BreadcrumbModel : ViewModel() {
    private val mutablePathData = MutableLiveData<List<String>>(emptyList())
    val pathData: LiveData<List<String>> = mutablePathData

    /**
     * Sets the path from the given stack. Returns true if the path computed from the stack differs
     * from the one that was held by the model.
     */
    fun setFromStack(stack: DocumentStack): Boolean {
        val currentPath =
            Array(stack.size()) { index ->
                if (index == 0) stack.root?.title ?: "" else stack.get(index).displayName
            }
        if (DEBUG) {
            Log.d(TAG, "setFromStack path ${currentPath.contentToString()}")
        }
        return setPath(currentPath)
    }

    /**
     * Sets the new path on this model. If the new path differs from the old path, registered
     * observers are notified about the change. This method does not create a copy of the given
     * path, which means the argument cannot be re-used. Returns true, if the set path was different
     * than the one kept by this model.
     */
    fun setPath(newPath: Array<String>): Boolean {
        val currentPath = mutablePathData.value?.toTypedArray() ?: arrayOf()
        if (currentPath.contentEquals(newPath)) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Setting ${newPath.contentToString()} on breadcrumb model")
        }

        mutablePathData.value = newPath.toList()
        return true
    }
}
