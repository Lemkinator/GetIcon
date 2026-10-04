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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.IoDispatcher
import java.io.File
import java.io.FileOutputStream
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
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(uri: Uri): ApkProcessResult {
        var tempFile: File? = null
        val result =
            try {
                withContext(ioDispatcher) { importApk(uri) { tempFile = it } }
            } catch (e: CancellationException) {
                tempFile?.delete()
                throw e
            }
        if (result !is ApkProcessResult.Success) tempFile?.delete()
        return result
    }

    @Suppress("TooGenericExceptionCaught")
    private fun importApk(
        uri: Uri,
        onTempFileCreated: (File) -> Unit,
    ): ApkProcessResult =
        try {
            val file = File.createTempFile("extractIcon", ".apk", context.cacheDir).also(onTempFileCreated)
            val stream = context.contentResolver.openInputStream(uri) ?: return ApkProcessResult.Error
            stream.use { input -> FileOutputStream(file).use { out -> input.copyTo(out) } }
            val path = file.absolutePath
            val applicationInfo =
                context.packageManager.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return ApkProcessResult.InvalidApk
            applicationInfo.sourceDir = path
            applicationInfo.publicSourceDir = path
            ApkProcessResult.Success(applicationInfo)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ApkProcessResult.Error
        }
}
