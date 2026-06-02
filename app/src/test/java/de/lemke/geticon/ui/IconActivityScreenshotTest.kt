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

    private fun updateUserSettings() =
        EntryPointAccessors
            .fromApplication(
                ApplicationProvider.getApplicationContext(),
                SettingsEntryPoint::class.java,
            ).updateUserSettings()

    @Before
    fun resetSettings() {
        hiltRule.inject()
        ApplicationProvider
            .getApplicationContext<HiltTestApplication>()
            .initCommonUtilsSettingsAndSetDarkMode()
        runBlocking {
            updateUserSettings().invoke {
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

    private fun launchIconActivity(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val intent =
            Intent(context, IconActivity::class.java)
                .putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
        return ActivityScenario.launch(intent)
    }

    @Test
    fun iconActivity_default() {
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_default.png")
            }
        }
    }

    @Test
    fun iconActivity_maskDisabled() {
        runBlocking { updateUserSettings().invoke { it.copy(maskEnabled = false) } }
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_mask_disabled.png")
            }
        }
    }

    @Test
    fun iconActivity_colorEnabled() {
        runBlocking { updateUserSettings().invoke { it.copy(colorEnabled = true) } }
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_color_enabled.png")
            }
        }
    }

    @Test
    fun iconActivity_sizeSmall() {
        runBlocking { updateUserSettings().invoke { it.copy(iconSize = UserSettings.MIN_ICON_SIZE) } }
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_size_small.png")
            }
        }
    }
}
