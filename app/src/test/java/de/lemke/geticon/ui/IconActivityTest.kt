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
import android.os.Environment
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.ActivityResult
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
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.geticon.R
import de.lemke.geticon.di.DispatchersModule
import de.lemke.geticon.domain.GenerateIconUseCase
import de.lemke.geticon.domain.IconResult
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@UninstallModules(DispatchersModule::class)
class IconActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val generateIconStub: GenerateIconUseCase = mockk()

    private val pausableIoDispatcher = PausableDispatcher(Dispatchers.Main)

    @BindValue
    @IoDispatcher
    @JvmField
    val ioDispatcher: CoroutineDispatcher = pausableIoDispatcher

    @Inject
    lateinit var settings: SettingsRepository

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
                val item = RoboMenuItem(R.id.menu_item_icon_save_as_image)
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
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val item = RoboMenuItem(R.id.menu_item_icon_share)
                activity.onOptionsItemSelected(item) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val startedIntent = shadowOf(activity).nextStartedActivity
                startedIntent?.action shouldBe Intent.ACTION_CHOOSER
                val innerIntent = IntentCompat.getParcelableExtra(startedIntent!!, Intent.EXTRA_INTENT, Intent::class.java)!!
                innerIntent.type shouldBe "image/png"
                val stream = IntentCompat.getParcelableExtra(innerIntent, Intent.EXTRA_STREAM, Uri::class.java)!!
                stream.toString() shouldMatch activity.iconContentUriPattern("share", "icon.png")
                activity.cacheFile(stream).length() shouldBeGreaterThan 0L
                innerIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        }
    }

    @Test
    fun saveAsImage_doubleTap_launchesDocumentPickerOnce() {
        launchWithAppInfo().use { scenario ->
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
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_share)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.action shouldBe Intent.ACTION_CHOOSER
                shadowActivity.nextStartedActivity shouldBe null
                File(activity.cacheDir, "share").walk().count { it.isFile } shouldBe 1
            }
        }
    }

    @Test
    fun share_whileCopyWrites_startsNothingAndShowsNoToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            pausableIoDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity shouldBe null
                File(activity.cacheDir, "share").exists() shouldBe false
            }
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }
    }

    @Test
    fun saveAsImage_fixedLocation_doubleTap_writesOneFileAndShowsOneToast() {
        settings.imageSaveLocation = SaveLocation.DOWNLOADS
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_save_as_image)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles()?.size shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved: Downloads"
        }
    }

    @Test
    @Config(sdk = [29])
    fun saveAsImage_api29WithStoredDownloads_savesThroughDocumentPicker() {
        settings.imageSaveLocation = SaveLocation.DOWNLOADS
        val file = File(ApplicationProvider.getApplicationContext<HiltTestApplication>().cacheDir, "icon_export_test.png")
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                val picker = shadowActivity.nextStartedActivityForResult.intent
                picker.action shouldBe Intent.ACTION_CREATE_DOCUMENT
                picker.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch """de_lemke_geticon_debug_mask_\d{4}(_\d{2}){5}\.png"""
                shadowActivity.receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            }
            shadowOf(Looper.getMainLooper()).idle()
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.list().orEmpty().toList() shouldBe emptyList()
            file.length() shouldBeGreaterThan 0L
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
        }
    }

    @Test
    fun onOptionsItemSelected_nullIcon_callsSuper() {
        // No appInfo → loadInitialState never runs, so state.icon stays null.
        launchWithoutAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_save_as_image)
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
                val item = RoboMenuItem(android.R.id.home)
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
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
                val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
                val uri = clip.getItemAt(0).uri
                clip.description.label shouldBe "icon"
                clip.description.getMimeType(0) shouldBe "image/png"
                uri.toString() shouldMatch activity.iconContentUriPattern("clipboard", "icon.png")
                activity.contentResolver.getType(uri) shouldBe "image/png"
            }
        }
    }

    @Test
    fun icon_doubleLongClick_copiesOneClipWithOneToast() {
        launchWithAppInfo().use { scenario ->
            var clipChanges = 0
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener { clipChanges++ }
                val icon = activity.findViewById<ImageView>(R.id.icon)
                icon.performLongClick() shouldBe true
                icon.performLongClick() shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            clipChanges shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }
    }

    @Test
    fun icon_longClickWhileShareSheetPending_copiesNothing() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip() shouldBe false
            }
            ShadowToast.shownToastCount() shouldBe 0
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
    fun sizeEdittext_showsDefaultSize() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "512"
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_showsLocaleDigitsAndParsesThemBack() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
            }
            onView(withId(R.id.size_edittext)).perform(replaceText("٢٥٦"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 256
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_mixedDigitsForCurrentSize_reformatsWithLocaleDigits() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
            }
            onView(withId(R.id.size_edittext)).perform(replaceText("٥١2"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 512
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_mixedDigitsForNewSize_reformatsWithLocaleDigits() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("٢٥6"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٢٥٦"
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 256
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_aboveMaxAtMaxSize_showsClampedSizeWithCursorAtEnd() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> activity.onSeekbarProgressChanged(1024) }
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("5000"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.size_edittext)
                field.text.toString() shouldBe "1024"
                field.selectionStart shouldBe 4
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 1024
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_belowMinAtMinSize_showsClampedSize() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> activity.onSeekbarProgressChanged(16) }
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("3"), pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "16"
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 16
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_sameSizeSubmitted_keepsTextAndCursorAtEnd() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            onView(withId(R.id.size_edittext)).perform(replaceText("512"))
            scenario.onActivity { activity -> activity.findViewById<EditText>(R.id.size_edittext).setSelection(3) }
            onView(withId(R.id.size_edittext)).perform(pressImeActionButton())
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.size_edittext)
                field.text.toString() shouldBe "512"
                field.selectionStart shouldBe 3
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 512
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
                ViewModelProvider(activity)[IconViewModel::class.java].state.value.size shouldBe 512
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
    fun colorButton_doubleTap_showsOneColorPicker() {
        launchWithAppInfo().use { scenario ->
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

    @Test
    fun onExportBitmapResult_nullIcon_showsWriteError() {
        // No appInfo → loadInitialState never runs, so state.icon stays null.
        launchWithoutAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                val file = File(activity.cacheDir, "icon_export_test.png")
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun onExportBitmapResult_resultOk_savesIcon() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            val file = File(ApplicationProvider.getApplicationContext<HiltTestApplication>().cacheDir, "icon_export_test.png")
            scenario.onActivity { activity ->
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
            file.length() shouldBeGreaterThan 0L
        }
    }

    @Test
    fun onExportBitmapResult_rotationDuringWrite_cancelsWriteAndAdmitsNextSave() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            val file = File(ApplicationProvider.getApplicationContext<HiltTestApplication>().cacheDir, "icon_export_test.png")
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            }
            scenario.recreate()
            pausableIoDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()
            file.exists() shouldBe false
            ShadowToast.shownToastCount() shouldBe 0
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            }
        }
    }

    @Test
    fun onExportBitmapResult_resultCanceled_showsNoToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_CANCELED, null))
            }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun onExportBitmapResult_resultOkWithoutUri_showsNoToast() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.onExportBitmapResult(ActivityResult(Activity.RESULT_OK, null))
            }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.shownToastCount() shouldBe 0
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

    @Test
    fun icon_contentDescription_namesApp() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Get Icon (Debug) icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun icon_contentDescription_namesApp_inGerman() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Icon von Get Icon (Debug)"
            }
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun icon_contentDescription_namesApp_inLandscape() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Get Icon (Debug) icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de-land")
    fun icon_contentDescription_namesApp_inGermanLandscape() {
        launchWithAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Icon von Get Icon (Debug)"
            }
        }
    }

    @Test
    fun icon_contentDescription_withoutAppName_keepsGenericFallback() {
        launchWithoutAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "App icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun icon_contentDescription_withoutAppName_keepsGermanGenericFallback() {
        launchWithoutAppInfo().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "App-Icon"
            }
        }
    }

    companion object {
        private val testBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        private val testIconResult = IconResult(bitmap = testBitmap, isAdaptiveIcon = true, hasMaskedAppIcon = true)
    }
}
