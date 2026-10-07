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
import android.graphics.Bitmap
import android.net.Uri
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import kotlinx.coroutines.CompletableDeferred

internal class FakeIconExporter : IconExporter {
    var directoryResult: BitmapSaveResult.DirectoryResult = BitmapSaveResult.Saved(SaveLocation.DOWNLOADS)
    var documentResult: BitmapSaveResult.UriResult = BitmapSaveResult.Saved(SaveLocation.CUSTOM)
    var clip: ClipData? = null
    var shareFile: BitmapShareFile = BitmapShareFile.Failed

    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<Call>()

    override suspend fun saveToDirectory(
        location: SaveLocation,
        icon: Bitmap,
        fileName: String,
    ): BitmapSaveResult.DirectoryResult = record(Call.SaveToDirectory(location, icon, fileName)) { directoryResult }

    override suspend fun saveToCreatedDocument(
        uri: Uri,
        icon: Bitmap?,
    ): BitmapSaveResult.UriResult = record(Call.SaveToCreatedDocument(uri, icon)) { documentResult }

    override suspend fun createClip(icon: Bitmap): ClipData? = record(Call.CreateClip(icon)) { clip }

    override suspend fun createShareFile(icon: Bitmap): BitmapShareFile = record(Call.CreateShareFile(icon)) { shareFile }

    private suspend fun <T> record(
        call: Call,
        result: () -> T,
    ): T {
        calls += call
        gate?.await()
        return result()
    }

    sealed interface Call {
        data class SaveToDirectory(
            val location: SaveLocation,
            val icon: Bitmap,
            val fileName: String,
        ) : Call

        data class SaveToCreatedDocument(
            val uri: Uri,
            val icon: Bitmap?,
        ) : Call

        data class CreateClip(
            val icon: Bitmap,
        ) : Call

        data class CreateShareFile(
            val icon: Bitmap,
        ) : Call
    }
}
