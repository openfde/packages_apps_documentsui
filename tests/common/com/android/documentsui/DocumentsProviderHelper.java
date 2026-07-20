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

package com.android.documentsui;

import static android.content.ContentResolver.wrap;
import static android.provider.DocumentsContract.buildChildDocumentsUri;
import static android.provider.DocumentsContract.buildDocumentUri;
import static android.provider.DocumentsContract.buildRootsUri;

import static androidx.core.util.Preconditions.checkArgument;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Providers.AUTHORITY_DOWNLOADS;
import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;
import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.test.MoreAsserts;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;

import libcore.io.Streams;

import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides support for creation of documents in a test settings.
 */
public class DocumentsProviderHelper {
    private static final String TAG = "DocumentsProviderHelper";

    private final UserId mUserId;
    private final String mAuthority;
    private final Boolean mRootHasLimitedFunctionalityWhenOffline;
    private final ContentProviderClient mClient;

    /** A helper constructor for local/internal storage (primary root) with the Download folder. */
    public static DocumentsProviderHelper setupStorageAuthorityDocsHelper(Context context)
            throws Exception {
        // Create DocumentsProviderHelper to create files in Internal storage.
        DocumentsProviderHelper storageDocsHelper =
                new DocumentsProviderHelper(
                        UserId.DEFAULT_USER, AUTHORITY_STORAGE, context, AUTHORITY_STORAGE);
        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);

        // Create Download folder if it doesn't exist.
        DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");

        if (info == null) {
            ContentResolver cr = context.getContentResolver();
            Uri uri = storageDocsHelper.createFolder(primaryRoot.documentId, "Download");
            info = DocumentInfo.fromUri(cr, uri, UserId.DEFAULT_USER);
        }

        assertTrue(info != null && info.isDirectory());
        return storageDocsHelper;
    }

    /** Initializes a helper for the Downloads authority. */
    public static DocumentsProviderHelper setupDownloadsAuthorityDocsHelper(Context context)
            throws Exception {
        return new DocumentsProviderHelper(
                UserId.DEFAULT_USER, AUTHORITY_DOWNLOADS, context, AUTHORITY_DOWNLOADS);
    }

    public DocumentsProviderHelper(UserId userId, String authority, Context context, String name) {
        this(userId, authority, context, name, /* rootHasLimitedFunctionalityWhenOffline= */ false);
    }

    public DocumentsProviderHelper(
            UserId userId,
            String authority,
            Context context,
            String name,
            boolean rootHasLimitedFunctionalityWhenOffline) {
        checkArgument(!TextUtils.isEmpty(authority));
        mUserId = userId;
        mAuthority = authority;
        mClient = userId.getContentResolver(context).acquireContentProviderClient(name);
        assertNotNull(mClient);
        mRootHasLimitedFunctionalityWhenOffline = rootHasLimitedFunctionalityWhenOffline;
    }

    public RootInfo getRoot(String documentId) throws RemoteException {
        final Uri rootsUri = buildRootsUri(mAuthority);
        Cursor cursor = null;
        try {
            cursor = mClient.query(rootsUri, null, null, null, null);
            while (cursor.moveToNext()) {
                if (documentId.equals(getCursorString(cursor, Root.COLUMN_ROOT_ID))) {
                    return RootInfo.fromRootsCursor(mUserId, mAuthority, cursor);
                }
            }
            throw new IllegalArgumentException("Can't find matching root for id=" + documentId);
        } catch (Exception e) {
            throw new RuntimeException("Can't load root for id=" + documentId , e);
        } finally {
            FileUtils.closeQuietly(cursor);
        }
    }

    /**
     * Delete the specified document.
     * @param documentUri the URI of the document to delete.
     * @return true if the document was deleted or false otherwise.
     */
    public boolean deleteDocument(Uri documentUri) {
        try {
            return DocumentsContract.deleteDocument(wrap(mClient), documentUri);
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public Uri createDocument(Uri parentUri, String mimeType, String name) {
        if (name.contains("/")) {
            throw new IllegalArgumentException("Name and mimetype probably interposed.");
        }
        try {
            Uri uri = DocumentsContract.createDocument(wrap(mClient), parentUri, mimeType, name);
            return uri;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Couldn't create document: " + name + " with mimetype "
                    + mimeType, e);
        }
    }

    public Uri createDocument(String parentId, String mimeType, String name) {
        Uri parentUri = buildDocumentUri(mAuthority, parentId);
        return createDocument(parentUri, mimeType, name);
    }

    public Uri createDocument(RootInfo root, String mimeType, String name) {
        return createDocument(root.documentId, mimeType, name);
    }

    public Uri createDocumentWithFlags(String documentId, String mimeType, String name, int flags,
            String... streamTypes)
            throws RemoteException {
        Bundle in = new Bundle();
        in.putInt(StubProvider.EXTRA_FLAGS, flags);
        in.putString(StubProvider.EXTRA_PARENT_ID, documentId);
        in.putString(Document.COLUMN_MIME_TYPE, mimeType);
        in.putString(Document.COLUMN_DISPLAY_NAME, name);
        in.putStringArrayList(StubProvider.EXTRA_STREAM_TYPES, Lists.newArrayList(streamTypes));

        Bundle out = mClient.call("createDocumentWithFlags", null, in);
        Uri uri = out.getParcelable(DocumentsContract.EXTRA_URI);
        return uri;
    }

    public Uri createFolder(Uri parentUri, String name) {
        return createDocument(parentUri, Document.MIME_TYPE_DIR, name);
    }

    public Uri createFolder(String parentId, String name) {
        Uri parentUri = buildDocumentUri(mAuthority, parentId);
        return createDocument(parentUri, Document.MIME_TYPE_DIR, name);
    }

    public Uri createFolder(RootInfo root, String name) {
        return createDocument(root, Document.MIME_TYPE_DIR, name);
    }

    public void writeDocument(Uri documentUri, InputStream contents)
            throws RemoteException, IOException {
        try (ParcelFileDescriptor fd = mClient.openFile(documentUri, "w", null)) {
            assert fd != null;
            try (OutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
                FileUtils.copy(contents, out);
            }
        }
        waitForWrite();
    }

    public void writeDocument(Uri documentUri, byte[] contents)
            throws RemoteException, IOException {
        ParcelFileDescriptor file = mClient.openFile(documentUri, "w", null);
        try (AutoCloseOutputStream out = new AutoCloseOutputStream(file)) {
            out.write(contents, 0, contents.length);
        }
        waitForWrite();
    }

    public void writeAppendDocument(Uri documentUri, byte[] contents, int length)
            throws RemoteException, IOException {
        ParcelFileDescriptor file = mClient.openFile(documentUri, "wa", null);
        try (AutoCloseOutputStream out = new AutoCloseOutputStream(file)) {
            out.write(contents, 0, length);
        }
        waitForWrite();
    }

    /** Delete a single document, do nothing if it does not exist. */
    public boolean deleteDocumentIfExists(Uri documentUri) {
        try {
            DocumentsContract.deleteDocument(wrap(mClient), documentUri);
            return true;
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Could not delete document: " + documentUri, e);
            return false;
        }
    }

    public void waitForWrite() throws RemoteException {
        mClient.call("waitForWrite", null, null);
    }

    public byte[] readDocument(Uri documentUri) throws RemoteException, IOException {
        ParcelFileDescriptor file = mClient.openFile(documentUri, "r", null);
        byte[] buf = null;
        try (AutoCloseInputStream in = new AutoCloseInputStream(file)) {
            buf = Streams.readFully(in);
        }
        return buf;
    }

    public void assertChildCount(Uri parentUri, int expected) throws Exception {
        List<DocumentInfo> children = listChildren(parentUri);
        assertEquals("Incorrect file count after copy", expected, children.size());
    }

    public void assertChildCount(String parentId, int expected) throws Exception {
        List<DocumentInfo> children = listChildren(parentId, -1);
        assertEquals("Incorrect file count after copy", expected, children.size());
    }

    public void assertChildCount(RootInfo root, int expected) throws Exception {
        assertChildCount(root.documentId, expected);
    }

    public void assertHasFile(Uri parentUri, String name) throws Exception {
        List<DocumentInfo> children = listChildren(parentUri);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName) && !child.isDirectory()) {
                return;
            }
        }
        fail("Could not find file named=" + name + " in children " + children);
    }

    public void assertHasFile(String parentId, String name) throws Exception {
        Uri parentUri = buildDocumentUri(mAuthority, parentId);
        assertHasFile(parentUri, name);
    }

    public void assertHasFile(RootInfo root, String name) throws Exception {
        assertHasFile(root.documentId, name);
    }

    public void assertHasDirectory(Uri parentUri, String name) throws Exception {
        List<DocumentInfo> children = listChildren(parentUri);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName) && child.isDirectory()) {
                return;
            }
        }
        fail("Could not find name=" + name + " in children " + children);
    }

    public void assertHasDirectory(String parentId, String name) throws Exception {
        Uri parentUri = buildDocumentUri(mAuthority, parentId);
        assertHasDirectory(parentUri, name);
    }

    public void assertHasDirectory(RootInfo root, String name) throws Exception {
        assertHasDirectory(root.documentId, name);
    }

    public void assertDoesNotExist(Uri parentUri, String name) throws Exception {
        List<DocumentInfo> children = listChildren(parentUri);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName)) {
                fail("Found name=" + name + " in children " + children);
            }
        }
    }

    public void assertDoesNotExist(String parentId, String name) throws Exception {
        Uri parentUri = buildDocumentUri(mAuthority, parentId);
        assertDoesNotExist(parentUri, name);
    }

    public void assertDoesNotExist(RootInfo root, String name) throws Exception {
        assertDoesNotExist(root.getUri(), name);
    }

    public @Nullable DocumentInfo findFile(String parentId, String name)
            throws Exception {
        List<DocumentInfo> children = listChildren(parentId);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName)) {
                return child;
            }
        }
        return null;
    }

    public DocumentInfo findDocument(String parentId, String name) throws Exception {
        List<DocumentInfo> children = listChildren(parentId);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName)) {
                return child;
            }
        }
        return null;
    }

    public DocumentInfo findDocument(Uri parentUri, String name) throws Exception {
        List<DocumentInfo> children = listChildren(parentUri);
        for (DocumentInfo child : children) {
            if (name.equals(child.displayName)) {
                return child;
            }
        }
        return null;
    }

    public List<DocumentInfo> listChildren(Uri parentUri) throws Exception {
        String id = DocumentsContract.getDocumentId(parentUri);
        return listChildren(id);
    }

    public List<DocumentInfo> listChildren(String documentId) throws Exception {
        return listChildren(documentId, 100);
    }

    public List<DocumentInfo> listChildren(Uri parentUri, int maxCount) throws Exception {
        String id = DocumentsContract.getDocumentId(parentUri);
        return listChildren(id, maxCount);
    }

    public List<DocumentInfo> listChildren(String documentId, int maxCount) throws Exception {
        Uri uri = buildChildDocumentsUri(mAuthority, documentId);
        List<DocumentInfo> children = new ArrayList<>();
        try (Cursor cursor = mClient.query(uri, null, null, null, null, null)) {
            if (cursor == null) {
                Log.w(TAG, "query() returned null cursor");
            } else {
                Cursor wrapper =
                        new RootCursorWrapper(
                                mUserId,
                                mAuthority,
                                "totally-fake",
                                mRootHasLimitedFunctionalityWhenOffline,
                                cursor,
                                maxCount);
                while (wrapper.moveToNext()) {
                    children.add(DocumentInfo.fromDirectoryCursor(wrapper));
                }
            }
        }
        return children;
    }

    /** List all the children for the specific `root`. */
    public List<DocumentInfo> listAllChildren(RootInfo root) throws Exception {
        List<DocumentInfo> children = new ArrayList<>();
        try (Cursor cursor =
                mClient.query(
                        buildChildDocumentsUri(root.authority, root.documentId),
                        null,
                        null,
                        null,
                        null,
                        null)) {
            Cursor wrapper =
                    new RootCursorWrapper(
                            mUserId,
                            mAuthority,
                            root.rootId,
                            root.hasLimitedFunctionalityWhenOffline(),
                            cursor,
                            100);
            while (wrapper.moveToNext()) {
                children.add(DocumentInfo.fromDirectoryCursor(wrapper));
            }
        }
        return children;
    }

    public void assertFileContents(Uri documentUri, byte[] expected) throws Exception {
        MoreAsserts.assertEquals(
                "Copied file contents differ",
                expected, readDocument(documentUri));
    }

    public void assertFileContents(String parentId, String fileName, byte[] expected)
            throws Exception {
        DocumentInfo file = findFile(parentId, fileName);
        assertNotNull(file);
        assertFileContents(file.derivedUri, expected);
    }

    /**
     * A helper method for StubProvider only. Won't work with other providers.
     * @throws RemoteException
     */
    public Uri createVirtualFile(
            RootInfo root, String path, String mimeType, byte[] content, String... streamTypes)
                    throws RemoteException {

        Bundle args = new Bundle();
        args.putString(StubProvider.EXTRA_ROOT, root.rootId);
        args.putString(StubProvider.EXTRA_PATH, path);
        args.putString(Document.COLUMN_MIME_TYPE, mimeType);
        args.putStringArrayList(StubProvider.EXTRA_STREAM_TYPES, Lists.newArrayList(streamTypes));
        args.putByteArray(StubProvider.EXTRA_CONTENT, content);

        Bundle result = mClient.call("createVirtualFile", null, args);
        String documentId = result.getString(Document.COLUMN_DOCUMENT_ID);

        return DocumentsContract.buildDocumentUri(mAuthority, documentId);
    }

    public void setLoadingDuration(long duration) throws RemoteException {
        final Bundle extra = new Bundle();
        extra.putLong(DocumentsContract.EXTRA_LOADING, duration);
        mClient.call("setLoadingDuration", null, extra);
    }

    public void configure(String args, Bundle configuration) throws RemoteException {
        mClient.call("configure", args, configuration);
    }

    public void simulateReadErrorsForFile(String args, Bundle configuration)
            throws RemoteException {
        mClient.call("simulateReadErrorsForFile", args, configuration);
    }

    public void clear(String args, Bundle configuration) throws RemoteException {
        mClient.call("clear", args, configuration);
    }

    /**
     * A helper method for TestCloudProvider only. Sets the COLUMN_CONTENT_SYNC_STATE_FLAGS for the
     * document with the given id.
     */
    public void setSyncState(String documentId, int syncState) throws RemoteException {
        Bundle extras = new Bundle();
        extras.putString(TestCloudProvider.METHOD_DOC_ID_EXTRA, documentId);
        extras.putInt(TestCloudProvider.METHOD_STATE_EXTRA, syncState);
        mClient.call(TestCloudProvider.SET_SYNC_STATE, null, extras);
    }

    /**
     * A helper method for TestCloudProvider only. Nullifies the COLUMN_CONTENT_SYNC_STATE_FLAGS for
     * the document with the given id.
     */
    public void nullifySyncState(String documentId) throws RemoteException {
        Bundle extras = new Bundle();
        extras.putString(TestCloudProvider.METHOD_DOC_ID_EXTRA, documentId);
        mClient.call(TestCloudProvider.NULLIFY_SYNC_STATE, null, extras);
    }

    public List<RootInfo> getRootList() throws RemoteException {
        List<RootInfo> list = new ArrayList<>();
        final Uri rootsUri = DocumentsContract.buildRootsUri(mAuthority);
        Cursor cursor = null;
        try {
            cursor = mClient.query(rootsUri, null, null, null, null);
            while (cursor.moveToNext()) {
                RootInfo rootInfo = RootInfo.fromRootsCursor(mUserId, mAuthority, cursor);
                if (rootInfo != null) {
                    list.add(rootInfo);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Can't load rootInfo list", e);
        } finally {
            FileUtils.closeQuietly(cursor);
        }
        return list;
    }

    public void cleanUp() {
        mClient.close();
    }

    /**
     * A helper method for TestCloudProvider only. Cleans up the provider after testing by removing
     * files added with createDocument().
     *
     * @throws RemoteException
     */
    public void cleanUpProvider() throws RemoteException {
        mClient.call(TestCloudProvider.CLEAN_UP, null, null);
    }

    /**
     * Retrieves a list of all documents in the trash.
     *
     * @return A {@link List} of {@link DocumentInfo} objects for each item in the trash.
     * @throws Exception if there is an issue querying the content provider.
     */
    public List<DocumentInfo> getAllTrashItems(String rootId) throws Exception {
        Uri uri = DocumentsContract.buildTrashDocumentsUri(mAuthority, rootId);
        List<DocumentInfo> children = new ArrayList<>();
        try (Cursor cursor = mClient.query(uri, null, null, null, null, null)) {
            if (cursor == null) {
                Log.w(TAG, "query() returned null cursor");
            } else {
                Cursor wrapper =
                        new RootCursorWrapper(
                                mUserId,
                                mAuthority,
                                "totally-fake",
                                mRootHasLimitedFunctionalityWhenOffline,
                                cursor,
                                -1);
                while (wrapper.moveToNext()) {
                    children.add(DocumentInfo.fromDirectoryCursor(wrapper));
                }
            }
        }
        return children;
    }

    /** Sends a message to the provider to remove all summaries. See {@link TestSummaryProvider}. */
    public void clearDocumentSummaries() throws RemoteException {
        Bundle configuration = new Bundle();
        configuration.putSerializable(
                TestSummaryProvider.EXTRA_SUMMARIES, new HashMap<String, String>());
        configure(null, configuration);
    }

    /**
     * Sends a message to the provider to prepare summaries for the tests. See {@link
     * TestSummaryProvider}.
     */
    public void setProviderSummaries(Map<String, String> summaries) throws RemoteException {
        Bundle configuration = new Bundle();
        configuration.putSerializable(
                TestSummaryProvider.EXTRA_SUMMARIES, new HashMap<>(summaries));
        configure(null, configuration);
    }

    /**
     * Sends a message to the provider to mark the provider's root as empty (or not empty). See
     * {@link TestSummaryProvider}.
     */
    public void setSummaryProviderIsEmpty(boolean isEmpty) throws RemoteException {
        Bundle configuration = new Bundle();
        configuration.putBoolean(TestSummaryProvider.EXTRA_IS_EMPTY, isEmpty);
        configure(null, configuration);
    }
}
