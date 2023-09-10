package de.lemke.geticon.ui

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
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslProgressBar
import androidx.apppickerview.widget.AppPickerView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityMainBinding
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.widget.Toast
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AppPickerView.OnBindListener {
    private lateinit var binding: ActivityMainBinding
    private var listType = AppPickerView.TYPE_GRID
    private var showSystemApps = false
    private val items: MutableList<Boolean> = ArrayList()
    private var isAllAppsSelected = false
    private var checkedPosition = 0
    private lateinit var appPickerView: AppPickerView
    private lateinit var progress: SeslProgressBar
    private var time: Long = 0
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

        progress = binding.apppickerProgress
        appPickerView = binding.apppickerList
        appPickerView.itemAnimator = null
        appPickerView.seslSetSmoothScrollEnabled(true)
        lifecycleScope.launch {
            binding.maskedCheckbox.isChecked = getUserSettings().mask
            binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                lifecycleScope.launch { updateUserSettings { it.copy(mask = isChecked) } }
            }
            binding.colorCheckbox.isChecked = getUserSettings().colorEnabled
            binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                lifecycleScope.launch { updateUserSettings { it.copy(colorEnabled = isChecked) } }
            }
            binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(getUserSettings().recentBackgroundColors.first())
            binding.colorButtonBackground.setOnClickListener {
                lifecycleScope.launch {
                    val userSettings = getUserSettings()
                    val dialog = SeslColorPickerDialog(
                        this@MainActivity,
                        { color: Int ->
                            val recentColors = userSettings.recentBackgroundColors.toMutableList()
                            if (recentColors.size >= 6) recentColors.removeAt(5)
                            recentColors.add(0, color)
                            lifecycleScope.launch { updateUserSettings { it.copy(recentBackgroundColors = recentColors) } }
                            binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(color)
                        },
                        userSettings.recentBackgroundColors.first(), buildIntArray(userSettings.recentBackgroundColors), true
                    )
                    dialog.setTransparencyControlEnabled(true)
                    dialog.show()
                }
            }
            binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(getUserSettings().recentForegroundColors.first())
            binding.colorButtonForeground.setOnClickListener {
                lifecycleScope.launch {
                    val userSettings = getUserSettings()
                    val dialog = SeslColorPickerDialog(
                        this@MainActivity,
                        { color: Int ->
                            val recentColors = userSettings.recentForegroundColors.toMutableList()
                            if (recentColors.size >= 6) recentColors.removeAt(5)
                            recentColors.add(0, color)
                            lifecycleScope.launch { updateUserSettings { it.copy(recentForegroundColors = recentColors) } }
                            binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(color)
                        },
                        userSettings.recentForegroundColors.first(), buildIntArray(userSettings.recentForegroundColors), true
                    )
                    dialog.setTransparencyControlEnabled(true)
                    dialog.show()
                }
            }
            showSystemApps = getUserSettings().showSystemApps
            fillListView()
            isUIReady = true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_picker, menu)
        val systemAppsItem = menu.findItem(R.id.menu_apppicker_system)
        lifecycleScope.launch {
            showSystemApps = getUserSettings().showSystemApps
            systemAppsItem.title = getString(if (showSystemApps) R.string.hide_system_apps else R.string.show_system_apps)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_apppicker_system) {
            showSystemApps = !showSystemApps
            lifecycleScope.launch { updateUserSettings { it.copy(showSystemApps = showSystemApps) } }
            item.title = getString(if (showSystemApps) R.string.hide_system_apps else R.string.show_system_apps)
            refreshListView()
            return true
        }
        return false
    }

    private fun fillListView() {
        isAllAppsSelected = false
        showProgressCircle(true)
        object : Thread() {
            override fun run() {
                runOnUiThread {
                    val installedAppSet = ArrayList(installedPackageNameUnmodifiableSet)
                    if (appPickerView.itemDecorationCount > 0) {
                        for (i in 0 until appPickerView.itemDecorationCount) {
                            appPickerView.removeItemDecorationAt(i)
                        }
                    }
                    appPickerView.setAppPickerView(listType, installedAppSet, AppPickerView.ORDER_ASCENDING_IGNORE_CASE)
                    appPickerView.setOnBindListener(this@MainActivity)
                    items.clear()
                    if (listType == AppPickerView.TYPE_LIST_CHECKBOX_WITH_ALL_APPS
                        || listType == AppPickerView.TYPE_LIST_SWITCH_WITH_ALL_APPS
                    ) {
                        items.add(java.lang.Boolean.FALSE)
                    }
                    for (app in installedAppSet) {
                        items.add(java.lang.Boolean.FALSE)
                    }
                    showProgressCircle(false)
                }
            }
        }.start()
    }

    private fun refreshListView() {
        showProgressCircle(true)
        object : Thread() {
            override fun run() {
                runOnUiThread {
                    val installedAppSet = ArrayList(installedPackageNameUnmodifiableSet)
                    appPickerView.resetPackages(installedAppSet)
                    items.clear()
                    if (listType == AppPickerView.TYPE_LIST_CHECKBOX_WITH_ALL_APPS
                        || listType == AppPickerView.TYPE_LIST_SWITCH_WITH_ALL_APPS
                    ) {
                        items.add(java.lang.Boolean.FALSE)
                    }
                    for (app in installedAppSet) {
                        items.add(java.lang.Boolean.FALSE)
                    }
                    showProgressCircle(false)
                }
            }
        }.start()
    }

    override fun onBindViewHolder(
        holder: AppPickerView.ViewHolder,
        position: Int, packageName: String
    ) {
        when (listType) {
            AppPickerView.TYPE_LIST -> holder.item.setOnClickListener { }
            AppPickerView.TYPE_LIST_ACTION_BUTTON ->
                holder.actionButton.setOnClickListener { Toast.makeText(this, "onClick", Toast.LENGTH_SHORT).show() }

            AppPickerView.TYPE_LIST_CHECKBOX -> {
                val checkBox = holder.checkBox
                checkBox.isChecked = items[position]
                checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> items[position] = isChecked }
            }

            AppPickerView.TYPE_LIST_CHECKBOX_WITH_ALL_APPS -> {
                val checkBox = holder.checkBox
                if (position == 0) {
                    holder.appLabel.text = getString(R.string.all_apps)
                    checkBox.isChecked = isAllAppsSelected
                    checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                        if (isAllAppsSelected != isChecked) {
                            isAllAppsSelected = isChecked
                            var i = 0
                            while (i < items.size) {
                                items[i] = isAllAppsSelected
                                i++
                            }
                            appPickerView.refreshUI()
                        }
                    }
                } else {
                    checkBox.isChecked = items[position]
                    checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                        items[position] = isChecked
                        checkAllAppsToggle()
                    }
                }
            }

            AppPickerView.TYPE_LIST_RADIOBUTTON -> {
                val radioButton = holder.radioButton
                radioButton.isChecked = items[position]
                holder.radioButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    if (isChecked) {
                        if (checkedPosition != position) {
                            items[checkedPosition] = false
                            appPickerView.refreshUI(checkedPosition)
                        }
                        items[position] = true
                        checkedPosition = position
                    }
                }
            }

            AppPickerView.TYPE_LIST_SWITCH -> {
                val switchWidget = holder.switch
                switchWidget.isChecked = items[position]
                switchWidget.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> items[position] = isChecked }
            }

            AppPickerView.TYPE_LIST_SWITCH_WITH_ALL_APPS -> {
                val switchWidget = holder.switch
                if (position == 0) {
                    holder.appLabel.text = getString(R.string.all_apps)
                    switchWidget.isChecked = isAllAppsSelected
                    switchWidget.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                        if (isAllAppsSelected != isChecked) {
                            isAllAppsSelected = isChecked
                            var i = 0
                            while (i < items.size) {
                                items[i] = isAllAppsSelected
                                i++
                            }
                            appPickerView.refreshUI()
                        }
                    }
                } else {
                    switchWidget.isChecked = items[position]
                    switchWidget.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                        items[position] = isChecked
                        checkAllAppsToggle()
                    }
                }
            }

            AppPickerView.TYPE_GRID -> holder.item.setOnClickListener {
                saveIcon(packageName)
            }

            AppPickerView.TYPE_GRID_CHECKBOX -> {
                val checkBox = holder.checkBox
                checkBox.isChecked = items[position]
                checkBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> items[position] = isChecked }
                holder.item.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            }
        }
    }

    private fun checkAllAppsToggle() {
        isAllAppsSelected = true
        for (selected in items) {
            if (!selected) {
                isAllAppsSelected = false
                break
            }
        }
        appPickerView.refreshUI(0)
    }

    private fun showProgressCircle(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        appPickerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private val installedPackageNameUnmodifiableSet: Set<String>
        get() {
            val set = HashSet<String>()
            for (appInfo in installedAppList) {
                set.add(appInfo.packageName)
            }
            return Collections.unmodifiableSet(set)
        }

    private val installedAppList: List<ApplicationInfo>
        get() {
            val list = ArrayList<ApplicationInfo>()
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in apps) {
                if (appInfo.flags and (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or ApplicationInfo.FLAG_SYSTEM) > 0 && !showSystemApps) {
                    continue
                }
                list.add(appInfo)
            }
            return list
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