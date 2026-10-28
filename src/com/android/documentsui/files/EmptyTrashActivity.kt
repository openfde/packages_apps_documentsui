/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.documentsui.files

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.MessageEvent
import com.android.documentsui.R
import com.android.documentsui.base.UserId
import com.android.documentsui.provider.FileUtils
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.android.documentsui.util.UnitUtils.Companion.dpToPx
import de.greenrobot.event.EventBus

/**
 * A standalone dialog-style activity shown when emptying the trash.
 *
 * The old EmptyTrashDialogFragment used to reach into [FilesActivity] to obtain its injector and
 * perform `permanentlyDeleteTrashDocuments()`. This activity replaces it and is fully independent:
 * it is registered on its own in the manifest, renders its own dialog UI and performs the empty
 * trash operation by itself, without depending on any other activity.
 *
 * The window is transparent/translucent (see `EmptyTrashDialogTheme`), so it can be shown as a
 * floating dialog even when launched with no other activity behind it. Note that a translucent
 * window only pauses (never stops) the activity underneath, so instead of relying on the host
 * activity's `onStart` refresh we explicitly notify the trash loaders after emptying.
 */
class EmptyTrashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmptyTrashActivity"

        /** 标准对话框与屏幕边缘之间的水平间距 (每侧)。 */
        private const val HORIZONTAL_INSET_DP = 24f

        /**
         * Creates an intent used to launch this activity.
         *
         * @param context a context used to create the intent.
         * @return an intent targeting [EmptyTrashActivity].
         */
        @JvmStatic
        fun createIntent(context: Context): Intent =
            Intent(context, EmptyTrashActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: The window of this activity is transparent (EmptyTrashDialogTheme). Do NOT
        // apply an opaque full-screen theme overlay here, otherwise the transparency is lost
        // and the activity renders as an opaque full-screen window with nothing behind it.
        //
        // When Material3 is enabled we still merge the Material3 default theme underneath so
        // the M3-only attributes used by empty_trash_activity_m3.xml (colorSurfaceContainerHigh,
        // Material3 text appearances, ...) resolve. applyStyle with force=false only adds
        // attributes that aren't set yet, and the manifest theme's transparent window
        // attributes always win, so the floating/translucent window style is preserved.
        supportActionBar?.hide()

        if (isUseMaterial3FlagEnabled()) {
            getTheme().applyStyle(getRes(R.style.DocumentsDefaultTheme), false)
        }
        setContentView(getRes(R.layout.empty_trash_activity))

        // 限制透明对话框窗口的宽度，避免内容 (match_parent) 撑满整个屏宽、看起来太宽。
        customizeWindowWidth()

        findViewById<Button>(android.R.id.button2).setOnClickListener { finish() }

        findViewById<Button>(android.R.id.button1).setOnClickListener {
            emptyTrashAndFinish()
        }
    }

    /**
     * 把窗口限制为普通 Alert 对话框的宽度：最大不超过 [R.dimen.empty_trash_dialog_max_width]，
     * 同时保留标准 24dp 的水平屏幕边距，让手机和小屏幕上也不会铺满全宽。
     */
    private fun customizeWindowWidth() {
        val params = WindowManager.LayoutParams()
        params.copyFrom(window.attributes)
        val horizontalInsetPx = dpToPx(this, HORIZONTAL_INSET_DP)
        params.width =
            (resources.displayMetrics.widthPixels - 2 * horizontalInsetPx)
                .coerceAtMost(resources.getDimensionPixelSize(R.dimen.empty_trash_dialog_max_width))
                .coerceAtLeast(0)
        window.attributes = params
    }

    /**
     * Permanently deletes everything currently in the trash. This replicates the tail of
     * [com.android.documentsui.ActionHandler#permanentlyDeleteTrashDocuments] so the whole flow
     * can run from this activity alone:
     *
     *  1. physically remove every file/folder sitting in the local trash folder;
     *  2. poke the trash loaders so any paused host activity still reloads its list;
     *  3. reset the trash size tracker read by the UI;
     *  4. broadcast the same file-change notification the providers emit after a delete, so
     *     external components (e.g. the desktop file list) refresh as well.
     */
    private fun emptyTrashAndFinish() {
        // Deleting can take a while for large trash folders, so don't block the UI thread.
        Thread {
            try {
                FileUtils.clearTrashFolder(FileUtils.PATH_TRASH)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear trash folder.", e)
            }

            // The trash page reloads when its loader observes a change on the trash documents URIs.
            // With a translucent window the host activity is only paused (not stopped) and its
            // onStart refresh won't run, so poke the loader directly to avoid stale contents.
            notifyTrashContentChanged()

            runOnUiThread {
                try {
                    Settings.System.putInt(contentResolver, "trash_storage", 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to reset trash_storage.", e)
                }

                EventBus.getDefault().post(MessageEvent("", "UPDATE"))
                finish()
            }
        }.start()
    }

    /**
     * Best-effort notification that triggers a reload of any active trash directory loaders (e.g.
     * the one owned by a paused [FilesActivity]). It notifies the same trash documents URIs that
     * [com.android.documentsui.loaders.TrashFileLoader] queries, so its
     * [androidx.loader.content.AsyncTaskLoader] observers fire and reload the list.
     */
    private fun notifyTrashContentChanged() {
        try {
            val providers = DocumentsApplication.getProvidersCache(this)
            for (root in providers.getRootsBlocking()) {
                if (!root.supportsQueryTrash() || root.userId != UserId.CURRENT_USER) {
                    continue
                }
                val uri = DocumentsContract.buildTrashDocumentsUri(root.authority, root.rootId)
                contentResolver.notifyChange(uri, null, false)
            }
        } catch (t: Throwable) {
            // Never let a best-effort notification prevent the activity from finishing.
            Log.w(TAG, "Failed to notify trash content changes.", t)
        }
    }
}
