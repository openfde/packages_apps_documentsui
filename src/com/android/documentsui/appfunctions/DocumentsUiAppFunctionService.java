/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui.appfunctions;

import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionService;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appsearch.GenericDocument;
import android.content.ContentResolver;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.MessageEvent;
import com.android.documentsui.base.UserId;
import com.android.documentsui.provider.FileUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.greenrobot.event.EventBus;

/**
 * 通过 App Functions 暴露文件管理器（DocumentsUI）的常用文件操作能力，供系统智能体（Agent）
 * 或其他调用方使用 {@link android.app.appfunctions.AppFunctionManager#executeAppFunction} 调用。
 *
 * <p>支持的功能：
 * <ul>
 *     <li>{@code deleteFile}：参数 {@code path}（必填）——删除（移入系统回收站）某个文件或文件夹，
 *         可在回收站页面恢复。</li>
 *     <li>{@code moveFiles}：参数 {@code sourcePath}（必填，源文件夹）、{@code destinationPath}
 *         （必填，目标目录）——把源文件夹下的所有文件/子文件夹移动到目标目录。</li>
 *     <li>{@code renameFolder}：参数 {@code path}（必填）、{@code newName}（必填）——重命名文件夹
 *         （对文件同样生效）。</li>
 *     <li>{@code emptyTrash}：清空回收站，永久删除回收站中的全部内容。</li>
 * </ul>
 *
 * <p>路径参数支持绝对路径（如 {@code /storage/emulated/0/Desktop/a.txt}），也兼容相对桌面
 * （Desktop）的名称（如 {@code a.txt}，等价于 {@code /storage/emulated/0/Desktop/a.txt}），
 * 与 {@link com.android.documentsui.IpcService} 的既有约定保持一致。
 *
 * <p>功能列表在 {@code assets/appfunctions.xml} 中声明，AndroidManifest 中通过
 * {@code <property android:name="android.app.appfunctions" android:value="appfunctions.xml"/>}
 * 关联。
 */
public class DocumentsUiAppFunctionService extends AppFunctionService {
    private static final String TAG = "DocumentsUiAppFunctionService";

    public static final String FUNCTION_DELETE_FILE = "deleteFile";
    public static final String FUNCTION_MOVE_FILES = "moveFiles";
    public static final String FUNCTION_RENAME_FOLDER = "renameFolder";
    public static final String FUNCTION_EMPTY_TRASH = "emptyTrash";

    /** 参数名（调用方通过 ExecuteAppFunctionRequest 的参数 GenericDocument 传入）。 */
    private static final String PARAM_PATH = "path";
    private static final String PARAM_SOURCE_PATH = "sourcePath";
    private static final String PARAM_DESTINATION_PATH = "destinationPath";
    private static final String PARAM_NEW_NAME = "newName";

    /** 统一的结果 schema 与字段。 */
    private static final String RESULT_SCHEMA = "FileOpResult";
    private static final String RESULT_OK = "ok";
    private static final String RESULT_STATUS = "status";
    private static final String RESULT_PATH = "path";
    private static final String RESULT_NEW_PATH = "newPath";
    private static final String RESULT_MOVED_COUNT = "movedCount";
    private static final String RESULT_DELETED_COUNT = "deletedCount";

    /** onExecuteFunction 运行在主线程，文件操作属于耗时/IO 操作，放到后台线程执行。 */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    @Override
    public void onExecuteFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull String callingPackage,
            @NonNull SigningInfo callingPackageSigningInfo,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback) {
        String functionId = request.getFunctionIdentifier();
        Log.d(TAG, "onExecuteFunction: " + functionId + " from " + callingPackage);

        final boolean[] cancelled = {false};
        cancellationSignal.setOnCancelListener(() -> cancelled[0] = true);

        final GenericDocument parameters = request.getParameters();
        EXECUTOR.execute(() -> {
            try {
                if (cancelled[0]) {
                    callback.onError(new AppFunctionException(
                            AppFunctionException.ERROR_CANCELLED, "Execution cancelled"));
                    return;
                }
                switch (String.valueOf(functionId)) {
                    case FUNCTION_DELETE_FILE:
                        deleteFile(parameters, cancelled, callback);
                        break;
                    case FUNCTION_MOVE_FILES:
                        moveFiles(parameters, cancelled, callback);
                        break;
                    case FUNCTION_RENAME_FOLDER:
                        renameFolder(parameters, callback);
                        break;
                    case FUNCTION_EMPTY_TRASH:
                        emptyTrash(parameters, cancelled, callback);
                        break;
                    default:
                        callback.onError(new AppFunctionException(
                                AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                                "Unknown function: " + functionId));
                }
            } catch (AppFunctionException e) {
                Log.e(TAG, "onExecuteFunction failed for " + functionId, e);
                callback.onError(e);
            } catch (Exception e) {
                Log.e(TAG, "onExecuteFunction failed for " + functionId, e);
                callback.onError(new AppFunctionException(
                        AppFunctionException.ERROR_SYSTEM_ERROR,
                        "Failed to execute function: " + e.getMessage()));
            }
        });
    }

    /**
     * 删除（移入回收站）某个文件或文件夹。
     *
     * <p>与项目既有的"删除即移入回收站"行为保持一致，删除后可在回收站页面恢复。回收流程复用
     * {@link FileUtils#trashPathSync(Context, String)}，通过
     * {@link DocumentsContract#trashDocument} 同步完成。
     */
    private void deleteFile(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (cancelled[0]) {
            throw new AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
        }
        File file = resolveFile(path);

        // 未找到目标文件/文件夹时直接报错，避免对不存在的路径做无意义操作。
        if (!file.exists()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "File not found: " + path);
        }

        boolean ok = FileUtils.trashPathSync(getApplicationContext(), file.getAbsolutePath());
        if (!ok) {
            throw new AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR,
                    "Failed to delete/move to trash: " + path);
        }

        GenericDocument resultDocument = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_DELETE_FILE + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyString(RESULT_PATH, file.getAbsolutePath())
                .build();
        callback.onResult(new ExecuteAppFunctionResponse(resultDocument));
    }

    /**
     * 把某个文件夹下的所有文件/子文件夹移动到某个目标目录。
     *
     * <p>仅移动源文件夹的子项本身（不合并子目录内容）。目标目录不存在时自动创建；
     * 目标目录里已有同名项时自动生成唯一名称（如 {@code a.txt_1.txt}）避免覆盖。
     * 返回实际移动成功的项数 {@code movedCount}。
     */
    private void moveFiles(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String sourcePath = parameters == null
                ? null : parameters.getPropertyString(PARAM_SOURCE_PATH);
        String destinationPath = parameters == null
                ? null : parameters.getPropertyString(PARAM_DESTINATION_PATH);
        if (TextUtils.isEmpty(sourcePath)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_SOURCE_PATH);
        }
        if (TextUtils.isEmpty(destinationPath)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_DESTINATION_PATH);
        }

        File sourceDir = resolveFile(sourcePath);
        File destinationDir = resolveFile(destinationPath);

        if (!sourceDir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Source is not a directory: " + sourcePath);
        }
        if (cancelled[0]) {
            throw new AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
        }
        // 目标必须是目录。
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR,
                    "Failed to create destination directory: " + destinationPath);
        }
        if (!destinationDir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Destination is not a directory: " + destinationPath);
        }
        // 防止把源文件夹移动到它自己内部（导致死循环/自引用）。
        if (isSameOrDescendant(destinationDir, sourceDir)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Destination directory cannot be inside source directory: " + destinationPath);
        }

        int moved = 0;
        File[] children = sourceDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (cancelled[0]) {
                    throw new AppFunctionException(
                            AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
                }
                File target = new File(destinationDir,
                        uniqueName(destinationDir, child.getName()));
                if (child.renameTo(target)) {
                    moved++;
                } else {
                    Log.w(TAG, "moveFiles: failed to move " + child.getAbsolutePath()
                            + " to " + target.getAbsolutePath());
                }
            }
        }

        // 桌面/文件列表刷新。
        EventBus.getDefault().post(new MessageEvent("", "UPDATE"));

        GenericDocument resultDocument = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_MOVE_FILES + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyLong(RESULT_MOVED_COUNT, moved)
                .build();
        callback.onResult(new ExecuteAppFunctionResponse(resultDocument));
    }

    /**
     * 重命名某个文件夹（对普通文件同样生效）。
     *
     * <p>新名称不能为空、不能含路径分隔符；目标位置如果已存在同名项则报错。返回重命名后的
     * 新路径 {@code newPath}。
     */
    private void renameFolder(GenericDocument parameters,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        String newName = parameters == null ? null : parameters.getPropertyString(PARAM_NEW_NAME);

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (TextUtils.isEmpty(newName)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_NEW_NAME);
        }
        if (!isValidFileName(newName)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Invalid new name: " + newName);
        }

        File sourceFile = resolveFile(path);
        if (!sourceFile.exists()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "File not found: " + path);
        }
        File parent = sourceFile.getParentFile();
        if (parent == null) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Cannot rename root: " + path);
        }
        File destFile = new File(parent, newName);
        if (destFile.exists()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Destination already exists: " + destFile.getAbsolutePath());
        }
        if (!sourceFile.renameTo(destFile)) {
            throw new AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR,
                    "Failed to rename: " + path + " -> " + newName);
        }

        EventBus.getDefault().post(new MessageEvent("", "UPDATE"));

        GenericDocument resultDocument = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_RENAME_FOLDER + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyString(RESULT_PATH, path)
                .setPropertyString(RESULT_NEW_PATH, destFile.getAbsolutePath())
                .build();
        callback.onResult(new ExecuteAppFunctionResponse(resultDocument));
    }

    /**
     * 清空回收站：永久删除回收站目录（{@link FileUtils#PATH_TRASH}）中的全部内容，并重置
     * 回收站数量标记、通知回收站页面/文件列表刷新。
     *
     * <p>实现参考 {@link com.android.documentsui.files.EmptyTrashActivity#emptyTrashAndFinish()}，
     * 返回实际删除的项数 {@code deletedCount}。
     */
    private void emptyTrash(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        File trashDir = new File(FileUtils.PATH_TRASH);

        int deleted = 0;
        File[] files = trashDir.isDirectory() ? trashDir.listFiles() : null;
        if (files != null) {
            deleted = files.length;
        }

        if (cancelled[0]) {
            throw new AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
        }

        try {
            FileUtils.clearTrashFolder(FileUtils.PATH_TRASH);
        } catch (Exception e) {
            Log.e(TAG, "emptyTrash: failed to clear trash folder", e);
            throw new AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR,
                    "Failed to clear trash: " + e.getMessage());
        }

        // 重置回收站数量标记（UI 依赖 trash_storage 显示角标）。失败不阻断主流程。
        try {
            Settings.System.putInt(getContentResolver(), "trash_storage", 0);
        } catch (Exception e) {
            Log.w(TAG, "emptyTrash: failed to reset trash_storage", e);
        }

        // 通知回收站页面/文件列表刷新。
        notifyTrashContentChanged();
        EventBus.getDefault().post(new MessageEvent("", "UPDATE"));

        GenericDocument resultDocument = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_EMPTY_TRASH + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyLong(RESULT_DELETED_COUNT, deleted)
                .build();
        callback.onResult(new ExecuteAppFunctionResponse(resultDocument));
    }

    /**
     * 通知所有支持查询回收站的 provider 的回收站文档 Uri 发生变化，让回收站页面及时刷新。
     * 参考 {@link com.android.documentsui.files.EmptyTrashActivity#notifyTrashContentChanged()}。
     */
    private void notifyTrashContentChanged() {
        try {
            ContentResolver resolver = getContentResolver();
            DocumentsApplication.getProvidersCache(this)
                    .getRootsBlocking()
                    .forEach(root -> {
                        if (!root.supportsQueryTrash() || root.userId != UserId.CURRENT_USER) {
                            return;
                        }
                        Uri trashUri = DocumentsContract.buildTrashDocumentsUri(
                                root.authority, root.rootId);
                        resolver.notifyChange(trashUri, null, false);
                    });
        } catch (Exception e) {
            Log.w(TAG, "emptyTrash: failed to notify trash content change", e);
        }
    }

    /**
     * 解析路径：绝对路径原样返回；相对路径按项目既有约定视为相对桌面（Desktop）目录。
     */
    private static File resolveFile(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(FileUtils.PATH_ID_DESKTOP, path);
        }
        return file;
    }

    /**
     * 在指定目录下为该文件名生成不冲突的唯一名称（如 {@code a.txt_1.txt}）。
     */
    private static String uniqueName(File parentDir, String fileName) {
        File target = new File(parentDir, fileName);
        if (!target.exists()) {
            return fileName;
        }
        String base = fileName;
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            base = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        }
        int count = 0;
        String candidate;
        do {
            count++;
            candidate = base + "_" + count + extension;
        } while (new File(parentDir, candidate).exists());
        return candidate;
    }

    /**
     * 判断 {@code ancestor} 是否是 {@code descendant} 本身或其祖先目录。
     */
    private static boolean isSameOrDescendant(File ancestor, File descendant) {
        String ancestorPath;
        String descendantPath;
        try {
            ancestorPath = ancestor.getCanonicalPath();
            descendantPath = descendant.getCanonicalPath();
        } catch (Exception e) {
            return false;
        }
        return ancestorPath.equals(descendantPath)
                || descendantPath.startsWith(ancestorPath + File.separator);
    }

    /**
     * 校验新文件名合法性：非空、不以分隔符结尾、不含路径分隔符。
     */
    private static boolean isValidFileName(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        return !name.contains("/") && !name.contains("\\") && !name.equals(".") && !name.equals("..");
    }
}
