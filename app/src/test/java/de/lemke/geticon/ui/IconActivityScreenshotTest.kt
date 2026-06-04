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

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivityScreenshotTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun updateUserSettings(): UpdateUserSettingsUseCase
    }

    private val updateUserSettings by lazy {
        EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                SettingsEntryPoint::class.java,
            ).updateUserSettings()
    }

    @Before
    fun resetSettings() {
        hiltRule.inject()
        ApplicationProvider
            .getApplicationContext<HiltTestApplication>()
            .initCommonUtilsSettingsAndSetDarkMode()
        runBlocking {
            updateUserSettings.invoke {
                UserSettings(
                    iconSize = UserSettings.DEFAULT_ICON_SIZE,
                    maskEnabled = true,
                    colorEnabled = false,
                    recentForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR),
                    recentBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR),
                )
            }
        }
    }

    private fun captureIconScreenshot(
        fileName: String,
        preSetup: (suspend () -> Unit)? = null,
    ) {
        preSetup?.let { runBlocking { it() } }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val intent = Intent(context, IconActivity::class.java).putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
        ActivityScenario.launch<IconActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage(fileName)
            }
        }
    }

    @Test
    fun iconActivity_default() {
        captureIconScreenshot("src/test/screenshots/icon_default.png")
    }

    @Test
    fun iconActivity_maskDisabled() {
        captureIconScreenshot("src/test/screenshots/icon_mask_disabled.png") {
            updateUserSettings.invoke { it.copy(maskEnabled = false) }
        }
    }

    @Test
    fun iconActivity_colorEnabled() {
        captureIconScreenshot("src/test/screenshots/icon_color_enabled.png") {
            updateUserSettings.invoke { it.copy(colorEnabled = true) }
        }
    }

    @Test
    fun iconActivity_sizeSmall() {
        captureIconScreenshot("src/test/screenshots/icon_size_small.png") {
            updateUserSettings.invoke { it.copy(iconSize = UserSettings.MIN_ICON_SIZE) }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_default_dark() {
        captureIconScreenshot("src/test/screenshots/icon_default_dark.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_maskDisabled_dark() {
        captureIconScreenshot("src/test/screenshots/icon_mask_disabled_dark.png") {
            updateUserSettings.invoke { it.copy(maskEnabled = false) }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_colorEnabled_dark() {
        captureIconScreenshot("src/test/screenshots/icon_color_enabled_dark.png") {
            updateUserSettings.invoke { it.copy(colorEnabled = true) }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_sizeSmall_dark() {
        captureIconScreenshot("src/test/screenshots/icon_size_small_dark.png") {
            updateUserSettings.invoke { it.copy(iconSize = UserSettings.MIN_ICON_SIZE) }
        }
    }
}
