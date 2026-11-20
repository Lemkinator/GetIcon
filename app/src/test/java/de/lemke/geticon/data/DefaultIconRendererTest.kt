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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.reflect.app.SeslApplicationPackageManagerReflector
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.App
import de.lemke.geticon.domain.model.IconKind
import de.lemke.geticon.domain.model.IconStyle
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultIconRendererTest {
    private lateinit var renderer: DefaultIconRenderer

    @Before
    fun setUp() {
        renderer = DefaultIconRenderer(ApplicationProvider.getApplicationContext<App>())
    }

    @Test
    fun `renders the installed app icon at the requested size`() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val result = renderer.render(context.packageManager.getApplicationInfo(context.packageName, 0), plain(size = 256))
        result.bitmap.width shouldBe 256
        result.bitmap.height shouldBe 256
        result.kind shouldBe IconKind.ADAPTIVE
    }

    @Test
    fun `labels the installed app by its label resource`() {
        val context = ApplicationProvider.getApplicationContext<App>()
        renderer.label(context.packageManager.getApplicationInfo(context.packageName, 0)) shouldBe "Get Icon (Debug)"
    }

    @Test
    fun `labels an app without label resource by its package name`() {
        renderer.label(app { ColorDrawable(Color.RED) }) shouldBe "com.example.icon"
    }

    @Test
    fun `draws both adaptive layers unclipped without mask and color`() {
        val result = renderer.render(app { AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE)) }, plain())
        result.kind shouldBe IconKind.ADAPTIVE
        result.bitmap.centerPixel() shouldBe Color.BLUE
        result.bitmap.getPixel(0, 0) shouldBe Color.BLUE
    }

    @Test
    fun `clips an adaptive icon to the icon mask`() {
        val result =
            renderer.render(
                app { AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE)) },
                plain(maskEnabled = true),
            )
        result.bitmap.centerPixel() shouldBe Color.BLUE
        result.bitmap.getPixel(0, 0) shouldBe Color.TRANSPARENT
    }

    @Test
    fun `tints the monochrome layer over the background`() {
        val result =
            renderer.render(
                app { AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE), ColorDrawable(Color.TRANSPARENT)) },
                tinted(),
            )
        result.kind shouldBe IconKind.ADAPTIVE
        result.bitmap.centerPixel() shouldBe Color.YELLOW
    }

    @Test
    fun `tints the foreground layer when the icon has no monochrome layer`() {
        val result =
            renderer.render(app { AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE)) }, tinted())
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `fills a transparent background color instead of drawing the app background`() {
        val result = renderer.render(app { glyphIcon() }, filled(backgroundColor = Color.TRANSPARENT))
        result.bitmap.getPixel(0, 0) shouldBe Color.TRANSPARENT
        result.bitmap.getPixel(10, 32) shouldBe Color.TRANSPARENT
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `fills an opaque background color instead of drawing the app background`() {
        val result = renderer.render(app { glyphIcon() }, filled(backgroundColor = Color.MAGENTA))
        result.bitmap.getPixel(0, 0) shouldBe Color.MAGENTA
        result.bitmap.getPixel(10, 32) shouldBe Color.MAGENTA
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `blends a half transparent background color`() {
        val result = renderer.render(app { glyphIcon() }, filled(backgroundColor = 0x7F0000FF))
        result.bitmap.getPixel(10, 32) shouldBe 0x7F0000FF
    }

    @Test
    fun `fills the background color only inside the icon mask`() {
        val result = renderer.render(app { glyphIcon() }, filled(backgroundColor = Color.MAGENTA, maskEnabled = true))
        result.bitmap.getPixel(0, 0) shouldBe Color.TRANSPARENT
        result.bitmap.getPixel(10, 32) shouldBe Color.MAGENTA
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `colors a glyph layer that ignores tints with the foreground color`() {
        val result = renderer.render(app { untintableGlyphIcon() }, filled(backgroundColor = Color.MAGENTA))
        result.bitmap.centerPixel() shouldBe Color.GREEN
        result.bitmap.getPixel(10, 32) shouldBe Color.MAGENTA
    }

    @Test
    fun `colors a glyph layer that carries a transparent tint with the foreground color`() {
        val pretinted = ColorDrawable(Color.BLUE).apply { setTint(Color.TRANSPARENT) }
        val result =
            renderer.render(
                app { AdaptiveIconDrawable(ColorDrawable(Color.RED), pretinted) },
                filled(backgroundColor = Color.MAGENTA),
            )
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `blends a half transparent foreground color over the background color`() {
        val style = filled(backgroundColor = Color.MAGENTA, foregroundColor = 0x8000FF00.toInt())
        val result = renderer.render(app { untintableGlyphIcon() }, style)
        result.bitmap.centerPixel() shouldBe 0xFF7F807F.toInt()
        result.bitmap.getPixel(10, 32) shouldBe Color.MAGENTA
    }

    @Test
    fun `draws no glyph for a fully transparent foreground color`() {
        val style = filled(backgroundColor = Color.MAGENTA, foregroundColor = 0x0000FF00)
        val result = renderer.render(app { untintableGlyphIcon() }, style)
        result.bitmap.centerPixel() shouldBe Color.MAGENTA
        result.bitmap.getPixel(10, 32) shouldBe Color.MAGENTA
    }

    @Test
    fun `keeps the area outside the glyph transparent without background color`() {
        val result = renderer.render(app { untintableGlyphIcon() }, filled(backgroundColor = 0))
        result.bitmap.centerPixel() shouldBe Color.GREEN
        result.bitmap.getPixel(10, 32) shouldBe Color.TRANSPARENT
    }

    @Test
    fun `colors a glyph layer that ignores tints only inside the icon mask`() {
        val result = renderer.render(app { untintableGlyphIcon() }, filled(backgroundColor = Color.MAGENTA, maskEnabled = true))
        result.bitmap.centerPixel() shouldBe Color.GREEN
        result.bitmap.getPixel(0, 0) shouldBe Color.TRANSPARENT
    }

    @Test
    fun `draws the app background untouched without color`() {
        val result = renderer.render(app { glyphIcon() }, plain())
        result.bitmap.getPixel(0, 0) shouldBe Color.RED
        result.bitmap.getPixel(10, 32) shouldBe Color.RED
        result.bitmap.centerPixel() shouldBe Color.BLUE
    }

    @Config(sdk = [32])
    @Test
    fun `tints the foreground layer below Android 13`() {
        val result = renderer.render(app { AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE)) }, tinted())
        result.bitmap.centerPixel() shouldBe Color.GREEN
    }

    @Test
    fun `draws an adaptive icon without background as a whole drawable`() {
        val result = renderer.render(app { AdaptiveIconDrawable(null, ColorDrawable(Color.BLUE)) }, plain(maskEnabled = true))
        result.kind shouldBe IconKind.ADAPTIVE
        result.bitmap.centerPixel() shouldBe Color.BLUE
    }

    @Test
    fun `draws an adaptive icon without foreground as a whole drawable`() {
        val result = renderer.render(app { AdaptiveIconDrawable(ColorDrawable(Color.RED), null) }, plain())
        result.kind shouldBe IconKind.ADAPTIVE
        result.bitmap.centerPixel() shouldBe Color.RED
    }

    @Test
    fun `draws a legacy icon unmasked when no tray icon exists`() {
        val result = renderer.render(app { ColorDrawable(Color.RED) }, plain(maskEnabled = true))
        result.kind shouldBe IconKind.LEGACY
        result.bitmap.getPixel(0, 0) shouldBe Color.RED
    }

    @Test
    fun `draws the tray icon of a maskable legacy icon when masked`() {
        mockkStatic(SeslApplicationPackageManagerReflector::class)
        try {
            every {
                SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray(any(), any(), any())
            } returns ColorDrawable(Color.GREEN)
            val maskable = app { ColorDrawable(Color.RED) }
            renderer.render(maskable, plain(maskEnabled = true)).run {
                kind shouldBe IconKind.MASKABLE
                bitmap.getPixel(0, 0) shouldBe Color.GREEN
            }
            renderer.render(maskable, plain()).bitmap.getPixel(0, 0) shouldBe Color.RED
        } finally {
            unmockkStatic(SeslApplicationPackageManagerReflector::class)
        }
    }

    @Test
    fun `falls back to the file type placeholder when the icon does not load`() {
        val result = renderer.render(app { throw IllegalStateException("forced failure") }, plain(size = 128))
        result.kind shouldBe IconKind.LEGACY
        result.bitmap.width shouldBe 128
        result.bitmap.height shouldBe 128
    }

    @Test
    fun `returns a blank legacy icon when neither icon nor placeholder loads`() {
        mockkStatic(AppCompatResources::class)
        try {
            every { AppCompatResources.getDrawable(any(), any()) } returns null
            val result = renderer.render(app { throw IllegalStateException("forced failure") }, plain(size = 128))
            result.kind shouldBe IconKind.LEGACY
            result.bitmap.width shouldBe 128
            result.bitmap.centerPixel() shouldBe Color.TRANSPARENT
        } finally {
            unmockkStatic(AppCompatResources::class)
        }
    }

    private fun app(icon: () -> Drawable): ApplicationInfo =
        object : ApplicationInfo() {
            override fun loadIcon(pm: PackageManager): Drawable = icon()
        }.apply { packageName = "com.example.icon" }

    private fun plain(
        size: Int = 64,
        maskEnabled: Boolean = false,
    ) = IconStyle(size, maskEnabled, colorEnabled = false, foregroundColor = Color.GREEN, backgroundColor = Color.YELLOW)

    private fun tinted() =
        IconStyle(64, maskEnabled = false, colorEnabled = true, foregroundColor = Color.GREEN, backgroundColor = Color.YELLOW)

    private fun filled(
        backgroundColor: Int,
        maskEnabled: Boolean = false,
        foregroundColor: Int = Color.GREEN,
    ) = IconStyle(64, maskEnabled, colorEnabled = true, foregroundColor = foregroundColor, backgroundColor = backgroundColor)

    private fun glyphIcon() = AdaptiveIconDrawable(UntintableColor(Color.RED), InsetDrawable(ColorDrawable(Color.BLUE), 0.35f))

    private fun untintableGlyphIcon() = AdaptiveIconDrawable(ColorDrawable(Color.RED), InsetDrawable(UntintableColor(Color.BLUE), 0.35f))

    private class UntintableColor(private val color: Int) : ColorDrawable(color) {
        override fun setTintList(tint: ColorStateList?) = Unit

        override fun getConstantState() =
            object : ConstantState() {
                override fun newDrawable(): Drawable = UntintableColor(color)

                override fun getChangingConfigurations() = 0
            }
    }

    private fun Bitmap.centerPixel(): Int = getPixel(width / 2, height / 2)
}
