package com.android.documentsui.provider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.android.documentsui.R;
import android.os.Bundle;
import android.content.Intent;
import de.greenrobot.event.EventBus;
import com.android.documentsui.MessageEvent;

public class LinuxUserProvider extends DocumentsProvider {

    public static final String AUTHORITY = "com.android.documentsui.linux.user";
    public static final String DOC_ID_ROOT = "linux";
    public static final String DIR_ID_ROOT = "/volumes";
    public static final String TAG = "LinuxUserProvider";

    @Override
    public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(FileUtils.resolveRootProjection(projection));
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, DOC_ID_ROOT);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_RECENTS
                | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH
                // 声明支持查询回收站：回收站页面(TrashFileLoader)会按该 flag
                // 对本 authority 调用 queryTrashDocuments()。
                | Root.FLAG_SUPPORTS_QUERY_TRASH);
        row.add(Root.COLUMN_ICON, R.mipmap.icon_linux_home);
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.fde_linux_dir));
        row.add(Root.COLUMN_DOCUMENT_ID, DIR_ID_ROOT+"/"+FileUtils.getLinuxUUID()+ FileUtils.getLinuxHomeDir());
        row.add(Root.COLUMN_QUERY_ARGS, FileUtils.SUPPORTED_QUERY_ARGS);
        return result;
    }

    @Override
    public boolean isChildDocument(final String parentDocumentId, final String documentId) {
        return documentId.startsWith(parentDocumentId);
    }

    @Override
    public Cursor queryChildDocuments(final String parentDocumentId, final String[] projection,
            final String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : FileUtils.DEFAULT_DOCUMENT_PROJECTION);
        final File parent = new File(parentDocumentId);
        // 只有 "文件系统" 的 /volumes 这一层才用 includeVolumesFile(把 UUID 卷目录名
        // 解析成宿主路径做显示名)。其余任意子目录(包括 Home dir, 其 docId 是
        // /volumes/<uuid>/home/..., 也包含 "volumes" 单词)都应走 includeFile。
        final boolean isVolumeLevel = DIR_ID_ROOT.equals(parentDocumentId);
        for (File file : parent.listFiles()) {
            // 不显示隐藏的文件或文件夹
            if (isVolumeLevel) {
                FileUtils.includeVolumesFile(result, file);
            } else if (!file.getName().startsWith(".")) {
                // 添加文件的名字, 类型, 大小等属性
                FileUtils.includeFile(result, file);
            }
        }
        // 设置通知 URI：provider 侧 createDocument/deleteDocument/renameDocument 等
        // 通过 notifyChange 该 URI 触发客户端目录列表自动刷新。
        result.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId));
        return result;
    }

    @Override
    public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : FileUtils.DEFAULT_DOCUMENT_PROJECTION);
        if (new File(documentId).exists()) {
            FileUtils.includeFile(result, new File(documentId));
        } else if (FileUtils.isTrashedDocument(documentId)) {
            // 已回收条目：原路径已不存在，但仍在回收站元数据中，按回收条目返回
            FileUtils.includeTrashedFile(result, documentId);
        }
        result.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildDocumentUri(AUTHORITY, documentId));
        return result;
    }

    @Nullable
    @Override
    public Cursor querySearchDocuments(@NonNull String rootId, @Nullable String[] projection, @NonNull Bundle queryArgs) throws FileNotFoundException {
        Log.d(TAG, "Model update: accept querySearchDocuments "+ " ,time : "+System.currentTimeMillis() + " ，rootId： "+rootId  );
        if (queryArgs != null) {
            final MatrixCursor result = new MatrixCursor(projection != null ? projection : FileUtils.DEFAULT_DOCUMENT_PROJECTION);
            String documentId = queryArgs.getString("documentId").replaceAll("primary:","");
            final File parent = new File(documentId);
            File[] files = parent.listFiles();
            int len = files.length;
            boolean isVolumesPath = DIR_ID_ROOT.equals(documentId);
            Log.d(TAG, "Model update: accept querySearchDocuments   documentId "+documentId  +",isVolumesPath  "+isVolumesPath + " ,files "+files.length);
            for (File file : files) {
                // 不显示隐藏的文件或文件夹

                if (FileUtils.matchSearchQueryArguments(file, queryArgs)) {
                    if (isVolumesPath) {
                        FileUtils.includeVolumesFile(result, file);
                    } else if (!file.getName().startsWith(".")) {
                        // 添加文件的名字, 类型, 大小等属性
                        FileUtils.includeFile(result, file);
                    }
                }
            }
            return result;
        }else{
            Log.d(TAG, "Model update: accept querySearchDocuments   Bundle is null");
        }
        return super.querySearchDocuments(rootId, projection, queryArgs);
    }

    @Override
    public String getDocumentType(final String documentId) throws FileNotFoundException {
        return FileUtils.getDocumentType(documentId);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = super.call(method, arg, extras);
        FileUtils.call(method, arg, extras,getContext());
        return result;
    }


    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, @Nullable CancellationSignal signal)
            throws FileNotFoundException {
        // 尊重客户端请求的 mode：复制时源文件是按 "r"(只读) 打开，
        // 若统一以 MODE_READ_WRITE 打开，遇到只读文件会抛 EACCES 导致复制失败。
        int pfdMode = mode != null && mode.contains("w")
                ? ParcelFileDescriptor.MODE_READ_WRITE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(new File(documentId), pfdMode);
    }

    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        String newDocId = FileUtils.renameFile(documentId, displayName);
        // 通知旧/新 document 及其父目录的 childDocuments Uri，触发列表刷新。
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, documentId), null, false);
        File parent = new File(newDocId).getParentFile();
        if (parent != null) {
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildChildDocumentsUri(AUTHORITY,
                            parent.getAbsolutePath()), null, false);
        }
        return newDocId;
    }

    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        String newDocId = FileUtils.copyFile(sourceDocumentId, targetParentDocumentId);
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, targetParentDocumentId), null, false);
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, targetParentDocumentId),
                null, false);
        return newDocId;
    }

    @Override
    public String trashDocument(@NonNull String documentId) throws FileNotFoundException {
        String ok = FileUtils.trashDocumentToTrash(documentId, AUTHORITY);
        if (ok == null) {
            throw new FileNotFoundException("Failed to trash document: " + documentId);
        }
        // 通知原父目录与被回收 URI：目录列表和回收站列表都刷新
        File parent = new File(documentId).getParentFile();
        if (parent != null) {
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildChildDocumentsUri(AUTHORITY,
                            parent.getAbsolutePath()), null, false);
        }
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, documentId), null, false);
        notifyTrashChanged();
        return ok;
    }

    @Override
    public String restoreDocumentFromTrash(@NonNull String documentId,
            @Nullable String targetParentDocumentId) throws FileNotFoundException {
        String restored = FileUtils.restoreTrashDocument(
                documentId, targetParentDocumentId, AUTHORITY,getContext());
        if (restored == null) {
            throw new FileNotFoundException("Failed to restore document: " + documentId);
        }
        File parent = new File(restored).getParentFile();
        if (parent != null) {
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildChildDocumentsUri(AUTHORITY,
                            parent.getAbsolutePath()), null, false);
        }
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, restored), null, false);
        notifyTrashChanged();
        return restored;
    }

    @Override
    public Cursor queryTrashDocuments(@NonNull String rootId, @Nullable String[] projection,
            @Nullable Bundle queryArgs, @Nullable CancellationSignal signal)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : FileUtils.DEFAULT_DOCUMENT_PROJECTION);
        List<FileUtils.TrashItem> items = FileUtils.getTrashItems(AUTHORITY);
        for (FileUtils.TrashItem item : items) {
            File trashFile = new File(FileUtils.PATH_TRASH_LINUX, item.trashName);
            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, item.originalPath);
            row.add(Document.COLUMN_DISPLAY_NAME, new File(item.originalPath).getName());
            row.add(Document.COLUMN_MIME_TYPE, item.mimeType);
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_RESTORE
                    | Document.FLAG_SUPPORTS_DELETE);
            row.add(Document.COLUMN_SIZE, trashFile.length());
            row.add(Document.COLUMN_LAST_MODIFIED, trashFile.lastModified());
        }
        result.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildTrashDocumentsUri(AUTHORITY, DOC_ID_ROOT));
        return result;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        // 回收站内的条目：从回收站永久删除(清空回收站走 clearTrashFolder，不经这里)
        if (FileUtils.isTrashedDocument(documentId)) {
            FileUtils.permanentlyDeleteTrashed(documentId,getContext());
            notifyTrashChanged();
            EventBus.getDefault().post(new MessageEvent("","UPDATE"));
            return;
        }
        File file = new File(documentId);
        if (file.delete()) {
        } else {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, documentId), null, false);
        File parent = file.getParentFile();
        if (parent != null) {
            getContext().getContentResolver().notifyChange(
                    DocumentsContract.buildChildDocumentsUri(AUTHORITY,
                            parent.getAbsolutePath()), null, false);
        }
        EventBus.getDefault().post(new MessageEvent("","UPDATE"));
    }

    /** 通知回收站文档列表(TrashFileLoader 订阅)需要重新加载。 */
    private void notifyTrashChanged() {
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildTrashDocumentsUri(AUTHORITY, DOC_ID_ROOT), null, false);
    }

    @Override
    public String createDocument(
            String documentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File file = FileUtils.createFile(documentId, mimeType, displayName);

        // AUTHORITY 已与 manifest 保持一致，这里通知新 document 及其父目录，
        // DirectoryLoader 订阅的 childDocuments Uri 收到变更后自动刷新列表。
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildDocumentUri(AUTHORITY, file.getAbsolutePath()),
                null, false);
        getContext().getContentResolver().notifyChange(
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, documentId),
                null, false);
        EventBus.getDefault().post(new MessageEvent("","UPDATE"));
        return file.getPath();
    }

    @Override
    public boolean onCreate() {
        return true; // 
    }

}
