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

import static androidx.recyclerview.selection.testing.TestEvents.Mouse.ALT_CLICK;
import static androidx.recyclerview.selection.testing.TestEvents.Mouse.CLICK;
import static androidx.recyclerview.selection.testing.TestEvents.Mouse.CTRL_CLICK;
import static androidx.recyclerview.selection.testing.TestEvents.Mouse.SECONDARY_CLICK;
import static androidx.recyclerview.selection.testing.TestEvents.Mouse.SHIFT_CLICK;
import static androidx.recyclerview.selection.testing.TestEvents.Mouse.TERTIARY_CLICK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.testing.SelectionProbe;
import androidx.recyclerview.selection.testing.SelectionTrackers;
import androidx.recyclerview.selection.testing.TestAdapter;
import androidx.recyclerview.selection.testing.TestData;
import androidx.recyclerview.selection.testing.TestEvents;
import androidx.recyclerview.selection.testing.TestFocusDelegate;
import androidx.recyclerview.selection.testing.TestItemDetails;
import androidx.recyclerview.selection.testing.TestItemDetailsLookup;
import androidx.recyclerview.selection.testing.TestItemKeyProvider;
import androidx.recyclerview.selection.testing.TestOnContextClickListener;
import androidx.recyclerview.selection.testing.TestOnItemActivatedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MouseInputHandlerTest {

    private MouseInputHandler<String> mInputDelegate;

    private TestOnContextClickListener mMouseCallbacks;
    private TestOnItemActivatedListener<String> mActivationCallbacks;
    private TestFocusDelegate<String> mFocusCallbacks;

    private TestItemDetailsLookup mDetailsLookup;
    private SelectionProbe mSelection;
    private SelectionTracker<String> mSelectionMgr;

    private TestEvents.Builder mEvent;

    @Before
    public void setUp() {

        mSelectionMgr = SelectionTrackers.createStringTracker("mouse-input-test", 100);
        mDetailsLookup = new TestItemDetailsLookup();
        mSelection = new SelectionProbe(mSelectionMgr);

        mMouseCallbacks = new TestOnContextClickListener();
        mActivationCallbacks = new TestOnItemActivatedListener<>();
        mFocusCallbacks = new TestFocusDelegate<>();

        mInputDelegate = new MouseInputHandler<>(
                mSelectionMgr,
                new TestItemKeyProvider<>(
                        ItemKeyProvider.SCOPE_MAPPED,
                        new TestAdapter<>(TestData.createStringData(100))),
                mDetailsLookup,
                mMouseCallbacks,
                mActivationCallbacks,
                mFocusCallbacks);

        mEvent = TestEvents.builder().mouse();
        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
    }

    private boolean callTapHandlers(MotionEvent e, boolean isDoubleTap) {
        // Follow the real event stream for single click and double click:
        // * single click: onDown -> onSingleTapUp
        // * double click: onDown -> onSingleTapUp -> onDoubleTap (first tap down)
        //                 -> onDoubleTapEvent (second tap down) -> onDown
        //                 -> onDoubleTapEvent (second tap up)

        final int gapBetweenDownAndUpInMs = 50;
        // By default, 2 clicks within 300ms are detected as double click.
        final int gapBetweenTwoClicksInMs = ViewConfiguration.getDoubleTapTimeout() / 2;
        // All events will follow the same setup as the passed in event (e.g. primary/secondary
        // click, ctrl/shift pressed), only updating the event time and action type.
        MotionEvent downEvent = e;
        MotionEvent upEvent =
                TestEvents.builder()
                        .copyFrom(e)
                        .up()
                        .time(e.getEventTime() + gapBetweenDownAndUpInMs)
                        .build();
        MotionEvent secondDownEvent =
                TestEvents.builder()
                        .copyFrom(e)
                        .time(e.getEventTime() + gapBetweenTwoClicksInMs)
                        .downTime(e.getEventTime() + gapBetweenTwoClicksInMs)
                        .build();
        MotionEvent secondUpEvent =
                TestEvents.builder()
                        .copyFrom(secondDownEvent)
                        .up()
                        .time(e.getEventTime() + gapBetweenDownAndUpInMs)
                        .build();

        boolean handled = mInputDelegate.onDown(downEvent)
                || mInputDelegate.onSingleTapUp(upEvent);

        if (isDoubleTap) {
            // As can be seen in the GestureDetector.onTouchEvent code, for the ACTION_DOWN case,
            // onDoubleTap will be called first (with the first down event) and then
            // onDoubleTapEvent will be called (with the second down event), and then onDown will be
            // called (with the second down event), regardless of whether onDoubleTap returned true
            // or false. When the GestureDetector recognizes this as a double tap, it also won't
            // call onSingleTapEtc methods.
            handled |= mInputDelegate.onDoubleTap(downEvent);
            handled |= mInputDelegate.onDoubleTapEvent(secondDownEvent);
            handled |= mInputDelegate.onDown(secondDownEvent);
            handled |= mInputDelegate.onDoubleTapEvent(secondUpEvent);
        }

        return handled;
    }

    private boolean singleTap(MotionEvent e) {
        return callTapHandlers(e, false);
    }

    private boolean doubleTap(MotionEvent e) {
        return callTapHandlers(e, true);
    }

    @Test
    public void testConfirmedClick_StartsSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        singleTap(CLICK);

        mSelection.assertSelection(11);
    }

    @Test
    public void testClickOnSelectionHotspot_Outside() {
        // When nothing is selected, clicking outside the hotspot does nothing.

        mSelection.assertNoSelection();

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_OUTSIDE);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_OUTSIDE);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        // When something is selected, clicking outside the hotspot does something conditional. If
        // clicking on a selected item, it deselects it. Otherwise, it clears the entire selection.

        mSelectionMgr.clearSelection();
        mSelectionMgr.select("8");
        mSelectionMgr.select("9");
        mSelectionMgr.select("10");
        mSelectionMgr.select("11");
        mSelection.assertSelection(8, 9, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_OUTSIDE);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(8, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_OUTSIDE);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        mDetailsLookup.initAt(20).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_OUTSIDE);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();
    }

    @Test
    public void testClickOnSelectionHotspot_InsideToggleMulti() {
        // Clicking inside the hotspot toggles selection, with multiple items selectable.

        mSelection.assertNoSelection();

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(5);

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        mSelectionMgr.clearSelection();
        mSelectionMgr.select("8");
        mSelectionMgr.select("9");
        mSelectionMgr.select("10");
        mSelectionMgr.select("11");
        mSelection.assertSelection(8, 9, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(8, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(8, 9, 10, 11);

        mDetailsLookup.initAt(20).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(8, 9, 10, 11, 20);
    }

    @Test
    public void testClickOnSelectionHotspot_InsideToggleSolo() {
        // Clicking inside the hotspot toggles selection, with just one item selectable.

        mSelection.assertNoSelection();

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(5);

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        mSelectionMgr.clearSelection();
        mSelectionMgr.select("8");
        mSelectionMgr.select("9");
        mSelectionMgr.select("10");
        mSelectionMgr.select("11");
        mSelection.assertSelection(8, 9, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(9);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        assertTrue(singleTap(CLICK));
        mSelection.assertNoSelection();

        mDetailsLookup.initAt(20).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(20);
    }

    @Test
    public void testClickOnSelectionHotspot_InsideClearAndThenSet() {
        // Clicking inside the hotspot clears and then sets selection.

        mSelection.assertNoSelection();

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(5);

        mDetailsLookup.initAt(5).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(5);

        mSelectionMgr.clearSelection();
        mSelectionMgr.select("8");
        mSelectionMgr.select("9");
        mSelectionMgr.select("10");
        mSelectionMgr.select("11");
        mSelection.assertSelection(8, 9, 10, 11);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(9);

        mDetailsLookup.initAt(9).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(9);

        mDetailsLookup.initAt(20).setClassifySelectionHotspot(
                ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET);
        assertTrue(singleTap(CLICK));
        mSelection.assertSelection(20);
    }

    @Test
    public void testClickOnSelectRegion_AddsToSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(10).setInItemSelectRegion(true);
        singleTap(CLICK);

        mSelection.assertSelected(10, 11);
    }

    @Test
    public void testClickOnIconOfSelectedItem_RemovesFromSelection() {
        mDetailsLookup.initAt(8).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(11);
        singleTap(SHIFT_CLICK);
        mSelection.assertSelected(8, 9, 10, 11);

        mDetailsLookup.initAt(9);
        singleTap(CLICK);
        mSelection.assertSelected(8, 10, 11);
    }

    @Test
    public void testRightClickDown_StartsContextMenu() {
        mInputDelegate.onDown(SECONDARY_CLICK);

        mMouseCallbacks.assertLastEvent(SECONDARY_CLICK);
    }

    @Test
    public void testAltClickDown_StartsContextMenu() {
        mInputDelegate.onDown(ALT_CLICK);

        mMouseCallbacks.assertLastEvent(ALT_CLICK);
    }

    @Test
    public void testScroll_shouldTrap() {
        mDetailsLookup.initAt(0);
        assertTrue(mInputDelegate.onScroll(
                null,
                mEvent.action(MotionEvent.ACTION_MOVE).primary().build(),
                -1,
                -1));
    }

    @Test
    public void testScroll_NoTrapForTwoFinger() {
        mDetailsLookup.initAt(0);
        assertFalse(mInputDelegate.onScroll(
                null,
                mEvent.action(MotionEvent.ACTION_MOVE).build(),
                -1,
                -1));
    }

    @Test
    public void testUnconfirmedShiftClick_ExtendsSelection() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(11);
        singleTap(SHIFT_CLICK);

        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testConfirmedShiftClick_ExtendsSelectionFromFocus() {
        TestItemDetails item = mDetailsLookup.initAt(7);
        mFocusCallbacks.focusItem(item);

        // There should be no selected item at this point, just focus on "7".
        mDetailsLookup.initAt(11);
        singleTap(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_RotatesAroundOrigin() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(11);
        singleTap(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mDetailsLookup.initAt(5);
        singleTap(SHIFT_CLICK);

        mSelection.assertSelection(5, 6, 7);
        mSelection.assertNotSelected(8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_Combination() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(11);
        singleTap(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_ShiftTakesPriority() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(11);
        singleTap(mEvent.primary().ctrl().shift().build());

        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    // TODO: Add testSpaceBar_Previews, but we need to set a system property
    // to have a deterministic state.

    @Test
    public void testDoubleClick_Opens() {
        // Double-click on an unselected file should open it and select it.
        TestItemDetails doc1 = mDetailsLookup.initAt(1);
        assertTrue(doubleTap(CLICK));
        mActivationCallbacks.assertActivated(doc1);
        mSelection.assertSelection(1);

        // Double-click on a selected file should also open it and keep it selected.
        mSelectionMgr.clearSelection();
        mSelectionMgr.select("2");
        TestItemDetails doc2 = mDetailsLookup.initAt(2);
        assertTrue(doubleTap(CLICK));
        mActivationCallbacks.assertActivated(doc2);
        mSelection.assertSelection(2);
    }

    @Test
    public void testDoubleClick_Opens_TwoItems() {
        // Double-click on an unselected file should open it and select it.
        TestItemDetails doc1 = mDetailsLookup.initAt(1);
        assertTrue(doubleTap(CLICK));
        mActivationCallbacks.assertActivated(doc1);
        mSelection.assertSelection(1);

        // Double-click on another unselected file should open it and keep it selected.
        TestItemDetails doc2 = mDetailsLookup.initAt(2);
        assertTrue(doubleTap(CLICK));
        mActivationCallbacks.assertActivated(doc2);
        mSelection.assertSelection(2);
    }

    @Test
    public void testRapidClick_DifferentItems_SelectsSecondItem() {
        // We can't use the helper doubleTap() because the 2 clicks have different items.
        final long firstDownTime = 100;
        final long firstUpTime = firstDownTime + 10;
        // Second tap is within the default double tap timeout (300ms).
        final long secondDownTime = 200;
        final long secondUpTime = secondDownTime + 10;

        // 1. First click on item 1 (on its selection hotspot to select it).
        MotionEvent firstTapDown =
                TestEvents.builder().mouse().primary().down().time(firstDownTime).build();
        MotionEvent firstTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(firstUpTime)
                        .downTime(firstDownTime)
                        .build();
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDown(firstTapDown);
        mInputDelegate.onSingleTapUp(firstTapUp);

        mSelection.assertSelection(1);

        // 2. Second click on item 2 (rapid enough to trigger GestureDetector's double-tap flow).
        MotionEvent secondTapDown =
                TestEvents.builder().mouse().primary().down().time(secondDownTime).build();
        MotionEvent secondTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(secondUpTime)
                        .downTime(secondDownTime)
                        .build();
        // GestureDetector.onDoubleTap receives the FIRST click's down event.
        // Therefore, the ItemDetailsLookup will resolve the coordinates to item 1.
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDoubleTap(firstTapDown);

        // For the subsequent events (the second click), the coordinates resolve to item 2.
        mDetailsLookup
                .initAt(2)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDoubleTapEvent(secondTapDown);

        // GestureDetector.onDown receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDown(secondTapDown);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_UP.
        mInputDelegate.onDoubleTapEvent(secondTapUp);

        // Should NOT activate since the two clicks were on different items.
        mActivationCallbacks.assertActivated(null);

        // Selection should now contain only item2, item1 is unselected.
        mSelection.assertSelection(2);
    }

    @Test
    public void testRapidClick_LeftAndRight_TriggerRightClick() {
        // We can't use the helper doubleTap() because the 2 clicks have different buttons.
        final long firstDownTime = 100;
        final long firstUpTime = firstDownTime + 10;
        // Second tap is within the default double tap timeout (300ms).
        final long secondDownTime = 200;
        final long secondUpTime = secondDownTime + 10;

        // 1. First click on item 1 (on its selection hotspot to select it).
        MotionEvent firstTapDown =
                TestEvents.builder().mouse().primary().down().time(firstDownTime).build();
        MotionEvent firstTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(firstUpTime)
                        .downTime(firstDownTime)
                        .build();
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDown(firstTapDown);
        mInputDelegate.onSingleTapUp(firstTapUp);

        mSelection.assertSelection(1);

        // 2. Second click on item 1 but with secondary button.
        MotionEvent secondTapDown =
                TestEvents.builder().mouse().secondary().down().time(secondDownTime).build();
        MotionEvent secondTapUp =
                TestEvents.builder()
                        .mouse()
                        .secondary()
                        .up()
                        .time(secondUpTime)
                        .downTime(secondDownTime)
                        .build();
        // GestureDetector.onDoubleTap receives the FIRST click's down event.
        // Therefore, the ItemDetailsLookup will resolve the coordinates to item 1.
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDoubleTap(firstTapDown);

        // For the subsequent events (the second click), the coordinates still resolve to item 1.
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDoubleTapEvent(secondTapDown);

        // GestureDetector.onDown receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDown(secondTapDown);

        // onDoubleTapEvent(ACTION_UP) won't be triggered because onDown() triggers the right click
        // flow.

        // Should NOT activate since the second click is a right click.
        mActivationCallbacks.assertActivated(null);

        // Item 1 should still be selected because it triggers the right click for it eventually.
        mSelection.assertSelected(1);

        // Check the last event is the secondary click handled in onDown().
        mMouseCallbacks.assertLastEvent(secondTapDown);
    }

    @Test
    public void testRapidClick_BlankAreaAndItem_SelectItem() {
        // We can't use the helper doubleTap() because the 2 clicks have different items.
        final long firstDownTime = 100;
        final long firstUpTime = firstDownTime + 10;
        // Second tap is within the default double tap timeout (300ms).
        final long secondDownTime = 200;
        final long secondUpTime = secondDownTime + 10;

        // 1. First click on blank area (no item).
        MotionEvent firstTapDown =
                TestEvents.builder().mouse().primary().down().time(firstDownTime).build();
        MotionEvent firstTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(firstUpTime)
                        .downTime(firstDownTime)
                        .build();
        mDetailsLookup.reset();
        mInputDelegate.onDown(firstTapDown);
        mInputDelegate.onSingleTapUp(firstTapUp);

        mSelection.assertNoSelection();

        // 2. Second click on item 1.
        MotionEvent secondTapDown =
                TestEvents.builder().mouse().primary().down().time(secondDownTime).build();
        MotionEvent secondTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(secondUpTime)
                        .downTime(secondDownTime)
                        .build();
        // GestureDetector.onDoubleTap receives the FIRST click's down event.
        // Therefore, the ItemDetailsLookup will resolve no item.
        mDetailsLookup.reset();
        mInputDelegate.onDoubleTap(firstTapDown);

        // For the subsequent events (the second click), the coordinates resolve to item 1.
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDoubleTapEvent(secondTapDown);

        // GestureDetector.onDown receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDown(secondTapDown);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_UP.
        mInputDelegate.onDoubleTapEvent(secondTapUp);

        // Should NOT activate since the second click has no item.
        mActivationCallbacks.assertActivated(null);

        // Selection should now contain item 1.
        mSelection.assertSelected(1);
    }

    @Test
    public void testRapidClick_ItemAndBlankArea_DeselectItem() {
        // We can't use the helper doubleTap() because the 2 clicks have different items.
        final long firstDownTime = 100;
        final long firstUpTime = firstDownTime + 10;
        // Second tap is within the default double tap timeout (300ms).
        final long secondDownTime = 200;
        final long secondUpTime = secondDownTime + 10;

        // 1. First click on item 1 (on its selection hotspot to select it).
        MotionEvent firstTapDown =
                TestEvents.builder().mouse().primary().down().time(firstDownTime).build();
        MotionEvent firstTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(firstUpTime)
                        .downTime(firstDownTime)
                        .build();
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDown(firstTapDown);
        mInputDelegate.onSingleTapUp(firstTapUp);

        mSelection.assertSelection(1);

        // 2. Second click on blank area (e.g. no item).
        MotionEvent secondTapDown =
                TestEvents.builder().mouse().primary().down().time(secondDownTime).build();
        MotionEvent secondTapUp =
                TestEvents.builder()
                        .mouse()
                        .primary()
                        .up()
                        .time(secondUpTime)
                        .downTime(secondDownTime)
                        .build();
        // GestureDetector.onDoubleTap receives the FIRST click's down event.
        // Therefore, the ItemDetailsLookup will resolve the coordinates to item 1.
        mDetailsLookup
                .initAt(1)
                .setClassifySelectionHotspot(ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO);
        mInputDelegate.onDoubleTap(firstTapDown);

        // For the subsequent events (the second click), the coordinates resolve to null item.
        mDetailsLookup.reset();

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDoubleTapEvent(secondTapDown);

        // GestureDetector.onDown receives the SECOND click's ACTION_DOWN.
        mInputDelegate.onDown(secondTapDown);

        // GestureDetector.onDoubleTapEvent receives the SECOND click's ACTION_UP.
        mInputDelegate.onDoubleTapEvent(secondTapUp);

        // Should NOT activate since the second click has no item.
        mActivationCallbacks.assertActivated(null);

        // Selection should now contain nothing, the second click on the blank area should deselect
        // item 1.
        mSelection.assertNoSelection();
    }

    @Test
    public void testDoubleClick_Ctrl_TogglesSelection_StartWithUnselectedItem() {
        // Double click item 2 with Ctrl.
        mDetailsLookup.initAt(2).setInItemSelectRegion(true);
        doubleTap(CTRL_CLICK);

        // Should NOT activate and select 2 because first tap select it and second tap deselect it.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertNoSelection();
    }

    @Test
    public void testDoubleClick_Ctrl_TogglesSelection_StartWithSelectedItem() {
        // Select item 2 first.
        mSelectionMgr.select("2");

        // Double click item 2 with Ctrl.
        mDetailsLookup.initAt(2).setInItemSelectRegion(true);
        doubleTap(CTRL_CLICK);

        // Should NOT activate but will select 2 because first tap deselect it and second tap
        // select it.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertSelection(2);
    }

    @Test
    public void testDoubleClick_Ctrl_TogglesSelection_withAnotherSelectedItem() {
        // Select item 5 first.
        mSelectionMgr.select("5");

        // Double click item 2 with Ctrl.
        mDetailsLookup.initAt(2).setInItemSelectRegion(true);
        doubleTap(CTRL_CLICK);

        // Should NOT activate and select 2 because first tap select it and second tap deselect it.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertSelection(5);
    }

    @Test
    public void testDoubleClick_Shift_ExtendRange_StartWithUnselectedItem() {
        // Double click item 2 with Shift.
        mDetailsLookup.initAt(2).setInItemSelectRegion(true);
        doubleTap(SHIFT_CLICK);

        // Should NOT activate but will select 2 because Shift+Click doesn't deselect files.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertSelection(2);
    }

    @Test
    public void testDoubleClick_Shift_ExtendRange_StartWithSelectedItem() {
        // Select item 2 first.
        mSelectionMgr.select("2");

        // Double click item 2 with Shift.
        mDetailsLookup.initAt(2).setInItemSelectRegion(true);
        doubleTap(SHIFT_CLICK);

        // Should NOT activate but will select 2 because Shift+Click doesn't deselect files.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertSelection(2);
    }

    @Test
    public void testDoubleClick_Shift_ExtendRange_withAnotherSelectedItem() {
        // Select item 5 first.
        mDetailsLookup.initAt(5).setInItemSelectRegion(true);
        singleTap(CLICK);
        mSelection.assertSelection(5);

        // Double click item 2 with Shift.
        mDetailsLookup.initAt(2);
        doubleTap(SHIFT_CLICK);

        // Should NOT activate but will select 2-5 because Shift+Click select files in range.
        mActivationCallbacks.assertActivated(null);
        mSelection.assertRangeSelected(2, 5);
    }

    @Test
    public void testMiddleClick_DoesNothing() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        singleTap(TERTIARY_CLICK);

        mSelection.assertNoSelection();
    }

    @Test
    public void testClickOff_ClearsSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
        singleTap(CLICK);

        mSelection.assertNoSelection();
    }

    @Test
    public void testClick_Focuses() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(false);
        singleTap(CLICK);

        mFocusCallbacks.assertHasFocus(true);
        mFocusCallbacks.assertFocused("11");
    }

    @Test
    public void testClickOff_ClearsFocus() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(false);
        singleTap(CLICK);
        mFocusCallbacks.assertHasFocus(true);

        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
        singleTap(CLICK);
        mFocusCallbacks.assertHasFocus(false);
    }

    @Test
    public void testClickOffSelection_RemovesSelectionAndFocuses() {
        mDetailsLookup.initAt(1).setInItemSelectRegion(true);
        singleTap(CLICK);

        mDetailsLookup.initAt(5);
        singleTap(SHIFT_CLICK);

        mSelection.assertSelection(1, 2, 3, 4, 5);

        mDetailsLookup.initAt(11);
        singleTap(CLICK);

        mFocusCallbacks.assertFocused("11");
        mSelection.assertNoSelection();
    }
}
