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

package de.lemke.geticon.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory

class DefaultApkImporterTest : ShouldSpec(
    {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val packageManager = mockk<PackageManager>()
        val importer = DefaultApkImporter(context)
        lateinit var cacheDir: File

        beforeEach {
            cacheDir = createTempDirectory("defaultApkImporter").toFile()
            every { context.cacheDir } returns cacheDir
            every { context.contentResolver } returns contentResolver
            every { context.packageManager } returns packageManager
        }

        afterEach { cacheDir.deleteRecursively() }

        should("create an empty APK file in the app cache") {
            val file = importer.createCacheFile()
            file.parentFile shouldBe cacheDir
            file.name shouldStartWith "extractIcon"
            file.name shouldEndWith ".apk"
            file.length() shouldBe 0L
        }

        should("open the picked document through the content resolver") {
            val uri = mockk<Uri>()
            every { contentResolver.openInputStream(uri) } returns "apk bytes".byteInputStream()
            importer.open(uri)?.reader()?.readText() shouldBe "apk bytes"
        }

        should("return null when the provider has no content") {
            val uri = mockk<Uri>()
            every { contentResolver.openInputStream(uri) } returns null
            importer.open(uri) shouldBe null
        }

        should("read the application of a parsed archive") {
            val apk = File(cacheDir, "app.apk")
            val applicationInfo = ApplicationInfo()
            every { packageManager.getPackageArchiveInfo(apk.absolutePath, 0) } returns
                PackageInfo().also { it.applicationInfo = applicationInfo }
            importer.readApplicationInfo(apk) shouldBe applicationInfo
        }

        should("return null for an archive without application") {
            val apk = File(cacheDir, "app.apk")
            every { packageManager.getPackageArchiveInfo(apk.absolutePath, 0) } returns PackageInfo()
            importer.readApplicationInfo(apk) shouldBe null
        }

        should("return null for a file that is no archive") {
            val apk = File(cacheDir, "app.apk")
            every { packageManager.getPackageArchiveInfo(apk.absolutePath, 0) } returns null
            importer.readApplicationInfo(apk) shouldBe null
        }
    },
)
