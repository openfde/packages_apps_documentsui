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

package com.android.documentsui.base

import android.os.Handler
import java.lang.Runnable

/**
 * Interface wrapper around `android.os.Handler` to allow for testing/mocking of final methods like
 * `postDelayed` and `removeCallbacks`.
 */
interface LoadingHandler {
    fun postDelayed(r: Runnable, delayMillis: Long)

    fun removeCallbacks(r: Runnable)
}

/** Concrete implementation that delegates to a real Android Handler. */
class LoadingHandlerImpl(private val handler: Handler) : LoadingHandler {
    override fun postDelayed(r: Runnable, delayMillis: Long) {
        handler.postDelayed(r, delayMillis)
    }

    override fun removeCallbacks(r: Runnable) {
        handler.removeCallbacks(r)
    }
}
