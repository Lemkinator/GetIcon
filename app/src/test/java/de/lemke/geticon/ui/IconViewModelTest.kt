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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_RECENT_COLORS
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.IconResult
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow

class IconViewModelTest : ShouldSpec(
    {
        val mockContext = mockk<Context>(relaxed = true)
        val mockPackageManager = mockk<PackageManager>(relaxed = true)
        val getUserSettings = mockk<GetUserSettingsUseCase>()
        val updateUserSettings = mockk<UpdateUserSettingsUseCase>()
        val generateIcon = mockk<GenerateIconUseCase>()

        val defaultSettings =
            UserSettings(
                iconSize = DEFAULT_ICON_SIZE,
                maskEnabled = true,
                colorEnabled = false,
                recentForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR),
                recentBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR),
            )
        val mockIconResult = IconResult(bitmap = mockk<Bitmap>(relaxed = true), isAdaptiveIcon = true, hasMaskedAppIcon = false)

        beforeEach {
            clearMocks(getUserSettings, updateUserSettings, generateIcon)
            every { mockContext.packageManager } returns mockPackageManager
            every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            coEvery { getUserSettings() } returns defaultSettings
            coEvery { generateIcon(any(), any(), any(), any(), any(), any(), any()) } returns mockIconResult
            coEvery { updateUserSettings(any()) } answers {
                val transform = firstArg<(UserSettings) -> UserSettings>()
                transform(defaultSettings)
            }
        }

        fun buildViewModel(appInfo: ApplicationInfo? = null): IconViewModel {
            val handle =
                if (appInfo != null) {
                    SavedStateHandle(mapOf(IconActivity.KEY_APPLICATION_INFO to appInfo))
                } else {
                    SavedStateHandle()
                }
            return IconViewModel(mockContext, handle, getUserSettings, updateUserSettings, generateIcon)
        }

        context("null applicationInfo") {
            should("emit Finish event immediately") {
                val viewModel = buildViewModel(appInfo = null)
                viewModel.events.tryReceive().getOrNull() shouldBe IconEvent.Finish
            }

            should("not call getUserSettings") {
                buildViewModel(appInfo = null)
                coVerify(exactly = 0) { getUserSettings() }
            }
        }

        context("valid applicationInfo") {
            val appInfo = mockk<ApplicationInfo>(relaxed = true).also { it.packageName = "com.example.test" }

            should("load initial state from getUserSettings") {
                val viewModel = buildViewModel(appInfo)
                withClue("size should match defaultSettings.iconSize") {
                    viewModel.state.value.size shouldBe defaultSettings.iconSize
                }
                withClue("maskEnabled should match defaultSettings.maskEnabled") {
                    viewModel.state.value.maskEnabled shouldBe defaultSettings.maskEnabled
                }
                withClue("colorEnabled should match defaultSettings.colorEnabled") {
                    viewModel.state.value.colorEnabled shouldBe defaultSettings.colorEnabled
                }
            }

            should("set isAdaptiveIcon from generateIcon result after init") {
                val viewModel = buildViewModel(appInfo)
                // StateFlow.filter{}.first() returns immediately if predicate matches current value;
                // suspends until a matching emission arrives otherwise. Robust for sync or async init.
                viewModel.state.filter { it.isAdaptiveIcon }.first()
            }

            should("set recentForegroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentForegroundColors shouldBe defaultSettings.recentForegroundColors
            }

            should("set recentBackgroundColors from settings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.state.value.recentBackgroundColors shouldBe defaultSettings.recentBackgroundColors
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
                val existingColor = defaultSettings.recentForegroundColors.first()
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

            should("onMaskChanged calls updateUserSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onMaskChanged(false)
                coVerify(atLeast = 1) { updateUserSettings(any()) }
            }

            should("onColorChanged calls updateUserSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onColorChanged(true)
                coVerify(atLeast = 1) { updateUserSettings(any()) }
            }

            should("onSizeChanged calls updateUserSettings") {
                val viewModel = buildViewModel(appInfo)
                viewModel.onSizeChanged(256)
                coVerify(atLeast = 1) { updateUserSettings(any()) }
            }

            should("onCleared skips file deletion when sourceDir is not in cacheDir") {
                appInfo.sourceDir = "/data/app/com.example.test.apk"
                val viewModel = buildViewModel(appInfo)
                viewModel.javaClass
                    .getDeclaredMethod("onCleared")
                    .also { it.isAccessible = true }
                    .invoke(viewModel)
            }

            should("onCleared deletes temp file when sourceDir is in cacheDir") {
                val tmpDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
                val tmpFile = File(tmpDir, "test_icon.apk").also { it.createNewFile() }
                appInfo.sourceDir = tmpFile.absolutePath
                val viewModel = buildViewModel(appInfo)
                viewModel.javaClass
                    .getDeclaredMethod("onCleared")
                    .also { it.isAccessible = true }
                    .invoke(viewModel)
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

            should("isLoading resets to false when regenerateIcon throws") {
                val viewModel = buildViewModel(appInfo)
                coEvery { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("regen failed")
                viewModel.onMaskChanged(false)
                viewModel.state.value.isLoading shouldBe false
            }

            should("emit GenerateFailed when generateIcon throws in loadInitialState") {
                coEvery { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("load failed")
                val viewModel = buildViewModel(appInfo)
                viewModel.events.receiveAsFlow().test {
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
            }

            should("emit GenerateFailed when generateIcon throws in regenerateIcon") {
                val viewModel = buildViewModel(appInfo)
                coEvery { generateIcon(any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("regen failed")
                viewModel.events.receiveAsFlow().test {
                    viewModel.onMaskChanged(false)
                    awaitItem().shouldBeInstanceOf<IconEvent.GenerateFailed>()
                }
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
        }
    },
)
