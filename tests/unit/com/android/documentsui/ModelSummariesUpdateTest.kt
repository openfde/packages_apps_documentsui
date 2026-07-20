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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class ModelSummariesUpdateTest {
    private lateinit var modelSummariesUpdate: ModelSummariesUpdate
    private lateinit var mockListener: SummaryUpdateListener

    @Before
    fun setUp() {
        modelSummariesUpdate = ModelSummariesUpdate()
        mockListener = mock(SummaryUpdateListener::class.java)
        modelSummariesUpdate.addSummaryUpdateListener(mockListener)
    }

    @Test
    fun testUpdateSummaries_newSummaries_notifiesListener() {
        val newSummaries = mapOf("doc1" to "Summary 1", "doc3" to "Summary 3")
        val idToPositionMap = mapOf("doc1" to 0, "doc2" to 1, "doc3" to 2)

        modelSummariesUpdate.updateSummaries(newSummaries, idToPositionMap)

        assertThat(modelSummariesUpdate.getSummary("doc1")).isEqualTo("Summary 1")
        assertThat(modelSummariesUpdate.getSummary("doc3")).isEqualTo("Summary 3")
        verify(mockListener).onSummariesUpdated(listOf(0, 2))
    }

    @Test
    fun testUpdateSummaries_updateExisting_notifiesListener() {
        val initialSummaries = mapOf("doc1" to "Initial 1")
        val idToPositionMap = mapOf("doc1" to 0)
        modelSummariesUpdate.updateSummaries(initialSummaries, idToPositionMap)
        verify(mockListener).onSummariesUpdated(listOf(0))

        val updatedSummaries = mapOf("doc1" to "Updated 1")
        modelSummariesUpdate.updateSummaries(updatedSummaries, idToPositionMap)

        assertThat(modelSummariesUpdate.getSummary("doc1")).isEqualTo("Updated 1")
        // Called again with the same arguments.
        verify(mockListener, times(2)).onSummariesUpdated(listOf(0))
    }

    @Test
    fun testUpdateSummaries_sameSummary_doesNotNotify() {
        val summaries = mapOf("doc1" to "Summary 1")
        val idToPositionMap = mapOf("doc1" to 0)
        modelSummariesUpdate.updateSummaries(summaries, idToPositionMap)
        verify(mockListener).onSummariesUpdated(listOf(0))

        // Update with the same summary.
        modelSummariesUpdate.updateSummaries(summaries, idToPositionMap)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun testUpdateSummaries_emptyMap_doesNotNotify() {
        val emptySummaries = emptyMap<String, String>()
        val idToPositionMap = mapOf("doc1" to 0)

        modelSummariesUpdate.updateSummaries(emptySummaries, idToPositionMap)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun testGetSummary_returnsCorrectSummary() {
        val summaries = mapOf("doc1" to "Test Summary")
        modelSummariesUpdate.updateSummaries(summaries, mapOf("doc1" to 0))

        assertThat(modelSummariesUpdate.getSummary("doc1")).isEqualTo("Test Summary")
        assertThat(modelSummariesUpdate.getSummary("doc2")).isNull()
    }

    @Test
    fun testReset_clearsSummaries() {
        val summaries = mapOf("doc1" to "Test Summary")
        modelSummariesUpdate.updateSummaries(summaries, mapOf("doc1" to 0))
        assertThat(modelSummariesUpdate.getSummary("doc1")).isNotNull()

        modelSummariesUpdate.reset()
        assertThat(modelSummariesUpdate.getSummary("doc1")).isNull()
    }

    @Test
    fun testRemoveListener_stopsNotifications() {
        modelSummariesUpdate.removeSummaryUpdateListener(mockListener)
        val newSummaries = mapOf("doc1" to "Summary 1")
        val idToPositionMap = mapOf("doc1" to 0)

        modelSummariesUpdate.updateSummaries(newSummaries, idToPositionMap)
        verifyNoMoreInteractions(mockListener)
    }
}
