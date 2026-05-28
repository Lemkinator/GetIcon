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
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.graphics.toColor
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.picker3.app.SeslColorPickerDialog
import com.google.android.material.appbar.model.ButtonModel
import com.google.android.material.appbar.model.SuggestAppBarModel
import com.google.android.material.appbar.model.view.SuggestAppBarView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.DrawerHost
import de.lemke.commonutils.autoCleared
import de.lemke.commonutils.clearLastNestedScrollingChild
import de.lemke.commonutils.collectEvents
import de.lemke.commonutils.collectState
import de.lemke.commonutils.copyToClipboard
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.exportBitmap
import de.lemke.commonutils.saveBitmapToUri
import de.lemke.commonutils.shareBitmap
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.databinding.FragmentIconBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import java.io.IOException
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class IconFragment :
    Fragment(R.layout.fragment_icon),
    ViewYTranslator by AppBarAwareYTranslator() {
    private val binding by autoCleared { FragmentIconBinding.bind(requireView()) }
    private val drawerLayout get() = (requireActivity() as DrawerHost).drawerLayout
    private val viewModel: IconViewModel by viewModels()
    private var isRendering = false
    private var suggestViewSet = false

    private val menuProvider =
        object : MenuProvider {
            override fun onCreateMenu(
                menu: Menu,
                menuInflater: MenuInflater,
            ) = menuInflater.inflate(R.menu.menu_icon, menu)

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val state = viewModel.state.value
                val icon = state.icon ?: return false
                return when (menuItem.itemId) {
                    R.id.menu_item_icon_save_as_image -> {
                        exportBitmap(commonUtilsSettings.imageSaveLocation, icon, state.fileName, exportBitmapResultLauncher)
                        true
                    }

                    R.id.menu_item_icon_share -> {
                        shareBitmap(icon, "icon.png")
                        true
                    }

                    else -> false
                }
            }
        }

    private val exportBitmapResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult? ->
            val icon = viewModel.state.value.icon ?: return@registerForActivityResult
            try {
                if (result?.resultCode == android.app.Activity.RESULT_OK) {
                    requireContext().saveBitmapToUri(result.data?.data, icon)
                } else {
                    toast(commonutilsR.string.commonutils_error_saving_image)
                }
            } catch (e: IOException) {
                Log.e("IconFragment", "Failed to save bitmap to URI", e)
                toast(commonutilsR.string.commonutils_error_saving_image)
            } catch (e: SecurityException) {
                Log.e("IconFragment", "Failed to save bitmap to URI", e)
                toast(commonutilsR.string.commonutils_error_saving_image)
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        collectState(viewModel.state) { renderState(it) }
        collectEvents(viewModel.events) { event ->
            when (event) {
                IconEvent.Finish -> {
                    toast(commonutilsR.string.commonutils_error_app_not_found)
                    findNavController().navigateUp()
                }
            }
        }
    }

    private fun initViews() {
        binding.icon.translateYWithAppBar(drawerLayout.appBarLayout, viewLifecycleOwner)
        binding.icon.setOnLongClickListener {
            viewModel.state.value.icon?.let {
                it.copyToClipboard(requireContext(), "icon", "icon.png")
                true
            } ?: false
        }
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
            requireActivity().hideSoftInput()
            true
        }
        binding.sizeSeekbar.min = 16
        binding.sizeSeekbar.max = 1024
        binding.sizeSeekbar.setOnSeekBarChangeListener(
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
            },
        )
        binding.colorButtonBackground.setOnClickListener { showColorPicker(isBackground = true) }
        binding.colorButtonForeground.setOnClickListener { showColorPicker(isBackground = false) }
    }

    @SuppressLint("SetTextI18n")
    private fun renderState(state: IconUiState) {
        isRendering = true
        if (state.appName.isNotEmpty()) drawerLayout.setTitle(state.appName)
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
            drawerLayout.setAppBarSuggestView(createSuggestAppBarModel())
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
            binding.colorButtonBackground.setTextColor(requireContext().getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonBackground.backgroundTintList =
                valueOf(requireContext().getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
            binding.colorButtonForeground.isEnabled = false
            binding.colorButtonForeground.setTextColor(requireContext().getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            binding.colorButtonForeground.backgroundTintList =
                valueOf(requireContext().getColor(appcompatR.color.sesl_show_button_shapes_color_disabled))
        }
    }

    private fun showColorPicker(isBackground: Boolean) {
        val state = viewModel.state.value
        val currentColor = if (isBackground) state.backgroundColor else state.foregroundColor
        val recentColors = if (isBackground) state.recentBackgroundColors else state.recentForegroundColors
        SeslColorPickerDialog(
            requireContext(),
            { color: Int ->
                if (isBackground) viewModel.onBackgroundColorChanged(color) else viewModel.onForegroundColorChanged(color)
            },
            currentColor,
            recentColors.toIntArray(),
            true,
        ).apply {
            setTransparencyControlEnabled(true)
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().addMenuProvider(menuProvider)
        drawerLayout.showNavigationButtonAsBack = true
    }

    override fun onPause() {
        super.onPause()
        requireActivity().removeMenuProvider(menuProvider)
    }

    override fun onDestroyView() {
        // Remove once sesl-androidx CoordinatorLayout uses WeakReference<View> for
        // mLastNestedScrollingChild (fix tracked in sesl-androidx fix/memory-leaks).
        clearLastNestedScrollingChild()
        super.onDestroyView()
    }

    override fun onStop() {
        super.onStop()
        drawerLayout.setAppBarSuggestView(null)
        suggestViewSet = false
    }

    private fun createSuggestAppBarModel(): SuggestAppBarModel<SuggestAppBarView> {
        // Remove WeakReference workaround once the Material/Samsung StackViewGroup
        // hideAnimator no longer holds a strong mTarget ref after animation end, preventing
        // GC of SuggestAppBarView → SuggestAppBarModel → listeners.
        val layout = drawerLayout
        val weakVm = java.lang.ref.WeakReference(viewModel)
        val appCtx = requireContext().applicationContext
        return SuggestAppBarModel
            .Builder(requireContext())
            .apply {
                setTitle(getString(R.string.long_press_icon_to_copy_to_clipboard))
                setCloseClickListener { _, _ -> layout.setAppBarSuggestView(null) }
                setButtons(
                    arrayListOf(
                        ButtonModel(
                            text = getString(R.string.copy_icon),
                            clickListener = { _, _ ->
                                weakVm
                                    .get()
                                    ?.state
                                    ?.value
                                    ?.icon
                                    ?.copyToClipboard(appCtx, "icon", "icon.png")
                            },
                        ),
                    ),
                )
            }.build()
    }
}
