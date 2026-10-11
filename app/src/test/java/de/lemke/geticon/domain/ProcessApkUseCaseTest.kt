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

import android.net.Uri
import de.lemke.geticon.data.FakeApkImporter
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessApkUseCaseTest : ShouldSpec(
    {
        val uri = mockk<Uri>()
        lateinit var importer: FakeApkImporter
        lateinit var useCase: ProcessApkUseCase

        beforeEach {
            importer = FakeApkImporter()
            useCase = ProcessApkUseCase(importer, UnconfinedTestDispatcher())
        }

        afterEach { importer.cacheDir.deleteRecursively() }

        should("return Success with the cached APK as source when the document is an APK") {
            importer.addApk(uri, "com.example.app")
            val success = useCase(uri).shouldBeInstanceOf<ApkProcessResult.Success>()
            val cached = importer.cachedFiles().single()
            success.applicationInfo.packageName shouldBe "com.example.app"
            success.applicationInfo.sourceDir shouldBe cached.absolutePath
            success.applicationInfo.publicSourceDir shouldBe cached.absolutePath
            cached.readText() shouldBe "apk:com.example.app"
        }

        should("return Error and delete the cache file when the provider has no content") {
            useCase(uri) shouldBe ApkProcessResult.Error
            importer.opened shouldBe listOf(uri)
            importer.cachedFiles() shouldBe emptyList()
        }

        should("return InvalidApk and delete the cache file when the document is no APK") {
            importer.addDocument(uri, "fake content")
            useCase(uri) shouldBe ApkProcessResult.InvalidApk
            importer.cachedFiles() shouldBe emptyList()
        }

        listOf(IOException("stream error"), SecurityException("no permission")).forEach { failure ->
            should("return Error and delete the cache file when opening throws ${failure::class.simpleName}") {
                importer.addUnreadable(uri, failure)
                useCase(uri) shouldBe ApkProcessResult.Error
                importer.cachedFiles() shouldBe emptyList()
            }
        }

        should("return Error and delete the cache file when parsing throws") {
            importer.addApk(uri, "com.example.app")
            importer.beforeReading = { throw IllegalStateException("parse error") }
            useCase(uri) shouldBe ApkProcessResult.Error
            importer.cachedFiles() shouldBe emptyList()
        }

        should("return Error when the cache file cannot be created") {
            val blocked = FakeApkImporter(File(importer.cacheDir, "notADir").also { it.createNewFile() })
            ProcessApkUseCase(blocked, UnconfinedTestDispatcher())(uri) shouldBe ApkProcessResult.Error
            blocked.opened shouldBe emptyList()
        }

        should("rethrow a CancellationException from opening instead of returning Error") {
            importer.addUnreadable(uri, CancellationException("cancelled"))
            runCatching { useCase(uri) }.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
            importer.cachedFiles() shouldBe emptyList()
        }

        should("delete the cache file when parsing is cancelled") {
            importer.addApk(uri, "com.example.app")
            importer.beforeReading = { throw CancellationException("cancelled") }
            runCatching { useCase(uri) }.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
            importer.cachedFiles() shouldBe emptyList()
        }

        should("delete the cache file when the caller is cancelled before a Success is delivered") {
            importer.addApk(uri, "com.example.app")
            lateinit var caller: Job
            importer.beforeReading = { caller.cancel() }
            coroutineScope {
                caller = launch(start = CoroutineStart.LAZY) { useCase(uri) }
                caller.join()
            }
            caller.isCancelled shouldBe true
            importer.cachedFiles() shouldBe emptyList()
        }

        should("open nothing when the caller is cancelled before processing starts") {
            importer.addApk(uri, "com.example.app")
            val caller =
                coroutineScope {
                    launch {
                        coroutineContext[Job]?.cancel()
                        useCase(uri)
                    }
                }
            caller.isCancelled shouldBe true
            importer.opened shouldBe emptyList()
            importer.cachedFiles() shouldBe emptyList()
        }
    },
)
