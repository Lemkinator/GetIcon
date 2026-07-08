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
import android.net.Uri
import androidx.picker.model.AppInfoData
import app.cash.turbine.test
import de.lemke.commonutils.getInstalledAppsForPicker
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.GetApplicationInfoUseCase
import de.lemke.geticon.domain.ProcessApkUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

class MainViewModelTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val processApk = mockk<ProcessApkUseCase>()
        val getApplicationInfo = mockk<GetApplicationInfoUseCase>()
        lateinit var viewModel: MainViewModel

        beforeEach {
            mockkStatic("de.lemke.commonutils.AppPickerStrategyKt")
            every { context.getInstalledAppsForPicker() } returns emptyList()
            every { getApplicationInfo(any()) } returns null
            viewModel = MainViewModel(context, processApk, getApplicationInfo)
        }

        afterEach {
            unmockkStatic("de.lemke.commonutils.AppPickerStrategyKt")
        }

        should("installedApps emits loaded list") {
            val app = mockk<AppInfoData>()
            every { context.getInstalledAppsForPicker() } returns listOf(app)
            viewModel = MainViewModel(context, processApk, getApplicationInfo)
            viewModel.installedApps.value shouldBe listOf(app)
        }

        should("emit ShowLoadError when getInstalledAppsForPicker throws") {
            every { context.getInstalledAppsForPicker() } throws RuntimeException("load failed")
            viewModel = MainViewModel(context, processApk, getApplicationInfo)
            viewModel.events.test {
                awaitItem() shouldBe MainEvent.ShowLoadError
            }
        }

        should("emit no event when uri is null") {
            viewModel.events.test {
                viewModel.onApkPicked(null)
                expectNoEvents()
            }
        }

        should("emit ShowError when processApk returns InvalidApk") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.InvalidApk

            viewModel.events.test {
                viewModel.onApkPicked(uri)
                awaitItem() shouldBe MainEvent.ShowError
            }
        }

        should("emit ShowError when processApk returns Error") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.Error

            viewModel.events.test {
                viewModel.onApkPicked(uri)
                awaitItem() shouldBe MainEvent.ShowError
            }
        }

        should("emit NavigateToApkIcon when processApk succeeds") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(appInfo)

            viewModel.events.test {
                viewModel.onApkPicked(uri)
                awaitItem().shouldBeInstanceOf<MainEvent.NavigateToApkIcon>()
            }
        }

        should("NavigateToApkIcon carries the returned ApplicationInfo") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(appInfo)

            viewModel.events.test {
                viewModel.onApkPicked(uri)
                val event = awaitItem() as MainEvent.NavigateToApkIcon
                event.applicationInfo shouldBe appInfo
            }
        }

        should("emit NavigateToIcon when onAppSelected finds the package") {
            val appInfo = mockk<ApplicationInfo>()
            every { getApplicationInfo(any()) } returns appInfo
            viewModel.events.test {
                viewModel.onAppSelected("com.example.test")
                val event = awaitItem()
                event.shouldBeInstanceOf<MainEvent.NavigateToIcon>()
                (event as MainEvent.NavigateToIcon).applicationInfo shouldBe appInfo
            }
        }

        should("emit ShowAppNotFoundError when onAppSelected package not found") {
            every { getApplicationInfo(any()) } returns null
            viewModel.events.test {
                viewModel.onAppSelected("com.nonexistent.pkg")
                awaitItem() shouldBe MainEvent.ShowAppNotFoundError
            }
        }
    },
)
