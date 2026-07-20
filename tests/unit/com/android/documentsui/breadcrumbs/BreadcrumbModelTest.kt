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
package com.android.documentsui.breadcrumbs

import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.InstantTaskExecutorRule
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/** Helper function for creating directory like DocumentInfo objects. */
fun createDir(name: String): DocumentInfo {
    val info = DocumentInfo()
    info.authority = "authority"
    info.documentId = name
    info.displayName = name
    info.userId = UserId.of(10)
    info.mimeType = DocumentsContract.Document.MIME_TYPE_DIR
    info.derivedUri = DocumentsContract.buildDocumentUri(info.authority, info.documentId)
    return info
}

@EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class BreadcrumbModelTest {
    // Rule to ensure LiveData operations run synchronously on the test thread
    @get:Rule(order = 0) val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val setFlags = OverrideFlagsRule()

    private val breadcrumbModel = BreadcrumbModel()
    private val root =
        RootInfo().apply {
            title = "root"
            authority = "some"
        }
    private val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)

    @Test
    fun testSetFromStack() {
        val testBreadcrumbObserver = mock<Observer<List<String>>>()
        breadcrumbModel.pathData.observe(lifecycleOwner, testBreadcrumbObserver)
        verify(testBreadcrumbObserver).onChanged(eq(listOf()))

        val stack = DocumentStack()
        stack.changeRoot(root)
        stack.push(createDir("root"))
        breadcrumbModel.setFromStack(stack)
        verify(testBreadcrumbObserver).onChanged(eq(listOf("root")))

        stack.push(createDir("Folder01"))
        breadcrumbModel.setFromStack(stack)
        verify(testBreadcrumbObserver).onChanged(eq(listOf("root", "Folder01")))

        // Calling with the same stack must not post notifications.
        breadcrumbModel.setFromStack(stack)
        verifyNoMoreInteractions(testBreadcrumbObserver)

        // If the caller is removed, no notifications are posted.
        breadcrumbModel.pathData.removeObserver(testBreadcrumbObserver)
        stack.push(createDir("Folder02"))

        breadcrumbModel.setFromStack(stack)
        verifyNoMoreInteractions(testBreadcrumbObserver)

        // Another call when we start observing.
        breadcrumbModel.pathData.observe(lifecycleOwner, testBreadcrumbObserver)
        verify(testBreadcrumbObserver).onChanged(eq(listOf("root", "Folder01", "Folder02")))

        // Shorter path triggers change, too.
        stack.pop()
        breadcrumbModel.setFromStack(stack)
        verify(testBreadcrumbObserver, times(2)).onChanged(eq(listOf("root", "Folder01")))
    }
}
