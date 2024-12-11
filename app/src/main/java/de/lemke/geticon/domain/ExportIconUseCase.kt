package de.lemke.geticon.domain


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ActivityContext
import de.lemke.commonutils.toSafeFileName
import de.lemke.commonutils.toast
import de.lemke.geticon.R
import javax.inject.Inject

class ExportIconUseCase @Inject constructor(
    @ActivityContext private val context: Context,
) {
    operator fun invoke(uri: Uri?, icon: Bitmap, name: String) {
        if (uri == null) {
            context.toast(R.string.error_no_folder_selected)
            return
        }
        val pngFile = DocumentFile.fromTreeUri(context, uri)!!.createFile("image/png", name.toSafeFileName(".png"))
        val os = pngFile?.uri?.let { context.contentResolver.openOutputStream(it) }
        if (pngFile == null || os == null) {
            context.toast(R.string.error_creating_file)
            return
        }
        icon.compress(Bitmap.CompressFormat.PNG, 100, os)
        context.toast(R.string.icon_saved)
    }
}