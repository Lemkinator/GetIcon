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
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.picker3.app.SeslColorPickerDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.PausableDispatcher
import de.lemke.commonutils.RecordingDocumentsProvider
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.cacheFileOf
import de.lemke.commonutils.cacheWriteUriPattern
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.hasPngSignature
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.receivePickerResult
import de.lemke.commonutils.returnFromLaunchedScreen
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.geticon.R
import de.lemke.geticon.data.FakeIconRenderer
import de.lemke.geticon.data.IconRenderer
import de.lemke.geticon.di.AppCoroutinesModule
import de.lemke.geticon.di.IconRendererModule
import de.lemke.geticon.domain.model.IconKind
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
@UninstallModules(AppCoroutinesModule::class, IconRendererModule::class)
class IconActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val renderer = FakeIconRenderer(testBitmap)

    @BindValue
    @JvmField
    val iconRenderer: IconRenderer = renderer

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
    }

    private fun pickDocument(
        activity: IconActivity,
        pick: DocumentPick,
    ) = ViewModelProvider(activity)[IconViewModel::class.java].onDocumentPicked(pick)

    private fun confirmRecentColor(slot: Int) {
        val dialog = ShadowDialog.getLatestDialog() as SeslColorPickerDialog
        dialog
            .requireViewById<ViewGroup>(androidx.picker.R.id.sesl_color_picker_used_color_item_list_layout)
            .getChildAt(slot)
            .performClick()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMainLooper()
    }

    private fun appInfoIntent(): Intent {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        return Intent(context, IconActivity::class.java).putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo)
    }

    private fun launchWithAppInfo(): ActivityScenario<IconActivity> = ActivityScenario.launch(appInfoIntent())

    private fun launchWithoutAppInfo(): ActivityScenario<IconActivity> {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        return ActivityScenario.launch(Intent(context, IconActivity::class.java))
    }

    @Test
    fun exit_noAppInfo_showsAppNotFoundAndFinishes() {
        launchWithoutAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                ShadowToast.shownToastCount() shouldBe 1
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_app_not_found)
                activity.isFinishing shouldBe true
                ViewModelProvider(activity)[IconViewModel::class.java].exit.value shouldBe IconExit.None
            }
        }
    }

    @Test
    fun exit_generateFailed_showsGenerationErrorAndFinishes() {
        failIconRendering()
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                ShadowToast.shownToastCount() shouldBe 1
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.error_icon_generation_failed)
                activity.isFinishing shouldBe true
                ViewModelProvider(activity)[IconViewModel::class.java].exit.value shouldBe IconExit.None
            }
        }
    }

    @Test
    fun exit_regenerationFailsWhileStopped_showsErrorOnceAfterRecreationAndFinishes() {
        val controller = Robolectric.buildActivity(IconActivity::class.java, appInfoIntent()).setup()
        try {
            idleMainLooper()
            controller.pause().stop()
            failIconRendering()
            val stopped = controller.get()
            ViewModelProvider(stopped)[IconViewModel::class.java].onMaskChanged(false)
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 0
            controller.recreate()
            idleMainLooper()
            controller.restart().start().resume()
            idleMainLooper()
            val recreated = controller.get()
            recreated shouldNotBeSameInstanceAs stopped
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe recreated.getString(R.string.error_icon_generation_failed)
            stopped.isFinishing shouldBe false
            recreated.isFinishing shouldBe true
            ViewModelProvider(recreated)[IconViewModel::class.java].exit.value shouldBe IconExit.None
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun exit_regenerationFailsWhilePaused_waitsForResumeThenFinishes() {
        val controller = Robolectric.buildActivity(IconActivity::class.java, appInfoIntent()).setup()
        try {
            idleMainLooper()
            controller.pause()
            failIconRendering()
            val activity = controller.get()
            ViewModelProvider(activity)[IconViewModel::class.java].onMaskChanged(false)
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 0
            activity.isFinishing shouldBe false
            ViewModelProvider(activity)[IconViewModel::class.java].exit.value shouldBe IconExit.GenerateFailed
            controller.resume()
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.error_icon_generation_failed)
            activity.isFinishing shouldBe true
            ViewModelProvider(activity)[IconViewModel::class.java].exit.value shouldBe IconExit.None
        } finally {
            controller.destroy()
        }
    }

    private fun failIconRendering() {
        renderer.failure = IOException("test")
    }

    @Test
    fun onOptionsItemSelected_saveAsImage() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                val item = RoboMenuItem(R.id.menu_item_icon_share)
                activity.onOptionsItemSelected(item) shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val startedIntent = shadowOf(activity).nextStartedActivity
                startedIntent?.action shouldBe Intent.ACTION_CHOOSER
                val innerIntent = IntentCompat.getParcelableExtra(startedIntent!!, Intent.EXTRA_INTENT, Intent::class.java)!!
                innerIntent.type shouldBe "image/png"
                val stream = IntentCompat.getParcelableExtra(innerIntent, Intent.EXTRA_STREAM, Uri::class.java)!!
                stream.toString() shouldMatch activity.cacheWriteUriPattern("icons", "share", "icon.png")
                activity.cacheFileOf(stream).length() shouldBeGreaterThan 0L
                innerIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        }
    }

    @Test
    fun saveAsImage_doubleTap_launchesDocumentPickerOnce() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_share)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
            }
            idleMainLooper()
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
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            pausableIoDispatcher.resume()
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity shouldBe null
                File(activity.cacheDir, "share").exists() shouldBe false
            }
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }
    }

    @Test
    fun share_rotationDuringWrite_opensShareSheetOnceInRecreatedActivity() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            scenario.recreate()
            pausableIoDispatcher.resume()
            idleMainLooper()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.action shouldBe Intent.ACTION_CHOOSER
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun saveAsImage_fixedLocation_rotationDuringWrite_showsToastInRecreatedActivity() {
        settings.imageSaveLocation = SaveLocation.DOWNLOADS
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            scenario.recreate()
            pausableIoDispatcher.resume()
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved: Downloads"
        }
    }

    @Test
    fun share_latchDropsLaunch_keepsResultAndOpensShareSheetOnNextResume() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
                activity.singleLaunchActivity(Intent(activity, CommonUtilsAboutActivity::class.java)) shouldBe true
            }
            pausableIoDispatcher.resume()
            idleMainLooper()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity?.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .export.value
                    .shouldBeInstanceOf<BitmapExport.Share>()
            }
            scenario.returnFromLaunchedScreen()
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.action shouldBe Intent.ACTION_CHOOSER
                ViewModelProvider(activity)[IconViewModel::class.java].export.value shouldBe BitmapExport.Idle
            }
        }
    }

    @Test
    fun saveAsImage_noDocumentPicker_showsSavingErrorOnceAcrossResume() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>()).checkActivities(true)
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
            scenario.returnFromLaunchedScreen()
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivityForResult shouldBe null
                ViewModelProvider(activity)[IconViewModel::class.java].export.value shouldBe BitmapExport.Idle
            }
        }
    }

    @Test
    fun saveAsImage_latchDropsPickerLaunch_keepsResultAndOpensPickerOnNextResume() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.singleLaunchActivity(Intent(activity, CommonUtilsAboutActivity::class.java)) shouldBe true
                ViewModelProvider(activity)[IconViewModel::class.java].onSave()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivityForResult
                    ?.intent
                    ?.component
                    ?.className shouldBe
                    CommonUtilsAboutActivity::class.java.name
                shadowActivity.nextStartedActivityForResult shouldBe null
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .export.value
                    .shouldBeInstanceOf<BitmapExport.OpenPicker>()
            }
            scenario.returnFromLaunchedScreen()
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivityForResult?.intent?.action shouldBe Intent.ACTION_CREATE_DOCUMENT
                ViewModelProvider(activity)[IconViewModel::class.java].export.value shouldBe BitmapExport.Idle
            }
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun share_noShareSheet_showsShareErrorOnceAcrossResume() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>()).checkActivities(true)
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error: Sharing content is not supported on your device."
            scenario.returnFromLaunchedScreen()
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity shouldBe null
                File(activity.cacheDir, "share").walk().count { it.isFile } shouldBe 1
                ViewModelProvider(activity)[IconViewModel::class.java].export.value shouldBe BitmapExport.Idle
            }
        }
    }

    @Test
    fun exportControls_disabledWhileWorkRuns() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).isLongClickable shouldBe false
                activity.preparedExportMenuItemsEnabled() shouldBe listOf(false, false)
            }
            pausableIoDispatcher.resume()
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).isLongClickable shouldBe true
                activity.preparedExportMenuItemsEnabled() shouldBe listOf(true, true)
            }
        }
    }

    @Test
    fun share_shareFileCannotBeWritten_showsShareError() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                File(activity.cacheDir, "share").createNewFile() shouldBe true
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity shouldBe null
                ShadowToast.getTextOfLatestToast() shouldBe
                    activity.getString(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)
            }
        }
    }

    @Test
    fun saveAsImage_fixedLocation_doubleTap_writesOneFileAndShowsOneToast() {
        settings.imageSaveLocation = SaveLocation.DOWNLOADS
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val item = RoboMenuItem(R.id.menu_item_icon_save_as_image)
                activity.onOptionsItemSelected(item) shouldBe true
                activity.onOptionsItemSelected(item) shouldBe true
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val picker = activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                picker.action shouldBe Intent.ACTION_CREATE_DOCUMENT
                picker.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch """de_lemke_geticon_debug_mask_\d{4}(_\d{2}){5}\.png"""
            }
            idleMainLooper()
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
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.maskEnabled shouldBe false
            }
        }
    }

    @Test
    fun colorCheckbox_click_togglesColor() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            onView(withId(R.id.color_checkbox)).perform(click())
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.colorEnabled shouldBe true
            }
        }
    }

    @Test
    fun colorCheckbox_programmaticUncheck_doesNotCallViewModel() {
        // The renderer reports a MASKABLE icon so regenerateIcon produces a state
        // where colorCheckbox.isChecked would be set from true→false inside renderState.
        renderer.kind = IconKind.MASKABLE
        launchWithAppInfo().use { scenario ->
            scenario.onActivity { activity ->
                // performClick() fires OnCheckedChangeListener (isRendering=false) → onColorChanged(true).
                // regenerateIcon runs with colorEnabled=true and the MASKABLE kind from the renderer.
                activity.findViewById<CheckBox>(R.id.color_checkbox).performClick()
            }
            // renderState: colorCheckbox.isChecked = colorEnabled && kind.canTint = true && false = false
            // → changes from true→false while isRendering=true → listener fires with isRendering=true (skips body).
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.colorEnabled shouldBe true
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(256)
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
                val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
                val uri = clip.getItemAt(0).uri
                clip.description.label shouldBe "icon"
                clip.description.getMimeType(0) shouldBe "image/png"
                uri.toString() shouldMatch activity.cacheWriteUriPattern("icons", "clipboard", "icon.png")
                activity.contentResolver.getType(uri) shouldBe "image/png"
            }
        }
    }

    @Test
    fun icon_doubleLongClick_copiesOneClipWithOneToast() {
        launchWithAppInfo().use { scenario ->
            var clipChanges = 0
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener { clipChanges++ }
                val icon = activity.findViewById<ImageView>(R.id.icon)
                icon.performLongClick() shouldBe true
                icon.performLongClick() shouldBe true
            }
            idleMainLooper()
            clipChanges shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }
    }

    @Test
    fun icon_longClick_clipCannotBeWritten_showsShareError() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                File(activity.cacheDir, "clipboard").createNewFile() shouldBe true
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip() shouldBe false
                ShadowToast.getTextOfLatestToast() shouldBe
                    activity.getString(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)
            }
        }
    }

    @Test
    fun icon_longClickWhileShareSheetPending_copiesNothing() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_share)) shouldBe true
                activity.findViewById<ImageView>(R.id.icon).performLongClick() shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).hasPrimaryClip() shouldBe false
            }
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun sizeEdittext_editorAction_updatesSize() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("256"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 256
            }
        }
    }

    @Test
    fun sizeEdittext_showsDefaultSize() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "512"
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_showsLocaleDigitsAndParsesThemBack() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
            }
            onView(withId(R.id.size_edittext)).perform(replaceText("٢٥٦"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 256
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_mixedDigitsForCurrentSize_reformatsWithLocaleDigits() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
            }
            onView(withId(R.id.size_edittext)).perform(replaceText("٥١2"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٥١٢"
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 512
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun sizeEdittext_arabicLocale_mixedDigitsForNewSize_reformatsWithLocaleDigits() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("٢٥6"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "٢٥٦"
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 256
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_aboveMaxAtMaxSize_showsClampedSizeWithCursorAtEnd() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onSeekbarProgressChanged(1024) }
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("5000"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.size_edittext)
                field.text.toString() shouldBe "1024"
                field.selectionStart shouldBe 4
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 1024
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_belowMinAtMinSize_showsClampedSize() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onSeekbarProgressChanged(16) }
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("3"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.size_edittext).text.toString() shouldBe "16"
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 16
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_sameSizeSubmitted_keepsTextAndCursorAtEnd() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("512"))
            scenario.onActivity { activity -> activity.findViewById<EditText>(R.id.size_edittext).setSelection(3) }
            onView(withId(R.id.size_edittext)).perform(pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.size_edittext)
                field.text.toString() shouldBe "512"
                field.selectionStart shouldBe 3
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 512
            }
        }
    }

    @Test
    fun sizeEdittext_editorAction_nonNumericText_doesNotUpdateSize() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            onView(withId(R.id.size_edittext)).perform(replaceText("abc"), pressImeActionButton())
            idleMainLooper()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 512
            }
        }
    }

    @Test
    fun seekbar_progressChanged_updatesSizeViaViewModel() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onSeekbarProgressChanged(100)
                activity.onSeekbarProgressChanged(100) // same size → early return (no-op)
            }
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[IconViewModel::class.java]
                    .state.value.style.size shouldBe 100
            }
            renderer.renders.count { it.style.size == 100 } shouldBe 1
        }
    }

    @Test
    fun colorButtons_click_showColorPicker() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
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
    fun colorPicker_confirmRecentColor_changesBackgroundColor() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.RED, isBackground = true)
                activity.onColorPicked(Color.GREEN, isBackground = true)
                activity.showColorPicker(isBackground = true)
                confirmRecentColor(slot = 1)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.style.backgroundColor shouldBe Color.RED
                state.recentBackgroundColors shouldBe listOf(Color.RED, Color.GREEN, 0xFF0381FE.toInt())
            }
        }
    }

    @Test
    fun colorPicker_confirmRecentColor_changesForegroundColor() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.RED, isBackground = false)
                activity.onColorPicked(Color.GREEN, isBackground = false)
                activity.showColorPicker(isBackground = false)
                confirmRecentColor(slot = 1)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.style.foregroundColor shouldBe Color.RED
                state.recentForegroundColors shouldBe listOf(Color.RED, Color.GREEN, Color.WHITE)
            }
        }
    }

    @Test
    fun colorButton_doubleTap_showsOneColorPicker() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
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
    fun documentPicked_afterGenerationFailed_deletesDocumentAndShowsCreateError() {
        renderer.failure = IOException("generation failed")
        val provider = RecordingDocumentsProvider.create(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                pickDocument(activity, DocumentPick.Created(provider.uri))
            }
            idleMainLooper()
            provider.deleted shouldBe listOf(provider.uri)
            provider.file.exists() shouldBe false
            ShadowToast.shownToastCount() shouldBe 2
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun documentPicked_resultOk_savesIcon() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            val file = File(ApplicationProvider.getApplicationContext<HiltTestApplication>().cacheDir, "icon_export_test.png")
            scenario.onActivity { activity ->
                pickDocument(activity, DocumentPick.Created(Uri.fromFile(file)))
            }
            idleMainLooper()
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
            file.length() shouldBeGreaterThan 0L
        }
    }

    @Test
    fun documentPicked_rotationDuringWrite_finishesWriteAndAdmitsNextSave() {
        val document = createPickedDocument()
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            pausableIoDispatcher.pause()
            scenario.onActivity { activity ->
                pickDocument(activity, DocumentPick.Created(Uri.fromFile(document)))
            }
            idleMainLooper()
            document.length() shouldBe 0L
            scenario.recreate()
            pausableIoDispatcher.resume()
            idleMainLooper()
            document.hasPngSignature() shouldBe true
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_icon_save_as_image)) shouldBe true
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivityForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            }
        }
    }

    @Test
    fun documentPicked_resultCanceled_showsNoToast() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                pickDocument(activity, DocumentPick.Canceled)
            }
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun documentPicked_resultOkWithoutUri_showsCreateError() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                pickDocument(activity, DocumentPick.MissingUri)
            }
            idleMainLooper()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun onColorPicked_isBackground_true() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.RED, true)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.style.backgroundColor shouldBe Color.RED
                state.recentBackgroundColors shouldBe listOf(Color.RED, 0xFF0381FE.toInt())
            }
        }
    }

    @Test
    fun onColorPicked_isBackground_false() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLUE, false)
                val state = ViewModelProvider(activity)[IconViewModel::class.java].state.value
                state.style.foregroundColor shouldBe Color.BLUE
                state.recentForegroundColors.first() shouldBe Color.BLUE
            }
        }
    }

    @Test
    fun colorButtons_colorDisabled_showDisabledPresentation() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.WHITE, isBackground = true)
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(Color.BLACK, isBackground = false)
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(translucentBlack, isBackground = false)
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.onColorPicked(translucentBlack, isBackground = false)
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Example App icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun icon_contentDescription_namesApp_inGerman() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Icon von Example App"
            }
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun icon_contentDescription_namesApp_inLandscape() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Example App icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de-land")
    fun icon_contentDescription_namesApp_inGermanLandscape() {
        launchWithAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "Icon von Example App"
            }
        }
    }

    @Test
    fun icon_contentDescription_withoutAppName_keepsGenericFallback() {
        launchWithoutAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "App icon"
            }
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun icon_contentDescription_withoutAppName_keepsGermanGenericFallback() {
        launchWithoutAppInfo().use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.icon).contentDescription shouldBe "App-Icon"
            }
        }
    }

    private fun IconActivity.preparedExportMenuItemsEnabled(): List<Boolean> {
        val menu = PopupMenu(this, findViewById(R.id.icon)).menu
        menuInflater.inflate(R.menu.menu_icon, menu)
        onPrepareOptionsMenu(menu)
        return listOf(R.id.menu_item_icon_save_as_image, R.id.menu_item_icon_share).map { menu.findItem(it).isEnabled }
    }

    private fun createPickedDocument(): File =
        File(ApplicationProvider.getApplicationContext<HiltTestApplication>().cacheDir, "icon_export_test.png").apply { createNewFile() }

    companion object {
        private val testBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    }
}
