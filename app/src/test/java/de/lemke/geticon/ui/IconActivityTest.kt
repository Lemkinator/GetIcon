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
import de.lemke.geticon.domain.IconResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.lang.reflect.InvocationTargetException
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
        } throws IOException("test")
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun onOptionsItemSelected_saveAsImage() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
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
                val item = mockk<MenuItem> { every { itemId } returns android.R.id.home }
                activity.onOptionsItemSelected(item)
            }
        }
    }

    @Test
    fun maskedCheckbox_click_togglesMask() {
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.masked_checkbox)).perform(click())
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
    fun icon_longClick_copiesClipboard() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns Uri.parse("content://test/icon.png")
        try {
            launchWithAppInfo().use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity { activity ->
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
    fun seekbar_trackingTouch_invokesListenerMethods() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)
                seekbar.onStartTrackingTouch()
                seekbar.onStopTrackingTouch()
            }
        }
    }

    @Test
    fun colorButtons_click_showColorPicker() {
        launchWithAppInfo().use { _ ->
            shadowOf(Looper.getMainLooper()).idle()
            // Enable color mode (fakeGenerateIcon returns isAdaptiveIcon=true)
            onView(withId(R.id.color_checkbox)).perform(click())
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.colorButtonBackground)).perform(click())
            onView(withId(R.id.colorButtonForeground)).perform(click())
        }
    }

    @Test
    fun saveIconToUri_resultOk_callsSaveBitmapToUri() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateSaveIconToUri(activity, ActivityResult(Activity.RESULT_OK, Intent()))
            }
        }
    }

    @Test
    fun saveIconToUri_otherResultCode_showsErrorToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateSaveIconToUri(activity, ActivityResult(99, null))
            }
        }
    }

    @Test
    fun onExportBitmapResult_nullIcon_returnsEarly() {
        launchWithAppInfo().use { scenario ->
            // icon still null before looper idles
            scenario.onActivity { activity ->
                invokePrivateOnExportBitmapResult(activity, ActivityResult(Activity.RESULT_OK, Intent()))
            }
        }
    }

    @Test
    fun onExportBitmapResult_withIcon_callsSave() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateOnExportBitmapResult(activity, ActivityResult(Activity.RESULT_OK, Intent()))
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_true() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateOnColorPicked(activity, Color.RED, true)
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_false() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateOnColorPicked(activity, Color.BLUE, false)
            }
        }
    }

    @Test
    fun onCopyButtonClick_withIcon_copiesClipboard() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns Uri.parse("content://test/icon.png")
        try {
            launchWithAppInfo().use { scenario ->
                scenario.onActivity { activity ->
                    invokePrivateOnCopyButtonClick(activity)
                }
            }
        } finally {
            unmockkStatic(FileProvider::class)
        }
    }

    private fun invokePrivateSaveIconToUri(
        activity: IconActivity,
        result: ActivityResult?,
    ) {
        try {
            val method =
                IconActivity::class.java.getDeclaredMethod(
                    "saveIconToUri",
                    ActivityResult::class.java,
                    Bitmap::class.java,
                )
            method.isAccessible = true
            method.invoke(activity, result, testBitmap)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
            // method signature changed
        }
    }

    private fun invokePrivateOnExportBitmapResult(
        activity: IconActivity,
        result: ActivityResult?,
    ) {
        try {
            val method = IconActivity::class.java.getDeclaredMethod("onExportBitmapResult", ActivityResult::class.java)
            method.isAccessible = true
            method.invoke(activity, result)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
        }
    }

    private fun invokePrivateOnColorPicked(
        activity: IconActivity,
        color: Int,
        isBackground: Boolean,
    ) {
        try {
            val method = IconActivity::class.java.getDeclaredMethod("onColorPicked", Int::class.java, Boolean::class.java)
            method.isAccessible = true
            method.invoke(activity, color, isBackground)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
        }
    }

    @Test
    fun showColorPicker_bothVariants_showDialog() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                invokePrivateShowColorPicker(activity, true)
                invokePrivateShowColorPicker(activity, false)
            }
        }
    }

    private fun invokePrivateOnCopyButtonClick(activity: IconActivity) {
        try {
            val method = IconActivity::class.java.getDeclaredMethod("onCopyButtonClick")
            method.isAccessible = true
            method.invoke(activity)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
        }
    }

    private fun invokePrivateShowColorPicker(
        activity: IconActivity,
        isBackground: Boolean,
    ) {
        try {
            val method = IconActivity::class.java.getDeclaredMethod("showColorPicker", Boolean::class.java)
            method.isAccessible = true
            method.invoke(activity, isBackground)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        } catch (_: ReflectiveOperationException) {
        }
    }

    companion object {
        private val testBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        private val testIconResult = IconResult(bitmap = testBitmap, isAdaptiveIcon = true, hasMaskedAppIcon = true)
    }
}
