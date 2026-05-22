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
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.geticon.App
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivityScreenshotTest {
    @Test
    fun iconActivity_default() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val intent =
            Intent(context, IconActivity::class.java)
                .putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
        ActivityScenario.launch<IconActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.captureRoboImage("icon_default.png")
            }
        }
    }
}
