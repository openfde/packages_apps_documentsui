package com.android.documentsui;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;


import androidx.recyclerview.selection.MutableSelection;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.dirlist.AppsRowItemData.AppData;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.provider.FileUtils;
import com.android.documentsui.util.NetUtils;
import com.android.documentsui.util.SPUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.app.ActivityManager;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.os.Build;

public class IpcService extends Service {
    Context context ;

    private static final String TAG = "IpcService";

    private IDataChangedCallback dataChangedCallback;

    public static final String ACTION_UPDATE_FILE = "com.android.documentsui.UPDATE_FILE";

    private DocumentClipper mClipper;


    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        mClipper = DocumentsApplication.getDocumentClipper(getApplicationContext());
        DocumentsApplication.getInstance().setIpcService(this);
    }



    private final IDocAidlInterface.Stub myBinder = new IDocAidlInterface.Stub() {

        @Override
        public void register( IDataChangedCallback callback){
             dataChangedCallback = callback;   
        }

        @Override
        public String basicIpcMethon(String method,String params) throws RemoteException {
            Log.i(TAG,"basicIpcMethon.....method........ "+method + ",params "+params);

            if(FileUtils.OPEN_FILE.equals(method)){
                finishDialog();
                // Intent intent = new Intent(Intent.ACTION_VIEW);
                // String path = "content://"+Providers.AUTHORITY_STORAGE+"/document/"+Providers.ROOT_ID_DESKTOP+"%2f"+params;
                // Uri uri = Uri.parse(path);
                // String mimeType = FileUtils.getMimeType(new File(Providers.PATH_ID_DESKTOP+params));
                // Log.i(TAG,"basicIpcMethon.....path "+path + ",mimeType "+mimeType);
                // if (mimeType == null) {
                //     if (params.contains(".txt") || params.contains(".json")  || params.contains(".md")) {
                //         intent.setDataAndType(uri, "text/plain");
                //     } else {
                //         intent.setDataAndType(uri, "application/*");
                //     }
                // } else if (mimeType.contains("image")) {
                //     intent.setDataAndType(uri, "image/*");
                // }else if(mimeType.contains("text") || mimeType.contains("plain") || mimeType.contains("json")){
                //     intent.setDataAndType(uri, "text/plain");
                // }else{
                //     intent.setDataAndType(uri, "application/*");
                // }
                // intent.putExtra("docTitle",params);
                // int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP;
                // flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                // flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
                // intent.setFlags(flags);
                // context.startActivity(intent);
            }else if(FileUtils.OPEN_DIR.equals(method)){
                finishDialog();
                //   Intent intent = new Intent();
                //   ComponentName componentName = new ComponentName( "com.android.documentsui", "com.android.documentsui.files.FilesActivity"  );
                //   intent.setComponent(componentName);
                //   intent.putExtra("getPath", FileUtils.PATH_ID_DESKTOP);
                //   intent.putExtra("childPath",params);
                //   int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP;
                //     flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                //     flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
                //     intent.setFlags(flags);
                //   startActivity(intent);

             try {
                // Intent intent = new Intent();
                // intent.setClass(context, FilesActivity.class);
                // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // intent.putExtra("childPath",params);
                SPUtils.putDocInfo(context,"getPath",FileUtils.PATH_ID_DESKTOP);
                // context.startActivity(intent);

             } catch (Exception e) {
                e.printStackTrace();
             }

               
            }else if(FileUtils.OPEN_LINUX_APP.equals(method)){
                String[] arrParams = params.split("###");
                String name = arrParams[0].trim().replaceAll("%[FfUu]", "");
                String exec = arrParams[1].trim().replaceAll("%[FfUu]", "");
                String type = arrParams[2];
                String fileName =  name+".desktop";
                if(arrParams.length > 3){
                    fileName = arrParams[3];
                }
                String path = "content://" + Providers.AUTHORITY_STORAGE + "/document/" + Providers.ROOT_ID_DESKTOP + "%2f" + fileName;
                if (!"open".equals(type)) {
                    Uri uri = Uri.parse(path);
                    Intent targetIntent = new Intent(Intent.ACTION_VIEW);
                    targetIntent.setDataAndType(uri, "application/vnd.desktop");
                    targetIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(targetIntent, "选择应用打开desktop文件");
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP;
                    flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
                    chooser.setFlags(flags);
                    context.startActivity(chooser);
                } else {
                    Log.i(TAG, "bellaDoc fileName: " + fileName + "   ,path : "+path);
                    Uri uri = Uri.parse(path);
                    Intent shareIntent = new Intent(Intent.ACTION_VIEW);
                    shareIntent.setDataAndType(uri, "application/vnd.desktop");
                    shareIntent.putExtra("fromOther", "Launcher");
                    shareIntent.putExtra("vnc_activity_name", name);
                    shareIntent.putExtra("App", name);
                    shareIntent.putExtra("openParams", params);
                    shareIntent.putExtra("docTitle", fileName);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_SINGLE_TOP;
                    flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    flags |= Intent.FLAG_ACTIVITY_NEW_TASK;
                    shareIntent.setFlags(flags);
                    startActivity(shareIntent);
                }
            } else if (FileUtils.DELETE_FILE.equals(method)) {
                // params 为文件实际路径：移至系统回收站（实现与
                // ActionHandler.trashSelectedDocuments() 相同的回收流程）
                FileUtils.trashPath(context,params);
                gotoClientApp("DELETE_FILE","");
            }else if(FileUtils.NEW_FILE.equals(method)){
                String fileName = FileUtils.newFile();
                gotoClientApp("NEW_FILE",fileName);
            }else if(FileUtils.NEW_DIR.equals(method)){
                String fileName = FileUtils.newDir();
                gotoClientApp("NEW_DIR",fileName);
            }else if(FileUtils.COPY_DIR.equals(method) || FileUtils.COPY_FILE.equals(method)){
                copyToClipboard(params);
            }else if(FileUtils.RENAME_FILE.equals(method) || FileUtils.RENAME_DIR.equals(method) ){
                try {

                    // Intent intent = new Intent();
                    // intent.setClass(context, RenameDialogActivity.class);
                    // intent.putExtra("oldFileName",params);
                    // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    // context.startActivity(intent);
                    String oldFileName = params.split("###")[0];
                    String newFileName = params.split("###")[1];
                    File file = new File(FileUtils.PATH_ID_DESKTOP + oldFileName);
                    file.renameTo(new File(FileUtils.PATH_ID_DESKTOP + newFileName));
                    gotoClientApp("RENAME",oldFileName +"###"+newFileName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else if(FileUtils.CUT_DIR.equals(method)  || FileUtils.CUT_FILE.equals(method)){
                cutToClipboard(params);
            }else if(FileUtils.PASTE_DIR.equals(method) || FileUtils.PASTE_FILE.equals(method)){
                pasteFromClipboard();
            }else if(FileUtils.FILE_LIST.equals(method)) {
            //    return FileUtils.getDesktopFiles();
            }else if(FileUtils.OP_INIT.equals(method)) {
                FileUtils.createDesktopDir();
            }else if(FileUtils.OP_CREATE_ANDROID_ICON.equals(method)) {
                FileUtils.createAllAndroidIconToLinux(context,params);
            }else if(FileUtils.OP_CREATE_LINUX_ICON.equals(method)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NetUtils.getLinuxApp();
                    }
                }).start();
            }else if(FileUtils.CLICK_BLANK.equals(method)){
                finishDialog();
            }
            return "1";
        }
    };

    /* ===================== 文件/文件夹的拷贝、剪切、粘贴 =====================
     * 拷贝/剪切通过 RuntimeDocumentClipper(DocumentClipper) 写入标准剪贴板。
     * 粘贴优先在 IpcService 进程内同步复制/移动本地文件（桌面复制、DocumentsUI 复制
     * 的 URI 都是 external-storage 的本地文件），复制完成后再通知 Launcher 刷新；
     * 仅当源无法解析时回退到 RuntimeDocumentClipper.copyFromClipboard() 交给
     * FileOperationService 异步执行。
     */
    private static final String CLIP_OP_TYPE_KEY = "clipper:opType";
    private static final String DOC_ROOT_ID_EXTERNAL = "primary:";
    private static final String AUTHORITY_STORAGE = "com.android.externalstorage.documents";
    private static final String DOC_ID_DESKTOP = "primary:Desktop";

    /**
     * 拷贝文件/文件夹到剪贴板。
     *
     * @param params 相对 Desktop 的文件名或子路径，例如 a.txt 或 dir/b.log
     */
    private void copyToClipboard(String params) {
        try {
            SPUtils.putDocInfo(context, FileUtils.FILE_OPERATE, FileUtils.OP_COPY);
            // 缓存源文件名，便于粘贴时在剪贴板不可读（Android 10+ 后台限制）时回退。
            SPUtils.putDocInfo(context, FileUtils.FILE_DESKTOP_NAME, params);

            Uri derivedUri = buildDesktopDocUri(params);
            MutableSelection<String> selection = new MutableSelection<>();
            selection.add(params);
            mClipper.clipDocumentsForCopy(id -> derivedUri, selection);
            Log.i(TAG, "copyToClipboard: " + derivedUri);
        } catch (Exception e) {
            Log.e(TAG, "copyToClipboard failed: " + params, e);
        }
    }

    /**
     * 剪切文件/文件夹到剪贴板（标记为移动操作）。
     *
     * @param params 相对 Desktop 的文件名或子路径
     */
    private void cutToClipboard(String params) {
        try {
            SPUtils.putDocInfo(context, FileUtils.FILE_OPERATE, FileUtils.OP_CUT);
            SPUtils.putDocInfo(context, FileUtils.FILE_DESKTOP_NAME, params);

            Uri derivedUri = buildDesktopDocUri(params);
            DocumentInfo parent = getParentDocumentInfo(params);
            MutableSelection<String> selection = new MutableSelection<>();
            selection.add(params);
            mClipper.clipDocumentsForCut(id -> derivedUri, selection, parent, false);
            Log.i(TAG, "cutToClipboard: " + derivedUri);
        } catch (Exception e) {
            Log.e(TAG, "cutToClipboard failed: " + params, e);
        }
    }

    /**
     * 粘贴剪贴板内容到 Desktop。
     *
     * 采用“同步本地复制/移动”解决两个根本问题：
     *   1) Android 8+ 禁止后台 startService()（Launcher 触发时 DocumentsUI 在后台，
     *      FileOperationService 无法启动）；
     *   2) FileOperations.start() 在复制完成前就同步回调 STATUS_ACCEPTED，导致
     *      Launcher 在文件真正出现前就已刷新。
     * 本地文件操作完成后才调用 gotoClientApp("PASTE")，保证新文件已就绪。
     */
    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            // 优先从系统剪贴板读取；Android 10+ 会隐藏后台应用对剪贴板的访问
            // （hasPrimaryClip() 返回 false），所以先从本地缓存准备一份备用数据。
            int opType = readPendingOpType();
            List<File> localSrcs = collectLocalFilesFromCache();

            ClipData clip = null;
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                clip = clipboard.getPrimaryClip();
            }
            if (clip != null && clip.getItemCount() > 0) {
                // 剪贴板可读，使用剪贴板中的真实数据（优先级高于本地缓存）。
                opType = readClipOpType(clip);
                localSrcs = collectLocalFiles(clip);
            }

            if (localSrcs != null && !localSrcs.isEmpty()) {
                final List<File> srcs = localSrcs;
                final int pasteOpType = opType;
                new Thread(() -> {
                    try {
                        pasteLocalFiles(srcs, pasteOpType);
                        if (pasteOpType == FileOperationService.OPERATION_MOVE) {
                            ClipboardManager cm =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.clearPrimaryClip();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "pasteFromClipboard local copy failed.", e);
                    } finally {
                        // 文件操作已经完成，此时通知 Launcher 刷新一定能看到结果。
                        gotoClientApp("PASTE");
                    }
                }, "paste-local").start();
                return;
            }

            if (clip != null && clip.getItemCount() > 0) {
                // 回退：剪贴板可读但源不是本地 external-storage 文件，走标准异步复制链路。
                DocumentStack dstStack = getDesktopDocumentStack();
                mClipper.copyFromClipboard(dstStack, (status, fOpType, docCount) -> {
                    Log.i(TAG, "pasteFromClipboard callback: status=" + status
                            + ", opType=" + fOpType + ", docCount=" + docCount);
                    gotoClientApp("PASTE");
                });
                return;
            }

            Log.w(TAG, "pasteFromClipboard: clipboard is empty.");
            gotoClientApp("PASTE");
        } catch (Exception e) {
            Log.e(TAG, "pasteFromClipboard failed", e);
            gotoClientApp("PASTE");
        }
    }

    /**
     * 从剪贴板描述中读取操作类型（COPY=1 / MOVE=4），默认 COPY。
     */
    private int readClipOpType(ClipData clip) {
        ClipDescription desc = clip.getDescription();
        PersistableBundle bundle = desc == null ? null : desc.getExtras();
        if (bundle != null) {
            return bundle.getInt(CLIP_OP_TYPE_KEY, FileOperationService.OPERATION_COPY);
        }
        return FileOperationService.OPERATION_COPY;
    }

    /**
     * 从本地缓存读取待粘贴的操作类型（COPY=1 / MOVE=4），默认 COPY。
     * 复制/剪切时已写入 {@link FileUtils#FILE_OPERATE}。
     */
    private int readPendingOpType() {
        String op = SPUtils.getDocInfo(context, FileUtils.FILE_OPERATE);
        return FileUtils.OP_CUT.equals(op) ? FileOperationService.OPERATION_MOVE
                : FileOperationService.OPERATION_COPY;
    }

    /**
     * 当系统剪贴板不可读或为空（例如 Android 10+ 后台应用被限制访问剪贴板）时，
     * 从本地缓存重建待粘贴的源文件。复制/剪切时源文件名已写入
     * {@link FileUtils#FILE_DESKTOP_NAME}。
     *
     * @return 源文件存在时返回单元素列表；无缓存或文件不存在时返回 null。
     */
    private List<File> collectLocalFilesFromCache() {
        try {
            String name = SPUtils.getDocInfo(context, FileUtils.FILE_DESKTOP_NAME);
            if (name == null || name.isEmpty()) {
                return null;
            }
            File src = new File(Environment.getExternalStorageDirectory(),
                    FileUtils.DESKTOP + "/" + name);
            if (!src.exists()) {
                return null;
            }
            List<File> result = new ArrayList<>();
            result.add(src);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "collectLocalFilesFromCache failed.", e);
            return null;
        }
    }

    /**
     * 将剪贴板中的所有项尝试解析为本地 external-storage 文件。
     *
     * @return 全部解析成功时返回文件列表；只要有任意一项无法解析则返回 null，
     *         由调用方整体回退到标准异步复制。
     */
    private List<File> collectLocalFiles(ClipData clip) {
        List<File> result = new ArrayList<>();
        for (int i = 0; i < clip.getItemCount(); i++) {
            File src = resolveLocalFile(clip.getItemAt(i).getUri());
            if (src == null) {
                return null;
            }
            result.add(src);
        }
        return result;
    }

    /**
     * 把 external-storage 的 document Uri 转换成真实文件。
     * 例如 content://com.android.externalstorage.documents/document/primary:Desktop/a.txt
     *       -> &lt;storage&gt;/Desktop/a.txt
     *
     * “primary:” 是 ExternalStorageProvider 主卷的 document id 前缀，可据此把 URI
     * 映射为 Environment.getExternalStorageDirectory() 下的真实路径。
     *
     * @return 无法解析或文件不存在时返回 null。
     */
    private File resolveLocalFile(Uri uri) {
        if (uri == null) {
            return null;
        }
        final String docId;
        try {
            docId = DocumentsContract.getDocumentId(uri);
        } catch (Exception e) {
            Log.w(TAG, "resolveLocalFile: cannot parse document id: " + uri, e);
            return null;
        }
        if (docId == null || !docId.startsWith(DOC_ROOT_ID_EXTERNAL)) {
            return null;
        }
        String rel = docId.substring(DOC_ROOT_ID_EXTERNAL.length());
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.isEmpty()) {
            return null;
        }
        File src = new File(Environment.getExternalStorageDirectory(), rel);
        return src.exists() ? src : null;
    }

    /**
     * 同步对一组本地文件执行复制/移动：源来自剪贴板，目标统一为 Desktop。
     * 目录递归复制；同名冲突自动追加 _1/_2… 后缀。
     */
    private void pasteLocalFiles(List<File> srcs, int opType) throws IOException {
        File desktopDir = new File(Environment.getExternalStorageDirectory(), FileUtils.DESKTOP);
        if (!desktopDir.exists() && !desktopDir.mkdirs()) {
            Log.e(TAG, "pasteLocalFiles: cannot create desktop dir " + desktopDir);
            return;
        }

        File desktopCanonical = desktopDir.getCanonicalFile();
        for (File src : srcs) {
            if (!src.exists()) {
                Log.w(TAG, "pasteLocalFiles: source not exists, skip " + src);
                continue;
            }
            File dst = destinationFor(desktopDir, src);
            if (opType == FileOperationService.OPERATION_MOVE) {
                File srcParent = src.getCanonicalFile().getParentFile();
                if (srcParent != null && srcParent.equals(desktopCanonical)) {
                    // 同一目录内剪切+粘贴：视为无操作
                    continue;
                }
                if (!src.renameTo(dst)) {
                    // 跨文件系统 rename 失败时回退为复制后删除
                    copyFileOrDir(src, dst);
                    FileUtils.deleteFiles(src.getAbsolutePath());
                }
            } else {
                copyFileOrDir(src, dst);
            }
            Log.i(TAG, "pasteLocalFiles " + src + " -> " + dst);
        }
    }

    /**
     * 在 Desktop 目录下为源文件生成不冲突的目标路径。
     */
    private File destinationFor(File desktopDir, File src) {
        File dst = new File(desktopDir, src.getName());
        if (!dst.exists()) {
            return dst;
        }
        int dot = src.isDirectory() ? -1 : src.getName().lastIndexOf('.');
        String base = dot > 0 ? src.getName().substring(0, dot) : src.getName();
        String ext = dot > 0 ? src.getName().substring(dot) : "";
        int i = 1;
        do {
            dst = new File(desktopDir, base + "_" + (i++) + ext);
        } while (dst.exists());
        return dst;
    }

    /**
     * 复制文件或递归复制目录到目标路径。
     */
    private void copyFileOrDir(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            copyRecursive(src, dst);
        } else {
            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
        }
    }

    private void copyRecursive(File src, File dst) throws IOException {
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
            copyFileOrDir(src, dst);
        }
    }

    /**
     * 构建 Desktop 下文件/文件夹的 document Uri。
     *
     * @param params 相对 Desktop 的名称或子路径
     */
    private Uri buildDesktopDocUri(String params) {
        try {
            String docId = FileUtils.getStorageDocIdFromPath(params);
            if (docId != null) {
                return DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, docId);
            }
        } catch (Exception e) {
            Log.w(TAG, "buildDesktopDocUri fallback for " + params, e);
        }
        return DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, DOC_ID_DESKTOP + "/" + params);
    }

    /**
     * 获取 Desktop 文件夹对应的 DocumentInfo（粘贴目的目录）。
     */
    private DocumentInfo getDesktopDocumentInfo() {
        Uri desktopUri = DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, DOC_ID_DESKTOP);
        DocumentInfo desktop = new DocumentInfo();
        desktop.authority = AUTHORITY_STORAGE;
        desktop.documentId = DOC_ID_DESKTOP;
        desktop.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        desktop.flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        desktop.userId = UserId.DEFAULT_USER;
        desktop.derivedUri = desktopUri;
        try {
            // 优先用 provider 的真实信息（包含目录/创建等能力位）
            return DocumentInfo.fromUri(context.getContentResolver(), desktopUri, UserId.DEFAULT_USER);
        } catch (Exception e) {
            Log.w(TAG, "getDesktopDocumentInfo query failed, using minimal fallback.", e);
            return desktop;
        }
    }

    /**
     * 获取被剪切的文件/文件夹的真实父目录 DocumentInfo，用于记录剪切来源，
     * 以便“同一目录内剪切+粘贴”被正确判定为无操作。
     *
     * @param params 相对 Desktop 的名称或子路径，例如 a.txt 或 dir/b.log
     */
    private DocumentInfo getParentDocumentInfo(String params) {
        String parentDocId = DOC_ID_DESKTOP; // primary:Desktop
        int slash = params == null ? -1 : params.lastIndexOf('/');
        if (slash >= 0) {
            parentDocId = DOC_ID_DESKTOP + "/" + params.substring(0, slash);
        }
        Uri parentUri = DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, parentDocId);
        DocumentInfo parent = new DocumentInfo();
        parent.authority = AUTHORITY_STORAGE;
        parent.documentId = parentDocId;
        parent.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        parent.derivedUri = parentUri;
        try {
            return DocumentInfo.fromUri(context.getContentResolver(), parentUri, UserId.DEFAULT_USER);
        } catch (Exception e) {
            Log.w(TAG, "getParentDocumentInfo query failed for " + parentDocId
                    + ", using minimal fallback.", e);
            return parent;
        }
    }

    /**
     * 以 Desktop 作为粘贴目的目录构造 DocumentStack。
     */
    private DocumentStack getDesktopDocumentStack() {
        return new DocumentStack((RootInfo) null, getDesktopDocumentInfo());
    }

    public  void gotoClientApp(String params){
       try {
        dataChangedCallback.onCallback(params);
       } catch (Exception e) {
        e.printStackTrace();
       }
    }

    public  void gotoClientApp(String method,String params){
        try {
         dataChangedCallback.onCallbackString(method,params);
        } catch (Exception e) {
         e.printStackTrace();
        }
     }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public void finishDialog(){
        Activity activity = DocumentsApplication.getInstance().getCurrentActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    public  boolean isActivityRunning(Context context, Class<?> activityClass) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);
    
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (activityClass.getName().equals(task.baseActivity.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } 
        return false;
    }
}