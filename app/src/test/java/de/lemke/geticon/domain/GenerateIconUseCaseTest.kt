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
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.App
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
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
        useCase = GenerateIconUseCase(context, UnconfinedTestDispatcher())
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
}
