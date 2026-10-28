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
package com.android.documentsui.appfunctions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.documentsui.MessageEvent
import com.android.documentsui.R
import com.android.documentsui.provider.FileUtils
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import de.greenrobot.event.EventBus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 删除前的确认弹窗（对话框式 Activity）。
 *
 * 由删除类 AppFunction（deleteFiles / deleteFilesByName / deleteFilesByTime / deleteFile 批量
 * 模式）在 {@code requireConfirmation=true} 时经 [PendingDeleteStore] 拉起。对话框列出待删除的
 * 文件，每项带复选框（默认全选），用户可取消勾选想要保留的文件，然后点"删除"把选中的文件移入
 * 回收站，或点"取消"放弃本次删除。
 *
 * 结果通过 [PendingDeleteStore.PendingRequest] 回传给等待中的 service 线程，最终由 service
 * 组装成 [android.app.appfunctions.ExecuteAppFunctionResponse] 返回给 AppFunction 调用方。
 *
 * 窗口是透明的（DeleteFilesConfirmationDialogTheme），可以像对话框一样悬浮显示，参考
 * [com.android.documentsui.files.EmptyTrashActivity] 的实现方式。
 */
class DeleteFilesConfirmationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DeleteFilesConfirmationActivity"

        /** Intent extra：PendingDeleteStore 中的请求 token。 */
        const val EXTRA_TOKEN = "com.android.documentsui.appfunctions.extra.DELETE_TOKEN"

        /** 创建用于拉起本对话框的 Intent。 */
        @JvmStatic
        fun createIntent(context: Context, token: String): Intent =
            Intent(context, DeleteFilesConfirmationActivity::class.java)
                .putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 防止重复完成/重复 finish（onDestroy 也可能触发一次取消）。 */
    private val finished = AtomicBoolean(false)

    private var token: String? = null
    private lateinit var listView: ListView
    private var adapter: DeleteListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 与 EmptyTrashActivity 一致：透明对话框窗口，先隐藏 ActionBar，M3 开关开启时叠加
        // Material3 默认主题（不覆盖透明窗口）。
        supportActionBar?.hide()
        if (isUseMaterial3FlagEnabled()) {
            getTheme().applyStyle(getRes(R.style.DocumentsDefaultTheme), false)
        }
        setContentView(getRes(R.layout.delete_files_confirmation_activity))

        token = intent.getStringExtra(EXTRA_TOKEN)
        val request = token?.let { PendingDeleteStore.get(it) }
        if (request == null || request.isDecided()) {
            // 请求不存在（已被处理、超时或 token 非法）时直接退出，避免展示空列表。
            Log.w(TAG, "onCreate: pending delete request not found or already decided")
            finish()
            return
        }

        listView = findViewById(R.id.delete_file_list)
        adapter = DeleteListAdapter(request.paths)
        listView.adapter = adapter

        findViewById<TextView>(R.id.delete_file_dialog_title).text =
            resources.getQuantityString(R.plurals.delete_files_confirm_title, adapter!!.count, adapter!!.count)

        findViewById<Button>(android.R.id.button2).setOnClickListener { cancel() }
        findViewById<Button>(android.R.id.button1).setOnClickListener { confirm() }
    }

    /** 用户勾选并点击"删除"：把选中的文件移入回收站，然后把结果回传给等待中的 service。 */
    private fun confirm() {
        val selected = adapter?.selectedPaths() ?: emptyList()
        if (selected.isEmpty()) {
            // 全都不勾选仍然点"删除"-> 删除 0 个文件。
            complete(emptyList(), cancelled = false)
            return
        }
        Thread {
            val deleted = mutableListOf<String>()
            var ok = 0
            for (path in selected) {
                if (FileUtils.trashPathSync(this, path)) {
                    ok++
                    deleted.add(path)
                } else {
                    Log.w(TAG, "confirm: failed to trash $path")
                }
            }
            if (ok > 0) {
                // 刷新桌面/文件列表。
                try {
                    EventBus.getDefault().post(MessageEvent("", "UPDATE"))
                } catch (t: Throwable) {
                    Log.w(TAG, "confirm: failed to post update event", t)
                }
            }
            val deletedCopy = deleted
            runOnUiThread { complete(deletedCopy, cancelled = false) }
        }.start()
    }

    private fun cancel() {
        complete(emptyList(), cancelled = true)
    }

    /** 把结果写回 PendingDeleteRequest 并关闭对话框（保证只执行一次）。 */
    private fun complete(deletedPaths: List<String>, cancelled: Boolean) {
        if (!finished.compareAndSet(false, true)) {
            return
        }
        try {
            token?.let { t ->
                PendingDeleteStore.get(t)?.complete(deletedPaths, cancelled)
                PendingDeleteStore.remove(t)
            }
        } finally {
            finish()
        }
    }

    override fun onDestroy() {
        // 用户按返回键或对话框被系统回收而未明确确认：视为取消，避免 service 一直阻塞等待。
        if (!finished.get()) {
            complete(emptyList(), cancelled = true)
        }
        super.onDestroy()
    }

    /** ListView 行适配器：每个待删除文件一行，带复选框（默认勾选）。 */
    private inner class DeleteListAdapter(private val paths: List<String>) : BaseAdapter() {

        private val checked = BooleanArray(paths.size) { true }

        override fun getCount(): Int = paths.size

        override fun getItem(position: Int): Any = paths[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val checkbox: CheckBox
            if (convertView is CheckBox) {
                checkbox = convertView
            } else {
                checkbox = CheckBox(parent.context)
                checkbox.setPadding(
                    checkbox.paddingLeft,
                    16 * resources.displayMetrics.density.toInt(),
                    checkbox.paddingRight,
                    16 * resources.displayMetrics.density.toInt(),
                )
            }
            checkbox.text = paths[position]
            checkbox.isChecked = checked[position]
            checkbox.setOnCheckedChangeListener { _, isChecked -> checked[position] = isChecked }
            return checkbox
        }

        /** 当前被勾选（需要删除）的路径。 */
        fun selectedPaths(): List<String> =
            paths.filterIndexed { index, _ -> checked[index] }
    }
}
