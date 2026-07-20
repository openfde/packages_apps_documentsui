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

package com.android.documentsui.sidebar;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.documentsui.R;

public final class RootItemView extends LinearLayout {
    private final int[] mStateHighlighted;
    private final int[] mStateError;

    private boolean mHighlighted = false;
    private boolean mError = false;

    public RootItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStateHighlighted = new int[] {getRes(R.attr.state_highlighted)};
        mStateError = new int[] {getRes(R.attr.state_error)};
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        // The error state overrides the highlighted state so can't exist at the same time.
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isUseMaterial3FlagEnabled() && mError) {
            mergeDrawableStates(drawableState, mStateError);
        } else if (mHighlighted) {
            mergeDrawableStates(drawableState, mStateHighlighted);
        }

        return drawableState;
    }

    /**
     * Sets whether this view is currently being hovered over during a drag operation. Unsetting
     * also removes the error state.
     */
    public void setHighlight(boolean highlight) {
        mHighlighted = highlight;
        if (isUseMaterial3FlagEnabled() && !highlight) {
            // The drag operation is leaving this view, so we also need to unset the hover error
            // state if active.
            mError = false;
        }
        refreshDrawableState();
    }

    /**
     * Sets whether this view is indicating that it can't be dropped into during a drag operation.
     */
    public void setError(boolean error) {
        if (isUseMaterial3FlagEnabled()) {
            mError = error;
            refreshDrawableState();
        }
    }

    /**
     * Synthesizes pressed state to trick RippleDrawable starting a ripple effect.
     */
    public void drawRipple() {
        setPressed(true);
        setPressed(false);
    }
}
