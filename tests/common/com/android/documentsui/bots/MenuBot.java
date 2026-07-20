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

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.not;

import android.annotation.LayoutRes;
import android.content.Context;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.espresso.ViewInteraction;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.actions.WaitUntilVisible;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;

/**
 * A test helper class that provides support for controlling menu items.
 */
public class MenuBot extends Bots.BaseBot {

    private static final String TAG = "MenuBot";

    public MenuBot(UiDevice device, Context context, long timeout, @LayoutRes Integer layoutId) {
        super(device, context, timeout, layoutId);
    }

    /** Attempts to find the menu item by also scrolling it into view if necessary. */
    private boolean scrollMenuItemIntoView(String label) {
        final UiObject2 item = mDevice.wait(Until.findObject(By.text(label)), /* timeout= */ 3000);
        if (item != null) {
            return true;
        }

        // Can't find the item, attempt to scroll to it instead.
        UiSelector scrollableView = new UiSelector().scrollable(true);
        final UiObject scrollableViewObject = mDevice.findObject(scrollableView);
        if (!scrollableViewObject.exists()) {
            // If there is no scrollable view available then the menu item is just not visible.
            return false;
        }

        UiScrollable contextScroller = new UiScrollable(scrollableView);
        try {
            final UiObject menuItem =
                    contextScroller.getChildByText(
                            new UiSelector().className(TextView.class), label);
            return menuItem != null;
        } catch (UiObjectNotFoundException e) {
            return false;
        }
    }

    public boolean hasMenuItem(String menuLabel) {
        return mDevice.findObject(By.text(menuLabel)) != null;
    }

    public boolean hasMenuItemByDesc(String menuDesc) {
        return mDevice.findObject(By.desc(menuDesc)) != null;
    }

    public void clickMenuItem(String label) throws UiObjectNotFoundException {
        final UiObject2 item = mDevice.wait(Until.findObject(By.text(label)), mTimeout);
        if (item == null) {
            throw new UiObjectNotFoundException("Cannot find the '" + label + "' menu item");
        }
        item.click();

        // Wait for the menu item to disappear from the view hierarchy.
        // This ensures the menu is closed after the click.
        mDevice.wait(Until.gone(By.text(label)), mTimeout);
    }

    /** Asserts that some menu items are present (and attempts to scroll to them if not). */
    public void assertPresentMenuItems(Map<String, Boolean> menuStates)
            throws UiObjectNotFoundException {
        for (String key : menuStates.keySet()) {
            boolean exists = scrollMenuItemIntoView(key);
            if (menuStates.get(key)) {
                assertTrue(key + " expected to be shown", exists);
            } else {
                assertFalse(key + " expected not to be shown", exists);
            }
        }
    }

    /** Finds the action menu item with the given label within the toolbar. */
    public ViewInteraction findToolbarActionMenuItem(@IdRes int id) {
        // The label is stored as the content description of the action menu item.
        return onView(allOf(withClassName(endsWith("ActionMenuItemView")), withId(id)))
                .perform(new WaitUntilVisible(mTimeout));
    }

    /** Finds the list menu item with the given label, scrolling to it if necessary. */
    public ViewInteraction findListMenuItem(String menuLabel) {
        // onData scrolls to the item.
        return onData(new MenuItemMatcher(menuLabel))
                .inAdapterView(withClassName(endsWith("MenuDropDownListView")))
                .inRoot(isPlatformPopup())
                .perform(new WaitUntilVisible(mTimeout));
    }

    /** Asserts that given menu items are visible and disabled. */
    public void assertListMenuItemsVisibleAndDisabled(@StringRes int... menuItemResIds) {
        for (int id : menuItemResIds) {
            String menuLabel = mContext.getString(id);
            // Check that the menu item is disabled.
            findListMenuItem(menuLabel).check(matches(not(isEnabled())));
        }
    }

    /**
     * Asserts that given `expectedMenuItems` are visible and enabled (and attempts to scroll to
     * them if not in view). .
     */
    public void assertListMenuItemsVisibleAndEnabled(@StringRes int... menuItemResIds) {
        for (int id : menuItemResIds) {
            String menuLabel = mContext.getString(id);
            // Check that the menu item is enabled.
            findListMenuItem(menuLabel).check(matches(isEnabled()));
        }
    }

    /** Asserts that given action menu items within the toolbar are visible and disabled. */
    public void assertToolbarMenuItemsVisibleAndDisabled(@IdRes int... menuItemResIds) {
        for (int id : menuItemResIds) {
            findToolbarActionMenuItem(id).check(matches(not(isEnabled())));
        }
    }

    /** Asserts that given action menu items within the toolbar are visible and disabled. */
    public void assertToolbarMenuItemsVisibleAndEnabled(@IdRes int... menuItemResIds) {
        for (int id : menuItemResIds) {
            findToolbarActionMenuItem(id).check(matches(isEnabled()));
        }
    }

    /** Matcher used for finding a MenuItem. */
    private static class MenuItemMatcher extends TypeSafeMatcher<Object> {
        private final String mLabel;

        MenuItemMatcher(String label) {
            mLabel = label;
        }

        @Override
        public boolean matchesSafely(Object item) {
            if (!(item instanceof MenuItem menuItem)) {
                return false;
            }
            final CharSequence title = menuItem.getTitle();
            return title != null && mLabel.equalsIgnoreCase(title.toString());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("with menu item title: " + mLabel);
        }
    }
}
