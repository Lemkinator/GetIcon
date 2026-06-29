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

package de.lemke.geticon.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import de.lemke.geticon.bypassOobe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.oneuiproject.oneui.R as ouiR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
        ApplicationProvider.getApplicationContext<HiltTestApplication>().initCommonUtilsSettingsAndSetDarkMode()
        commonUtilsSettings.bypassOobe()
        installFakeApps()
    }

    @Test
    fun mainActivity_default() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/main_default.png")
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun mainActivity_default_dark() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(isRoot()).captureRoboImage("src/test/screenshots/main_default_dark.png")
        }
    }

    private data class FakeApp(val label: String, val packageName: String, val bgColor: Int, val iconRes: Int)

    companion object {
        private const val ICON_SIZE = 192

        @Suppress("MagicNumber")
        private val FAKE_APPS =
            listOf(
                FakeApp("OneURL", "de.lemke.oneurl", 0xFF8766C5.toInt(), ouiR.drawable.ic_oui_open_split_view),
                FakeApp("Sudoku", "de.lemke.sudoku", 0xFFCF7200.toInt(), ouiR.drawable.ic_oui_list_grid),
                FakeApp("NAKBuch", "de.lemke.nakbuch", 0xFF6F9DD1.toInt(), ouiR.drawable.ic_oui_audio_outline),
                FakeApp("OneUI Sample", "de.lemke.oneui.sample", 0xFF0381FE.toInt(), ouiR.drawable.ic_oui_labs_outline),
                FakeApp("WhatZap", "com.whatzap.android", 0xFF25D366.toInt(), ouiR.drawable.ic_oui_message_outline),
                FakeApp("Faceplant", "io.faceplant.app", 0xFF1877F2.toInt(), ouiR.drawable.ic_oui_biometric_type_face),
                FakeApp("Instasham", "co.instasham", 0xFFC13584.toInt(), ouiR.drawable.ic_oui_image),
                FakeApp("SnackChat", "com.snackchat.android", 0xFFD1BB15.toInt(), ouiR.drawable.ic_oui_creatures_outline),
                FakeApp("Xitter", "com.xitter.android", 0xFF050505.toInt(), ouiR.drawable.ic_oui_share),
                FakeApp("LinkedOut", "com.linkedout.droid", 0xFF0A66C2.toInt(), ouiR.drawable.ic_oui_contact_outline),
                FakeApp("YouToob", "com.youtoob.android", 0xFFFF0000.toInt(), ouiR.drawable.ic_oui_control_play_circle_filled),
                FakeApp("Glitch", "tv.glitch.android", 0xFF9146FF.toInt(), ouiR.drawable.ic_oui_game),
                FakeApp("Spotifly", "com.spotifly.music", 0xFF1DB954.toInt(), ouiR.drawable.ic_oui_sound_outline),
                FakeApp("HomeResist", "io.homeresist.companion", 0xFF38B2D8.toInt(), ouiR.drawable.ic_oui_home_outline),
                FakeApp("Clod", "com.clod.ai", 0xFFD97C4B.toInt(), ouiR.drawable.ic_oui_message_bot),
                FakeApp("Calculator", "com.android.calculator2", 0xFF4CAF50.toInt(), ouiR.drawable.ic_oui_calculation),
                FakeApp("Files", "com.android.documentsui", 0xFFFF9800.toInt(), ouiR.drawable.ic_oui_file_type_folder),
                FakeApp("Camera", "com.android.camera2", 0xFF212121.toInt(), ouiR.drawable.ic_oui_camera),
                FakeApp("Browser", "com.android.chrome", 0xFF1565C0.toInt(), ouiR.drawable.ic_oui_internet_website),
                FakeApp("Phone", "com.android.dialer", 0xFF00BCD4.toInt(), ouiR.drawable.ic_oui_during_call_outline),
                FakeApp("Contacts", "com.android.contacts", 0xFF009688.toInt(), ouiR.drawable.ic_oui_contact),
                FakeApp("Clock", "com.android.deskclock", 0xFFFF5722.toInt(), ouiR.drawable.ic_oui_time),
                FakeApp("Gallery", "com.android.gallery3d", 0xFF9C27B0.toInt(), ouiR.drawable.ic_oui_image_visual),
                FakeApp("Messages", "com.android.mms", 0xFF3F51B5.toInt(), ouiR.drawable.ic_oui_message_all_read),
                FakeApp("Email", "com.android.email", 0xFFF44336.toInt(), ouiR.drawable.ic_oui_email),
                FakeApp("Maps", "com.google.android.apps.maps", 0xFF00C853.toInt(), ouiR.drawable.ic_oui_location),
                FakeApp("Settings", "com.android.settings", 0xFF607D8B.toInt(), ouiR.drawable.ic_oui_settings_outline),
            )
    }

    @Suppress("DEPRECATION")
    private fun installFakeApps() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(context.packageManager)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowPm.setResolveInfosForIntent(
            launcherIntent,
            FAKE_APPS.map { app ->
                ResolveInfo().apply {
                    nonLocalizedLabel = app.label
                    activityInfo =
                        ActivityInfo().apply {
                            packageName = app.packageName
                            name = "${app.packageName}.MainActivity"
                            applicationInfo =
                                ApplicationInfo().apply {
                                    packageName = app.packageName
                                    nonLocalizedLabel = app.label
                                    flags = ApplicationInfo.FLAG_INSTALLED
                                }
                        }
                }
            },
        )
        FAKE_APPS.forEach { app ->
            val bitmap = makeIcon(context, app.bgColor, app.iconRes)
            shadowPm.addActivityIcon(
                ComponentName(app.packageName, "${app.packageName}.MainActivity"),
                BitmapDrawable(context.resources, bitmap),
            )
        }
    }

    @Suppress("MagicNumber")
    private fun makeIcon(
        context: Context,
        bgColor: Int,
        @DrawableRes iconResId: Int,
    ): Bitmap {
        val size = ICON_SIZE
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawPath(squirclePath(), paint)
        val iconPad = size / 4
        AppCompatResources.getDrawable(context, iconResId)?.apply {
            setBounds(iconPad, iconPad, size - iconPad, size - iconPad)
            setTint(Color.WHITE)
            draw(canvas)
        }
        return bitmap
    }

    @Suppress("MagicNumber")
    private fun squirclePath(): Path {
        // Squircle cubic bezier from ic_splash.xml (100×100 viewport), scaled to bitmap size.
        val s = ICON_SIZE / 100f
        return Path().apply {
            moveTo(99f * s, 50f * s)
            cubicTo(99f * s, 6.935f * s, 77.063f * s, 1f * s, 50f * s, 1f * s)
            cubicTo(22.935f * s, 1f * s, 1f * s, 6.935f * s, 1f * s, 50f * s)
            cubicTo(1f * s, 93.063f * s, 22.935f * s, 99f * s, 50f * s, 99f * s)
            cubicTo(77.063f * s, 99f * s, 99f * s, 93.063f * s, 99f * s, 50f * s)
            close()
        }
    }
}
