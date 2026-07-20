/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.recyclerview.selection;

import static androidx.core.util.Preconditions.checkArgument;

import android.view.MotionEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.widget.RecyclerView;

import org.jspecify.annotations.NonNull;

/**
 * A MotionInputHandler that provides the high-level glue for mouse driven selection. This class
 * works with {@link RecyclerView}, {@link GestureRouter}, and {@link GestureSelectionHelper} to
 * implement the primary policies around mouse input.
 */
final class MouseInputHandler<K> extends MotionInputHandler<K> {

    private static final String TAG = "MouseInputHandler";

    private final ItemDetailsLookup<K> mDetailsLookup;
    private final OnContextClickListener mOnContextClickListener;
    private final OnItemActivatedListener<K> mOnItemActivatedListener;
    private final FocusDelegate<K> mFocusDelegate;

    // Tracks the item that was tapped during the first half of the double-tap gesture.
    private ItemDetails<K> mFirstTapItem;

    MouseInputHandler(
            @NonNull SelectionTracker<K> selectionTracker,
            @NonNull ItemKeyProvider<K> keyProvider,
            @NonNull ItemDetailsLookup<K> detailsLookup,
            @NonNull OnContextClickListener onContextClickListener,
            @NonNull OnItemActivatedListener<K> onItemActivatedListener,
            @NonNull FocusDelegate<K> focusDelegate) {

        super(selectionTracker, keyProvider, focusDelegate);

        checkArgument(detailsLookup != null);
        checkArgument(onContextClickListener != null);
        checkArgument(onItemActivatedListener != null);

        mDetailsLookup = detailsLookup;
        mOnContextClickListener = onContextClickListener;
        mOnItemActivatedListener = onItemActivatedListener;
        mFocusDelegate = focusDelegate;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if ((MotionEvents.isAltKeyPressed(e) && MotionEvents.isPrimaryMouseButtonPressed(e, true))
                || MotionEvents.isSecondaryMouseButtonPressed(e)) {
            return onRightClick(e);
        }

        return false;
    }

    @Override
    public boolean onScroll(
            @NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        // Don't scroll content window in response to mouse drag
        // If it's two-finger trackpad scrolling, we want to scroll
        return !MotionEvents.isTouchpadScroll(e2);
    }

    // Called when left-clicking on an item and there is an existing selection (which may or may
    // not include that item). We extend / clear / modify the selection (and adjust focus).
    private void onLeftClickWhenSomethingSelected(
            @NonNull MotionEvent e, @NonNull ItemDetails<K> item) {
        checkArgument(item != null);

        if (shouldExtendRange(e)) {
            extendSelectionRange(item);
            return;
        }

        K key = item.getSelectionKey();
        switch (item.classifySelectionHotspot(e)) {
            case ItemDetails.SELECTION_HOTSPOT_OUTSIDE:
                if (!mSelectionTracker.isSelected(key)) {
                    focusItemQuietly(item);
                } else if (mSelectionTracker.deselect(key)) {
                    mFocusDelegate.clearFocus();
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI:
                if (!mSelectionTracker.isSelected(key)) {
                    selectItem(item);
                } else if (mSelectionTracker.deselect(key)) {
                    mFocusDelegate.clearFocus();
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO:
                boolean wasTheOnlyThingSelected =
                        mSelectionTracker.isSelected(key)
                                && (mSelectionTracker.getSelection().size() == 1);
                clearSelectionQuietly();
                if (!wasTheOnlyThingSelected) {
                    selectItem(item);
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET:
                clearSelectionQuietly();
                selectItem(item);
                return;
        }
    }

    // We use onSingleTapUp instead of onSingleTapConfirmed to remove the 300ms delay associated
    // with waiting for a double-tap. Selection happens immediately on the first ACTION_UP.
    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent e) {
        if (MotionEvents.isAltKeyPressed(e) || !MotionEvents.isPrimaryMouseButtonPressed(e, true)) {
            return false;
        }

        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if (item == null) {
            clearSelectionQuietly();
            mFocusDelegate.clearFocus();
            return false;
        }

        if (mSelectionTracker.hasSelection()) {
            onLeftClickWhenSomethingSelected(e, item);
            return true;
        }

        if (mFocusDelegate.hasFocusedItem() && MotionEvents.isShiftKeyPressed(e)) {
            mSelectionTracker.startRange(mFocusDelegate.getFocusedPosition());
            mSelectionTracker.extendRange(item.getPosition());
        } else if (item.classifySelectionHotspot(e) == ItemDetails.SELECTION_HOTSPOT_OUTSIDE) {
            focusItemQuietly(item);
        } else {
            selectItem(item);
        }
        return true;
    }

    private void focusItemQuietly(@NonNull ItemDetails<K> item) {
        clearSelectionQuietly();
        mFocusDelegate.focusItem(item);
    }

    private void clearSelectionQuietly() {
        // We use setItemsSelected(..., false) instead of clearSelection() because clearSelection()
        // triggers ResetManager, which cancels the GestureDetector's double-tap timer. Using a
        // "quiet" clear allows double-clicks to work even when they cause a selection change
        // (e.g. one item is selected and we double-click another unselected item).

        // We must copy the selection before passing it to setItemsSelected, as setItemsSelected
        // will modify the underlying selection set, potentially causing a
        // ConcurrentModificationException if we pass the live set.
        MutableSelection<K> copy = new MutableSelection<>();
        copy.copyFrom(mSelectionTracker.getSelection());
        mSelectionTracker.setItemsSelected(copy, false);
    }

    /**
     * @param e The 1st tap DOWN event when double click is detected.
     */
    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        // We ensure the first tap is a valid primary click without the Alt, Ctrl, or Shift key.
        if (MotionEvents.isAltKeyPressed(e)
                || MotionEvents.isCtrlKeyPressed(e)
                || MotionEvents.isShiftKeyPressed(e)
                || !MotionEvents.isPrimaryMouseButtonPressed(e, true)) {
            mFirstTapItem = null;
            return false;
        }

        mFirstTapItem = mDetailsLookup.overItemWithSelectionKeyAsItem(e);

        // DO NOT RETURN TRUE YET. Returning true here makes the `GestureDetector.onTouchEvent`
        // inside `GestureDetectorWrapper.onInterceptTouchEvent` returns true, which let the
        // GestureDetectorWrapper intercept the gesture stream (e.g. the ACTION_UP for the second
        // up event will only reach `GestureDetectorWrapper.onTouchEvent` which is an empty
        // implementation), making GestureDetector not receive the ACTION_UP for the second tap,
        // leaving its internal state stuck in the double click process.
        return false;
    }

    /**
     * We implement onDoubleTapEvent to intercept the second ACTION_UP and reroute it to
     * onSingleTapUp to handle an edge cases: The user rapidly clicks two *different* items. The
     * system groups them into a double-tap, but we reject the activation because the items don't
     * match. We route the second click to {@link #onSingleTapUp(MotionEvent)} so the second item
     * gets selected properly.
     *
     * <p>Here's the full event flow when double click is detected:
     *
     * <p>First tap:
     *
     * <ul>
     *   <li>{@link #onDown(MotionEvent firstTapDown)}
     *   <li>{@link #onSingleTapUp(MotionEvent firstTapUp)}
     * </ul>
     *
     * <p>Second tap:
     *
     * <ul>
     *   <li>{@link #onDoubleTap(MotionEvent firstTapDown)} - Note that it's NOT the 2nd tap down!
     *   <li>{@link #onDoubleTapEvent(MotionEvent secondTapDown)}
     *   <li>{@link #onDown(MotionEvent secondTapDown)}
     *   <li>{@link #onDoubleTapEvent(MotionEvent secondTapUp)}
     * </ul>
     *
     * @param e The 2nd tap event when double click is detected.
     */
    @Override
    public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
        if (!MotionEvents.isActionUp(e)) {
            // For ACTION_DOWN of the second tap, we simply return false and delay all the handling
            // to the ACTION_UP, similar to single tap (which is handled inside `onSingleTapUp`.
            // This is because only in ACTION_UP we know all the information we need:
            // * We know if the 2 clicks are on the same item or not.
            // * We can re-route the incorrect detection to `onSingleTap` to treat it as single
            //   click.
            // Returning false here also makes `GestureDetectorWrapper.onInterceptTouchEvent`
            // returns false (as explained in the above `onDoubleTap`), which prevents second tap's
            // ACTION_UP from being swallowed.
            return false;
        }
        ItemDetails<K> secondTapItem = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        // We must verify the second tap is also on the same item.
        // This prevents a sequence like "Left-Click on item1 -> Rapid Left-Click on item2" from
        // being treated as a valid double-click activation.
        if (mFirstTapItem != null
                && secondTapItem != null
                && secondTapItem.getSelectionKey() != null
                && secondTapItem.getSelectionKey().equals(mFirstTapItem.getSelectionKey())) {
            // No need to check if the second tap is right click or not, because `onDown` is called
            // for second tap before this.

            // If the first tap of the double click deselected the item (e.g. for double-clicking
            // on a selected file), re-select it here.
            if (!mSelectionTracker.isSelected(secondTapItem.getSelectionKey())) {
                selectItem(secondTapItem);
            }

            mOnItemActivatedListener.onItemActivated(secondTapItem, e);
            // Return true here for ACTION_UP because that's the last event for double click stream,
            // this makes sure no events in the double click gesture stream is intercepted by the
            // GestureDetectorWrapper (thus no events are routed to its onTouchEvent which is an
            // empty implementation), returning true also makes sure the double click event won't
            // be passed to other onItemTouchListener for RecyclerView.
            return true;
        }
        // When this is hit, it's because 2 rapid click happens on different items or with
        // different buttons:
        // * either the first click lands on a non-item (click blank area where `mFirstTapItem` is
        //   null),
        // * or the second click is a right click (e.g. `mFirstTapItem` resets to null in `onDown`
        //   for the second tap),
        // * or the second click lands on a non-item (e.g. blank area where `item` is null),
        // * or the second click lands on a different item (e.g. 2 items don't match).
        // Process it as a normal single tap.
        return onSingleTapUp(e);
    }

    private boolean onRightClick(@NonNull MotionEvent e) {
        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if ((item != null) && !mSelectionTracker.isSelected(item.getSelectionKey())) {
            clearSelectionQuietly();
            selectItem(item);
        }

        // We always delegate final handling of the event,
        // since the handler might want to show a context menu
        // in an empty area or some other weirdo view.
        return mOnContextClickListener.onContextClick(e);
    }
}
