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

import android.content.pm.ApplicationInfo
import android.net.Uri
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.geticon.data.ApkImporter
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed class ApkProcessResult {
    data class Success(val applicationInfo: ApplicationInfo) : ApkProcessResult()

    data object InvalidApk : ApkProcessResult()

    data object Error : ApkProcessResult()
}

class ProcessApkUseCase @Inject constructor(
    private val importer: ApkImporter,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(uri: Uri): ApkProcessResult {
        var tempFile: File? = null
        val result =
            try {
                withContext(ioDispatcher) { importApk(uri) { tempFile = it } }
            } catch (e: CancellationException) {
                tempFile?.let(importer::discard)
                throw e
            }
        if (result !is ApkProcessResult.Success) tempFile?.let(importer::discard)
        return result
    }

    @Suppress("TooGenericExceptionCaught")
    private fun importApk(
        uri: Uri,
        onTempFileCreated: (File) -> Unit,
    ): ApkProcessResult =
        try {
            val file = importer.createCacheFile().also(onTempFileCreated)
            val stream = importer.open(uri) ?: return ApkProcessResult.Error
            stream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            val applicationInfo = importer.readApplicationInfo(file) ?: return ApkProcessResult.InvalidApk
            val path = file.absolutePath
            applicationInfo.sourceDir = path
            applicationInfo.publicSourceDir = path
            ApkProcessResult.Success(applicationInfo)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ApkProcessResult.Error
        }
}
