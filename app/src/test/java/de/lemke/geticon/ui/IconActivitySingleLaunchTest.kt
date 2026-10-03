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
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.geticon.R
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivitySingleLaunchTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun launchIconActivity(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        return ActivityScenario.launch<IconActivity>(
            Intent(context, IconActivity::class.java).putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo),
        )
    }

    @Test
    fun saveAsImage_doubleTap_launchesDocumentPickerOnce() {
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_save_as_image)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivityForResult?.intent?.action shouldBe Intent.ACTION_CREATE_DOCUMENT
                shadowActivity.nextStartedActivityForResult shouldBe null
            }
        }
    }

    @Test
    fun share_doubleTap_opensShareSheetOnce() {
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_share)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.action shouldBe Intent.ACTION_CHOOSER
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun colorButton_doubleTap_showsOneColorPicker() {
        launchIconActivity().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.colorButtonBackground)
                button.performClick()
                button.performClick()
                ShadowDialog.getShownDialogs().size shouldBe 1
                ShadowDialog.getLatestDialog().isShowing shouldBe true
            }
        }
    }
}
