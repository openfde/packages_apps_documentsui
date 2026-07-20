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

package com.android.documentsui.base;


import static com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.platform.test.annotations.EnableFlags;

import androidx.test.filters.SmallTest;

import com.android.documentsui.TestRunner;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.Parcelables;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(TestRunner.class)
@SmallTest
public class StateTest {

    private static final String[] MIME_TYPES = {"image/gif", "image/jpg"};

    private Intent mIntent;
    private State mState;

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Before
    public void setUp() {
        mIntent = new Intent();
        mState = new State();
    }

    @Test
    public void testAcceptGivenMimeTypesInExtra() {
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);

        mState.initAcceptMimes(mIntent, "*/*");

        assertArrayEquals(MIME_TYPES, mState.acceptMimes);
    }

    @Test
    public void testAcceptIntentTypeWithoutExtra() {
        mState.initAcceptMimes(mIntent, MIME_TYPES[0]);

        assertArrayEquals(new String[]{MIME_TYPES[0]}, mState.acceptMimes);
    }

    @Test
    public void testShouldShowPreview_actionBrowse() {
        mState.action = State.ACTION_BROWSE;

        assertFalse(mState.shouldShowPreview(true));
    }

    @Test
    public void testShouldShowPreview_actionOpen() {
        mState.action = State.ACTION_OPEN;

        assertTrue(mState.shouldShowPreview(true));
    }

    @Test
    public void testShouldShowPreview_actionGetContent() {
        mState.action = State.ACTION_GET_CONTENT;

        assertTrue(mState.shouldShowPreview(true));
    }

    @Test
    public void testShouldShowPreview_actionOpenTree() {
        mState.action = State.ACTION_OPEN_TREE;

        assertTrue(mState.shouldShowPreview(true));
    }

    @Test
    public void testPhotoPicking_onlyOneImageType() {
        String[] stringArray = {MIME_TYPES[0]};
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, stringArray);

        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_GET_CONTENT;

        assertTrue(mState.isPhotoPicking());
    }

    @Test
    public void testPhotoPicking_allImageTypes() {
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_GET_CONTENT;

        assertTrue(mState.isPhotoPicking());
    }

    @Test
    public void testPhotoPicking_noImageType() {
        final String[] mimeTypes = {"audio/mp3", "video/mp4"};
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_GET_CONTENT;

        assertFalse(mState.isPhotoPicking());
    }

    @Test
    public void testPhotoPicking_oneIsNotImageType() {
        final String[] mimeTypes = {"image/gif", "image/jpg", "audio/mp3"};
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_GET_CONTENT;

        assertFalse(mState.isPhotoPicking());
    }

    @Test
    public void testPhotoPicking_browseMode() {
        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_BROWSE;

        assertFalse(mState.isPhotoPicking());
    }

    @Test
    public void testPhotoPicking_nullExrta() {
        final String[] mimeTypes = null;
        mIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        mState.initAcceptMimes(mIntent, "*/*");
        mState.action = State.ACTION_GET_CONTENT;

        assertFalse(mState.isPhotoPicking());
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testParcelingWithNoShortcut() {
        mState.shortcut = null;

        Parcelables.assertParcelable(mState, 0,
            (State left, State right) -> Objects.equals(left.toString(), right.toString()));
    }

    @Test
    @EnableFlags({FLAG_HOME_SCREEN_FILES_RO, FLAG_USE_MATERIAL3})
    public void testParcelingWithShortcut() {
        RootInfo root = new RootInfo();
        root.userId = UserId.of(100);
        root.authority = "aaa";
        root.rootId = "rrr";
        mState.shortcut = new ShortcutInfo(root, "ppp", "ttt", "lll", 10);

        Parcelables.assertParcelable(mState, 0,
            (State left, State right) -> Objects.equals(left.toString(), right.toString()));
    }
}
