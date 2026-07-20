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

package com.android.documentsui.testing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.StringRes;

import org.mockito.Mockito;

/**
 * Test copy of {@link android.view.MenuItem}.
 *
 * We use abstract so we don't have to implement all the necessary methods from the interface,
 * and we use Mockito to just mock out the methods we need.
 * To get an instance, use {@link #create(int)}.
 */
public abstract class TestMenuItem implements MenuItem {

    boolean enabled;
    boolean visible;
    View actionView;
    @StringRes int title;
    CharSequence titleCharSequence;
    Intent mIntent;
    int groupId;
    int itemId;
    int showAsAction;

    public static TestMenuItem create(int id) {
        return create(Menu.NONE, id);
    }

    public static TestMenuItem create(int groupId, int id) {
        final TestMenuItem mockMenuItem = Mockito.mock(TestMenuItem.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));

        mockMenuItem.groupId = groupId;
        mockMenuItem.itemId = id;
        // By default all menu items are enabled and visible.
        mockMenuItem.enabled = true;
        mockMenuItem.visible = true;

        return mockMenuItem;
    }

    @Override
    public TestMenuItem setTitle(@StringRes int title) {
        this.title = title;
        return this;
    }

    @Override
    public TestMenuItem setTitle(CharSequence title) {
        this.titleCharSequence = title;
        return this;
    }

    @Override
    public CharSequence getTitle() {
        return this.titleCharSequence;
    }

    @Override
    public MenuItem setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public MenuItem setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    final public MenuItem setActionView(View actionView) {
        this.actionView = actionView;
        return this;
    }

    @Override
    final public View getActionView() {
        return this.actionView;
    }

    @Override
    public MenuItem setIntent(Intent intent) {
        mIntent = intent;
        return this;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public int getItemId() {
        return itemId;
    }

    @Override
    public int getGroupId() {
        return groupId;
    }

    public void assertEnabledAndVisible() {
        assertTrue(this.title + " should be enabled", this.enabled);
        assertTrue(this.title + " should be visible", this.visible);
    }

    public void assertDisabledAndInvisible() {
        assertFalse(this.title + " should be disabled", this.enabled);
        assertFalse(this.title + " should be invisible", this.visible);
    }

    /** Asserts that the menu item is disabled but visible. */
    public void assertDisabledAndVisible() {
        assertFalse(this.title + " should be disabled", this.enabled);
        assertTrue(this.title + " should be visible", this.visible);
    }

    public void assertTitle(@StringRes int title) {
        assertTrue(this.title == title);
    }

    @Override
    public void setShowAsAction(int actionEnum) {
        this.showAsAction = actionEnum;
    }

    public int getShowAsAction() {
        return this.showAsAction;
    }
}
