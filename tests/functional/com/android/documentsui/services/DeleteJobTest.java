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

package com.android.documentsui.services;

import static android.app.Notification.CATEGORY_ERROR;
import static android.app.Notification.CATEGORY_PROGRESS;
import static android.app.Notification.EXTRA_PROGRESS_INDETERMINATE;
import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TITLE;

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.services.Job.STATE_COMPLETED;
import static com.android.documentsui.services.Job.STATE_CREATED;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;

import androidx.test.filters.MediumTest;

import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@MediumTest
public class DeleteJobTest extends AbstractJobTest<DeleteJobKt> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3})
    public void failsOnInvalidParent() throws Exception {
        // Create a source file to be moved.
        Uri testFile = mDocs.createDocument(mSrcRoot, "text/plain", "test.txt");
        mDocs.writeDocument(testFile, HAM_BYTES);

        // A URI that is guaranteed to fail when resolving.
        Uri invalidParentUri = Uri.parse("content://i.do.not.exist/doc/ghost");

        // Create and run the job with the invalid source parent.
        final DeleteJobKt job = createJob(newArrayList(testFile), invalidParentUri);

        {
            final Notification notification = job.getSetupNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_PROGRESS);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Deleting files");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isEqualTo("Preparing...");
            assertThat(extras.getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isTrue();
        }

        job.run();

        {
            final Notification notification = job.getProgressNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_PROGRESS);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Deleting files");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isNull();
            assertThat(extras.getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isFalse();
        }

        mJobListener.assertFailed();
        mJobListener.assertFailureCount(1);

        {
            final Notification notification = job.getFailureNotification();
            assertThat(notification.category).isEqualTo(CATEGORY_ERROR);
            final Bundle extras = notification.extras;
            assertThat(extras.getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t delete 1 file");
            assertThat(extras.getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details");
        }

        // The job should fail, so no files should be deleted.
        // Verify the source file still exists.
        mDocs.assertChildCount(mSrcRoot, 1);
        mDocs.assertHasFile(mSrcRoot, "test.txt");
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void deleteOneFile() throws Exception {
        final Uri uri = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(uri, HAM_BYTES);

        final DeleteJobKt job =
                createJob(
                        newArrayList(uri),
                        DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId));

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_CREATED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.filename).isEqualTo("test1.txt");
            assertThat(progress.numFiles).isEqualTo(1);
        }

        job.run();
        mJobListener.waitForFinished();

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_COMPLETED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.filename).isEqualTo("test1.txt");
            assertThat(progress.numFiles).isEqualTo(1);
        }

        // All the files should have been deleted.
        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void deleteTwoFiles() throws Exception {
        final Uri uri1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(uri1, HAM_BYTES);

        final Uri uri2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(uri2, FRUITY_BYTES);

        final DeleteJobKt job =
                createJob(
                        newArrayList(uri1, uri2),
                        DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId));

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_CREATED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.numFiles).isEqualTo(2);
        }

        job.run();
        mJobListener.waitForFinished();

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_COMPLETED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.numFiles).isEqualTo(2);
        }

        // All the files should have been deleted.
        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void deleteTwoFilesWithoutParent() throws Exception {
        final Uri uri1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(uri1, HAM_BYTES);

        final Uri uri2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(uri2, FRUITY_BYTES);

        final DeleteJobKt job = createJob(newArrayList(uri1, uri2), null);

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_CREATED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.numFiles).isEqualTo(2);
        }

        job.run();
        mJobListener.waitForFinished();

        {
            final JobProgress progress = job.getJobProgress();
            assertThat(progress.operationType).isEqualTo(OPERATION_DELETE);
            assertThat(progress.id).isEqualTo(job.id);
            assertThat(progress.state).isEqualTo(STATE_COMPLETED);
            assertThat(progress.hasFailures).isFalse();
            assertThat(progress.numFiles).isEqualTo(2);
        }

        // All the files should have been deleted.
        mDocs.assertChildCount(mSrcRoot, 0);
    }

    /** Creates a job with a stack consisting of the default source directory. */
    private DeleteJobKt createJob(List<Uri> srcs, Uri srcParent) throws Exception {
        final Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        return createJob(OPERATION_DELETE, srcs, srcParent, stack);
    }
}
