package de.lemke.geticon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
    suspend fun updateSettings(f: (UserSettings) -> UserSettings): UserSettings {
        val prefs = dataStore.edit {
            val newSettings = f(settingsFromPreferences(it))
            it[KEY_ICON_SIZE] = newSettings.iconSize
            it[KEY_MASK_ENABLED] = newSettings.maskEnabled
            it[KEY_COLOR_ENABLED] = newSettings.colorEnabled
            it[KEY_RECENT_BACKGROUND_COLORS] = newSettings.recentBackgroundColors.joinToString(",")
            it[KEY_RECENT_FOREGROUND_COLORS] = newSettings.recentForegroundColors.joinToString(",")
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        iconSize = prefs[KEY_ICON_SIZE] ?: 512,
        maskEnabled = prefs[KEY_MASK_ENABLED] != false,
        colorEnabled = prefs[KEY_COLOR_ENABLED] == true,
        recentBackgroundColors = prefs[KEY_RECENT_BACKGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-16547330),
        recentForegroundColors = prefs[KEY_RECENT_FOREGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-1),
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
)
