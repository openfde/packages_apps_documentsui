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
package com.android.documentsui.dirlist

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(AndroidJUnit4::class)
@SmallTest
class ItemDecorationInvalidatorTest {

    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Captor private lateinit var runnableCaptor: ArgumentCaptor<Runnable>

    @Mock private lateinit var recViewMock: RecyclerView

    @Before
    fun setUp() {
        var testView = View(context)
        // Cannot mock final class ViewTreeObserver.
        `when`(recViewMock.viewTreeObserver).thenReturn(testView.viewTreeObserver)
    }

    private fun runPostedRunnable(recViewMock: RecyclerView, times: Int = 1) {
        verify(recViewMock, times(times)).post(runnableCaptor.capture())
        runnableCaptor.value.run()
    }

    @Test
    fun invalidates_whenNoScrollOrLayout() {
        // Set state to idle.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        `when`(recViewMock.isComputingLayout).thenReturn(false)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(false)

        ItemDecorationInvalidator.create(recViewMock)

        // Verify that invalidation has occurred.
        verify(recViewMock).invalidateItemDecorations()
    }

    @Test
    fun invalidates_afterScroll() {
        // Set scroll state to SCROLL_STATE_DRAGGING.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_DRAGGING)
        `when`(recViewMock.isComputingLayout).thenReturn(false)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(false)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that no invalidation has occurred.
        verify(recViewMock, never()).invalidateItemDecorations()

        // Set scroll state to SCROLL_STATE_SETTLING.
        itemDecorationInvalidator.onScrollStateChanged(
            recViewMock,
            RecyclerView.SCROLL_STATE_SETTLING,
        )

        // Verify that invalidation still has not occurred.
        verify(recViewMock, never()).invalidateItemDecorations()

        // Set scroll state to SCROLL_STATE_IDLE.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        itemDecorationInvalidator.onScrollStateChanged(recViewMock, RecyclerView.SCROLL_STATE_IDLE)

        // Verify that invalidation has now occurred.
        verify(recViewMock).invalidateItemDecorations()
    }

    @Test
    fun invalidates_afterScrollAndLayout() {
        // Set scroll state to SCROLL_STATE_DRAGGING and isComputingLayout to true.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_DRAGGING)
        `when`(recViewMock.isComputingLayout).thenReturn(true)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(false)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that invalidation has not occurred.
        verify(recViewMock, never()).invalidateItemDecorations()

        // Set scroll state to SCROLL_STATE_IDLE.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        itemDecorationInvalidator.onScrollStateChanged(recViewMock, RecyclerView.SCROLL_STATE_IDLE)

        // Verify that invalidation has not occurred.
        verify(recViewMock, never()).invalidateItemDecorations()

        // Update the layout state to idle. This will trigger an invalidation.
        `when`(recViewMock.isComputingLayout).thenReturn(false)
        itemDecorationInvalidator.onGlobalLayout()
        // Run the post from onGlobalLayout explicitly to simulate the looper.
        runPostedRunnable(recViewMock)

        // Verify that invalidation has occurred.
        verify(recViewMock).invalidateItemDecorations()
    }

    @Test
    fun invalidates_afterLayout() {
        // Set layout state to pending.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        `when`(recViewMock.isComputingLayout).thenReturn(false)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(true)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that invalidation has not occurred.
        verify(recViewMock, never()).invalidateItemDecorations()

        // Update the layout state to idle. This will trigger an invalidation.
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(false)
        itemDecorationInvalidator.onGlobalLayout()
        // Run the post from onGlobalLayout explicitly to simulate the looper.
        runPostedRunnable(recViewMock)

        // Verify that invalidation has occurred.
        verify(recViewMock).invalidateItemDecorations()
    }

    @Test
    fun addsScrollListenerButNotLayoutListener_whenScrollInitiallyActive() {
        // Set scroll state to SCROLL_STATE_DRAGGING.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_DRAGGING)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that it is added as a scroll listener.
        verify(recViewMock).addOnScrollListener(itemDecorationInvalidator)
        // Verify that it is not added as a layout listener.
        assertFalse(itemDecorationInvalidator.mObservingLayout)
    }

    @Test
    fun addsScrollAndLayoutListener_whenScrollInitiallyInactive() {
        // Set scroll state to SCROLL_STATE_IDLE but isComputingLayout to true.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        `when`(recViewMock.isComputingLayout).thenReturn(true)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that it is added as a scroll listener.
        verify(recViewMock).addOnScrollListener(itemDecorationInvalidator)
        // Verify that it is added as a layout listener.
        assertTrue(itemDecorationInvalidator.mObservingLayout)
    }

    @Test
    fun addLayoutListener_afterScroll() {
        // Set scroll state to SCROLL_STATE_DRAGGING and isComputingLayout to true.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_DRAGGING)
        `when`(recViewMock.isComputingLayout).thenReturn(true)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(true)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Verify that it is added as a scroll listener.
        verify(recViewMock).addOnScrollListener(itemDecorationInvalidator)
        // Verify that it is not added as a layout listener yet.
        assertFalse(itemDecorationInvalidator.mObservingLayout)

        // Set scroll state to SCROLL_STATE_IDLE.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        itemDecorationInvalidator.onScrollStateChanged(recViewMock, RecyclerView.SCROLL_STATE_IDLE)

        // Verify that it is added as a layout listener.
        assertTrue(itemDecorationInvalidator.mObservingLayout)
    }

    @Test
    fun removesListeners_afterInvalidation() {
        // Set scroll state to SCROLL_STATE_DRAGGING and isComputingLayout to true. This ensures
        // that both a scroll and layout listener will be added.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_DRAGGING)
        `when`(recViewMock.isComputingLayout).thenReturn(true)
        `when`(recViewMock.hasPendingAdapterUpdates()).thenReturn(false)

        var itemDecorationInvalidator = ItemDecorationInvalidator.create(recViewMock)

        // Set scroll state to SCROLL_STATE_IDLE.
        `when`(recViewMock.scrollState).thenReturn(RecyclerView.SCROLL_STATE_IDLE)
        itemDecorationInvalidator.onScrollStateChanged(recViewMock, RecyclerView.SCROLL_STATE_IDLE)

        // Update the layout state to idle. This will trigger an invalidation and teardown.
        `when`(recViewMock.isComputingLayout).thenReturn(false)
        itemDecorationInvalidator.onGlobalLayout()
        // Run the post from onGlobalLayout explicitly to simulate the looper.
        runPostedRunnable(recViewMock)

        // Verify that invalidation has been marked as true.
        assertTrue(itemDecorationInvalidator.hasFinishedInvalidation())

        // Verify that the listeners have been removed.
        verify(recViewMock).removeOnScrollListener(itemDecorationInvalidator)
        assertFalse(itemDecorationInvalidator.mObservingLayout)
    }
}
