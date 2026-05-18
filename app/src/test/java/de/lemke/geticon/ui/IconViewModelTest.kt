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
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.IconResult
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class IconViewModelTest : ShouldSpec({
    val mockContext = mockk<Context>(relaxed = true)
    val mockPackageManager = mockk<PackageManager>(relaxed = true)
    val getUserSettings = mockk<GetUserSettingsUseCase>()
    val updateUserSettings = mockk<UpdateUserSettingsUseCase>()
    val generateIcon = mockk<GenerateIconUseCase>()

    val defaultSettings =
        UserSettings(
            iconSize = 512,
            maskEnabled = true,
            colorEnabled = false,
            recentForegroundColors = listOf(-1),
            recentBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR),
        )
    val mockIconResult = IconResult(bitmap = mockk<Bitmap>(relaxed = true), isAdaptiveIcon = true, hasMaskedAppIcon = false)

    beforeEach {
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

        should("onSizeChanged clamps value below 16 to 16") {
            val viewModel = buildViewModel(appInfo)
            viewModel.onSizeChanged(0)
            viewModel.state.value.size shouldBe 16
        }

        should("onSizeChanged clamps value above 1024 to 1024") {
            val viewModel = buildViewModel(appInfo)
            viewModel.onSizeChanged(9999)
            viewModel.state.value.size shouldBe 1024
        }

        should("onSizeChanged accepts 16 and 1024 as boundary values") {
            val viewModel = buildViewModel(appInfo)
            viewModel.onSizeChanged(16)
            viewModel.state.value.size shouldBe 16
            viewModel.onSizeChanged(1024)
            viewModel.state.value.size shouldBe 1024
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
    }
})
