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
import android.content.Intent;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.MessageEvent;
import com.android.documentsui.base.UserId;
import com.android.documentsui.provider.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import de.greenrobot.event.EventBus;

/**
 * 通过 App Functions 暴露文件管理器（DocumentsUI）的常用文件操作能力，供系统智能体（Agent）
 * 或其他调用方使用 {@link android.app.appfunctions.AppFunctionManager#executeAppFunction} 调用。
 *
 * <p>支持的功能：
 * <ul>
 *     <li>{@code deleteFile}：删除（移入系统回收站）某个文件或文件夹，可在回收站页面恢复。
 *         单目标模式参数 {@code path}（必填）；批量模式参数 {@code folder}（必填）+ 至少一个
 *         过滤条件，扫描该文件夹**直接子项**并按条件删除：
 *         <ul>
 *             <li>{@code namePrefix}（可选）：仅删除名称以该前缀开头的子项；</li>
 *             <li>{@code nameSuffix}（可选）：仅删除名称以该后缀结尾的子项；</li>
 *             <li>{@code ageMonths}（可选，Long）：仅删除修改时间早于 N 个月前的子项
 *                 （即“几个月前的文件”）；</li>
 *             <li>{@code beforeDate}（可选）：仅删除修改时间早于该时间点的子项，支持
 *                 {@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss} 或 epoch 毫秒。</li>
 *         </ul></li>
 *     <li>{@code moveFiles}：参数 {@code sourcePath}（必填，源文件夹）、{@code destinationPath}
 *         （必填，目标目录）——把源文件夹下的所有文件/子文件夹移动到目标目录。</li>
 *     <li>{@code renameFolder}：参数 {@code path}（必填）、{@code newName}（必填）——重命名文件夹
 *         （对文件同样生效）。</li>
 *     <li>{@code emptyTrash}：清空回收站，永久删除回收站中的全部内容。</li>
 *     <li>{@code findFiles}：参数 {@code folder}（必填）+ 可选过滤条件——只读查找该文件夹
 *         **直接子项**，返回匹配的文件路径列表。过滤条件：
 *         <ul>
 *             <li>{@code namePrefix} / {@code nameSuffix}（可选）：按名称前缀/后缀匹配；</li>
 *             <li>{@code lastMonths}（可选，Long）：仅返回最近 N 个月内有修改的子项
 *                 （例如查找“最近三个月的文件”）；</li>
 *             <li>{@code beforeDate}（可选）：仅返回修改时间早于该时间点的子项；
 *                 {@code afterDate}（可选）：仅返回修改时间晚于该时间点的子项。二者都支持
 *                 {@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss} 或 epoch 毫秒。</li>
 *             <li>{@code limit}（可选，Long）：最多返回条数。</li>
 *         </ul></li>
 *     <li>{@code deleteFilesByName}：参数 {@code path}（必填）、{@code prefix} /
 *         {@code suffix}（至少一个）、{@code recursive}（可选，缺省 true）——删除（移入回收站）
 *         文件夹下以指定前缀开头 / 后缀结尾的所有文件。支持递归扫描子文件夹。</li>
 *     <li>{@code deleteFilesByTime}：参数 {@code path}（必填）、{@code before} /
 *         {@code after}（至少一个，epoch 毫秒）、{@code recursive}（可选，缺省 true）——删除
 *         （移入回收站）最后修改时间在某个时间点之前 / 之后的所有文件。</li>
 *     <li>{@code findLargeFiles}：参数 {@code path}（必填）、{@code minSize}（可选，字节，
 *         缺省 100MB）、{@code recursive}（可选）、{@code limit}（可选，缺省 50）——递归找出
 *         文件夹下的大文件，按大小降序返回，结果含 paths / sizes / lastModified。</li>
 *     <li>{@code findUnusedFiles}：参数 {@code path}（必填）、{@code days}（可选，缺省 30）、
 *         {@code limit}（可选，缺省 50）——递归找出 {days} 天内未被访问/修改的不常用文件，
 *         按最久未使用在前排序，结果含 paths / lastModified / lastAccessed。</li>
 *     <li>{@code deleteFiles}：参数 {@code paths}（必填，路径数组，通常是上述查找功能返回的
 *         结果；也支持单个 {@code path} 字符串）——删除这些显式列出的文件。</li>
 * </ul>
 *
 * <p>所有删除类功能（deleteFile / deleteFilesByName / deleteFilesByTime / deleteFiles）都支持
 * 可选参数 {@code requireConfirmation}：缺省 true —— 删除前会弹出对话框列出待删除文件，用户可
 * 逐项取消勾选想要保留的文件，确认后才真正移入回收站（移入后可在回收站页面恢复）；传 false 则
 * 直接删除、不弹窗。
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

    /** 功能 ID：按过滤条件查找文件夹下的文件（只读），与 assets/appfunctions.xml 保持一致。 */
    public static final String FUNCTION_FIND_FILES = "findFiles";
    public static final String FUNCTION_DELETE_FILES_BY_NAME = "deleteFilesByName";
    public static final String FUNCTION_DELETE_FILES_BY_TIME = "deleteFilesByTime";
    public static final String FUNCTION_FIND_LARGE_FILES = "findLargeFiles";
    public static final String FUNCTION_FIND_UNUSED_FILES = "findUnusedFiles";
    /** 功能 ID：删除显式指定路径列表的文件（通常是某次查找的结果），支持逐项弹窗确认。 */
    public static final String FUNCTION_DELETE_FILES = "deleteFiles";

    /** 参数名（调用方通过 ExecuteAppFunctionRequest 的参数 GenericDocument 传入）。 */
    private static final String PARAM_PATH = "path";
    private static final String PARAM_SOURCE_PATH = "sourcePath";
    private static final String PARAM_DESTINATION_PATH = "destinationPath";
    private static final String PARAM_NEW_NAME = "newName";
    private static final String PARAM_FOLDER = "folder";
    private static final String PARAM_NAME_PREFIX = "namePrefix";
    private static final String PARAM_NAME_SUFFIX = "nameSuffix";
    /** 删除过滤：修改时间早于 N 个月前的文件（“几个月前的文件”）。 */
    private static final String PARAM_AGE_MONTHS = "ageMonths";
    /** 查找过滤：最近 N 个月内有修改的文件（“最近三个月的文件”）。 */
    private static final String PARAM_LAST_MONTHS = "lastMonths";
    /** 删除/查找过滤：修改时间早于某个时间点（支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）。 */
    private static final String PARAM_BEFORE_DATE = "beforeDate";
    /** 查找最多返回条数。 */
    private static final String PARAM_LIMIT = "limit";
    /** 按名称批量删除：名称前缀 / 后缀。 */
    private static final String PARAM_PREFIX = "prefix";
    private static final String PARAM_SUFFIX = "suffix";
    /** 按时间批量删除：最后修改时间早于 / 晚于某个时间点（epoch 毫秒）。 */
    private static final String PARAM_BEFORE = "before";
    private static final String PARAM_AFTER = "after";
    /** 是否递归扫描子文件夹（deleteFilesByName / deleteFilesByTime / findLargeFiles）。 */
    private static final String PARAM_RECURSIVE = "recursive";
    /** findLargeFiles 最小文件大小（字节）。 */
    private static final String PARAM_MIN_SIZE = "minSize";
    /** findUnusedFiles：N 天内未访问/未修改视为不常用。 */
    private static final String PARAM_DAYS = "days";
    /** findFiles 过滤：修改时间晚于某个时间点（与 beforeDate 同格式：yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）。 */
    private static final String PARAM_AFTER_DATE = "afterDate";
    /** deleteFiles：待删除的文件路径列表（即某次查找的结果），支持绝对路径或相对桌面名称。 */
    private static final String PARAM_PATHS = "paths";
    /** 删除类功能（deleteFiles / deleteFile / deleteFilesByName / deleteFilesByTime）是否弹窗列
     *  出待删除文件让用户逐项确认。缺省 true（安全起见），传 false 直接删除不弹窗。 */
    private static final String PARAM_REQUIRE_CONFIRMATION = "requireConfirmation";
    /** 删除结果：用户是否在确认弹窗中取消。 */
    private static final String RESULT_CANCELLED = "cancelled";
    /** 等待用户在确认弹窗中做决定的最长时间（毫秒），超时视为取消。 */
    private static final long CONFIRM_TIMEOUT_MS = 10L * 60L * 1000L; // 10 分钟

    /** 统一的结果 schema 与字段。 */
    private static final String RESULT_SCHEMA = "FileOpResult";
    private static final String RESULT_SCHEMA_FIND = "FilesFindResult";
    private static final String RESULT_OK = "ok";
    private static final String RESULT_STATUS = "status";
    private static final String RESULT_PATH = "path";
    private static final String RESULT_NEW_PATH = "newPath";
    private static final String RESULT_MOVED_COUNT = "movedCount";
    private static final String RESULT_DELETED_COUNT = "deletedCount";
    private static final String RESULT_COUNT = "count";
    private static final String RESULT_MATCHES = "matches";
    /** 文件列表类结果（count / paths / sizes / lastModified / lastAccessed）。 */
    private static final String RESULT_SCHEMA_LIST = "FileListResult";
    private static final String RESULT_PATHS = "paths";
    private static final String RESULT_SIZES = "sizes";
    private static final String RESULT_LAST_MODIFIED = "lastModified";
    private static final String RESULT_LAST_ACCESSED = "lastAccessed";

    /** findLargeFiles 缺省最小文件大小：100MB。 */
    private static final long DEFAULT_MIN_SIZE = 100L * 1024L * 1024L;
    /** findLargeFiles / findUnusedFiles 缺省返回数量上限。 */
    private static final int DEFAULT_LIMIT = 50;
    /** findUnusedFiles 缺省天数：30 天未访问/未修改视为不常用。 */
    private static final int DEFAULT_UNUSED_DAYS = 30;
    /** 批量删除结果文档中携带的路径数量上限（避免结果文档过大），不影响实际删除数量。 */
    private static final int MAX_RESULT_PATHS = 200;

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
                    case FUNCTION_FIND_FILES:
                        findFiles(parameters, callback);
                        break;
                    case FUNCTION_DELETE_FILES_BY_NAME:
                        deleteFilesByName(parameters, cancelled, callback);
                        break;
                    case FUNCTION_DELETE_FILES_BY_TIME:
                        deleteFilesByTime(parameters, cancelled, callback);
                        break;
                    case FUNCTION_FIND_LARGE_FILES:
                        findLargeFiles(parameters, cancelled, callback);
                        break;
                    case FUNCTION_FIND_UNUSED_FILES:
                        findUnusedFiles(parameters, cancelled, callback);
                        break;
                    case FUNCTION_DELETE_FILES:
                        deleteFiles(parameters, cancelled, callback);
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
     *
     * <p>两种模式：
     * <ul>
     *     <li>单目标模式：参数 {@code path}（必填）——删除（移入回收站）指定的文件/文件夹；</li>
     *     <li>批量模式：参数 {@code folder}（必填，扫描其直接子项）+ 至少一个过滤条件，删除
     *         匹配的子项。过滤条件：{@code namePrefix} / {@code nameSuffix} /
     *         {@code ageMonths}（修改时间早于 N 个月前的文件）/ {@code beforeDate}
     *         （修改时间早于某个时间点，支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）。</li>
     * </ul>
     */
    private void deleteFile(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        String folder = parameters == null ? null : parameters.getPropertyString(PARAM_FOLDER);

        // 批量模式：给定 folder（+ 至少一个过滤条件）时，删除该文件夹下匹配的直接子项。
        if (!TextUtils.isEmpty(folder)) {
            deleteFiltered(parameters, cancelled, callback);
            return;
        }

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH
                            + "（或提供 " + PARAM_FOLDER + " 做批量删除）");
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
     * 批量删除：扫描 {@code folder} 的**直接子项**，按过滤条件（名称前缀/后缀、修改时间）
     * 把每个匹配项删除（移入回收站）。返回实际删除的项数 {@code deletedCount}。
     *
     * <p>过滤条件至少提供一个，否则拒绝执行，避免误删整个文件夹。
     */
    private void deleteFiltered(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String folder = parameters == null ? null : parameters.getPropertyString(PARAM_FOLDER);
        String prefix = parameters == null ? null : parameters.getPropertyString(PARAM_NAME_PREFIX);
        String suffix = parameters == null ? null : parameters.getPropertyString(PARAM_NAME_SUFFIX);
        Long ageMonths = optionalLong(parameters, PARAM_AGE_MONTHS);
        String beforeDate = parameters == null ? null : parameters.getPropertyString(PARAM_BEFORE_DATE);

        boolean hasFilter = !TextUtils.isEmpty(prefix)
                || !TextUtils.isEmpty(suffix)
                || (ageMonths != null && ageMonths > 0)
                || !TextUtils.isEmpty(beforeDate);
        if (!hasFilter) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "deleteFile with " + PARAM_FOLDER + " requires at least one filter: "
                            + PARAM_NAME_PREFIX + " / " + PARAM_NAME_SUFFIX + " / "
                            + PARAM_AGE_MONTHS + " / " + PARAM_BEFORE_DATE);
        }

        File dir = resolveFile(folder);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Folder not found or not a directory: " + folder);
        }

        List<File> matches = filterChildren(dir, prefix, suffix, ageMonths, beforeDate);

        // 弹窗确认开关：缺省 true（安全起见），调用方可传 requireConfirmation=false 直接删除。
        boolean requireConfirmation = getPropertyBool(
                parameters, PARAM_REQUIRE_CONFIRMATION, true);
        DeleteOutcome outcome = deleteFilesInternal(matches, requireConfirmation, cancelled);
        if (!outcome.cancelled && outcome.deletedCount > 0) {
            EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        }

        GenericDocument.Builder<?> builder = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_DELETE_FILE + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyString(RESULT_PATH, dir.getAbsolutePath())
                .setPropertyBoolean(RESULT_CANCELLED, outcome.cancelled)
                .setPropertyLong(RESULT_DELETED_COUNT, outcome.deletedCount);
        callback.onResult(new ExecuteAppFunctionResponse(builder.build()));
    }

    /**
     * 只读查找 {@code folder} 的**直接子项**，按过滤条件（名称前缀/后缀、最近 N 个月修改、
     * 早于/晚于某个时间点）返回匹配的文件路径列表。
     *
     * <p>参数：{@code folder}（必填）；{@code namePrefix} / {@code nameSuffix}（可选）；
     * {@code beforeDate}（可选，修改时间早于该时间点）与 {@code afterDate}（可选，修改时间
     * 晚于该时间点），二者都支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒；
     * {@code lastMonths}（可选，最近 N 个月内有修改，等价于 afterDate="N 个月前"）；
     * {@code limit}（可选，最多返回条数，按修改时间倒序排列取前 limit 条）。
     */
    private void findFiles(GenericDocument parameters,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String folder = parameters == null ? null : parameters.getPropertyString(PARAM_FOLDER);
        if (TextUtils.isEmpty(folder)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_FOLDER);
        }

        File dir = resolveFile(folder);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Folder not found or not a directory: " + folder);
        }

        String prefix = parameters == null ? null : parameters.getPropertyString(PARAM_NAME_PREFIX);
        String suffix = parameters == null ? null : parameters.getPropertyString(PARAM_NAME_SUFFIX);
        Long lastMonths = optionalLong(parameters, PARAM_LAST_MONTHS);
        String beforeDate = parameters == null ? null : parameters.getPropertyString(PARAM_BEFORE_DATE);
        String afterDate = parameters == null ? null : parameters.getPropertyString(PARAM_AFTER_DATE);
        Long limit = optionalLong(parameters, PARAM_LIMIT);

        // 时间下限（修改时间晚于该点）：显式 afterDate 优先，其次取 lastMonths（最近 N 个月）。
        Long afterMillis = null;
        if (!TextUtils.isEmpty(afterDate)) {
            afterMillis = parseTimeMillis(afterDate);
            if (afterMillis == null) {
                throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                        "Cannot parse " + PARAM_AFTER_DATE + ": " + afterDate
                                + "（支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）");
            }
        } else if (lastMonths != null && lastMonths > 0) {
            afterMillis = monthsAgoMillis(lastMonths.intValue()); // 最近 N 个月之内 -> 修改时间晚于该点
        }
        Long beforeMillis = null;
        if (!TextUtils.isEmpty(beforeDate)) {
            beforeMillis = parseTimeMillis(beforeDate);
            if (beforeMillis == null) {
                throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                        "Cannot parse " + PARAM_BEFORE_DATE + ": " + beforeDate
                                + "（支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）");
            }
        }

        File[] children = dir.listFiles();
        List<File> matches = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                if (!TextUtils.isEmpty(prefix) && !name.startsWith(prefix)) {
                    continue;
                }
                if (!TextUtils.isEmpty(suffix) && !name.endsWith(suffix)) {
                    continue;
                }
                long modified = child.lastModified();
                if (afterMillis != null && modified < afterMillis) {
                    continue;
                }
                if (beforeMillis != null && modified >= beforeMillis) {
                    continue;
                }
                matches.add(child);
                if (limit != null && limit > 0 && matches.size() >= limit) {
                    break;
                }
            }
        }
        // 按修改时间倒序（新的在前），便于用户一眼看到最近的文件。
        matches.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        GenericDocument.Builder<?> builder = new GenericDocument.Builder<>(
                getPackageName(), FUNCTION_FIND_FILES + "_result", RESULT_SCHEMA_FIND)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyLong(RESULT_COUNT, matches.size());
        if (!matches.isEmpty()) {
            String[] paths = new String[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                paths[i] = matches.get(i).getAbsolutePath();
            }
            builder.setPropertyString(RESULT_MATCHES, paths);
        }
        callback.onResult(new ExecuteAppFunctionResponse(builder.build()));
    }

    /** 读取可选 Long 参数（AppSearch 侧会以 Long 属性下发，通过 getPropertyLongArray 读取）。 */
    @Nullable
    private static Long optionalLong(@Nullable GenericDocument parameters, String name) {
        if (parameters == null) {
            return null;
        }
        long[] values = parameters.getPropertyLongArray(name);
        if (values != null && values.length > 0) {
            return values[0];
        }
        return null;
    }

    /**
     * 返回源文件夹的直接子项中匹配过滤条件的集合（仅扫描一层，不递归）。
     *
     * <p>{@code ageMonths} 表示“修改时间早于 N 个月前的文件”；{@code beforeDate} 表示“修改时间
     * 早于某个时间点的文件”。两个时间条件只取其一，都为 null 时仅按名称过滤。
     */
    private static List<File> filterChildren(
            @NonNull File folder,
            @Nullable String prefix,
            @Nullable String suffix,
            @Nullable Long ageMonths,
            @Nullable String beforeDate) throws AppFunctionException {
        Long beforeMillis = null;
        if (ageMonths != null && ageMonths > 0) {
            beforeMillis = monthsAgoMillis(ageMonths.intValue());
        } else if (!TextUtils.isEmpty(beforeDate)) {
            beforeMillis = parseTimeMillis(beforeDate);
            if (beforeMillis == null) {
                throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                        "Cannot parse " + PARAM_BEFORE_DATE + ": " + beforeDate
                                + "（支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / epoch 毫秒）");
            }
        }

        File[] children = folder.listFiles();
        List<File> matches = new ArrayList<>();
        if (children == null) {
            return matches;
        }
        for (File child : children) {
            String name = child.getName();
            if (!TextUtils.isEmpty(prefix) && !name.startsWith(prefix)) {
                continue;
            }
            if (!TextUtils.isEmpty(suffix) && !name.endsWith(suffix)) {
                continue;
            }
            if (beforeMillis != null && child.lastModified() >= beforeMillis) {
                continue;
            }
            matches.add(child);
        }
        return matches;
    }

    /** N 个月之前的时间戳（毫秒），按自然月计算。 */
    private static long monthsAgoMillis(int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -months);
        return calendar.getTimeInMillis();
    }

    /**
     * 解析时间参数为 epoch 毫秒。支持 {@code yyyy-MM-dd}（当天 00:00）、
     * {@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd HH:mm:ss} 以及纯数字 epoch 毫秒。
     * 全部失败返回 {@code null}。
     */
    @Nullable
    private static Long parseTimeMillis(@NonNull String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d+")) {
            return Long.parseLong(trimmed);
        }
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                java.util.Date date = format.parse(trimmed);
                if (date != null) {
                    return date.getTime();
                }
            } catch (java.text.ParseException ignored) {
                // 尝试下一个格式
            }
        }
        return null;
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
     * 删除某个文件夹下所有以指定前缀开头 / 后缀结尾的文件。
     *
     * <p>{@code prefix} 与 {@code suffix} 至少提供一个，可同时提供（两个条件都满足的文件才会被
     * 删除）；只删除匹配的普通文件，不影响文件夹本身。删除动作与 {@code deleteFile} 一致：移入
     * 系统回收站，可在回收站页面恢复。返回实际删除数量 {@code deletedCount} 与（最多 200 条的）
     * 已删除路径列表 {@code paths}。
     */
    private void deleteFilesByName(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        String prefix = parameters == null ? null : parameters.getPropertyString(PARAM_PREFIX);
        String suffix = parameters == null ? null : parameters.getPropertyString(PARAM_SUFFIX);
        boolean recursive = getPropertyBool(parameters, PARAM_RECURSIVE, true);

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (TextUtils.isEmpty(prefix) && TextUtils.isEmpty(suffix)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "At least one of prefix or suffix must be provided");
        }

        File dir = resolveFile(path);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Not a directory: " + path);
        }

        final String startWith = prefix == null ? "" : prefix;
        final String endWith = suffix == null ? "" : suffix;
        List<File> matches = new ArrayList<>();
        walkFiles(dir, recursive,
                f -> {
                    String name = f.getName();
                    boolean startOk = startWith.isEmpty() || name.startsWith(startWith);
                    boolean endOk = endWith.isEmpty() || name.endsWith(endWith);
                    return startOk && endOk;
                },
                matches, cancelled);

        // 弹窗确认开关：缺省 true（安全起见），调用方可传 requireConfirmation=false 直接删除。
        boolean requireConfirmation = getPropertyBool(
                parameters, PARAM_REQUIRE_CONFIRMATION, true);
        DeleteOutcome outcome = deleteFilesInternal(matches, requireConfirmation, cancelled);
        if (!outcome.cancelled && outcome.deletedCount > 0) {
            EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        }
        callback.onResult(new ExecuteAppFunctionResponse(
                buildDeleteResult(FUNCTION_DELETE_FILES_BY_NAME, outcome)));
    }

    /**
     * 删除某个文件夹下在指定时间点之前 / 之后（按最后修改时间）的所有文件。
     *
     * <p>{@code before} 与 {@code after}（Unix 毫秒时间戳）至少提供一个：删除
     * {@code lastModified < before}（更早/之前）或 {@code lastModified > after}（更晚/之后）
     * 的文件；同时提供时两个条件需同时满足。删除动作与 {@code deleteFile} 一致：移入系统回收站。
     * 返回实际删除数量 {@code deletedCount}。
     */
    private void deleteFilesByTime(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        long before = getPropertyLong(parameters, PARAM_BEFORE, 0L);
        long after = getPropertyLong(parameters, PARAM_AFTER, 0L);
        boolean recursive = getPropertyBool(parameters, PARAM_RECURSIVE, true);

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (before <= 0L && after <= 0L) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "At least one of before or after must be provided");
        }

        File dir = resolveFile(path);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Not a directory: " + path);
        }

        List<File> matches = new ArrayList<>();
        walkFiles(dir, recursive,
                f -> (before <= 0L || f.lastModified() < before)
                        && (after <= 0L || f.lastModified() > after),
                matches, cancelled);

        // 弹窗确认开关：缺省 true（安全起见），调用方可传 requireConfirmation=false 直接删除。
        boolean requireConfirmation = getPropertyBool(
                parameters, PARAM_REQUIRE_CONFIRMATION, true);
        DeleteOutcome outcome = deleteFilesInternal(matches, requireConfirmation, cancelled);
        if (!outcome.cancelled && outcome.deletedCount > 0) {
            EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        }
        callback.onResult(new ExecuteAppFunctionResponse(
                buildDeleteResult(FUNCTION_DELETE_FILES_BY_TIME, outcome)));
    }

    /**
     * 找出某个文件夹下的大文件。
     *
     * <p>{@code minSize}（字节，缺省 100MB）为最小文件大小门槛，递归扫描子文件夹，找到的文件
     * 按大小从大到小排序，最多返回 {@code limit} 个（缺省 50）。结果携带 {@code count}（命中
     * 总数，可能超过返回上限）、{@code paths}、{@code sizes}（与 paths 对应的字节大小）、
     * {@code lastModified}（对应最后修改时间）。
     */
    private void findLargeFiles(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        long minSize = getPropertyLong(parameters, PARAM_MIN_SIZE, DEFAULT_MIN_SIZE);
        int limit = (int) getPropertyLong(parameters, PARAM_LIMIT, DEFAULT_LIMIT);
        boolean recursive = getPropertyBool(parameters, PARAM_RECURSIVE, true);

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        // minSize 会被下面的 lambda 捕获，保持为 final 副本，避免"非有效 final"编译错误。
        final long minSizeThreshold = minSize <= 0L ? 1L : minSize;

        File dir = resolveFile(path);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Not a directory: " + path);
        }

        List<File> matches = new ArrayList<>();
        walkFiles(dir, recursive, f -> f.length() >= minSizeThreshold, matches, cancelled);
        matches.sort(Comparator.comparingLong(File::length).reversed());

        buildFileListResult(FUNCTION_FIND_LARGE_FILES, matches, limit, callback);
    }

    /**
     * 找出某个文件夹下不常用的文件。
     *
     * <p>{@code days}（缺省 30）表示：最后一次被访问或修改的时间早于 {days} 天前的文件视为
     * "不常用"。递归扫描子文件夹，按最后访问时间升序（最久未使用在前）排序，最多返回
     * {@code limit} 个（缺省 50）。结果携带 {@code count}、{@code paths}、{@code lastModified}
     * 与 {@code lastAccessed}（均为与 paths 对应的毫秒时间戳）。
     */
    private void findUnusedFiles(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        String path = parameters == null ? null : parameters.getPropertyString(PARAM_PATH);
        int days = (int) getPropertyLong(parameters, PARAM_DAYS, DEFAULT_UNUSED_DAYS);
        int limit = (int) getPropertyLong(parameters, PARAM_LIMIT, DEFAULT_LIMIT);

        if (TextUtils.isEmpty(path)) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Missing required parameter: " + PARAM_PATH);
        }
        if (days <= 0) {
            days = DEFAULT_UNUSED_DAYS;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        File dir = resolveFile(path);
        if (!dir.isDirectory()) {
            throw new AppFunctionException(AppFunctionException.ERROR_INVALID_ARGUMENT,
                    "Not a directory: " + path);
        }

        final long threshold = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        List<File> matches = new ArrayList<>();
        walkFiles(dir, true,
                f -> Math.max(lastAccessMillis(f), f.lastModified()) < threshold,
                matches, cancelled);
        matches.sort(Comparator.comparingLong(f -> Math.max(lastAccessMillis(f), f.lastModified())));

        buildFileListResult(FUNCTION_FIND_UNUSED_FILES, matches, limit, callback);
    }

    /**
     * 读取文件的最后访问时间（毫秒）。优先通过 NIO 读取真实的 atime；读取失败或不可用时
     * 回退到最后修改时间，保证"不常用文件"的判断仍可按最后访问时间工作。
     */
    private static long lastAccessMillis(File file) {
        if (file == null) {
            return 0L;
        }
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class)
                    .lastAccessTime().toMillis();
        } catch (Exception e) {
            // 部分文件系统（如外置存储 FUSE）不暴露可靠的访问时间，回退到最后修改时间。
            return file.lastModified();
        }
    }

    /** 删除流程的产出：是否被用户取消、实际移入回收站的数量、已删除路径（最多 MAX_RESULT_PATHS 条）。 */
    private static final class DeleteOutcome {
        boolean cancelled;
        int deletedCount;
        List<String> deletedPaths = new ArrayList<>();
    }

    /**
     * 执行删除（移入回收站）。要求确认（requireConfirmation=true）时，先把待删除文件列表交给
     * {@link DeleteFilesConfirmationActivity} 弹窗展示（默认全选，用户可取消勾选想要保留的文件），
     * 用户确认后才真正移入回收站；否则直接删除。
     */
    private DeleteOutcome deleteFilesInternal(List<File> matches, boolean requireConfirmation,
            boolean[] cancelled) throws AppFunctionException {
        DeleteOutcome outcome = new DeleteOutcome();
        if (matches == null || matches.isEmpty()) {
            return outcome;
        }
        if (!requireConfirmation) {
            for (File file : matches) {
                trashOne(file, cancelled, outcome);
            }
            return outcome;
        }

        // 需要用户弹窗确认：把完整文件列表交由对话框展示，阻塞等待用户做出决定。
        List<String> paths = new ArrayList<>(matches.size());
        for (File file : matches) {
            paths.add(file.getAbsolutePath());
        }
        String token = UUID.randomUUID().toString();
        PendingDeleteStore.PendingRequest request = PendingDeleteStore.create(token, paths);
        try {
            startActivity(DeleteFilesConfirmationActivity.createIntent(this, token));
        } catch (Exception e) {
            PendingDeleteStore.remove(token);
            Log.e(TAG, "deleteFilesInternal: failed to show confirmation dialog", e);
            throw new AppFunctionException(AppFunctionException.ERROR_SYSTEM_ERROR,
                    "Failed to show delete confirmation dialog: " + e.getMessage());
        }

        PendingDeleteStore.PendingRequest done = request.await(CONFIRM_TIMEOUT_MS);
        PendingDeleteStore.remove(token);
        if (done == null) {
            // 用户长时间未操作（超时），视为取消，避免调用方一直等待。
            Log.w(TAG, "deleteFilesInternal: no decision from user, treat as cancelled");
            outcome.cancelled = true;
            return outcome;
        }
        outcome.cancelled = done.isCancelled();
        outcome.deletedPaths = new ArrayList<>(done.getDeletedPaths());
        outcome.deletedCount = outcome.deletedPaths.size();
        // 结果文档携带的路径上限（避免结果文档过大）；对话框可能一次性删掉成百上千个文件。
        if (outcome.deletedPaths.size() > MAX_RESULT_PATHS) {
            outcome.deletedPaths =
                    new ArrayList<>(outcome.deletedPaths.subList(0, MAX_RESULT_PATHS));
        }
        return outcome;
    }

    /**
     * 删除显式指定的文件列表（通常是 findFiles / findLargeFiles / findUnusedFiles 的查找结果），
     * 支持逐项弹窗确认。参数：{@code paths}（必填，路径列表，支持绝对路径或相对桌面名称；
     * 也可传单个 {@code path} 字符串）、{@code requireConfirmation}（可选，缺省 true 弹窗确认，
     * 传 false 直接删除）。
     */
    private void deleteFiles(GenericDocument parameters, boolean[] cancelled,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback)
            throws AppFunctionException {
        List<File> targets = resolveDeleteTargets(parameters);
        boolean requireConfirmation = getPropertyBool(
                parameters, PARAM_REQUIRE_CONFIRMATION, true);
        DeleteOutcome outcome = deleteFilesInternal(targets, requireConfirmation, cancelled);
        if (!outcome.cancelled && outcome.deletedCount > 0) {
            EventBus.getDefault().post(new MessageEvent("", "UPDATE"));
        }
        callback.onResult(new ExecuteAppFunctionResponse(
                buildDeleteResult(FUNCTION_DELETE_FILES, outcome)));
    }

    /**
     * 解析 deleteFiles 的目标文件：读取 {@code paths}（字符串数组，或单个字符串），
     * 兼容绝对路径与相对桌面的名称；不存在的路径跳过并告警。
     */
    private static List<File> resolveDeleteTargets(GenericDocument parameters) {
        List<File> targets = new ArrayList<>();
        if (parameters == null) {
            return targets;
        }
        List<String> paths = readStringList(parameters, PARAM_PATHS);
        for (String path : paths) {
            File file = resolveFile(path);
            if (file.exists()) {
                targets.add(file);
            } else {
                Log.w(TAG, "resolveDeleteTargets: skip missing path: " + path);
            }
        }
        return targets;
    }

    /** 读取字符串列表参数：优先取字符串数组，其次取单个字符串。 */
    private static List<String> readStringList(GenericDocument parameters, String name) {
        List<String> result = new ArrayList<>();
        if (parameters == null) {
            return result;
        }
        String[] array;
        try {
            array = parameters.getPropertyStringArray(name);
        } catch (Exception e) {
            array = null;
        }
        if (array != null) {
            for (String s : array) {
                if (!TextUtils.isEmpty(s)) {
                    result.add(s);
                }
            }
        }
        if (result.isEmpty()) {
            String single = parameters.getPropertyString(name);
            if (!TextUtils.isEmpty(single)) {
                result.add(single);
            }
        }
        return result;
    }

    /** 构建统一的删除结果文档（status / cancelled / deletedCount / paths）。 */
    private GenericDocument buildDeleteResult(String resultIdPrefix, DeleteOutcome outcome) {
        GenericDocument.Builder<?> builder = new GenericDocument.Builder<>(
                getPackageName(), resultIdPrefix + "_result", RESULT_SCHEMA)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyBoolean(RESULT_CANCELLED, outcome.cancelled)
                .setPropertyLong(RESULT_DELETED_COUNT, outcome.deletedCount);
        if (!outcome.deletedPaths.isEmpty()) {
            builder.setPropertyString(RESULT_PATHS, outcome.deletedPaths.toArray(new String[0]));
        }
        return builder.build();
    }

    /**
     * 把单个文件移入回收站并累加删除结果（失败只打日志，不中断整体流程）。
     */
    private void trashOne(File file, boolean[] cancelled, DeleteOutcome outcome)
            throws AppFunctionException {
        if (cancelled[0]) {
            throw new AppFunctionException(
                    AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
        }
        if (FileUtils.trashPathSync(getApplicationContext(), file.getAbsolutePath())) {
            outcome.deletedCount++;
            if (outcome.deletedPaths.size() < MAX_RESULT_PATHS) {
                outcome.deletedPaths.add(file.getAbsolutePath());
            }
        } else {
            Log.w(TAG, "trashOne: failed to trash " + file.getAbsolutePath());
        }
    }

    /**
     * 构建一个文件列表类的结果文档（count / paths / sizes / lastModified / lastAccessed）。
     * 仅当命中数量非零时写入数组属性，避免空数组报错。
     */
    private void buildFileListResult(String resultIdPrefix, List<File> files, int limit,
            OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback) {
        int total = files.size();
        List<File> limited = files.size() <= limit ? files : new ArrayList<>(files.subList(0, limit));
        String[] paths = new String[limited.size()];
        long[] sizes = new long[limited.size()];
        long[] lastModified = new long[limited.size()];
        long[] lastAccessed = new long[limited.size()];
        for (int i = 0; i < limited.size(); i++) {
            File f = limited.get(i);
            paths[i] = f.getAbsolutePath();
            sizes[i] = f.length();
            lastModified[i] = f.lastModified();
            lastAccessed[i] = lastAccessMillis(f);
        }

        GenericDocument.Builder<?> builder = new GenericDocument.Builder<>(
                getPackageName(), resultIdPrefix + "_result", RESULT_SCHEMA_LIST)
                .setPropertyString(RESULT_STATUS, RESULT_OK)
                .setPropertyLong(RESULT_COUNT, total);
        if (!limited.isEmpty()) {
            builder.setPropertyString(RESULT_PATHS, paths);
            builder.setPropertyLong(RESULT_SIZES, sizes);
            builder.setPropertyLong(RESULT_LAST_MODIFIED, lastModified);
            builder.setPropertyLong(RESULT_LAST_ACCESSED, lastAccessed);
        }
        callback.onResult(new ExecuteAppFunctionResponse(builder.build()));
    }

    /**
     * 递归收集 {@code dir} 下满足 {@code filter} 的普通文件（不处理类型为文件夹的项）。
     * 递归时不跟随符号链接，避免出现循环。
     */
    private static void walkFiles(File dir, boolean recursive, Predicate<File> filter,
            List<File> matches, boolean[] cancelled) throws AppFunctionException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (cancelled[0]) {
                throw new AppFunctionException(
                        AppFunctionException.ERROR_CANCELLED, "Execution cancelled");
            }
            if (child.isFile()) {
                if (filter.test(child)) {
                    matches.add(child);
                }
            } else if (recursive && child.isDirectory()
                    && !Files.isSymbolicLink(child.toPath())) {
                walkFiles(child, true, filter, matches, cancelled);
            }
        }
    }

    /** 读取布尔型参数，支持 true/false、1/0。 */
    private static boolean getPropertyBool(GenericDocument doc, String name, boolean defValue) {
        String value = doc == null ? null : doc.getPropertyString(name);
        if (TextUtils.isEmpty(value)) {
            return defValue;
        }
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value);
    }

    /** 读取长整型参数（GenericDocument 属性为 long 数组，取第一个元素）。 */
    private static long getPropertyLong(GenericDocument doc, String name, long defValue) {
        if (doc == null) {
            return defValue;
        }
        long[] values = doc.getPropertyLongArray(name);
        return (values != null && values.length > 0) ? values[0] : defValue;
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
