package de.lemke.geticon.ui

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.apppickerview.widget.AppPickerView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.domain.setCustomOnBackPressedLogic
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val backPressEnabled = MutableStateFlow(false)
    private var showSystemApps = false
    private var search: String? = null
    private var time: Long = 0
    private var refreshAppsJob: Job? = null
    private var isUIReady = false

    private val iconSize = 512

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        /*  Note: https://stackoverflow.com/a/69831106/18332741
         On Android 12 just running the app via android studio doesn't show the full splash screen.
         You have to kill it and open the app from the launcher.
         */
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
            setCustomOnBackPressedLogic(triggerStateFlow = backPressEnabled, onBackPressedLogic = { checkBackPressed() })
            showSystemApps = getUserSettings().showSystemApps
            initSettingsViews()
            initAppPicker()
            binding.toolbarLayout.setSearchModeListener(SearchModeListener())
            binding.toolbarLayout.searchView.setSearchableInfo(
                (getSystemService(SEARCH_SERVICE) as SearchManager).getSearchableInfo(componentName)
            )
            isUIReady = true
        }
    }

    private fun checkBackPressed() {
        when {
            binding.toolbarLayout.isSearchMode -> {
                if (ViewCompat.getRootWindowInsets(binding.root)!!.isVisible(WindowInsetsCompat.Type.ime())) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                        currentFocus!!.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
                } else {
                    search = null
                    binding.toolbarLayout.dismissSearchMode()
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
        if (intent?.action == Intent.ACTION_SEARCH) binding.toolbarLayout.searchView.setQuery(
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
                binding.toolbarLayout.showSearchMode()
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

    private fun initAppPicker() {
        if (binding.apppickerList.itemDecorationCount > 0) {
            for (i in 0 until binding.apppickerList.itemDecorationCount) {
                binding.apppickerList.removeItemDecorationAt(i)
            }
        }
        binding.apppickerList.setAppPickerView(AppPickerView.TYPE_GRID, getApps(), AppPickerView.ORDER_ASCENDING_IGNORE_CASE)
        binding.apppickerList.setOnBindListener { holder: AppPickerView.ViewHolder, _: Int, packageName: String ->
            holder.item.setOnClickListener { saveIcon(packageName) }
        }
        binding.apppickerList.itemAnimator = null
        binding.apppickerList.seslSetSmoothScrollEnabled(true)
    }

    private suspend fun initSettingsViews() {
        val userSettings = getUserSettings()
        binding.maskedCheckbox.isChecked = userSettings.mask
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            lifecycleScope.launch { updateUserSettings { it.copy(mask = isChecked) } }
        }
        binding.colorCheckbox.isChecked = userSettings.colorEnabled
        binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            lifecycleScope.launch { updateUserSettings { it.copy(colorEnabled = isChecked) } }
        }
        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(userSettings.recentBackgroundColors.first())
        binding.colorButtonBackground.setOnClickListener {
            lifecycleScope.launch {
                val userSettingsColor = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@MainActivity,
                    { color: Int ->
                        val recentColors = userSettingsColor.recentBackgroundColors.toMutableList()
                        if (recentColors.size >= 6) recentColors.removeAt(5)
                        recentColors.add(0, color)
                        lifecycleScope.launch { updateUserSettings { it.copy(recentBackgroundColors = recentColors) } }
                        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(color)
                    },
                    userSettingsColor.recentBackgroundColors.first(), buildIntArray(userSettingsColor.recentBackgroundColors), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(userSettings.recentForegroundColors.first())
        binding.colorButtonForeground.setOnClickListener {
            lifecycleScope.launch {
                val userSettingsColor = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@MainActivity,
                    { color: Int ->
                        val recentColors = userSettingsColor.recentForegroundColors.toMutableList()
                        if (recentColors.size >= 6) recentColors.removeAt(5)
                        recentColors.add(0, color)
                        lifecycleScope.launch { updateUserSettings { it.copy(recentForegroundColors = recentColors) } }
                        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(color)
                    },
                    userSettingsColor.recentForegroundColors.first(), buildIntArray(userSettingsColor.recentForegroundColors), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
    }

    private fun getApps(): List<String> {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val filteredApps = if (showSystemApps) apps
        else apps.filter { it.flags and (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or ApplicationInfo.FLAG_SYSTEM) == 0 }
        return if (search.isNullOrBlank()) filteredApps.map { it.packageName }
        else filteredApps.filter {
            packageManager.getApplicationLabel(it).toString().contains(search!!, ignoreCase = true) ||
                    it.packageName.contains(search!!, ignoreCase = true)
        }.map { it.packageName }
    }

    private fun refreshApps() {
        refreshAppsJob?.cancel()
        if (!this@MainActivity::binding.isInitialized) return
        refreshAppsJob = lifecycleScope.launch {
            val apps = getApps()
            if (apps.isEmpty() || search?.isBlank() == true) {
                binding.apppickerList.visibility = View.GONE
                binding.urlListLottie.cancelAnimation()
                binding.urlListLottie.progress = 0f
                binding.urlNoEntryScrollView.visibility = View.VISIBLE
                binding.urlListLottie.addValueCallback(
                    KeyPath("**"),
                    LottieProperty.COLOR_FILTER,
                    LottieValueCallback(SimpleColorFilter(getColor(R.color.primary_color_themed)))
                )
                binding.urlListLottie.postDelayed({ binding.urlListLottie.playAnimation() }, 400)
            } else {
                binding.apppickerList.resetPackages(apps)
                binding.urlNoEntryScrollView.visibility = View.GONE
                binding.apppickerList.visibility = View.VISIBLE
            }
        }
    }

    private fun saveIcon(packageName: String) {
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            try {
                val timeStamp = System.currentTimeMillis()
                val base = packageManager.getApplicationIcon(packageName)
                val icon = buildIcon(
                    base,
                    iconSize,
                    userSettings.mask,
                    userSettings.colorEnabled,
                    userSettings.recentForegroundColors.first(),
                    userSettings.recentBackgroundColors.first()
                )
                val suffix = if (userSettings.mask) "mask" else "default" + if (userSettings.colorEnabled) "_mono" else ""
                val fileName = String.format("%s_%s_%d.png", packageName, suffix, timeStamp)
                try {
                    saveBitmap(icon, fileName)
                } catch (e: IOException) {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
                Toast.makeText(this@MainActivity, fileName, Toast.LENGTH_SHORT).show()
            } catch (e: PackageManager.NameNotFoundException) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun buildIcon(
        drawable: Drawable,
        size: Int,
        mask: Boolean,
        color: Boolean,
        foregroundColor: Int,
        backgroundColor: Int
    ): Bitmap {
        if (drawable is AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, size, size)
            val background = drawable.background.mutate()
            var foreground = drawable.foreground.mutate()
            if (color && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val monochrome = drawable.monochrome
                if (monochrome != null) foreground = monochrome.mutate()
            }
            if (color) {
                background.setTint(backgroundColor)
                foreground.setTint(foregroundColor)
            }
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            if (mask) canvas.clipPath(drawable.iconMask)
            background.draw(canvas)
            foreground.draw(canvas)
            return output
        }
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawable.draw(canvas)
        return output
    }

    @Throws(IOException::class)
    private fun saveBitmap(bitmap: Bitmap, fileName: String) {
        val os: OutputStream =
            Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName).toPath())
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, os)
        os.close()
    }

    private fun buildIntArray(integers: List<Int>): IntArray {
        val ints = IntArray(integers.size)
        var i = 0
        for (n in integers) {
            ints[i++] = n
        }
        return ints
    }

}