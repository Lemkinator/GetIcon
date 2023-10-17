package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColor
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.picker3.app.SeslColorPickerDialog
import androidx.reflect.app.SeslApplicationPackageManagerReflector
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.geticon.R
import de.lemke.geticon.data.SaveLocation
import de.lemke.geticon.databinding.ActivityIconBinding
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import de.lemke.geticon.domain.setCustomOnBackPressedLogic
import dev.oneuiproject.oneui.widget.Toast
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.inject.Inject


@AndroidEntryPoint
class IconActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIconBinding
    private lateinit var icon: Bitmap
    private lateinit var applicationInfo: ApplicationInfo
    private lateinit var saveLocation: SaveLocation
    private var size: Int = 0
    private var maskEnabled: Boolean = true
    private var colorEnabled: Boolean = false
    private var foregroundColor: Int = 0
    private var backgroundColor: Int = 0
    private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri>
    private val minSize = 16
    private val maxSize = 1024

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    private val isAdaptiveIcon: Boolean
        get() = appIcon is AdaptiveIconDrawable

    private val appIcon: Drawable
        get() = packageManager.getApplicationIcon(applicationInfo.packageName)

    private val maskedAppIcon: Drawable
        @SuppressLint("RestrictedApi")
        get() = SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray(packageManager, applicationInfo.packageName, 1)
            ?: appIcon

    @Suppress("unused")
    @SuppressLint("RestrictedApi")
    private fun getMaskedAppIcon(activityName: String?): Drawable = if (!activityName.isNullOrBlank()) {
        val componentName = ComponentName(applicationInfo.packageName, activityName)
        SeslApplicationPackageManagerReflector.semGetActivityIconForIconTray(packageManager, componentName, 1)
            ?: packageManager.getActivityIcon(componentName)
    } else maskedAppIcon

    private val fileName: String
        get() {
            val timeStamp = System.currentTimeMillis()
            val suffix = if (maskEnabled) "mask" else "default" + if (colorEnabled) "_mono" else ""
            return String.format("%s_%s_%d.png", applicationInfo.packageName, suffix, timeStamp)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIconBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setNavigationButtonOnClickListener { showInAppReviewOrFinish() }
        binding.root.tooltipText = getString(R.string.sesl_navigate_up)
        val packageName = intent.getStringExtra("packageName")
        if (packageName == null) {
            android.widget.Toast.makeText(this, getString(R.string.error_app_not_found), android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        binding.root.setTitle(packageManager.getApplicationLabel(applicationInfo))
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            size = userSettings.iconSize
            maskEnabled = userSettings.maskEnabled
            colorEnabled = userSettings.colorEnabled
            foregroundColor = userSettings.recentForegroundColors.first()
            backgroundColor = userSettings.recentBackgroundColors.first()
            saveLocation = userSettings.saveLocation
            initViews()
        }
        pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null)
                android.widget.Toast.makeText(
                    this@IconActivity,
                    getString(R.string.error_no_folder_selected),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            else lifecycleScope.launch { exportIcon(uri) }
        }
        setCustomOnBackPressedLogic { showInAppReviewOrFinish() }
    }

    private fun showInAppReviewOrFinish() {
        lifecycleScope.launch {
            try {
                val lastInAppReviewRequest = getUserSettings().lastInAppReviewRequest
                val daysSinceLastRequest = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastInAppReviewRequest)
                if (daysSinceLastRequest < 14) {
                    finishAfterTransition()
                    return@launch
                }
                updateUserSettings { it.copy(lastInAppReviewRequest = System.currentTimeMillis()) }
                val manager = ReviewManagerFactory.create(this@IconActivity)
                //val manager = FakeReviewManager(context);
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(this@IconActivity, reviewInfo)
                        flow.addOnCompleteListener { finishAfterTransition() }
                    } else {
                        // There was some problem, log or handle the error code.
                        Log.e("InAppReview", "Review task failed: ${task.exception?.message}")
                        finishAfterTransition()
                    }
                }
            } catch (e: Exception) {
                Log.e("InAppReview", "Error: ${e.message}")
                finishAfterTransition()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_icon, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_item_icon_save_as_image -> {
                saveIcon()
                return true
            }

            R.id.menu_item_icon_copy -> {
                copyIconToClipboard()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun initViews() {
        generateIcon()
        binding.icon.setOnClickListener { saveIcon() }
        binding.icon.setOnLongClickListener {
            copyIconToClipboard()
            true
        }
        binding.maskedCheckbox.isChecked = maskEnabled
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            maskEnabled = isChecked
            generateIcon()
            lifecycleScope.launch { updateUserSettings { it.copy(maskEnabled = isChecked) } }
        }
        setButtonColors()
        binding.colorCheckbox.isChecked = colorEnabled
        binding.colorCheckbox.isEnabled = isAdaptiveIcon
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
                    val userSettingsColor = getUserSettings()
                    val dialog = SeslColorPickerDialog(
                        this@IconActivity,
                        { color: Int ->
                            backgroundColor = color
                            generateIcon()
                            val recentColors = userSettingsColor.recentBackgroundColors.toMutableList()
                            if (recentColors.size >= 6) recentColors.removeAt(5)
                            recentColors.add(0, color)
                            lifecycleScope.launch { updateUserSettings { it.copy(recentBackgroundColors = recentColors) } }
                            setButtonColors()
                        },
                        userSettingsColor.recentBackgroundColors.first(), buildIntArray(userSettingsColor.recentBackgroundColors), true
                    )
                    dialog.setTransparencyControlEnabled(true)
                    dialog.show()
                }
            }
            binding.colorButtonForeground.setOnClickListener {
                lifecycleScope.launch {
                    val userSettingsColor = getUserSettings()
                    val dialog = SeslColorPickerDialog(
                        this@IconActivity,
                        { color: Int ->
                            foregroundColor = color
                            generateIcon()
                            val recentColors = userSettingsColor.recentForegroundColors.toMutableList()
                            if (recentColors.size >= 6) recentColors.removeAt(5)
                            recentColors.add(0, color)
                            lifecycleScope.launch { updateUserSettings { it.copy(recentForegroundColors = recentColors) } }
                            setButtonColors()
                        },
                        userSettingsColor.recentForegroundColors.first(), buildIntArray(userSettingsColor.recentForegroundColors), true
                    )
                    dialog.setTransparencyControlEnabled(true)
                    dialog.show()
                }
            }
        }
    }

    private fun setButtonColors() {
        if (!isAdaptiveIcon || !colorEnabled) {
            binding.colorButtonBackground.isEnabled = false
            binding.colorButtonForeground.isEnabled = false
            binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(getColor(dev.oneuiproject.oneui.design.R.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonBackground.setTextColor(getColor(R.color.secondary_text_icon_color))
            binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(getColor(dev.oneuiproject.oneui.design.R.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonForeground.setTextColor(getColor(R.color.secondary_text_icon_color))
            return
        }
        binding.colorButtonBackground.isEnabled = true
        binding.colorButtonForeground.isEnabled = true
        binding.colorButtonBackground.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        binding.colorButtonForeground.backgroundTintList = ColorStateList.valueOf(foregroundColor)
        binding.colorButtonBackground.setTextColor(if (backgroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
        binding.colorButtonForeground.setTextColor(if (foregroundColor.toColor().luminance() >= 0.5) Color.BLACK else Color.WHITE)
    }

    private fun buildIntArray(integers: List<Int>): IntArray {
        val ints = IntArray(integers.size)
        var i = 0
        for (n in integers) {
            ints[i++] = n
        }
        return ints
    }

    private fun generateIcon() {
        val drawable = appIcon.mutate()
        if (drawable is AdaptiveIconDrawable) {
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
            icon = if (maskEnabled) maskedAppIcon.toBitmap(size, size)
            else drawable.toBitmap(size, size)
        }
        binding.icon.setImageBitmap(icon)
    }

    private fun copyIconToClipboard() {
        val cacheFile = File(cacheDir, "icon.png")
        icon.compress(Bitmap.CompressFormat.PNG, 100, cacheFile.outputStream())
        val uri = FileProvider.getUriForFile(this, "de.lemke.geticon.fileprovider", cacheFile)
        val clip = ClipData.newUri(contentResolver, "qr-code", uri)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun saveIcon() {
        if (saveLocation == SaveLocation.CUSTOM) {
            pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
            return
        }
        val dir: String = when (saveLocation) {
            SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
            SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
            SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
            SaveLocation.CUSTOM -> Environment.DIRECTORY_DOWNLOADS // should never happen
        }
        try {
            val os: OutputStream = Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), fileName).toPath())
            icon.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.close()
        } catch (e: IOException) {
            Toast.makeText(this@IconActivity, e.message, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        Toast.makeText(this, getString(R.string.icon_saved) + ": ${saveLocation.toLocalizedString(this)}", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun exportIcon(uri: Uri) {
        val pngFile = DocumentFile.fromTreeUri(this, uri)!!.createFile("image/png", fileName)
        icon.compress(Bitmap.CompressFormat.PNG, 100, contentResolver.openOutputStream(pngFile!!.uri)!!)
        Toast.makeText(this, R.string.icon_saved, android.widget.Toast.LENGTH_SHORT).show()
    }
}