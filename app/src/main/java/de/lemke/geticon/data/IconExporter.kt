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

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.createBitmapClip
import de.lemke.commonutils.ui.utils.createBitmapShareFile
import de.lemke.commonutils.ui.utils.saveBitmapToDirectory
import de.lemke.commonutils.ui.utils.saveBitmapToUri
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

interface IconExporter {
    suspend fun saveToDirectory(
        location: SaveLocation,
        icon: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult

    /** Deletes the created document unless [icon] is written to it. */
    suspend fun saveToCreatedDocument(
        uri: Uri,
        icon: Bitmap?,
    ): BitmapSaveResult.UriResult

    suspend fun createClip(icon: Bitmap): ClipData?

    suspend fun createShareFile(icon: Bitmap): BitmapShareFile
}

class DefaultIconExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : IconExporter {
    override suspend fun saveToDirectory(
        location: SaveLocation,
        icon: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult = saveBitmapToDirectory(location, icon, fileName, ioDispatcher)

    override suspend fun saveToCreatedDocument(
        uri: Uri,
        icon: Bitmap?,
    ): BitmapSaveResult.UriResult = context.saveBitmapToUri(uri, icon, createdDocument = true, ioDispatcher)

    override suspend fun createClip(icon: Bitmap): ClipData? = context.createBitmapClip(icon, CLIP_LABEL, FILE_NAME, ioDispatcher)

    override suspend fun createShareFile(icon: Bitmap): BitmapShareFile = context.createBitmapShareFile(icon, FILE_NAME, ioDispatcher)

    private companion object {
        const val CLIP_LABEL = "icon"
        const val FILE_NAME = "icon.png"
    }
}
