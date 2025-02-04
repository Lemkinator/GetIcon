package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.apppickerview.widget.AppPickerView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.domain.AppStart
import de.lemke.geticon.domain.CheckAppStartUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.ui.IconActivity.Companion.KEY_APPLICATION_INFO
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.ktx.hideSoftInputOnScroll
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.sequences.forEach


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pickApkActivityResultLauncher: ActivityResultLauncher<String>
    private lateinit var drawerListView: LinearLayout
    private val drawerItemTitles: MutableList<TextView> = mutableListOf()
    private var showSystemApps = false
    private var search: String? = null
    private var time: Long = 0
    private var refreshAppsJob: Job? = null
    private var isUIReady = false

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        time = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        }
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
                AppStart.FIRST_TIME -> openOOBE()
                AppStart.NORMAL -> checkTOS(getUserSettings(), savedInstanceState)
                AppStart.FIRST_TIME_VERSION -> checkTOS(getUserSettings(), savedInstanceState)
            }
        }
        pickApkActivityResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { processApk(it) }
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finishAfterTransition()
    }

    private suspend fun checkTOS(userSettings: UserSettings, savedInstanceState: Bundle?) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain(savedInstanceState)
    }

    private suspend fun openMain(savedInstanceState: Bundle?) {
        initDrawer()
        showSystemApps = getUserSettings().showSystemApps
        initAppPicker()
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() })
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        isUIReady = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized) return
        outState.saveSearchAndActionMode(isSearchMode = binding.drawerLayout.isSearchMode)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_SEARCH) {
            binding.drawerLayout.setSearchQueryFromIntent(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_picker, menu)
        lifecycleScope.launch {
            showSystemApps = getUserSettings().showSystemApps
            menu.findItem(R.id.menu_apppicker_system).title =
                getString(if (showSystemApps) R.string.hide_system_apps else R.string.show_system_apps)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_search -> {
                startSearch()
                return true
            }

            R.id.menu_apppicker_system -> {
                showSystemApps = !showSystemApps
                lifecycleScope.launch { updateUserSettings { it.copy(showSystemApps = showSystemApps) } }
                item.title = getString(if (showSystemApps) R.string.hide_system_apps else R.string.show_system_apps)
                refreshApps()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startSearch() {
        binding.drawerLayout.startSearchMode(SearchModeListener(), DISMISS)
    }

    private fun updateSearch(query: String?): Boolean {
        if (search == null) return false
        search = query ?: ""
        refreshApps()
        lifecycleScope.launch { updateUserSettings { it.copy(search = query ?: "") } }
        return true
    }

    inner class SearchModeListener : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean = updateSearch(query).also { hideSoftInput() }
        override fun onQueryTextChange(query: String?): Boolean = updateSearch(query)
        override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
            lifecycleScope.launch {
                if (visible) {
                    search = getUserSettings().search
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    searchView.queryHint = getString(R.string.search_apps)
                    refreshApps()
                } else {
                    search = null
                    refreshApps()
                }
            }
        }
    }

    fun closeDrawerAfterDelay() {
        if (binding.drawerLayout.isLargeScreenMode) return
        lifecycleScope.launch {
            delay(500) //delay, so closing the drawer is not visible for the user
            binding.drawerLayout.setDrawerOpen(false, false)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        drawerListView = findViewById(R.id.drawerListView)
        drawerItemTitles.apply {
            clear()
            add(findViewById(R.id.drawerItemExtractIconFromApkTitle))
            add(findViewById(R.id.drawerItemAboutAppTitle))
            add(findViewById(R.id.drawerItemAboutMeTitle))
            add(findViewById(R.id.drawerItemSettingsTitle))
        }
        findViewById<LinearLayout>(R.id.drawerItemExtractIconFromApk).onSingleClick {
            pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
            //pickApkActivityResultLauncher.launch("*/*")
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemAboutApp).onSingleClick {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemAboutMe).onSingleClick {
            startActivity(Intent(this@MainActivity, AboutMeActivity::class.java))
            closeDrawerAfterDelay()
        }
        findViewById<LinearLayout>(R.id.drawerItemSettings).onSingleClick {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            closeDrawerAfterDelay()
        }
        binding.drawerLayout.apply {
            setHeaderButtonIcon(AppCompatResources.getDrawable(this@MainActivity, dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline))
            setHeaderButtonTooltip(getString(R.string.about_app))
            setHeaderButtonOnClickListener {
                startActivity(Intent().setClass(this@MainActivity, AboutActivity::class.java))
                closeDrawerAfterDelay()
            }
            setNavRailContentMinSideMargin(14)
            lockNavRailOnActionMode = true
            lockNavRailOnSearchMode = true
            closeNavRailOnBack = true
            isImmersiveScroll = true
        }
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayout.setButtonBadges(Badge.DOT, Badge.DOT)
        }
        binding.iconNoEntryView.translateYWithAppBar(binding.drawerLayout.appBarLayout, this)

        //setupNavRailFadeEffect
        binding.drawerLayout.apply {
            if (!isLargeScreenMode) return
            setDrawerStateListener {
                when (it) {
                    DrawerLayout.DrawerState.OPEN -> {
                        offsetUpdaterJob?.cancel()
                        updateOffset(1f)
                    }

                    DrawerLayout.DrawerState.CLOSE -> {
                        offsetUpdaterJob?.cancel()
                        updateOffset(0f)
                    }

                    DrawerLayout.DrawerState.CLOSING,
                    DrawerLayout.DrawerState.OPENING -> {
                        startOffsetUpdater()
                    }
                }
            }
        }

        //Set initial offset
        binding.drawerLayout.post {
            updateOffset(binding.drawerLayout.drawerOffset)
        }
    }

    private var offsetUpdaterJob: Job? = null
    private fun startOffsetUpdater() {
        offsetUpdaterJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateOffset(binding.drawerLayout.drawerOffset)
                delay(50)
            }
        }
    }

    fun updateOffset(offset: Float) {
        drawerItemTitles.forEach { it.alpha = offset }
        drawerListView.children.forEach {
            if (offset == 0f) {
                it.post {
                    it.updateLayoutParams<MarginLayoutParams> {
                        width = if (it is LinearLayout) 52f.dpToPx(it.context.resources)
                        else 25f.dpToPx(it.context.resources)
                    }
                }
            } else {
                it.updateLayoutParams<MarginLayoutParams> {
                    width = MATCH_PARENT
                }
            }
        }
    }

    private suspend fun initAppPicker() {
        if (binding.apppickerList.itemDecorationCount > 0) {
            for (i in 0 until binding.apppickerList.itemDecorationCount) {
                binding.apppickerList.removeItemDecorationAt(i)
            }
        }
        binding.apppickerList.setAppPickerView(AppPickerView.TYPE_GRID, getApps(null), AppPickerView.ORDER_ASCENDING_IGNORE_CASE)
        binding.apppickerList.setOnBindListener { holder: AppPickerView.ViewHolder, _: Int, packageName: String ->
            holder.item.onSingleClick {
                try {
                    hideSoftInput()
                    startActivity(
                        Intent(this, IconActivity::class.java)
                            .putExtra(KEY_APPLICATION_INFO, packageManager.getApplicationInfo(packageName, 0)),
                        ActivityOptions.makeSceneTransitionAnimation(this, Pair.create(holder.appIcon, "icon")).toBundle()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(R.string.error_app_not_found)
                }
            }
        }
        binding.apppickerList.itemAnimator = null
        binding.apppickerList.seslSetSmoothScrollEnabled(true)
        binding.apppickerList.hideSoftInputOnScroll()
    }

    private suspend fun getApps(search: String?): List<String> = withContext(Dispatchers.Default) {
        try {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val filteredApps = if (showSystemApps) apps
            else apps.filter { it.flags and (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or ApplicationInfo.FLAG_SYSTEM) == 0 }
            return@withContext if (search.isNullOrBlank()) filteredApps.map { it.packageName }
            else filteredApps.filter {
                packageManager.getApplicationLabel(it).toString().contains(search, ignoreCase = true) ||
                        it.packageName.toString().contains(search, ignoreCase = true)
            }.map { it.packageName }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.error_loading_apps)
            emptyList()
        }
    }

    private fun refreshApps() {
        binding.iconNoEntryScrollView.visibility = View.GONE
        binding.apppickerList.visibility = View.GONE
        binding.apppickerProgress.visibility = View.VISIBLE
        refreshAppsJob?.cancel()
        if (!this@MainActivity::binding.isInitialized) return
        refreshAppsJob = lifecycleScope.launch {
            val apps = getApps(search)
            if (apps.isEmpty() || search?.isBlank() == true) {
                binding.apppickerList.visibility = View.GONE
                binding.apppickerProgress.visibility = View.GONE
                binding.iconListLottie.cancelAnimation()
                binding.iconListLottie.progress = 0f
                binding.iconNoEntryScrollView.visibility = View.VISIBLE
                binding.iconListLottie.addValueCallback(
                    KeyPath("**"),
                    LottieProperty.COLOR_FILTER,
                    LottieValueCallback(SimpleColorFilter(getColor(R.color.primary_color_themed)))
                )
                binding.iconListLottie.postDelayed({ binding.iconListLottie.playAnimation() }, 400)
            } else {
                binding.apppickerList.resetPackages(apps)
                binding.iconNoEntryScrollView.visibility = View.GONE
                binding.apppickerProgress.visibility = View.GONE
                binding.apppickerList.visibility = View.VISIBLE
            }
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
            startActivity(
                Intent(this@MainActivity, IconActivity::class.java)
                    .putExtra(KEY_APPLICATION_INFO, applicationInfo)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            toast(R.string.error_no_valid_file_selected)
        }
    }
}