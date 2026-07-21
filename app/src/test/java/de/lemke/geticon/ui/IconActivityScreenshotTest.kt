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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.geticon.data.UserSettings
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun resetSettings() {
        hiltRule.inject()
    }

    private fun captureIconScreenshot(fileName: String) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val intent = Intent(context, IconActivity::class.java).putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
        ActivityScenario.launch<IconActivity>(intent).use {
            onView(isRoot()).captureRoboImage(fileName)
        }
    }

    @Test
    fun iconActivity_default() {
        captureIconScreenshot("src/test/screenshots/icon_default.png")
    }

    @Test
    fun iconActivity_maskDisabled() {
        userSettings.maskEnabled = false
        captureIconScreenshot("src/test/screenshots/icon_mask_disabled.png")
    }

    @Test
    fun iconActivity_colorEnabled() {
        userSettings.colorEnabled = true
        captureIconScreenshot("src/test/screenshots/icon_color_enabled.png")
    }

    @Test
    fun iconActivity_sizeSmall() {
        userSettings.iconSize = UserSettings.MIN_ICON_SIZE
        captureIconScreenshot("src/test/screenshots/icon_size_small.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_default_dark() {
        captureIconScreenshot("src/test/screenshots/icon_default_dark.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_maskDisabled_dark() {
        userSettings.maskEnabled = false
        captureIconScreenshot("src/test/screenshots/icon_mask_disabled_dark.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_colorEnabled_dark() {
        userSettings.colorEnabled = true
        captureIconScreenshot("src/test/screenshots/icon_color_enabled_dark.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun iconActivity_sizeSmall_dark() {
        userSettings.iconSize = UserSettings.MIN_ICON_SIZE
        captureIconScreenshot("src/test/screenshots/icon_size_small_dark.png")
    }
}
