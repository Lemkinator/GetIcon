package de.lemke.geticon.domain


import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.geticon.R
import de.lemke.geticon.data.SaveLocation
import dev.oneuiproject.oneui.widget.Toast
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportIconToSaveLocationUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(saveLocation: SaveLocation, qrCode: Bitmap, name: String) {
        val fileName = "${name}_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())}.png"
        val dir: String = when (saveLocation) {
            SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
            SaveLocation.PICTURES -> Environment.DIRECTORY_PICTURES
            SaveLocation.DCIM -> Environment.DIRECTORY_DCIM
            SaveLocation.CUSTOM -> Environment.DIRECTORY_PICTURES // should never happen
        }
        try {
            val os: OutputStream = Files.newOutputStream(File(Environment.getExternalStoragePublicDirectory(dir), fileName).toPath())
            qrCode.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.close()
        } catch (e: IOException) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
        Toast.makeText(context, context.getString(R.string.icon_saved) + ": ${saveLocation.toLocalizedString(context)}", Toast.LENGTH_SHORT).show()
    }
}