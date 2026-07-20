/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.documentsui.util;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class FileUtils {

    /**
     * Returns the canonical pathname string of the provided abstract pathname.
     *
     * @return The canonical pathname string denoting the same file or directory as this abstract
     *         pathname.
     * @see File#getCanonicalPath()
     */
    @NonNull
    public static String getCanonicalPath(@NonNull String path) throws IOException {
        Objects.requireNonNull(path);
        return new File(path).getCanonicalPath();
    }

    /**
     * This is basically a very slightly tweaked fork of
     * {@link com.android.externalstorage.ExternalStorageProvider#getPathFromDocId(String)}.
     * The difference between this fork and the "original" method is that here we do not strip
     * the leading and trailing "/"s (because we don't worry about those).
     *
     * @return canonicalized file path.
     */
    public static String getPathFromStorageDocId(String docId) throws IOException {
        // Remove the root tag from the docId, e.g. "primary:", which should leave with the file
        // path.
        final String docIdPath = docId.substring(docId.indexOf(':', 1) + 1);

        return getCanonicalPath(docIdPath);
    }

    /**
     * Returns the number of opening apps for the given file type.
     *
     * @param doc File MIME type.
     * @param pm PackageManager (usually Activity.getPackageManager())
     * @return Number of opening apps.
     */
    public static int countOpeningApps(DocumentInfo doc, PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(doc.getDocumentUri(), doc.mimeType);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        return resolveInfos.size();
    }

    /**
     * Sanitizes the name of a folder, trimming any trailing spaces from the string or throws an
     * InvalidNameError which carries the desired validation message string and resource id.
     */
    public static String sanitizeDirectoryName(String name) throws InvalidNameError {
        return sanitizeName(name, R.string.add_folder_name_error);
    }

    /**
     * Sanitizes the name of a file, trimming any trailing spaces from the string and checking for
     * invalid characters. Throws an {@link InvalidNameError} if the name is empty or contains
     * invalid characters.
     *
     * @param name The filename string to be sanitized.
     * @return The sanitized filename.
     * @throws InvalidNameError If the filename is invalid (empty or contains invalid characters).
     */
    public static String sanitizeFileName(String name) throws InvalidNameError {
        name = sanitizeName(name, R.string.missing_rename_error);

        final int invalidCharIdx = getFirstInvalidCharIndex(name);
        if (isUseMaterial3FlagEnabled() && invalidCharIdx != -1) {
            throw new InvalidNameError(
                    "Invalid character in filename: '" + name.charAt(invalidCharIdx) + "'",
                    R.string.rename_invalid_character,
                    name.charAt(invalidCharIdx));
        }
        return name;
    }

    @VisibleForTesting
    public static String sanitizeName(String name, @StringRes int resourceId)
            throws InvalidNameError {
        name = name.replaceAll("\\s+$", "");
        if (name.isEmpty()) {
            throw new InvalidNameError("Invalid name error: '" + name + "'", resourceId);
        }
        return name;
    }

    /**
     * Returns the index of the first occurrence of an invalid character in a filename.
     *
     * @param filename The filename string to be validated.
     * @return The index of the first invalid character found. Returns -1 if no invalid characters
     *     exist, or if the input is null.
     */
    public static int getFirstInvalidCharIndex(String filename) {
        if (filename == null) {
            return -1;
        }

        for (int i = 0; i < filename.length(); i++) {
            final char c = filename.charAt(i);
            switch (c) {
                case '"':
                case '*':
                case '/':
                case ':':
                case '<':
                case '>':
                case '?':
                case '\\':
                case '|':
                case 0x7F:
                    return i;
                default:
                    continue;
            }
        }
        return -1;
    }

    public static class InvalidNameError extends Error {
        @StringRes public final int mResource;
        @Nullable private final Character mInvalidChar;

        public InvalidNameError(String message, int resource) {
            super(message);
            mResource = resource;
            mInvalidChar = null;
        }

        public InvalidNameError(String message, int resource, char invalidChar) {
            super(message);
            mResource = resource;
            mInvalidChar = invalidChar;
        }

        /**
         * Returns the invalid character if one was specified, otherwise null.
         *
         * @return The invalid character or null.
         */
        @Nullable
        public Character getInvalidChar() {
            return mInvalidChar;
        }

        /** Returns true if this error was caused by an invalid character. */
        public boolean hasInvalidChar() {
            return mInvalidChar != null;
        }

        public String getTranslatedError(Context context) {
            String errorString = context.getString(mResource);
            if (hasInvalidChar()) {
                errorString += getInvalidChar();
            }
            return errorString;
        }
    }

    private FileUtils() {
    }
}
