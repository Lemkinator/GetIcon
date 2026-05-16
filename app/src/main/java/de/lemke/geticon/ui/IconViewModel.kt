/*
 * Copyright 2026 Leonard Lemke
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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_BACKGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_FOREGROUND_COLOR
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IconUiState(
    val icon: Bitmap? = null,
    val appName: String = "",
    val size: Int = 512,
    val maskEnabled: Boolean = true,
    val colorEnabled: Boolean = false,
    val foregroundColor: Int = 0,
    val backgroundColor: Int = 0,
    val isAdaptiveIcon: Boolean = false,
    val hasMaskedAppIcon: Boolean = false,
    val fileName: String = "",
    val recentForegroundColors: List<Int> = listOf(DEFAULT_FOREGROUND_COLOR),
    val recentBackgroundColors: List<Int> = listOf(DEFAULT_BACKGROUND_COLOR),
    val isLoading: Boolean = true,
)

sealed class IconEvent {
    data object Finish : IconEvent()
}

@HiltViewModel
class IconViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val generateIcon: GenerateIconUseCase,
) : ViewModel() {
    private val applicationInfo: ApplicationInfo? = savedStateHandle.get<ApplicationInfo>(IconActivity.KEY_APPLICATION_INFO)

    private val _state = MutableStateFlow(IconUiState())
    val state: StateFlow<IconUiState> = _state.asStateFlow()

    val events = Channel<IconEvent>(Channel.BUFFERED)

    init {
        if (applicationInfo == null) {
            viewModelScope.launch { events.send(IconEvent.Finish) }
        } else {
            viewModelScope.launch { loadInitialState() }
        }
    }

    private suspend fun loadInitialState() {
        val appInfo = applicationInfo ?: return
        val userSettings = getUserSettings()
        val fg = userSettings.recentForegroundColors.first()
        val bg = userSettings.recentBackgroundColors.first()
        val result =
            generateIcon(
                appInfo,
                userSettings.iconSize,
                userSettings.maskEnabled,
                userSettings.colorEnabled,
                fg,
                bg,
                context.packageManager,
            )
        _state.value =
            IconUiState(
                icon = result.bitmap,
                appName = appInfo.loadLabel(context.packageManager).toString(),
                size = userSettings.iconSize,
                maskEnabled = userSettings.maskEnabled,
                colorEnabled = userSettings.colorEnabled,
                foregroundColor = fg,
                backgroundColor = bg,
                isAdaptiveIcon = result.isAdaptiveIcon,
                hasMaskedAppIcon = result.hasMaskedAppIcon,
                fileName = buildFileName(appInfo.packageName, userSettings.maskEnabled, userSettings.colorEnabled),
                recentForegroundColors = userSettings.recentForegroundColors,
                recentBackgroundColors = userSettings.recentBackgroundColors,
                isLoading = false,
            )
    }

    fun onMaskChanged(enabled: Boolean) {
        viewModelScope.launch {
            updateUserSettings { it.copy(maskEnabled = enabled) }
            regenerateIcon(_state.value.copy(maskEnabled = enabled))
        }
    }

    fun onColorChanged(enabled: Boolean) {
        viewModelScope.launch {
            updateUserSettings { it.copy(colorEnabled = enabled) }
            regenerateIcon(_state.value.copy(colorEnabled = enabled))
        }
    }

    fun onSizeChanged(size: Int) {
        val clamped = size.coerceIn(16, 1024)
        viewModelScope.launch {
            updateUserSettings { it.copy(iconSize = clamped) }
            regenerateIcon(_state.value.copy(size = clamped))
        }
    }

    fun onForegroundColorChanged(color: Int) {
        viewModelScope.launch {
            val recentColors = (listOf(color) + _state.value.recentForegroundColors).distinct().take(6)
            updateUserSettings { it.copy(recentForegroundColors = recentColors) }
            regenerateIcon(_state.value.copy(foregroundColor = color, recentForegroundColors = recentColors))
        }
    }

    fun onBackgroundColorChanged(color: Int) {
        viewModelScope.launch {
            val recentColors = (listOf(color) + _state.value.recentBackgroundColors).distinct().take(6)
            updateUserSettings { it.copy(recentBackgroundColors = recentColors) }
            regenerateIcon(_state.value.copy(backgroundColor = color, recentBackgroundColors = recentColors))
        }
    }

    private suspend fun regenerateIcon(newState: IconUiState) {
        val appInfo = applicationInfo ?: return
        _state.value = newState.copy(isLoading = true)
        val result =
            generateIcon(
                appInfo,
                newState.size,
                newState.maskEnabled,
                newState.colorEnabled,
                newState.foregroundColor,
                newState.backgroundColor,
                context.packageManager,
            )
        _state.value =
            newState.copy(
                icon = result.bitmap,
                isAdaptiveIcon = result.isAdaptiveIcon,
                hasMaskedAppIcon = result.hasMaskedAppIcon,
                fileName = buildFileName(appInfo.packageName, newState.maskEnabled, newState.colorEnabled),
                isLoading = false,
            )
    }

    override fun onCleared() {
        super.onCleared()
        val path = applicationInfo?.sourceDir ?: return
        if (path.startsWith(context.cacheDir.absolutePath)) File(path).delete()
    }

    private fun buildFileName(
        packageName: String,
        maskEnabled: Boolean,
        colorEnabled: Boolean,
    ): String = "${packageName}_${if (maskEnabled) "mask" else "default"}${if (colorEnabled) "_mono" else ""}"
}
