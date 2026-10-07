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
import de.lemke.geticon.data.FakeApkImporter
import de.lemke.geticon.domain.ProcessApkUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

private fun MainViewModel.triggerOnCleared() {
    ViewModelStore().also { it.put("vm", this) }.clear()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : ShouldSpec(
    {
        lateinit var importer: FakeApkImporter
        lateinit var getInstalledApps: GetInstalledAppsUseCase
        lateinit var getApplicationInfo: GetApplicationInfoUseCase
        lateinit var viewModel: MainViewModel

        fun buildViewModel(io: CoroutineDispatcher = Dispatchers.Main) =
            MainViewModel(ProcessApkUseCase(importer, io), getInstalledApps, getApplicationInfo)

        beforeEach {
            importer = FakeApkImporter()
            getInstalledApps = mockk()
            getApplicationInfo = mockk()
            coEvery { getInstalledApps() } returns emptyList()
            viewModel = buildViewModel()
        }

        afterEach { importer.cacheDir.deleteRecursively() }

        fun pick(packageName: String): ApkImport.Imported {
            val uri = mockk<Uri>()
            importer.addApk(uri, packageName)
            viewModel.onApkPicked(uri)
            return viewModel.apkImport.value.shouldBeInstanceOf<ApkImport.Imported>()
        }

        should("installedApps is Loading until the load returns, then Loaded with the apps") {
            val app = mockk<AppInfoData>()
            val gate = CompletableDeferred<List<AppInfoData>>()
            coEvery { getInstalledApps() } coAnswers { gate.await() }
            viewModel = buildViewModel()
            viewModel.installedApps.test {
                awaitItem() shouldBe InstalledApps.Loading
                gate.complete(listOf(app))
                awaitItem() shouldBe InstalledApps.Loaded(listOf(app))
            }
        }

        should("installedApps is Failed and not yet handled when getInstalledApps throws") {
            coEvery { getInstalledApps() } throws RuntimeException("load failed")
            viewModel = buildViewModel()
            viewModel.installedApps.value shouldBe InstalledApps.Failed(handled = false)
        }

        should("onInstalledAppsFailureHandled marks the failure handled") {
            coEvery { getInstalledApps() } throws RuntimeException("load failed")
            viewModel = buildViewModel()
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
            importer.opened shouldBe emptyList()
        }

        should("apkImport holds Invalid when the picked document is no APK") {
            val uri = mockk<Uri>()
            importer.addDocument(uri, "not an apk")
            viewModel.onApkPicked(uri)
            viewModel.apkImport.value shouldBe ApkImport.Invalid
        }

        should("apkImport holds Invalid when the picked document has no content") {
            viewModel.onApkPicked(mockk<Uri>())
            viewModel.apkImport.value shouldBe ApkImport.Invalid
        }

        should("apkImport holds Imported with the application of the cached APK") {
            val uri = mockk<Uri>()
            importer.addApk(uri, "com.example.app")
            viewModel.apkImport.test {
                awaitItem() shouldBe ApkImport.Idle
                viewModel.onApkPicked(uri)
                val imported = awaitItem().shouldBeInstanceOf<ApkImport.Imported>()
                imported.applicationInfo.packageName shouldBe "com.example.app"
                imported.applicationInfo.sourceDir shouldBe importer.cachedFiles().single().absolutePath
            }
        }

        should("onApkImportHandled returns to Idle") {
            val uri = mockk<Uri>()
            importer.addDocument(uri, "not an apk")
            viewModel.onApkPicked(uri)
            viewModel.onApkImportHandled(ApkImport.Invalid)
            viewModel.apkImport.value shouldBe ApkImport.Idle
        }

        should("onApkImportHandled keeps a result other than the handled one") {
            val imported = pick("com.example.app")
            viewModel.onApkImportHandled(ApkImport.Invalid)
            viewModel.apkImport.value shouldBe imported
        }

        should("a superseding import deletes the cached APK of the displaced Imported result") {
            val first = pick("com.example.first")
            val second = pick("com.example.second")
            viewModel.apkImport.value shouldBe second
            File(first.applicationInfo.sourceDir).exists() shouldBe false
            importer.cachedFiles() shouldBe listOf(File(second.applicationInfo.sourceDir))
        }

        should("a newer pick cancels an older import before it opens its document") {
            val io = StandardTestDispatcher()
            viewModel = buildViewModel(io)
            val olderUri = mockk<Uri>()
            val newerUri = mockk<Uri>()
            importer.addApk(olderUri, "com.example.older")
            importer.addApk(newerUri, "com.example.newer")
            viewModel.onApkPicked(olderUri)
            viewModel.onApkPicked(newerUri)
            io.scheduler.advanceUntilIdle()
            importer.opened shouldBe listOf(newerUri)
            val imported = viewModel.apkImport.value.shouldBeInstanceOf<ApkImport.Imported>()
            imported.applicationInfo.packageName shouldBe "com.example.newer"
            importer.cachedFiles() shouldBe listOf(File(imported.applicationInfo.sourceDir))
        }

        should("an Invalid result superseding an Imported one deletes its cached APK") {
            pick("com.example.app")
            val invalidUri = mockk<Uri>()
            importer.addDocument(invalidUri, "not an apk")
            viewModel.onApkPicked(invalidUri)
            viewModel.apkImport.value shouldBe ApkImport.Invalid
            importer.cachedFiles() shouldBe emptyList()
        }

        should("a handled Imported result keeps its cached APK for the screen it opened") {
            val imported = pick("com.example.app")
            viewModel.onApkImportHandled(imported)
            viewModel.triggerOnCleared()
            importer.cachedFiles() shouldBe listOf(File(imported.applicationInfo.sourceDir))
        }

        should("onCleared deletes the cached APK of an Imported result that was never handled") {
            pick("com.example.app")
            viewModel.triggerOnCleared()
            importer.cachedFiles() shouldBe emptyList()
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
