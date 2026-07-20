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

import static android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API;
import static android.provider.Flags.FLAG_ENABLE_SYNC_STATE;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.OUTLINE;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_OFF_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ENABLE_BUTTON;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ERROR_TITLE;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;
import static com.android.documentsui.testing.DrawableAsserts.assertDrawablesEqual;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.core.util.Preconditions;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.CrossProfileNoPermissionException;
import com.android.documentsui.CrossProfileQuietModeException;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestModel;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@SmallTest
@RunWith(Parameterized.class)
public final class MessageTest {

    private UserId mUserId = UserId.of(100);
    private Message mInflateMessage;
    private Message mHeaderMessage;
    private Context mContext;
    private Runnable mDefaultCallback = () -> {
    };
    private UserManager mUserManager;
    private DevicePolicyManager mDevicePolicyManager;
    private TestActionHandler mTestActionHandler;
    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private DocumentsAdapter.Environment mEnv;

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

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

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mUserManager = UserManagers.create();
        mTestActionHandler = new TestActionHandler();
        mDevicePolicyManager = mock(DevicePolicyManager.class);
        when(mContext.getSystemServiceName(UserManager.class)).thenReturn("mUserManager");
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
        mEnv = new TestEnvironment(mContext, TestEnv.create(), mTestActionHandler);
        mEnv.getDisplayState().action = State.ACTION_GET_CONTENT;

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (SdkLevel.isAtLeastV()) {
            UserProperties userProperties = new UserProperties.Builder()
                    .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                    .build();
            UserHandle userHandle = UserHandle.of(mUserId.getIdentifier());
            when(mUserManager.getUserProperties(userHandle)).thenReturn(userProperties);
        }
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            String personalLabel = mContext.getString(R.string.personal_tab);
            String workLabel = mContext.getString(R.string.work_tab);
            Map<UserId, String> userIdToLabelMap = new HashMap<>();
            userIdToLabelMap.put(TestProvidersAccess.USER_ID, personalLabel);
            userIdToLabelMap.put(mUserId, workLabel);
            mInflateMessage = new Message.InflateMessage(mEnv, mDefaultCallback,
                    TestProvidersAccess.USER_ID, mUserId, userIdToLabelMap, mUserManager,
                    mTestConfigStore);
        } else {
            mInflateMessage = new Message.InflateMessage(mEnv, mDefaultCallback, mTestConfigStore);
        }
        mHeaderMessage = new Message.HeaderMessage(mEnv, mDefaultCallback, mTestConfigStore);
    }

    @Test
    public void testInflateMessage_updateToCrossProfileNoPermission() {
        // Make sure this test is running on system user.
        assume().that(UserId.CURRENT_USER.isSystem()).isTrue();
        Preconditions.checkArgument(UserId.CURRENT_USER.isSystem());
        Model.Update error = new Model.Update(
                new CrossProfileNoPermissionException(),
                /* isRemoteActionsEnabled= */ true);
        if (SdkLevel.isAtLeastT()) {
            String title = mContext.getString(R.string.cant_select_work_files_error_title);
            String message = mContext.getString(R.string.cant_select_work_files_error_message);
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(CANT_SELECT_WORK_FILES_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(CANT_SELECT_WORK_FILES_MESSAGE), any()))
                    .thenReturn(message);
        }

        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);
        Log.d("DocsUiAdi", "title string in test = " + mInflateMessage.getTitleString());
        if (isPrivateSpaceEnabled) {
            String workLabel = mContext.getString(R.string.work_tab);
            String personalLabel = mContext.getString(R.string.personal_tab);
            assertThat(mInflateMessage.getTitleString())
                    .isEqualTo(
                            mContext.getString(R.string.cant_select_cross_profile_files_error_title,
                                    workLabel.toLowerCase(Locale.getDefault())));
            assertThat(mInflateMessage.getMessageString())
                    .isEqualTo(mContext.getString(
                            R.string.cant_select_cross_profile_files_error_message,
                            workLabel.toLowerCase(Locale.getDefault()),
                            personalLabel.toLowerCase(Locale.getDefault())));
        } else {
            assertThat(mInflateMessage.getTitleString())
                    .isEqualTo(mContext.getString(R.string.cant_select_work_files_error_title));
            assertThat(mInflateMessage.getMessageString())
                    .isEqualTo(mContext.getString(R.string.cant_select_work_files_error_message));
        }
        // No button for this error
        assertThat(mInflateMessage.getButtonString()).isNull();
    }

    @Test
    public void testInflateMessage_updateToCrossProfileQuietMode() {
        if (SdkLevel.isAtLeastV()) return;
        Model.Update error = new Model.Update(
                new CrossProfileQuietModeException(mUserId),
                /* isRemoteActionsEnabled= */ true);
        if (SdkLevel.isAtLeastT()) {
            String title = mContext.getString(R.string.quiet_mode_error_title);
            String text = mContext.getString(R.string.quiet_mode_button);
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ERROR_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ENABLE_BUTTON), any()))
                    .thenReturn(text);
        }

        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);
        assertThat(mInflateMessage.getTitleString())
                .isEqualTo(mContext.getString(R.string.quiet_mode_error_title));
        assertThat(mInflateMessage.getButtonString()).isEqualTo(
                mContext.getString(R.string.quiet_mode_button));
        assertThat(mInflateMessage.mCallback).isNotNull();
        mInflateMessage.mCallback.run();

        assertThat(mTestActionHandler.mRequestDisablingQuietModeHappened).isTrue();
    }

    @Test
    public void testInflateMessage_updateToCrossProfileQuietMode_PostV() {
        if (!SdkLevel.isAtLeastV()) return;
        Model.Update error = new Model.Update(
                new CrossProfileQuietModeException(mUserId),
                /* isRemoteActionsEnabled= */ true);

        DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                DevicePolicyResourcesManager.class);
        when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);

        if (isPrivateSpaceEnabled) {
            Drawable icon = mContext.getDrawable(R.drawable.work_off);
            when(devicePolicyResourcesManager.getDrawable(eq(WORK_PROFILE_OFF_ICON), eq(OUTLINE),
                    any()))
                    .thenReturn(icon);
        } else {
            String title = mContext.getString(R.string.quiet_mode_error_title);
            String text = mContext.getString(R.string.quiet_mode_button);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ERROR_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ENABLE_BUTTON), any()))
                    .thenReturn(text);
        }
        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);

        if (!isPrivateSpaceEnabled) {
            assert mInflateMessage.getTitleString() != null;
            assertThat(mInflateMessage.getTitleString())
                    .isEqualTo(mContext.getString(R.string.quiet_mode_error_title));
            assert mInflateMessage.getButtonString() != null;
            assertThat(mInflateMessage.getButtonString().toString()).isEqualTo(
                    mContext.getString(R.string.quiet_mode_button));
        }
        assertThat(mInflateMessage.mCallback).isNotNull();
        mInflateMessage.mCallback.run();

        assertThat(mTestActionHandler.mRequestDisablingQuietModeHappened).isTrue();
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testInflateMessage_noEmptyMessageWhileLoading() {
        // Set model to empty.
        ((TestModel) mEnv.getModel()).clearIds();
        // Make sure we have a root doc for title access.
        mEnv.getDisplayState().stack.changeRoot(TestProvidersAccess.HOME);
        // Turn off search mode.
        ((TestEnvironment) mEnv).setInSearchMode(false);

        // set model to loading state
        mEnv.getModel().setLoading(true);
        mInflateMessage.update(Model.Update.UPDATE);
        assertNull(mInflateMessage.getMessageString());
        assertNull(mInflateMessage.getIcon());

        // update model state to indicate loading has finished
        mEnv.getModel().setLoading(false);
        mInflateMessage.update(Model.Update.UPDATE);
        Drawable expectedDrawable = mContext.getDrawable(getRes(R.drawable.empty));
        assertDrawablesEqual(mInflateMessage.getIcon(), expectedDrawable);
        assertThat(mInflateMessage.getMessageString().toString())
                .isEqualTo(mContext.getString(R.string.empty));
    }

    @Test
    public void testInflateMessage_updateToEmptyMessage() {
        // Set model to empty.
        ((TestModel) mEnv.getModel()).clearIds();
        // Make sure we have a root doc for title access.
        mEnv.getDisplayState().stack.changeRoot(TestProvidersAccess.HOME);
        // Turn off search mode.
        ((TestEnvironment) mEnv).setInSearchMode(false);

        mInflateMessage.update(Model.Update.UPDATE);

        Drawable expectedDrawable = mContext.getDrawable(getRes(R.drawable.empty));
        assertDrawablesEqual(mInflateMessage.getIcon(), expectedDrawable);
        assertThat(mInflateMessage.getMessageString().toString())
                .isEqualTo(mContext.getString(R.string.empty));
    }

    @Test
    public void testInflateMessage_updateToEmptyMessage_InSearch() {
        // Set model to empty.
        ((TestModel) mEnv.getModel()).clearIds();
        // Make sure we have a root doc for title access.
        mEnv.getDisplayState().stack.changeRoot(TestProvidersAccess.HOME);
        // Turn on search mode.
        ((TestEnvironment) mEnv).setInSearchMode(true);

        mInflateMessage.update(Model.Update.UPDATE);

        final Drawable expectedDrawable;
        if (isUseMaterial3FlagEnabled()) {
            expectedDrawable = mContext.getDrawable(R.drawable.empty_search);
        } else {
            expectedDrawable = mContext.getDrawable(R.drawable.empty);
        }
        assertDrawablesEqual(mInflateMessage.getIcon(), expectedDrawable);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENABLE_DOCUMENTS_TRASH_API})
    @EnableFlags({Flags.FLAG_USE_MATERIAL3, Flags.FLAG_ENABLE_TRASH_FLOW_RO})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    public void testInflateMessage_updateToEmptyMessage_InTrashPage() {
        // Set model to empty.
        ((TestModel) mEnv.getModel()).clearIds();
        // Set is on trash page.
        ((TestEnvironment) mEnv).setIsOnTrashPage(true);

        mInflateMessage.update(Model.Update.UPDATE);

        final Drawable expectedDrawable;
        if (isUseMaterial3FlagEnabled()) {
            expectedDrawable = mContext.getDrawable(R.drawable.ic_empty_trash);
        } else {
            expectedDrawable = mContext.getDrawable(R.drawable.empty);
        }
        assertDrawablesEqual(mInflateMessage.getIcon(), expectedDrawable);
        Assert.assertNotNull(mInflateMessage.getMessageString());
        assertThat(mInflateMessage.getMessageString().toString())
                .isEqualTo(mContext.getString(R.string.trash_page_empty_title));
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testHeaderMessage_offlineAndLimitedWhenOffline_showsOfflineBanner() {
        // Set offline.
        ((TestEnvironment) mEnv).setIsOnline(false);
        ((TestModel) mEnv.getModel()).setHasLimitedFunctionalityWhenOffline(true);

        mHeaderMessage.update(new Model.Update(null, false));

        assertTrue(mHeaderMessage.shouldShow());
        assertThat(mHeaderMessage.getMessageString().toString())
                .isEqualTo(mContext.getString(getRes(R.string.you_are_offline_banner_message)));
        assertThat(mHeaderMessage.getButtonString().toString())
                .isEqualTo(mContext.getString(getRes(R.string.button_dismiss)));
        if (isUseMaterial3FlagEnabled()) {
            assertDrawablesEqual(
                    mHeaderMessage.getIcon(), mContext.getDrawable(R.drawable.ic_wifi_off_m3));
        } else {
            assertNull(mHeaderMessage.getIcon());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHeaderMessage_flagDisabled_doesNotShowOfflineBanner() {
        // Set offline.
        ((TestEnvironment) mEnv).setIsOnline(false);
        ((TestModel) mEnv.getModel()).setHasLimitedFunctionalityWhenOffline(true);

        mHeaderMessage.update(new Model.Update(null, false));

        assertFalse(mHeaderMessage.shouldShow());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testHeaderMessage_onlineAndLimitedWhenOffline_doesNotShowBanner() {
        // Set online.
        ((TestEnvironment) mEnv).setIsOnline(true);
        ((TestModel) mEnv.getModel()).setHasLimitedFunctionalityWhenOffline(true);

        mHeaderMessage.update(new Model.Update(null, false));

        assertFalse(mHeaderMessage.shouldShow());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled({FLAG_ENABLE_SYNC_STATE})
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testHeaderMessage_offlineAndNotLimitedWhenOffline_doesNotShowBanner() {
        // Set offline.
        ((TestEnvironment) mEnv).setIsOnline(false);
        ((TestModel) mEnv.getModel()).setHasLimitedFunctionalityWhenOffline(false);

        mHeaderMessage.update(new Model.Update(null, false));

        assertFalse(mHeaderMessage.shouldShow());
    }
}
