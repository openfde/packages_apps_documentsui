/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.documentsui.compose.tests.testdata

import com.android.documentsui.compose.data.DocumentsProviderDataSource
import com.android.documentsui.compose.data.model.Root
import com.android.documentsui.compose.data.model.RootType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeDocumentsProviderDataSource @Inject constructor() : DocumentsProviderDataSource {
    override val rootsFlow: Flow<List<Root>> =
        flowOf(
            listOf(
                Root(
                    "com.android.externalstorage.documents",
                    "primary",
                    "primary:",
                    "Pixel 8",
                    RootType.PRIMARY,
                ),
                Root(
                    "com.android.providers.downloads.documents",
                    "downloads",
                    "downloads",
                    "Downloads",
                    RootType.DOWNLOADS,
                ),
                Root(
                    "com.android.providers.media.documents",
                    "images_root",
                    "images_root",
                    "Images",
                    RootType.GENERIC,
                ),
                Root(
                    "com.android.providers.media.documents",
                    "videos_root",
                    "videos_root",
                    "Videos",
                    RootType.GENERIC,
                ),
                Root(
                    "com.android.providers.media.documents",
                    "audio_root",
                    "audio_root",
                    "Audio",
                    RootType.GENERIC,
                ),
            )
        )
}
