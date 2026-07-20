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

import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;
import static com.android.documentsui.base.Providers.ROOT_ID_DOWNLOADS;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO;
import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;

import static com.google.common.collect.Lists.newArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.net.Uri;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract.Document;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.PollingCheck;
import com.android.documentsui.DocumentsProviderHelper;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.DocsProviders;

import org.junit.Rule;
import org.junit.Test;

@MediumTest
public class CopyJobTest extends AbstractCopyJobTest<CopyJob> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    public CopyJobTest() {
        super(OPERATION_COPY);
    }

    @Test
    public void testCopyFiles() throws Exception {
        runCopyFilesTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyFilesWithProgress() throws Exception {
        runCopyFilesTestWithJobProgress();
    }

    @Test
    public void testCopyVirtualTypedFile() throws Exception {
        runCopyVirtualTypedFileTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyVirtualTypedFileWithJobProgress() throws Exception {
        runCopyVirtualTypedFileTestWithJobProgress();
    }

    @Test
    public void testCopyVirtualFile_noExtensionForMimeType() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        // Create a virtual file with a streamable mime type that has no known extension.
        final String displayName = "no-extension-file";
        final String mimeTypeWithNoExtension = "application/x-funky-town";
        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", displayName,
                Document.FLAG_VIRTUAL_DOCUMENT, mimeTypeWithNoExtension);

        createJob(newArrayList(testFile)).run();
        waitForJobFinished();

        mDocs.assertChildCount(mDestRoot, 1);
        // The display name should be unchanged because there's no extension to add.
        mDocs.assertHasFile(mDestRoot, displayName);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyVirtualFile_noExtensionForMimeType_withJobProgress() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        // Create a virtual file with a streamable mime type that has no known extension.
        final String displayName = "no-extension-file";
        final String mimeTypeWithNoExtension = "application/x-funky-town";
        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", displayName,
                Document.FLAG_VIRTUAL_DOCUMENT, mimeTypeWithNoExtension);

        CopyJob job = createJob(newArrayList(testFile));
        job.run();
        waitForJobFinished();

        mDocs.assertChildCount(mDestRoot, 1);
        // The display name should be unchanged because there's no extension to add.
        mDocs.assertHasFile(mDestRoot, displayName);

        JobProgress progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_COPY, progress.operationType);
        assertFalse(progress.hasFailures);
    }

    @Test
    public void testCopyVirtualNonTypedFile() throws Exception {
        runCopyVirtualNonTypedFileTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyVirtualNonTypedFileWithProgress() throws Exception {
        runCopyVirtualNonTypedFileTestWithJobProgress();
    }

    @Test
    public void testCopy_BackendSideVirtualTypedFile_Fallback() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        createJob(newArrayList(testFile)).run();

        waitForJobFinished();
        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "tokyo.sth.pdf");  // Copy should convert file to PDF.
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyWithJobProgress_BackendSideVirtualTypedFile_Fallback() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        CopyJob job = createJob(newArrayList(testFile));

        JobProgress progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals("tokyo.sth", progress.filename);
        assertEquals(1, progress.numFiles);
        assertFalse(progress.hasFailures);

        job.run();
        waitForJobFinished();
        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "tokyo.sth.pdf");  // Copy should convert file to PDF.

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_COPY, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("tokyo.sth", progress.filename);
        assertEquals(1, progress.numFiles);
        assertEquals(-1, progress.currentBytes);
        assertEquals(-1, progress.requiredBytes);
    }

    @Test
    public void testCopyEmptyDir() throws Exception {
        runCopyEmptyDirTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyEmptyDirWithJobProgress() throws Exception {
        runCopyEmptyDirTestWithJobProgress();
    }

    @Test
    public void testCopyDirRecursively() throws Exception {
        runCopyDirRecursivelyTest();
    }

    // This test sometimes takes >1 minute to run.
    @LargeTest
    @Test
    public void testCopyDirRecursively_loadingInFirstCursor() throws Exception {
        mDocs.setLoadingDuration(500);
        testCopyDirRecursively();
    }

    @Test
    public void testNoCopyDirToSelf() throws Exception {
        runNoCopyDirToSelfTest();
    }

    @Test
    public void testNoCopyDirToDescendent() throws Exception {
        runNoCopyDirToDescendentTest();
    }

    @Test
    public void testCopyFileWithReadErrors() throws Exception {
        runCopyFileWithReadErrorsTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyFileWithFileNotFound() throws Exception {
        runCopyFileWithFileNotFoundTest();
    }

    @Test
    public void testCopyProgressWithFileCount() throws Exception {
        runCopyProgressForFileCountTest();
    }

    @Test
    public void testCopyProgressWithByteCount() throws Exception {
        runCopyProgressForByteCountTest();
    }

    @Test
    public void testRecursiveCopy_RealStorage() throws Exception {
        DocumentsProviderHelper storageHelper =
                DocumentsProviderHelper.setupStorageAuthorityDocsHelper(mContext);
        DocumentsProviderHelper downloadsHelper =
                DocumentsProviderHelper.setupDownloadsAuthorityDocsHelper(mContext);

        RootInfo storageRoot = storageHelper.getRoot(ROOT_ID_DEVICE);
        DocumentInfo downloadDir = storageHelper.findDocument(storageRoot.documentId, "Download");

        String subfolderName = "RecursiveCopyTest_" + System.currentTimeMillis();
        Uri subfolderUri = storageHelper.createFolder(downloadDir.documentId, subfolderName);
        try {
            final String finalSubfolderName = subfolderName;
            PollingCheck.check(
                    "Subfolder not found in DownloadsProvider.",
                    5000,
                    () -> {
                        try {
                            return downloadsHelper.findDocument(
                                            ROOT_ID_DOWNLOADS, finalSubfolderName)
                                    != null;
                        } catch (Exception e) {
                            return false;
                        }
                    });

            DocumentInfo subfolderInDownloads =
                    downloadsHelper.findDocument(ROOT_ID_DOWNLOADS, subfolderName);

            RootInfo downloadsRoot = downloadsHelper.getRoot(ROOT_ID_DOWNLOADS);
            DocumentStack stack = new DocumentStack(downloadsRoot, subfolderInDownloads);
            UrisSupplier srcs =
                    DocsProviders.createDocsProvider(newArrayList(downloadDir.derivedUri));

            FileOperation operation =
                    new FileOperation.Builder()
                            .withOpType(OPERATION_COPY)
                            .withSrcs(srcs)
                            .withDestination(stack)
                            .build();

            CopyJob job = createJob(operation);
            job.run();
            waitForJobFinished();

            mJobListener.assertFailed();
            mJobListener.assertFilesFailed(newArrayList("Download"));
        } finally {
            storageHelper.deleteDocument(subfolderUri);
        }

        storageHelper.cleanUp();
        downloadsHelper.cleanUp();
    }
}
