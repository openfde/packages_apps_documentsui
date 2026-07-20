/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.annotation.LayoutRes;
import android.app.UiAutomation;
import android.content.Context;
import android.os.SystemClock;

import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.documentsui.R;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A test helper class that provides support for controlling and asserting against the roots list
 * drawer.
 */
public class SidebarBot extends Bots.BaseBot {
    private static final String TAG = "SidebarBot";

    private final String mRootListId;
    private final UiAutomation mAutomation;

    /**
     * Root list exists in both drawer and navigation rail. With this enum the
     * consumer can decide which container to use to find the root list:
     *  FOLLOW_LAYOUT: use nav rail in navigation rail layout, otherwise use drawer.
     *  FORCE_DRAWER: always use drawer, in fixed layout it uses the fixed navigation side bar, in
     *          drawer layout and navigation rail layout, it uses the drawer.
     *  FORCE_NAV_RAIL: use nav rail in navigation rail layout, navigation rail layout has both the
     *            nav rail and the drawer, this will force it to use the nav rail.
     */
    private enum RootListContainerType {
        FOLLOW_LAYOUT,
        FORCE_DRAWER,
        FORCE_NAV_RAIL
    }

    public SidebarBot(
            UiDevice device,
            UiAutomation automation,
            Context context,
            long timeout,
            @LayoutRes Integer layoutId) {
        super(device, context, timeout, layoutId);
        mAutomation = automation;
        mRootListId = mTargetPackage + ":id/roots_list";
    }

    private UiSelector getRootsContainerSelector(RootListContainerType containerType) {
        String containerId;
        switch (containerType) {
            case FORCE_DRAWER:
                containerId = ":id/container_roots";
                break;
            case FORCE_NAV_RAIL:
                containerId = ":id/nav_rail_container_roots";
                break;
            default:
                containerId =
                        this.inNavRailLayout()
                                ? ":id/nav_rail_container_roots"
                                : ":id/container_roots";
        }
        return new UiSelector()
                .resourceId(mTargetPackage + containerId)
                .childSelector(new UiSelector().resourceId(mRootListId));
    }

    private boolean toolbarHasTitle(String title) {
        try {
            mBots.main.assertWindowTitle(title);
            return true;
        } catch (AssertionFailedError e) {
            return false;
        }
    }

    private UiObject findRoot(String label, RootListContainerType containerType)
            throws UiObjectNotFoundException {
        // We might need to expand drawer if not visible.
        openDrawer();

        final UiSelector rootsList = getRootsContainerSelector(containerType);

        // Wait for the first list item to appear.
        boolean exists =
                new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(mTimeout);
        if (!exists) {
            throw new UiObjectNotFoundException("First list item not found after timeout");
        }

        // Now scroll around to find our item.
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));
        // If the list is scrolled to the bottom, there may be a bottom bounce animation, which
        // makes clicks fail. Wait for the animation to settle before returning the UI element so
        // the caller can interact with it reliably.
        SystemClock.sleep(500);
        return new UiObject(rootsList.childSelector(new UiSelector().text(label)));
    }

    /** Finds the root with the given label. */
    public UiObject findRoot(String label) throws UiObjectNotFoundException {
        return findRoot(label, RootListContainerType.FOLLOW_LAYOUT);
    }

    /** Open navigation root either from the Drawer or the Navigation rail. */
    public void openRoot(String label) throws UiObjectNotFoundException {
        if (toolbarHasTitle(label)) {
            return;
        }
        assertTrue(
                "Failed to click on root: " + label,
                findRoot(label, RootListContainerType.FOLLOW_LAYOUT).click());
        mBots.main.assertWindowTitle(label);
    }

    /**
     * Use openRoot above for general usage, which caters both the navigation rail and the drawer,
     * only use openNavRailRoot if you want to open root explicitly from the navigation rail.
     */
    public void openNavRailRoot(String label) throws UiObjectNotFoundException {
        if (toolbarHasTitle(label)) {
            return;
        }
        assertTrue(
                "Failed to click on nav rail root: " + label,
                findRoot(label, RootListContainerType.FORCE_NAV_RAIL).click());
        mBots.main.assertWindowTitle(label);
    }

    /** Open navigation drawer from the burger menu button within the navigation rail layout. */
    public void openDrawerFromNavRail() {
        onView(withId(R.id.nav_rail_burger_menu)).perform(click());
    }

    /**
     * Drawer can be open for both drawer layout and navigation rail layout, but this method only
     * opens drawer for the drawer layout.
     */
    public void openDrawer() throws UiObjectNotFoundException {
        // In drawer layout we explicitly open the drawer by clicking the burger menu in the
        // toolbar, in other layouts we do nothing because the nav sidebar is shown by default.
        if (!this.inDrawerLayout()) {
            return;
        }

        final UiSelector hamburgerSelector =
                new UiSelector()
                        .resourceId(mTargetPackage + ":id/toolbar")
                        .childSelector(
                                new UiSelector()
                                        .className("android.widget.ImageButton")
                                        .description(mContext.getString(R.string.drawer_open))
                                        .clickable(true));
        UiObject hamburgerButton = mDevice.findObject(hamburgerSelector);
        assertTrue("Hamburger button is NOT present",
                hamburgerButton.waitForExists(mTimeout));
        hamburgerButton.click();

        // Wait for the roots to appear and fail if it doesn't.
        assertTrue(
                mDevice.findObject(getRootsContainerSelector(RootListContainerType.FORCE_DRAWER))
                        .waitForExists(mTimeout));
    }

    /** Attempt to close the drawer. */
    public void closeDrawer() {
        // mLayoutId is null when it's not a FileActivity/PickerActivity.
        if (mLayoutId == null || inFixedLayout()) {
            // Not a layout with a drawer.
            return;
        }
        // Attempt to close the drawer if it's open.
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.close());
    }

    public void assertRootsPresent(String... labels) throws UiObjectNotFoundException {
        List<String> missing = new ArrayList<>();
        for (String label : labels) {
            if (!findRoot(label, RootListContainerType.FOLLOW_LAYOUT).exists()) {
                missing.add(label);
            }
        }
        if (!missing.isEmpty()) {
            Assert.fail(
                    "Expected roots " + Arrays.asList(labels) + ", but missing " + missing);
        }
    }

    public void assertRootsAbsent(String... labels) throws UiObjectNotFoundException {
        List<String> unexpected = new ArrayList<>();
        for (String label : labels) {
            if (findRoot(label, RootListContainerType.FOLLOW_LAYOUT).exists()) {
                unexpected.add(label);
            }
        }
        if (!unexpected.isEmpty()) {
            Assert.fail("Unexpected roots " + unexpected);
        }
    }

    public void assertHasFocus() {
        assertHasFocus(mRootListId);
    }

    /** Returns whether a root is focused. */
    public boolean anyRootHasFocus() {
        UiObject2 list = mDevice.findObject(By.res(mRootListId));
        return (list != null) && (list.findObject(By.focused(true)) != null);
    }

    /**
     * Check if the labelled item is selected on the sidebar.
     */
    public void assertItemSelected(String label) throws UiObjectNotFoundException {
        UiObject sidebarItem = findRoot(label, RootListContainerType.FOLLOW_LAYOUT);
        if (!sidebarItem.exists()) {
            throw new AssertionError("Cannot find item " + label);
        }
        assertTrue(sidebarItem.isSelected());
        closeDrawer();
    }

    /**
     * Check if the labelled item is not selected on the sidebar.
     */
    public void assertItemNotSelected(String label) throws UiObjectNotFoundException {
        UiObject sidebarItem = findRoot(label, RootListContainerType.FOLLOW_LAYOUT);
        if (!sidebarItem.exists()) {
            throw new AssertionError("Cannot find item " + label);
        }
        assertFalse(sidebarItem.isSelected());
        closeDrawer();
    }
}
