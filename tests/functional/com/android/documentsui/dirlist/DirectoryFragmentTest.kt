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
package com.android.documentsui.dirlist

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.ActionHandler
import com.android.documentsui.ActionModeController
import com.android.documentsui.BaseActivity
import com.android.documentsui.DirectoryResult
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.ProfileTabsController
import com.android.documentsui.R
import com.android.documentsui.SelectionBarController
import com.android.documentsui.base.NetworkMonitor
import com.android.documentsui.base.State.MODE_GRID
import com.android.documentsui.base.State.MODE_LIST
import com.android.documentsui.flags.Flags
import com.android.documentsui.flags.Flags.FLAG_DESKTOP_FILE_HANDLING_RO
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_NEW_OPEN_WITH
import com.android.documentsui.roots.ProvidersAccess
import com.android.documentsui.roots.RootCursorWrapper
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.FileOperationService.OPERATION_DELETE
import com.android.documentsui.services.FileOperationService.OPERATION_TRASH
import com.android.documentsui.services.Job.STATE_CANCELED
import com.android.documentsui.services.Job.STATE_COMPLETED
import com.android.documentsui.services.Job.STATE_CREATED
import com.android.documentsui.services.Job.STATE_SET_UP
import com.android.documentsui.services.Job.STATE_STARTED
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.SortModels
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestEvents
import com.android.documentsui.testing.TestProvidersAccess
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@MediumTest
class DirectoryFragmentTest {
    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    private lateinit var env: TestEnv
    private lateinit var injector: Injector<ActionHandler>
    private lateinit var fragment: DirectoryFragmentWithActivity
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var activity: BaseActivity

    @Before
    fun setUp() {
        env = TestEnv.create()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)
        val inflater = LayoutInflater.from(context)
        val container = FrameLayout(context)
        env.state.sortModel = SortModels.createTestSortModel()
        env.state.stack.changeRoot(TestProvidersAccess.DOWNLOADS)

        // Mock injector.
        injector = spy(env.injector)
        networkMonitor = spy(injector.networkMonitor)
        injector.networkMonitor = networkMonitor
        injector.profileTabsController = mock(ProfileTabsController::class.java)
        injector.menuManager = mock(MenuManager::class.java)
        injector.actions = mock(ActionHandler::class.java)
        doNothing().`when`(injector.actions).loadDocumentsForCurrentStack()
        `when`(injector.getActionHandler(any())).thenReturn(injector.actions)
        injector.actionModeController = mock(ActionModeController::class.java)
        `when`(injector.actionModeController!!.reset(any(), any()))
            .thenReturn(injector.actionModeController)
        // SelectionBarController itself can't be mocked, initialize a real one.
        injector.selectionBarController =
            SelectionBarController(
                MaterialToolbar(context),
                MaterialToolbar(context),
                injector.focusManager,
                injector.menuManager,
                injector.selectionMgr,
            )
        // Mock updateSharedSelectionTracker so the test selection manager from the TestEnv won't
        // be replaced with something we can't mock.
        doNothing().`when`(injector).updateSharedSelectionTracker(any())
        // Mock the activity and its dependencies.
        activity = mock(BaseActivity::class.java)
        val testDispatcher = StandardTestDispatcher()
        val ioTestDispatcher = UnconfinedTestDispatcher(testDispatcher.scheduler)
        val mockApp = mock(Application::class.java)
        `when`(mockApp.contentResolver).thenReturn(context.contentResolver)
        `when`(mockApp.applicationContext).thenReturn(mockApp)
        val summariesViewModel = SummariesViewModel(mockApp, ioTestDispatcher)
        `when`(activity.displayState).thenReturn(env.state)
        `when`(activity.getInjector()).thenReturn(injector)
        `when`(activity.getSystemService(Context.ACTIVITY_SERVICE))
            .thenReturn(mock(ActivityManager::class.java))
        `when`(activity.packageManager).thenReturn(mock(PackageManager::class.java))
        `when`(activity.contentResolver).thenReturn(mock(ContentResolver::class.java))
        `when`(activity.selectedUser).thenReturn(env.userId)
        `when`(activity.resources).thenReturn(context.resources)
        `when`(activity.applicationContext).thenReturn(context.applicationContext)
        `when`(activity.providersAccess).thenReturn(mock(ProvidersAccess::class.java))

        fragment = DirectoryFragmentWithActivity(context, activity, summariesViewModel)
        fragment.arguments = Bundle()

        // Need this to create selection tracker inside onActivityCreated().
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
        fragment.onCreateView(inflater, container, null)
        fragment.onActivityCreated(null)
    }

    fun prepareLooperForUpdateLayout() {
        // updateLayout() will then trigger a SwipeRefreshLayout to set the offset, which internally
        // triggers CircularProgressDrawable's animation. The animation requires a valid Looper
        // thread.
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
    }

    @Test
    fun testOnModelUpdate_notifySelectionChange() {
        val authority = TestProvidersAccess.HOME.authority

        // Match model update ID format: "0|com.android.externalstorage.documents|file1".
        fun getModelId(name: String): String = "${env.userId}|$authority|$name"
        // Put 2 files into selection manager.
        assertThat(injector.selectionMgr.selection).hasSize(0)
        injector.selectionMgr.select(getModelId("file1"))
        injector.selectionMgr.select(getModelId("file2"))
        assertThat(injector.selectionMgr.selection).hasSize(2)

        // Add an observer to the selection manager.
        val observer =
            object : SelectionTracker.SelectionObserver<String>() {
                var selections = MutableSelection<String>()

                override fun onSelectionChanged() {
                    injector.selectionMgr.copySelection(selections)
                }
            }
        injector.selectionMgr.addObserver(observer)

        // model.update() will trigger a updateLayout() call
        prepareLooperForUpdateLayout()
        // Trigger a model update with only "file2" in it to simulate the deletion of "file1".
        val cursor =
            MatrixCursor(
                arrayOf(
                    RootCursorWrapper.COLUMN_AUTHORITY,
                    RootCursorWrapper.COLUMN_USER_ID,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
            )
        val row = cursor.newRow()
        row.add(RootCursorWrapper.COLUMN_AUTHORITY, authority)
        row.add(RootCursorWrapper.COLUMN_USER_ID, env.userId)
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, "file2")
        val result = DirectoryResult()
        result.cursor = cursor
        env.model.update(result)

        // Assert that only "file2" is left in the selection model and its observer.
        assertThat(injector.selectionMgr.selection).hasSize(1)
        assertThat(injector.selectionMgr.selection).contains(getModelId("file2"))
        assertThat(observer.selections).hasSize(1)
        assertThat(observer.selections).contains(getModelId("file2"))
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_inGridMode_createsItemDecorationInvalidator_whenMaterial3Enabled() {
        fragment.mItemDecorationInvalidator = null

        // Trigger an updateLayout for GRID mode which will require invalidating the item
        // decorations.
        env.state.derivedMode = MODE_GRID
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        // Check that a ItemDecorationInvalidator exists.
        assertNotNull(fragment.mItemDecorationInvalidator)
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_inGridMode_doesNotCreateItemDecorationInvalidator_whenMaterial3Disabled() {
        fragment.mItemDecorationInvalidator = null

        // Trigger an updateLayout for GRID mode but since the useMaterial3 flag is off, no item
        // decorations are being used.
        env.state.derivedMode = MODE_GRID
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        // Check that no ItemDecorationInvalidator was created.
        assertNull(fragment.mItemDecorationInvalidator)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_inListMode_doesNotCreateItemDecorationInvalidator() {
        fragment.mItemDecorationInvalidator = null

        // Trigger an updateLayout for List mode which will not require invalidating the item
        // decorations.
        env.state.derivedMode = MODE_LIST
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        // Check that no ItemDecorationInvalidator exists.
        assertNull(fragment.mItemDecorationInvalidator)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_inGridMode_doesNotReuseFinishedItemDecorationInvalidator() {
        val existingItemDecorationInvalidator = mock(ItemDecorationInvalidator::class.java)
        `when`(existingItemDecorationInvalidator.hasFinishedInvalidation()).thenReturn(true)
        fragment.mItemDecorationInvalidator = existingItemDecorationInvalidator

        // Trigger an updateLayout for GRID mode which will require invalidating the item
        // decorations.
        env.state.derivedMode = MODE_GRID
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        // Check that a new ItemDecorationInvalidator has been created.
        assertNotEquals(existingItemDecorationInvalidator, fragment.mItemDecorationInvalidator)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_inGridMode_reusesUnfinishedItemDecorationInvalidator() {
        val existingItemDecorationInvalidator = mock(ItemDecorationInvalidator::class.java)
        `when`(existingItemDecorationInvalidator.hasFinishedInvalidation()).thenReturn(false)
        fragment.mItemDecorationInvalidator = existingItemDecorationInvalidator

        // Trigger an updateLayout for GRID mode which will require invalidating the item
        // decorations.
        env.state.derivedMode = MODE_GRID
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        // Check that the existing ItemDecorationInvalidator is still used.
        assertEquals(existingItemDecorationInvalidator, fragment.mItemDecorationInvalidator)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testUpdateLayout_listMode_setsPaddingBottom() {
        // Trigger updateLayout for LIST mode
        env.state.derivedMode = MODE_LIST
        prepareLooperForUpdateLayout()
        fragment.onViewModeChanged()

        val expectedPadding =
            fragment.resources.getDimensionPixelSize(getRes(R.dimen.list_container_padding_bottom))
        assertNotEquals(0, expectedPadding)
        assertEquals(expectedPadding, fragment.getRecyclerView().paddingBottom)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testItemDecorationInvalidator_teardown_whenFragmentDestroyed() {
        val itemDecorationInvalidator = mock(ItemDecorationInvalidator::class.java)
        fragment.mItemDecorationInvalidator = itemDecorationInvalidator

        verify(itemDecorationInvalidator, never()).teardown()

        fragment.onDestroyView()

        verify(itemDecorationInvalidator).teardown()
        assertNull(fragment.mItemDecorationInvalidator)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
    @RequiresFlagsEnabled(android.provider.Flags.FLAG_ENABLE_SYNC_STATE)
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES, FLAG_USE_MATERIAL3)
    fun testAddsNetworkListener() {
        verify(networkMonitor).addNetworkListener(any())
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    @DisableFlags(FLAG_USE_NEW_OPEN_WITH)
    fun testOnItemActivated_doubleTap_opensItemOnce_useNewOpenWithFlagDisabled() {
        val documentHolder = mock(DocumentHolder::class.java)
        `when`(documentHolder.inPreviewIconRegion(any())).thenReturn(false)
        val item = DocumentItemDetails(documentHolder)
        val event = TestEvents.Touch.TAP

        // First tap.
        fragment.onItemActivated(item, event)
        // Second tap immediately after.
        fragment.onItemActivated(item, event)

        // Verify that the item is opened only once.
        verify(injector.actions, times(1))
            .openItem(item, ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_DESKTOP_FILE_HANDLING_RO, FLAG_USE_NEW_OPEN_WITH)
    fun testOnItemActivated_doubleTap_opensItemOnce_useNewOpenWithFlagEnabled() {
        val documentHolder = mock(DocumentHolder::class.java)
        `when`(documentHolder.inPreviewIconRegion(any())).thenReturn(false)
        val item = DocumentItemDetails(documentHolder)
        val event = TestEvents.Touch.TAP

        // First tap.
        fragment.onItemActivated(item, event)
        // Second tap immediately after.
        fragment.onItemActivated(item, event)

        // Verify that the item is opened only once.
        verify(injector.actions, times(1))
            .openItem(item, ActionHandler.VIEW_TYPE_REGULAR, ActionHandler.VIEW_TYPE_NONE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    @DisableFlags(FLAG_USE_NEW_OPEN_WITH)
    fun testOnItemActivated_tapTwiceWithGap_opensItemTwice_useNewOpenWithFlagDisabled() {
        val documentHolder = mock(DocumentHolder::class.java)
        `when`(documentHolder.inPreviewIconRegion(any())).thenReturn(false)
        val item = DocumentItemDetails(documentHolder)
        val event = TestEvents.Touch.TAP

        // First tap.
        fragment.onItemActivated(item, event)
        // Wait for double tap timeout.
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 100L)
        // Second tap immediately after.
        fragment.onItemActivated(item, event)

        // Verify that the item is opened twice.
        verify(injector.actions, times(2))
            .openItem(item, ActionHandler.VIEW_TYPE_PREVIEW, ActionHandler.VIEW_TYPE_REGULAR)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_DESKTOP_FILE_HANDLING_RO, FLAG_USE_NEW_OPEN_WITH)
    fun testOnItemActivated_tapTwiceWithGap_opensItemTwice_useNewOpenWithFlagEnabled() {
        val documentHolder = mock(DocumentHolder::class.java)
        `when`(documentHolder.inPreviewIconRegion(any())).thenReturn(false)
        val item = DocumentItemDetails(documentHolder)
        val event = TestEvents.Touch.TAP

        // First tap.
        fragment.onItemActivated(item, event)
        // Wait for double tap timeout.
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout().toLong() + 100L)
        // Second tap immediately after.
        fragment.onItemActivated(item, event)

        // Verify that the item is opened twice.
        verify(injector.actions, times(2))
            .openItem(item, ActionHandler.VIEW_TYPE_REGULAR, ActionHandler.VIEW_TYPE_NONE)
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testJobProgressObserverRefreshesOnDestructiveJobCompletionWhileSearching() {
        `when`(activity.isSearching).thenReturn(true)
        val receiver = fragment.jobProgressObserver

        for (opType in listOf(OPERATION_DELETE, OPERATION_TRASH)) {
            for (state in listOf(STATE_CREATED, STATE_STARTED, STATE_SET_UP, STATE_CANCELED)) {
                val progress = JobProgress("jobId", opType, state, "file.txt", 1, false)
                val intent = Intent(ACTION_PROGRESS)
                intent.putParcelableArrayListExtra(EXTRA_PROGRESS, arrayListOf(progress))
                fragment.onRefreshCalled = false
                receiver.onReceive(fragment.context, intent)
                assertThat(fragment.onRefreshCalled).isFalse()
            }

            val progress = JobProgress("jobId", opType, STATE_COMPLETED, "file.txt", 1, false)
            val intent = Intent(ACTION_PROGRESS)
            intent.putParcelableArrayListExtra(EXTRA_PROGRESS, arrayListOf(progress))
            fragment.onRefreshCalled = false
            receiver.onReceive(fragment.context, intent)
            assertThat(fragment.onRefreshCalled).isTrue()
        }
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    fun testJobProgressObserverDoesNothingWhileNotSearching() {
        `when`(activity.isSearching).thenReturn(false)
        val receiver = fragment.jobProgressObserver

        for (opType in listOf(OPERATION_DELETE, OPERATION_TRASH)) {
            val progress = JobProgress("jobId", opType, STATE_COMPLETED, "file.txt", 1, false)
            val intent = Intent(ACTION_PROGRESS)
            intent.putParcelableArrayListExtra(EXTRA_PROGRESS, arrayListOf(progress))
            fragment.onRefreshCalled = false
            receiver.onReceive(fragment.context, intent)
            assertThat(fragment.onRefreshCalled).isFalse()
        }
    }
}

// DirectoryFragment requires a valid activity, use this class to provide a fake one for it so we
// don't need to actually launch an activity.
class DirectoryFragmentWithActivity(
    private val context: Context,
    private val fakeActivity: BaseActivity,
    private val summariesViewModel: SummariesViewModel,
) : DirectoryFragment() {
    private var view: View? = null

    override fun getBaseActivity(): BaseActivity = fakeActivity

    override fun getContext(): Context = context

    var onRefreshCalled = false

    override fun onRefresh() {
        onRefreshCalled = true
    }

    override fun createSummariesViewModel(): SummariesViewModel {
        return summariesViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    fun getRecyclerView(): RecyclerView = view!!.findViewById(R.id.dir_list)

    val jobProgressObserver: BroadcastReceiver
        get() {
            return this.mJobProgressObserver
        }
}
