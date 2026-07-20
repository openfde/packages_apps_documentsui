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

package com.android.documentsui.picker

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.State
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestEnv
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ConfigTest {

    companion object {
        private const val FLAG_READ_ONLY = 0
        private const val OFFLINE = false
    }

    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    private lateinit var config: Config
    private lateinit var env: TestEnv
    private lateinit var state: State

    @Before
    fun setUp() {
        env = TestEnv.create()
        state = env.state
        config = Config()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
    fun testIsDocumentEnabled_superReturnsTrue_doesNotAffectResult() {
        state.action = State.ACTION_CREATE
        // The super method, ActivityConfig.isDocumentEnabled, will return true because the root
        // doesn't have limited functionality when offline. However the Config method should still
        // return false because read-only files are disabled when creating.
        var doc = DocumentInfo()
        doc.mimeType = "image/png"
        doc.flags = FLAG_READ_ONLY
        doc.syncStateFlags = 0
        doc.rootHasLimitedFunctionalityWhenOffline = false
        assertFalse(config.isDocumentEnabled(doc, state, OFFLINE))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
    fun testIsDocumentEnabled_superReturnsFalse_returnsFalse() {
        state.action = State.ACTION_CREATE
        state.acceptMimes = arrayOf("image/png")
        // The super method, ActivityConfig.isDocumentEnabled, will return true because the root
        // doesn't have limited functionality when offline. The Config method should also return
        // true because the "image/png" mime type is accepted.
        var doc = DocumentInfo()
        doc.mimeType = "image/png"
        doc.flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        doc.syncStateFlags = 0
        doc.rootHasLimitedFunctionalityWhenOffline = false
        assertTrue(config.isDocumentEnabled(doc, state, OFFLINE))

        // The super method, ActivityConfig.isDocumentEnabled, will now return false because the
        // root has limited functionality when offline. This should cause the Config method to
        // return false.
        doc.rootHasLimitedFunctionalityWhenOffline = true
        assertFalse(config.isDocumentEnabled(doc, state, OFFLINE))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
    fun testCanSelectType_returnsFalseWhenDocumentDisabled() {
        state.action = State.ACTION_CREATE
        state.acceptMimes = arrayOf("image/png")
        // isDocumentEnabled returns true and all other conditions are met to allow the document to
        // be selected.
        var doc = DocumentInfo()
        doc.mimeType = "image/png"
        doc.flags = Document.FLAG_SUPPORTS_WRITE
        doc.syncStateFlags = 0
        doc.rootHasLimitedFunctionalityWhenOffline = false
        assertTrue(config.isDocumentEnabled(doc, state, OFFLINE))
        assertTrue(config.canSelectType(doc, state, OFFLINE))

        // isDocumentEnabled returns false and so the doc can't be selected.
        doc.rootHasLimitedFunctionalityWhenOffline = true
        assertFalse(config.isDocumentEnabled(doc, state, OFFLINE))
        assertFalse(config.canSelectType(doc, state, OFFLINE))
    }
}
