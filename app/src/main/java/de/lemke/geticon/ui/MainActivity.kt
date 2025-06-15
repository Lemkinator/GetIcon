package de.lemke.geticon.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.app.ActivityOptions.makeSceneTransitionAnimation
import android.content.Intent
import android.content.Intent.ACTION_SEARCH
import android.graphics.ColorFilter
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.apppickerview.widget.AppPickerView
import androidx.apppickerview.widget.AppPickerView.ORDER_ASCENDING_IGNORE_CASE
import androidx.apppickerview.widget.AppPickerView.TYPE_GRID
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieProperty.COLOR_FILTER
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.AboutActivity
import de.lemke.commonutils.AboutMeActivity
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.setupCommonActivities
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.geticon.BuildConfig
import de.lemke.geticon.R
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.domain.AppStart.FIRST_TIME
import de.lemke.geticon.domain.AppStart.FIRST_TIME_VERSION
import de.lemke.geticon.domain.AppStart.NORMAL
import de.lemke.geticon.domain.CheckAppStartUseCase
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
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pickApkActivityResultLauncher: ActivityResultLauncher<String>
    private var search: MutableStateFlow<String?> = MutableStateFlow(null)
    private var time: Long = 0
    private var isUIReady = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var observeApps: ObserveAppsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        time = System.currentTimeMillis()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        splashScreen.setKeepOnScreenCondition { !isUIReady }
        /*
        there is a bug in the new splash screen api, when using the onExitAnimationListener -> splash icon flickers
        therefore setting a manual delay in openMain()
        splashScreen.setOnExitAnimationListener { splash ->
            val splashAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                splash.view,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f)
            )
            splashAnimator.interpolator = AccelerateDecelerateInterpolator()
            splashAnimator.duration = 400L
            splashAnimator.doOnEnd { splash.remove() }
            val contentAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.root,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f, 1f)
            )
            contentAnimator.interpolator = AccelerateDecelerateInterpolator()
            contentAnimator.duration = 400L

            val remainingDuration = splash.iconAnimationDurationMillis - (System.currentTimeMillis() - splash.iconAnimationStartMillis)
                .coerceAtLeast(0L)
            lifecycleScope.launch {
                delay(remainingDuration)
                splashAnimator.start()
                contentAnimator.start()
            }
        }*/

        lifecycleScope.launch {
            when (checkAppStart()) {
                FIRST_TIME -> openOOBE()
                NORMAL -> checkTOS(getUserSettings(), savedInstanceState)
                FIRST_TIME_VERSION -> checkTOS(getUserSettings(), savedInstanceState)
            }
        }
        pickApkActivityResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { processApk(it) }
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
    }

    private suspend fun checkTOS(userSettings: UserSettings, savedInstanceState: Bundle?) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain(savedInstanceState)
    }

    private fun openMain(savedInstanceState: Bundle?) {
        setupCommonUtilsActivities()
        initDrawer()
        initAppPicker()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() })
        lifecycleScope.launch {
            observeApps(search).flowWithLifecycle(lifecycle).collectLatest {
                updateAppPicker(it)
                //manually waiting for the splash animation to finish :/
                if (!isUIReady) delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
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
        menuInflater.inflate(R.menu.menu_app_picker, menu)
        lifecycleScope.launch {
            menu.findItem(R.id.menu_app_picker_system).title =
                getString(if (getUserSettings().showSystemApps) R.string.hide_system_apps else R.string.show_system_apps)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_item_search -> startSearch().let { true }
        R.id.menu_app_picker_system -> {
            lifecycleScope.launch {
                item.title =
                    getString(
                        if (updateUserSettings { it.copy(showSystemApps = !it.showSystemApps) }.showSystemApps) R.string.hide_system_apps
                        else R.string.show_system_apps
                    )
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun startSearch() = binding.drawerLayout.startSearchMode(searchModeListener, DISMISS)

    val searchModeListener = object : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean = setSearch(query).also { hideSoftInput() }
        override fun onQueryTextChange(query: String?): Boolean = setSearch(query)
        private fun setSearch(query: String?): Boolean {
            if (search.value == null) return false
            search.value = query ?: ""
            lifecycleScope.launch { updateUserSettings { it.copy(search = query ?: "") } }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, isActive: Boolean) {
            if (isActive) lifecycleScope.launch {
                search.value = getUserSettings().search
                searchView.queryHint = getString(R.string.search_apps)
                searchView.setQuery(search.value, false)
            } else search.value = null
        }
    }

    private fun setupCommonUtilsActivities() {
        lifecycleScope.launch {
            setupCommonActivities(
                appName = getString(R.string.app_name),
                appVersion = BuildConfig.VERSION_NAME,
                optionalText = getString(R.string.app_description),
                email = getString(R.string.email),
                devModeEnabled = getUserSettings().devModeEnabled,
                onDevModeChanged = { newDevModeEnabled: Boolean -> updateUserSettings { it.copy(devModeEnabled = newDevModeEnabled) } }
            )
        }
    }

    private fun initDrawer() {
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                //pickApkActivityResultLauncher.launch("*/*")
                R.id.extract_icon_from_apk_dest -> pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
                R.id.about_app_dest -> findViewById<View>(R.id.about_app_dest).transformToActivity(AboutActivity::class.java)
                R.id.about_me_dest -> findViewById<View>(R.id.about_me_dest).transformToActivity(AboutMeActivity::class.java)
                R.id.settings_dest -> findViewById<View>(R.id.settings_dest).transformToActivity(SettingsActivity::class.java)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.setupHeaderAndNavRail(getString(R.string.about_app))
        binding.drawerLayout.isImmersiveScroll = true
        binding.noEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
        binding.appPickerProgress.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)
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
                    toast(R.string.error_app_not_found)
                }
            }
        }
        itemAnimator = null
        seslSetSmoothScrollEnabled(true)
        hideSoftInputOnScroll()
        if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(binding.drawerLayout)
    }

    private fun updateAppPicker(apps: List<String>) {
        binding.noEntryScrollView.isVisible = false
        binding.appPickerList.isVisible = false
        binding.appPickerProgress.isVisible = true
        if (!this@MainActivity::binding.isInitialized) return
        if (apps.isEmpty()) {
            binding.appPickerList.isVisible = false
            binding.appPickerProgress.isVisible = false
            binding.noEntryLottie.cancelAnimation()
            binding.noEntryLottie.progress = 0f
            binding.noEntryScrollView.isVisible = true
            val callback = LottieValueCallback<ColorFilter>(SimpleColorFilter(getColor(R.color.primary_color_themed)))
            binding.noEntryLottie.addValueCallback(KeyPath("**"), COLOR_FILTER, callback)
            binding.noEntryLottie.postDelayed({ binding.noEntryLottie.playAnimation() }, 400)
        } else {
            binding.appPickerList.resetPackages(apps)
            binding.noEntryScrollView.isVisible = false
            binding.appPickerProgress.isVisible = false
            binding.appPickerList.isVisible = true
        }
    }

    private fun processApk(uri: Uri?) {
        try {
            if (uri == null) {
                toast(R.string.error_no_valid_file_selected)
                return
            }
            /*val importFile = DocumentFile.fromSingleUri(this, uri)
            if (importFile == null || !importFile.exists() || !importFile.canRead()) {
                toast(R.string.error_no_valid_file_selected)
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
                    toast(R.string.error_no_valid_file_selected)
                    return
                }
            }*/
            val path = tempFile.absolutePath
            val packageInfo = packageManager.getPackageArchiveInfo(path, 0)
            val applicationInfo = packageInfo?.applicationInfo
            Log.d("MainActivity", "extract from apk: uri: $uri, path: $path, applicationInfo: $applicationInfo")
            if (applicationInfo == null) {
                toast(R.string.error_no_valid_file_selected)
                return
            }
            applicationInfo.sourceDir = path
            applicationInfo.publicSourceDir = path
            startActivity(Intent(this@MainActivity, IconActivity::class.java).putExtra(KEY_APPLICATION_INFO, applicationInfo))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.error_no_valid_file_selected)
        }
    }
}