package de.lemke.geticon.domain

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.reflect.app.SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class IconResult(
    val bitmap: Bitmap,
    val isAdaptiveIcon: Boolean,
    val hasMaskedAppIcon: Boolean,
)

class GenerateIconUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @SuppressLint("RestrictedApi")
    suspend operator fun invoke(
        applicationInfo: ApplicationInfo,
        size: Int,
        maskEnabled: Boolean,
        colorEnabled: Boolean,
        foregroundColor: Int,
        backgroundColor: Int,
        packageManager: PackageManager,
    ): IconResult = withContext(Dispatchers.Default) {
        val appIcon: Drawable = try {
            applicationInfo.loadIcon(packageManager)
        } catch (_: Exception) {
            AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_file_type_image)!!
        }
        val maskedAppIcon = semGetApplicationIconForIconTray(packageManager, applicationInfo.packageName, 1)
        val isAdaptiveIcon = appIcon is AdaptiveIconDrawable
        val hasMaskedAppIcon = isAdaptiveIcon || maskedAppIcon != null

        val drawable = appIcon.mutate()
        val bitmap: Bitmap
        if (drawable is AdaptiveIconDrawable && drawable.foreground != null && drawable.background != null) {
            bitmap = drawable.toBitmap(size, size)
            drawable.setBounds(0, 0, size, size)
            val background = drawable.background.mutate()
            var foreground = drawable.foreground.mutate()
            if (colorEnabled) {
                if (SDK_INT >= TIRAMISU) {
                    val monochrome = drawable.monochrome
                    if (monochrome != null) foreground = monochrome.mutate()
                }
                background.setTint(backgroundColor)
                foreground.setTint(foregroundColor)
            }
            val canvas = Canvas(bitmap)
            if (maskEnabled) canvas.clipPath(drawable.iconMask)
            background.draw(canvas)
            foreground.draw(canvas)
        } else {
            bitmap = if (maskEnabled && hasMaskedAppIcon) {
                maskedAppIcon?.toBitmap(size, size) ?: appIcon.toBitmap(size, size)
            } else {
                drawable.toBitmap(size, size)
            }
        }
        IconResult(bitmap, isAdaptiveIcon, hasMaskedAppIcon)
    }
}
