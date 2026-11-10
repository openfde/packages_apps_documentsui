package com.android.documentsui.provider;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import de.greenrobot.event.EventBus;
import com.android.documentsui.MessageEvent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;
import android.provider.Settings;
import android.os.PersistableBundle;

public class FileUtils {

    public static final String DESKTOP = "Desktop";

    public static final String DESKTOP_CH = "桌面";

    public static final String VOLUMES = "/volumes/";

    public static final String PATH_ID_DESKTOP = "/mnt/sdcard/" + DESKTOP + "/";

    public static final String PATH_TRASH = "/mnt/sdcard/.trash-storage/";

    /**
     * Linux 卷/Home 的回收站目录。与系统回收站(.trash-storage)共用一个目录树，
     * 放在 linux/ 子目录下：目录由 Linux 两个 provider 自行管理，
     * "清空回收站"(clearTrashFolder(PATH_TRASH)) 会连同 linux/ 一起清空。
     */
    public static final String PATH_TRASH_LINUX = "/mnt/sdcard/.trash-storage/linux/";

    /** 记录每个回收项来源(原始路径)的元数据文件 */
    public static final String TRASH_META_FILE = "meta.json";

    public static final String OPEN_DIR = "OPEN_DIR";

    public static final String OPEN_FILE = "OPEN_FILE";

    public static final String DELETE_DIR = "DELETE_DIR";

    public static final String OPEN_LINUX_APP = "OPEN_LINUX_APP";

    public static final String CLICK_BLANK = "CLICK_BLANK";

    public static final String DELETE_FILE = "DELETE_FILE";

    public static final String NEW_DIR = "NEW_DIR";

    public static final String NEW_FILE = "NEW_FILE";

    public static final String COPY_DIR = "COPY_DIR";

    public static final String COPY_FILE = "COPY_FILE";

    public static final String CUT_DIR = "CUT_DIR";

    public static final String CUT_FILE = "CUT_FILE";

    public static final String PASTE_DIR = "PASTE_DIR";

    public static final String PASTE_FILE = "PASTE_FILE";

    public static final String RENAME_DIR = "RENAME_DIR";

    public static final String RENAME_FILE = "RENAME_FILE";

    public static final String DIR_INFO = "DIR_INFO";

    public static final String FILE_INFO = "FILE_INFO";

    public static final String FILE_OPERATE = "FILE_OPERATE";

    public static final String FILE_LIST = "FILE_LIST";

    public static final String OP_COPY = "OP_COPY";

    public static final String OP_CUT = "OP_CUT";

    public static final String OP_PASTE = "OP_PASTE";

    public static final String OP_INIT = "OP_INIT";

    public static final String OP_CREATE_LINUX_ICON = "OP_CREATE_LINUX_ICON";

    public static final String OP_CREATE_ANDROID_ICON = "OP_CREATE_ANDROID_ICON";

    public static final String FILE_DESKTOP_NAME = "FILE_DESKTOP_NAME";

    protected static final String TAG = "FileUtils";


    /**
     * 默认root需要查询的项
     */
    public final static String[] DEFAULT_ROOT_PROJECTION = new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES};
    /**
     * 默认Document需要查询的项
     */
    public final static String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED};

    public static final String SUPPORTED_QUERY_ARGS = joinNewline(
            DocumentsContract.QUERY_ARG_DISPLAY_NAME,
            DocumentsContract.QUERY_ARG_FILE_SIZE_OVER,
            DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
            DocumentsContract.QUERY_ARG_MIME_TYPES);

    public static void createDesktopDir() {
        File storageRoot = Environment.getExternalStorageDirectory();
        if (storageRoot == null) {
            Log.e(TAG, "bella_doc External storage directory is null");
            return;
        }
        File file = new File(storageRoot, DESKTOP);
        if (!file.exists()) {
            boolean result = file.mkdirs();
            if (!result) {
                Log.e(TAG, " Failed to create desktop directory");
            }
        }
    }

    public static void createDesktopDir(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static String joinNewline(String... args) {
        return TextUtils.join("\n", args);
    }

    public static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : FileUtils.DEFAULT_ROOT_PROJECTION;
    }

    public static File createFile(String documentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File file = new File(documentId, displayName);
        if (file.exists()) {
            file = new File(documentId, getUniqueFileName(documentId, displayName));
        }

        if (mimeType.equals(Document.MIME_TYPE_DIR)) {
            if (!file.mkdirs()) {
                throw new FileNotFoundException("Failed to create directory(s): " + file);
            }
        } else {
            boolean created = false;
            try {
                created = file.createNewFile();
            } catch (IOException e) {
            }
            if (!created) {
                throw new FileNotFoundException("createNewFile operation failed for: " + file);
            }
        }
        return file;
    }

    public static String getUniqueFileName(String documentId, String fileName) {
        String name = fileName;
        String extension = "";
        if (fileName.contains(".") && fileName.length() > 0) {
            name = fileName.substring(0, fileName.lastIndexOf('.'));
            extension = fileName.substring(fileName.lastIndexOf('.'));
        } else {

        }

        String newName = name;
        int count = 0;
        File newFile;
        do {
            count++;
            newName = name + "_" + count + extension;
            newFile = new File(documentId, newName);
        } while (newFile.exists());

        return newName;
    }

    public static String copyFile(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {

        File parent = new File(targetParentDocumentId);
        File oldFile = new File(sourceDocumentId);
        File newFile = new File(parent.getPath(), oldFile.getName());

        try {
            // Create the new File to copy into
            boolean wasNewFileCreated = false;
            if (newFile.createNewFile()) {
                if (newFile.setWritable(true) && newFile.setReadable(true)) {
                    wasNewFileCreated = true;
                }
            }

            if (!wasNewFileCreated) {
                throw new FileNotFoundException("Failed to copy document " + sourceDocumentId +
                        ". Could not create new file.");
            }

            // Copy the bytes into the new file
            try (InputStream inStream = new FileInputStream(oldFile)) {
                try (OutputStream outStream = new FileOutputStream(newFile)) {
                    // Transfer bytes from in to out
                    byte[] buf = new byte[4096]; // ideal range for network: 2-8k, disk: 8-64k
                    int len;
                    while ((len = inStream.read(buf)) > 0) {
                        outStream.write(buf, 0, len);
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();
                }
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to copy document: " + sourceDocumentId +
                    ". " + e.getMessage());
        }
        EventBus.getDefault().post(new MessageEvent("","UPDATE"));
        return newFile.getAbsolutePath();
    }


    public static String renameFile(String documentId, String displayName) throws FileNotFoundException {
        Log.i("bella", "renameFile   documentId: " + documentId + " , displayName: " + displayName);
        if (displayName == null) {
            throw new FileNotFoundException("Failed to rename document, new name is null");
        }

        // Create the destination file in the same directory as the source file
        File sourceFile = new File(documentId);
        File sourceParentFile = sourceFile.getParentFile();
        if (sourceParentFile == null) {
            throw new FileNotFoundException("Failed to rename document. File has no parent.");
        }
        File destFile = new File(sourceParentFile.getPath(), displayName);

        // Try to do the rename
        try {
            boolean renameSucceeded = sourceFile.renameTo(destFile);
            if (!renameSucceeded) {
                throw new FileNotFoundException("Failed to rename document. Renamed failed.");
            }
        } catch (Exception e) {
            throw new FileNotFoundException("Failed to rename document. Error: " + e.getMessage());
        }
        EventBus.getDefault().post(new MessageEvent("","UPDATE"));
        return destFile.getAbsolutePath();
    }

    public static void call(String method, String arg, Bundle extras, Context context) {
        if (method.contains("createDocument") || method.contains("renameDocument")) {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.FDE_PROVIDER");
            context.sendBroadcast(intent);
        }
    }


    public static void deleteTrashFiles(Context context , String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                return;
            }
            // 回收站目录
            File trashDir = new File("/mnt/sdcard/.trash-storage/");
            if (!trashDir.exists() && !trashDir.mkdirs()) {
                return;
            }
            // 生成目标文件名，避免重名覆盖
            // File target = new File(trashDir,file.getName() + "_" + System.currentTimeMillis()
            // );
            // 直接移动文件或整个文件夹
            if (!file.renameTo(trashDir)) {
                Log.d(TAG,"移动到回收站失败: " + file.getAbsolutePath());
            }
            Settings.System.putInt(context.getContentResolver(), "trash_storage", 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将文件(或文件夹)移入回收站。
     * <p>实现参考 {@link com.android.documentsui.files.ActionHandler#trashSelectedDocuments()}：
     * 先把文件实际路径转成 external storage provider 的 document Uri，
     * 再构造 {@code OPERATION_TRASH} 的 {@link FileOperation}，交给
     * {@link FileOperationService}(TrashJob) 异步执行，
     * 最终通过 {@link DocumentsContract#trashDocument} 移入系统回收站。
     *
     * @param context 上下文
     * @param path 文件实际路径，如 /mnt/sdcard/Desktop/a.txt；
     *             也兼容相对 Desktop 的名称，如 a.txt
     */
    public static void trashPath(Context context ,String path) {
        try {
            String docId = getStorageDocIdFromPath(path);
            if (docId == null) {
                Log.e(TAG, "trashPath: cannot resolve storage document id for path: " + path);
                return;
            }
            Uri docUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", docId);
            Log.i(TAG, "trashPath: moving to trash, docUri = " + docUri);

            // 1. 构造待回收的 doc URI 集合，参考 trashSelectedDocuments()
            UrisSupplier srcs = UrisSupplier.create(
                    Collections.singletonList(docUri),
                    DocumentsApplication.getClipStore(context));

            // 2. 构造回收站操作(OPERATION_TRASH)，参考 trashSelectedDocuments()
            FileOperation operation = new FileOperation.Builder()
                    .withOpType(FileOperationService.OPERATION_TRASH)
                    .withSrcs(srcs)
                    .build();

            // 3. 交给 FileOperationService(TrashJob) 异步移入回收站
            FileOperations.start(context, operation, null, FileOperations.createJobId());
        } catch (Exception e) {
            Log.e(TAG, "trashPath failed: " + path, e);
        }
    }

    /**
     * 同步删除（移入回收站）某个文件或文件夹。
     *
     * <p>与 {@link #trashPath(Context, String)} 的回收流程一致，区别在于这里是阻塞式调用：
     * 直接把文件实际路径转成 external storage provider 的 document Uri，再调用
     * {@link DocumentsContract#trashDocument}，由系统回收站统一管理（文件会被移动到
     * {@code /mnt/sdcard/.trash-storage/} 下并可在回收站页面恢复）。适合 AppFunction 等需要
     * 同步返回结果的调用方使用。
     *
     * @param context 上下文
     * @param path 文件实际路径，如 /mnt/sdcard/Desktop/a.txt；也兼容相对 Desktop 的名称
     * @return 是否成功移入回收站
     */
    public static boolean trashPathSync(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return false;
        }
        try {
            String docId = getStorageDocIdFromPath(path);
            if (docId == null) {
                Log.e(TAG, "trashPathSync: cannot resolve storage document id for path: " + path);
                return false;
            }
            Uri docUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", docId);
            Uri trashedUri =
                    DocumentsContract.trashDocument(context.getContentResolver(), docUri);
            boolean ok = trashedUri != null;
            Log.i(TAG, "trashPathSync: " + path + " -> trash " + (ok ? "succeeded" : "failed")
                    + ", docUri = " + docUri);
            if (ok) {
                EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
            }
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "trashPathSync failed: " + path, e);
            return false;
        }
    }

    // ======================= Linux 回收站支持 =======================

    /**
     * 一个被回收的 Linux 文件/目录的元信息。
     */
    public static class TrashItem {
        public final String trashName;
        public final String originalPath;
        public final String mimeType;
        public final boolean isDir;
        public final String authority;

        TrashItem(String trashName, String originalPath, String mimeType,
                boolean isDir, String authority) {
            this.trashName = trashName;
            this.originalPath = originalPath;
            this.mimeType = mimeType;
            this.isDir = isDir;
            this.authority = authority;
        }
    }

    private static File getLinuxTrashDir() {
        return new File(PATH_TRASH_LINUX);
    }

    /**
     * 读取 linux 回收站元数据；目录/文件不存在或解析失败时返回空对象。
     */
    private static JSONObject readTrashMeta() {
        JSONObject root = new JSONObject();
        try {
            root.put("items", new JSONArray());
            File metaFile = new File(getLinuxTrashDir(), TRASH_META_FILE);
            if (!metaFile.exists()) {
                return root;
            }
            return new JSONObject(new String(Files.readAllBytes(metaFile.toPath())));
        } catch (Exception e) {
            Log.w(TAG, "readTrashMeta failed", e);
            return root;
        }
    }

    /**
     * 原子写回回收站元数据。
     */
    private static void writeTrashMeta(JSONObject meta) {
        try {
            File dir = getLinuxTrashDir();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "writeTrashMeta: cannot create dir " + dir);
                return;
            }
            File tmp = new File(dir, TRASH_META_FILE + ".tmp");
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(tmp), "UTF-8"));
            try {
                writer.write(meta.toString());
            } finally {
                writer.close();
            }
            File target = new File(dir, TRASH_META_FILE);
            if (target.exists() && !target.delete()) {
                Log.w(TAG, "writeTrashMeta: cannot remove old meta");
            }
            if (!tmp.renameTo(target)) {
                Log.w(TAG, "writeTrashMeta: rename failed");
            }
        } catch (Exception e) {
            Log.w(TAG, "writeTrashMeta failed", e);
        }
    }

    /**
     * 把文件/文件夹移入 Linux 回收站，并记录原始路径(用于恢复)。
     *
     * @param documentId 准备回收的文件路径，如 /volumes/<uuid>/home/x.txt
     * @param authority  执行回收的是哪个 provider，回收站列表按来源区分，
     *                   两个 Linux provider 各自展示自己回收的条目。
     * @return 成功返回 documentId；失败返回 null。
     */
    public static String trashDocumentToTrash(String documentId, String authority) {
        File src = new File(documentId);
        if (!src.exists()) {
            Log.e(TAG, "trash: source not exists: " + documentId);
            return null;
        }
        try {
            boolean isDir = src.isDirectory();
            String mime = isDir ? Document.MIME_TYPE_DIR : getDocumentType(documentId);

            JSONObject meta = readTrashMeta();
            JSONArray items = meta.optJSONArray("items");
            if (items == null) {
                items = new JSONArray();
                meta.put("items", items);
            }
            // 同一原始路径不允许重复回收
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null && documentId.equals(it.optString("path"))) {
                    Log.w(TAG, "trash: already trashed: " + documentId);
                    return documentId;
                }
            }

            File dir = getLinuxTrashDir();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "trash: cannot create dir " + dir);
                return null;
            }

            String trashName = src.getName();
            File dst = new File(dir, trashName);
            if (dst.exists()) {
                trashName = trashName + "_" + System.currentTimeMillis();
                dst = new File(dir, trashName);
            }
            if (!moveFile(src, dst)) {
                Log.e(TAG, "trash: move failed: " + documentId);
                return null;
            }

            JSONObject entry = new JSONObject();
            entry.put("name", trashName);
            entry.put("path", documentId);
            entry.put("mime", mime);
            entry.put("isDir", isDir);
            entry.put("authority", authority);
            items.put(entry);
            writeTrashMeta(meta);

            EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
            Log.i(TAG, "trash: " + documentId + " -> " + dst.getAbsolutePath());
            return documentId;
        } catch (Exception e) {
            Log.e(TAG, "trash failed: " + documentId, e);
            return null;
        }
    }

    /**
     * 按原始路径查找回收项。
     */
    public static TrashItem findTrashItem(String originalPath) {
        JSONObject meta = readTrashMeta();
        JSONArray items = meta.optJSONArray("items");
        if (items == null) {
            return null;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null && originalPath.equals(it.optString("path"))) {
                return new TrashItem(it.optString("name"), it.optString("path"),
                        it.optString("mime"), it.optBoolean("isDir", false),
                        it.optString("authority"));
            }
        }
        return null;
    }

    /**
     * 列出某个 Linux provider 名下的全部回收项(只返回磁盘上仍然存在的)。
     */
    public static List<TrashItem> getTrashItems(String authority) {
        List<TrashItem> result = new ArrayList<>();
        JSONObject meta = readTrashMeta();
        JSONArray items = meta.optJSONArray("items");
        if (items == null) {
            return result;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) {
                continue;
            }
            if (authority != null && !authority.equals(it.optString("authority"))) {
                continue;
            }
            File trashFile = new File(getLinuxTrashDir(), it.optString("name"));
            if (!trashFile.exists()) {
                continue;
            }
            result.add(new TrashItem(it.optString("name"), it.optString("path"),
                    it.optString("mime"), it.optBoolean("isDir", false),
                    it.optString("authority")));
        }
        return result;
    }

    /**
     * 是否已回收某个原路径。
     */
    public static boolean isTrashedDocument(String documentId) {
        return findTrashItem(documentId) != null;
    }

    /**
     * 从回收站恢复：把回收项移回原始位置(targetParentDocumentId 为空时)或指定目录。
     *
     * @return 恢复后的最终路径(可能加后缀避免重名)；失败返回 null。
     */
    public static String restoreTrashDocument(String documentId,
            String targetParentDocumentId, String authority, Context context) {
        TrashItem item = findTrashItem(documentId);
        if (item == null) {
            Log.e(TAG, "restore: no trash meta for " + documentId);
            return null;
        }
        File src = new File(getLinuxTrashDir(), item.trashName);
        if (!src.exists()) {
            Log.e(TAG, "restore: trash file missing " + src);
            return null;
        }

        File destDir = TextUtils.isEmpty(targetParentDocumentId)
                ? new File(item.originalPath).getParentFile()
                : new File(targetParentDocumentId);
        if (destDir == null || !destDir.isDirectory()) {
            Log.e(TAG, "restore: bad destination for " + item.originalPath);
            return null;
        }

        String origName = new File(item.originalPath).getName();
        File dest = new File(destDir, origName);
        if (dest.exists()) {
            dest = new File(destDir, getUniqueFileName(destDir.getAbsolutePath(), origName));
        }

        if (!moveFile(src, dest)) {
            Log.e(TAG, "restore: move back failed: " + src + " -> " + dest);
            return null;
        }
        removeTrashMetaEntry(documentId, context);

        EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        Log.i(TAG, "restore: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
        return dest.getAbsolutePath();
    }

    /**
     * 从回收站永久删除某个条目(文件/文件夹)。
     */
    public static boolean permanentlyDeleteTrashed(String documentId,Context context) {
        TrashItem item = findTrashItem(documentId);
        if (item == null) {
            return false;
        }
        File trashFile = new File(getLinuxTrashDir(), item.trashName);
        if (trashFile.exists()) {
            deleteRecursive(trashFile);
        }
        boolean metaOk = removeTrashMetaEntry(documentId,context);
        EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        return metaOk || !trashFile.exists();
    }

    /**
     * 移除回收站元数据里某个原始路径的条目。
     */
    private static boolean removeTrashMetaEntry(String originalPath, Context context) {
        try {
            JSONObject meta = readTrashMeta();
            JSONArray items = meta.optJSONArray("items");
            if (items == null) {
                return false;
            }
            JSONArray next = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null && originalPath.equals(it.optString("path"))) {
                    removed = true;
                    continue;
                }
                next.put(it);
            }
            meta.put("items", next);
            writeTrashMeta(meta);
            // 恢复/删除完最后一个条目后，meta 已空：把整个 linux 回收站目录
            // (含 meta.json)清掉，避免 .trash-storage 下残留空的 linux/ 目录。
            if (next.length() == 0) {
                deleteEmptyLinuxTrashDir(context);
            }
            return removed;
        } catch (Exception e) {
            Log.w(TAG, "removeTrashMetaEntry failed", e);
            return false;
        }
    }

    /**
     * linux 回收站已无任何条目时删除整个目录(含 meta.json)。
     */
    private static void deleteEmptyLinuxTrashDir(Context context) {
        File dir = getLinuxTrashDir();
        if (dir.exists()) {
            deleteRecursive(dir);
        }
        Settings.System.putInt(context.getContentResolver(), "trash_storage", 0);
    }

    /**
     * 把已回收的原路径补一行到 MatrixCursor 中(queryDocument 遇到已回收条目时使用)。
     */
    public static void includeTrashedFile(final MatrixCursor result, final String originalPath) {
        TrashItem item = findTrashItem(originalPath);
        if (item == null) {
            return;
        }
        File trashFile = new File(getLinuxTrashDir(), item.trashName);
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, item.originalPath);
        row.add(Document.COLUMN_DISPLAY_NAME, new File(item.originalPath).getName());
        row.add(Document.COLUMN_MIME_TYPE, item.mimeType);
        row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_RESTORE
                | Document.FLAG_SUPPORTS_DELETE);
        row.add(Document.COLUMN_SIZE, trashFile.length());
        row.add(Document.COLUMN_LAST_MODIFIED, trashFile.lastModified());
    }

    /**
     * 移动文件/目录：优先原子 rename；跨文件系统(EXDEV)退化为递归复制+删除。
     */
    private static boolean moveFile(File src, File dst) {
        if (src.renameTo(dst)) {
            return true;
        }
        try {
            copyRecursive(src, dst);
            deleteRecursive(src);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "moveFile copy fallback failed: " + src + " -> " + dst, e);
            // 清理半成品，避免污染回收站列表
            deleteRecursive(dst);
            return false;
        }
    }

    private static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("Cannot create dir " + dst);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(dst, child.getName()));
                }
            }
        } else {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
                out.close();
            }
        }
    }

    /**
     * 把文件实际路径转换成 external storage provider 的 document id，例如：
     * <ul>
     *   <li>/mnt/sdcard/Desktop/a.txt           -&gt; primary:Desktop/a.txt</li>
     *   <li>/storage/emulated/0/Pictures/b.png  -&gt; primary:Pictures/b.png</li>
     *   <li>a.txt (相对 Desktop)                 -&gt; primary:Desktop/a.txt</li>
     * </ul>
     *
     * @throws IOException 路径无法归一化时抛出
     */
    public static String getStorageDocIdFromPath(String path) throws IOException {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.isAbsolute()) {
            // 兼容相对 Desktop 的传参（即文件名）
            file = new File(PATH_ID_DESKTOP, path);
        }
        // getCanonicalPath() 会解析软链（/mnt/sdcard -> /storage/emulated/0）
        String canonical = file.getCanonicalPath();
        String storageRoot = new File(Environment.getExternalStorageDirectory(), ".")
                .getCanonicalPath();

        if (canonical.equals(storageRoot)) {
            return "primary:";
        }
        if (canonical.startsWith(storageRoot + File.separator)) {
            return "primary:" + canonical.substring(storageRoot.length() + 1);
        }
        return null;
    }

    public static void deleteFiles(String path) {
        try {
            File file = new File(path);
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    deleteFiles(child.getAbsolutePath());
                }
            }
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void includeFile(final MatrixCursor result, final File file) throws FileNotFoundException {
        try {
            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
            row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
            String mimeType = getDocumentType(file.getAbsolutePath());
            Log.i(TAG, "includeFile mimeType: " + mimeType + ",file.getAbsolutePath() " + file.getAbsolutePath());
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
            // 不再用 File.canWrite() 决定能力集：宿主机 bind mount 上 Java 的
            // canWrite() 常常不可靠(以 App UID 视角判断宿主权限)，会导致复制/剪切/
            // 粘贴/重命名等菜单被置灰。Linux 卷在文件系统层面自行支持所有操作，
            // 具体读写的真实失败会由 IO 异常向上抛出，而不是静默禁用菜单。
            boolean isDir = mimeType.equals(Document.MIME_TYPE_DIR);
            int flags = Document.FLAG_SUPPORTS_DELETE
                    | Document.FLAG_SUPPORTS_WRITE
                    | Document.FLAG_SUPPORTS_RENAME
                    | Document.FLAG_SUPPORTS_MOVE
                    // 支持移入回收站：delete/移入回收站 路由到 trashDocument
                    // (LinuxUserProvider/LinuxRootProvider 已实现)。目录同样支持回收。
                    | Document.FLAG_SUPPORTS_TRASH
                    // 目录不声明 FLAG_SUPPORTS_COPY：provider 侧 copyDocument
                    // (FileUtils.copyFile) 只支持单文件复制；目录复制走 CopyJob 的
                    // 递归字节复制，避免失败的优化复制产生残留空文件。
                    | (isDir ? 0 : Document.FLAG_SUPPORTS_COPY)
                    | (isDir ? Document.FLAG_DIR_SUPPORTS_CREATE : 0);
            if (mimeType.startsWith("image/"))
                flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
            row.add(Document.COLUMN_FLAGS, flags);
            row.add(Document.COLUMN_SIZE, file.length());
            row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void includeVolumesFile(final MatrixCursor result, final File file) throws FileNotFoundException {
        String fileName = file.getName();
        String uuid = parseFile(fileName);
        if (uuid != null) {
            fileName = uuid;
        }
        // Log.i("bella", "includeVolumesFile uuid " + uuid + ",fileName " + fileName);
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
        row.add(Document.COLUMN_DISPLAY_NAME, fileName);
        String mimeType = getDocumentType(file.getAbsolutePath());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        Log.i(TAG, "includeVolumesFile mimeType: " + mimeType + ",file.getAbsolutePath() " + file.getAbsolutePath() + ",fileName " + fileName);

        boolean isDir = mimeType != null && mimeType.equals(Document.MIME_TYPE_DIR);
        int flags = Document.FLAG_SUPPORTS_DELETE
                | Document.FLAG_SUPPORTS_WRITE
                | Document.FLAG_SUPPORTS_RENAME
                | Document.FLAG_SUPPORTS_MOVE
                | Document.FLAG_SUPPORTS_TRASH
                // 目录不声明 FLAG_SUPPORTS_COPY，理由同 includeFile()。
                | (isDir ? 0 : Document.FLAG_SUPPORTS_COPY)
                | (isDir ? Document.FLAG_DIR_SUPPORTS_CREATE : 0);
        if (mimeType != null && mimeType.startsWith("image/"))
            flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    public static boolean isMissingPermission(@Nullable Context context) {
        if (context == null) {
            return true;
        }
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 通知root的Uri失去权限, 禁止相关操作
            context.getContentResolver().notifyChange(
                    DocumentsContract.buildRootsUri(LinuxRootProvider.AUTHORITY), null);
            return true;
        }
        return false;
    }

    public static String getDocumentType(final String documentId) throws FileNotFoundException {
        File file = new File(documentId);
        if (file.isDirectory())
            return Document.MIME_TYPE_DIR;
        final int lastDot = file.getName().lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = file.getName().substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        String str = getFileTyle(documentId);
        return str;
    }

    public static String readFile() {
        String filePath = VOLUMES+".fde_path_key";
        String content = "";
        try {
            content = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return content;
    }

    public static String getLinuxUUID() {
        String result = null;
        try {
            String jsonString = readFile();
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String uuid = jsonObject.getString("UUID");
                String path = jsonObject.getString("Path");
                if ("/".equals(path)) {
                    result = uuid;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static int findNthSlashIndex(String str, int n) {
        int index = -1;
        int count = 0;

        // 从头开始查找斜杠
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '/') {
                count++;
                if (count == n) {
                    index = i;
                    break;
                }
            }
        }
        return index;
    }

    public static String findFileDir(String findPath) {
        String rootPath = VOLUMES+ getLinuxUUID() + getLinuxHomeDir();
        final File parent = new File(rootPath);
        String resPath = "";
        for (File file : parent.listFiles()) {
            if (file.getPath().contains(findPath)) {
                resPath = findPath;
                break;
            }
        }
        return resPath;
    }

    public static String getLinuxHomeDir() {
        try {
            // String propertyValue = SystemProperties.get("waydroid.host_data_path");
            String propertyValue = SystemProperties.get("openfde.host_data_path");
            int len = findNthSlashIndex(propertyValue, 3);
            String subPath = propertyValue.substring(0, len);
            Log.i("bella", "propertyValue " + propertyValue + ",subPath " + subPath);
            return subPath;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "/";
    }

    public static String parseFile(String filePath) {
        String result = null;
        try {
            String jsonString = readFile();
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String uuid = jsonObject.getString("UUID");
                String path = jsonObject.getString("Path");
                if (filePath.equals(uuid)) {
                    result = path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * get file type
     *
     * @param filePath
     * @return
     */
    public static String getFileTyle(String filePath) {
        try {
            File file = new File(filePath);
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                String extension = fileName.substring(dotIndex + 1);
                // 优先返回标准 MIME，避免用裸扩展名(如 "conf")充当类型，
                // 否则 createDocument 用非法 MIME 创建目标文件会失败。
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null) {
                    return mime;
                }
            }
            return "application/octet-stream";
        } catch (Exception e) {
            e.printStackTrace();
            return "application/octet-stream";
        }
    }

    public static Bitmap getThumbnail(String imagePath, int width, int height) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        return ThumbnailUtils.extractThumbnail(bitmap, width, height);
    }

    public static String getMimeType(File file) {
        String mimeType = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
        if (extension != null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return mimeType;
    }


    public static String readTextFromUri(Uri uri, Context context) {
        StringBuilder text = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line).append("\n");
            }
            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    public static String readTextFromPath(String docPath) {
        try {
            File file = new File(docPath);
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            fis.close();
            return sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }


    public static void writeTextToUri(Uri uri, String text, Context context) {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "UTF-8");
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)) {
            bufferedWriter.write(text);
            bufferedWriter.close();
            outputStreamWriter.close();
            outputStream.close();
            Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void writeTextToPath(String docPath, String fileContents, Context context) {
        File file = new File(docPath);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(fileContents.getBytes());
            Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void clearFileContent(Uri uri, int contentBytesLen, Context context) {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                // Write an empty byte array to clear the content
                outputStream.write(new byte[contentBytesLen]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static String newDir() {
        String documentId = PATH_ID_DESKTOP;
        File folder = new File(documentId);
        String newDirName = "NewDir";
        if (!folder.exists()) {
            boolean result = folder.mkdirs();
            if (result) {
                Log.i("bella", "Folder created: " + folder.getAbsolutePath());
            } else {
                Log.e("bella", "Failed to create folder");
            }
        } else {
            Log.i("bella", "Folder already exists");
            newDirName = getUniqueFileName(documentId, newDirName);
        }
        folder = new File(documentId, newDirName);
        folder.mkdirs();
        return newDirName;
    }

    public static void initFolder() {

    }

    public static String newFile() {
        try {
            String documentId = PATH_ID_DESKTOP;
            File folder = new File(documentId);
            String newDocName = "NewFile.txt";
            if (!folder.exists()) {
                boolean result = folder.mkdirs();
                if (result) {
                    Log.i("bella", "Folder created: " + folder.getAbsolutePath());
                } else {
                    Log.e("bella", "Failed to create folder");
                }
            } else {
                Log.i("bella", "file already exists");
                newDocName = getUniqueFileName(documentId, newDocName);
            }
            folder = new File(documentId, newDocName);
            folder.createNewFile();
            folder.setExecutable(true);
            return newDocName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void copyFileToClipboard(Context context, Uri uri) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newUri(context.getContentResolver(), "File", uri);
        if (clip == null) {
            Log.i("bella", "copyFileToClipboard........ clip is null ");
             return; 
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putInt("clipper:opType", 1);
        clip.getDescription().setExtras(extras);

        clipboard.setPrimaryClip(clip);
        Log.i("bella", "copyFileToClipboard  File copied to clipboard");
    }

    public static Uri pasteFileFromClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null) {
                Log.i("bella", "pasteFileFromClipboard........ clip is null ");
            }
            ClipData.Item item = clip.getItemAt(0);
            return item.getUri();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void cleanClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                // clipboard.setPrimaryClip(null);
                clipboard.clearPrimaryClip();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFolder(String oldPath, String newPath) {

        try {
            File newDir = new File(newPath);
            if (!newDir.exists()) newDir.mkdirs();
            File oldDir = new File(oldPath);
            String[] file = oldDir.list();
            File temp = null;
            for (int i = 0; i < file.length; i++) {
                if (oldPath.endsWith(File.separator)) {
                    temp = new File(oldPath + file[i]);
                } else {
                    temp = new File(oldPath + File.separator + file[i]);
                }

                if (temp.isFile()) {
                    FileInputStream input = new FileInputStream(temp);
                    FileOutputStream output = new FileOutputStream(newPath + "/" +
                            (temp.getName()).toString());
                    byte[] b = new byte[1024];
                    int len;
                    while ((len = input.read(b)) != -1) {
                        output.write(b, 0, len);
                    }
                    output.flush();
                    output.close();
                    input.close();
                }
                if (temp.isDirectory()) {
                    copyFolder(oldPath + "/" + file[i], newPath + "/" + file[i]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public static void copyUriToFile(Context context, Uri uri, File destinationFile) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteFileWithUri(Context context, Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            // Use DocumentsContract for document Uris
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return DocumentsContract.deleteDocument(contentResolver, uri);
            }
            // Handle other Uris (like MediaStore)
            int rowsDeleted = contentResolver.delete(uri, null, null);
            return rowsDeleted > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return path;
        }
        return path.substring(lastSlashIndex + 1);
    }


    public static String getDesktopFiles() {
        try {
            String documentId = FileUtils.PATH_ID_DESKTOP;
            File parent = new File(documentId);
            File[] files = parent.listFiles();
            return Arrays.stream(files)
                    .map(File::getName)
                    .collect(Collectors.joining("###"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void drawableToPng(Context context, Drawable drawable, String filePath) {
        Bitmap bitmapT = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);

        Bitmap bitmap;
        if (drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) drawable;
            bitmapT = adaptiveIconToBitmap(adaptiveIconDrawable);
            bitmapT = scaleBitmap(bitmapT, 48, 48);
            Bitmap b2 = vectorToBitmap(context, R.mipmap.bg_android);
            b2 = scaleBitmap(b2, 80, 80);
            bitmap = overlayBitmaps(b2, bitmapT);
        } else {
            Log.i("bella", "createAllAndroidIconToLinux drawable is not  AdaptiveIconDrawable....  ");
            if (drawable instanceof BitmapDrawable) {
                Log.i("bella", "createAllAndroidIconToLinux drawable 1111");
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                bitmapT = bitmapDrawable.getBitmap();
                bitmapT = scaleBitmap(bitmapT, 48, 48);
                Bitmap b2 = vectorToBitmap(context, R.mipmap.bg_android);
                b2 = scaleBitmap(b2, 80, 80);
                bitmap = overlayBitmaps(b2, bitmapT);
                // }else if(drawable instanceof VectorDrawable){
                // }else if(drawable instanceof StateListDrawable){
                // }else if(drawable instanceof LayerDrawable){
                // }else if(drawable instanceof LevelListDrawable){
                // }else if(drawable instanceof ShapeDrawable){
                // }else if(drawable instanceof GradientDrawable){
                // }else if(drawable instanceof InsetDrawable){
                // }else if(drawable instanceof RippleDrawable){
                // }else if(drawable instanceof ColorDrawable){
                // }else if(drawable instanceof AdaptiveIconDrawable){
            } else {
                Log.i("bella", "createAllAndroidIconToLinux drawable cccc");
                bitmap = bitmapT;
            }

        }

        // bitmapT  = scaleBitmap(bitmapT,42,42);
        // Bitmap b2 = vectorToBitmap(context, R.mipmap.bg_android);
        // b2  = scaleBitmap(b2,80,80);
        // Bitmap bitmap = overlayBitmaps(b2,bitmapT);  

        Bitmap mutableBitmap = bitmapT.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        // Canvas canvas = new Canvas(bitmapT);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        // 保存Bitmap到PNG文件
        File file = new File(filePath);
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static Bitmap vectorToBitmap(Context context, int drawableId) {
        Drawable drawable = context.getResources().getDrawable(drawableId, null);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Bitmap overlayBitmaps(Bitmap bitmap1, Bitmap bitmap2) {
        // 创建一个与第一个 Bitmap 相同大小的空白 Bitmap
        Bitmap overlayBitmap = Bitmap.createBitmap(bitmap1.getWidth(), bitmap1.getHeight(), bitmap1.getConfig());
        // 创建 Canvas，将第一个 Bitmap 作为底图
        Canvas canvas = new Canvas(overlayBitmap);
        canvas.drawBitmap(bitmap1, 0, 0, null);  // 将 bitmap1 绘制到 canvas 上
        // 将第二个 Bitmap 绘制到 Canvas 上，叠加在第一个 Bitmap 上
        canvas.drawBitmap(bitmap2, (bitmap1.getWidth() - bitmap2.getWidth()) / 2, (bitmap1.getHeight() - bitmap2.getHeight()) / 2, null);  // 将 bitmap2 绘制到 canvas 上
        // 将叠加后的 Bitmap 设置到 ImageView
        return overlayBitmap;
    }

    public static Bitmap scaleBitmap(Bitmap originalBitmap, int newWidth, int newHeight) {
        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
    }

    private static List<ApplicationInfo> getAllApp(Context context) {
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<UserHandle> userHandles = userManager.getUserProfiles();
        List<LauncherActivityInfo> list = new ArrayList<>();
        for (UserHandle userHandle : userHandles) {
            list.addAll(launcherApps.getActivityList(null, userHandle));
        }

        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> listApps = new ArrayList<>();
        Log.i("bella", "getAllApp list  size:   " + list.size());
        for (LauncherActivityInfo li : list) {
            String appName = packageManager.getApplicationLabel(li.getApplicationInfo()).toString();
            Drawable icon = packageManager.getApplicationIcon(li.getApplicationInfo());
            String packageName = li.getApplicationInfo().packageName;
            // Log.i("bella","getAllApp list  li:  getName: " + li.getName() +"  ,appName: "+appName + ",packageName "+packageName + " ,name : "+li.getApplicationInfo().name);
            listApps.add(li.getApplicationInfo());
        }
        return listApps;
    }

    public static String getMD5(String input) {
        try {
            // 创建一个 MessageDigest 实例，指定使用 MD5 算法
            MessageDigest digest = MessageDigest.getInstance("MD5");

            // 计算 MD5 值，得到一个字节数组
            byte[] hashBytes = digest.digest(input.getBytes());

            // 转换字节数组为 16 进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "a" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void createAllAndroidIconToLinux(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        String rootPath = VOLUMES+ getLinuxUUID() + getLinuxHomeDir() + "/.local/share/icons/";


        if ("".equals(packageName)) {
            List<ApplicationInfo> apps = packageManager.getInstalledApplications(0);
            apps.addAll(getAllApp(context));
            for (ApplicationInfo appInfo : apps) {
                try {
                    // if(appInfo.name !=null){
                    Drawable icon = packageManager.getApplicationIcon(appInfo);
                    String appName = packageManager.getApplicationLabel(appInfo).toString();
                    String md5 = appInfo.packageName;//getMD5(appInfo.packageName);

                    String path = rootPath + md5 + ".png";
                    Log.i("bella", "createAllAndroidIconToLinux md5 : " + md5 + ",path: " + path + ",packName: " + appInfo.packageName);
                    File file = new File(path);
                    if (!file.exists() && !path.contains(" ")) {
                        drawableToPng(context, icon, path);
                    }
                    // }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                Drawable icon = packageManager.getApplicationIcon(appInfo);
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                String md5 = appInfo.packageName;// getMD5(appInfo.packageName);

                String path = rootPath + md5 + ".png";
                Log.i("bella", "createAllAndroidIconToLinux md5 : " + md5 + ",path: " + path + ",packageName: " + packageName + ",appName: " + appName);
                File file = new File(path);
                if (!file.exists() && !path.contains(" ")) {
                    drawableToPng(context, icon, path);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static boolean isAppInstalled(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true; // app installed
        } catch (PackageManager.NameNotFoundException e) {
            return false; // app not install
        }
    }

    public static void setSystemProperty(String key, String value) {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method setMethod = systemPropertiesClass.getDeclaredMethod("set", String.class, String.class);
            setMethod.invoke(null, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Bitmap addTextWatermark(Bitmap source, String watermarkText) {
        int width = source.getWidth();
        int height = source.getHeight();

        // 创建一个新的Bitmap，大小与原始Bitmap相同
        Bitmap watermarkBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // 创建画布并将原图绘制到画布上
        Canvas canvas = new Canvas(watermarkBitmap);
        canvas.drawBitmap(source, 0, 0, null);

        // 设置水印文本样式
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);  // 设置水印文本颜色
        //paint.setAlpha(100);  // 设置透明度，100代表半透明
        paint.setTextSize(14f);  // 设置文本大小
        // paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);  // 设置抗锯齿

        // 获取水印文本的边界框，用于计算文本位置
        Rect textBounds = new Rect();
        paint.getTextBounds(watermarkText, 0, watermarkText.length(), textBounds);
        int textWidth = textBounds.width();
        int textHeight = textBounds.height();

        // 设置文本的位置（右下角）
        float x = (width - textWidth) / 2;//width - textWidth - 20f;  // 距离右侧20像素
        float y = (height - textHeight) / 2;//height - textHeight - 20f;  // 距离底部20像素

        // 在Bitmap上绘制文本水印
        canvas.drawText(watermarkText, x, y, paint);

        return watermarkBitmap;
    }

    // 在Bitmap上添加图片水印
    private static Bitmap addImageWatermark(Bitmap source, int watermarkResId, Context context) {
        int width = source.getWidth();
        int height = source.getHeight();

        // 创建一个新的Bitmap，大小与原始Bitmap相同
        Bitmap watermarkBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // 创建画布并将原图绘制到画布上
        Canvas canvas = new Canvas(watermarkBitmap);
        canvas.drawBitmap(source, 0, 0, null);

        // 获取水印图片
        Bitmap watermarkImage = BitmapFactory.decodeResource(context.getResources(), watermarkResId);

        // 设置水印图片的大小（可选）
        int watermarkWidth = width / 4;  // 水印宽度为原图的1/4
        int watermarkHeight = watermarkImage.getHeight() * watermarkWidth / watermarkImage.getWidth();  // 保持宽高比

        // 设置水印图片的位置（右下角）
        float left = width - watermarkWidth - 20f;  // 距离右侧20像素
        float top = height - watermarkHeight - 20f;  // 距离底部20像素

        // 在Bitmap上绘制水印图片
        canvas.drawBitmap(Bitmap.createScaledBitmap(watermarkImage, watermarkWidth, watermarkHeight, true), left, top, null);

        return watermarkBitmap;
    }

    public static Bitmap adaptiveIconToBitmap(AdaptiveIconDrawable adaptiveIconDrawable) {
        int width = adaptiveIconDrawable.getIntrinsicWidth();
        int height = adaptiveIconDrawable.getIntrinsicHeight();

        // 创建一个与 AdaptiveIcon 大小相同的 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 获取前景和背景
        Drawable background = adaptiveIconDrawable.getBackground();
        Drawable foreground = adaptiveIconDrawable.getForeground();

        // 绘制背景和前景
        if (background != null) {
            background.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            background.draw(canvas);
        } else {
            Log.i("bella", "createAllAndroidIconToLinux background is null ...  ");
        }
        if (foreground != null) {
            foreground.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            foreground.draw(canvas);
        } else {
            Log.i("bella", "createAllAndroidIconToLinux  background is null ....  ");
        }

        return bitmap;
    }


    public static boolean containsChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        //is Chinese
        String regex = "[\\u4e00-\\u9fa5]";
        return str.matches(".*" + regex + ".*");
    }

/**
 *  get desktop path
 */
    public static String getDesktopPath(){
        String desktopPath = PATH_ID_DESKTOP;
        String documentId =  VOLUMES +getLinuxUUID()+getLinuxHomeDir()+"/"+DESKTOP_CH+"/";  
        File ff = new File(documentId);
        if(!ff.exists()){
            desktopPath  = getLinuxHomeDir()+"/"+DESKTOP+"/";
        }else{
            desktopPath  = getLinuxHomeDir()+"/"+DESKTOP_CH+"/";
        }
       return desktopPath ;
    }

    public static String getDesktopShortPath(){
        String desktopPath = DESKTOP;
        String documentId =  VOLUMES +getLinuxUUID()+getLinuxHomeDir()+"/"+DESKTOP_CH+"/";  
        File ff = new File(documentId);
        if(!ff.exists()){
            desktopPath  = DESKTOP;
        }else{
            desktopPath  = DESKTOP_CH;
        }
        return desktopPath ;
    }

/**
 *  scan media file update db  
 */
    public static void triggerSystemMediaScan(final Context context,final String filePath) {
        try{
             new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "triggerSystemMediaScan filePath 1: " + filePath);
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    File externalDir = new File(Environment.getExternalStorageDirectory() +"/"+filePath);
                    Uri contentUri = Uri.fromFile(externalDir);
                    mediaScanIntent.setData(contentUri);
                    context.sendBroadcast(mediaScanIntent);
                    Log.i(TAG, "triggerSystemMediaScan contentUri 2: " + contentUri.toString());
                }
            }, 100 * 3);
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static boolean isChineseSystem() {
        return Locale.getDefault().getLanguage().equals("zh");
    }

    public static boolean matchSearchQueryArguments(File file, Bundle queryArgs) {
        if (file == null) {
            return false;
        }

        final String fileMimeType;
        final String fileName = file.getName();

        if (file.isDirectory()) {
            fileMimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            int dotPos = fileName.lastIndexOf('.');
            if (dotPos < 0) {
                return false;
            }
            final String extension = fileName.substring(dotPos + 1);
            fileMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return matchSearchQueryArguments(queryArgs, fileName, fileMimeType,
                file.lastModified(), file.length());
    }

public static final String QUERY_ARG_DISPLAY_NAME = "android:query-arg-display-name";
public static final String QUERY_ARG_MIME_TYPES = "android:query-arg-mime-types";
public static final String QUERY_ARG_FILE_SIZE_OVER = "android:query-arg-file-size-over";
public static final String QUERY_ARG_LAST_MODIFIED_AFTER =
    "android:query-arg-last-modified-after";
    public static boolean matchSearchQueryArguments(Bundle queryArgs, String displayName,
            String mimeType, long lastModified, long size) {
        if (queryArgs == null) {
            return true;
        }

        final String argDisplayName = queryArgs.getString(QUERY_ARG_DISPLAY_NAME, "");
        if (!argDisplayName.isEmpty()) {
            // TODO (118795812) : Enhance the search string handled in DocumentsProvider
            if (!displayName.toLowerCase().contains(argDisplayName.toLowerCase())) {
                return false;
            }
        }

        final long argFileSize = queryArgs.getLong(QUERY_ARG_FILE_SIZE_OVER, -1 /* defaultValue */);
        if (argFileSize != -1 && size < argFileSize) {
            return false;
        }

        final long argLastModified = queryArgs.getLong(QUERY_ARG_LAST_MODIFIED_AFTER,
                -1 /* defaultValue */);
        if (argLastModified != -1 && lastModified < argLastModified) {
            return false;
        }

        final String[] argMimeTypes = queryArgs.getStringArray(QUERY_ARG_MIME_TYPES);
        if (argMimeTypes != null && argMimeTypes.length > 0) {
            mimeType = Intent.normalizeMimeType(mimeType);
            for (String type : argMimeTypes) {
                if (MimeTypeFilter.matches(mimeType, Intent.normalizeMimeType(type))) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }


    /**
     * 清空指定目录下的所有文件和子文件夹（保留根目录本身）
     * @param dirPath 目录路径，例如 "/mnt/sdcard/.trash-storage/"
     */
    public static void clearTrashFolder(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return;
        }
        clearTrashFolder(new File(dirPath));
    }

    /**
     * 重载方法：传入 File 对象
     */
    public static void clearTrashFolder(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        // 获取目录下的所有子项
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteRecursive(file);
            }
        }
    }

    /**
     * 递归删除文件或子目录
     */
    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            // 如果是子文件夹，先清空其内部内容
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        // 删除文件或已经清空的空文件夹
        file.delete();
    }
}
