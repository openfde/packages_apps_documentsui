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

package com.android.documentsui;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;

import androidx.annotation.StringRes;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.ui.TestDialogController;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@LargeTest
public class PickActivityTest extends ActivityTestJunit4<PickActivity> {

    private static final String RESULT_EXTRA = "test_result_extra";
    private static final String RESULT_DATA = "123321";

    private TestDialogController mTestDialogs;
    private TestConfigStore mTestConfigStore;

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                docsHelper.createDocument(
                                        root, "text/plain", TestFilesRule.FILE_NAME_1);
                                docsHelper.createDocument(
                                        root, "image/png", TestFilesRule.FILE_NAME_2);
                            });

    @Override
    protected void launchActivity() {
        Intent getContentIntent = new Intent(context, PickActivity.class);
        getContentIntent.setAction(Intent.ACTION_GET_CONTENT);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        getContentIntent.setType("*/*");

        // Open picker in default initial directory.
        getContentIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootDir0.getUri());
        mActivityScenario = ActivityScenario.launchActivityForResult(getContentIntent);
    }

    @Before
    public void setUpTest() throws Exception {
        mTestDialogs = new TestDialogController();
        mTestConfigStore = new TestConfigStore();
        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
    }

    @Test
    public void testOnDocumentPicked() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        mActivityScenario.onActivity(
                pickActivity -> {
                    pickActivity.mState.configStore = mTestConfigStore;
                    if (isPrivateSpaceEnabled) {
                        mTestConfigStore.enablePrivateSpaceInPhotoPicker();
                        pickActivity.mState.canForwardToProfileIdMap.put(
                                TestProvidersAccess.USER_ID, true);
                    } else {
                        pickActivity.mState.canShareAcrossProfile = true;
                    }
                    pickActivity.onDocumentPicked(doc);
                });
        SystemClock.sleep(3000);

        Instrumentation.ActivityResult result = mActivityScenario.getResult();
        assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
    }

    @Test
    public void testOnDocumentPicked_otherUser() {
        if (VersionUtils.isAtLeastR()) {
            DocumentInfo doc = new DocumentInfo();
            doc.userId = TestProvidersAccess.OtherUser.USER_ID;
            doc.authority = "authority";
            doc.documentId = "documentId";

            mActivityScenario.onActivity(
                    pickActivity -> {
                        pickActivity.mState.configStore = mTestConfigStore;
                        if (isPrivateSpaceEnabled) {
                            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
                            pickActivity.mState.canForwardToProfileIdMap.put(
                                    TestProvidersAccess.USER_ID, true);
                            pickActivity.mState.canForwardToProfileIdMap.put(
                                    TestProvidersAccess.OtherUser.USER_ID, true);
                        } else {
                            pickActivity.mState.canShareAcrossProfile = true;
                        }
                        pickActivity.onDocumentPicked(doc);
                    });
            SystemClock.sleep(3000);

            Instrumentation.ActivityResult result = mActivityScenario.getResult();
            assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
            assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
        }
    }

    @Test
    public void testOnDocumentPicked_otherUserDoesNotReturn() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.OtherUser.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        mActivityScenario.onActivity(
                pickActivity -> {
                    pickActivity.mState.configStore = mTestConfigStore;
                    if (isPrivateSpaceEnabled) {
                        mTestConfigStore.enablePrivateSpaceInPhotoPicker();
                        pickActivity.mState.canForwardToProfileIdMap.put(
                                TestProvidersAccess.USER_ID, true);
                    } else {
                        pickActivity.mState.canShareAcrossProfile = false;
                    }
                    pickActivity.getInjector().dialogs = mTestDialogs;
                    pickActivity.onDocumentPicked(doc);
                });
        SystemClock.sleep(3000);

        mTestDialogs.assertActionNotAllowedShown();
        assertThat(mActivityScenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
    }

    @Test
    public void testOptionMenuWorksWhileOptionSelected() throws UiObjectNotFoundException {
        // Switch to list mode and select the test document.
        bots.main.switchToListMode();
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Open the overflow menu and assert that the Sort by menu option is there.
        bots.main.openOverflowMenu();
        bots.menu.hasMenuItem("Sort by...");
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testOptionMenuWorksWhileOptionSelected_M3Enabled()
            throws UiObjectNotFoundException {
        // Switch to list mode and select the test document.
        bots.main.switchToListMode();
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Open the overflow menu and assert that the expected menu options are there.
        bots.main.openOverflowMenu();
        bots.menu.hasMenuItem(context.getString(R.string.menu_rename));
        bots.menu.hasMenuItem(context.getString(R.string.menu_inspect));
        final @StringRes int zipMenuId =
                isZipNgFlagEnabled() ? R.string.menu_zip : R.string.menu_compress;
        bots.menu.hasMenuItem(context.getString(zipMenuId));
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_rename() throws UiObjectNotFoundException {
        // Right click FILE_NAME_1 to trigger rename.
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        bots.menu.clickMenuItem(context.getString(R.string.menu_rename));
        device.waitForIdle();

        // Input a new name.
        final String newName = "renamed inside picker";
        bots.main.setDialogText(newName);
        bots.keyboard.pressEnter();

        // Assert the old file is gone, the new file appears.
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        bots.directory.assertDocumentsPresent(newName);
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_getInfo() throws UiObjectNotFoundException {
        // Right click FILE_NAME_1 to trigger get info.
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        bots.menu.clickMenuItem(context.getString(R.string.menu_inspect));
        device.waitForIdle();

        // Assert inspector activity is shown.
        if (!isUsePeekPreviewFlagEnabled()) {
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(
                            InspectorActivity.class.getName(), null, false);
            monitor.waitForActivityWithTimeout(5000);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_delete() throws UiObjectNotFoundException {
        // Right click FILE_NAME_1 to trigger delete.
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        bots.menu.clickMenuItem(context.getString(R.string.menu_permanently_delete));
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        // Assert the file is gone.
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
    }

    @Test
    public void testContextMenu_zip() throws UiObjectNotFoundException {
        // We can't use EnableFlags here because: @EnableFlags(FLAG_USE_MATERIAL3) will override
        // the M3 flag to true thus the isZipNgFlagEnabled() is also true, but the override doesn't
        // affect the CompressJob service, which internally use the flag name to control the zipped
        // file name, which eventually results in: the ZipNg flag is ON in this test context, but
        // it's OFF in the CompressJob service.
        assumeTrue(
                "Skipping test: the use_material3 flag is OFF on the test device.",
                isUseMaterial3FlagEnabled());

        // Right click FILE_NAME_1 to trigger zip.
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        bots.menu.clickMenuItem(context.getString(R.string.menu_zip));
        device.waitForIdle();

        // Assert a zip file is created.
        bots.directory.assertDocumentsPresent(TestFilesRule.FILE_NAME_1);
        final String zipFileName = isZipNgFlagEnabled() ? "file1.zip" : "file1.log.zip";
        bots.directory.assertDocumentsPresent(zipFileName);
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ActionOpenDocument_SingleFile()
            throws UiObjectNotFoundException {
        // Close the existing activity first because we need a different intent.
        mActivityScenario.close();
        // Wait some time before launching the new activity to make it more stable.
        SystemClock.sleep(1000);
        Intent intentOpenDocument = new Intent(context, PickActivity.class);
        intentOpenDocument.setAction(Intent.ACTION_OPEN_DOCUMENT);
        intentOpenDocument.addCategory(Intent.CATEGORY_OPENABLE);
        intentOpenDocument.setType("*/*");
        intentOpenDocument.putExtra(
                DocumentsContract.EXTRA_INITIAL_URI, rootDir0.getUri());
        mActivityScenario = ActivityScenario.launchActivityForResult(intentOpenDocument);

        // There should be a Cancel (button2) and Select (button1) button.
        boolean showPickerCancelButton =
                context.getResources().getBoolean(R.bool.show_picker_cancel_button);
        if (showPickerCancelButton) {
            bots.picker.checkCancelButtonDisplayed();
            bots.picker.checkCancelButtonEnabled();
        }
        bots.picker.checkPickButtonDisplayed();
        // The Select button should be disabled since there are no selected files.

        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // The Select button should be enabled since there is a selected file.
        bots.picker.checkPickButtonEnabled();

        // Click the Select button to pick the selected file.
        bots.picker.clickPickButton();
        SystemClock.sleep(3000);

        // Check that the file was picked.
        Instrumentation.ActivityResult result = mActivityScenario.getResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData())
                .isEqualTo(mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_1));

        // Check that the activity is finishing.
        assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ActionGetContent_MultiFiles() throws Exception {
        // Close the existing activity first because we need a different intent.
        mActivityScenario.close();
        // Wait some time before launching the new activity to make it more stable.
        SystemClock.sleep(1000);
        Intent intent = new Intent(context, PickActivity.class);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootDir0.getUri());
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivityScenario = ActivityScenario.launchActivityForResult(intent);

        // There should be a Cancel (button2) and Select (button1) button.
        boolean showPickerCancelButton =
                context.getResources().getBoolean(R.bool.show_picker_cancel_button);
        if (showPickerCancelButton) {
            bots.picker.checkCancelButtonDisplayed();
            bots.picker.checkCancelButtonEnabled();
        }
        bots.picker.checkPickButtonDisplayed();
        // The Select button should be disabled since there are no selected files.
        bots.picker.checkPickButtonDisabled();

        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 2);

        // The Select button should be enabled since there are selected files.
        bots.picker.checkPickButtonEnabled();

        bots.directory.clearSelection();

        // The Select button should be disabled since there are no selected files.
        bots.picker.checkPickButtonDisabled();

        // Select the files again and click the Select button to pick the selected files.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 2);
        bots.picker.checkPickButtonEnabled();
        bots.picker.clickPickButton();
        SystemClock.sleep(3000);

        // Check that the files were picked.
        Instrumentation.ActivityResult result = mActivityScenario.getResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        ClipData clipData = result.getResultData().getClipData();
        assertNotNull(clipData);
        assertEquals(clipData.getItemCount(), 2);
        assertEquals(
                mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_1),
                clipData.getItemAt(0).getUri());
        assertEquals(
                mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_2),
                clipData.getItemAt(1).getUri());

        // Check that the activity is finishing.
        assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ClickCancel() throws UiObjectNotFoundException {
        assume().that(context.getResources().getBoolean(R.bool.show_picker_cancel_button)).isTrue();

        // There should be a Cancel (button2) and Select (button1) button.
        bots.picker.checkCancelButtonDisplayed();
        bots.picker.checkCancelButtonEnabled();
        bots.picker.checkPickButtonDisplayed();

        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Click Cancel.
        bots.picker.clickCancelButton();
        SystemClock.sleep(3000);

        // Check that the files weren't picked.
        Instrumentation.ActivityResult result = mActivityScenario.getResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_CANCELED);

        // Check that the activity is finishing.
        assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_FlagDisabled() throws UiObjectNotFoundException {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // The Cancel (button2) and Select (button1) buttons should not exist.
        bots.picker.checkCancelButtonDoesNotExist();
        bots.picker.checkPickButtonDoesNotExist();
    }
}
