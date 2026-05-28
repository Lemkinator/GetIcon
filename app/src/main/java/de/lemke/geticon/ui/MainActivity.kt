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
import android.content.Intent.ACTION_SEARCH
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.DrawerHost
import de.lemke.commonutils.checkAppStart
import de.lemke.commonutils.configureCommonUtilsSplashScreen
import de.lemke.commonutils.setupCommonUtilsNavGraph
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.geticon.BuildConfig
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.openLeakCanary
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.setupNavigation
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), DrawerHost {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val pickApkActivityResultLauncher = registerForActivityResult(GetContent()) { viewModel.onApkPicked(it) }
    private var isUIReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        setupCommonUtilsNavGraph(
            oobeCompleteNavAction = R.id.main_dest,
            preferences =
                listOf(
                    commonutilsR.xml.preferences_design,
                    commonutilsR.xml.preferences_general_language_and_image_save_location,
                    commonutilsR.xml.preferences_dev_options_delete_app_data,
                    commonutilsR.xml.preferences_more_info,
                ),
            appVersion = BuildConfig.VERSION_NAME,
        )
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navigationHost) as NavHostFragment
        if (savedInstanceState == null) {
            val appStart = checkAppStart(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
            if (appStart.shouldShowOOBE) {
                navHostFragment.navController.navigate(
                    R.id.commonutils_oobe_dest,
                    null,
                    NavOptions
                        .Builder()
                        .setPopUpTo(R.id.main_dest, inclusive = true)
                        .build(),
                )
            }
        }
        initDrawer(navHostFragment)
        isUIReady = true
    }

    private fun initDrawer(navHostFragment: NavHostFragment) {
        binding.navigationView.findMenuItem(R.id.leaks_dest)?.isVisible = BuildConfig.DEBUG
        val configuration = AppBarConfiguration(setOf(R.id.main_dest), binding.drawerLayout)
        binding.drawerLayout.setupNavigation(binding.navigationView, navHostFragment, configuration)
        navHostFragment.navController.addOnDestinationChangedListener { _, _, _ ->
            binding.drawerLayout.setExpanded(expanded = false, animate = true)
        }
        binding.navigationView.findMenuItem(R.id.extract_icon_from_apk_dest)?.setOnMenuItemClickListener {
            pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
            true
        }
        binding.navigationView.findMenuItem(R.id.leaks_dest)?.setOnMenuItemClickListener {
            openLeakCanary(this)
            true
        }
        binding.drawerLayout.apply {
            setupHeaderAndNavRail(getString(commonutilsR.string.commonutils_about_app)) {
                binding.drawerLayout.setDrawerOpen(open = false, animate = true, ignoreOnNavRailMode = true)
                navHostFragment.navController.navigate(R.id.commonutils_about_dest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SEARCH) binding.drawerLayout.setSearchQueryFromIntent(intent)
    }

    override val drawerLayout: NavDrawerLayout get() = binding.drawerLayout
}
