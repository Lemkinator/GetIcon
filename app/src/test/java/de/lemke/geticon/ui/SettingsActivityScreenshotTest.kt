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

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.setupCommonUtilsSettingsActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.geticon.App
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.lemke.commonutils.R as commonutilsR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
// App::class: uses the production Hilt component so App.onCreate() initializes commonUtilsSettings.
@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsActivityScreenshotTest {
    @Before
    fun setup() {
        commonUtilsSettings.lastVersionCode = Int.MAX_VALUE
        commonUtilsSettings.acceptedTosVersion = Int.MAX_VALUE
        setupCommonUtilsSettingsActivity(
            commonutilsR.xml.preferences_design,
            commonutilsR.xml.preferences_general_language_and_image_save_location,
            commonutilsR.xml.preferences_dev_options_delete_app_data,
            commonutilsR.xml.preferences_more_info,
        )
    }

    @Test
    fun settingsActivity_default() {
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("src/test/screenshots/settings_default.png")
            }
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsActivity_default_dark() {
        ActivityScenario.launch(CommonUtilsSettingsActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("src/test/screenshots/settings_default_dark.png")
            }
        }
    }
}
