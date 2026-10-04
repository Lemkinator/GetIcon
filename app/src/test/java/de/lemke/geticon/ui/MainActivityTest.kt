/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.geticon.ui

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_SEARCH_MODE
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.geticon.BuildConfig
import de.lemke.geticon.R
import de.lemke.geticon.di.DispatchersModule
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import leakcanary.AppWatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowToast
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as oneuiDesignR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@UninstallModules(DispatchersModule::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val processApkStub: ProcessApkUseCase = mockk(relaxed = true)

    @BindValue
    @IoDispatcher
    @JvmField
    val ioDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        }
    }

    @Test
    fun onCreate_onboardingRequired_returnsEarly() {
        settings.lastVersionCode = -1
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun onSaveInstanceState_ready_savesState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_search))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe true
            }
        }
    }

    @Test
    fun onSaveInstanceState_ready_bundleContainsSearchModeKey() {
        // onSaveInstanceState is a protected override; ActivityScenario has no public entry point
        // to inspect the bundle it builds, so drive the lifecycle via ActivityController instead.
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            controller.get().onOptionsItemSelected(RoboMenuItem(R.id.menu_item_search))
            shadowOf(Looper.getMainLooper()).idle()
            val outState = Bundle()
            controller.pause().saveInstanceState(outState)
            outState.getBoolean(COMMONUTILS_KEY_IS_SEARCH_MODE) shouldBe true
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun onSaveInstanceState_notReady_returnsEarly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_search))
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe true
                activity.isUIReady = false
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe false
            }
        }
    }

    @Test
    fun onNewIntent_actionSearch_setsQuery() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_search))
            shadowOf(Looper.getMainLooper()).idle()
            controller.newIntent(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "sometext"))
            shadowOf(Looper.getMainLooper()).idle()
            activity.findViewById<TextView>(appcompatR.id.search_src_text).text.toString() shouldBe "sometext"
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun onNewIntent_nonSearch_doesNothing() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            controller.newIntent(Intent("some.other.action"))
            shadowOf(Looper.getMainLooper()).idle()
            activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe false
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun onOptionsItemSelected_searchItem_startsSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_search)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe true
                // End search mode to trigger onEnd lambda → applyFilter()
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endSearchMode()
                activity.findViewById<NoEntryView>(R.id.noEntryView).visibility shouldBe View.GONE
            }
        }
    }

    @Test
    fun onOptionsItemSelected_unknownItem_returnsFalseWithoutSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = RoboMenuItem(android.R.id.home)
                activity.onOptionsItemSelected(item) shouldBe false
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe false
            }
        }
    }

    @Test
    fun applyFilter_direct_callsSetSearchFilter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.applyFilter("test")
                activity.findViewById<NoEntryView>(R.id.noEntryView).visibility shouldBe View.VISIBLE
            }
        }
    }

    @Test
    fun apkImport_invalid_showsToastOnce() {
        coEvery { processApkStub(any()) } returns ApkProcessResult.InvalidApk
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].onApkPicked(Uri.parse("content://test"))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.shownToastCount() shouldBe 1
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_no_valid_file_selected)
                ViewModelProvider(activity)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Idle
            }
        }
    }

    @Test
    fun apkImport_imported_startsIconActivity() {
        val appInfo = ApplicationInfo().apply { packageName = "com.test" }
        coEvery { processApkStub(any()) } returns ApkProcessResult.Success(appInfo)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java]
                    .onApkPicked(Uri.parse("content://test"))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity!!
                started.component!!.className shouldBe IconActivity::class.java.name
                val extra = IntentCompat.getParcelableExtra(started, IconActivity.KEY_APPLICATION_INFO, ApplicationInfo::class.java)!!
                extra.packageName shouldBe "com.test"
                ViewModelProvider(activity)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Idle
            }
        }
    }

    @Test
    fun apkImport_invalidWhilePaused_showsToastOnceAfterRecreation() {
        coEvery { processApkStub(any()) } returns ApkProcessResult.InvalidApk
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().pause()
        try {
            val paused = controller.get()
            ViewModelProvider(paused)[MainViewModel::class.java].onApkPicked(Uri.parse("content://test"))
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.shownToastCount() shouldBe 0
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            controller.resume()
            shadowOf(Looper.getMainLooper()).idle()
            val recreated = controller.get()
            recreated shouldNotBeSameInstanceAs paused
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe recreated.getString(commonutilsR.string.commonutils_error_no_valid_file_selected)
            ViewModelProvider(recreated)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Idle
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun apkImport_importedWhilePaused_startsIconActivityOnceAfterRecreation() {
        val appInfo = ApplicationInfo().apply { packageName = "com.test" }
        coEvery { processApkStub(any()) } returns ApkProcessResult.Success(appInfo)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().pause()
        try {
            val paused = controller.get()
            ViewModelProvider(paused)[MainViewModel::class.java].onApkPicked(Uri.parse("content://test"))
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(paused).nextStartedActivity shouldBe null
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            controller.resume()
            shadowOf(Looper.getMainLooper()).idle()
            val recreated = controller.get()
            recreated shouldNotBeSameInstanceAs paused
            val shadowActivity = shadowOf(recreated)
            shadowActivity.nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            shadowActivity.nextStartedActivity shouldBe null
            ViewModelProvider(recreated)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Idle
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun apkImport_latchDropsLaunch_opensIconActivityOnNextResume() {
        val appInfo = ApplicationInfo().apply { packageName = "com.test" }
        coEvery { processApkStub(any()) } returns ApkProcessResult.Success(appInfo)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].onApkPicked(Uri.parse("content://test"))
                activity.singleLaunchActivity(Intent(activity, CommonUtilsAboutActivity::class.java)) shouldBe true
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
                ViewModelProvider(activity)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Imported(appInfo)
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
                ViewModelProvider(activity)[MainViewModel::class.java].apkImport.value shouldBe ApkImport.Idle
            }
        }
    }

    @Test
    fun navItem_extractApk_launchesFilePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(RoboMenuItem(R.id.extract_icon_from_apk_dest))
                result shouldBe true
                shadowOf(activity).nextStartedActivityForResult?.intent?.type shouldBe
                    "application/vnd.android.package-archive"
            }
        }
    }

    @Test
    fun navItem_extractApkTwice_launchesFilePickerOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(RoboMenuItem(R.id.extract_icon_from_apk_dest))
                activity.onNavigationItemSelected(RoboMenuItem(R.id.extract_icon_from_apk_dest))
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivityForResult?.intent?.type shouldBe "application/vnd.android.package-archive"
                shadowActivity.nextStartedActivityForResult shouldBe null
            }
        }
    }

    @Test
    fun pickedApk_resultWhilePaused_startsIconActivityOnResume() {
        val appInfo = ApplicationInfo().apply { packageName = "com.test" }
        coEvery { processApkStub(any()) } returns ApkProcessResult.Success(appInfo)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var pickerIntent: Intent
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(RoboMenuItem(R.id.extract_icon_from_apk_dest))
                pickerIntent = shadowOf(activity).nextStartedActivity
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                shadowOf(activity).receiveResult(pickerIntent, Activity.RESULT_OK, Intent().setData(Uri.parse("content://test.apk")))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            }
        }
    }

    @Test
    fun drawerItem_doubleTap_navigatesOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navigationView = activity.findViewById<DrawerNavigationView>(R.id.navigationView)
                val aboutItem = navigationView.findMenuItem(R.id.commonutils_about_dest) as MenuItemImpl
                aboutItem.invoke() shouldBe true
                aboutItem.invoke() shouldBe false
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun navItem_about_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(RoboMenuItem(R.id.commonutils_about_dest))
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_aboutMe_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(RoboMenuItem(R.id.commonutils_about_me_dest))
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsAboutMeActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_settings_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(RoboMenuItem(R.id.commonutils_settings_dest))
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsSettingsActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_leaks_opensLeakCanary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(RoboMenuItem(R.id.leaks_dest))
                result shouldBe true
                shadowOf(activity)
                    .nextStartedActivity
                    ?.component
                    ?.className
                    ?.contains("leakcanary", ignoreCase = true) shouldBe true
            }
        }
    }

    @Test
    fun navItem_unknown_returnsFalse() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(RoboMenuItem(-1)) shouldBe false
                shadowOf(activity).nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun onAppPickerItemClick_success_returnsTrue() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = activity.packageName, activityName = "")
                activity.onAppPickerItemClick(null, appInfo) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            }
        }
    }

    @Test
    fun onAppPickerItemClick_withNonNullView_setsTransitionView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = activity.packageName, activityName = "")
                val view = activity.findViewById<View>(R.id.appPicker)
                activity.onAppPickerItemClick(view, appInfo) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
                activity.findViewById<View>(R.id.appPicker).transitionName shouldBe "commonUtilsActivityTransitionName"
            }
        }
    }

    @Test
    fun onAppPickerItemClick_detachedView_launchesWithoutTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: View
            scenario.onActivity { activity ->
                view = View(activity)
                activity.onAppPickerItemClick(view, AppInfo(packageName = activity.packageName, activityName = ""))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            }
            view.transitionName shouldBe null
        }
    }

    @Test
    fun onAppPickerItemClick_activityStoppedBeforeLookupEnds_launchesWithoutTransition() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val view = activity.findViewById<View>(R.id.appPicker)
            activity.onAppPickerItemClick(view, AppInfo(packageName = activity.packageName, activityName = ""))
            controller.pause().stop()
            shadowOf(Looper.getMainLooper()).idle()
            controller.restart().start().resume()
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            view.transitionName shouldBe null
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun onAppPickerItemClick_doubleTap_startsIconActivityOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = activity.packageName, activityName = "")
                activity.onAppPickerItemClick(null, appInfo)
                activity.onAppPickerItemClick(null, appInfo)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun onAppPickerItemClick_secondAppTappedDuringLookup_opensFirstAppWithItsTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var secondView: View
            scenario.onActivity { activity ->
                secondView = activity.findViewById(R.id.noEntryView)
                activity.onAppPickerItemClick(
                    activity.findViewById(R.id.appPicker),
                    AppInfo(packageName = activity.packageName, activityName = ""),
                )
                activity.onAppPickerItemClick(secondView, AppInfo(packageName = "com.nonexistent.pkg.test", activityName = ""))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                val applicationInfo =
                    IntentCompat.getParcelableExtra(
                        started,
                        IconActivity.KEY_APPLICATION_INFO,
                        ApplicationInfo::class.java,
                    )
                applicationInfo?.packageName shouldBe activity.packageName
                activity.findViewById<View>(R.id.appPicker).transitionName shouldBe "commonUtilsActivityTransitionName"
            }
            secondView.transitionName shouldBe null
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun onAppPickerItemClick_latchDropsLaunch_opensIconActivityOnNextResume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onAppPickerItemClick(null, AppInfo(packageName = activity.packageName, activityName = ""))
                activity.singleLaunchActivity(Intent(activity, CommonUtilsAboutActivity::class.java)) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
                ViewModelProvider(activity)[MainViewModel::class.java].appLookup.value.shouldBeInstanceOf<AppLookup.Found>()
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
                ViewModelProvider(activity)[MainViewModel::class.java].appLookup.value shouldBe AppLookup.Idle
            }
        }
    }

    @Test
    fun onAppPickerItemClick_rotationDuringLookup_opensIconActivityFromRecreatedActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onAppPickerItemClick(
                    activity.findViewById(R.id.appPicker),
                    AppInfo(packageName = activity.packageName, activityName = ""),
                )
            }
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun onAppPickerItemClick_doubleTapOnMissingPackage_showsOneToast() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = "com.nonexistent.pkg.test", activityName = "")
                activity.onAppPickerItemClick(null, appInfo)
                activity.onAppPickerItemClick(null, appInfo)
            }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.shownToastCount() shouldBe 1
        }
    }

    @Test
    fun onAppPickerItemClick_packageNotFound_showsToast() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = "com.nonexistent.pkg.test", activityName = "")
                activity.onAppPickerItemClick(null, appInfo) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_app_not_found)
            }
        }
    }

    @Test
    @Config(sdk = [29])
    fun initAppPicker_belowApiR_skipsImmBottomPadding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.state shouldBe Lifecycle.State.RESUMED
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.appPicker).getTag(oneuiDesignR.id.tag_rv_imm_bottom_padding_listener) shouldBe null
            }
        }
    }

    @Test
    fun setLeaksMenuItemVisibility_nonNullItem_setsVisibility() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = activity.findViewById<DrawerNavigationView>(R.id.navigationView).findMenuItem(R.id.leaks_dest)!!
                item.isVisible = false
                activity.setLeaksMenuItemVisibility(item)
                item.isVisible shouldBe BuildConfig.DEBUG
            }
        }
    }

    @Test
    fun setLeaksMenuItemVisibility_nullItem_doesNothing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setLeaksMenuItemVisibility(null)
            }
            scenario.state shouldBe Lifecycle.State.RESUMED
        }
    }

    @Test
    fun loadInstalledApps_onError_showsErrorOnceAcrossRecreation() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws RuntimeException("test")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.state shouldBe Lifecycle.State.RESUMED
                scenario.onActivity { activity ->
                    ShadowToast.shownToastCount() shouldBe 1
                    ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error)
                    ViewModelProvider(activity)[MainViewModel::class.java].installedApps.value shouldBe
                        InstalledApps.Failed(handled = true)
                }
                scenario.recreate()
                shadowOf(Looper.getMainLooper()).idle()
                ShadowToast.shownToastCount() shouldBe 1
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }

    @Test
    fun loadInstalledApps_errorBeforeResume_showsErrorOnceAfterRecreation() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws RuntimeException("test")
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            controller.stop()
            ShadowToast.shownToastCount() shouldBe 0
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            controller.restart().start().resume()
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe controller.get().getString(commonutilsR.string.commonutils_error)
        } finally {
            controller.destroy()
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }

    @Test
    fun loadInstalledApps_cancellationException_doesNotCrash() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws CancellationException("cancelled")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.state shouldBe Lifecycle.State.RESUMED
                ShadowToast.shownToastCount() shouldBe 0
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }
}
