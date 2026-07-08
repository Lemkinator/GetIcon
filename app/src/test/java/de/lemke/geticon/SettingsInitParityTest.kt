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

package de.lemke.geticon

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.applyDarkMode
import de.lemke.geticon.data.UserSettings
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App.kt eagerly constructs the real [UserSettings] singleton via Hilt; TestApp.kt (which can't reach
 * Hilt at `onCreate()` time — see TestApp.kt) constructs a plain base [SettingsRepository] on the same
 * SharedPreferences file instead. This only stays safe because `applyDarkMode()` is a
 * [SettingsRepository] extension resolved statically, not virtually — so it behaves identically no
 * matter which subtype wraps the preferences. If this test ever fails, [UserSettings] has shadowed a
 * base field `applyDarkMode()` reads, and the App.kt/TestApp.kt init paths have silently diverged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SettingsInitParityTest {
    @Test
    fun `applyDarkMode behaves identically for SettingsRepository and UserSettings on the same prefs`() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        SettingsRepository(prefs).apply {
            autoDarkMode = false
            darkMode = true
        }

        SettingsRepository(prefs).applyDarkMode()
        val baseResult = AppCompatDelegate.getDefaultNightMode()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
        UserSettings(prefs).applyDarkMode()
        val subclassResult = AppCompatDelegate.getDefaultNightMode()

        subclassResult shouldBe baseResult
        baseResult shouldBe AppCompatDelegate.MODE_NIGHT_YES
    }
}
