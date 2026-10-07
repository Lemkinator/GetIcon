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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

interface ApkImporter {
    /** Creates an empty file in the app cache that receives a picked APK. */
    fun createCacheFile(): File

    /** Opens the picked document at [uri], or returns null when its provider has no content. */
    fun open(uri: Uri): InputStream?

    /** Parses the APK at [apk], or returns null when it holds no application. */
    fun readApplicationInfo(apk: File): ApplicationInfo?
}

class DefaultApkImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApkImporter {
    override fun createCacheFile(): File = File.createTempFile("extractIcon", ".apk", context.cacheDir)

    override fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)

    override fun readApplicationInfo(apk: File): ApplicationInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.applicationInfo
}
