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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.annotation.LayoutRes;
import android.content.Context;
import android.view.View;
import android.view.View.Visibility;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.documentsui.actions.WaitUntilGone;
import com.android.documentsui.actions.WaitUntilVisible;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A test helper class that provides support for controlling the UI Breadcrumb programmatically, and
 * making assertions against the state of the UI.
 *
 * <p>Support for working directly with Roots and Directory view can be found in the respective
 * bots.
 */
public class BreadBot extends Bots.BaseBot {

    private final String mBreadCrumbId;

    /** A view assertion that extract the path and checks it against the given predicate. */
    private static class PathViewAssertion implements ViewAssertion {
        private final String[] mExpectedPath;
        private final BiPredicate<String[], String[]> mMatcher;

        PathViewAssertion(String[] expectedPath, BiPredicate<String[], String[]> matcher) {
            mExpectedPath = expectedPath;
            mMatcher = matcher;
        }

        private List<String> getBreadcrumbV2Path(ViewGroup parent) {
            List<String> path = new ArrayList<>();
            for (int i = 0; i < parent.getChildCount(); ++i) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView) {
                    path.add(((TextView) child).getText().toString());
                }
            }
            return path;
        }

        @Override
        public void check(View view, NoMatchingViewException noViewFoundException) {
            if (!(view instanceof ViewGroup) || noViewFoundException != null) {
                throw new AssertionFailedError("Failed to locate the view");
            }
            List<String> shownPath = getBreadcrumbV2Path((ViewGroup) view);
            String[] got = shownPath.toArray(new String[0]);
            if (!mMatcher.test(got, mExpectedPath)) {
                throw new AssertionFailedError(
                        "Path '"
                                + String.join("/", mExpectedPath)
                                + "' does not match '"
                                + String.join("/", got)
                                + "'");
            }
        }
    }

    /**
     * @param expectedPath Path to be compared with the one shown in the breadcrumbs.
     * @return A view assertion that checks if the path is equal to the expected.
     */
    public ViewAssertion pathEqualsTo(String... expectedPath) {
        return new PathViewAssertion(expectedPath, Arrays::equals);
    }

    /**
     * @param expectedPathRegEx Regular expressions to be matched against the breadcrumb path.
     * @return A view assertion that checks if the path matches to the expected regular expressions.
     */
    public ViewAssertion pathMatches(String... expectedPathRegEx) {
        return new PathViewAssertion(
                expectedPathRegEx,
                (String[] got, String[] want) -> {
                    if (got.length != want.length) {
                        return false;
                    }
                    for (int i = 0; i < got.length; ++i) {
                        if (!got[i].matches(want[i])) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    /**
     * @param expectedPath Path to be compared with the one shown in the breadcrumbs.
     * @return A view assertion that checks if the path starts with the expected path.
     */
    public ViewAssertion pathStartsWith(String... expectedPath) {
        return new PathViewAssertion(
                expectedPath,
                (String[] got, String[] want) -> {
                    if (got.length < want.length) {
                        return false;
                    }
                    for (int i = 0; i < want.length; ++i) {
                        if (!got[i].equals(want[i])) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    public BreadBot(UiDevice device, Context context, long timeout, @LayoutRes Integer layoutId) {
        super(device, context, timeout, layoutId);
        mBreadCrumbId = mTargetPackage + ":id/horizontal_breadcrumb";
    }

    public void clickItem(String label) {
        findHorizontalEntry(label).click();
    }

    public void assertItemsPresent(String... items) {
        Predicate<String> checker = this::hasHorizontalEntry;
        assertItemsPresent(items, checker);
    }

    private void assertItemsPresent(String[] items, Predicate<String> predicate) {
        List<String> absent = new ArrayList<>();
        for (String item : items) {
            if (!predicate.test(item)) {
                absent.add(item);
            }
        }
        if (!absent.isEmpty()) {
            Assert.fail("Expected iteams " + Arrays.asList(items) + ", but missing " + absent);
        }
    }

    /**
     * Checks if the breadcrumb with the specified ID has the specified visibility.
     *
     * @param breadcrumbId The ID of the breadcrumb.
     * @param visibility The desired visibility.
     */
    public void assertBreadcrumbHasVisibility(@IdRes int breadcrumbId, @Visibility int visibility) {
        ViewMatchers.Visibility effectiveVisibility =
                ViewMatchers.Visibility.forViewVisibility(visibility);
        onView(withId(breadcrumbId)).check(matches(withEffectiveVisibility(effectiveVisibility)));
    }

    /**
     * Waits for the given visibility of the breadcrumb with the specified ID.
     *
     * @param breadcrumbId The ID of the breadcrumb.
     * @param visibility The desired visibility.
     */
    public void waitForBreadcrumbVisibility(@IdRes int breadcrumbId, @Visibility int visibility) {
        if (View.GONE == visibility) {
            onView(withId(breadcrumbId)).perform(new WaitUntilGone(mTimeout));
        } else {
            onView(withId(breadcrumbId)).perform(new WaitUntilVisible(mTimeout));
        }
    }

    private boolean hasHorizontalEntry(String label) {
        return findHorizontalEntry(label) != null;
    }

    @SuppressWarnings("unchecked")
    private UiObject2 findHorizontalEntry(String label) {
        UiObject2 breadcrumb = mDevice.findObject(By.res(mBreadCrumbId));
        return breadcrumb.findObject(By.text(label));
    }
}
