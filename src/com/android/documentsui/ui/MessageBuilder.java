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
package com.android.documentsui.ui;

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED;
import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_FAILURE;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.icu.text.MessageFormat;
import android.net.Uri;
import android.text.BidiFormatter;
import android.text.Html;

import androidx.annotation.PluralsRes;

import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageBuilder {

    private Context mContext;

    public MessageBuilder(Context context) {
        mContext = context;
    }

    /**
     * Generates a confirmation message for deleting the given documents.
     *
     * @param docs The list of documents to be deleted.
     * @param inTrash Whether the documents are currently in the trash.
     */
    public String generateDeleteMessage(List<DocumentInfo> docs, boolean inTrash) {
        int count = docs.size();
        HashMap<String, Object> args = new HashMap<>();
        args.put("count", count);
        if (count == 1) {
            String displayName = BidiFormatter.getInstance().unicodeWrap(docs.get(0).displayName);
            args.put("name", displayName);
        }

        int resourceId =
                inTrash
                        ? R.string.delete_forever_from_trash_confirmation_message
                        : R.string.delete_forever_confirmation_message;

        return new MessageFormat(mContext.getString(resourceId), Locale.getDefault()).format(args);
    }

    private static int getResourceId(int dialogType, int operationType) {
        switch (dialogType) {
            case DIALOG_TYPE_CONVERTED:
                return getRes(
                        isZipNgFlagEnabled()
                                ? R.string.copy_converted_warning_title
                                : R.plurals.copy_converted_warning_content);

            case DIALOG_TYPE_FAILURE:
                switch (operationType) {
                    case FileOperationService.OPERATION_COPY:
                        return getRes(
                                isZipNgFlagEnabled()
                                        ? R.string.copy_failure_alert_title
                                        : R.plurals.copy_failure_alert_content);
                    case FileOperationService.OPERATION_COMPRESS:
                        return getRes(
                                isZipNgFlagEnabled()
                                        ? R.string.compress_failure_alert_title
                                        : R.plurals.compress_failure_alert_content);
                    case FileOperationService.OPERATION_EXTRACT:
                    case FileOperationService.OPERATION_UNPACK:
                        return getRes(
                                isZipNgFlagEnabled()
                                        ? R.string.extract_failure_alert_title
                                        : R.plurals.extract_failure_alert_content);
                    case FileOperationService.OPERATION_DELETE:
                        return getRes(
                                isZipNgFlagEnabled()
                                        ? R.string.delete_failure_alert_title
                                        : R.plurals.delete_failure_alert_content);
                    case FileOperationService.OPERATION_MOVE:
                        return getRes(
                                isZipNgFlagEnabled()
                                        ? R.string.move_failure_alert_title
                                        : R.plurals.move_failure_alert_content);
                    case FileOperationService.OPERATION_TRASH:
                        return getRes(R.string.trash_failure_alert_title);
                    case FileOperationService.OPERATION_RESTORE:
                        return getRes(R.string.restore_from_trash_failure_alert_title);
                    default:
                        throw new UnsupportedOperationException();
                }
            default:
                throw new UnsupportedOperationException();
        }
    }

    /** Get the file list string based on the passed in documents, URIs and paths. */
    public static String getListContent(
            List<DocumentInfo> docs, List<Uri> uris, List<String> paths) {
        final StringBuilder list = new StringBuilder("<p>");
        final BidiFormatter bdf = BidiFormatter.getInstance();

        if (docs != null) {
            for (DocumentInfo doc : docs) {
                // Bullet and quotation mark.
                list.append("&#8226; &#34;");
                list.append(Html.escapeHtml(bdf.unicodeWrap(doc.displayName)));
                list.append("&#34;<br>");
            }
        }

        if (uris != null) {
            for (Uri uri : uris) {
                list.append("&#8226; &#34;");
                list.append(Html.escapeHtml(bdf.unicodeWrap(uri.toString())));
                list.append("&#34;<br>");
            }
        }

        if (paths != null) {
            for (String path : paths) {
                list.append("&#8226; &#34;");
                list.append(Html.escapeHtml(bdf.unicodeWrap(new File(path).getName())));
                list.append("&#34;<br>");
            }
        }

        list.append("</p>");
        return list.toString();
    }

    /**
     * Generates the content for a dialog that displays a list of files from a file operation.
     *
     * @param dialogType The type of dialog, e.g. failure or converted.
     * @param operationType The type of file operation, e.g. copy or delete.
     * @param docs The list of documents involved in the operation.
     * @param uris The list of Uris for files that failed to be represented as documents.
     * @param paths The list of paths for files that failed to be represented as documents or Uris.
     * @return A {@link ListDialogContent} object containing the title and message for the dialog.
     */
    public ListDialogContent generateListDialogContent(
            @DialogType int dialogType,
            @OpType int operationType,
            List<DocumentInfo> docs,
            List<Uri> uris,
            List<String> paths) {
        final int resourceId = getResourceId(dialogType, operationType);
        final String list = getListContent(docs, uris, paths);

        final int docCount = docs != null ? docs.size() : 0;
        final int uriCount = uris != null ? uris.size() : 0;
        final int pathCount = paths != null ? paths.size() : 0;
        final int count = docCount + uriCount + pathCount;

        boolean isUseNewFormat =
                isZipNgFlagEnabled()
                        || operationType == FileOperationService.OPERATION_TRASH
                        || operationType == FileOperationService.OPERATION_RESTORE;
        if (isUseNewFormat) {
            // When ZipNg is ON, the message is being used as the dialog title (in getResourceId()
            // below), and the list content will be used as the dialog message separately.
            // TODO(b/456014591): Remove the empty string once the translation is done.
            // We remove one argument during the string update but still pass the empty string
            // below, otherwise it will crash before the translation is done for other
            // languages.
            final String title =
                    new MessageFormat(
                                    mContext.getResources().getString(resourceId),
                                    Locale.getDefault())
                            .format(Map.of("count", count, "list", ""));
            return new ListDialogContent(title, list);
        }

        final String message = mContext.getResources().getQuantityString(resourceId, count, list);
        return new ListDialogContent(null, message);
    }

    /**
     * Generates a formatted quantity string.
     */
    public String getQuantityString(@PluralsRes int stringId, int quantity) {
        return Shared.getQuantityString(mContext, stringId, quantity);
    }

    public static class ListDialogContent {
        public final String title;
        public final String message;

        ListDialogContent(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }
}
