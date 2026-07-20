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

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.base.Providers.isMediaStoreUri
import com.android.documentsui.base.Providers.isSameProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ProvidersTest {

    @Test
    fun testIsMediaStoreUri() {
        assertTrue(isMediaStoreUri("content://media/file/1".toUri()))
        assertFalse(isMediaStoreUri("content://other/file/1".toUri()))
        assertFalse(isMediaStoreUri("other://media/file/1".toUri()))
        assertFalse(isMediaStoreUri(null))
    }

    @Test
    fun testIsSameProvider() {
        assertTrue(isSameProvider("scheme://authority/1".toUri(), "scheme://authority/1".toUri()))
        assertTrue(isSameProvider("scheme://authority/1".toUri(), "scheme://authority/2".toUri()))
        assertFalse(isSameProvider("scheme://authority/1".toUri(), "scheme://other/1".toUri()))
        assertFalse(isSameProvider("scheme://authority/1".toUri(), "other://authority/1".toUri()))
        assertFalse(isSameProvider("scheme://authority/1".toUri(), null))
        assertFalse(isSameProvider(null, "scheme://authority/1".toUri()))
        assertFalse(isSameProvider(null, null))
    }
}
