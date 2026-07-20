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

package com.android.documentsui

import android.os.Build
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract.Document
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.documentsui.ActivityConfigTest.ParameterizedTests.Companion.NO_FLAGS
import com.android.documentsui.ActivityConfigTest.ParameterizedTests.Companion.OFFLINE
import com.android.documentsui.ActivityConfigTest.ParameterizedTests.Companion.SYNC_UNAVAILABLE_LOCALLY
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.State
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

data class ActivityConfigTestParams(
    val testName: String,
    val mimeType: String,
    val docFlags: Int,
    val syncStateFlags: Int?,
    val rootHasLimitedFunctionalityWhenOffline: Boolean,
    val isOnline: Boolean,
    val expectedResult: Boolean,
) {
    override fun toString(): String = testName
}

@RunWith(Enclosed::class)
@SmallTest
class ActivityConfigTest {

    @RunWith(Parameterized::class)
    class ParameterizedTests(private val testParams: ActivityConfigTestParams) {

        @get:Rule val overrideFlagsRule = OverrideFlagsRule()
        @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()

        private lateinit var config: TestActivityConfig
        private lateinit var env: TestEnv
        private lateinit var state: State

        @Before
        fun setUp() {
            env = TestEnv.create()
            state = env.state
            config = TestActivityConfig()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
        fun testIsDocumentEnabled() {
            var doc = DocumentInfo()
            doc.mimeType = testParams.mimeType
            doc.flags = testParams.docFlags
            doc.syncStateFlags = testParams.syncStateFlags
            doc.rootHasLimitedFunctionalityWhenOffline =
                testParams.rootHasLimitedFunctionalityWhenOffline
            assertEquals(
                testParams.expectedResult,
                config.isDocumentEnabled(doc, state, testParams.isOnline),
            )
        }

        companion object {
            // Constants for readability.
            private const val ONLINE = true
            const val OFFLINE = false
            const val NO_FLAGS = 0
            private val NO_SYNC_STATE: Int? = null
            private const val SYNC_AVAILABLE_LOCALLY: Int =
                Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY
            const val SYNC_UNAVAILABLE_LOCALLY = 0
            private const val LIMITED_FUNCTIONALITY_OFFLINE = true
            private const val NOT_LIMITED_FUNCTIONALITY_OFFLINE = false

            @JvmStatic
            @Parameters(name = "{0}")
            fun data(): Collection<ActivityConfigTestParams> {
                return listOf(
                    ActivityConfigTestParams(
                        "limitedFunctionality_unavailableLocally_offline",
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        false,
                    ),
                    ActivityConfigTestParams(
                        "assertObjectsEventuallyHiddenlimitedFunctionality_OnDocumentonline",
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        ONLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "limitedFunctionality_virtualDocument",
                        "image/png",
                        Document.FLAG_VIRTUAL_DOCUMENT,
                        SYNC_UNAVAILABLE_LOCALLY,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "limitedFunctionality_folder",
                        Document.MIME_TYPE_DIR,
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "notLimitedFunctionality",
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        NOT_LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "limitedFunctionality_noSyncState",
                        "image/png",
                        NO_FLAGS,
                        NO_SYNC_STATE,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "limitedFunctionality_availableLocally",
                        "image/png",
                        NO_FLAGS,
                        SYNC_AVAILABLE_LOCALLY,
                        LIMITED_FUNCTIONALITY_OFFLINE,
                        OFFLINE,
                        true,
                    ),
                )
            }
        }
    }

    @RunWith(AndroidJUnit4::class)
    class NonParameterizedTests {
        @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
        @get:Rule val overrideFlagsRule = OverrideFlagsRule()

        private lateinit var config: TestActivityConfig
        private lateinit var env: TestEnv
        private lateinit var state: State

        @Before
        fun setUp() {
            env = TestEnv.create()
            state = env.state
            config = TestActivityConfig()
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
        @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
        fun testIsDocumentEnabled_featureFlagDisabled() {
            var doc = DocumentInfo()
            doc.mimeType = "image/png"
            doc.flags = 0
            doc.syncStateFlags = 0
            doc.rootHasLimitedFunctionalityWhenOffline = true
            assertTrue(config.isDocumentEnabled(doc, state, false))
        }

        @Test
        @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
        @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
        @EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
        fun testIsContentAvailable_folder() {
            var doc = DocumentInfo()
            doc.mimeType = Document.MIME_TYPE_DIR
            doc.flags = NO_FLAGS
            doc.syncStateFlags = SYNC_UNAVAILABLE_LOCALLY
            doc.rootHasLimitedFunctionalityWhenOffline = true
            assertFalse(config.isContentAvailable(doc, state, OFFLINE))
        }
    }

    private class TestActivityConfig : ActivityConfig()
}
