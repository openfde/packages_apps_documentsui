/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui

import android.app.Activity
import android.app.ActivityOptions
import android.app.UiAutomation
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.NetworkCapabilities
import android.os.LocaleList
import android.os.RemoteException
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.LayoutRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.base.Features
import com.android.documentsui.base.Features.RuntimeFeatures
import com.android.documentsui.base.NetworkMonitor
import com.android.documentsui.base.NetworkMonitorImpl
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.bots.Bots
import com.android.documentsui.dirlist.DirectoryFragment.TICK_VISIBLE_DURATION_MS
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.prefs.LocalPreferences
import com.android.documentsui.util.FlagUtils.Companion.isDesktopFileHandlingFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isDesktopUxPhase2FlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isGetInfoDialogEnabled
import com.android.documentsui.util.FlagUtils.Companion.isHomeScreenFilesFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isIncludeRemoteRootsInRecentsEnabled
import com.android.documentsui.util.FlagUtils.Companion.isMovingContentIntoPrivateSpaceEnabled
import com.android.documentsui.util.FlagUtils.Companion.isSearchV2Enabled
import com.android.documentsui.util.FlagUtils.Companion.isSingleClickToSelectEnabled
import com.android.documentsui.util.FlagUtils.Companion.isSupportVisibleBackgroundUserFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isSyncStateEnabled
import com.android.documentsui.util.FlagUtils.Companion.isTrashFlowEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseAllfilesRootForRecentsEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseFileSummaryEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseLocalSearchProviderEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUseNewOpenWithEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUsePeekPreviewFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isVisualSignalsFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isZipNgFlagEnabled
import java.io.IOException
import java.util.Locale
import java.util.function.Supplier
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit

/**
 * Provides basic test environment for UI tests:
 * - Launches activity
 * - Creates and gives access to test root directories and test files
 * - Cleans up the test environment
 */
@RunWith(TestRunner::class)
abstract class ActivityTestJunit4<T : Activity?> {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    lateinit var bots: Bots

    @JvmField var device: UiDevice? = null

    @JvmField var context: Context? = null

    @JvmField protected var userId: UserId? = null
    var automation: UiAutomation? = null

    @JvmField var features: Features? = null

    /**
     * Returns the root that will be opened within the activity. By default tests are started with
     * one of the test roots. Override the method if you want to open different root on start.
     *
     * @return Root that will be opened. Return null if you want to open activity's default root.
     */
    protected open var initialRoot: RootInfo? = null

    @JvmField var rootDir0: RootInfo? = null

    @JvmField var rootDir1: RootInfo? = null

    @JvmField protected var mDocsHelper: DocumentsProviderHelper? = null

    @JvmField protected var mActivityScenario: ActivityScenario<T?>? = null
    @LayoutRes protected var activityLayoutId: Int? = null
    private var initialScreenOffTimeoutValue: String? = null
    private var initialSleepTimeoutValue: String? = null
    private var testIsOnline = true
    private var testTickDuration = TICK_VISIBLE_DURATION_MS
    private var networkMonitor: NetworkMonitorImpl? = null
    @Mock private lateinit var network: android.net.Network

    protected val testingProviderAuthority: String
        /**
         * Returns the authority of the testing provider begin used. By default it's StubProvider's
         * authority.
         *
         * @return Authority of the provider.
         */
        get() = StubProvider.DEFAULT_AUTHORITY

    /** Resolves testing roots. */
    @Throws(RemoteException::class)
    protected open fun setupTestingRoots() {
        rootDir0 = mDocsHelper!!.getRoot(StubProvider.ROOT_0_ID)
        rootDir1 = mDocsHelper!!.getRoot(StubProvider.ROOT_1_ID)
        this.initialRoot = rootDir0
    }

    @Before
    open fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // NOTE: Must be the "target" context, else security checks in content provider will fail.
        context = InstrumentationRegistry.getInstrumentation().targetContext
        userId = UserId.DEFAULT_USER
        automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        features = RuntimeFeatures(context!!.getResources(), null)

        Configurator.getInstance().toolType = MotionEvent.TOOL_TYPE_MOUSE

        mDocsHelper =
            DocumentsProviderHelper(
                userId,
                this.testingProviderAuthority,
                context,
                this.testingProviderAuthority,
            )

        device!!.setOrientationNatural()
        device!!.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)

        disableScreenOffAndSleepTimeouts()
        // Start with the feature disabled, so the welcome dialog doesn't interfere with the test.
        LocalPreferences.setSummaryConsent(context, LocalPreferences.CONSENT_REJECTED)

        setupTestingRoots()
        ActivityTest.closeNonDocsUiWindows(context, device)

        if (isSyncStateEnabled()) {
            // Fake the online state.
            val isCurrentlyConnectedFunction: (NetworkCapabilities) -> Boolean = { _ ->
                testIsOnline
            }
            networkMonitor =
                NetworkMonitor.create(context!!, isCurrentlyConnectedFunction) as NetworkMonitorImpl
            NetworkMonitor.setTestInstance(networkMonitor)
        }

        launchActivity()

        mActivityScenario?.onActivity({ activity ->
            activityLayoutId = (activity as? BaseActivity)?.layoutId
            bots = Bots(device, automation, context, TIMEOUT, activityLayoutId)
            if (activityLayoutId != null) {
                logLayout()
            }

            if (isSyncStateEnabled()) {
                // Allow the adjustment of the inline sync tick icon visibility duration.
                val tickDurationSupplier = Supplier<Int> { testTickDuration }
                (activity as? BaseActivity)?.setTickDurationSupplierForTest(tickDurationSupplier)
            }
        })
        logLocales()
        logFeatureFlags()

        if (activityLayoutId != null) {
            // Since at the launch of activity, ROOT_0 and ROOT_1 have no files, drawer will
            // automatically open for phone devices. Espresso register click() as (x, y)
            // MotionEvents, so if a drawer is on top of a file we want to select, it will actually
            // click the drawer. Thus to start a clean state, we always try to close first.
            // Only attempt to close the drawer if it's an instance of BaseActivity (i.e.
            // FilesActivity, PickerActivity), other activities don't have a drawer.
            bots.roots!!.closeDrawer()
        }
    }

    @After
    fun tearDown() {
        device!!.unfreezeRotation()
        restoreScreenOffAndSleepTimeouts()
        mActivityScenario?.close()
        NetworkMonitor.setTestInstance(null)
    }

    /** Set the online state that the NetworkMonitor returns and trigger a network notification. */
    fun setIsOnline(isOnline: Boolean) {
        if (networkMonitor == null) {
            Log.w(TAG, "networkMonitor is null")
            return
        }
        testIsOnline = isOnline
        // Trigger network notification.
        networkMonitor!!.networkCallback.onCapabilitiesChanged(network, NetworkCapabilities())
    }

    /* Set the inline sync tick icon visibility duration. */
    fun setTickVisibleDuration(tickDuration: Int) {
        testTickDuration = tickDuration
    }

    protected open fun launchActivity() {
        val intent = Intent(context, FilesActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        val root = this.initialRoot
        if (root != null) {
            intent.setAction(Intent.ACTION_VIEW)
            intent.setDataAndType(root.uri, DocumentsContract.Root.MIME_TYPE_ITEM)
        }

        // If the TestRunner is running tests with different screen sizes, we need to launch the
        // activity in fullscreen mode so that the activity takes up the full device screen.
        if (System.getProperty("documentsui_fullscreen") != null) {
            Log.d(TAG, "using launchWindowingMode=FULLSCREEN")
            val options = ActivityOptions.makeBasic()
            options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
            mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
        } else {
            mActivityScenario = ActivityScenario.launch(intent)
        }
    }

    /**
     * Programmatically switches the root to the one with the given label.
     *
     * @param label The label of the root to switch to.
     */
    protected fun switchRoot(label: String) {
        val scenario =
            checkNotNull(mActivityScenario) {
                "mActivityScenario is null. Ensure launchActivity() has run."
            }
        bots.navigation.switchRoot(label, scenario as ActivityScenario<out BaseActivity>)
    }

    /**
     * Programmatically opens and enters a new folder with the given label.
     *
     * @param label The label of the folder to open.
     */
    protected fun openFolder(label: String) {
        val scenario =
            checkNotNull(mActivityScenario) {
                "mActivityScenario is null. Ensure launchActivity() has run."
            }
        bots.navigation.openFolder(label, scenario as ActivityScenario<out BaseActivity>)
    }

    protected fun setNotificationAccess(enabled: Boolean) {
        mActivityScenario?.onActivity({ activity ->
            try {
                bots.notifications.setNotificationAccess(activity, enabled)
            } catch (e: Exception) {
                Log.d(TAG, "Cannot set notification access. ", e)
            }
        })
    }

    @Throws(IOException::class)
    private fun disableScreenOffAndSleepTimeouts() {
        initialScreenOffTimeoutValue =
            device!!.executeShellCommand("settings get system screen_off_timeout").trimEnd()
        initialSleepTimeoutValue =
            device!!.executeShellCommand("settings get secure sleep_timeout").trimEnd()
        Log.d(TAG, "initialScreenOffTimeoutValue = $initialScreenOffTimeoutValue")
        Log.d(TAG, "initialSleepTimeoutValue = $initialSleepTimeoutValue")
        device!!.executeShellCommand("settings put system screen_off_timeout -1")
        device!!.executeShellCommand("settings put secure sleep_timeout -1")
    }

    @Throws(IOException::class)
    private fun restoreScreenOffAndSleepTimeouts() {
        requireNotNull(initialScreenOffTimeoutValue) {
            "Require the initial screen off timeout value to be non-null"
        }
        requireNotNull(initialSleepTimeoutValue) {
            "Require the sleep timeout value to be non-null"
        }
        try {
            device!!.executeShellCommand(
                "settings put system screen_off_timeout $initialScreenOffTimeoutValue"
            )
            device!!.executeShellCommand(
                "settings put secure sleep_timeout $initialSleepTimeoutValue"
            )
        } finally {
            initialScreenOffTimeoutValue = null
            initialSleepTimeoutValue = null
        }
    }

    private fun logLayout() {
        val layoutType =
            if (bots.main.inFixedLayout()) {
                "Fixed layout"
            } else if (bots.main.inNavRailLayout()) {
                "Nav rail layout"
            } else if (bots.main.inDrawerLayout()) {
                "Drawer layout"
            } else {
                "Unknown layout (should not happen)"
            }
        Log.d(TAG, "Test is running with layout: $layoutType.")
    }

    private fun logFeatureFlags() {
        Log.d(TAG, "Flag isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}")
        Log.d(
            TAG,
            "Flag isDesktopFileHandlingFlagEnabled() = ${isDesktopFileHandlingFlagEnabled()}",
        )
        Log.d(TAG, "Flag isSearchV2Enabled() = ${isSearchV2Enabled()}")
        Log.d(TAG, "Flag isUsePeekPreviewFlagEnabled() = ${isUsePeekPreviewFlagEnabled()}")
        Log.d(TAG, "Flag isVisualSignalsFlagEnabled() = ${isVisualSignalsFlagEnabled()}")
        Log.d(TAG, "Flag isZipNgFlagEnabled() = ${isZipNgFlagEnabled()}")
        Log.d(TAG, "Flag isSyncStateEnabled() = ${isSyncStateEnabled()}")
        Log.d(TAG, "Flag isDesktopUxPhase2FlagEnabled() = ${isDesktopUxPhase2FlagEnabled()}")
        Log.d(TAG, "Flag isSingleClickToSelectEnabled() = ${isSingleClickToSelectEnabled()}")
        Log.d(TAG, "Flag isTrashFlowEnabled() = ${isTrashFlowEnabled()}")
        Log.d(
            TAG,
            "Flag isMovingContentIntoPrivateSpaceEnabled() = " +
                "${isMovingContentIntoPrivateSpaceEnabled()}",
        )
        Log.d(
            TAG,
            "Flag isSupportVisibleBackgroundUserFlagEnabled() = " +
                "${isSupportVisibleBackgroundUserFlagEnabled()}",
        )
        Log.d(TAG, "Flag isHomeScreenFilesFlagEnabled() = ${isHomeScreenFilesFlagEnabled()}")
        Log.d(TAG, "Flag isUseFileSummaryEnabled() = ${isUseFileSummaryEnabled()}")
        Log.d(TAG, "Flag isUseLocalSearchProviderEnabled() = ${isUseLocalSearchProviderEnabled()}")
        Log.d(
            TAG,
            "Flag isUseAllfilesRootForRecentsEnabled() = ${isUseAllfilesRootForRecentsEnabled()}",
        )
        Log.d(TAG, "Flag isUseNewOpenWithEnabled() = ${isUseNewOpenWithEnabled()}")
        Log.d(TAG, "Flag isGetInfoDialogEnabled() = ${isGetInfoDialogEnabled()}")
        Log.d(
            TAG,
            "Flag isIncludeRemoteRootsInRecentsEnabled() = " +
                "${isIncludeRemoteRootsInRecentsEnabled()}",
        )
    }

    private fun logLocales() {
        val config: Configuration = context!!.resources.configuration
        val locales: LocaleList = config.getLocales()
        val primaryLocale: Locale = locales.get(0) // User's primary preferred locale.

        Log.d(TAG, "Primary Locale: ${primaryLocale.toLanguageTag()}")
    }

    companion object {
        const val TIMEOUT = 5000L
        const val TAG = "ActivityTestJunit4"
    }
}
