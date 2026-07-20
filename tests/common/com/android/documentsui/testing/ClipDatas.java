/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.testing;

import static org.mockito.ArgumentMatchers.anyInt;

import android.content.ClipData;
import android.content.ClipDescription;

import org.mockito.Mockito;

import java.util.List;

public final class ClipDatas {

    private ClipDatas() {}

    public static ClipData createTestClipData() {
        final ClipData data = Mockito.mock(ClipData.class);

        return data;
    }

    public static ClipData createTestClipData(ClipDescription description) {
        final ClipData data = createTestClipData();

        Mockito.when(data.getDescription()).thenReturn(description);

        return data;
    }

    /**
     * Creates a mock ClipData instance for testing with a ClipDescription and a list of
     * ClipData.Item objects.
     */
    public static ClipData createTestClipData(
            ClipDescription description, List<ClipData.Item> items) {
        final ClipData data = createTestClipData(description);

        Mockito.when(data.getItemCount()).thenReturn(items.size());
        Mockito.when(data.getItemAt(anyInt())).thenAnswer(i -> items.get(i.getArgument(0)));

        return data;
    }
}
