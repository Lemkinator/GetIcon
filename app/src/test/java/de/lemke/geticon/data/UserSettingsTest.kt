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

package de.lemke.geticon.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_BACKGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_FOREGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_RECENT_COLORS
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins GetIcon's own settings invariants (defaults, clamping, capping, malformed-input fallback) against the real [UserSettings]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UserSettingsTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: UserSettings

    private fun reload() = UserSettings(prefs)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("user_settings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        settings = UserSettings(prefs)
    }

    @Test
    fun `defaults on fresh store`() {
        settings.iconSize shouldBe DEFAULT_ICON_SIZE
        settings.maskEnabled.shouldBeTrue()
        settings.colorEnabled.shouldBeFalse()
        settings.recentForegroundColors shouldBe listOf(DEFAULT_FOREGROUND_COLOR)
        settings.recentBackgroundColors shouldBe listOf(DEFAULT_BACKGROUND_COLOR)
    }

    @Test
    fun `iconSize round-trips a value within range`() {
        val mid = (MIN_ICON_SIZE + MAX_ICON_SIZE) / 2
        settings.iconSize = mid
        reload().iconSize shouldBe mid
    }

    @Test
    fun `iconSize clamps to MIN_ICON_SIZE when written below range`() {
        settings.iconSize = MIN_ICON_SIZE - 1
        settings.iconSize shouldBe MIN_ICON_SIZE
    }

    @Test
    fun `iconSize clamps to MAX_ICON_SIZE when written above range`() {
        settings.iconSize = MAX_ICON_SIZE + 1
        settings.iconSize shouldBe MAX_ICON_SIZE
    }

    @Test
    fun `iconSize persists the clamped value rather than the raw write`() {
        settings.iconSize = MAX_ICON_SIZE + 500
        prefs.getInt("iconSize", -1) shouldBe MAX_ICON_SIZE
    }

    @Test
    fun `maskEnabled round-trips false`() {
        settings.maskEnabled = false
        reload().maskEnabled.shouldBeFalse()
    }

    @Test
    fun `colorEnabled round-trips true`() {
        settings.colorEnabled = true
        reload().colorEnabled.shouldBeTrue()
    }

    @Test
    fun `recentForegroundColors round-trips`() {
        val colors = listOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt())
        settings.recentForegroundColors = colors
        reload().recentForegroundColors shouldBe colors
    }

    @Test
    fun `recentBackgroundColors round-trips`() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        settings.recentBackgroundColors = colors
        reload().recentBackgroundColors shouldBe colors
    }

    @Test
    fun `recentForegroundColors caps to MAX_RECENT_COLORS when more are written`() {
        val colors = (1..MAX_RECENT_COLORS + 1).map { 0xFF000000.toInt() + it }
        settings.recentForegroundColors = colors
        reload().recentForegroundColors.size shouldBe MAX_RECENT_COLORS
    }

    @Test
    fun `recentBackgroundColors caps to MAX_RECENT_COLORS when more are written`() {
        val colors = (1..MAX_RECENT_COLORS + 1).map { 0xFF000000.toInt() + it }
        settings.recentBackgroundColors = colors
        reload().recentBackgroundColors.size shouldBe MAX_RECENT_COLORS
    }

    @Test
    fun `recentForegroundColors falls back to default when stored string is all-invalid`() {
        prefs.edit().putString("recentForegroundColors", "abc,,xyz").apply()
        reload().recentForegroundColors shouldBe listOf(DEFAULT_FOREGROUND_COLOR)
    }

    @Test
    fun `recentBackgroundColors falls back to default on empty stored string`() {
        prefs.edit().putString("recentBackgroundColors", "").apply()
        reload().recentBackgroundColors shouldBe listOf(DEFAULT_BACKGROUND_COLOR)
    }

    @Test
    fun `recentForegroundColors keeps only valid integers from mixed stored input`() {
        val validColor = 0xFF0381FE.toInt()
        prefs.edit().putString("recentForegroundColors", "abc,$validColor").apply()
        reload().recentForegroundColors shouldBe listOf(validColor)
    }

    @Test
    fun `recentBackgroundColors falls back to default when stored string is all-invalid`() {
        prefs.edit().putString("recentBackgroundColors", "abc,,xyz").apply()
        reload().recentBackgroundColors shouldBe listOf(DEFAULT_BACKGROUND_COLOR)
    }

    @Test
    fun `recentBackgroundColors keeps only valid integers from mixed stored input`() {
        val validColor = 0xFF0381FE.toInt()
        prefs.edit().putString("recentBackgroundColors", "abc,$validColor").apply()
        reload().recentBackgroundColors shouldBe listOf(validColor)
    }
}
