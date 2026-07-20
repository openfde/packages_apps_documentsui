/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.CATEGORY_DEFAULT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.endsWith;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.action.ViewActions;
import androidx.test.filters.LargeTest;

import com.android.documentsui.picker.PickActivity;

import org.junit.Test;

import java.util.UUID;

@LargeTest
public class ActionCreateDocumentUiTest extends ActivityTestJunit4<PickActivity> {

    @Override
    protected void launchActivity() {
        final Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
        intent.addCategory(CATEGORY_DEFAULT);
        intent.addCategory(CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri(AUTHORITY_STORAGE, "primary"));

        mActivityScenario = ActivityScenario.launchActivityForResult(intent);
    }

    @Test
    public void testActionCreate_TextFile() throws Exception {
        final String fileName = UUID.randomUUID() + ".txt";

        // Do not use setDialogText() here because both search input and saver input are EditText.
        onView(allOf(withClassName(endsWith("EditText")), withId(android.R.id.title)))
                .perform(ViewActions.replaceText(fileName));
        device.waitForIdle();
        bots.picker.clickSaveButton();
        SystemClock.sleep(3000);

        final Instrumentation.ActivityResult activityResult = mActivityScenario.getResult();
        assertThat(activityResult.getResultCode()).isEqualTo(RESULT_OK);

        final Intent resultData = activityResult.getResultData();
        final Uri uri = resultData.getData();

        assertThat(uri.getAuthority()).isEqualTo(AUTHORITY_STORAGE);
        assertThat(uri.getPath()).contains(fileName);

        assertThat(resultData.getFlags()).isEqualTo(FLAG_GRANT_READ_URI_PERMISSION
                | FLAG_GRANT_WRITE_URI_PERMISSION
                | FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        final boolean deletedSuccessfully =
                DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        assertThat(deletedSuccessfully).isTrue();
    }
}