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
import androidx.lifecycle.ViewModelStore
import androidx.picker.model.AppInfoData
import app.cash.turbine.test
import de.lemke.commonutils.domain.GetApplicationInfoUseCase
import de.lemke.commonutils.domain.GetInstalledAppsUseCase
import de.lemke.geticon.domain.ApkProcessResult
import de.lemke.geticon.domain.ProcessApkUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CompletableDeferred

private fun MainViewModel.triggerOnCleared() {
    ViewModelStore().also { it.put("vm", this) }.clear()
}

private fun cachedApk(): ApplicationInfo =
    ApplicationInfo().also { it.sourceDir = File.createTempFile("extractIcon", ".apk").apply { deleteOnExit() }.absolutePath }

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

        should("installedApps is Loading until the load returns, then Loaded with the apps") {
            val app = mockk<AppInfoData>()
            val gate = CompletableDeferred<List<AppInfoData>>()
            coEvery { getInstalledApps() } coAnswers { gate.await() }
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
            viewModel.installedApps.test {
                awaitItem() shouldBe InstalledApps.Loading
                gate.complete(listOf(app))
                awaitItem() shouldBe InstalledApps.Loaded(listOf(app))
            }
        }

        should("installedApps is Failed and not yet handled when getInstalledApps throws") {
            coEvery { getInstalledApps() } throws RuntimeException("load failed")
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
            viewModel.installedApps.value shouldBe InstalledApps.Failed(handled = false)
        }

        should("onInstalledAppsFailureHandled marks the failure handled") {
            coEvery { getInstalledApps() } throws RuntimeException("load failed")
            viewModel = MainViewModel(processApk, getInstalledApps, getApplicationInfo)
            viewModel.installedApps.test {
                awaitItem() shouldBe InstalledApps.Failed(handled = false)
                viewModel.onInstalledAppsFailureHandled()
                awaitItem() shouldBe InstalledApps.Failed(handled = true)
            }
        }

        should("onInstalledAppsFailureHandled keeps a loaded list") {
            viewModel.onInstalledAppsFailureHandled()
            viewModel.installedApps.value shouldBe InstalledApps.Loaded(emptyList())
        }

        should("apkImport stays Idle when uri is null") {
            viewModel.onApkPicked(null)
            viewModel.apkImport.value shouldBe ApkImport.Idle
            coVerify(exactly = 0) { processApk(any()) }
        }

        should("apkImport holds Invalid when processApk returns InvalidApk") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.InvalidApk
            viewModel.onApkPicked(uri)
            viewModel.apkImport.value shouldBe ApkImport.Invalid
        }

        should("apkImport holds Invalid when processApk returns Error") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.Error
            viewModel.onApkPicked(uri)
            viewModel.apkImport.value shouldBe ApkImport.Invalid
        }

        should("apkImport holds Imported with the returned ApplicationInfo when processApk succeeds") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(appInfo)
            viewModel.apkImport.test {
                awaitItem() shouldBe ApkImport.Idle
                viewModel.onApkPicked(uri)
                awaitItem() shouldBe ApkImport.Imported(appInfo)
            }
        }

        should("onApkImportHandled returns to Idle") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.InvalidApk
            viewModel.onApkPicked(uri)
            viewModel.onApkImportHandled(ApkImport.Invalid)
            viewModel.apkImport.value shouldBe ApkImport.Idle
        }

        should("onApkImportHandled keeps a result other than the handled one") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(appInfo)
            viewModel.onApkPicked(uri)
            viewModel.onApkImportHandled(ApkImport.Invalid)
            viewModel.apkImport.value shouldBe ApkImport.Imported(appInfo)
        }

        should("a superseding import deletes the cached APK of the displaced Imported result") {
            val first = cachedApk()
            val second = cachedApk()
            val firstUri = mockk<Uri>()
            val secondUri = mockk<Uri>()
            coEvery { processApk(firstUri) } returns ApkProcessResult.Success(first)
            coEvery { processApk(secondUri) } returns ApkProcessResult.Success(second)
            viewModel.onApkPicked(firstUri)
            viewModel.onApkPicked(secondUri)
            viewModel.apkImport.value shouldBe ApkImport.Imported(second)
            File(first.sourceDir).exists() shouldBe false
            File(second.sourceDir).exists() shouldBe true
        }

        should("a newer pick cancels an older import that would otherwise finish after it") {
            val newer = cachedApk()
            val olderUri = mockk<Uri>()
            val newerUri = mockk<Uri>()
            val olderGate = CompletableDeferred<ApkProcessResult>()
            coEvery { processApk(olderUri) } coAnswers { olderGate.await() }
            coEvery { processApk(newerUri) } returns ApkProcessResult.Success(newer)
            viewModel.onApkPicked(olderUri)
            viewModel.onApkPicked(newerUri)
            olderGate.complete(ApkProcessResult.Success(cachedApk()))
            viewModel.apkImport.value shouldBe ApkImport.Imported(newer)
            File(newer.sourceDir).exists() shouldBe true
        }

        should("an Invalid result superseding an Imported one deletes its cached APK") {
            val imported = cachedApk()
            val importedUri = mockk<Uri>()
            val invalidUri = mockk<Uri>()
            coEvery { processApk(importedUri) } returns ApkProcessResult.Success(imported)
            coEvery { processApk(invalidUri) } returns ApkProcessResult.InvalidApk
            viewModel.onApkPicked(importedUri)
            viewModel.onApkPicked(invalidUri)
            viewModel.apkImport.value shouldBe ApkImport.Invalid
            File(imported.sourceDir).exists() shouldBe false
        }

        should("a handled Imported result keeps its cached APK for the screen it opened") {
            val imported = cachedApk()
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(imported)
            viewModel.onApkPicked(uri)
            viewModel.onApkImportHandled(ApkImport.Imported(imported))
            viewModel.triggerOnCleared()
            File(imported.sourceDir).exists() shouldBe true
        }

        should("onCleared deletes the cached APK of an Imported result that was never handled") {
            val imported = cachedApk()
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns ApkProcessResult.Success(imported)
            viewModel.onApkPicked(uri)
            viewModel.triggerOnCleared()
            File(imported.sourceDir).exists() shouldBe false
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
    },
)
