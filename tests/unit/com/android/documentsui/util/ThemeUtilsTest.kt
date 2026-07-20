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

package com.android.documentsui.util

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ThemeUtilsTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    @Before
    fun setUp() {
        Material3Config.overrideMappingForTest(
            mapOf(
                R.id.option_menu_debug to 111,
                R.string.file_operation_rejected to R.string.file_operation_rejected_m3,
                R.string.no_results to R.string.no_results_m3,
            )
        )
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testGetRes_forOptionMenuDebug_returnsM3Id() {
        assertEquals(111, Material3Config.getRes(R.id.option_menu_debug))
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testGetRes_forOptionMenuDebug_returnsOriginalId() {
        assertEquals(R.id.option_menu_debug, Material3Config.getRes(R.id.option_menu_debug))
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testGetRes_whenIdNotInMap_returnsOriginalId() {
        // Use a resource ID that is not present in the test mapping configured in setUp().
        val unmappedResourceId = R.id.action_menu_sort

        // Verify that the original resource ID is returned when no mapping is found.
        assertEquals(unmappedResourceId, Material3Config.getRes(unmappedResourceId))
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testGetRes_forFileOperationRejected_returnsM3String() {
        // Verifies that the resource ID for file_operation_rejected string is correctly
        // mapped to its Material3 version.
        assertEquals(
            R.string.file_operation_rejected_m3,
            Material3Config.getRes(R.string.file_operation_rejected),
        )
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testGetRes_forNoResults_returnsM3String() {
        // Verifies that the resource ID for no_results string is correctly mapped to its Material3
        // version.
        assertEquals(R.string.no_results_m3, Material3Config.getRes(R.string.no_results))
    }
}
