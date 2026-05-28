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

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.picker.helper.SeslAppInfoDataHelper
import androidx.picker.model.AppData.GridAppDataBuilder
import androidx.picker.widget.SeslAppPickerView.Companion.ORDER_ASCENDING
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.DrawerHost
import de.lemke.commonutils.autoCleared
import de.lemke.commonutils.clearLastNestedScrollingChild
import de.lemke.commonutils.collectEvents
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.restoreSearchAndActionMode
import de.lemke.commonutils.saveSearchAndActionMode
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.databinding.FragmentMainBinding
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.hideSoftInput
import dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeOnBackBehavior.DISMISS
import dev.oneuiproject.oneui.layout.startSearchMode
import dev.oneuiproject.oneui.recyclerview.ktx.configureImmBottomPadding
import dev.oneuiproject.oneui.recyclerview.ktx.hideSoftInputOnScroll
import de.lemke.commonutils.R as commonutilsR

@AndroidEntryPoint
class MainFragment :
    Fragment(R.layout.fragment_main),
    ViewYTranslator by AppBarAwareYTranslator() {
    private val binding by autoCleared { FragmentMainBinding.bind(requireView()) }
    private val viewModel: MainViewModel by activityViewModels()
    private val drawerLayout get() = (requireActivity() as DrawerHost).drawerLayout

    private val menuProvider =
        object : MenuProvider {
            override fun onCreateMenu(
                menu: Menu,
                menuInflater: MenuInflater,
            ) = menuInflater.inflate(R.menu.menu_main, menu)

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.menu_item_search) return false
                startSearch()
                return true
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initAppPicker()
        collectEvents(viewModel.events) { event ->
            when (event) {
                is MainEvent.NavigateToIcon -> {
                    drawerLayout.endSearchMode()
                    findNavController().navigate(
                        R.id.action_main_to_icon,
                        bundleOf(IconViewModel.KEY_APPLICATION_INFO to event.applicationInfo),
                    )
                }

                MainEvent.ShowError -> {
                    toast(commonutilsR.string.commonutils_error_no_valid_file_selected)
                }
            }
        }
        binding.noEntryView.translateYWithAppBar(drawerLayout.appBarLayout, viewLifecycleOwner)
        savedInstanceState?.restoreSearchAndActionMode(onSearchMode = { startSearch() })
    }

    override fun onResume() {
        super.onResume()
        requireActivity().addMenuProvider(menuProvider)
        drawerLayout.showNavigationButtonAsBack = false
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.saveSearchAndActionMode(isSearchMode = drawerLayout.isSearchMode)
    }

    private fun applyFilter(query: String = "") {
        binding.appPicker.setSearchFilter(query) { binding.noEntryView.updateVisibility(it <= 0, binding.appPicker) }
    }

    private fun startSearch() =
        drawerLayout.startSearchMode(
            onStart = {
                it.queryHint = getString(commonutilsR.string.commonutils_search_apps)
                it.setQuery(commonUtilsSettings.search, false)
            },
            onQuery = { query, _ ->
                applyFilter(query)
                commonUtilsSettings.search = query
                true
            },
            onEnd = { applyFilter() },
            onBackBehavior = DISMISS,
        )

    private fun initAppPicker() =
        binding.appPicker.apply {
            appListOrder = ORDER_ASCENDING
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                seslSetIndexTipEnabled(true, bars.top)
                insets
            }
            hideSoftInputOnScroll()
            if (SDK_INT >= VERSION_CODES.R) configureImmBottomPadding(drawerLayout)
            val appInfoDataHelper = SeslAppInfoDataHelper(requireContext(), GridAppDataBuilder::class.java)
            val appInfoDataList = appInfoDataHelper.getPackages().onEach { it.subLabel = it.packageName }
            submitList(appInfoDataList)
            setOnItemClickEventListener { _, appInfo ->
                try {
                    requireActivity().hideSoftInput()
                    drawerLayout.endSearchMode()
                    val args =
                        bundleOf(
                            IconViewModel.KEY_APPLICATION_INFO to
                                requireContext()
                                    .packageManager
                                    .getApplicationInfo(appInfo.packageName, 0),
                        )
                    findNavController().navigate(R.id.action_main_to_icon, args)
                    true
                } catch (e: NameNotFoundException) {
                    Log.e("MainFragment", "App not found: ${appInfo.packageName}", e)
                    toast(commonutilsR.string.commonutils_error_app_not_found)
                    false
                }
            }
        }
}
