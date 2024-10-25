package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.SearchManager
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
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.apppickerview.widget.AppPickerView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.appbar.AppBarLayout
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.geticon.R
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.domain.AppStart
import de.lemke.geticon.domain.CheckAppStartUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.domain.utils.setCustomOnBackPressedLogic
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pickApkActivityResultLauncher: ActivityResultLauncher<String>
    private val backPressEnabled = MutableStateFlow(false)
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
                AppStart.NORMAL -> checkTOS(getUserSettings())
                AppStart.FIRST_TIME_VERSION -> checkTOS(getUserSettings())
            }
        }
        pickApkActivityResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            try {
                if (uri == null) {
                    Toast.makeText(this@MainActivity, R.string.error_no_valid_file_selected, Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                /*val importFile = DocumentFile.fromSingleUri(this, uri)
                if (importFile == null || !importFile.exists() || !importFile.canRead()) {
                    Toast.makeText(this@MainActivity, R.string.error_no_valid_file_selected, Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
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
                        Toast.makeText(this@MainActivity, R.string.error_no_valid_file_selected, Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }
                }*/
                val path = tempFile.absolutePath
                val packageInfo = packageManager.getPackageArchiveInfo(path, 0)
                val applicationInfo = packageInfo?.applicationInfo
                Log.d("MainActivity", "extract from apk: uri: $uri, path: $path, applicationInfo: $applicationInfo")
                if (applicationInfo == null) {
                    Toast.makeText(this@MainActivity, R.string.error_no_valid_file_selected, Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                applicationInfo.sourceDir = path
                applicationInfo.publicSourceDir = path
                startActivity(
                    Intent(this@MainActivity, IconActivity::class.java)
                        .putExtra("applicationInfo", applicationInfo)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, R.string.error_no_valid_file_selected, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    private suspend fun checkTOS(userSettings: UserSettings) {
        if (!userSettings.tosAccepted) openOOBE()
        else openMain()
    }

    private fun openMain() {
        lifecycleScope.launch {
            setCustomOnBackPressedLogic(backPressEnabled) { checkBackPressed() }
            initDrawer()
            showSystemApps = getUserSettings().showSystemApps
            initAppPicker()
            binding.drawerLayoutMain.setSearchModeListener(SearchModeListener())
            binding.drawerLayoutMain.searchView.setSearchableInfo(
                (getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName)
            )
            //manually waiting for the animation to finish :/
            delay(700 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
            isUIReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            delay(500) //delay, so closing the drawer is not visible for the user
            binding.drawerLayoutMain.setDrawerOpen(false, false)
        }
    }

    private fun checkBackPressed() {
        when {
            binding.drawerLayoutMain.isSearchMode -> {
                if (ViewCompat.getRootWindowInsets(binding.root)!!.isVisible(WindowInsetsCompat.Type.ime())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                        currentFocus!!.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
                } else {
                    search = null
                    binding.drawerLayoutMain.dismissSearchMode()
                }
            }

            else -> {
                //should not get here, callback should be disabled/unregistered
                finishAffinity()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == Intent.ACTION_SEARCH) binding.drawerLayoutMain.searchView.setQuery(
            intent.getStringExtra(SearchManager.QUERY),
            true
        )
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
                binding.drawerLayoutMain.showSearchMode()
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

    inner class SearchModeListener : ToolbarLayout.SearchModeListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            if (search == null) return false
            lifecycleScope.launch {
                search = query ?: ""
                updateUserSettings { it.copy(search = query ?: "") }
                refreshApps()
            }
            return true
        }

        override fun onQueryTextChange(query: String?): Boolean {
            if (search == null) return false
            lifecycleScope.launch {
                search = query ?: ""
                updateUserSettings { it.copy(search = query ?: "") }
                refreshApps()
            }
            return true
        }

        override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
            lifecycleScope.launch {
                if (visible) {
                    search = getUserSettings().search
                    backPressEnabled.value = true
                    searchView.setQuery(search, false)
                    val autoCompleteTextView = searchView.seslGetAutoCompleteView()
                    autoCompleteTextView.setText(search)
                    autoCompleteTextView.setSelection(autoCompleteTextView.text.length)
                    refreshApps()
                } else {
                    search = null
                    backPressEnabled.value = false
                    refreshApps()
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initDrawer() {
        val pickIconFromApkOption = findViewById<LinearLayout>(R.id.draweritem_extract_icon_from_apk)
        val aboutAppOption = findViewById<LinearLayout>(R.id.draweritem_about_app)
        val aboutMeOption = findViewById<LinearLayout>(R.id.draweritem_about_me)
        val settingsOption = findViewById<LinearLayout>(R.id.draweritem_settings)
        pickIconFromApkOption.setOnClickListener {
            pickApkActivityResultLauncher.launch("application/vnd.android.package-archive")
            //pickApkActivityResultLauncher.launch("*/*")
        }
        aboutAppOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }
        aboutMeOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutMeActivity::class.java))
        }
        settingsOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        binding.drawerLayoutMain.setDrawerButtonIcon(
            AppCompatResources.getDrawable(
                this,
                dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline
            )
        )
        binding.drawerLayoutMain.setDrawerButtonOnClickListener {
            startActivity(Intent().setClass(this@MainActivity, AboutActivity::class.java))
        }
        binding.drawerLayoutMain.setDrawerButtonTooltip(getText(R.string.about_app))
        binding.drawerLayoutMain.setSearchModeListener(SearchModeListener())
        binding.drawerLayoutMain.searchView.setSearchableInfo(
            (getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName)
        )
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayoutMain.setButtonBadges(ToolbarLayout.N_BADGE, DrawerLayout.N_BADGE)
        }
        binding.drawerLayoutMain.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                getSystemService(INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.iconNoEntryView.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.iconNoEntryView.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
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
            holder.item.setOnClickListener {
                try {
                    startActivity(
                        Intent(this@MainActivity, IconActivity::class.java)
                            .putExtra("applicationInfo", packageManager.getApplicationInfo(packageName, 0)),
                        ActivityOptions
                            .makeSceneTransitionAnimation(
                                this@MainActivity,
                                Pair.create(holder.appIcon, "icon"),
                            )
                            .toBundle()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.apppickerList.itemAnimator = null
        binding.apppickerList.seslSetSmoothScrollEnabled(true)
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
            Toast.makeText(this@MainActivity, R.string.error_loading_apps, Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    private fun refreshApps() {
        binding.iconListLottie.visibility = View.GONE
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
}