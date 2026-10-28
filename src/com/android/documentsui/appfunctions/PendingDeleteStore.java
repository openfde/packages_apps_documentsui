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

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 应用进程内的"待确认删除"握手存储。
 *
 * <p>当删除类 AppFunction 需要用户弹窗确认时，service 端通过 {@link #create} 登记一个
 * {@link PendingRequest}（携带完整的待删除路径列表），然后拉起
 * {@link DeleteFilesConfirmationActivity} 弹窗展示；用户在对话框里逐项勾选并点击"删除"/"取消"
 * 后，由对话框调用 {@link PendingRequest#complete} 回写结果并唤醒阻塞等待中的 service 线程，
 * service 再把结果组装成 {@code ExecuteAppFunctionResponse} 返回给 AppFunction 调用方。
 *
 * <p>Service 与 Activity 同属 DocumentsUI 进程，因此直接使用进程内静态 Map 即可，无需跨进程。
 */
public final class PendingDeleteStore {
    private static final Map<String, PendingRequest> REQUESTS = new ConcurrentHashMap<>();

    private PendingDeleteStore() {
    }

    /** 登记一个新的待确认删除请求并返回句柄。 */
    public static PendingRequest create(String token, List<String> paths) {
        PendingRequest request = new PendingRequest(token, paths);
        REQUESTS.put(token, request);
        return request;
    }

    /** 按 token 获取请求；不存在或已移除时返回 {@code null}。 */
    @Nullable
    public static PendingRequest get(String token) {
        return token == null ? null : REQUESTS.get(token);
    }

    /** 移除请求（在请求被完成或超时后调用，避免内存泄漏）。 */
    public static void remove(String token) {
        if (token != null) {
            REQUESTS.remove(token);
        }
    }

    /** 一个待用户确认的删除请求：持有要删除的路径列表，并在用户做出决定后回写结果。 */
    public static final class PendingRequest {
        private final String token;
        private final List<String> paths;
        private final CountDownLatch latch = new CountDownLatch(1);
        private List<String> deletedPaths = Collections.emptyList();
        private boolean cancelled = true;
        private boolean decided;

        private PendingRequest(String token, List<String> paths) {
            this.token = token;
            this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
        }

        public String getToken() {
            return token;
        }

        /** 全部待确认删除的路径（只读）。 */
        public List<String> getPaths() {
            return paths;
        }

        public boolean isDecided() {
            synchronized (this) {
                return decided;
            }
        }

        /**
         * 用户完成操作后由 {@link DeleteFilesConfirmationActivity} 调用：写回实际删除（移入
         * 回收站）成功的路径与是否取消，并唤醒等待中的 service 线程。多次调用只有第一次生效。
         *
         * @param deletedPaths 实际删除成功的路径（可为空集合）
         * @param cancelled    用户是否取消（取消时 deletedPaths 应为空）
         */
        public void complete(@Nullable List<String> deletedPaths, boolean cancelled) {
            synchronized (this) {
                if (decided) {
                    return;
                }
                decided = true;
                this.deletedPaths = deletedPaths == null
                        ? Collections.emptyList() : new ArrayList<>(deletedPaths);
                this.cancelled = cancelled;
            }
            latch.countDown();
        }

        public List<String> getDeletedPaths() {
            synchronized (this) {
                return deletedPaths;
            }
        }

        public boolean isCancelled() {
            synchronized (this) {
                return cancelled;
            }
        }

        /**
         * 阻塞等待用户做出决定，最多 {@code timeoutMs} 毫秒。
         *
         * @return 用户已做出决定时返回 {@code this}；超时或线程被中断时返回 {@code null}。
         */
        @Nullable
        public PendingRequest await(long timeoutMs) {
            try {
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            return this;
        }
    }
}
