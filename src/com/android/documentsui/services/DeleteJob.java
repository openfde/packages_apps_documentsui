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

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.redact;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Notification;
import android.app.Notification.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.UrisSupplier;

import javax.annotation.Nullable;

public class DeleteJob extends ResolvedResourcesJob {

    private static final String TAG = "DeleteJob";

    private final Uri mParentUri;

    private volatile int mDocsProcessed = 0;

    /**
     * Moves files to a destination identified by {@code destination}.
     * Performs most work by delegating to CopyJob, then deleting
     * a file after it has been copied.
     *
     * @see @link {@link Job} constructor for most param descriptions.
     */
    DeleteJob(Context service, Listener listener, String id, DocumentStack stack,
            UrisSupplier srcs, @Nullable Uri srcParent, Features features) {
        super(service, listener, id, OPERATION_DELETE, stack, srcs, features);
        mParentUri = srcParent;
    }

    @Override
    Builder createProgressBuilder() {
        return super.createProgressBuilder(
                service.getString(getRes(R.string.delete_notification_title)),
                getRes(R.drawable.ic_menu_delete),
                service.getString(android.R.string.cancel),
                getRes(R.drawable.ic_cab_cancel));
    }

    @Override
    public Notification getSetupNotification() {
        return getSetupNotification(service.getString(getRes(R.string.delete_preparing)));
    }

    @Override
    public Notification getProgressNotification() {
        mProgressBuilder.setProgress(mResourceUris.getItemCount(), mDocsProcessed, false);
        String format = service.getString(getRes(R.string.delete_progress));
        mProgressBuilder.setSubText(
                String.format(format, mDocsProcessed, mResourceUris.getItemCount()));

        mProgressBuilder.setContentText(null);

        return mProgressBuilder.build();
    }

    @Override
    public Notification getFailureNotification() {
        return getFailureNotification(
                getFailureContentTitle(
                        getRes(
                                isUseMaterial3FlagEnabled()
                                        ? R.string.delete_error_2
                                        : R.string.delete_error_notification_title)),
                getRes(R.drawable.ic_menu_delete));
    }

    @Override
    JobProgress getJobProgress() {
        return new JobProgress(
                id,
                operationType,
                getState(),
                getFilename(),
                mResourceUris.getItemCount(),
                hasFailures(),
                failedDocs,
                failedUris,
                failedPaths);
    }

    @Override
    void start() {
        DocumentInfo parentDoc = null;
        if (mParentUri != null) {
            try {
                ContentResolver resolver = appContext.getContentResolver();
                parentDoc = DocumentInfo.fromUri(resolver, mParentUri, UserId.DEFAULT_USER);
            } catch (Exception e) {
                Log.e(TAG, "Cannot resolve parent URI " + redact(mParentUri), e);
                onFileFailed(mResolvedDocs);
                return;
            }
        }

        for (DocumentInfo doc : mResolvedDocs) {
            if (DEBUG) Log.d(TAG, "Deleting " + redact(doc));
            try {
                performDelete(doc, parentDoc);
            } catch (Exception e) {
                Metrics.logFileOperationFailure(
                        appContext, MetricConsts.SUBFILEOP_DELETE_DOCUMENT, doc.derivedUri);
                Log.e(TAG, "Cannot delete " + redact(doc), e);
                onFileFailed(doc);
            }

            mDocsProcessed++;
            if (isCanceled()) {
                return;
            }
        }

        Metrics.logFileOperation(operationType, mResolvedDocs, null);
    }

    protected void performDelete(DocumentInfo doc, @Nullable DocumentInfo parent)
            throws ResourceException {
        deleteDocument(doc, parent);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DeleteJob")
                .append("{")
                .append("id=" + id)
                .append(", uris=" + mResourceUris)
                .append(", docs=" + mResolvedDocs)
                .append(", srcParent=" + mParentUri)
                .append(", location=" + stack)
                .append("}")
                .toString();
    }
}
