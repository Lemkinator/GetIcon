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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.reflect.app.SeslApplicationPackageManagerReflector
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.App
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateIconUseCaseTest {
    private lateinit var useCase: GenerateIconUseCase
    private lateinit var appInfo: ApplicationInfo
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<App>()
        useCase = GenerateIconUseCase(context)
        packageManager = context.packageManager
        appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
    }

    @Test
    fun `returns non-null bitmap for test app icon`() =
        runTest {
            val result = useCase(appInfo, 128, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
        }

    @Test
    fun `bitmap has requested size`() =
        runTest {
            val size = 256
            val result = useCase(appInfo, size, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
            result.bitmap.width shouldBe size
            result.bitmap.height shouldBe size
        }

    @Test
    fun `maskEnabled does not crash for adaptive icon`() =
        runTest {
            val result = useCase(appInfo, 128, maskEnabled = true, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
        }

    @Test
    fun `colorEnabled does not crash and returns bitmap`() =
        runTest {
            val result =
                useCase(
                    appInfo,
                    128,
                    maskEnabled = false,
                    colorEnabled = true,
                    foregroundColor = 0xFFFF0000.toInt(),
                    backgroundColor = 0xFF0000FF.toInt(),
                    packageManager,
                )
            result.bitmap shouldNotBe null
        }

    @Test
    fun `colorEnabled with maskEnabled does not crash`() =
        runTest {
            val result =
                useCase(
                    appInfo,
                    128,
                    maskEnabled = true,
                    colorEnabled = true,
                    foregroundColor = 0xFFFF0000.toInt(),
                    backgroundColor = 0xFF0000FF.toInt(),
                    packageManager,
                )
            result.bitmap shouldNotBe null
        }

    @SuppressLint("NewApi")
    @Test
    fun `colorEnabled applies tint to monochrome layer when icon has monochrome`() =
        runTest {
            val monochromeInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable =
                        AdaptiveIconDrawable(
                            ColorDrawable(Color.RED),
                            ColorDrawable(Color.BLUE),
                            ColorDrawable(Color.WHITE),
                        )
                }.apply { packageName = "de.lemke.geticon" }
            val result =
                useCase(
                    monochromeInfo,
                    128,
                    maskEnabled = false,
                    colorEnabled = true,
                    foregroundColor = 0xFFFF0000.toInt(),
                    backgroundColor = 0xFF0000FF.toInt(),
                    packageManager,
                )
            result.bitmap shouldNotBe null
            result.isAdaptiveIcon shouldBe true
        }

    @Test
    fun `AdaptiveIconDrawable with null background falls through to else branch`() =
        runTest {
            val nullBgInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable = AdaptiveIconDrawable(null, ColorDrawable(Color.BLUE))
                }.apply { packageName = "de.lemke.geticon" }
            val result = useCase(nullBgInfo, 128, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
            result.isAdaptiveIcon shouldBe true
        }

    @Test
    fun `AdaptiveIconDrawable with null foreground falls through to else branch`() =
        runTest {
            val nullFgInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable = AdaptiveIconDrawable(ColorDrawable(Color.RED), null)
                }.apply { packageName = "de.lemke.geticon" }
            val result = useCase(nullFgInfo, 128, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
            result.isAdaptiveIcon shouldBe true
        }

    @Test
    fun `AdaptiveIconDrawable with null background and maskEnabled uses appIcon fallback`() =
        runTest {
            val nullBgInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable = AdaptiveIconDrawable(null, ColorDrawable(Color.BLUE))
                }.apply { packageName = "de.lemke.geticon" }
            val result = useCase(nullBgInfo, 128, maskEnabled = true, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
        }

    @Test
    fun `non-adaptive icon with maskEnabled true and no maskedAppIcon uses drawable toBitmap`() =
        runTest {
            val nonAdaptiveInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable = ColorDrawable(Color.RED)
                }.apply { packageName = "de.lemke.geticon" }
            val result = useCase(nonAdaptiveInfo, 128, maskEnabled = true, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
            result.isAdaptiveIcon shouldBe false
        }

    @Config(sdk = [32])
    @Test
    fun `colorEnabled on SDK below TIRAMISU skips monochrome path`() =
        runTest {
            val adaptiveInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable =
                        AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE))
                }.apply { packageName = "de.lemke.geticon" }
            val result =
                useCase(
                    adaptiveInfo,
                    128,
                    maskEnabled = false,
                    colorEnabled = true,
                    foregroundColor = 0xFFFF0000.toInt(),
                    backgroundColor = 0xFF0000FF.toInt(),
                    packageManager,
                )
            result.bitmap shouldNotBe null
        }

    @SuppressLint("NewApi")
    @Test
    fun `colorEnabled on API 33+ with no monochrome layer skips monochrome assignment`() =
        runTest {
            val noMonochromeInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager): Drawable =
                        AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.BLUE))
                }.apply { packageName = "de.lemke.geticon" }
            val result =
                useCase(
                    noMonochromeInfo,
                    128,
                    maskEnabled = false,
                    colorEnabled = true,
                    foregroundColor = 0xFFFF0000.toInt(),
                    backgroundColor = 0xFF0000FF.toInt(),
                    packageManager,
                )
            result.bitmap shouldNotBe null
        }

    @Test
    fun `non-adaptive icon with maskEnabled and non-null maskedAppIcon uses maskedAppIcon bitmap`() =
        runTest {
            mockkStatic(SeslApplicationPackageManagerReflector::class)
            try {
                val maskedDrawable = ColorDrawable(Color.GREEN)
                every {
                    SeslApplicationPackageManagerReflector.semGetApplicationIconForIconTray(any(), any(), any())
                } returns maskedDrawable
                val nonAdaptiveInfo =
                    object : ApplicationInfo() {
                        override fun loadIcon(pm: PackageManager): Drawable = ColorDrawable(Color.RED)
                    }.apply { packageName = "de.lemke.geticon" }
                val result = useCase(nonAdaptiveInfo, 128, maskEnabled = true, colorEnabled = false, 0, 0, packageManager)
                result.bitmap shouldNotBe null
                result.hasMaskedAppIcon shouldBe true
                result.bitmap.getPixel(0, 0) shouldBe Color.GREEN
            } finally {
                unmockkStatic(SeslApplicationPackageManagerReflector::class)
            }
        }

    @Test
    fun `loadIcon failure falls back to placeholder bitmap`() =
        runTest {
            val badAppInfo =
                object : ApplicationInfo() {
                    override fun loadIcon(pm: PackageManager) = throw IllegalStateException("forced failure")
                }.apply {
                    packageName = "com.does.not.exist"
                }
            val result = useCase(badAppInfo, 128, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
            result.bitmap shouldNotBe null
        }

    @Test
    fun `loadIcon failure with null fallback drawable returns blank IconResult of requested size`() =
        runTest {
            mockkStatic(AppCompatResources::class)
            try {
                every { AppCompatResources.getDrawable(any(), any()) } returns null
                val badAppInfo =
                    object : ApplicationInfo() {
                        override fun loadIcon(pm: PackageManager) = throw IllegalStateException("forced failure")
                    }.apply { packageName = "com.does.not.exist" }
                val result = useCase(badAppInfo, 128, maskEnabled = false, colorEnabled = false, 0, 0, packageManager)
                result.bitmap.width shouldBe 128
                result.bitmap.height shouldBe 128
                result.isAdaptiveIcon shouldBe false
                result.hasMaskedAppIcon shouldBe false
            } finally {
                unmockkStatic(AppCompatResources::class)
            }
        }
}
