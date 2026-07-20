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

package com.android.documentsui.services;

import static android.app.Notification.CATEGORY_ERROR;
import static android.app.Notification.CATEGORY_PROGRESS;
import static android.app.Notification.EXTRA_PROGRESS_INDETERMINATE;
import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TITLE;

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO;
import static com.android.documentsui.services.FileOperationService.OPERATION_MOVE;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract.Document;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.Rule;
import org.junit.Test;

@MediumTest
public class MoveJobTest extends AbstractCopyJobTest<MoveJob> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    public MoveJobTest() {
        super(OPERATION_MOVE);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3})
    public void failsOnInvalidParent() throws Exception {
        // Create a source file to be moved.
        Uri testFile = mDocs.createDocument(mSrcRoot, "text/plain", "test.txt");
        mDocs.writeDocument(testFile, HAM_BYTES);

        // A URI that is guaranteed to fail when resolving.
        Uri invalidParentUri = Uri.parse("content://i.do.not.exist/doc/ghost");

        // Create and run the job with the invalid source parent.
        final MoveJob job = createJob(newArrayList(testFile), invalidParentUri);

        {
            final Notification notification = job.getSetupNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_PROGRESS);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Moving files");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isEqualTo("Preparing...");
            assertThat(extras.getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isTrue();
        }

        job.run();

        {
            final Notification notification = job.getProgressNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_PROGRESS);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Moving files");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isNull();
            assertThat(extras.getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isFalse();
        }

        mJobListener.assertFailed();
        mJobListener.assertFailureCount(1);

        {
            final Notification notification = job.getFailureNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_ERROR);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t move 1 file");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details");
        }

        // The job should fail during setUp, so no files should be moved or deleted.
        // Verify the source file still exists.
        mDocs.assertChildCount(mSrcRoot, 1);
        mDocs.assertHasFile(mSrcRoot, "test.txt");

        // Verify the destination is still empty.
        mDocs.assertChildCount(mDestRoot, 0);
    }

    @Test
    public void testMoveFiles() throws Exception {
        runCopyFilesTest();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testMoveFilesWithJobProgress() throws Exception {
        runCopyFilesTestWithJobProgress();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    public void testMoveFiles_NoSrcParent() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2), null).run();
        waitForJobFinished();

        mDocs.assertChildCount(mDestRoot, 2);
        mDocs.assertHasFile(mDestRoot, "test1.txt");
        mDocs.assertHasFile(mDestRoot, "test2.txt");
        mDocs.assertFileContents(mDestRoot.documentId, "test1.txt", HAM_BYTES);
        mDocs.assertFileContents(mDestRoot.documentId, "test2.txt", FRUITY_BYTES);
    }

    @Test
    public void testMoveVirtualTypedFile() throws Exception {
        mDocs.createFolder(mSrcRoot, "hello");
        Uri testFile = mDocs.createVirtualFile(
                mSrcRoot, "/hello/virtual.sth", "virtual/mime-type",
                FRUITY_BYTES, "application/pdf", "text/html");
        createJob(newArrayList(testFile)).run();

        waitForJobFinished();

        // Should have failed, source not deleted. Moving by bytes for virtual files
        // is not supported.
        mDocs.assertChildCount(mDestRoot, 0);
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    public void testMoveVirtualNonTypedFile() throws Exception {
        runCopyVirtualNonTypedFileTest();

        // Should have failed, source not deleted.
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testMoveVirtualNonTypedFileWithJobProgress() throws Exception {
        runCopyVirtualNonTypedFileTestWithJobProgress();

        // Should have failed, source not deleted.
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    public void testMove_BackendSideVirtualTypedFile_Fallback() throws Exception {
        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        createJob(newArrayList(testFile)).run();
        waitForJobFinished();

        // Should have failed, source not deleted. Moving by bytes for virtual files
        // is not supported.
        mDocs.assertChildCount(mDestRoot, 0);
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    public void testMoveEmptyDir() throws Exception {
        runCopyEmptyDirTest();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testMoveEmptyDirWithJobProgress() throws Exception {
        runCopyEmptyDirTestWithJobProgress();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    public void testMoveDirRecursively() throws Exception {
        runCopyDirRecursivelyTest();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @LargeTest
    @Test
    public void testMoveDirRecursively_loadingInFirstCursor() throws Exception {
        mDocs.setLoadingDuration(500);
        testMoveDirRecursively();
    }

    @Test
    public void testNoMoveDirToSelf() throws Exception {
        runNoCopyDirToSelfTest();

        // should have failed, source not deleted
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    public void testNoMoveDirToDescendent() throws Exception {
        runNoCopyDirToDescendentTest();

        // should have failed, source not deleted
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    public void testMoveFileWithReadErrors() throws Exception {
        runCopyFileWithReadErrorsTest();

        // should have failed, source not deleted
        mDocs.assertChildCount(mSrcRoot, 1);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testMoveFileWithFileNotFound() throws Exception {
        runCopyFileWithFileNotFoundTest();
    }

    // TODO: Add test cases for moving when multi-parented.
}
