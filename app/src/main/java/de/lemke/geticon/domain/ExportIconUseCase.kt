package de.lemke.geticon.domain


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.geticon.R
import dev.oneuiproject.oneui.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportIconUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(uri: Uri?, icon: Bitmap, name: String) {
        if (uri == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_no_folder_selected),
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.GERMANY).format(Date())
        val pngFile = DocumentFile.fromTreeUri(context, uri)!!.createFile("image/png", "${name}_$timestamp")
        icon.compress(Bitmap.CompressFormat.PNG, 100, context.contentResolver.openOutputStream(pngFile!!.uri)!!)
        Toast.makeText(context, R.string.icon_saved, Toast.LENGTH_SHORT).show()
    }
}