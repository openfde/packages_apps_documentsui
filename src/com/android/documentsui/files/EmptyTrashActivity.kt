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
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.android.documentsui.MessageEvent
import com.android.documentsui.R
import com.android.documentsui.provider.FileUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import de.greenrobot.event.EventBus

/**
 * A standalone dialog-style activity shown when emptying the trash.
 *
 * The old EmptyTrashDialogFragment used to reach into [FilesActivity] to obtain its injector and
 * perform `permanentlyDeleteTrashDocuments()`. This activity replaces it and is fully independent:
 * it is registered on its own in the manifest, renders its own dialog UI and performs the empty
 * trash operation by itself, without depending on any other activity.
 */
class EmptyTrashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EmptyTrashActivity"

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

        // Merge the default (possibly Material3) theme so Material components and colors render
        // consistently with the rest of the app, while keeping the floating dialog window style
        // defined by the manifest theme.
        getTheme().applyStyle(getRes(R.style.DocumentsDefaultTheme), false)

        setContentView(R.layout.empty_trash_activity)

        findViewById<Button>(android.R.id.button2).setOnClickListener { finish() }

        findViewById<Button>(android.R.id.button1).setOnClickListener {
            emptyTrashAndFinish()
        }
    }

    /**
     * Permanently deletes everything currently in the trash. This replicates the tail of
     * [com.android.documentsui.ActionHandler#permanentlyDeleteTrashDocuments] so the whole flow
     * can run from this activity alone:
     *
     *  1. physically remove every file/folder sitting in the local trash folder;
     *  2. reset the trash size tracker read by the UI;
     *  3. broadcast the same file-change notification the providers emit after a delete, so
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
}
