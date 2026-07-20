/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO;

import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.bots.EspressoBotsKt;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.services.TestNotificationService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class test the below points
 *
 * <p>- Delete large number of files
 */
@LargeTest
public class FileDeleteUiTest extends ActivityTestJunit4<FilesActivity> {
    private static final String TAG = "FileDeleteUiTest";

    private static final int STUB_FILE_COUNT = 50;

    private static final int WAIT_TIME_SECONDS = 30;

    private final BroadcastReceiver mReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (TestNotificationService.ACTION_PONG.equals(action)) {
                        sRendezvousCountDownLatch.countDown();
                    } else if (TestNotificationService.ACTION_RECENT_NOTIFICATIONS.equals(action)) {
                        mRecentNotificationsAsText =
                                intent.getStringExtra(
                                        TestNotificationService.EXTRA_RECENT_NOTIFICATIONS_AS_TEXT);
                    }
                }
            };

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule().createTestFiles(this::initTestFiles);

    private static CountDownLatch sRendezvousCountDownLatch = new CountDownLatch(1);

    private String mRecentNotificationsAsText;

    @Before
    public void setUpTest() throws Exception {
        setNotificationAccess(true);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TestNotificationService.ACTION_PONG);
        filter.addAction(TestNotificationService.ACTION_RECENT_NOTIFICATIONS);
        context.registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);
        if (!TestNotificationService.rendezvous(context, sRendezvousCountDownLatch)) {
            fail("TestNotificationService.rendezvous failed");
        }
        context.sendBroadcast(new Intent(
                TestNotificationService.ACTION_CHANGE_EXECUTION_MODE));
    }

    @After
    public void tearDownTest() {
        try {
            context.unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Log.d(TAG, "Error unregistering the receiver, it might not be registered.", e);
        }
        setNotificationAccess(false);
    }

    private void initTestFiles(DocumentsProviderHelper docsHelper) throws Exception {
        // Set a flag to prevent many refreshes.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        docsHelper.configure(null, bundle);
        final RootInfo root = docsHelper.getRoot(StubProvider.ROOT_0_ID);
        final ThreadPoolExecutor exec = new ThreadPoolExecutor(
                5, 5, 1000L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(100, true));

        // Test cannot succeed if any error occurs during file creation.
        // Track the first error encountered to report it if setup fails.
        final java.util.concurrent.atomic.AtomicReference<Exception> creationError =
                new java.util.concurrent.atomic.AtomicReference<>();

        for (int i = 0; i < STUB_FILE_COUNT; i++) {
            final String fileName = "file" + String.format("%04d", i) + ".log";
            if (exec.getQueue().size() >= 80) {
                SystemClock.sleep(50);
            }
            exec.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Uri uri = docsHelper.createDocument(root, "text/plain", fileName);
                                docsHelper.writeDocument(uri, new byte[1]);
                            } catch (Exception e) {
                                creationError.set(e);
                            }
                        }
                    });
        }
        exec.shutdown();

        // Ensure all background creation tasks have completed without error.
        if (!exec.awaitTermination(WAIT_TIME_SECONDS, TimeUnit.SECONDS)) {
            fail("Timed out waiting for file creation tasks to complete");
        }

        if (creationError.get() != null) {
            fail("Failed to initialise all test files. Failure: " + creationError.get());
        }
    }

    @HugeLongTest
    @Test
    @DisableFlags(FLAG_HOME_SCREEN_FILES_RO)
    public void testDeleteAllDocument() throws Exception {
        device.waitForIdle();

        waitUntilFileCountIs(STUB_FILE_COUNT);

        bots.main.clickToolbarOverflowItem(
                context.getResources().getString(R.string.menu_select_all));
        device.waitForIdle();

        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        waitUntilFileCountIs(0);
    }

    private void waitUntilFileCountIs(int expected) throws Exception {
        // Assert that mDocsHelper.listChildren(etc).size() has hit the expected count. File
        // creation and deletion happens asynchronously from the UI thread so we'll poll every
        // second (up to WAIT_TIME_SECONDS iterations), failing early if no progress was made after
        // 10 iterations.
        int count = 0;
        int prevCount = 0;
        int numIterationsNoProgressMade = 0;
        for (int i = 0; i < WAIT_TIME_SECONDS; i++) {
            count = mDocsHelper.listChildren(rootDir0.documentId, STUB_FILE_COUNT).size();
            if (count == expected) {
                return;
            } else if ((i == 0) || (prevCount != count)) {
                prevCount = count;
                numIterationsNoProgressMade = 0;
            } else if (++numIterationsNoProgressMade == 10) {
                break;
            }
            SystemClock.sleep(1000);
        }

        // Recent notifications, whether DocumentsUI progress notifications or otherwise, as
        // recorded by the TestNotificationService, might give some insight as to whether any
        // delete-the-files progress is being made (albeit slowly) or whether the overall
        // multiple-file-delete operation is stuck.
        Log.e(TAG, "Recent notifications: " + mRecentNotificationsAsText);

        fail("waitUntilFileCountIs(" + expected + ") failed, count=" + count);
    }
}
