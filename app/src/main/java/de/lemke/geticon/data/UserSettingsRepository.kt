package de.lemke.geticon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.lemke.commonutils.SaveLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Provides CRUD operations for user settings. */
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Returns the current user settings. */
    suspend fun getSettings(): UserSettings = dataStore.data.map(::settingsFromPreferences).first()

    /** Emits the current user settings. */
    fun observeSettings(): Flow<UserSettings> = dataStore.data.map(::settingsFromPreferences)

    /** Emits the current showSystemApps setting. */
    fun observeShowSystemApps(): Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_SYSTEM_APPS] == true }.distinctUntilChanged()

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
            it[KEY_ICON_SIZE] = newSettings.iconSize
            it[KEY_MASK_ENABLED] = newSettings.maskEnabled
            it[KEY_COLOR_ENABLED] = newSettings.colorEnabled
            it[KEY_RECENT_BACKGROUND_COLORS] = newSettings.recentBackgroundColors.joinToString(",")
            it[KEY_RECENT_FOREGROUND_COLORS] = newSettings.recentForegroundColors.joinToString(",")
            it[KEY_SAVE_LOCATION] = newSettings.saveLocation.ordinal
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        darkMode = prefs[KEY_DARK_MODE] != false,
        autoDarkMode = prefs[KEY_AUTO_DARK_MODE] != false,
        tosAccepted = prefs[KEY_TOS_ACCEPTED] == true,
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] == true,
        search = prefs[KEY_SEARCH] ?: "",
        showSystemApps = prefs[KEY_SHOW_SYSTEM_APPS] == true,
        iconSize = prefs[KEY_ICON_SIZE] ?: 512,
        maskEnabled = prefs[KEY_MASK_ENABLED] != false,
        colorEnabled = prefs[KEY_COLOR_ENABLED] == true,
        recentBackgroundColors = prefs[KEY_RECENT_BACKGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-16547330),
        recentForegroundColors = prefs[KEY_RECENT_FOREGROUND_COLORS]?.split(",")?.map { it.toInt() } ?: listOf(-1),
        saveLocation = SaveLocation.entries[prefs[KEY_SAVE_LOCATION] ?: SaveLocation.default.ordinal],
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
        private val KEY_ICON_SIZE = intPreferencesKey("iconSize")
        private val KEY_MASK_ENABLED = booleanPreferencesKey("maskEnabled")
        private val KEY_COLOR_ENABLED = booleanPreferencesKey("colorEnabled")
        private val KEY_RECENT_BACKGROUND_COLORS = stringPreferencesKey("recentBackgroundColors")
        private val KEY_RECENT_FOREGROUND_COLORS = stringPreferencesKey("recentForegroundColors")
        private val KEY_SAVE_LOCATION = intPreferencesKey("saveLocation")
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
    /** save location */
    val saveLocation: SaveLocation,
)
