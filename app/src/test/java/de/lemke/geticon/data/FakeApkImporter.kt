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

import android.content.pm.ApplicationInfo
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.io.path.createTempDirectory

/**
 * Serves the documents added for a [Uri] and parses a cached file as an APK when it holds the content of [addApk].
 * Records every opened [Uri]; [beforeReading] runs before each parse.
 */
internal class FakeApkImporter(
    val cacheDir: File = createTempDirectory("fakeApkImporter").toFile(),
) : ApkImporter {
    private val documents = mutableMapOf<Uri, () -> InputStream>()
    val opened = mutableListOf<Uri>()
    var beforeReading: () -> Unit = {}

    fun addApk(
        uri: Uri,
        packageName: String,
    ) {
        documents[uri] = { "$APK_HEADER$packageName".byteInputStream() }
    }

    fun addDocument(
        uri: Uri,
        content: String,
    ) {
        documents[uri] = { content.byteInputStream() }
    }

    fun addUnreadable(
        uri: Uri,
        failure: Exception,
    ) {
        documents[uri] = { throw failure }
    }

    fun cachedFiles(): List<File> = cacheDir.listFiles().orEmpty().toList()

    override fun createCacheFile(): File = File.createTempFile("extractIcon", ".apk", cacheDir)

    override fun open(uri: Uri): InputStream? {
        opened += uri
        return documents[uri]?.invoke()
    }

    override fun readApplicationInfo(apk: File): ApplicationInfo? {
        beforeReading()
        val content = apk.readText()
        if (!content.startsWith(APK_HEADER)) return null
        return ApplicationInfo().apply { packageName = content.removePrefix(APK_HEADER) }
    }

    private companion object {
        const val APK_HEADER = "apk:"
    }
}
