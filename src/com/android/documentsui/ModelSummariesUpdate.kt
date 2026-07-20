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

/** Listener interface for being notified when document summaries are updated. */
interface SummaryUpdateListener {
    /**
     * Called when summaries have been updated for specific items.
     *
     * @param updatedIndices The list of indices in the model that have been updated.
     */
    fun onSummariesUpdated(updatedIndices: List<Int>)
}

/**
 * Manages the storage and update notifications for document summaries, designed to be composed
 * within the [Model] class.
 */
class ModelSummariesUpdate {

    /**
     * Maps from model ID to its summary string, so they can be queried after the update
     * notification.
     */
    private val summaries = HashMap<String, String>()
    private val listeners = ArrayList<SummaryUpdateListener>()

    fun addSummaryUpdateListener(listener: SummaryUpdateListener) {
        listeners.add(listener)
    }

    fun removeSummaryUpdateListener(listener: SummaryUpdateListener) {
        listeners.remove(listener)
    }

    /**
     * Updates the stored summaries and notifies listeners of changed indices.
     *
     * @param newSummaries A map of Model ID to its summary string.
     * @param idToPositionMap A map from Model ID to its current index (position) in the list. This
     *   is typically {@link Model.mPositions}.
     */
    fun updateSummaries(newSummaries: Map<String, String>, idToPositionMap: Map<String, Int>) {
        if (newSummaries.isEmpty()) {
            return
        }

        val updatedIndices = ArrayList<Int>()
        var changed = false

        for ((modelId, newSummary) in newSummaries.entries) {
            val oldSummary = summaries[modelId]
            if (newSummary != oldSummary) {
                summaries[modelId] = newSummary
                changed = true
                val index = idToPositionMap[modelId]
                if (index != null) {
                    updatedIndices.add(index)
                }
            }
        }

        if (changed) {
            notifyListeners(updatedIndices)
        }
    }

    /** Gets the summary for a given document ID. */
    fun getSummary(modelId: String): String? {
        return summaries[modelId]
    }

    /** Clears all stored summaries. */
    fun reset() {
        summaries.clear()
    }

    private fun notifyListeners(updatedIndices: List<Int>) {
        if (updatedIndices.isEmpty()) return
        for (listener in listeners) {
            listener.onSummariesUpdated(updatedIndices)
        }
    }
}
