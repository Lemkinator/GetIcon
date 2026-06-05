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

package de.lemke.geticon.domain

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessApkUseCaseTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val uri = mockk<Uri>()
        lateinit var cacheDir: File
        lateinit var useCase: ProcessApkUseCase

        beforeEach {
            cacheDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "pauTest_${System.nanoTime()}")
            cacheDir.mkdirs()
            every { context.packageManager } returns packageManager
            every { context.contentResolver } returns contentResolver
            every { context.cacheDir } returns cacheDir
            every { contentResolver.openInputStream(any()) } returns null
            useCase = ProcessApkUseCase(context, UnconfinedTestDispatcher())
        }

        afterEach { cacheDir.deleteRecursively() }

        should("return Error when openInputStream returns null") {
            // null stream = provider could not open the content, not an invalid APK
            val result = useCase(uri)
            result shouldBe ApkProcessResult.Error
        }

        should("delete temp file when openInputStream returns null") {
            useCase(uri)
            cacheDir.listFiles()?.filter { it.name.startsWith("extractIcon") } shouldBe emptyList()
        }

        should("return InvalidApk when package manager returns no applicationInfo") {
            every { contentResolver.openInputStream(any()) } returns "fake content".byteInputStream()
            every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns null
            val result = useCase(uri)
            result shouldBe ApkProcessResult.InvalidApk
        }

        should("delete temp file when applicationInfo is null") {
            every { contentResolver.openInputStream(any()) } returns "fake content".byteInputStream()
            every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns null
            useCase(uri)
            cacheDir.listFiles()?.filter { it.name.startsWith("extractIcon") } shouldBe emptyList()
        }

        should("return Error on IOException from openInputStream") {
            every { contentResolver.openInputStream(any()) } throws IOException("stream error")
            val result = useCase(uri)
            result shouldBe ApkProcessResult.Error
        }

        should("delete temp file on IOException") {
            every { contentResolver.openInputStream(any()) } throws IOException("stream error")
            useCase(uri)
            cacheDir.listFiles()?.filter { it.name.startsWith("extractIcon") } shouldBe emptyList()
        }

        should("return Error on SecurityException") {
            every { contentResolver.openInputStream(any()) } throws SecurityException("no permission")
            val result = useCase(uri)
            result shouldBe ApkProcessResult.Error
        }

        should("return Error on RuntimeException from getPackageArchiveInfo") {
            every { contentResolver.openInputStream(any()) } returns "fake content".byteInputStream()
            every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } throws RuntimeException("parse error")
            val result = useCase(uri)
            result shouldBe ApkProcessResult.Error
        }

        should("delete temp file on RuntimeException") {
            every { contentResolver.openInputStream(any()) } returns "fake content".byteInputStream()
            every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } throws RuntimeException("parse error")
            useCase(uri)
            cacheDir.listFiles()?.filter { it.name.startsWith("extractIcon") } shouldBe emptyList()
        }

        should("rethrow CancellationException instead of returning Error") {
            every { contentResolver.openInputStream(any()) } throws CancellationException("cancelled")
            val exception = runCatching { useCase(uri) }.exceptionOrNull()
            exception.shouldBeInstanceOf<CancellationException>()
        }

        should("return Error and handle null tempFile when createTempFile throws IOException") {
            val notADir = File(cacheDir, "notADir").also { it.createNewFile() }
            every { context.cacheDir } returns notADir
            val result = useCase(uri)
            result shouldBe ApkProcessResult.Error
        }

        should("return Success with sourceDir set when applicationInfo is non-null") {
            every { contentResolver.openInputStream(any()) } returns "fake content".byteInputStream()
            val fakeAppInfo = ApplicationInfo()
            val fakePackageInfo = PackageInfo().also { it.applicationInfo = fakeAppInfo }
            every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns fakePackageInfo
            val result = useCase(uri)
            val success = result.shouldBeInstanceOf<ApkProcessResult.Success>()
            success.applicationInfo.sourceDir.isNotEmpty() shouldBe true
            success.applicationInfo.sourceDir shouldBe success.applicationInfo.publicSourceDir
        }
    },
)
