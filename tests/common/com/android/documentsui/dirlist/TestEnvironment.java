/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.documentsui.dirlist.DirectoryFragment.TICK_VISIBLE_DURATION_MS;

import android.content.Context;
import android.database.Cursor;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.Model;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.TestEnv;

public final class TestEnvironment implements DocumentsAdapter.Environment {
    private final Context testContext;
    private final TestEnv mEnv;
    private final ActionHandler mActionHandler;
    private boolean mInSearchMode = false;
    private boolean mIsOnTrashPage = false;
    private boolean mIsOnline = true;
    private boolean mShouldDisplaySummary = false;

    public TestEnvironment(Context context, TestEnv env, ActionHandler actionHandler) {
        testContext = context;
        mEnv = env;
        mActionHandler = actionHandler;
    }

    @Override
    public Features getFeatures() {
        return mEnv.features;
    }

    @Override
    public ActionHandler getActionHandler() {
        return mActionHandler;
    }

    @Override
    public boolean isSelected(String id) {
        return false;
    }

    @Override
    public boolean isOnline() {
        return mIsOnline;
    }

    @Override
    public boolean isDocumentEnabled(DocumentInfo doc) {
        return true;
    }

    @Override
    public boolean isContentAvailable(DocumentInfo doc) {
        return true;
    }

    @Override
    public void initDocumentHolder(DocumentHolder holder) {
    }

    @Override
    public Model getModel() {
        return mEnv.model;
    }

    @Override
    public int getTickDuration() {
        return TICK_VISIBLE_DURATION_MS;
    }

    @Override
    public State getDisplayState() {
        return mEnv.state;
    }

    @Override
    public boolean isInSearchMode() {
        return mInSearchMode;
    }

    @Override
    public Context getContext() {
        return testContext;
    }

    @Override
    public int getColumnCount() {
        return 4;
    }

    @Override
    public void onBindDocumentHolder(DocumentHolder holder, Cursor cursor) {
    }

    @Override
    public boolean shouldDisplaySummary() {
        return mShouldDisplaySummary;
    }

    @Override
    public boolean isOnTrashPage() {
        return mIsOnTrashPage;
    }

    public void setInSearchMode(boolean inSearchMode) {
        mInSearchMode = inSearchMode;
    }

    public void setIsOnTrashPage(boolean isOnTrashPage) {
        mIsOnTrashPage = isOnTrashPage;
    }

    public void setIsOnline(boolean isOnline) {
        mIsOnline = isOnline;
    }

    public void setShouldDisplaySummary(boolean shouldDisplaySummary) {
        mShouldDisplaySummary = shouldDisplaySummary;
    }
}
