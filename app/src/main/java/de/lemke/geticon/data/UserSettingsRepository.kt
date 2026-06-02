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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_BACKGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_FOREGROUND_COLOR
import de.lemke.geticon.data.UserSettings.Companion.DEFAULT_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MAX_ICON_SIZE
import de.lemke.geticon.data.UserSettings.Companion.MIN_ICON_SIZE
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Provides CRUD operations for user settings. */
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Returns the current user settings. */
    suspend fun getSettings(): UserSettings = dataStore.data.map(::settingsFromPreferences).first()

    /**
     * Updates the current user settings and returns the new settings.
     * @param f Invoked with the current settings; The settings returned from this function will replace the current ones.
     */
    suspend fun updateSettings(f: (UserSettings) -> UserSettings): UserSettings =
        dataStore
            .edit {
                val newSettings = f(settingsFromPreferences(it))
                it[KEY_ICON_SIZE] = newSettings.iconSize
                it[KEY_MASK_ENABLED] = newSettings.maskEnabled
                it[KEY_COLOR_ENABLED] = newSettings.colorEnabled
                it[KEY_RECENT_BACKGROUND_COLORS] = newSettings.recentBackgroundColors.take(UserSettings.MAX_RECENT_COLORS).joinToString(",")
                it[KEY_RECENT_FOREGROUND_COLORS] = newSettings.recentForegroundColors.take(UserSettings.MAX_RECENT_COLORS).joinToString(",")
            }.let(::settingsFromPreferences)

    private fun settingsFromPreferences(prefs: Preferences) =
        UserSettings(
            iconSize = (prefs[KEY_ICON_SIZE] ?: DEFAULT_ICON_SIZE).coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE),
            maskEnabled = prefs[KEY_MASK_ENABLED] != false,
            colorEnabled = prefs[KEY_COLOR_ENABLED] == true,
            recentBackgroundColors =
                prefs[KEY_RECENT_BACKGROUND_COLORS]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.take(UserSettings.MAX_RECENT_COLORS)
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(DEFAULT_BACKGROUND_COLOR),
            recentForegroundColors =
                prefs[KEY_RECENT_FOREGROUND_COLORS]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.take(UserSettings.MAX_RECENT_COLORS)
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(DEFAULT_FOREGROUND_COLOR),
        )

    private companion object {
        private val KEY_ICON_SIZE = intPreferencesKey("iconSize")
        private val KEY_MASK_ENABLED = booleanPreferencesKey("maskEnabled")
        private val KEY_COLOR_ENABLED = booleanPreferencesKey("colorEnabled")
        private val KEY_RECENT_BACKGROUND_COLORS = stringPreferencesKey("recentBackgroundColors")
        private val KEY_RECENT_FOREGROUND_COLORS = stringPreferencesKey("recentForegroundColors")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** icon Size*/
    val iconSize: Int,
    /** mask enabled*/
    val maskEnabled: Boolean,
    /** color enabled*/
    val colorEnabled: Boolean,
    /** recent background colors */
    val recentBackgroundColors: List<Int>,
    /** recent foreground colors */
    val recentForegroundColors: List<Int>,
) {
    companion object {
        const val DEFAULT_BACKGROUND_COLOR = 0xFF0381FE.toInt()
        const val DEFAULT_FOREGROUND_COLOR = -1
        const val DEFAULT_ICON_SIZE = 512
        const val MIN_ICON_SIZE = 16
        const val MAX_ICON_SIZE = 1024
        const val MAX_RECENT_COLORS = 6
    }
}
