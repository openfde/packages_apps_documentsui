package com.android.documentsui;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.content.ComponentName;


import androidx.recyclerview.selection.MutableSelection;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.AppsRowItemData.AppData;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.provider.FileUtils;
import com.android.documentsui.util.NetUtils;
import com.android.documentsui.util.SPUtils;

import java.io.File;
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
     * 拷贝/剪切通过 RuntimeDocumentClipper(DocumentClipper) 写入标准剪贴板，
     * 粘贴通过 RuntimeDocumentClipper.copyFromClipboard() 交给 FileOperationService
     * 异步执行真正的文件操作，完成后会清除剪贴板（剪切/移动操作）。
     */
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
            SPUtils.putDocInfo(context, FileUtils.FILE_DESKTOP_NAME, "");

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
     * 把剪贴板中的内容粘贴（拷贝/移动）到 Desktop，由 FileOperationService 异步执行。
     */
    private void pasteFromClipboard() {
        try {
            DocumentStack dstStack = getDesktopDocumentStack();
            mClipper.copyFromClipboard(dstStack, (status, opType, docCount) -> {
                Log.i(TAG, "pasteFromClipboard callback: status=" + status
                        + ", opType=" + opType + ", docCount=" + docCount);
                // 通知桌面端刷新（实际操作由 FileOperationService 异步完成）
                gotoClientApp("PASTE");
            });
        } catch (Exception e) {
            Log.e(TAG, "pasteFromClipboard failed", e);
            gotoClientApp("PASTE");
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