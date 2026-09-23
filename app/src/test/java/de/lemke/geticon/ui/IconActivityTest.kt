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
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
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
import de.lemke.geticon.R
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.IconResult
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val generateIconStub: GenerateIconUseCase = mockk()

    @Before
    fun setup() {
        hiltRule.inject()
        every {
            generateIconStub(
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
        launchWithoutAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_app_not_found)
                activity.isFinishing shouldBe true
            }
        }
    }

    @Test
    fun collectEvents_generateFailed_finishesActivity() {
        every {
            generateIconStub(
                any<ApplicationInfo>(),
                any<Int>(),
                any<Boolean>(),
                any<Boolean>(),
                any<Int>(),
                any<Int>(),
                any<PackageManager>(),
            )
        } throws IOException("test")
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.error_icon_generation_failed)
                activity.isFinishing shouldBe true
            }
        }
    }

    @Test
    fun onOptionsItemSelected_saveAsImage() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_save_as_image }
                activity.onOptionsItemSelected(item) shouldBe true
                // default imageSaveLocation is CUSTOM, so save routes through the document-picker launcher.
                val startedIntent = shadowOf(activity).nextStartedActivityForResult?.intent
                startedIntent?.action shouldBe Intent.ACTION_CREATE_DOCUMENT
                startedIntent?.type shouldBe "image/png"
            }
        }
    }

    @Test
    fun onOptionsItemSelected_share() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns Uri.parse("content://test/icon.png")
        try {
            launchWithAppInfo().use { scenario ->
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity { activity ->
                    activity.onSeekbarProgressChanged(256)
                    val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_share }
                    activity.onOptionsItemSelected(item) shouldBe true
                    val startedIntent = shadowOf(activity).nextStartedActivity
                    startedIntent?.action shouldBe Intent.ACTION_CHOOSER
                    val innerIntent = IntentCompat.getParcelableExtra(startedIntent!!, Intent.EXTRA_INTENT, Intent::class.java)
                    innerIntent?.type shouldBe "image/png"
                }
            }
        } finally {
            unmockkStatic(FileProvider::class)
        }
    }

    @Test
    fun onOptionsItemSelected_nullIcon_callsSuper() {
        // No appInfo → loadInitialState never runs, so state.icon stays null.
        launchWithoutAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                val item = mockk<MenuItem> { every { itemId } returns R.id.menu_item_icon_save_as_image }
                activity.onOptionsItemSelected(item) shouldBe false
            }
        }
    }

    @Test
    fun onOptionsItemSelected_unknownItem_callsSuper() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val item = mockk<MenuItem> { every { itemId } returns android.R.id.home }
                activity.onOptionsItemSelected(item) shouldBe false
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
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.maskEnabled shouldBe false
            }
        }
    }

    @Test
    fun colorCheckbox_click_togglesColor() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.color_checkbox)).perform(click())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.colorEnabled shouldBe true
            }
        }
    }

    @Test
    fun colorCheckbox_programmaticUncheck_doesNotCallViewModel() {
        // generateIconStub returns isAdaptiveIcon=false so regenerateIcon produces a state
        // where colorCheckbox.isChecked would be set from true→false inside renderState.
        every {
            generateIconStub(
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
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.colorEnabled shouldBe true
                activity.findViewById<CheckBox>(R.id.color_checkbox).isChecked shouldBe false
                listOf(R.id.colorButtonBackground, R.id.colorButtonForeground).forEach { id ->
                    val button = activity.findViewById<Button>(id)
                    button.isEnabled shouldBe false
                    button.backgroundTintList?.defaultColor shouldBe 0x66FFFFFF
                    button.currentTextColor shouldBe 0xFF8C8C8C.toInt()
                }
            }
        }
    }

    @Test
    fun icon_longClick_nullIcon_returnsFalse() {
        // No appInfo → loadInitialState never runs, so state.icon stays null.
        launchWithoutAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe false
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
                    activity.onSeekbarProgressChanged(256)
                    activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
                }
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity { activity ->
                    ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
                    val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
                    clip?.description?.label shouldBe "icon"
                    clip?.getItemAt(0)?.uri shouldBe Uri.parse("content://test/icon.png")
                }
            }
        } finally {
            unmockkStatic(FileProvider::class)
        }
    }

    @Test
    fun sizeEdittext_editorAction_updatesSize() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("256"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 256
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_nonNumericText_doesNotUpdateSize() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("abc"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe DEFAULT_ICON_SIZE
            }
        }
    }

    @Test
    fun seekbar_progressChanged_updatesSizeViaViewModel() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(100)
                activity.onSeekbarProgressChanged(100) // same size → early return (no-op)
            }
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 100
            }
            verify(exactly = 1) { generateIconStub(any(), 100, any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun colorButtons_click_showColorPicker() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.showColorPicker(isBackground = true)
                val backgroundDialog = ShadowDialog.getLatestDialog()
                backgroundDialog?.isShowing shouldBe true
                activity.showColorPicker(isBackground = false)
                val foregroundDialog = ShadowDialog.getLatestDialog()
                foregroundDialog?.isShowing shouldBe true
                foregroundDialog shouldNotBe backgroundDialog
            }
        }
    }

    @Test
    fun onExportBitmapResult_nullIcon_returnsEarly() {
        // No appInfo → loadInitialState never runs, so state.icon stays null.
        launchWithoutAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                val before = ShadowToast.shownToastCount()
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent()))
                ShadowToast.shownToastCount() shouldBe before
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultOk_callsSave() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val file = File(activity.cacheDir, "icon_export_test.png")
                val intent = Intent().setData(Uri.fromFile(file))
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, intent))
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
                file.exists() shouldBe true
                file.length() shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultCanceled_doesNothing() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val before = ShadowToast.shownToastCount()
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_CANCELED, null))
                ShadowToast.shownToastCount() shouldBe before
            }
        }
    }

    @Test
    fun onExportBitmapResult_nullResult_showsErrorToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                activity.onExportBitmapResult(null)
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_saving_image)
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultOkNullData_callsSaveWithNullUri() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, null))
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
            }
        }
    }

    @Test
    fun onExportBitmapResult_otherCode_showsErrorToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                activity.onExportBitmapResult(ActivityResult(99, null))
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_saving_image)
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_true() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.RED, true)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.backgroundColor shouldBe Color.RED
                state.recentBackgroundColors.first() shouldBe Color.RED
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_false() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLUE, false)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.foregroundColor shouldBe Color.BLUE
                state.recentForegroundColors.first() shouldBe Color.BLUE
            }
        }
    }

    @Test
    fun colorButtons_colorDisabled_showDisabledPresentation() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                listOf(R.id.colorButtonBackground, R.id.colorButtonForeground).forEach { id ->
                    val button = activity.findViewById<Button>(id)
                    button.isEnabled shouldBe false
                    button.backgroundTintList?.defaultColor shouldBe 0x66FFFFFF
                    button.currentTextColor shouldBe 0xFF8C8C8C.toInt()
                }
            }
        }
    }

    @Test
    fun backgroundColorButton_brightColorPicked_showsColorWithBlackText() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.WHITE, isBackground = true)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.colorButtonBackground)
                button.isEnabled shouldBe true
                button.backgroundTintList?.defaultColor shouldBe Color.WHITE
                button.currentTextColor shouldBe Color.BLACK
            }
        }
    }

    @Test
    fun foregroundColorButton_darkColorPicked_showsColorWithWhiteText() {
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLACK, isBackground = false)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.colorButtonForeground)
                button.isEnabled shouldBe true
                button.backgroundTintList?.defaultColor shouldBe Color.BLACK
                button.currentTextColor shouldBe Color.WHITE
            }
        }
    }

    @Test
    fun foregroundColorButton_translucentDarkColorPicked_usesBlackTextOverLightWindow() {
        val translucentBlack = Color.argb(0x40, 0, 0, 0)
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(translucentBlack, isBackground = false)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.colorButtonForeground)
                button.isEnabled shouldBe true
                button.backgroundTintList?.defaultColor shouldBe translucentBlack
                button.currentTextColor shouldBe Color.BLACK
            }
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun foregroundColorButton_translucentDarkColorPicked_usesWhiteTextOverDarkWindow() {
        val translucentBlack = Color.argb(0x40, 0, 0, 0)
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onColorPicked(translucentBlack, isBackground = false)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.colorButtonForeground)
                button.isEnabled shouldBe true
                button.backgroundTintList?.defaultColor shouldBe translucentBlack
                button.currentTextColor shouldBe Color.WHITE
            }
        }
    }

    companion object {
        private val testBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        private val testIconResult = IconResult(bitmap = testBitmap, isAdaptiveIcon = true, hasMaskedAppIcon = true)
    }
}
