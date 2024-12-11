package de.lemke.geticon.domain


import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.commonutils.toSafeFileName
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import de.lemke.geticon.data.SaveLocation
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import javax.inject.Inject

class ExportIconToSaveLocationUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(saveLocation: SaveLocation, qrCode: Bitmap, name: String) {
        try {
            val dir: String = when (saveLocation) {
                SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
                SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
                SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
                SaveLocation.CUSTOM -> Environment.DIRECTORY_PICTURES // should never happen
            }
            val fileName = name.toSafeFileName(".png")
            val os: OutputStream = Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), fileName).toPath())
            qrCode.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.close()
            context.toast(context.getString(R.string.icon_saved) + ": ${saveLocation.toLocalizedString(context)}")
        } catch (e: IOException) {
            context.toast(R.string.error_creating_file)
            Log.e("ExportIconToSaveLocationUseCase", e.message.toString())
            e.printStackTrace()
        }
    }
}