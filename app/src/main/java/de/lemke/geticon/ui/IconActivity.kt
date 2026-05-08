package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList.valueOf
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.picker3.app.SeslColorPickerDialog
import com.google.android.material.appbar.model.ButtonModel
import com.google.android.material.appbar.model.SuggestAppBarModel
import com.google.android.material.appbar.model.view.SuggestAppBarView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.setWindowTransparent
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.databinding.ActivityIconBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class IconActivity :
    AppCompatActivity(),
    ViewYTranslator by AppBarAwareYTranslator() {
    companion object {
        const val KEY_APPLICATION_INFO = "applicationInfo"
    }

    private lateinit var binding: ActivityIconBinding
    private val viewModel: IconViewModel by viewModels()
    private var isRendering = false
    private var suggestViewSet = false

    private val exportBitmapResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
            val icon = viewModel.state.value.icon ?: return@registerForActivityResult
            try {
                if (result?.resultCode == RESULT_OK) {
                    saveBitmapToUri(result.data?.data, icon)
                } else {
                    toast(commonutilsR.string.commonutils_error_saving_image)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toast(commonutilsR.string.commonutils_error_saving_image)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityIconBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        initViews()
        collectState()
        collectEvents()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = menuInflater.inflate(R.menu.menu_icon, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val state = viewModel.state.value
        val icon = state.icon ?: return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.menu_item_icon_save_as_image ->
                exportBitmap(commonUtilsSettings.imageSaveLocation, icon, state.fileName, exportBitmapResultLauncher).let { true }
            R.id.menu_item_icon_share -> shareBitmap(icon, "icon.png").let { true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
        binding.icon.translateYWithAppBar(binding.root.appBarLayout, this)
        binding.icon.setOnClickListener { viewModel.state.value.icon?.copyToClipboard(this, "icon", "icon.png") }
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onMaskChanged(isChecked)
        }
        binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onColorChanged(isChecked)
        }
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            textView.text.toString().toIntOrNull()?.let { viewModel.onSizeChanged(it) }
            hideSoftInput()
            true
        }
        binding.sizeSeekbar.min = 16
        binding.sizeSeekbar.max = 1024
        binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.onSizeChanged(progress)
            }
        })
        binding.colorButtonBackground.setOnClickListener { showColorPicker(isBackground = true) }
        binding.colorButtonForeground.setOnClickListener { showColorPicker(isBackground = false) }
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(it) }
            }
        }
    }

    private fun collectEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.receiveAsFlow().collect { event ->
                    when (event) {
                        IconEvent.Finish -> {
                            toast(commonutilsR.string.commonutils_error_app_not_found)
                            finishAfterTransition()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderState(state: IconUiState) {
        isRendering = true
        if (state.appName.isNotEmpty()) binding.root.setTitle(state.appName)
        state.icon?.let { binding.icon.setImageBitmap(it) }
        binding.maskedCheckbox.isChecked = state.maskEnabled && state.hasMaskedAppIcon
        binding.maskedCheckbox.isEnabled = state.hasMaskedAppIcon
        binding.colorCheckbox.isChecked = state.colorEnabled && state.isAdaptiveIcon
        binding.colorCheckbox.isEnabled = state.isAdaptiveIcon
        if (binding.sizeSeekbar.progress != state.size) binding.sizeSeekbar.progress = state.size
        if (binding.sizeEdittext.text.toString().toIntOrNull() != state.size) binding.sizeEdittext.setText(state.size.toString())
        isRendering = false
        setButtonColors(state)
        if (!suggestViewSet && state.icon != null) {
            binding.root.setAppBarSuggestView(createSuggestAppBarModel())
            suggestViewSet = true
        }
    }

    @SuppressLint("PrivateResource")
    private fun setButtonColors(state: IconUiState) {
        if (state.isAdaptiveIcon && state.colorEnabled) {
            binding.colorButtonBackground.isEnabled = true
            binding.colorButtonBackground.setTextColor(if (state.backgroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
            binding.colorButtonBackground.backgroundTintList = valueOf(state.backgroundColor)
            binding.colorButtonForeground.isEnabled = true
            binding.colorButtonForeground.setTextColor(if (state.foregroundColor.toColor().luminance() >= 0.5) BLACK else WHITE)
            binding.colorButtonForeground.backgroundTintList = valueOf(state.foregroundColor)
        } else {
            binding.colorButtonBackground.isEnabled = false
            binding.colorButtonBackground.setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonBackground.backgroundTintList = valueOf(getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonForeground.isEnabled = false
            binding.colorButtonForeground.setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonForeground.backgroundTintList = valueOf(getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
        }
    }

    private fun showColorPicker(isBackground: Boolean) {
        val state = viewModel.state.value
        val currentColor = if (isBackground) state.backgroundColor else state.foregroundColor
        val recentColors = if (isBackground) state.recentBackgroundColors else state.recentForegroundColors
        val dialog = SeslColorPickerDialog(
            this,
            { color: Int ->
                if (isBackground) {
                    viewModel.onBackgroundColorChanged(color)
                } else {
                    viewModel.onForegroundColorChanged(color)
                }
            },
            currentColor,
            recentColors.toIntArray(),
            true,
        )
        dialog.setTransparencyControlEnabled(true)
        dialog.show()
    }

    private fun createSuggestAppBarModel(): SuggestAppBarModel<SuggestAppBarView> = SuggestAppBarModel.Builder(this).apply {
        setTitle(getString(R.string.tap_icon_to_copy_to_clipboard))
        setCloseClickListener { _, _ -> binding.root.setAppBarSuggestView(null) }
        setButtons(
            arrayListOf(
                ButtonModel(
                    text = getString(R.string.copy_icon),
                    clickListener = { _, _ -> viewModel.state.value.icon?.copyToClipboard(this@IconActivity, "icon", "icon.png") },
                ),
            ),
        )
    }.build()
}
