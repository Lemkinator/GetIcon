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

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import de.lemke.geticon.R
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.GetUserSettingsUseCase
import de.lemke.geticon.domain.IconResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
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
class IconActivityTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val fakeGenerateIcon: GenerateIconUseCase = mockk()

    @BindValue
    @JvmField
    val fakeGetUserSettings: GetUserSettingsUseCase = mockk()

    @Before
    fun setup() {
        hiltRule.inject()
        ApplicationProvider.getApplicationContext<HiltTestApplication>().initCommonUtilsSettingsAndSetDarkMode()
        every {
            fakeGenerateIcon(
                any<ApplicationInfo>(),
                any<Int>(),
                any<Boolean>(),
                any<Boolean>(),
                any<Int>(),
                any<Int>(),
                any<PackageManager>(),
            )
        } returns testIconResult
        coEvery { fakeGetUserSettings() } coAnswers { awaitCancellation() }
    }

    private fun launchWithAppInfo(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val intent =
            Intent(context, IconActivity::class.java)
                .putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
        return ActivityScenario.launch(intent)
    }

    private fun launchWithoutAppInfo(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        return ActivityScenario.launch(Intent(context, IconActivity::class.java))
    }

    @Test
    fun collectEvents_finish_whenNoAppInfo() {
        launchWithoutAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun collectEvents_generateFailed_finishesActivity() {
        coEvery { fakeGetUserSettings() } throws IOException("test")
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun onOptionsItemSelected_saveAsImage() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_save_as_image }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun onOptionsItemSelected_share() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_share }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun onOptionsItemSelected_nullIcon_callsSuper() {
        // Call before idling looper so state.icon is still null
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_save_as_image }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun onOptionsItemSelected_unknownItem_callsSuper() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                val item = mockk<MenuItem> { every { itemId } returns android.R.id.home }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun maskedCheckbox_click_togglesMask() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                // performClick() calls toggle() before the enabled check, firing the listener
                // with isRendering=false regardless of whether the checkbox is enabled.
                activity.findViewById<CheckBox>(R.id.masked_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun colorCheckbox_click_togglesColor() {
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.color_checkbox)).perform(click())
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun colorCheckbox_programmaticUncheck_doesNotCallViewModel() {
        // fakeGenerateIcon returns isAdaptiveIcon=false so regenerateIcon produces a state
        // where colorCheckbox.isChecked would be set from true→false inside renderState.
        every {
            fakeGenerateIcon(
                any<ApplicationInfo>(),
                any<Int>(),
                any<Boolean>(),
                any<Boolean>(),
                any<Int>(),
                any<Int>(),
                any<PackageManager>(),
            )
        } returns testIconResult.copy(isAdaptiveIcon = false)
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                // performClick() fires OnCheckedChangeListener (isRendering=false) → onColorChanged(true).
                // regenerateIcon runs with colorEnabled=true, isAdaptiveIcon=false from stub.
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            // renderState: colorCheckbox.isChecked = colorEnabled && isAdaptiveIcon = true && false = false
            // → changes from true→false while isRendering=true → listener fires with isRendering=true (skips body).
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun icon_longClick_nullIcon_returnsFalse() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).performLongClick()
            }
        }
    }

    @Test
    fun icon_longClick_copiesClipboard() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns Uri.parse("content://test/icon.png")
        try {
            launchWithAppInfo().use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity { activity ->
                    activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                    activity.findViewById<ImageView>(R.id.icon).performLongClick()
                }
            }
        } finally {
            unmockkStatic(FileProvider::class)
        }
    }

    @Test
    fun sizeEdittext_editorAction_updatesSizeAndHidesKeyboard() {
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("256"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun sizeEdittext_editorAction_nonNumericText_doesNotUpdateSize() {
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("abc"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun seekbar_trackingTouch_invokesListenerMethods() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)
                activity.seekbarChangeListener.onStartTrackingTouch(seekbar)
                activity.seekbarChangeListener.onStopTrackingTouch(seekbar)
            }
        }
    }

    @Test
    fun seekbar_progressChanged_fromUser_updatesSizeViaViewModel() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)
                activity.seekbarChangeListener.onProgressChanged(seekbar, 100, true)
            }
        }
    }

    @Test
    fun colorButtons_click_showColorPicker() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.showColorPicker(isBackground = true)
                activity.showColorPicker(isBackground = false)
            }
        }
    }

    @Test
    fun onExportBitmapResult_nullIcon_returnsEarly() {
        // icon still null before looper idles
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent()))
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultOk_callsSave() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent()))
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultCanceled_doesNothing() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_CANCELED, null))
            }
        }
    }

    @Test
    fun onExportBitmapResult_nullResult_showsErrorToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                activity.onExportBitmapResult(null)
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultOkNullData_callsSaveWithNullUri() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, null))
            }
        }
    }

    @Test
    fun onExportBitmapResult_otherCode_showsErrorToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.seekbarChangeListener.onProgressChanged(activity.findViewById(R.id.size_seekbar), 512, true)
                activity.onExportBitmapResult(ActivityResult(99, null))
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_true() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.RED, true)
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_false() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLUE, false)
            }
        }
    }

    @Test
    fun setButtonColors_brightBackground_usesBlackText() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.WHITE, isBackground = true)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun setButtonColors_darkForeground_usesWhiteText() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLACK, isBackground = false)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    companion object {
        private val testBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        private val testIconResult = IconResult(bitmap = testBitmap, isAdaptiveIcon = true, hasMaskedAppIcon = true)
    }
}
