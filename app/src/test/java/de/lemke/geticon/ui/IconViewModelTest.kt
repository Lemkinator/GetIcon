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
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_RECENT_COLORS
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.IconResult
import de.lemke.geticon.ui.FakeIconExporter.Call
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private fun IconViewModel.triggerOnCleared() {
    ViewModelStore().also { it.put("vm", this) }.clear()
}

class IconViewModelTest : ShouldSpec(
    {
        val mockContext = mockk<Context>(relaxed = true)
        val mockPackageManager = mockk<PackageManager>(relaxed = true)
        lateinit var userSettings: UserSettings
        lateinit var exporter: FakeIconExporter
        val generateIcon = mockk<GenerateIconUseCase>()

        val defaultIconSize = DEFAULT_ICON_SIZE
        val defaultForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR)
        val defaultBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR)
        val mockIconResult = IconResult(bitmap = mockk<Bitmap>(relaxed = true), isAdaptiveIcon = true, hasMaskedAppIcon = false)

        beforeEach {
            clearMocks(generateIcon)
            every { mockContext.packageManager } returns mockPackageManager
            every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            userSettings = UserSettings(FakeSharedPreferences())
            exporter = FakeIconExporter()
            every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } returns mockIconResult
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
            return IconViewModel(mockContext, handle, settings, generateIcon, exporter)
        }

        context("null applicationInfo") {
            should("emit Finish event immediately") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.events.test { awaitItem() shouldBe IconEvent.Finish }
            }

            should("not read from userSettings") {
                val spyPreferences = spyk(FakeSharedPreferences())
                val viewModel = buildViewModel(appInfo = null, settings = UserSettings(spyPreferences))
                viewModel.state.value shouldBe IconUiState()
                verify(exactly = 0) { spyPreferences.getInt(any(), any()) }
                verify(exactly = 0) { spyPreferences.getBoolean(any(), any()) }
                verify(exactly = 0) { spyPreferences.getString(any(), any()) }
            }

            should("onMaskChanged does not call generateIcon when applicationInfo is null") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.onMaskChanged(false)
                verify(exactly = 0) { generateIcon(any(), any(), any(), any(), any(), any(), any()) }
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
                viewModel.state.value.export shouldBe IconExport.Idle
            }
        }

        context("valid applicationInfo") {
            val appInfo = mockk<ApplicationInfo>(relaxed = true).also { it.packageName = "com.example.test" }

            should("load initial state from userSettings") {
                val viewModel = buildViewModel(appInfo)
                withClue("size should match userSettings.iconSize") {
                    viewModel.state.value.size shouldBe defaultIconSize
                }
                withClue("maskEnabled should match userSettings.maskEnabled") {
                    viewModel.state.value.maskEnabled shouldBe userSettings.maskEnabled
                }
                withClue("colorEnabled should match userSettings.colorEnabled") {
                    viewModel.state.value.colorEnabled shouldBe userSettings.colorEnabled
                }
            }

            should("set isAdaptiveIcon from generateIcon result after init") {
                val viewModel = buildViewModel(appInfo)
                // StateFlow.filter{}.first() returns immediately if predicate matches current value;
                // suspends until a matching emission arrives otherwise. Robust for sync or async init.
                viewModel.state.first { it.isAdaptiveIcon }
            }

            should("set recentForegroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentForegroundColors shouldBe defaultForegroundColors
            }

            should("set recentBackgroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentBackgroundColors shouldBe defaultBackgroundColors
            }

            should("onMaskChanged updates maskEnabled in state") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                viewModel.state.value.maskEnabled shouldBe false
            }

            should("onColorChanged updates colorEnabled in state") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onColorChanged(true)
                viewModel.state.value.colorEnabled shouldBe true
            }

            should("onSizeChanged updates size in state") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(300)
                viewModel.state.value.size shouldBe 300
            }

            should("onSizeChanged clamps value below MIN_ICON_SIZE to MIN_ICON_SIZE") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MIN_ICON_SIZE - 1)
                viewModel.state.value.size shouldBe MIN_ICON_SIZE
            }

            should("onSizeChanged clamps value above MAX_ICON_SIZE to MAX_ICON_SIZE") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MAX_ICON_SIZE + 1)
                viewModel.state.value.size shouldBe MAX_ICON_SIZE
            }

            should("onSizeChanged accepts MIN_ICON_SIZE and MAX_ICON_SIZE as boundary values") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(MIN_ICON_SIZE)
                viewModel.state.value.size shouldBe MIN_ICON_SIZE
                viewModel.onSizeChanged(MAX_ICON_SIZE)
                viewModel.state.value.size shouldBe MAX_ICON_SIZE
            }

            should("onForegroundColorChanged prepends color to recent list") {
                val viewModel = buildViewModel(appInfo)
                val newColor = 0xFFFF0000.toInt()
                viewModel.onForegroundColorChanged(newColor)
                viewModel.state.value.recentForegroundColors
                    .first() shouldBe newColor
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
                viewModel.state.value.recentBackgroundColors
                    .first() shouldBe newColor
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

            should("onCleared skips file deletion when sourceDir is not in cacheDir") {
                buildViewModel(
                    ApplicationInfo().also {
                        it.packageName = "com.example.test"
                        it.sourceDir = "/data/app/com.example.test.apk"
                    },
                ).triggerOnCleared()
            }

            should("onCleared deletes temp file when sourceDir is in cacheDir") {
                val tmpDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                val tmpFile = File(tmpDir, "test_icon.apk").also { it.createNewFile() }
                buildViewModel(
                    ApplicationInfo().also {
                        it.packageName = "com.example.test"
                        it.sourceDir = tmpFile.absolutePath
                    },
                ).triggerOnCleared()
                tmpFile.exists() shouldBe false
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
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError("oom")
                viewModel.onMaskChanged(false)
                viewModel.state.value.isLoading shouldBe false
            }

            should("emit GenerateFailed when generateIcon throws IOException in loadInitialState") {
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws IOException("io error")
                val viewModel = buildViewModel(appInfo)
                viewModel.events.test {
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit GenerateFailed when generateIcon throws OutOfMemoryError in loadInitialState") {
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError("oom")
                val viewModel = buildViewModel(appInfo)
                viewModel.events.test {
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit GenerateFailed when generateIcon throws RuntimeException in loadInitialState") {
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("crash")
                val viewModel = buildViewModel(appInfo)
                viewModel.events.test {
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit GenerateFailed when generateIcon throws OutOfMemoryError in regenerateIcon") {
                val viewModel = buildViewModel(appInfo)
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws OutOfMemoryError("oom")
                viewModel.events.test {
                    viewModel.onMaskChanged(false)
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit GenerateFailed when generateIcon throws RuntimeException in regenerateIcon") {
                val viewModel = buildViewModel(appInfo)
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("crash")
                viewModel.events.test {
                    viewModel.onMaskChanged(false)
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit Finish when temp APK file was deleted before loadInitialState (process death)") {
                val tmpDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                val deletedApk = File(tmpDir, "deleted_icon_${System.nanoTime()}.apk") // never created
                val staleInfo =
                    ApplicationInfo().also {
                        it.packageName = "com.example.test"
                        it.sourceDir = deletedApk.absolutePath
                    }
                every { mockContext.cacheDir } returns tmpDir
                val viewModel = buildViewModel(staleInfo)
                viewModel.events.test {
                    awaitItem() shouldBe IconEvent.Finish
                }
            }

            should("not emit GenerateFailed when generateIcon throws CancellationException in loadInitialState") {
                every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")
                val viewModel = buildViewModel(appInfo)
                viewModel.events.test { expectNoEvents() }
            }

            should("buildFileName: mask=true color=false produces _mask suffix") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.fileName shouldBe "${appInfo.packageName}_mask"
            }

            should("buildFileName: mask=false color=false produces _default suffix") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                viewModel.state.value.fileName shouldBe "${appInfo.packageName}_default"
            }

            should("buildFileName: mask=true color=true produces _mask_mono suffix") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onColorChanged(true)
                viewModel.state.value.fileName shouldBe "${appInfo.packageName}_mask_mono"
            }

            should("buildFileName: mask=false color=true produces _default_mono suffix") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                viewModel.onColorChanged(true)
                viewModel.state.value.fileName shouldBe "${appInfo.packageName}_default_mono"
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

            should("onCleared skips deletion when canonicalFile throws IOException") {
                val mockCacheDir = mockk<File>()
                every { mockCacheDir.canonicalFile } throws IOException("canonical failed")
                every { mockContext.cacheDir } returns mockCacheDir
                val tmpFile =
                    File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_${System.nanoTime()}.apk")
                        .also { it.createNewFile() }
                try {
                    buildViewModel(
                        ApplicationInfo().also {
                            it.packageName = "com.example.test"
                            it.sourceDir = tmpFile.absolutePath
                        },
                    ).triggerOnCleared()
                    tmpFile.exists() shouldBe true
                } finally {
                    tmpFile.delete()
                }
            }

            context("export") {
                val icon = mockIconResult.bitmap
                val fileName = "com.example.test_mask"

                should("onSave writes the icon to the stored location and holds the toast result") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, icon, fileName))
                    viewModel.state.value.export shouldBe IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
                }

                should("onSave holds OpenPicker with the file name when the location needs the picker") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.state.value.export shouldBe IconExport.OpenPicker(fileName)
                }

                should("an export is Running until its work returns, and a second tap meanwhile does nothing") {
                    val gate = CompletableDeferred<Unit>()
                    exporter.gate = gate
                    val viewModel = buildViewModel(appInfo)
                    viewModel.state.map { it.export }.test {
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
                    viewModel.state.value.export shouldBe IconExport.Idle
                    viewModel.onShare()
                    exporter.calls.size shouldBe 2
                }

                should("onExportHandled keeps a result other than the handled one") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.onExportHandled(IconExport.CopyFailed)
                    viewModel.state.value.export shouldBe IconExport.OpenPicker(fileName)
                }

                should("a tap while a result waits for the activity replaces that result") {
                    exporter.directoryResult = BitmapSaveResult.NeedsPicker
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onSave()
                    viewModel.onCopy()
                    viewModel.state.value.export shouldBe IconExport.CopyFailed
                }

                should("onCopy holds the written clip") {
                    val clip = mockk<ClipData>()
                    exporter.clip = clip
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onCopy()
                    exporter.calls shouldBe listOf(Call.CreateClip(icon))
                    viewModel.state.value.export shouldBe IconExport.Copy(clip)
                }

                should("onCopy holds CopyFailed when no clip could be written") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onCopy()
                    viewModel.state.value.export shouldBe IconExport.CopyFailed
                }

                should("onShare holds the written share file") {
                    val file = BitmapShareFile.Written(mockk<Uri>())
                    exporter.shareFile = file
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    exporter.calls shouldBe listOf(Call.CreateShareFile(icon))
                    viewModel.state.value.export shouldBe IconExport.Share(file)
                }

                should("onShare holds ShareFailed when the share file could not be written") {
                    exporter.shareFile = BitmapShareFile.Failed
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.state.value.export shouldBe IconExport.ShareFailed
                }

                should("onShare returns to Idle when the share file write was dropped") {
                    exporter.shareFile = BitmapShareFile.Dropped
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.state.value.export shouldBe IconExport.Idle
                }

                should("onDocumentPicked writes the icon into the created document") {
                    val uri = mockk<Uri>()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(uri))
                    exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, icon))
                    viewModel.state.value.export shouldBe IconExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
                }

                should("onDocumentPicked hands a failed generation's missing icon to the write, which reports WriteFailed") {
                    every { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws IOException("generation failed")
                    exporter.documentResult = BitmapSaveResult.WriteFailed
                    val uri = mockk<Uri>()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(uri))
                    exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
                    viewModel.state.value.export shouldBe IconExport.SaveFinished(BitmapSaveResult.WriteFailed)
                }

                should("onDocumentPicked returns to Idle when the write reports Canceled") {
                    exporter.documentResult = BitmapSaveResult.Canceled
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Created(mockk<Uri>()))
                    viewModel.state.value.export shouldBe IconExport.Idle
                }

                should("onDocumentPicked holds WriteFailed for a result without a URI") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.MissingUri)
                    exporter.calls shouldBe emptyList()
                    viewModel.state.value.export shouldBe IconExport.SaveFinished(BitmapSaveResult.WriteFailed)
                }

                should("onDocumentPicked stays silent for a canceled picker") {
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onDocumentPicked(DocumentPick.Canceled)
                    exporter.calls shouldBe emptyList()
                    viewModel.state.value.export shouldBe IconExport.Idle
                }

                should("a regenerated icon keeps the running export") {
                    exporter.gate = CompletableDeferred()
                    val viewModel = buildViewModel(appInfo)
                    viewModel.onShare()
                    viewModel.onSizeChanged(300)
                    viewModel.state.value.export shouldBe IconExport.Running
                }
            }

            should("loadInitialState falls through to generateIcon when canonicalFile throws IOException") {
                val mockCacheDir = mockk<File>()
                every { mockCacheDir.canonicalFile } throws IOException("canonical failed")
                every { mockContext.cacheDir } returns mockCacheDir
                val viewModel =
                    buildViewModel(
                        mockk<ApplicationInfo>(relaxed = true).also {
                            it.packageName = "com.example.test"
                            it.sourceDir =
                                File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_${System.nanoTime()}.apk").absolutePath
                        },
                    )
                viewModel.state.value.isLoading shouldBe false
            }
        }
    },
)
