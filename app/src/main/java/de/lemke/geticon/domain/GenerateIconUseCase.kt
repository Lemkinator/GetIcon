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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.reflect.app.SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.di.DefaultDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class IconResult(
    val bitmap: Bitmap,
    val isAdaptiveIcon: Boolean,
    val hasMaskedAppIcon: Boolean,
)

class GenerateIconUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
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
    ): IconResult =
        withContext(defaultDispatcher) {
            val appIcon: Drawable =
                try {
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
                bitmap = createBitmap(size, size)
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
                bitmap =
                    if (maskEnabled && maskedAppIcon != null) {
                        maskedAppIcon.toBitmap(size, size)
                    } else {
                        drawable.toBitmap(size, size)
                    }
            }
            IconResult(bitmap, isAdaptiveIcon, hasMaskedAppIcon)
        }
}
