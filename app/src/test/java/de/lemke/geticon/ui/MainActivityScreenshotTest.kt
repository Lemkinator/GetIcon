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

import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.geticon.App
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
// App::class: uses the production Hilt component so App.onCreate() initializes commonUtilsSettings.
@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityScreenshotTest {
    @Before
    fun setup() {
        // Bypass OOBE: fresh test has lastVersionCode == -1 which triggers openOOBEAndFinish().
        commonUtilsSettings.lastVersionCode = Int.MAX_VALUE
        commonUtilsSettings.acceptedTosVersion = Int.MAX_VALUE
    }

    @Test
    fun mainActivity_default() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("main_default.png")
            }
        }
    }
}
