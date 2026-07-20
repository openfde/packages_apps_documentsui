package com.android.documentsui;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.database.Cursor;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;

/**
 * A utility class for creating and parsing unique model IDs for documents. A model ID is a string
 * that uniquely identifies a document across different users and content providers (authorities).
 * The format is "userId|authority|documentId".
 */
public class ModelId {

    /**
     * Builds a model ID string from a cursor.
     *
     * @param cursor The cursor containing document information. Must include {@link
     *     RootCursorWrapper#COLUMN_USER_ID}, {@link RootCursorWrapper#COLUMN_AUTHORITY}, and {@link
     *     DocumentsContract.Document#COLUMN_DOCUMENT_ID}.
     * @return The generated model ID string, or {@code null} if the cursor is null or missing
     *     required information.
     */
    @Nullable
    public static String build(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        return ModelId.build(UserId.of(getCursorInt(cursor, RootCursorWrapper.COLUMN_USER_ID)),
                getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY),
                getCursorString(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID));
    }

    /**
     * Builds a model ID string from its constituent parts.
     *
     * @param userId The user ID associated with the document.
     * @param authority The authority of the content provider for the document.
     * @param docId The document ID.
     * @return The generated model ID string in the format "userId|authority|docId", or {@code null}
     *     if any of the arguments are null or empty.
     */
    @Nullable
    public static String build(UserId userId, String authority, String docId) {
        if (userId == null || authority == null || authority.isEmpty() || docId == null
                || docId.isEmpty()) {
            return null;
        }
        return userId + "|" + authority + "|" + docId;
    }

    /**
     * Extracts the document ID from a model ID string. The model ID is expected to be in the format
     * "userId|authority|docId".
     *
     * @param modelId The model ID string.
     * @return The document ID part of the model ID, or {@code null} if the model ID is invalid.
     */
    @Nullable
    public static String getDocumentId(@Nullable String modelId) {
        if (TextUtils.isEmpty(modelId)) {
            return null;
        }
        final String[] parts = modelId.split("\\|");
        if (parts.length != 3) {
            return null;
        }
        return parts[2];
    }
}
