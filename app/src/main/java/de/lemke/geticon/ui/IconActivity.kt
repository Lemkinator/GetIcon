package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColor
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import androidx.reflect.app.SeslApplicationPackageManagerReflector
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.geticon.R
import de.lemke.geticon.data.SaveLocation
import de.lemke.geticon.databinding.ActivityIconBinding
import de.lemke.geticon.domain.ExportIconToSaveLocationUseCase
import de.lemke.geticon.domain.ExportIconUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.ShowInAppReviewOrFinishUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.domain.setCustomAnimatedOnBackPressedLogic
import de.lemke.geticon.domain.setWindowTransparent
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


@AndroidEntryPoint
class IconActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIconBinding
    private lateinit var icon: Bitmap
    private lateinit var applicationInfo: ApplicationInfo
    private lateinit var saveLocation: SaveLocation
    private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri?>
    private var size: Int = 0
    private var maskEnabled: Boolean = true
    private var colorEnabled: Boolean = false
    private var foregroundColor: Int = 0
    private var backgroundColor: Int = 0
    private val minSize = 16
    private val maxSize = 1024

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var exportIcon: ExportIconUseCase

    @Inject
    lateinit var exportIconToSaveLocation: ExportIconToSaveLocationUseCase

    @Inject
    lateinit var showInAppReviewOrFinish: ShowInAppReviewOrFinishUseCase

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
        get() = SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray(packageManager, applicationInfo.packageName, 1)

    private val hasMaskedAppIcon get() = isAdaptiveIcon || maskedAppIcon != null

    @Suppress("unused")
    @SuppressLint("RestrictedApi")
    private fun getMaskedAactivityIcon(activityName: String?): Drawable? = if (activityName.isNullOrBlank()) maskedAppIcon
    else {
        val componentName = ComponentName(applicationInfo.packageName, activityName)
        SeslApplicationPackageManagerReflector.semGetActivityIconForIconTray(packageManager, componentName, 1)
    }

    private val fileName: String
        get() = applicationInfo.packageName + "_" + if (maskEnabled) "mask" else "default" + if (colorEnabled) "_mono" else ""

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIconBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        binding.root.setNavigationButtonOnClickListener { lifecycleScope.launch { showInAppReviewOrFinish(this@IconActivity) } }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        try {
            val nullableApplicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("applicationInfo", ApplicationInfo::class.java)
            } else {
                intent.getParcelableExtra("applicationInfo")
            }
            if (nullableApplicationInfo == null) {
                Toast.makeText(this, getString(R.string.error_app_not_found), Toast.LENGTH_SHORT).show()
                finishAfterTransition()
                return
            } else {
                applicationInfo = nullableApplicationInfo
            }
            binding.root.setTitle(applicationInfo.loadLabel(packageManager))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_app_not_found), Toast.LENGTH_SHORT).show()
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
            saveLocation = userSettings.saveLocation
            initViews()
            setCustomAnimatedOnBackPressedLogic(binding.root, showInAppReviewOrFinish.canShowInAppReview()) {
                lifecycleScope.launch { showInAppReviewOrFinish(this@IconActivity) }
            }
        }
        pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            lifecycleScope.launch { exportIcon(uri, icon, fileName) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_icon, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_icon_save_as_image -> {
                if (saveLocation == SaveLocation.CUSTOM || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
                } else {
                    lifecycleScope.launch { exportIconToSaveLocation(saveLocation, icon, fileName) }
                }
                return true
            }

            R.id.menu_item_icon_share -> {
                val cacheFile = File(cacheDir, "icon.png")
                icon.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
                val uri = FileProvider.getUriForFile(this, "de.lemke.geticon.fileprovider", cacheFile)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "image/png"
                }
                startActivity(Intent.createChooser(sendIntent, null))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun initViews() {
        generateIcon()
        binding.icon.setOnClickListener { copyIconToClipboard() }
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
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                textView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            textView.clearFocus()
            true
        }
        binding.sizeSeekbar.min = minSize
        binding.sizeSeekbar.max = maxSize
        binding.sizeSeekbar.progress = size
        binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                size = progress
                binding.sizeEdittext.setText(size.toString())
                generateIcon()
                lifecycleScope.launch { updateUserSettings { it.copy(iconSize = size) } }
            }

            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
        })
        if (isAdaptiveIcon) {
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
    }

    @SuppressLint("PrivateResource")
    private fun setButtonColors() {
        if (isAdaptiveIcon && colorEnabled) {
            binding.colorButtonBackground.isEnabled = true
            binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
            binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            binding.colorButtonForeground.isEnabled = true
            binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
            binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(foregroundColor)
        } else {
            binding.colorButtonBackground.isEnabled = false
            binding.colorButtonBackground.setTextColor(getColor(R.color.secondary_text_icon_color))
            binding.colorButtonBackground.backgroundTintList =
                ColorStateList.valueOf(getColor(androidx.appcompat.R.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonForeground.isEnabled = false
            binding.colorButtonForeground.setTextColor(getColor(R.color.secondary_text_icon_color))
            binding.colorButtonForeground.backgroundTintList =
                ColorStateList.valueOf(getColor(androidx.appcompat.R.color.sesl_show_button_shapes_color_disabled))
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    private fun copyIconToClipboard() {
        val cacheFile = File(cacheDir, "icon.png")
        icon.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
        val uri = FileProvider.getUriForFile(this, "de.lemke.geticon.fileprovider", cacheFile)
        val clip = ClipData.newUri(contentResolver, "icon", uri)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}