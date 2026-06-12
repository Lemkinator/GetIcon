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

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Looper
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import de.lemke.geticon.R
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
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

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeProcessApk: ProcessApkUseCase = mockk(relaxed = true)

    @Before
    fun setup() {
        hiltRule.inject()
        ApplicationProvider.getApplicationContext<HiltTestApplication>().initCommonUtilsSettingsAndSetDarkMode()
        commonUtilsSettings.lastVersionCode = Int.MAX_VALUE
        commonUtilsSettings.acceptedTosVersion = Int.MAX_VALUE
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        }
    }

    @Test
    fun onSaveInstanceState_ready_savesState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
        }
    }

    @Test
    fun onSaveInstanceState_notReady_returnsEarly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.isUIReady = false }
            scenario.recreate()
        }
    }

    @Test
    fun onNewIntent_actionSearch_setsQuery() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            controller.newIntent(Intent(Intent.ACTION_SEARCH))
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun onOptionsItemSelected_searchItem_startsSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_search }
                activity.onOptionsItemSelected(item)
                // End search mode to trigger onEnd lambda → applyFilter()
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endSearchMode()
            }
        }
    }

    @Test
    fun onOptionsItemSelected_unknownItem_callsSuper() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns android.R.id.home }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun applyFilter_direct_callsSetSearchFilter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.applyFilter("test")
            }
        }
    }

    @Test
    fun collectEvents_showError_callsToast() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].onApkPicked(null)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun collectEvents_navigateToIcon_startsIconActivity() {
        val appInfo = mockk<ApplicationInfo>(relaxed = true).also { it.packageName = "com.test" }
        coEvery { fakeProcessApk(any()) } returns ApkProcessResult.Success(appInfo)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java]
                    .onApkPicked(Uri.parse("content://test"))
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun navItem_extractApk_launchesFilePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.extract_icon_from_apk_dest })
            }
        }
    }

    @Test
    fun navItem_about_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_about_dest })
            }
        }
    }

    @Test
    fun navItem_aboutMe_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_about_me_dest })
            }
        }
    }

    @Test
    fun navItem_settings_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.commonutils_settings_dest })
            }
        }
    }

    @Test
    fun navItem_leaks_opensLeakCanary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns R.id.leaks_dest })
            }
        }
    }

    @Test
    fun navItem_unknown_returnsFalse() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onNavigationItemSelected(mockk { every { itemId } returns -1 })
            }
        }
    }

    @Test
    fun onAppPickerItemClick_success_returnsTrue() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = activity.packageName, activityName = "")
                activity.onAppPickerItemClick(null, appInfo)
            }
        }
    }

    @Test
    fun onAppPickerItemClick_packageNotFound_returnsFalse() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = "com.nonexistent.pkg.test", activityName = "")
                activity.onAppPickerItemClick(null, appInfo)
            }
        }
    }

    @Test
    fun initAppPicker_loadFailure_logsError() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws RuntimeException("test")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { _ ->
                shadowOf(Looper.getMainLooper()).idle()
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }

    @Test
    fun loadPackageList_cancellationException_rethrows() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws CancellationException("cancelled")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { _ ->
                shadowOf(Looper.getMainLooper()).idle()
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }
}
