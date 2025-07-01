package de.lemke.geticon.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.app.ActivityOptions.makeSceneTransitionAnimation
import android.content.Intent
import android.content.Intent.ACTION_SEARCH
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.apppickerview.widget.AppPickerView
import androidx.apppickerview.widget.AppPickerView.ORDER_ASCENDING_IGNORE_CASE
import androidx.apppickerview.widget.AppPickerView.TYPE_GRID
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.checkAppStartAndHandleOOBE
import de.lemke.commonutils.configureCommonUtilsSplashScreen
import de.lemke.commonutils.getCommonUtilsSearchListener
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.setupCommonUtilsOOBEActivity
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
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.ObserveAppsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.ui.IconActivity.Companion.KEY_APPLICATION_INFO
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.configureImmBottomPadding
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.ktx.hideSoftInputOnScroll
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private var pickApkActivityResultLauncher = registerForActivityResult(GetContent()) { processApk(it) }
    private var search: MutableStateFlow<String?> = MutableStateFlow(null)
    private var isUIReady = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var observeApps: ObserveAppsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        setupCommonUtilsOOBEActivity(nextActivity = MainActivity::class.java)
        if (!checkAppStartAndHandleOOBE(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) openMain(savedInstanceState)
    }

    private fun openMain(savedInstanceState: Bundle?) {
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME)
        setupCommonUtilsSettingsActivity(
            commonutilsR.xml.preferences_design,
            commonutilsR.xml.preferences_general_language_and_image_save_location,
            commonutilsR.xml.preferences_dev_options_delete_app_data,
            commonutilsR.xml.preferences_more_info
        )
        initDrawer()
        initAppPicker()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() })
        lifecycleScope.launch {
            observeApps(search).flowWithLifecycle(lifecycle).collectLatest {
                updateAppPicker(it)
                isUIReady = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized) return
        outState.saveSearchAndActionMode(isSearchMode = binding.drawerLayout.isSearchMode)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_SEARCH) binding.drawerLayout.setSearchQueryFromIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        lifecycleScope.launch {
            menu.findItem(R.id.menu_item_show_system_apps).title = getString(
                if (getUserSettings().showSystemApps) commonutilsR.string.commonutils_hide_system_apps
                else commonutilsR.string.commonutils_show_system_apps
            )
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_item_search -> startSearch().let { true }
        R.id.menu_item_show_system_apps -> {
            lifecycleScope.launch {
                item.title = getString(
                    if (updateUserSettings { it.copy(showSystemApps = !it.showSystemApps) }.showSystemApps) commonutilsR.string.commonutils_hide_system_apps
                    else commonutilsR.string.commonutils_show_system_apps
                )
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun startSearch() =
        binding.drawerLayout.startSearchMode(getCommonUtilsSearchListener(search, commonutilsR.string.commonutils_search_apps), DISMISS)

    private fun initDrawer() {
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                //pickApkActivityResultLauncher.launch("*/*")
                R.id.extract_icon_from_apk_dest -> pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
                R.id.about_app_dest -> findViewById<View>(R.id.about_app_dest).transformToActivity(CommonUtilsAboutActivity::class.java)
                R.id.about_me_dest -> findViewById<View>(R.id.about_me_dest).transformToActivity(CommonUtilsAboutMeActivity::class.java)
                R.id.settings_dest -> findViewById<View>(R.id.settings_dest).transformToActivity(CommonUtilsSettingsActivity::class.java)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.setupHeaderAndNavRail(getString(R.string.about_app))
        binding.drawerLayout.isImmersiveScroll = true
        binding.noEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
    }

    private fun initAppPicker() = binding.appPickerList.apply {
        if (itemDecorationCount > 0) for (i in 0 until itemDecorationCount) removeItemDecorationAt(i)
        setAppPickerView(TYPE_GRID, emptyList(), ORDER_ASCENDING_IGNORE_CASE)
        setOnBindListener { holder: AppPickerView.ViewHolder, _: Int, packageName: String ->
            holder.item.onSingleClick {
                try {
                    hideSoftInput()
                    startActivity(
                        Intent(this@MainActivity, IconActivity::class.java)
                            .putExtra(KEY_APPLICATION_INFO, packageManager.getApplicationInfo(packageName, 0)),
                        makeSceneTransitionAnimation(this@MainActivity, Pair.create(holder.appIcon, "icon")).toBundle()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(commonutilsR.string.commonutils_error_app_not_found)
                }
            }
        }
        itemAnimator = null
        seslSetSmoothScrollEnabled(true)
        hideSoftInputOnScroll()
        if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(binding.drawerLayout)
    }

    private fun updateAppPicker(apps: List<String>) {
        if (apps.isNotEmpty()) binding.appPickerList.resetPackages(apps)
        binding.noEntryView.updateVisibilityWith(apps, binding.appPickerList)
    }

    private fun processApk(uri: Uri?) {
        try {
            if (uri == null) {
                toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                return
            }
            /*val importFile = DocumentFile.fromSingleUri(this, uri)
            if (importFile == null || !importFile.exists() || !importFile.canRead()) {
                toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                return
            }
            Log.d("MainActivity", "importFile: uri: $uri, name: ${importFile.name}, type: ${importFile.type}")*/
            val tempFile = File.createTempFile("extractIcon", ".apk", cacheDir)
            contentResolver.openInputStream(uri).use { it?.copyTo(FileOutputStream(tempFile)) }
            /*when (importFile.type) {
                "application/vnd.android.package-archive" -> {
                    contentResolver.openInputStream(uri).use { it?.copyTo(FileOutputStream(tempFile)) }
                }

                "application/octet-stream" -> {
                    val zipInputStream = ZipInputStream(contentResolver.openInputStream(uri)!!)
                    var zipEntry = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        val fileName = zipEntry.name
                        Log.d("MainActivity", "extract from apks: fileName: $fileName")
                        if (fileName == "base.apk") {
                            tempFile.outputStream().use { zipInputStream.copyTo(it) }
                            break
                        }
                        zipEntry = zipInputStream.nextEntry
                        zipInputStream.closeEntry()
                    }
                    zipInputStream.close()
                }

                else -> {
                    toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                    return
                }
            }*/
            val path = tempFile.absolutePath
            val packageInfo = packageManager.getPackageArchiveInfo(path, 0)
            val applicationInfo = packageInfo?.applicationInfo
            Log.d("MainActivity", "extract from apk: uri: $uri, path: $path, applicationInfo: $applicationInfo")
            if (applicationInfo == null) {
                toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                return
            }
            applicationInfo.sourceDir = path
            applicationInfo.publicSourceDir = path
            startActivity(Intent(this@MainActivity, IconActivity::class.java).putExtra(KEY_APPLICATION_INFO, applicationInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
        }
    }
}