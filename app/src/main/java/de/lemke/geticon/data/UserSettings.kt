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

import android.content.SharedPreferences
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.delegates
import de.lemke.commonutils.data.sanitized

/** GetIcon-specific settings, layered on top of common-utils [SettingsRepository]. */
class UserSettings(
    preferences: SharedPreferences,
) : SettingsRepository(preferences) {
    var iconSize: Int by preferences.delegates.int(DEFAULT_ICON_SIZE).sanitized { it.coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE) }
    var maskEnabled: Boolean by preferences.delegates.boolean(true)
    var colorEnabled: Boolean by preferences.delegates.boolean(false)
    var recentForegroundColors: List<Int> by preferences.delegates
        .intList(listOf(DEFAULT_FOREGROUND_COLOR))
        .sanitized { it.take(MAX_RECENT_COLORS) }
    var recentBackgroundColors: List<Int> by preferences.delegates
        .intList(listOf(DEFAULT_BACKGROUND_COLOR))
        .sanitized { it.take(MAX_RECENT_COLORS) }

    companion object {
        const val DEFAULT_FOREGROUND_COLOR = -1
        const val DEFAULT_BACKGROUND_COLOR = 0xFF0381FE.toInt()
        const val DEFAULT_ICON_SIZE = 512
        const val MIN_ICON_SIZE = 16
        const val MAX_ICON_SIZE = 1024
        const val MAX_RECENT_COLORS = 6
    }
}
