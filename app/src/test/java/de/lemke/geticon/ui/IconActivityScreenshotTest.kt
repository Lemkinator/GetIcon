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
import android.widget.CompoundButton
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.geticon.App
import de.lemke.geticon.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivityScreenshotTest {
    private fun launchIconActivity(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<App>()
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
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                requireNotNull(activity.findViewById<CompoundButton>(R.id.masked_checkbox)).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_mask_disabled.png")
            }
        }
    }

    @Test
    fun iconActivity_colorEnabled() {
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                requireNotNull(activity.findViewById<CompoundButton>(R.id.color_checkbox)).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_color_enabled.png")
            }
        }
    }
}
