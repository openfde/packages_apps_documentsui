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

package com.android.documentsui.files;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.documentsui.R;

import java.util.List;

/**
 * Provides FilesActivity task grouping support. This allows multiple FilesActivities to be
 * launched (a behavior imparted by way of {@code documentLaunchMode="intoExisting"} and
 * our use of pseudo document {@link Uri}s. This also lets us move an existing task
 * to the foreground when a suitable task exists.
 *
 * Requires that {@code documentLaunchMode="intoExisting"} be set on target activity.
 *
 */
public class LauncherActivity extends Activity {

    public static final String TASK_LABEL_RES = "com.android.documentsui.taskLabel";
    public static final String TASK_ICON_RES = "com.android.documentsui.taskIcon";

    private static final String LAUNCH_CONTROL_AUTHORITY = "com.android.documentsui.launchControl";
    private static final String TAG = "LauncherActivity";

    /**
     * OpenFDE issue 496 diagnostics. This tag is deliberately unconditional (not gated on
     * the documentsui DEBUG flag) so that a user build can be checked with
     * {@code adb logcat -s OpenFdeFix496}. The marker line proves that this patch is present
     * in the running build; the remaining lines trace task reuse.
     */
    public static final String FIX496_TAG = "OpenFdeFix496";
    private static final String FIX496_MARKER =
            "issue496-fix-applied documentLaunchMode=intoExisting"
                    + " restoreTask=MULTIPLE_TASK-stripped";

    // Array of boolean extras that should be copied when creating new launch intents.
    // Missing intents will be ignored.
    private static final String[] PERSISTENT_BOOLEAN_EXTRAS = {
        DocumentsContract.EXTRA_SHOW_ADVANCED
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(FIX496_TAG, FIX496_MARKER
                + " LauncherActivity.onCreate taskId=" + getTaskId()
                + " savedInstanceState=" + (savedInstanceState != null)
                + " isChangingConfigurations=" + isChangingConfigurations()
                + " data=" + getIntent().getData()
                + " flags=0x" + Integer.toHexString(getIntent().getFlags()));

        launch();

        finish();

        Log.i(FIX496_TAG, "LauncherActivity.finish() called taskId=" + getTaskId());
    }

    private void launch() {
        ActivityManager activities = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        Intent intent = findTask(activities);
        Log.i(FIX496_TAG, "launch(): existing launch task=" + (intent != null)
                + " data=" + (intent == null ? null : intent.getData()));
        if (intent != null) {
            if (restoreTask(intent)) {
                return;
            } else {
                // We failed to restore the task. It may happen when system was just updated and we
                // moved the location of the targeted activity. Chances is that the rest of tasks
                // can't be restored either, so clean those tasks and start a new one.
                clearTask(activities);
            }
        }

        startTask();
    }

    private @Nullable Intent findTask(ActivityManager activities) {
        List<AppTask> tasks = activities.getAppTasks();
        Log.i(FIX496_TAG, "findTask(): appTasks=" + tasks.size());
        for (AppTask task : tasks) {
            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
            Intent intent = info.baseIntent;
            Uri uri = intent.getData();
            boolean launchUri = isLaunchUri(uri);
            Log.i(FIX496_TAG, "findTask(): candidate taskId=" + info.taskId
                    + " launchUri=" + launchUri
                    + " data=" + uri
                    + " flags=0x" + Integer.toHexString(intent.getFlags()));
            if (launchUri) {
                Log.i(FIX496_TAG, "findTask(): selected taskId=" + info.taskId);
                return intent;
            }
        }
        return null;
    }

    private void startTask() {
        Intent intent = createLaunchIntent(this);

        intent.putExtra(TASK_LABEL_RES, getRes(R.string.launcher_label));
        intent.putExtra(TASK_ICON_RES, getRes(R.drawable.launcher_icon));

        // Forward any flags from the original intent.
        intent.setFlags(getIntent().getFlags());
        if (DEBUG) {
            Log.d(TAG, "Starting new task > " + intent.getData());
        }
        Log.i(FIX496_TAG, "startTask(): new task data=" + intent.getData()
                + " flags=0x" + Integer.toHexString(intent.getFlags()));
        startActivity(intent);
    }

    private boolean restoreTask(Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "Restoring existing task > " + intent.getData());
        }
        try {
            // The base intent persisted with an existing task can still carry
            // FLAG_ACTIVITY_MULTIPLE_TASK, e.g. when the task was created while
            // documentLaunchMode was "always". Replaying it verbatim makes
            // ActivityStarter#resolveReusableTask skip the existing task and start
            // another FilesActivity task instead. Drop the flag so that the
            // intoExisting deduplication also works for already persisted tasks.
            intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            // TODO: This doesn't appear to restore a task once it has stopped running.
            Log.i(FIX496_TAG, "restoreTask(): startActivity data=" + intent.getData()
                    + " flags=0x" + Integer.toHexString(intent.getFlags()));
            startActivity(intent);

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore task > " + intent.getData() +
                    ". Clear all existing tasks and start a new one.", e);
        }

        return false;
    }

    private void clearTask(ActivityManager activities) {
        List<AppTask> tasks = activities.getAppTasks();
        for (AppTask task : tasks) {
            task.finishAndRemoveTask();
        }
    }

    public static final Intent createLaunchIntent(Activity activity) {
        Intent intent = new Intent(activity, FilesActivity.class);
        intent.setData(buildLaunchUri());

        // Relay any config overrides bits present in the original intent.
        Intent original = activity.getIntent();
        if (original != null) {
            copyExtras(original, intent);
            if (original.hasExtra(Intent.EXTRA_TITLE)) {
                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        original.getStringExtra(Intent.EXTRA_TITLE));
            }
        }
        return intent;
    }

    private static void copyExtras(Intent src, Intent dest) {
        for (String extra : PERSISTENT_BOOLEAN_EXTRAS) {
            if (src.hasExtra(extra)) {
                dest.putExtra(extra, src.getBooleanExtra(extra, false));
            }
        }
    }

    private static Uri buildLaunchUri() {
        return new Uri.Builder()
                .authority(LAUNCH_CONTROL_AUTHORITY)
                .fragment(String.valueOf(System.currentTimeMillis()))
                .build();
    }

    public static boolean isLaunchUri(@Nullable Uri uri) {
        boolean result = uri != null && LAUNCH_CONTROL_AUTHORITY.equals(uri.getAuthority());
        return result;
    }
}
