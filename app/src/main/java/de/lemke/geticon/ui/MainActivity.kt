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

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.content.Intent.ACTION_SEARCH
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppData.GridAppDataBuilder
import androidx.picker.widget.SeslAppPickerView.Companion.ORDER_ASCENDING
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.collectEvents
import de.lemke.commonutils.configureCommonUtilsSplashScreen
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.handleFirstRun
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.setupCommonUtilsSettingsActivity
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.geticon.BuildConfig
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.openLeakCanary
import de.lemke.geticon.ui.IconActivity.Companion.KEY_APPLICATION_INFO
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.startSearchMode
import dev.oneuiproject.oneui.recyclerview.ktx.configureImmBottomPadding
import dev.oneuiproject.oneui.recyclerview.ktx.hideSoftInputOnScroll
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity(),
    ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var pickApkActivityResultLauncher = registerForActivityResult(GetContent()) { viewModel.onApkPicked(it) }
    private var isUIReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        handleFirstRun(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, allowSkip = BuildConfig.FIRST_RUN_SKIPPABLE) ?: return
        if (SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        openMain(savedInstanceState)
    }

    private fun openMain(savedInstanceState: Bundle?) {
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME)
        setupCommonUtilsSettingsActivity(
            commonutilsR.xml.preferences_design,
            commonutilsR.xml.preferences_general_language_and_image_save_location,
            commonutilsR.xml.preferences_dev_options_delete_app_data,
            commonutilsR.xml.preferences_more_info,
        )
        initDrawer()
        initAppPicker()
        collectEvents()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() })
        isUIReady = true
    }

    private fun collectEvents() {
        collectEvents(viewModel.events) { event ->
            when (event) {
                is MainEvent.NavigateToIcon -> {
                    startActivity(
                        Intent(this@MainActivity, IconActivity::class.java).putExtra(KEY_APPLICATION_INFO, event.applicationInfo),
                    )
                }

                MainEvent.ShowError -> {
                    toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized) return
        outState.saveSearchAndActionMode(isSearchMode = binding.drawerLayout.isSearchMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SEARCH) binding.drawerLayout.setSearchQueryFromIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = menuInflater.inflate(R.menu.menu_main, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_item_search -> startSearch().let { true }
            else -> super.onOptionsItemSelected(item)
        }

    private fun applyFilter(query: String = "") {
        binding.appPicker.setSearchFilter(query) { binding.noEntryView.updateVisibility(it <= 0, binding.appPicker) }
    }

    private fun startSearch() =
        binding.drawerLayout.startSearchMode(
            onStart = {
                it.queryHint = getString(commonutilsR.string.commonutils_search_apps)
                it.setQuery(commonUtilsSettings.search, false)
            },
            onQuery = { query, _ ->
                applyFilter(query)
                commonUtilsSettings.search = query
                true
            },
            onEnd = { applyFilter() },
            onBackBehavior = DISMISS,
        )

    private fun initDrawer() {
        binding.navigationView.findMenuItem(R.id.leaks_dest)?.isVisible = BuildConfig.DEBUG
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                R.id.extract_icon_from_apk_dest -> pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
                R.id.commonutils_about_dest ->
                    (
                        findViewById(R.id.commonutils_about_dest)
                            ?: binding.navigationView
                    ).transformToActivity(CommonUtilsAboutActivity::class.java)
                R.id.commonutils_about_me_dest ->
                    (
                        findViewById(R.id.commonutils_about_me_dest)
                            ?: binding.navigationView
                    ).transformToActivity(CommonUtilsAboutMeActivity::class.java)
                R.id.commonutils_settings_dest ->
                    (
                        findViewById(R.id.commonutils_settings_dest)
                            ?: binding.navigationView
                    ).transformToActivity(CommonUtilsSettingsActivity::class.java)
                R.id.leaks_dest -> openLeakCanary(this)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.setTitle(getString(R.string.app_name))
        binding.drawerLayout.setupHeaderAndNavRail(getString(R.string.about_app))
        binding.drawerLayout.isImmersiveScroll = true
        binding.noEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
    }

    private fun initAppPicker() =
        binding.appPicker.apply {
            appListOrder = ORDER_ASCENDING
            ViewCompat.setOnApplyWindowInsetsListener(binding.appPicker) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                seslSetIndexTipEnabled(true, bars.top)
                insets
            }
            hideSoftInputOnScroll()
            if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(binding.drawerLayout)
            val appInfoDataHelper = SeslAppInfoDataHelper(context, GridAppDataBuilder::class.java)
            val appInfoDataList = appInfoDataHelper.getPackages().onEach { it.subLabel = it.packageName }
            submitList(appInfoDataList)
            setOnItemClickEventListener { view, appInfo ->
                try {
                    hideSoftInput()
                    view?.transformToActivity(
                        Intent(this@MainActivity, IconActivity::class.java)
                            .putExtra(KEY_APPLICATION_INFO, packageManager.getApplicationInfo(appInfo.packageName, 0)),
                    )
                    true
                } catch (e: NameNotFoundException) {
                    Log.e("MainActivity", "App not found: ${appInfo.packageName}", e)
                    toast(commonutilsR.string.commonutils_error_app_not_found)
                    false
                }
            }
        }
}
