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
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.reflect.app.SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import de.lemke.geticon.domain.model.RenderedIcon
import javax.inject.Inject

interface IconRenderer {
    /** Loads the label of [applicationInfo], falling back to its package name. */
    fun label(applicationInfo: ApplicationInfo): String

    /** Loads the icon of [applicationInfo] and draws it into a bitmap in [style]. */
    fun render(
        applicationInfo: ApplicationInfo,
        style: IconStyle,
    ): RenderedIcon
}

class DefaultIconRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IconRenderer {
    override fun label(applicationInfo: ApplicationInfo): String = applicationInfo.loadLabel(context.packageManager).toString()

    @SuppressLint("RestrictedApi")
    override fun render(
        applicationInfo: ApplicationInfo,
        style: IconStyle,
    ): RenderedIcon {
        val size = style.size
        val packageManager = context.packageManager
        val appIcon = loadIcon(applicationInfo, packageManager) ?: return RenderedIcon(createBitmap(size, size), IconKind.LEGACY)
        val maskedAppIcon = semGetApplicationIconForIconTray(packageManager, applicationInfo.packageName, 1)
        val kind =
            when {
                appIcon is AdaptiveIconDrawable -> IconKind.ADAPTIVE
                maskedAppIcon != null -> IconKind.MASKABLE
                else -> IconKind.LEGACY
            }
        val drawable = appIcon.mutate()
        val bitmap =
            when {
                drawable is AdaptiveIconDrawable && drawable.foreground != null && drawable.background != null -> drawable.drawLayers(style)
                style.maskEnabled && maskedAppIcon != null -> maskedAppIcon.toBitmap(size, size)
                else -> drawable.toBitmap(size, size)
            }
        return RenderedIcon(bitmap, kind)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadIcon(
        applicationInfo: ApplicationInfo,
        packageManager: PackageManager,
    ): Drawable? =
        try {
            applicationInfo.loadIcon(packageManager)
        } catch (e: Exception) {
            Log.w("DefaultIconRenderer", "loadIcon failed for ${applicationInfo.packageName}", e)
            AppCompatResources.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_file_type_image)
        }

    private fun AdaptiveIconDrawable.drawLayers(style: IconStyle): Bitmap {
        val bitmap = createBitmap(style.size, style.size)
        setBounds(0, 0, style.size, style.size)
        val background = background.mutate()
        var foreground = foreground.mutate()
        if (style.colorEnabled) {
            if (SDK_INT >= TIRAMISU) monochrome?.let { foreground = it.mutate() }
            background.setTint(style.backgroundColor)
            foreground.setTint(style.foregroundColor)
        }
        val canvas = Canvas(bitmap)
        if (style.maskEnabled) canvas.clipPath(iconMask)
        background.draw(canvas)
        foreground.draw(canvas)
        return bitmap
    }
}
