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

import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.picker.model.AppInfoData
import app.cash.turbine.test
import de.lemke.commonutils.domain.GetApplicationInfoUseCase
import de.lemke.commonutils.domain.GetInstalledAppsUseCase
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

class MainViewModelTest : ShouldSpec(
    {
        lateinit var processApk: ProcessApkUseCase
        lateinit var getInstalledApps: GetInstalledAppsUseCase
        lateinit var getApplicationInfo: GetApplicationInfoUseCase
        lateinit var viewModel: MainViewModel

        beforeEach {
            processApk = mockk()
            getInstalledApps = mockk()
            getApplicationInfo = mockk()
            coEvery { getInstalledApps() } returns emptyList()
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
        }

        should("installedApps emits loaded list") {
            val app = mockk<AppInfoData>()
            coEvery { getInstalledApps() } returns listOf(app)
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
            viewModel.installedApps.value shouldBe listOf(app)
        }

        should("emit ShowLoadError when getInstalledApps throws") {
            coEvery { getInstalledApps() } throws RuntimeException("load failed")
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
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

        should("onAppSelected holds Found with the looked-up ApplicationInfo") {
            val appInfo = mockk<ApplicationInfo>()
            coEvery { getApplicationInfo("com.example.app") } returns appInfo
            viewModel.onAppSelected("com.example.app")
            viewModel.appLookup.value shouldBe AppLookup.Found(appInfo)
        }

        should("onAppSelected holds NotFound for a package that is not installed") {
            coEvery { getApplicationInfo("com.example.missing") } returns null
            viewModel.onAppSelected("com.example.missing")
            viewModel.appLookup.value shouldBe AppLookup.NotFound
        }

        should("a lookup is Running until it returns, and a second selection meanwhile does nothing") {
            val appInfo = mockk<ApplicationInfo>()
            val gate = CompletableDeferred<ApplicationInfo?>()
            coEvery { getApplicationInfo("com.example.app") } coAnswers { gate.await() }
            viewModel.appLookup.test {
                awaitItem() shouldBe AppLookup.Idle
                viewModel.onAppSelected("com.example.app")
                awaitItem() shouldBe AppLookup.Running
                viewModel.onAppSelected("com.example.other")
                gate.complete(appInfo)
                awaitItem() shouldBe AppLookup.Found(appInfo)
            }
            coVerify(exactly = 0) { getApplicationInfo("com.example.other") }
        }

        should("onAppLookupHandled returns to Idle") {
            coEvery { getApplicationInfo("com.example.missing") } returns null
            viewModel.onAppSelected("com.example.missing")
            viewModel.onAppLookupHandled(AppLookup.NotFound)
            viewModel.appLookup.value shouldBe AppLookup.Idle
        }

        should("onAppLookupHandled keeps a result other than the handled one") {
            val appInfo = mockk<ApplicationInfo>()
            coEvery { getApplicationInfo("com.example.app") } returns appInfo
            viewModel.onAppSelected("com.example.app")
            viewModel.onAppLookupHandled(AppLookup.NotFound)
            viewModel.appLookup.value shouldBe AppLookup.Found(appInfo)
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
    },
)
