/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.base;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsProvider;
import android.util.Log;

import com.android.documentsui.picker.LastAccessedProvider;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Representation of a stack of {@link DocumentInfo}, usually the result of a
 * user-driven traversal.
 */
public class DocumentStack implements Durable, Parcelable {

    private static final String TAG = "DocumentStack";

    private static final int VERSION_INIT = 1;
    private static final int VERSION_ADD_ROOT = 2;

    private LinkedList<DocumentInfo> mList;
    private @Nullable RootInfo mRoot;

    private boolean mStackTouched;

    public DocumentStack() {
        mList = new LinkedList<>();
    }

    /**
     * Creates an instance, and pushes all docs to it in the same order as they're passed as
     * parameters, i.e. the last document will be at the top of the stack.
     */
    public DocumentStack(RootInfo root, DocumentInfo... docs) {
        mList = new LinkedList<>();
        for (int i = 0; i < docs.length; ++i) {
            mList.add(docs[i]);
        }

        mRoot = root;
    }

    /**
     * Same as {@link #DocumentStack(DocumentStack, DocumentInfo...)} except it takes a {@link List}
     * instead of an array.
     */
    public DocumentStack(RootInfo root, List<DocumentInfo> docs) {
        mList = new LinkedList<>(docs);
        mRoot = root;
    }

    /**
     * Makes a new copy, and pushes all docs to the new copy in the same order as they're
     * passed as parameters, i.e. the last document will be at the top of the stack.
     */
    public DocumentStack(DocumentStack src, DocumentInfo... docs) {
        mList = new LinkedList<>(src.mList);
        for (DocumentInfo doc : docs) {
            push(doc);
        }

        mStackTouched = false;
        mRoot = src.mRoot;
    }

    public boolean isInitialized() {
        return mRoot != null;
    }

    public @Nullable RootInfo getRoot() {
        return mRoot;
    }

    public boolean isEmpty() {
        return mList.isEmpty();
    }

    public int size() {
        return mList.size();
    }

    public DocumentInfo peek() {
        return mList.peekLast();
    }

    /**
     * Returns {@link DocumentInfo} at index counted from the bottom of this stack.
     */
    public DocumentInfo get(int index) {
        return mList.get(index);
    }

    public void push(DocumentInfo info) {
        mList.addLast(info);
        mStackTouched = true;
        if (DEBUG) {
            Log.d(TAG, "Pushed " + quote(info) + " to the stack");
            Log.d(TAG, "Stack = " + toShortString());
        }
    }

    public DocumentInfo pop() {
        final DocumentInfo result = mList.removeLast();
        mStackTouched = true;
        if (DEBUG) {
            Log.d(TAG, "Popped " + quote(result) + " from the stack");
            Log.d(TAG, "Stack = " + toShortString());
        }
        return result;
    }

    public void popToRootDocument() {
        while (mList.size() > 1) {
            mList.removeLast();
        }
        mStackTouched = true;
        if (DEBUG) {
            Log.d(TAG, "Popped all the folders from the stack except the root folder");
            Log.d(TAG, "Stack = " + toShortString());
        }
    }

    /**
     * If this stack contains a folder with the given URI, then all the folders on top of the found
     * one are popped, otherwise nothing is changed.
     *
     * @return whether a folder with the given URI was found in this stack.
     */
    public boolean popTo(@Nonnull Uri uri) {
        int i = 0;
        for (DocumentInfo doc : mList) {
            i += 1;
            if (uri.equals(doc.derivedUri)) {
                if (DEBUG) Log.d(TAG, "Trim stack to position " + i);
                while (mList.size() > i) {
                    mList.removeLast();
                    mStackTouched = true;
                }
                if (DEBUG) Log.d(TAG, "Stack = " + toShortString());
                assert uri.equals(peek().derivedUri);
                return true;
            }
        }

        return false;
    }

    public void changeRoot(RootInfo root) {
        reset();
        mRoot = root;
        if (DEBUG) Log.d(TAG, "Changed root of the stack to " + quote(root));

        // Add this for keep stack size is 1 on recent root.
        if (root.isRecents()) {
            DocumentInfo rootRecent = new DocumentInfo();
            rootRecent.userId = root.userId;
            rootRecent.deriveFields();
            push(rootRecent);
            return;
        }

        if (root.isTrash()) {
            DocumentInfo trashRoot = new DocumentInfo();
            trashRoot.userId = root.userId;
            trashRoot.deriveFields();
            push(trashRoot);
            return;
        }

        if (DEBUG) Log.d(TAG, "Stack = " + toShortString());
    }

    /** This will return true even when the initial location is set.
     * To get a read on if the user has changed something, use {@link #hasInitialLocationChanged()}.
     */
    public boolean hasLocationChanged() {
        return mStackTouched;
    }

    public String getTitle() {
        if (mList.size() == 1 && mRoot != null) {
            if (isHomeScreenFilesFlagEnabled()
                    && mRoot.documentId != null
                    && mRoot.authority != null
                    && !mRoot.getDocumentUri().equals(peek().derivedUri)) {
                // If the document stack was created as a result of drag and drop to a shortcut or
                // by clicking on a shortcut on the sidebar, then we want the title of the shortcut
                // folder. In this case, the peek item on the list will be the shortcut folder and
                // not the root folder.
                return peek().displayName;
            }
            return mRoot.title;
        } else if (mList.size() > 1) {
            return peek().displayName;
        } else {
            return null;
        }
    }

    public boolean isRecents() {
        return mRoot != null && mRoot.isRecents() && size() == 1;
    }

    /**
     * @return whether the user is at the top-level of the trash directory.
     */
    public boolean isTrashTopLevel() {
        return isTrashRoot() && size() == 1;
    }

    /**
     * @return whether the current navigation stack belongs to the trash root. This will be true
     *     even when navigating into sub-folders within the trash.
     */
    public boolean isTrashRoot() {
        if (!isTrashFlowEnabled()) {
            return false;
        }

        return mRoot != null && mRoot.isTrash();
    }

    /**
     * Resets this stack to the given stack. It takes the reference of {@link #mList} and
     * {@link #mRoot} instead of making a copy.
     */
    public void reset(DocumentStack stack) {
        mList = stack.mList;
        mRoot = stack.mRoot;
        mStackTouched = true;
        if (DEBUG) {
            Log.d(TAG, "Reset the whole stack");
            Log.d(TAG, "Stack = " + toShortString());
        }
    }

    @Override
    public String toString() {
        return "DocumentStack{"
                + "root=" + mRoot
                + ", docStack=" + mList
                + ", stackTouched=" + mStackTouched
                + "}";
    }

    /** Gets a short string representation of this object for logging and debugging purposes. */
    public String toShortString() {
        StringBuilder s = new StringBuilder();
        s.append('{');
        s.append(quote(mRoot));

        String separator = ": ";
        for (DocumentInfo doc : mList) {
            s.append(separator);
            s.append(quote(doc));
            separator = " > ";
        }

        s.append('}');
        return s.toString();
    }

    @Override
    public void reset() {
        mList.clear();
        mRoot = null;
    }

    private void updateRoot(Collection<RootInfo> matchingRoots) throws FileNotFoundException {
        for (RootInfo root : matchingRoots) {
            // RootInfo's equals() only checks authority and rootId, so this will update RootInfo if
            // its flag has changed.
            if (root.equals(this.mRoot)) {
                this.mRoot = root;
                return;
            }
        }
        throw new FileNotFoundException("Failed to find matching mRoot for " + mRoot);
    }

    /**
     * Update a possibly stale restored stack against a live
     * {@link DocumentsProvider}.
     */
    private void updateDocuments(Context context) throws FileNotFoundException {
        for (DocumentInfo info : mList) {
            info.updateSelf(info.userId.getContentResolver(context), info.userId);
        }
    }

    public static @Nullable DocumentStack fromLastAccessedCursor(
            Cursor cursor, Collection<RootInfo> matchingRoots, Context context)
            throws IOException {

        if (cursor.moveToFirst()) {
            DocumentStack stack = new DocumentStack();
            final byte[] rawStack = cursor.getBlob(
                    cursor.getColumnIndex(LastAccessedProvider.Columns.STACK));
            DurableUtils.readFromArray(rawStack, stack);

            stack.updateRoot(matchingRoots);
            stack.updateDocuments(context);

            return stack;
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof DocumentStack)) {
            return false;
        }

        DocumentStack other = (DocumentStack) o;
        return Objects.equals(mRoot, other.mRoot)
                && mList.equals(other.mList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRoot, mList);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        final int version = in.readInt();
        switch (version) {
            case VERSION_INIT:
                throw new ProtocolException("Ignored upgrade");
            case VERSION_ADD_ROOT:
                if (in.readBoolean()) {
                    mRoot = new RootInfo();
                    mRoot.read(in);
                }
                final int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    final DocumentInfo doc = new DocumentInfo();
                    doc.read(in);
                    mList.add(doc);
                }
                mStackTouched = in.readInt() != 0;
                break;
            default:
                throw new ProtocolException("Unknown version " + version);
        }
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(VERSION_ADD_ROOT);
        if (mRoot != null) {
            out.writeBoolean(true);
            mRoot.write(out);
        } else {
            out.writeBoolean(false);
        }
        final int size = mList.size();
        out.writeInt(size);
        for (int i = 0; i < size; i++) {
            final DocumentInfo doc = mList.get(i);
            doc.write(out);
        }
        out.writeInt(mStackTouched ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        DurableUtils.writeToParcel(dest, this);
    }

    public static final Creator<DocumentStack> CREATOR = new Creator<DocumentStack>() {
        @Override
        public DocumentStack createFromParcel(Parcel in) {
            final DocumentStack stack = new DocumentStack();
            DurableUtils.readFromParcel(in, stack);
            return stack;
        }

        @Override
        public DocumentStack[] newArray(int size) {
            return new DocumentStack[size];
        }
    };

    private static String quote(DocumentInfo doc) {
        if (doc == null) return "(no folder)";
        if (doc.displayName == null) return "(no name)";
        return "'" + doc.displayName + "'";
    }

    private static String quote(RootInfo root) {
        if (root == null) return "(no root)";
        return "'" + root.getDirectoryString() + "'";
    }
}
