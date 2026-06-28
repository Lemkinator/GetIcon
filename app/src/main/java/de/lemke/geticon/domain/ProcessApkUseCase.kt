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

private const val MAX_APK_BYTES = 512L * 1024 * 1024 // 512 MB

sealed class ApkProcessResult {
    data class Success(val applicationInfo: ApplicationInfo) : ApkProcessResult()

    data object InvalidApk : ApkProcessResult()

    data object Error : ApkProcessResult()
}

class ProcessApkUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri): ApkProcessResult =
        withContext(ioDispatcher) {
            var tempFile: File? = null
            try {
                tempFile = File.createTempFile("extractIcon", ".apk", context.cacheDir)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    tempFile.delete()
                    return@withContext ApkProcessResult.Error
                }
                stream.use { input ->
                    FileOutputStream(tempFile).use { out ->
                        var total = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var n: Int
                        while (input.read(buffer).also { n = it } >= 0) {
                            total += n
                            if (total > MAX_APK_BYTES) {
                                tempFile.delete()
                                return@withContext ApkProcessResult.Error
                            }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                val path = tempFile.absolutePath
                val applicationInfo = context.packageManager.getPackageArchiveInfo(path, 0)?.applicationInfo
                if (applicationInfo == null) {
                    tempFile.delete()
                    return@withContext ApkProcessResult.InvalidApk
                }
                applicationInfo.sourceDir = path
                applicationInfo.publicSourceDir = path
                ApkProcessResult.Success(applicationInfo)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tempFile?.delete()
                ApkProcessResult.Error
            }
        }
}
