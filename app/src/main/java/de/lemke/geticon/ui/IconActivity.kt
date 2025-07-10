package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList.valueOf
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColor
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import androidx.reflect.app.SeslApplicationPackageManagerReflector.semGetActivityIconForIconTray
import androidx.reflect.app.SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray
import com.google.android.material.appbar.model.ButtonModel
import com.google.android.material.appbar.model.SuggestAppBarModel
import com.google.android.material.appbar.model.view.SuggestAppBarView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityIconBinding
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR


@AndroidEntryPoint
class IconActivity : AppCompatActivity(), ViewYTranslator by AppBarAwareYTranslator() {
    companion object {
        const val KEY_APPLICATION_INFO = "applicationInfo"
    }

    private lateinit var binding: ActivityIconBinding
    private lateinit var icon: Bitmap
    private lateinit var applicationInfo: ApplicationInfo
    private var size: Int = 0
    private var maskEnabled: Boolean = true
    private var colorEnabled: Boolean = false
    private var foregroundColor: Int = 0
    private var backgroundColor: Int = 0
    private val minSize = 16
    private val maxSize = 1024
    private val exportBitmapResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) saveBitmapToUri(result.data?.data, icon)
    }

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    private val appIcon: Drawable
        get() = try {
            applicationInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            e.printStackTrace()
            AppCompatResources.getDrawable(this, dev.oneuiproject.oneui.R.drawable.ic_oui_file_type_image)!!
        }

    private val isAdaptiveIcon get() = appIcon is AdaptiveIconDrawable

    private val maskedAppIcon: Drawable?
        @SuppressLint("RestrictedApi")
        get() = semGetApplicationIconForIconTray(packageManager, applicationInfo.packageName, 1)

    private val hasMaskedAppIcon get() = isAdaptiveIcon || maskedAppIcon != null

    @Suppress("unused")
    @SuppressLint("RestrictedApi")
    private fun getMaskedActivityIcon(activityName: String?): Drawable? = if (activityName.isNullOrBlank()) maskedAppIcon
    else {
        val componentName = ComponentName(applicationInfo.packageName, activityName)
        semGetActivityIconForIconTray(packageManager, componentName, 1)
    }

    private val fileName: String
        get() = applicationInfo.packageName + "_" + if (maskEnabled) "mask" else "default" + if (colorEnabled) "_mono" else ""

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIconBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        try {
            val nullableApplicationInfo =
                if (SDK_INT >= TIRAMISU) intent.getParcelableExtra(KEY_APPLICATION_INFO, ApplicationInfo::class.java)
                else intent.getParcelableExtra(KEY_APPLICATION_INFO)
            if (nullableApplicationInfo != null) applicationInfo = nullableApplicationInfo
            else {
                toast(commonutilsR.string.commonutils_error_app_not_found)
                finishAfterTransition()
                return
            }
            binding.root.setTitle(applicationInfo.loadLabel(packageManager))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(commonutilsR.string.commonutils_error_app_not_found)
            finishAfterTransition()
            return
        }
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            size = userSettings.iconSize
            maskEnabled = userSettings.maskEnabled
            colorEnabled = userSettings.colorEnabled
            foregroundColor = userSettings.recentForegroundColors.first()
            backgroundColor = userSettings.recentBackgroundColors.first()
            initViews()
            setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
            binding.icon.translateYWithAppBar(binding.root.appBarLayout, this@IconActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = menuInflater.inflate(R.menu.menu_icon, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_item_icon_save_as_image -> exportBitmap(commonUtilsSettings.imageSaveLocation, icon, fileName, exportBitmapResultLauncher)
        R.id.menu_item_icon_share -> shareBitmap(icon, "icon.png")
        else -> super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun initViews() {
        generateIcon()
        binding.icon.setOnClickListener { icon.copyToClipboard(this, "icon", "icon.png") }
        binding.maskedCheckbox.isChecked = maskEnabled && hasMaskedAppIcon
        binding.maskedCheckbox.isEnabled = hasMaskedAppIcon
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            maskEnabled = isChecked
            generateIcon()
            lifecycleScope.launch { updateUserSettings { it.copy(maskEnabled = isChecked) } }
        }
        binding.colorCheckbox.isChecked = colorEnabled && isAdaptiveIcon
        binding.colorCheckbox.isEnabled = isAdaptiveIcon
        setButtonColors()
        binding.sizeEdittext.setText(size.toString())
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            val newSize = textView.text.toString().toIntOrNull()
            if (newSize != null) {
                size = newSize.coerceAtLeast(minSize).coerceAtMost(maxSize)
                binding.sizeSeekbar.progress = size
                generateIcon()
                lifecycleScope.launch { updateUserSettings { it.copy(iconSize = size) } }
            }
            hideSoftInput()
            true
        }
        binding.sizeSeekbar.min = minSize
        binding.sizeSeekbar.max = maxSize
        binding.sizeSeekbar.progress = size
        binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                size = progress
                binding.sizeEdittext.setText(size.toString())
                generateIcon()
                lifecycleScope.launch { updateUserSettings { it.copy(iconSize = size) } }
            }
        })
        if (isAdaptiveIcon) setupAdaptiveIconOptions()
        binding.root.setAppBarSuggestView(createSuggestAppBarModel())
    }

    private fun setupAdaptiveIconOptions() {
        binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            colorEnabled = isChecked
            setButtonColors()
            generateIcon()
            lifecycleScope.launch { updateUserSettings { it.copy(colorEnabled = isChecked) } }
        }
        binding.colorButtonBackground.setOnClickListener {
            lifecycleScope.launch {
                val userSettings = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@IconActivity,
                    { color: Int ->
                        backgroundColor = color
                        generateIcon()
                        setButtonColors()
                        val recentColors = (listOf(color) + userSettings.recentBackgroundColors).distinct().take(6)
                        lifecycleScope.launch { updateUserSettings { it.copy(recentBackgroundColors = recentColors) } }
                    },
                    userSettings.recentBackgroundColors.first(), userSettings.recentBackgroundColors.toIntArray(), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
        binding.colorButtonForeground.setOnClickListener {
            lifecycleScope.launch {
                val userSettings = getUserSettings()
                val dialog = SeslColorPickerDialog(
                    this@IconActivity,
                    { color: Int ->
                        foregroundColor = color
                        generateIcon()
                        setButtonColors()
                        val recentColors = (listOf(color) + userSettings.recentForegroundColors).distinct().take(6)
                        lifecycleScope.launch { updateUserSettings { it.copy(recentForegroundColors = recentColors) } }
                    },
                    userSettings.recentForegroundColors.first(), userSettings.recentForegroundColors.toIntArray(), true
                )
                dialog.setTransparencyControlEnabled(true)
                dialog.show()
            }
        }
    }

    private fun createSuggestAppBarModel(): SuggestAppBarModel<SuggestAppBarView> =
        SuggestAppBarModel.Builder(this).apply {
            setTitle(getString(R.string.tap_icon_to_copy_to_clipboard))
            setCloseClickListener { _, _ -> binding.root.setAppBarSuggestView(null) }
            setButtons(
                arrayListOf(
                    ButtonModel(
                        text = getString(R.string.copy_icon),
                        clickListener = { _, _ -> icon.copyToClipboard(this@IconActivity, "icon", "icon.png") },
                    )
                )
            )
        }.build()

    @SuppressLint("PrivateResource")
    private fun setButtonColors() {
        if (isAdaptiveIcon && colorEnabled) {
            binding.colorButtonBackground.isEnabled = true
            binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
            binding.colorButtonBackground.backgroundTintList = valueOf(backgroundColor)
            binding.colorButtonForeground.isEnabled = true
            binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
            binding.colorButtonForeground.backgroundTintList = valueOf(foregroundColor)
        } else {
            binding.colorButtonBackground.isEnabled = false
            binding.colorButtonBackground.setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonBackground.backgroundTintList = valueOf(getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonForeground.isEnabled = false
            binding.colorButtonForeground.setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonForeground.backgroundTintList = valueOf(getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
        }
    }

    private fun generateIcon() {
        val drawable = appIcon.mutate()
        if (drawable is AdaptiveIconDrawable && drawable.foreground != null && drawable.background != null) {
            Log.d("IconActivity", "Icon is adaptive and has foreground and background")
            icon = drawable.toBitmap(size, size)
            drawable.setBounds(0, 0, size, size)
            val background = drawable.background.mutate()
            var foreground = drawable.foreground.mutate()
            if (colorEnabled) {
                if (SDK_INT >= TIRAMISU) {
                    val monochrome = drawable.monochrome
                    if (monochrome != null) foreground = monochrome.mutate()
                }
                background.setTint(backgroundColor)
                foreground.setTint(foregroundColor)
            }
            val canvas = Canvas(icon)
            if (maskEnabled) canvas.clipPath(drawable.iconMask)
            background.draw(canvas)
            foreground.draw(canvas)
        } else {
            Log.d("IconActivity", "Icon is not adaptive or has no foreground or background")
            icon = if (maskEnabled && hasMaskedAppIcon) maskedAppIcon?.toBitmap(size, size) ?: appIcon.toBitmap(size, size)
            else drawable.toBitmap(size, size)
        }
        binding.icon.setImageBitmap(icon)
    }
}