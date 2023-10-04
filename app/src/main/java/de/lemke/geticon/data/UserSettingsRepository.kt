package de.lemke.geticon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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
            it[KEY_LAST_VERSION_CODE] = newSettings.lastVersionCode
            it[KEY_LAST_VERSION_NAME] = newSettings.lastVersionName
            it[KEY_DARK_MODE] = newSettings.darkMode
            it[KEY_AUTO_DARK_MODE] = newSettings.autoDarkMode
            it[KEY_TOS_ACCEPTED] = newSettings.tosAccepted
            it[KEY_DEV_MODE_ENABLED] = newSettings.devModeEnabled
            it[KEY_SEARCH] = newSettings.search
            it[KEY_SHOW_SYSTEM_APPS] = newSettings.showSystemApps
            it[KEY_MASK] = newSettings.mask
            it[KEY_COLOR_ENABLED] = newSettings.colorEnabled
            it[KEY_RECENT_BACKGROUND_COLORS] = newSettings.recentBackgroundColors.joinToString(",")
            it[KEY_RECENT_FOREGROUND_COLORS] = newSettings.recentForegroundColors.joinToString(",")
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        darkMode = prefs[KEY_DARK_MODE] ?: true,
        autoDarkMode = prefs[KEY_AUTO_DARK_MODE] ?: true,
        tosAccepted = prefs[KEY_TOS_ACCEPTED] ?: false,
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] ?: false,
        search = prefs[KEY_SEARCH] ?: "",
        showSystemApps = prefs[KEY_SHOW_SYSTEM_APPS] ?: false,
        mask = prefs[KEY_MASK] ?: true,
        colorEnabled = prefs[KEY_COLOR_ENABLED] ?: false,
        recentBackgroundColors = prefs[KEY_RECENT_BACKGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-16547330),
        recentForegroundColors = prefs[KEY_RECENT_FOREGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-1),
    )


    private companion object {
        private val KEY_LAST_VERSION_CODE = intPreferencesKey("lastVersionCode")
        private val KEY_LAST_VERSION_NAME = stringPreferencesKey("lastVersionName")
        private val KEY_DARK_MODE = booleanPreferencesKey("darkMode")
        private val KEY_AUTO_DARK_MODE = booleanPreferencesKey("autoDarkMode")
        private val KEY_TOS_ACCEPTED = booleanPreferencesKey("tosAccepted")
        private val KEY_DEV_MODE_ENABLED = booleanPreferencesKey("devModeEnabled")
        private val KEY_SEARCH = stringPreferencesKey("search")
        private val KEY_SHOW_SYSTEM_APPS = booleanPreferencesKey("showSystemApps")
        private val KEY_MASK = booleanPreferencesKey("mask")
        private val KEY_COLOR_ENABLED = booleanPreferencesKey("colorEnabled")
        private val KEY_RECENT_BACKGROUND_COLORS = stringPreferencesKey("recentBackgroundColors")
        private val KEY_RECENT_FOREGROUND_COLORS = stringPreferencesKey("recentForegroundColors")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** Last App-Version-Code */
    val lastVersionCode: Int,
    /** Last App-Version-Name */
    val lastVersionName: String,
    /** Dark Mode enabled */
    val darkMode: Boolean,
    /** Auto Dark Mode enabled */
    val autoDarkMode: Boolean,
    /** terms of service accepted by user */
    val tosAccepted: Boolean,
    /** devMode enabled */
    val devModeEnabled: Boolean,
    /** search */
    val search: String,
    /** show system apps*/
    val showSystemApps: Boolean,
    /** mask*/
    val mask: Boolean,
    /** color enabled*/
    val colorEnabled: Boolean,
    /** recent background colors */
    val recentBackgroundColors: List<Int>,
    /** recent foreground colors */
    val recentForegroundColors: List<Int>,

)
