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
package com.android.documentsui.loaders

import androidx.loader.content.Loader
import com.android.documentsui.DirectoryResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Implements an OnLoadCompleteListener for DirectoryResults. Counts the number of times the
 * onLoadComplete method has been called and stores the last results with which the loader has been
 * called.
 */
class TestLoadCompletedListener(private val count: Int) :
    Loader.OnLoadCompleteListener<DirectoryResult> {

    var result: DirectoryResult? = null
    var loadCount = 0
    private var latch = CountDownLatch(count)

    override fun onLoadComplete(loader: Loader<DirectoryResult?>, data: DirectoryResult?) {
        result = data
        loadCount++
        latch.countDown()
    }

    fun await(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS) {
        if (!latch.await(timeout, unit)) {
            throw InterruptedException("Test listener latch timed out.")
        }
    }

    fun reset() {
        latch = CountDownLatch(count)
        result = null
    }
}
