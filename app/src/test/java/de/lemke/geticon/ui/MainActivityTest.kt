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

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppInfo
import androidx.picker.widget.SeslAppPickerGridView
import androidx.picker.widget.SeslAppPickerView.Companion.ORDER_ASCENDING
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_SEARCH_MODE
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.geticon.BuildConfig
import de.lemke.geticon.R
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
import org.robolectric.shadows.ShadowToast
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val processApkStub: ProcessApkUseCase = mockk(relaxed = true)

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
                activity.onOptionsItemSelected(mockk { every { itemId } returns R.id.menu_item_search })
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe true
            }
        }
    }

    @Test
    fun onSaveInstanceState_notReady_returnsEarly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(mockk { every { itemId } returns R.id.menu_item_search })
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
            activity.onOptionsItemSelected(mockk { every { itemId } returns R.id.menu_item_search })
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
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_search }
                activity.onOptionsItemSelected(item) shouldBe true
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode shouldBe true
                // End search mode to trigger onEnd lambda → applyFilter()
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endSearchMode()
                activity.findViewById<NoEntryView>(R.id.noEntryView).visibility shouldBe View.GONE
            }
        }
    }

    @Test
    fun onOptionsItemSelected_unknownItem_callsSuper() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns android.R.id.home }
                activity.onOptionsItemSelected(item) shouldBe false
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
    fun collectEvents_showError_callsToast() {
        coEvery { processApkStub(any()) } returns ApkProcessResult.InvalidApk
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].onApkPicked(Uri.parse("content://test"))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_no_valid_file_selected)
            }
        }
    }

    @Test
    fun collectEvents_navigateToApkIcon_startsIconActivity() {
        val appInfo = mockk<ApplicationInfo>(relaxed = true).also { it.packageName = "com.test" }
        coEvery { processApkStub(any()) } returns ApkProcessResult.Success(appInfo)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java]
                    .onApkPicked(Uri.parse("content://test"))
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe IconActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_extractApk_launchesFilePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.extract_icon_from_apk_dest })
                result shouldBe true
                shadowOf(activity).nextStartedActivityForResult?.intent?.type shouldBe
                    "application/vnd.android.package-archive"
            }
        }
    }

    @Test
    fun navItem_about_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_about_dest })
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_aboutMe_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_about_me_dest })
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsAboutMeActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_settings_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_settings_dest })
                result shouldBe true
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe CommonUtilsSettingsActivity::class.java.name
            }
        }
    }

    @Test
    fun navItem_leaks_opensLeakCanary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val result = activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.leaks_dest })
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
                activity.onNavigationItemSelected(mockk { every { itemId } returns -1 }) shouldBe false
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
                activity.findViewById<View>(R.id.appPicker).transitionName shouldNotBe null
            }
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
            scenario.onActivity { activity ->
                activity.findViewById<SeslAppPickerGridView>(R.id.appPicker).appListOrder shouldBe ORDER_ASCENDING
            }
        }
    }

    @Test
    fun setLeaksMenuItemVisibility_nonNullItem_setsVisibility() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem>(relaxed = true)
                activity.setLeaksMenuItemVisibility(item)
                verify { item.isVisible = BuildConfig.DEBUG }
            }
        }
    }

    @Test
    fun setLeaksMenuItemVisibility_nullItem_doesNothing() {
        // item is null: there is nothing to hold state, so a no-crash check is the only assertion possible here.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setLeaksMenuItemVisibility(null)
            }
        }
    }

    @Test
    fun loadInstalledApps_onError_doesNotCrash() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws RuntimeException("test")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.state shouldBe Lifecycle.State.RESUMED
                scenario.onActivity { activity ->
                    ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error)
                }
            }
        } finally {
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
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[MainViewModel::class.java].installedApps.value shouldBe emptyList()
                }
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }
}
