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

package com.android.documentsui.approveddochandlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.ActionHandler
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.UserId
import com.android.documentsui.rules.InstantTaskExecutorRule
import com.android.documentsui.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class ApprovedDocHandlersTest {

    @Mock private lateinit var applicationContext: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var selectionDetails: MenuManager.SelectionDetails
    @Mock private lateinit var icon: Drawable
    @Mock private lateinit var mockActionHandler: ActionHandler
    @Mock private lateinit var launcherApps: LauncherApps

    private lateinit var injector: Injector<ActionHandler>
    private lateinit var approvedDocHandlers: ApprovedDocHandlers
    private val testPackage = "com.test.package"
    private val testClass = "com.test.package.TestClass"
    private val testComponent = ComponentName(testPackage, testClass)
    private val activityInfo =
        spy(ActivityInfo()).apply {
            packageName = testPackage
            name = testClass
        }
    private val resolveInfo = ResolveInfo()

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val testCoroutineRule = MainDispatcherRule(testDispatcher)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        `when`(applicationContext.getSystemServiceName(LauncherApps::class.java))
            .thenReturn(Context.LAUNCHER_APPS_SERVICE)
        `when`(applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE))
            .thenReturn(launcherApps)
        `when`(applicationContext.packageManager).thenReturn(packageManager)
        `when`(applicationContext.resources).thenReturn(resources)
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        doReturn(icon).`when`(activityInfo).loadIcon(packageManager)
        doReturn("Test App").`when`(activityInfo).loadLabel(packageManager)

        injector = Injector<ActionHandler>(null, null, null, null, null, null, null, null)
        injector.actions = mockActionHandler

        approvedDocHandlers = ApprovedDocHandlers(applicationContext, injector, testDispatcher)
        resolveInfo.activityInfo = activityInfo
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
    }

    private fun TestScope.getApprovedDocHandlers(
        selectionDetails: MenuManager.SelectionDetails
    ): List<ApprovedDocHandler> {
        var handlers: List<ApprovedDocHandler> = emptyList()
        val job =
            this.launch(testDispatcher) {
                approvedDocHandlers.getApprovedDocHandlersFlow(selectionDetails).collect {
                    handlers = it
                }
            }
        testScheduler.advanceUntilIdle()
        job.cancel()
        return handlers
    }

    private fun setupPackageManagerForUser(
        userHandle: UserHandle,
        className: String = "com.test.package.OtherClass",
        label: String = "Other Test App",
    ): Pair<PackageManager, ComponentName> {
        val otherContext = mock(Context::class.java)
        val otherPackageManager = mock(PackageManager::class.java)
        `when`(applicationContext.createPackageContextAsUser(eq("android"), eq(0), eq(userHandle)))
            .thenReturn(otherContext)
        `when`(otherContext.packageManager).thenReturn(otherPackageManager)

        val otherActivityInfo =
            spy(ActivityInfo()).apply {
                packageName = testPackage
                name = className
            }
        val otherResolveInfo = ResolveInfo().apply { activityInfo = otherActivityInfo }

        doReturn(label).`when`(otherActivityInfo).loadLabel(otherPackageManager)
        doReturn(icon).`when`(otherActivityInfo).loadIcon(otherPackageManager)
        `when`(otherPackageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(otherResolveInfo))

        return Pair(otherPackageManager, ComponentName(testPackage, className))
    }

    private fun captureLauncherAppsCallback(): LauncherApps.Callback {
        val callbackCaptor = ArgumentCaptor.forClass(LauncherApps.Callback::class.java)
        verify(launcherApps, atLeastOnce()).registerCallback(callbackCaptor.capture(), any())
        return callbackCaptor.value
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_returnsHandler() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        val handler = handlers[0]
        assertThat(handler.isEnabled).isTrue()
        assertThat(handler.componentName).isEqualTo(testComponent)
        assertThat(handler.label).isEqualTo("Test App")
        assertThat(handler.icon).isNull()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.categories)
            .contains(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_returnsHandler() = runTest {
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png", "image/jpeg"))
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.categories)
            .contains(DocumentsContract.CATEGORY_APPROVED_DOCUMENT_HANDLER)
    }

    @Test
    fun testGetApprovedDocHandlers_handlerNotInstalled_returnsEmptyList() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).isEmpty()
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonTrueInMetadata() = runTest {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, true) }
        activityInfo.metaData = metaData
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(true)
        assertThat(handlers[0].icon).isEqualTo(icon)
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonFalseInMetadata() = runTest {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, false) }
        activityInfo.metaData = metaData
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(false)
        assertThat(handlers[0].icon).isNull()
    }

    @Test
    fun testGetApprovedDocHandlers_unapprovedHandler_isSkipped() = runTest {
        val approvedResolveInfo = ResolveInfo()
        approvedResolveInfo.activityInfo = activityInfo

        val unapprovedActivityInfo =
            spy(ActivityInfo()).apply {
                packageName = "unapproved.package"
                name = "TestClass"
            }
        val unapprovedResolveInfo = ResolveInfo()
        unapprovedResolveInfo.activityInfo = unapprovedActivityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(approvedResolveInfo, unapprovedResolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_nullMimeTypes_usesWildcard() = runTest {
        // Verifies that for a single selection with null mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(null)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_emptyMimeTypes_usesWildcard() = runTest {
        // Verifies that for a single selection with empty mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(emptySet())
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_nullMimeTypes_usesWildcard() = runTest {
        // Verifies that for multiple selections with null mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(null)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_emptyMimeTypes_usesWildcard() = runTest {
        // Verifies that for multiple selections with empty mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(emptySet())
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
    }

    @Test
    fun testGetApprovedDocHandlers_handlerWithNoLabel_isSkipped() = runTest {
        // Verifies that a handler with no label is skipped.
        doReturn(null).`when`(activityInfo).loadLabel(packageManager)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that no handlers are returned because the only available one has no label
        assertThat(handlers).isEmpty()
    }

    @Test
    fun testGetApprovedDocHandlers_usesCache() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageChange() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture callback
        val callback = captureLauncherAppsCallback()

        // Trigger package change
        callback.onPackageChanged(testPackage, Process.myUserHandle())
        testScheduler.advanceUntilIdle() // Process flow emission

        // Call again
        getApprovedDocHandlers(selectionDetails)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageRemoved() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture callback
        val callback = captureLauncherAppsCallback()

        // Trigger package removed
        callback.onPackageRemoved(testPackage, Process.myUserHandle())
        testScheduler.advanceUntilIdle()

        // Call again
        val handlers = getApprovedDocHandlers(selectionDetails)

        // Verify result is empty
        assertThat(handlers).isEmpty()

        // Verify PM NOT queried again (cache updated directly)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageAdded() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())

        // First call to populate cache
        var handlers = getApprovedDocHandlers(selectionDetails)
        assertThat(handlers).isEmpty()
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture callback
        val callback = captureLauncherAppsCallback()

        // Now the package is found
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // Trigger package added
        callback.onPackageAdded(testPackage, Process.myUserHandle())
        testScheduler.advanceUntilIdle()

        // Call again
        handlers = getApprovedDocHandlers(selectionDetails)

        // Verify result contains the handler
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageReplaced() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture callback
        val callback = captureLauncherAppsCallback()

        // Trigger package replaced
        callback.onPackageChanged(testPackage, Process.myUserHandle())
        testScheduler.advanceUntilIdle()

        // Call again
        getApprovedDocHandlers(selectionDetails)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_multiUserSwitch_queriesCorrectPackageManager() = runTest {
        val otherUserHandle = UserHandle.of(100)
        `when`(mockActionHandler.selectedUser).thenReturn(UserId.of(otherUserHandle))

        val (otherPackageManager, otherComponent) = setupPackageManagerForUser(otherUserHandle)

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(otherComponent)

        verify(otherPackageManager).queryIntentActivities(any(Intent::class.java), anyInt())
        verify(packageManager, never()).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_multiUserSwitch_cachesIsolatedPerUser() = runTest {
        val otherUserHandle = UserHandle.of(100)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val (otherPackageManager, otherComponent) = setupPackageManagerForUser(otherUserHandle)

        // Query for current user
        `when`(mockActionHandler.selectedUser).thenReturn(UserId.CURRENT_USER)
        val handlersUser0 = getApprovedDocHandlers(selectionDetails)
        assertThat(handlersUser0).hasSize(1)
        assertThat(handlersUser0[0].componentName).isEqualTo(testComponent)

        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
        verify(otherPackageManager, never())
            .queryIntentActivities(any(Intent::class.java), anyInt())

        // Switch to other user and query
        `when`(mockActionHandler.selectedUser).thenReturn(UserId.of(otherUserHandle))
        val handlersUser100 = getApprovedDocHandlers(selectionDetails)
        assertThat(handlersUser100).hasSize(1)
        assertThat(handlersUser100[0].componentName).isEqualTo(otherComponent)

        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
        verify(otherPackageManager, times(1))
            .queryIntentActivities(any(Intent::class.java), anyInt())

        // Switch back to current user and query
        `when`(mockActionHandler.selectedUser).thenReturn(UserId.CURRENT_USER)
        val handlersUser0Again = getApprovedDocHandlers(selectionDetails)
        assertThat(handlersUser0Again).hasSize(1)
        assertThat(handlersUser0Again[0].componentName).isEqualTo(testComponent)

        // No additional queries should be made as they should be fetched from their respective
        // isolated caches
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
        verify(otherPackageManager, times(1))
            .queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_profileSpecificBroadcast_onlyInvalidatesSpecificUserCache() =
        runTest {
            val otherUserHandle = UserHandle.of(100)
            `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
                .thenReturn(listOf(resolveInfo))

            val (otherPackageManager, otherComponent) = setupPackageManagerForUser(otherUserHandle)

            // Query for current user to populate cache
            `when`(mockActionHandler.selectedUser).thenReturn(UserId.CURRENT_USER)
            getApprovedDocHandlers(selectionDetails)

            // Query for other user to populate cache
            `when`(mockActionHandler.selectedUser).thenReturn(UserId.of(otherUserHandle))
            getApprovedDocHandlers(selectionDetails)

            val callback = captureLauncherAppsCallback()

            // Trigger package added broadcast for the OTHER user
            callback.onPackageAdded(testPackage, otherUserHandle)
            testScheduler.advanceUntilIdle()

            // Query again for other user
            `when`(mockActionHandler.selectedUser).thenReturn(UserId.of(otherUserHandle))
            val handlersUser100 = getApprovedDocHandlers(selectionDetails)
            assertThat(handlersUser100).hasSize(1)
            assertThat(handlersUser100[0].componentName).isEqualTo(otherComponent)

            // Verify otherPackageManager queried again (total 2 times)
            verify(otherPackageManager, times(2))
                .queryIntentActivities(any(Intent::class.java), anyInt())

            // Switch back to current user and query
            `when`(mockActionHandler.selectedUser).thenReturn(UserId.CURRENT_USER)
            val handlersUser0 = getApprovedDocHandlers(selectionDetails)
            assertThat(handlersUser0).hasSize(1)
            assertThat(handlersUser0[0].componentName).isEqualTo(testComponent)

            // Verify packageManager NOT queried again (still 1 time)
            verify(packageManager, times(1))
                .queryIntentActivities(any(Intent::class.java), anyInt())
        }

    @Test
    fun testPackageEventForUnloadedUser_isGracefullyIgnored() = runTest {
        val unloadedUserHandle = UserHandle.of(999)

        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to start monitoring and populate cache
        getApprovedDocHandlers(selectionDetails)

        // Capture callback
        val callback = captureLauncherAppsCallback()

        // Trigger an event for a user that hasn't been loaded into the cache yet
        callback.onPackageAdded(testPackage, unloadedUserHandle)
        testScheduler.advanceUntilIdle()

        val handlers = getApprovedDocHandlers(selectionDetails)
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)
    }

    @Test
    fun testUpdateEvents_emitsOnCacheUpdate() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        var emitted = false
        val job =
            launch(testDispatcher) { approvedDocHandlers.updateEvents.collect { emitted = true } }

        // Trigger update
        getApprovedDocHandlers(selectionDetails)

        assertThat(emitted).isTrue()

        job.cancel()
    }
}
