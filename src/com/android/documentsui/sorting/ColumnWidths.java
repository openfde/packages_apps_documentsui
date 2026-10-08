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

package com.android.documentsui.sorting;

import static com.android.documentsui.util.FlagUtils.isUseFileSummaryEnabled;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the current relative widths (as LayoutParams weights) of the resizable table columns
 * (Name, Type, Size, Date). Both the table header and the file rows read from here so that the
 * columns stay vertically aligned after the user drags a column divider.
 */
public final class ColumnWidths {

    public static final int COLUMN_TITLE = 0;
    public static final int COLUMN_FILE_TYPE = 1;
    public static final int COLUMN_SIZE = 2;
    public static final int COLUMN_DATE = 3;

    private static final int COLUMN_COUNT = 4;

    private static final float DEFAULT_TITLE = 0.4f;
    private static final float DEFAULT_COLUMN = 0.2f;
    private static final float DEFAULT_TITLE_WITH_SUMMARY = 0.35f;
    private static final float DEFAULT_COLUMN_WITH_SUMMARY = 0.15f;
    private static final float MIN_WEIGHT = 0.02f;
    private static final float MAX_WEIGHT = 0.75f;

    private static final float[] sWeights = new float[COLUMN_COUNT];
    private static boolean sInitialized = false;
    private static final List<Listener> sListeners = new ArrayList<>(2);

    private ColumnWidths() {}

    /** Listener notified when the user changes the column widths. */
    public interface Listener {
        void onColumnWidthsChanged();
    }

    /** Lazily initialize the weights to the built-in defaults. */
    public static void ensureDefaults() {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        if (isUseFileSummaryEnabled()) {
            sWeights[COLUMN_TITLE] = DEFAULT_TITLE_WITH_SUMMARY;
            sWeights[COLUMN_FILE_TYPE] = DEFAULT_COLUMN_WITH_SUMMARY;
            sWeights[COLUMN_SIZE] = DEFAULT_COLUMN_WITH_SUMMARY;
            sWeights[COLUMN_DATE] = DEFAULT_COLUMN_WITH_SUMMARY;
        } else {
            sWeights[COLUMN_TITLE] = DEFAULT_TITLE;
            sWeights[COLUMN_FILE_TYPE] = DEFAULT_COLUMN;
            sWeights[COLUMN_SIZE] = DEFAULT_COLUMN;
            sWeights[COLUMN_DATE] = DEFAULT_COLUMN;
        }
    }

    /** Returns the current weight of the given column. */
    public static float getWeight(int column) {
        ensureDefaults();
        return sWeights[column];
    }

    /**
     * Atomically replaces all column weights and notifies listeners only once. Weight changes made
     * by dragging a column divider are written through this method to keep the header and the
     * visible file rows in sync with a single dispatch.
     */
    public static void updateWeights(float[] weights) {
        ensureDefaults();
        boolean changed = false;
        for (int i = 0; i < COLUMN_COUNT; i++) {
            float clamped = clamp(weights[i]);
            if (sWeights[i] != clamped) {
                sWeights[i] = clamped;
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        for (Listener listener : sListeners) {
            listener.onColumnWidthsChanged();
        }
    }

    public static void addListener(Listener listener) {
        sListeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        sListeners.remove(listener);
    }

    private static float clamp(float value) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, value));
    }
}
