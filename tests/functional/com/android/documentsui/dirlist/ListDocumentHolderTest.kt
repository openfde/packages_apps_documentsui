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

package com.android.documentsui.dirlist

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.TestConfigStore
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Shared
import com.android.documentsui.base.State.MODE_LIST
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestActionHandler
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ListDocumentHolderTest {
    @get:Rule val rule = OverrideFlagsRule()

    private val testConfigStore = TestConfigStore()
    private lateinit var context: Context
    private lateinit var doc: DocumentInfo
    private lateinit var holder: ListDocumentHolder
    private lateinit var env: TestEnvironment

    @SuppressLint("VisibleForTests")
    @Before
    fun setUp() {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(targetContext.resources.configuration)
        // Force a narrow screen to ensure the phone layout (consolidated metadata) is inflated.
        config.screenWidthDp = 320
        config.smallestScreenWidthDp = 320

        context = targetContext.createConfigurationContext(config)
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)

        val parent = FrameLayout(context)
        val testEnv = TestEnv.create()
        val actionHandler = TestActionHandler()
        doc = DocumentInfo()
        doc.derivedUri = DocumentsContract.buildDocumentUri("authority", "name")

        env = TestEnvironment(context, testEnv, actionHandler)
        Log.d(TAG, "In setUp(): isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}")
        holder =
            ListDocumentHolder(
                context,
                parent,
                IconHelper(context, MODE_LIST, false, null, USER_ID, null, testConfigStore),
                { "type_label" },
                testConfigStore,
                env,
            )
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoM3() {
        doc.mimeType = "text/plain"
        doc.lastModified = Calendar.getInstance().timeInMillis
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfFileNoM3(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        val time = Shared.formatTime(context, doc.lastModified)
        assertThat(view.text.toString()).isEqualTo("$time, 2.00 kB, type_label")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFile() {
        doc.mimeType = "text/plain"
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfFile(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        val time = Shared.formatTime(context, doc.lastModified)
        assertThat(view.text.toString()).isEqualTo("2.00 kB • $time")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoModificationTimeNoM3() {
        doc.mimeType = "text/plain"
        doc.lastModified = 0
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfFileNoModificationTimeNoM3(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        assertThat(view.text.toString()).isEqualTo("2.00 kB, type_label")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoModificationTime() {
        doc.mimeType = "text/plain"
        doc.lastModified = 0
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfFileNoModificationTime(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        assertThat(view.text.toString()).isEqualTo("2.00 kB")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoSizeNoM3() {
        doc.mimeType = "text/plain"
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = -1
        Log.d(
            TAG,
            "In testMetadataOfFileNoSizeNoM3(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        val time = Shared.formatTime(context, doc.lastModified)
        assertThat(view.text.toString()).isEqualTo("$time, type_label")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoSize() {
        doc.mimeType = "text/plain"
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = -1
        Log.d(
            TAG,
            "In testMetadataOfFileNoSize(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        val time = Shared.formatTime(context, doc.lastModified)
        assertThat(view.text.toString()).isEqualTo(time)
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoModificationTimeNoSizeNoM3() {
        doc.mimeType = "text/plain"
        doc.lastModified = 0
        doc.size = -1
        Log.d(
            TAG,
            "In testMetadataOfFileNoModificationTimeNoSizeNoM3(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.text.toString()).isEqualTo("type_label")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(GONE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileNoModificationTimeNoSize() {
        doc.mimeType = "text/plain"
        doc.lastModified = 0
        doc.size = -1
        Log.d(
            TAG,
            "In testMetadataOfFileNoModificationTimeNoSize(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.text.toString()).isEmpty()
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(GONE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfFileWithSummary() {
        doc.mimeType = "text/plain"
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = 2000
        val summary = "Test Summary"
        env.setShouldDisplaySummary(true)
        Log.d(
            TAG,
            "In testMetadataOfFileWithSummary(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, summary, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.visibility).isEqualTo(VISIBLE)
        val time = Shared.formatTime(context, doc.lastModified)
        assertThat(view.text.toString()).isEqualTo("$summary • 2.00 kB • $time")
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfDirectoryNoM3() {
        doc.mimeType = MIME_TYPE_DIR
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfDirectoryNoM3(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.text.toString()).isEmpty()
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(GONE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testMetadataOfDirectory() {
        doc.mimeType = MIME_TYPE_DIR
        doc.lastModified = Calendar.getInstance().getTimeInMillis()
        doc.size = 2000
        Log.d(
            TAG,
            "In testMetadataOfDirectory(): " +
                "isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}",
        )
        holder.bind(doc, MODEL_ID, null, false)

        val view = holder.itemView.findViewById<TextView>(getRes(R.id.metadata))
        assertThat(view).isNotNull()
        assertThat(view.text.toString()).isEmpty()
        val details = holder.itemView.findViewById<LinearLayout>(getRes(R.id.line2))
        assertThat(details.visibility).isEqualTo(GONE)
    }

    companion object {
        private const val TAG = "ListDocumentHolderTest"
        private const val MODEL_ID = "model_id"
        private val USER_ID = UserId.of(0)
    }
}
