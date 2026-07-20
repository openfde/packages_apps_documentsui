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

package com.android.documentsui.queries

import android.content.Context
import android.os.Bundle
import android.platform.test.annotations.EnableFlags
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.MetricConsts
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.OverrideFlagsRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy

class TestSearchOptionsListener : SearchOptionsListener {
    var optionsState: SearchOptionsState? = null

    override fun onOptionsChanged(options: SearchOptionsState) {
        optionsState = options
    }
}

@EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class SearchOptionsControllerTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    lateinit var context: Context
    lateinit var controller: SearchOptionsController
    lateinit var container: LinearLayout
    val optionsListener = TestSearchOptionsListener()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        container = spy(LinearLayout(context))
        controller = SearchOptionsController(container)
        controller.setOptionChangeListener(optionsListener)
    }

    @Test
    fun testOptionsUpdateWorks() {
        for (e in enumValues<SearchLocationOption>()) {
            controller.onLocationSelected(e.value)
            controller.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.location, e)
        }
        for (e in enumValues<LastModifiedOption>()) {
            controller.onLastModifiedSelected(e.value)
            controller.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.lastModified, e)
        }
        for (e in enumValues<FileTypeOption>()) {
            controller.onFileTypeSelected(e.value)
            controller.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.fileType, e)
        }
    }

    @Test
    fun testGetOptionsQueryArgs() {
        // Reset the options to minimum filtering state.
        controller.onLocationSelected(SearchLocationOption.EVERYWHERE.ordinal)
        controller.onLastModifiedSelected(LastModifiedOption.ANY_TIME.ordinal)
        controller.onFileTypeSelected(FileTypeOption.ANY_TYPE.ordinal)

        val queryArgs = controller.getOptionsQueryArgs()
        // Expect no query args with the default (no limits) settings.
        assertEquals(queryArgs.size, 0)
    }

    @Test
    fun testSetSelectedFileType() {
        controller.setSelectedFileType(MetricConsts.TYPE_CHIP_AUDIOS)
        controller.notifyOptionsChangeListener()
        assertEquals(optionsListener.optionsState!!.fileType, FileTypeOption.AUDIO)

        controller.setSelectedFileType(MetricConsts.TYPE_CHIP_VIDEOS)
        controller.notifyOptionsChangeListener()
        assertEquals(optionsListener.optionsState!!.fileType, FileTypeOption.VIDEO)

        controller.setSelectedFileType(MetricConsts.TYPE_CHIP_IMAGES)
        controller.notifyOptionsChangeListener()
        assertEquals(optionsListener.optionsState!!.fileType, FileTypeOption.IMAGES)

        controller.setSelectedFileType(MetricConsts.TYPE_CHIP_DOCS)
        controller.notifyOptionsChangeListener()
        assertEquals(optionsListener.optionsState!!.fileType, FileTypeOption.DOCUMENTS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSetSelectedInvalidFileType() {
        controller.setSelectedFileType(MetricConsts.TYPE_CHIP_FROM_THIS_WEEK)
    }

    @Test
    fun testStateSaving() {
        val expectedTypeOption = FileTypeOption.IMAGES
        val expectedLocationOption = SearchLocationOption.EVERYWHERE
        val expectedModifiedOption = LastModifiedOption.LAST_30_DAYS

        val root =
            RootInfo().apply {
                userId = UserId.of(101)
                authority = "root-authority"
                rootId = "root-123"
            }

        controller.setRoot(root)
        controller.onFileTypeSelected(expectedTypeOption.value)
        controller.onLocationSelected(expectedLocationOption.value)
        controller.onLastModifiedSelected(expectedModifiedOption.value)

        val bundle = Bundle()
        controller.saveState(bundle)

        // Change options to different values. When changing root, expect the default location for
        // a new root to be expected. For other values, expect the on..Selected() method to return
        // true, indicating that the value supplied is different than the value set.
        assertThat(controller.onFileTypeSelected(FileTypeOption.AUDIO.value)).isTrue()
        assertThat(controller.onLocationSelected(SearchLocationOption.ROOT_FOLDER.value)).isTrue()
        assertThat(controller.onLastModifiedSelected(LastModifiedOption.LAST_7_DAYS.value)).isTrue()
        assertThat(
                controller.setRoot(
                    RootInfo().apply {
                        userId = UserId.of(102)
                        authority = "another-root-authority"
                        rootId = "root-456"
                    }
                )
            )
            .isEqualTo(SearchLocationOption.ROOT_FOLDER)

        controller.restoreState(bundle)

        val listener =
            object : SearchOptionsListener {
                override fun onOptionsChanged(options: SearchOptionsState) {
                    assertThat(options.fileType).isEqualTo(expectedTypeOption)
                    assertThat(options.location).isEqualTo(expectedLocationOption)
                    assertThat(options.lastModified).isEqualTo(expectedModifiedOption)
                }
            }
        controller.setOptionChangeListener(listener)
        controller.notifyOptionsChangeListener()
        // Setting root back to the current root does not modify the location.
        assertThat(controller.setRoot(root)).isEqualTo(SearchLocationOption.EVERYWHERE)

        // However, if we set to a different root, the location goes back to ROOT_FOLDER.
        assertThat(
                controller.setRoot(
                    RootInfo().apply {
                        userId = UserId.of(102)
                        authority = "some-other-authority"
                        rootId = "root-789"
                    }
                )
            )
            .isEqualTo(SearchLocationOption.ROOT_FOLDER)
    }
}
