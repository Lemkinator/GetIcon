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

import android.content.ClipData
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.geticon.data.ApkImporter
import de.lemke.geticon.data.IconExporter
import de.lemke.geticon.data.IconRenderer
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_BACKGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_FOREGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_RECENT_COLORS
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IconUiState(
    val icon: Bitmap? = null,
    val appName: String = "",
    val style: IconStyle =
        IconStyle(
            size = DEFAULT_ICON_SIZE,
            maskEnabled = true,
            colorEnabled = false,
            foregroundColor = DEFAULT_FOREGROUND_COLOR,
            backgroundColor = DEFAULT_BACKGROUND_COLOR,
        ),
    val kind: IconKind = IconKind.LEGACY,
    val fileName: String = "",
    val recentForegroundColors: List<Int> = listOf(DEFAULT_FOREGROUND_COLOR),
    val recentBackgroundColors: List<Int> = listOf(DEFAULT_BACKGROUND_COLOR),
    val isLoading: Boolean = true,
)

/** A save, copy or share of the icon. The activity acts on a [Result] and then reports it handled. */
sealed interface IconExport {
    sealed interface Result : IconExport

    data object Idle : IconExport

    data object Running : IconExport

    data class OpenPicker(val fileName: String) : Result

    data class SaveFinished(val result: BitmapSaveResult.Finished) : Result

    data class Copy(val clip: ClipData) : Result

    data object CopyFailed : Result

    data class Share(val file: BitmapShareFile.Written) : Result

    data object ShareFailed : Result
}

sealed interface DocumentPick {
    data class Created(val uri: Uri) : DocumentPick

    data object MissingUri : DocumentPick

    data object Canceled : DocumentPick
}

/** Whether the screen must close. The activity shows a [Reason], finishes and then reports it handled. */
sealed interface IconExit {
    sealed interface Reason : IconExit

    data object None : IconExit

    data object AppNotFound : Reason

    data object GenerateFailed : Reason
}

@HiltViewModel
class IconViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSettings: UserSettings,
    private val generateIcon: GenerateIconUseCase,
    private val renderer: IconRenderer,
    private val exporter: IconExporter,
    private val apkImporter: ApkImporter,
) : ViewModel() {
    private val applicationInfo: ApplicationInfo? = savedStateHandle.get<ApplicationInfo>(IconActivity.KEY_APPLICATION_INFO)

    val state: StateFlow<IconUiState>
        field = MutableStateFlow(IconUiState())

    val export: StateFlow<IconExport>
        field = MutableStateFlow<IconExport>(IconExport.Idle)

    val exit: StateFlow<IconExit>
        field = MutableStateFlow<IconExit>(IconExit.None)

    init {
        val appInfo = applicationInfo
        if (appInfo == null) {
            exitWith(IconExit.AppNotFound)
        } else {
            viewModelScope.launch { loadInitialState(appInfo) }
        }
    }

    override fun onCleared() {
        applicationInfo?.sourceDir?.let { apkImporter.discard(File(it)) }
    }

    private suspend fun loadInitialState(appInfo: ApplicationInfo) {
        val sourceFile = appInfo.sourceDir?.let { File(it) }
        if (sourceFile != null && apkImporter.isCached(sourceFile) && !sourceFile.exists()) {
            exitWith(IconExit.AppNotFound)
            return
        }
        runCatching {
            val recentForegroundColors = userSettings.recentForegroundColors
            val recentBackgroundColors = userSettings.recentBackgroundColors
            val style =
                IconStyle(
                    size = userSettings.iconSize,
                    maskEnabled = userSettings.maskEnabled,
                    colorEnabled = userSettings.colorEnabled,
                    foregroundColor = recentForegroundColors.first(),
                    backgroundColor = recentBackgroundColors.first(),
                )
            val appName = renderer.label(appInfo)
            val icon = generateIcon(appInfo, style)
            state.value =
                IconUiState(
                    icon = icon.bitmap,
                    appName = appName,
                    style = style,
                    kind = icon.kind,
                    fileName = icon.fileName,
                    recentForegroundColors = recentForegroundColors,
                    recentBackgroundColors = recentBackgroundColors,
                    isLoading = false,
                )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            exitWith(IconExit.GenerateFailed)
        }
    }

    fun onMaskChanged(enabled: Boolean) {
        userSettings.maskEnabled = enabled
        regenerateIcon(state.value.withStyle { copy(maskEnabled = enabled) })
    }

    fun onColorChanged(enabled: Boolean) {
        userSettings.colorEnabled = enabled
        regenerateIcon(state.value.withStyle { copy(colorEnabled = enabled) })
    }

    fun onSizeChanged(size: Int) {
        val clamped = size.coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE)
        if (clamped == state.value.style.size) return
        userSettings.iconSize = clamped
        regenerateIcon(state.value.withStyle { copy(size = clamped) })
    }

    fun onForegroundColorChanged(color: Int) {
        val recentColors = (listOf(color) + state.value.recentForegroundColors).distinct().take(MAX_RECENT_COLORS)
        userSettings.recentForegroundColors = recentColors
        regenerateIcon(state.value.withStyle { copy(foregroundColor = color) }.copy(recentForegroundColors = recentColors))
    }

    fun onBackgroundColorChanged(color: Int) {
        val recentColors = (listOf(color) + state.value.recentBackgroundColors).distinct().take(MAX_RECENT_COLORS)
        userSettings.recentBackgroundColors = recentColors
        regenerateIcon(state.value.withStyle { copy(backgroundColor = color) }.copy(recentBackgroundColors = recentColors))
    }

    fun onSave() {
        val current = state.value
        val icon = current.icon ?: return
        startExport {
            when (val result = exporter.saveToDirectory(userSettings.imageSaveLocation, icon, current.fileName)) {
                is BitmapSaveResult.Finished -> IconExport.SaveFinished(result)
                BitmapSaveResult.NeedsPicker -> IconExport.OpenPicker(current.fileName)
            }
        }
    }

    fun onDocumentPicked(pick: DocumentPick) {
        when (pick) {
            DocumentPick.Canceled -> Unit
            DocumentPick.MissingUri -> export.value = IconExport.SaveFinished(BitmapSaveResult.WriteFailed)
            is DocumentPick.Created -> launchExport { exporter.saveToCreatedDocument(pick.uri, state.value.icon).toExport() }
        }
    }

    fun onCopy() {
        val icon = state.value.icon ?: return
        startExport { exporter.createClip(icon)?.let(IconExport::Copy) ?: IconExport.CopyFailed }
    }

    fun onShare() {
        val icon = state.value.icon ?: return
        startExport {
            when (val file = exporter.createShareFile(icon)) {
                is BitmapShareFile.Written -> IconExport.Share(file)
                BitmapShareFile.Failed -> IconExport.ShareFailed
                BitmapShareFile.Dropped -> null
            }
        }
    }

    fun onExportHandled(result: IconExport.Result) {
        export.update { if (it == result) IconExport.Idle else it }
    }

    fun onExitHandled(reason: IconExit.Reason) {
        exit.update { if (it == reason) IconExit.None else it }
    }

    private fun exitWith(reason: IconExit.Reason) {
        exit.update { if (it == IconExit.None) reason else it }
    }

    private fun startExport(work: suspend () -> IconExport.Result?) {
        if (export.value == IconExport.Running) return
        launchExport(work)
    }

    private fun launchExport(work: suspend () -> IconExport.Result?) {
        export.value = IconExport.Running
        viewModelScope.launch { export.value = work() ?: IconExport.Idle }
    }

    // generateIcon runs synchronously on Main (~5 ms). Dispatching to Default caused slider jank:
    // cancellation was ineffective mid-withContext, producing concurrent bitmap allocations (557e6db).
    private fun regenerateIcon(newState: IconUiState) {
        val appInfo = applicationInfo ?: return
        runCatching {
            val icon = generateIcon(appInfo, newState.style)
            state.value = newState.copy(icon = icon.bitmap, kind = icon.kind, fileName = icon.fileName, isLoading = false)
        }.onFailure {
            exitWith(IconExit.GenerateFailed)
        }
    }

    private fun BitmapSaveResult.UriResult.toExport(): IconExport.Result? =
        when (this) {
            is BitmapSaveResult.Finished -> IconExport.SaveFinished(this)
            BitmapSaveResult.Canceled -> null
        }
}

private fun IconUiState.withStyle(transform: IconStyle.() -> IconStyle): IconUiState = copy(style = style.transform())
