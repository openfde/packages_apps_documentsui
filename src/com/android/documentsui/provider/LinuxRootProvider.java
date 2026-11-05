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

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.android.documentsui.R;
import android.os.Bundle;
import android.content.Intent;
import de.greenrobot.event.EventBus;
import com.android.documentsui.MessageEvent;

public class LinuxRootProvider extends DocumentsProvider {

    public static final String AUTHORITY = "com.android.documentsui.fusionvolume";
    public static final String DOC_ID_ROOT = "linux";
    public static final String DIR_ID_ROOT = "/volumes";
    public static final String VOLUME_TITLE = "Linux volumes";
    public static final String TAG = "LinuxRootProvider";

    @Override
    public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(FileUtils.resolveRootProjection(projection));
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, DOC_ID_ROOT);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_RECENTS
                | Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH);
        row.add(Root.COLUMN_ICON, R.mipmap.icon_pc);
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.fde_file_system));
        row.add(Root.COLUMN_DOCUMENT_ID, DIR_ID_ROOT);
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
        // 只有 /volumes 这一层(列出 UUID 卷目录)才用 includeVolumesFile，
        // 其余任意子目录(包括 /volumes/<uuid> 起的文件系统树)都用 includeFile。
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
        result.setNotificationUri(getContext().getContentResolver(),
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId));
        return result;
    }

    @Override
    public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : FileUtils.DEFAULT_DOCUMENT_PROJECTION);
        FileUtils.includeFile(result, new File(documentId));
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
        // 尊重客户端请求的 mode：源文件按 "r"(只读) 打开时以只读模式打开，
        // 避免对只读文件强开读写导致 EACCES 复制失败。
        int pfdMode = mode != null && mode.contains("w")
                ? ParcelFileDescriptor.MODE_READ_WRITE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(new File(documentId), pfdMode);
    }

    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        String newDocId = FileUtils.renameFile(documentId, displayName);
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
    public void deleteDocument(String documentId) throws FileNotFoundException {
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

    @Override
    public String createDocument(
            String documentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File file = FileUtils.createFile(documentId, mimeType, displayName);

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
