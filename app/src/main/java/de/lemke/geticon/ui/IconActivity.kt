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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.picker3.app.SeslColorPickerDialog
import com.google.android.material.appbar.model.ButtonModel
import com.google.android.material.appbar.model.SuggestAppBarModel
import com.google.android.material.appbar.model.view.SuggestAppBarView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.utils.bindColorSwatch
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.copyToClipboard
import de.lemke.commonutils.ui.utils.exportBitmap
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.setWindowTransparent
import de.lemke.commonutils.ui.utils.shareBitmap
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.commonutils.ui.utils.toast
import de.lemke.geticon.R
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.databinding.ActivityIconBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.ktx.onProgressChanged
import java.util.Locale
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class IconActivity :
    AppCompatActivity(),
    ViewYTranslator by AppBarAwareYTranslator() {
    @Inject
    lateinit var settings: SettingsRepository

    private lateinit var binding: ActivityIconBinding
    private val viewModel: IconViewModel by viewModels()
    private var isRendering = false
    private var suggestViewSet = false

    private val exportBitmapResultLauncher: ActivityResultLauncher<Intent> =
        registerForSingleLaunchResult(StartActivityForResult()) { onExportBitmapResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityIconBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setWindowTransparent(true)
        initViews()
        collectState(viewModel.state) { renderState(it) }
        collectState(viewModel.export) { renderExportControls(it) }
        collectState(viewModel.export, minActiveState = RESUMED) { if (it is IconExport.Result) onExportResult(it) }
        collectState(viewModel.exit) { if (it is IconExit.Reason) onExit(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = menuInflater.inflate(R.menu.menu_icon, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val enabled = viewModel.export.value != IconExport.Running
        menu.findItem(R.id.menu_item_icon_save_as_image).isEnabled = enabled
        menu.findItem(R.id.menu_item_icon_share).isEnabled = enabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (viewModel.state.value.icon == null) return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.menu_item_icon_save_as_image -> singleLaunchMenuItem { viewModel.onSave() }
            R.id.menu_item_icon_share -> singleLaunchMenuItem { viewModel.onShare() }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    internal fun onExportBitmapResult(result: ActivityResult) {
        viewModel.onDocumentPicked(result.toDocumentPick())
    }

    private fun initViews() {
        setCustomBackAnimation(binding.root, inAppReview = settings)
        binding.icon.translateYWithAppBar(binding.root.appBarLayout, this)
        binding.icon.setOnLongClickListener { copyIcon() }
        binding.maskedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onMaskChanged(isChecked)
        }
        binding.colorCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (!isRendering) viewModel.onColorChanged(isChecked)
        }
        binding.sizeEdittext.setOnEditorActionListener { _, _, _ ->
            onSizeSubmitted()
            hideSoftInput()
            true
        }
        binding.sizeSeekbar.min = MIN_ICON_SIZE
        binding.sizeSeekbar.max = MAX_ICON_SIZE
        binding.sizeSeekbar.onProgressChanged { onSeekbarProgressChanged(it) }
        binding.colorButtonBackground.onSingleLaunchClick { showColorPicker(isBackground = true) }
        binding.colorButtonForeground.onSingleLaunchClick { showColorPicker(isBackground = false) }
    }

    private fun onExportResult(result: IconExport.Result) {
        val handled =
            when (result) {
                is IconExport.OpenPicker -> {
                    true.also { exportBitmap(result.fileName, exportBitmapResultLauncher) }
                }

                is IconExport.Share -> {
                    shareBitmap(result.file)
                }

                is IconExport.SaveFinished -> {
                    true.also { toast(result.result) }
                }

                is IconExport.Copy -> {
                    true.also { copyToClipboard(result.clip) }
                }

                IconExport.CopyFailed, IconExport.ShareFailed -> {
                    true.also {
                        toast(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)
                    }
                }
            }
        if (handled) viewModel.onExportHandled(result)
    }

    private fun onExit(reason: IconExit.Reason) {
        val message =
            when (reason) {
                IconExit.AppNotFound -> commonutilsR.string.commonutils_error_app_not_found
                IconExit.GenerateFailed -> R.string.error_icon_generation_failed
            }
        toast(message)
        finishAfterTransition()
        viewModel.onExitHandled(reason)
    }

    private fun renderState(state: IconUiState) {
        isRendering = true
        if (state.appName.isNotEmpty()) {
            binding.root.setTitle(state.appName)
            binding.icon.contentDescription = getString(R.string.app_icon_of, state.appName)
        }
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
            binding.sizeEdittext.setText("%d".format(Locale.getDefault(), state.size))
        }
        isRendering = false
        val colorButtonsEnabled = state.isAdaptiveIcon && state.colorEnabled
        binding.colorButtonBackground.bindColorSwatch(state.backgroundColor, colorButtonsEnabled)
        binding.colorButtonForeground.bindColorSwatch(state.foregroundColor, colorButtonsEnabled)
        if (!suggestViewSet && state.icon != null) {
            binding.root.setAppBarSuggestView(createSuggestAppBarModel())
            suggestViewSet = true
        }
    }

    private fun renderExportControls(export: IconExport) {
        binding.icon.isLongClickable = export != IconExport.Running
        invalidateOptionsMenu()
    }

    private fun onSizeSubmitted() {
        val field = binding.sizeEdittext
        val size =
            field.text
                .toString()
                .toIntOrNull()
                ?.coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE) ?: return
        val formatted = "%d".format(Locale.getDefault(), size)
        if (field.text.toString() != formatted) {
            field.setText(formatted)
            field.setSelection(formatted.length)
        }
        viewModel.onSizeChanged(size)
    }

    @VisibleForTesting(otherwise = PRIVATE)
    internal fun onSeekbarProgressChanged(progress: Int) {
        viewModel.onSizeChanged(progress)
    }

    @VisibleForTesting(otherwise = PRIVATE)
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

    @VisibleForTesting(otherwise = PRIVATE)
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
        dialog.showOnce(if (isBackground) BACKGROUND_COLOR_PICKER_TAG else FOREGROUND_COLOR_PICKER_TAG)
    }

    private fun copyIcon(): Boolean {
        if (viewModel.state.value.icon == null) return false
        singleLaunch { viewModel.onCopy() }
        return true
    }

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
                            clickListener = { _, _ -> copyIcon() },
                        ),
                    ),
                )
            }.build()

    companion object {
        const val KEY_APPLICATION_INFO = "applicationInfo"
        private const val BACKGROUND_COLOR_PICKER_TAG = "backgroundColorPicker"
        private const val FOREGROUND_COLOR_PICKER_TAG = "foregroundColorPicker"
    }
}

private fun ActivityResult.toDocumentPick(): DocumentPick {
    val uri = data?.data
    return when {
        resultCode != Activity.RESULT_OK -> DocumentPick.Canceled
        uri == null -> DocumentPick.MissingUri
        else -> DocumentPick.Created(uri)
    }
}
