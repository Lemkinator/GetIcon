/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.geticon.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList.valueOf
import android.graphics.Bitmap
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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.picker3.app.SeslColorPickerDialog
import com.google.android.material.appbar.model.ButtonModel
import com.google.android.material.appbar.model.SuggestAppBarModel
import com.google.android.material.appbar.model.view.SuggestAppBarView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.collectEvents
import de.lemke.commonutils.collectState
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
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.databinding.ActivityIconBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
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
        registerForActivityResult(StartActivityForResult()) { onExportBitmapResult(it) }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val seekbarChangeListener =
        object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}

            override fun onProgressChanged(
                seekBar: SeslSeekBar,
                progress: Int,
                fromUser: Boolean,
            ) {
                if (fromUser) viewModel.onSizeChanged(progress)
            }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun onExportBitmapResult(result: ActivityResult?) {
        val icon = viewModel.state.value.icon ?: return
        saveIconToUri(result, icon)
    }

    private fun saveIconToUri(
        result: ActivityResult?,
        icon: Bitmap,
    ) {
        if (result?.resultCode == RESULT_OK) {
            saveBitmapToUri(result.data?.data, icon)
        } else if (result?.resultCode != RESULT_CANCELED) {
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
            R.id.menu_item_icon_save_as_image -> {
                exportBitmap(commonUtilsSettings.imageSaveLocation, icon, state.fileName, exportBitmapResultLauncher)
                true
            }

            R.id.menu_item_icon_share -> {
                shareBitmap(icon, "icon.png")
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun initViews() {
        setCustomBackAnimation(binding.root, showInAppReviewIfPossible = true)
        binding.icon.translateYWithAppBar(binding.root.appBarLayout, this)
        binding.icon.setOnLongClickListener { onCopyButtonClick() }
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onMaskChanged(isChecked)
        }
        binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onColorChanged(isChecked)
        }
        binding.sizeEdittext.setOnEditorActionListener { textView, _, _ ->
            textView.text
                .toString()
                .toIntOrNull()
                ?.let { viewModel.onSizeChanged(it) }
            hideSoftInput()
            true
        }
        binding.sizeSeekbar.min = MIN_ICON_SIZE
        binding.sizeSeekbar.max = MAX_ICON_SIZE
        binding.sizeSeekbar.setOnSeekBarChangeListener(seekbarChangeListener)
        binding.colorButtonBackground.setOnClickListener { showColorPicker(isBackground = true) }
        binding.colorButtonForeground.setOnClickListener { showColorPicker(isBackground = false) }
    }

    private fun collectState() {
        collectState(viewModel.state) { renderState(it) }
    }

    private fun collectEvents() {
        collectEvents(viewModel.events) { event ->
            when (event) {
                IconEvent.Finish -> {
                    toast(commonutilsR.string.commonutils_error_app_not_found)
                    finishAfterTransition()
                }

                is IconEvent.GenerateFailed -> {
                    toast(R.string.error_icon_generation_failed)
                    finishAfterTransition()
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
        if (binding.sizeEdittext.text
                .toString()
                .toIntOrNull() != state.size
        ) {
            binding.sizeEdittext.setText(state.size.toString())
        }
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun onColorPicked(
        color: Int,
        isBackground: Boolean,
    ) {
        if (isBackground) {
            viewModel.onBackgroundColorChanged(color)
        } else {
            viewModel.onForegroundColorChanged(color)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun showColorPicker(isBackground: Boolean) {
        val state = viewModel.state.value
        val currentColor = if (isBackground) state.backgroundColor else state.foregroundColor
        val recentColors = if (isBackground) state.recentBackgroundColors else state.recentForegroundColors
        val dialog =
            SeslColorPickerDialog(
                this,
                { color: Int -> onColorPicked(color, isBackground) },
                currentColor,
                recentColors.toIntArray(),
                true,
            )
        dialog.setTransparencyControlEnabled(true)
        dialog.show()
    }

    private fun onCopyButtonClick(): Boolean =
        viewModel.state.value.icon
            ?.copyToClipboard(this, "icon", "icon.png") ?: false

    private fun createSuggestAppBarModel(): SuggestAppBarModel<SuggestAppBarView> =
        SuggestAppBarModel
            .Builder(this)
            .apply {
                setTitle(getString(R.string.long_press_icon_to_copy_to_clipboard))
                setCloseClickListener { _, _ -> binding.root.setAppBarSuggestView(null) }
                setButtons(
                    arrayListOf(
                        ButtonModel(
                            text = getString(R.string.copy_icon),
                            clickListener = { _, _ -> onCopyButtonClick() },
                        ),
                    ),
                )
            }.build()
}
