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

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.FakeBitmapExporter
import de.lemke.commonutils.ui.utils.FakeBitmapExporter.Call
import de.lemke.geticon.data.FakeApkImporter
import de.lemke.geticon.data.FakeIconRenderer
import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.domain.GenerateIconUseCase
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class IconViewModelSaveTest {
    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val renderer = FakeIconRenderer(icon)
    private val exporter = FakeBitmapExporter()
    private val apkImporter = FakeApkImporter()
    private val settings = UserSettings(freshTestPreferences())
    private val appInfo = ApplicationInfo().also { it.packageName = "com.example.test" }

    @After
    fun tearDown() {
        apkImporter.cacheDir.deleteRecursively()
    }

    private fun saveToDownloads(): IconViewModel {
        settings.imageSaveLocation = SaveLocation.DOWNLOADS
        val viewModel =
            IconViewModel(
                SavedStateHandle(mapOf(IconActivity.KEY_APPLICATION_INFO to appInfo)),
                settings,
                GenerateIconUseCase(renderer),
                renderer,
                exporter,
                apkImporter,
            )
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.onSave()
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    @Test
    fun `onSave writes the icon to the stored location and holds the saved result`() {
        val viewModel = saveToDownloads()
        exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.DOWNLOADS, icon, "com.example.test_mask"))
        viewModel.export.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
    }

    @Test
    fun `onSave holds a failed write to the stored location`() {
        exporter.directoryResult = BitmapSaveResult.WriteFailed
        val viewModel = saveToDownloads()
        viewModel.export.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
    }
}
