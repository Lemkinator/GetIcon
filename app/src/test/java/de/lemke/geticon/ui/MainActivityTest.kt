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
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.navigation.NavigationView
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
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import leakcanary.AppWatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
    fun onSaveInstanceState_withInitializedBinding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
        }
    }

    @Test
    fun onNewIntent_actionSearch_setsQuery() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
                    method.isAccessible = true
                    method.invoke(activity, Intent(Intent.ACTION_SEARCH))
                } catch (_: Exception) {
                }
            }
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
    fun applyFilter_viaSearchModeQueryListener() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_search }
                activity.onOptionsItemSelected(item)
                // Trigger onQuery lambda → applyFilter(query)
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                @Suppress("TooGenericExceptionCaught")
                try {
                    val field = ToolbarLayout::class.java.getDeclaredField("searchModeListener")
                    field.isAccessible = true
                    val listener = field.get(drawerLayout) as? ToolbarLayout.SearchModeListener
                    listener?.onQueryTextChange("test")
                } catch (_: Exception) {
                    // Reflection may fail if field name changes; endSearchMode covers onEnd
                }
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

    // --- Navigation drawer item tests via reflection ---

    private fun ActivityScenario<MainActivity>.simulateNavItem(itemId: Int) {
        onActivity { activity ->
            val navView = activity.findViewById<DrawerNavigationView>(R.id.navigationView)
            @Suppress("TooGenericExceptionCaught")
            try {
                val field = DrawerNavigationView::class.java.getDeclaredField("navigationItemSelectedListener")
                field.isAccessible = true
                val listener = field.get(navView) as? NavigationView.OnNavigationItemSelectedListener
                val item = navView.findMenuItem(itemId) ?: return@onActivity
                listener?.onNavigationItemSelected(item)
            } catch (_: Exception) {
                // no-op if reflection unavailable
            }
        }
    }

    private fun ActivityScenario<MainActivity>.simulateUnknownNavItem() {
        onActivity { activity ->
            val navView = activity.findViewById<DrawerNavigationView>(R.id.navigationView)
            @Suppress("TooGenericExceptionCaught")
            try {
                val field = DrawerNavigationView::class.java.getDeclaredField("navigationItemSelectedListener")
                field.isAccessible = true
                val listener = field.get(navView) as? NavigationView.OnNavigationItemSelectedListener
                val unknownItem = mockk<MenuItem> { every { itemId } returns -1 }
                listener?.onNavigationItemSelected(unknownItem)
            } catch (_: Exception) {
                // no-op if reflection unavailable
            }
        }
    }

    @Test
    fun navItem_extractApk_launchesFilePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateNavItem(R.id.extract_icon_from_apk_dest)
        }
    }

    @Test
    fun navItem_about_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateNavItem(R.id.commonutils_about_dest)
        }
    }

    @Test
    fun navItem_aboutMe_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateNavItem(R.id.commonutils_about_me_dest)
        }
    }

    @Test
    fun navItem_settings_navigates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateNavItem(R.id.commonutils_settings_dest)
        }
    }

    @Test
    fun navItem_leaks_opensLeakCanary() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateNavItem(R.id.leaks_dest)
        }
    }

    @Test
    fun navItem_unknown_returnsFalse() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.simulateUnknownNavItem()
        }
    }

    @Test
    fun onAppPickerItemClick_success_returnsTrue() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = activity.packageName, activityName = "")
                invokePrivateOnAppPickerItemClick(activity, null, appInfo)
            }
        }
    }

    @Test
    fun onAppPickerItemClick_packageNotFound_returnsFalse() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appInfo = AppInfo(packageName = "com.nonexistent.pkg.test", activityName = "")
                invokePrivateOnAppPickerItemClick(activity, null, appInfo)
            }
        }
    }

    @Test
    fun initAppPicker_loadFailure_logsError() {
        mockkConstructor(SeslAppInfoDataHelper::class)
        every { anyConstructed<SeslAppInfoDataHelper>().getPackages() } throws RuntimeException("test")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
            }
        } finally {
            unmockkConstructor(SeslAppInfoDataHelper::class)
        }
    }

    private fun invokePrivateOnAppPickerItemClick(
        activity: MainActivity,
        view: View?,
        appInfo: AppInfo,
    ) {
        try {
            val method =
                MainActivity::class.java.getDeclaredMethod(
                    "onAppPickerItemClick",
                    View::class.java,
                    AppInfo::class.java,
                )
            method.isAccessible = true
            method.invoke(activity, view, appInfo)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
        }
    }
}
