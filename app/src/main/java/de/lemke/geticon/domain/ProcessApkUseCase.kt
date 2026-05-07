package de.lemke.geticon.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ProcessApkUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(uri: Uri): ApplicationInfo? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("extractIcon", ".apk", context.cacheDir)
            context.contentResolver.openInputStream(uri).use { it?.copyTo(FileOutputStream(tempFile)) }
            val path = tempFile.absolutePath
            val applicationInfo = context.packageManager.getPackageArchiveInfo(path, 0)?.applicationInfo
            applicationInfo?.apply {
                sourceDir = path
                publicSourceDir = path
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
