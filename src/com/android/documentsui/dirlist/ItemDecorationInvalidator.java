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

package com.android.documentsui.dirlist;

import android.util.Log;
import android.view.ViewTreeObserver;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import org.jspecify.annotations.NonNull;

/*
 * Calls {@link RecyclerView#invalidateItemDecorations} when the recycler view is idle. That is,
 * when it is not scrolling or doing a layout. To avoid spamming the UI thread with runnables,
 * only observe global layout changes when scrolling is idle. This is because if scrolling is active
 * theres bound to be a layout change and so there will be no point observing the global layout
 * changes.
 *
 * Once {@link RecyclerView#invalidateItemDecorations} has been called, {@link
 * #hasFinishedInvalidation}
 * will return true and the ItemDecorationInvalidator will no longer attempt to call {@link
 * RecyclerView#invalidateItemDecorations}.
 */
public class ItemDecorationInvalidator extends RecyclerView.OnScrollListener
        implements ViewTreeObserver.OnGlobalLayoutListener {
    static final String TAG = "ItemDecInvalidator";
    @VisibleForTesting boolean mObservingLayout = false;
    private @NonNull RecyclerView mRecView;
    private boolean mInvalidatedItemDecorations = false;
    private boolean mPostPending = false;

    private ItemDecorationInvalidator(@NonNull RecyclerView recView) {
        mRecView = recView;
    }

    private void init() {
        if (isRecyclerViewIdle()) {
            invalidate();
            return;
        }

        mRecView.addOnScrollListener(this);

        if (!isRecyclerViewScrolling()) {
            // Start waiting for the layout to finish now that there's no active scroll.
            observeLayout();
        }
    }

    /** Create and initialise a ItemDecorationInvalidator. */
    public static ItemDecorationInvalidator create(@NonNull RecyclerView recView) {
        ItemDecorationInvalidator itemDecorationInvalidator =
                new ItemDecorationInvalidator(recView);
        itemDecorationInvalidator.init();
        return itemDecorationInvalidator;
    }

    /**
     * True when the ItemDecorationInvalidator has called {@link
     * RecyclerView#invalidateItemDecorations}.
     */
    public boolean hasFinishedInvalidation() {
        return mInvalidatedItemDecorations;
    }

    /** Unregisters the ItemDecorationInvalidator as a listener for the scroll and layout state. */
    public void teardown() {
        mRecView.removeOnScrollListener(this);
        stopObservingLayout();
    }

    private void invalidate() {
        mRecView.invalidateItemDecorations();
        mInvalidatedItemDecorations = true;
        teardown();
    }

    private void observeLayout() {
        if (!mObservingLayout) {
            ViewTreeObserver observer = mRecView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(this);
            }
            mObservingLayout = true;
        }
    }

    private void stopObservingLayout() {
        if (mObservingLayout) {
            ViewTreeObserver observer = mRecView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
            mObservingLayout = false;
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            if (isRecyclerViewIdle()) {
                // Got lucky. Invalidate immediately.
                invalidate();
            } else if (isRecyclerViewDoingLayout()) {
                // Wait for the layout to finish and then re-check idleness.
                observeLayout();
            } else {
                Log.w(TAG, "Unexpectedly not idle but not doing a layout");
            }
        }
    }

    @Override
    public void onGlobalLayout() {
        if (mPostPending) {
            return;
        }
        mPostPending = true;
        mRecView.post(
                () -> {
                    // When the layout finishes check if it's safe to invalidate the item
                    // decorations.
                    if (isRecyclerViewIdle()) {
                        invalidate();
                    } else if (isRecyclerViewScrolling()) {
                        // Unfortunately entered a scroll again. Wait for it to finish, then
                        // re-check for idleness.
                        stopObservingLayout();
                    } else {
                        // A layout is still active. Wait for it to finish, then re-check for
                        // idleness.
                    }
                    mPostPending = false;
                });
    }

    private boolean isRecyclerViewScrolling() {
        return mRecView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
    }

    private boolean isRecyclerViewDoingLayout() {
        return mRecView.isComputingLayout() || mRecView.hasPendingAdapterUpdates();
    }

    private boolean isRecyclerViewIdle() {
        return !isRecyclerViewScrolling() && !isRecyclerViewDoingLayout();
    }
}
