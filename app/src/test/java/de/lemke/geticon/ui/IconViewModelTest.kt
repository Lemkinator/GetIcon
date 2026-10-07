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
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.geticon.data.FakeApkImporter
import de.lemke.geticon.data.FakeIconExporter
import de.lemke.geticon.data.FakeIconExporter.Call
import de.lemke.geticon.data.FakeIconRenderer
import de.lemke.geticon.data.FakeIconRenderer.Render
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_RECENT_COLORS
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

private fun IconViewModel.triggerOnCleared() {
    ViewModelStore().also { it.put("vm", this) }.clear()
}

class IconViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var exporter: FakeIconExporter
        lateinit var renderer: FakeIconRenderer
        lateinit var apkImporter: FakeApkImporter

        val defaultForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR)
        val defaultBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR)
        val icon = mockk<Bitmap>()
        val defaultStyle =
            IconStyle(
                size = 512,
                maskEnabled = true,
                colorEnabled = false,
                foregroundColor = -1,
                backgroundColor = 0xFF0381FE.toInt(),
            )

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences())
            exporter = FakeIconExporter()
            renderer = FakeIconRenderer(icon)
            apkImporter = FakeApkImporter()
        }

        afterEach { apkImporter.cacheDir.deleteRecursively() }

        fun appInfoAt(sourceFile: File) =
            ApplicationInfo().also {
                it.packageName = "com.example.test"
                it.sourceDir = sourceFile.absolutePath
            }

        fun buildViewModel(
            appInfo: ApplicationInfo? = null,
            settings: UserSettings = userSettings,
        ): IconViewModel {
            val handle =
                if (appInfo != null) {
                    SavedStateHandle(mapOf(IconActivity.KEY_APPLICATION_INFO to appInfo))
                } else {
                    SavedStateHandle()
                }
            return IconViewModel(handle, settings, GenerateIconUseCase(renderer), renderer, exporter, apkImporter)
        }

        context("null applicationInfo") {
            should("exit with AppNotFound immediately") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.exit.value shouldBe IconExit.AppNotFound
            }

            should("onExitHandled returns the exit to None") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.exit.test {
                    awaitItem() shouldBe IconExit.AppNotFound
                    viewModel.onExitHandled(IconExit.AppNotFound)
                    awaitItem() shouldBe IconExit.None
                }
            }

            should("onExitHandled keeps an exit other than the handled one") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.onExitHandled(IconExit.GenerateFailed)
                viewModel.exit.value shouldBe IconExit.AppNotFound
            }

            should("not read from userSettings") {
                val spyPreferences = spyk(FakeSharedPreferences())
                val viewModel = buildViewModel(appInfo = null, settings = UserSettings(spyPreferences))
                viewModel.state.value shouldBe IconUiState()
                verify(exactly = 0) { spyPreferences.getInt(any(), any()) }
                verify(exactly = 0) { spyPreferences.getBoolean(any(), any()) }
                verify(exactly = 0) { spyPreferences.getString(any(), any()) }
            }

            should("onMaskChanged renders nothing when applicationInfo is null") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.onMaskChanged(false)
                renderer.renders shouldBe emptyList()
            }

            should("onCleared does nothing when applicationInfo is null") {
                buildViewModel(appInfo = null).triggerOnCleared()
            }

            should("onSave, onCopy and onShare export nothing without an icon") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.onSave()
                viewModel.onCopy()
                viewModel.onShare()
                exporter.calls shouldBe emptyList()
                viewModel.export.value shouldBe IconExport.Idle
            }
        }

        context("valid applicationInfo") {
            val appInfo = ApplicationInfo().also { it.packageName = "com.example.test" }

            should("load the initial style from userSettings and render the app in it") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.style shouldBe defaultStyle
                renderer.renders shouldBe listOf(Render(appInfo, defaultStyle))
            }

            should("hold the kind and bitmap of the rendered icon after init") {
                renderer.kind = IconKind.MASKABLE
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.kind shouldBe IconKind.MASKABLE
                viewModel.state.value.icon shouldBe icon
            }

            should("hold the label of the app as the app name") {
                renderer.label = "Example"
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.appName shouldBe "Example"
            }

            should("load the label once and keep it across a re-render") {
                renderer.label = "Example"
                val viewModel = buildViewModel(appInfo)
                renderer.label = "Renamed"
                viewModel.onMaskChanged(false)
                renderer.labeled shouldBe listOf(appInfo)
                viewModel.state.value.appName shouldBe "Example"
            }

            should("set recentForegroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentForegroundColors shouldBe defaultForegroundColors
            }

            should("set recentBackgroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentBackgroundColors shouldBe defaultBackgroundColors
            }

            should("onMaskChanged renders the icon unmasked") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                viewModel.state.value.style shouldBe defaultStyle.copy(maskEnabled = false)
                renderer.renders.last() shouldBe Render(appInfo, defaultStyle.copy(maskEnabled = false))
            }

            should("onColorChanged renders the icon tinted") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onColorChanged(true)
                viewModel.state.value.style shouldBe defaultStyle.copy(colorEnabled = true)
                renderer.renders.last() shouldBe Render(appInfo, defaultStyle.copy(colorEnabled = true))
            }

            should("onSizeChanged renders the icon at the new size") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(300)
                viewModel.state.value.style.size shouldBe 300
                renderer.renders.last() shouldBe Render(appInfo, defaultStyle.copy(size = 300))
            }

            should("onSizeChanged renders nothing for the current size") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(512)
                renderer.renders shouldBe listOf(Render(appInfo, defaultStyle))
            }

            should("onSizeChanged clamps value below MIN_ICON_SIZE to MIN_ICON_SIZE") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MIN_ICON_SIZE - 1)
                viewModel.state.value.style.size shouldBe MIN_ICON_SIZE
            }

            should("onSizeChanged clamps value above MAX_ICON_SIZE to MAX_ICON_SIZE") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MAX_ICON_SIZE + 1)
                viewModel.state.value.style.size shouldBe MAX_ICON_SIZE
            }

            should("onSizeChanged accepts MIN_ICON_SIZE and MAX_ICON_SIZE as boundary values") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MIN_ICON_SIZE)
                viewModel.state.value.style.size shouldBe MIN_ICON_SIZE
                viewModel.onSizeChanged(MAX_ICON_SIZE)
                viewModel.state.value.style.size shouldBe MAX_ICON_SIZE
            }

            should("onForegroundColorChanged prepends color to recent list") {
                val viewModel = buildViewModel(appInfo)
                val newColor = 0xFFFF0000.toInt()
                viewModel.onForegroundColorChanged(newColor)
                viewModel.state.value.recentForegroundColors shouldBe listOf(newColor, -1)
                renderer.renders.last() shouldBe Render(appInfo, defaultStyle.copy(foregroundColor = newColor))
            }

            should("onForegroundColorChanged deduplicates recent colors") {
                val existingColor = defaultForegroundColors.first()
                val viewModel = buildViewModel(appInfo)
                viewModel.onForegroundColorChanged(existingColor)
                val colors = viewModel.state.value.recentForegroundColors
                withClue("duplicate color should not appear twice") {
                    colors.count { it == existingColor } shouldBe 1
                }
            }

            should("onBackgroundColorChanged prepends color to recent list") {
                val viewModel = buildViewModel(appInfo)
                val newColor = 0xFF00FF00.toInt()
                viewModel.onBackgroundColorChanged(newColor)
                viewModel.state.value.recentBackgroundColors shouldBe listOf(newColor, 0xFF0381FE.toInt())
                renderer.renders.last() shouldBe Render(appInfo, defaultStyle.copy(backgroundColor = newColor))
            }

            should("onMaskChanged writes maskEnabled to userSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                userSettings.maskEnabled shouldBe false
            }

            should("onColorChanged writes colorEnabled to userSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onColorChanged(true)
                userSettings.colorEnabled shouldBe true
            }

            should("onSizeChanged writes iconSize to userSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(256)
                userSettings.iconSize shouldBe 256
            }

            should("onCleared does nothing when sourceDir is null") {
                val infoWithNullSourceDir = ApplicationInfo().also { it.packageName = "com.example.test" }
                buildViewModel(appInfo = infoWithNullSourceDir).triggerOnCleared()
            }

            should("onCleared keeps an installed APK outside the cache") {
                val installedApk = File.createTempFile("installed", ".apk")
                try {
                    buildViewModel(appInfoAt(installedApk)).triggerOnCleared()
                    installedApk.exists() shouldBe true
                } finally {
                    installedApk.delete()
                }
            }

            should("onCleared deletes the cached APK") {
                val cachedApk = apkImporter.createCacheFile()
                buildViewModel(appInfoAt(cachedApk)).triggerOnCleared()
                cachedApk.exists() shouldBe false
            }

            should("isLoading is false after initial load completes") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.isLoading shouldBe false
            }

            should("isLoading is false after regenerateIcon completes") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                viewModel.state.value.isLoading shouldBe false
            }

            should("isLoading is false when regenerateIcon throws OutOfMemoryError") {
                val viewModel = buildViewModel(appInfo)
                renderer.failure = OutOfMemoryError("oom")
                viewModel.onMaskChanged(false)
                viewModel.state.value.isLoading shouldBe false
            }

            should("exit with GenerateFailed when rendering throws IOException in loadInitialState") {
                renderer.failure = IOException("io error")
                val viewModel = buildViewModel(appInfo)
                viewModel.exit.value shouldBe IconExit.GenerateFailed
            }

            should("exit with GenerateFailed when rendering throws OutOfMemoryError in loadInitialState") {
                renderer.failure = OutOfMemoryError("oom")
                val viewModel = buildViewModel(appInfo)
                viewModel.exit.value shouldBe IconExit.GenerateFailed
            }

            should("exit with GenerateFailed when rendering throws RuntimeException in loadInitialState") {
                renderer.failure = RuntimeException("crash")
                val viewModel = buildViewModel(appInfo)
                viewModel.exit.value shouldBe IconExit.GenerateFailed
            }

            should("exit with GenerateFailed when rendering throws OutOfMemoryError in regenerateIcon") {
                val viewModel = buildViewModel(appInfo)
                renderer.failure = OutOfMemoryError("oom")
                viewModel.exit.test {
                    awaitItem() shouldBe IconExit.None
                    viewModel.onMaskChanged(false)
                    awaitItem() shouldBe IconExit.GenerateFailed
                }
            }

            should("exit with GenerateFailed when rendering throws RuntimeException in regenerateIcon") {
                val viewModel = buildViewModel(appInfo)
                renderer.failure = RuntimeException("crash")
                viewModel.exit.test {
                    awaitItem() shouldBe IconExit.None
                    viewModel.onMaskChanged(false)
                    awaitItem() shouldBe IconExit.GenerateFailed
                }
            }

            should("exit with AppNotFound when temp APK file was deleted before loadInitialState (process death)") {
                val viewModel = buildViewModel(appInfoAt(File(apkImporter.cacheDir, "deleted.apk")))
                viewModel.exit.value shouldBe IconExit.AppNotFound
            }

            should("render an app whose missing APK lies outside the cache") {
                val viewModel = buildViewModel(appInfoAt(File(apkImporter.cacheDir.parentFile, "missing_${System.nanoTime()}.apk")))
                viewModel.state.value.isLoading shouldBe false
                viewModel.exit.value shouldBe IconExit.None
            }

            should("not exit when rendering throws CancellationException in loadInitialState") {
                renderer.failure = CancellationException("cancelled")
                val viewModel = buildViewModel(appInfo)
                viewModel.exit.value shouldBe IconExit.None
            }

            should("a failure while an exit waits for the activity keeps the waiting exit") {
                renderer.failure = RuntimeException("crash")
                val viewModel = buildViewModel(appInfoAt(File(apkImporter.cacheDir, "deleted.apk")))
                viewModel.onMaskChanged(false)
                viewModel.exit.value shouldBe IconExit.AppNotFound
            }

            should("a failure after the handled exit exits again") {
                renderer.failure = RuntimeException("crash")
                val viewModel = buildViewModel(appInfo)
                viewModel.onExitHandled(IconExit.GenerateFailed)
                viewModel.onMaskChanged(false)
                viewModel.exit.value shouldBe IconExit.GenerateFailed
            }

            should("a successful load leaves the exit at None") {
                val viewModel = buildViewModel(appInfo)
                viewModel.exit.value shouldBe IconExit.None
            }

            should("state holds the file name of the generated icon") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.fileName shouldBe "com.example.test_mask"
            }

            should("onForegroundColorChanged caps recent colors to MAX_RECENT_COLORS") {
                val viewModel = buildViewModel(appInfo)
                repeat(MAX_RECENT_COLORS + 1) { i -> viewModel.onForegroundColorChanged(0xFF000000.toInt() + i + 1) }
                viewModel.state.value.recentForegroundColors.size shouldBe MAX_RECENT_COLORS
            }

            should("onBackgroundColorChanged caps recent colors to MAX_RECENT_COLORS") {
                val viewModel = buildViewModel(appInfo)
                repeat(MAX_RECENT_COLORS + 1) { i -> viewModel.onBackgroundColorChanged(0xFF000000.toInt() + i + 1) }
                viewModel.state.value.recentBackgroundColors.size shouldBe MAX_RECENT_COLORS
            }

            context("export") {
                val fileName = "com.example.test_mask"

                should("onSave writes the icon to the stored location and holds the toast result") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, icon, fileName))
                    viewModel.export.value shouldBe IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
                }

                should("onSave holds OpenPicker with the file name when the location needs the picker") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.export.value shouldBe IconExport.OpenPicker(fileName)
                }

                should("an export is Running until its work returns, and a second tap meanwhile does nothing") {
                    val gate = CompletableDeferred<Unit>()
                    exporter.gate = gate
                    val viewModel = buildViewModel(appInfo)
                    viewModel.export.test {
                        awaitItem() shouldBe IconExport.Idle
                        viewModel.onSave()
                        awaitItem() shouldBe IconExport.Running
                        viewModel.onSave()
                        viewModel.onCopy()
                        viewModel.onShare()
                        exporter.calls.size shouldBe 1
                        gate.complete(Unit)
                        awaitItem() shouldBe IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
                    }
                }

                should("onExportHandled returns to Idle and admits the next export") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.onExportHandled(IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS)))
                    viewModel.export.value shouldBe IconExport.Idle
                    viewModel.onShare()
                    exporter.calls.size shouldBe 2
                }

                should("onExportHandled keeps a result other than the handled one") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.onExportHandled(IconExport.CopyFailed)
                    viewModel.export.value shouldBe IconExport.OpenPicker(fileName)
                }

                should("a tap while a result waits for the activity replaces that result") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.onCopy()
                    viewModel.export.value shouldBe IconExport.CopyFailed
                }

                should("onCopy holds the written clip") {
                    val clip = mockk<ClipData>()
                    exporter.clip = clip
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onCopy()
                    exporter.calls shouldBe listOf(Call.CreateClip(icon))
                    viewModel.export.value shouldBe IconExport.Copy(clip)
                }

                should("onCopy holds CopyFailed when no clip could be written") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onCopy()
                    viewModel.export.value shouldBe IconExport.CopyFailed
                }

                should("onShare holds the written share file") {
                    val file = BitmapShareFile.Written(mockk<Uri>())
                    exporter.shareFile = file
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    exporter.calls shouldBe listOf(Call.CreateShareFile(icon))
                    viewModel.export.value shouldBe IconExport.Share(file)
                }

                should("onShare holds ShareFailed when the share file could not be written") {
                    exporter.shareFile = BitmapShareFile.Failed
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.export.value shouldBe IconExport.ShareFailed
                }

                should("onShare returns to Idle when the share file write was dropped") {
                    exporter.shareFile = BitmapShareFile.Dropped
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.export.value shouldBe IconExport.Idle
                }

                should("onDocumentPicked writes the icon into the created document") {
                    val uri = mockk<Uri>()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(uri))
                    exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, icon))
                    viewModel.export.value shouldBe IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
                }

                should("onDocumentPicked hands a failed generation's missing icon to the write, which reports WriteFailed") {
                    renderer.failure = IOException("generation failed")
                    exporter.documentResult = BitmapSaveResult.WriteFailed
                    val uri = mockk<Uri>()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(uri))
                    exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
                    viewModel.export.value shouldBe IconExport.SaveFinished(BitmapSaveResult.WriteFailed)
                }

                should("onDocumentPicked returns to Idle when the write reports Canceled") {
                    exporter.documentResult = BitmapSaveResult.Canceled
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(mockk<Uri>()))
                    viewModel.export.value shouldBe IconExport.Idle
                }

                should("onDocumentPicked holds WriteFailed for a result without a URI") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.MissingUri)
                    exporter.calls shouldBe emptyList()
                    viewModel.export.value shouldBe IconExport.SaveFinished(BitmapSaveResult.WriteFailed)
                }

                should("onDocumentPicked stays silent for a canceled picker") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Canceled)
                    exporter.calls shouldBe emptyList()
                    viewModel.export.value shouldBe IconExport.Idle
                }

                should("a regenerated icon keeps the running export") {
                    exporter.gate = CompletableDeferred()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.onSizeChanged(300)
                    viewModel.export.value shouldBe IconExport.Running
                }
            }
        }
    },
)
