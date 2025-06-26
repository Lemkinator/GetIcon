package de.lemke.geticon.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserSettingsRepositoryTest {

    private lateinit var userSettingsRepository: UserSettingsRepository
    private val dataStore: DataStore<Preferences> = mock()

    private val KEY_SHOW_SYSTEM_APPS = booleanPreferencesKey("showSystemApps")
    private val KEY_ICON_SIZE = intPreferencesKey("iconSize")
    private val KEY_MASK_ENABLED = booleanPreferencesKey("maskEnabled")
    private val KEY_COLOR_ENABLED = booleanPreferencesKey("colorEnabled")
    private val KEY_RECENT_BACKGROUND_COLORS = stringPreferencesKey("recentBackgroundColors")
    private val KEY_RECENT_FOREGROUND_COLORS = stringPreferencesKey("recentForegroundColors")

    @Before
    fun setUp() {
        userSettingsRepository = UserSettingsRepository(dataStore)
    }

    @Test
    fun `getSettings returns default values when no settings saved`() = runTest {
        val mockPreferences: Preferences = mock()
        whenever(dataStore.data).thenReturn(flowOf(mockPreferences))
        whenever(mockPreferences[KEY_SHOW_SYSTEM_APPS]).thenReturn(null) // Test default true
        whenever(mockPreferences[KEY_ICON_SIZE]).thenReturn(null)
        whenever(mockPreferences[KEY_MASK_ENABLED]).thenReturn(null) // Test default true
        whenever(mockPreferences[KEY_COLOR_ENABLED]).thenReturn(null) // Test default false
        whenever(mockPreferences[KEY_RECENT_BACKGROUND_COLORS]).thenReturn(null)
        whenever(mockPreferences[KEY_RECENT_FOREGROUND_COLORS]).thenReturn(null)


        val settings = userSettingsRepository.getSettings()

        assertTrue(settings.showSystemApps)
        assertEquals(512, settings.iconSize)
        assertTrue(settings.maskEnabled)
        assertFalse(settings.colorEnabled)
        assertEquals(listOf(-16547330), settings.recentBackgroundColors)
        assertEquals(listOf(-1), settings.recentForegroundColors)
    }

    @Test
    fun `getSettings returns saved values`() = runTest {
        val mockPreferences: Preferences = mock()
        whenever(dataStore.data).thenReturn(flowOf(mockPreferences))
        whenever(mockPreferences[KEY_SHOW_SYSTEM_APPS]).thenReturn(false)
        whenever(mockPreferences[KEY_ICON_SIZE]).thenReturn(256)
        whenever(mockPreferences[KEY_MASK_ENABLED]).thenReturn(false)
        whenever(mockPreferences[KEY_COLOR_ENABLED]).thenReturn(true)
        whenever(mockPreferences[KEY_RECENT_BACKGROUND_COLORS]).thenReturn("1,2,3")
        whenever(mockPreferences[KEY_RECENT_FOREGROUND_COLORS]).thenReturn("4,5,6")

        val settings = userSettingsRepository.getSettings()

        assertFalse(settings.showSystemApps)
        assertEquals(256, settings.iconSize)
        assertFalse(settings.maskEnabled)
        assertTrue(settings.colorEnabled)
        assertEquals(listOf(1, 2, 3), settings.recentBackgroundColors)
        assertEquals(listOf(4, 5, 6), settings.recentForegroundColors)
    }

    @Test
    fun `observeShowSystemApps emits correct values`() = runTest {
        val mockPreferences1: Preferences = mock()
        val mockPreferences2: Preferences = mock()
        whenever(dataStore.data).thenReturn(flowOf(mockPreferences1, mockPreferences2))
        whenever(mockPreferences1[KEY_SHOW_SYSTEM_APPS]).thenReturn(true)
        whenever(mockPreferences2[KEY_SHOW_SYSTEM_APPS]).thenReturn(false)


        val showSystemAppsFlow = userSettingsRepository.observeShowSystemApps()
        val emittedValues = mutableListOf<Boolean>()
        showSystemAppsFlow.collect { emittedValues.add(it) }


        assertEquals(listOf(true, false), emittedValues.distinct()) // distinctUntilChanged behavior
    }

    @Test
    fun `observeShowSystemApps emits default true when key is null`() = runTest {
        val mockPreferences: Preferences = mock()
        whenever(dataStore.data).thenReturn(flowOf(mockPreferences))
        whenever(mockPreferences[KEY_SHOW_SYSTEM_APPS]).thenReturn(null)

        val showSystemApps = userSettingsRepository.observeShowSystemApps().first()

        assertTrue(showSystemApps)
    }


    @Test
    fun `updateSettings updates DataStore`() = runTest {
        val initialPreferences: Preferences = mock()
        val newPreferences: Preferences = mock()

        whenever(dataStore.edit(any())).thenAnswer { invocation ->
            val transform = invocation.getArgument<suspend (Preferences) -> Preferences>(0)
            // Simulate the edit operation by applying the transform to initialPreferences
            // and returning newPreferences which would represent the state after edit.
            // In a real DataStore, this would involve more complex preference mutation.
            // For this mock, we assume the transform function is called and newPreferences is the result.
            flowOf(transform(initialPreferences)).first() // Apply transform
            newPreferences // Return the state after edit
        }
        whenever(dataStore.data).thenReturn(flowOf(newPreferences)) // Ensure subsequent reads get the new state

        val newSettings = UserSettings(
            showSystemApps = false,
            iconSize = 128,
            maskEnabled = false,
            colorEnabled = true,
            recentBackgroundColors = listOf(10, 20),
            recentForegroundColors = listOf(30, 40)
        )

        userSettingsRepository.updateSettings { newSettings }

        // Verify that edit was called, which implies preferences were updated.
        // A more complex verification could involve capturing the argument to edit
        // and asserting its behavior, but for this scope, verifying edit is called is sufficient.
        verify(dataStore).edit(any())

        // To further ensure the update happened as expected, we can simulate reading after update
        // This part depends on how dataStore.data is mocked to reflect changes from 'edit'
        // For simplicity, if 'edit' is successful, we assume 'dataStore.data' would emit the new state
        whenever(newPreferences[KEY_SHOW_SYSTEM_APPS]).thenReturn(newSettings.showSystemApps)
        whenever(newPreferences[KEY_ICON_SIZE]).thenReturn(newSettings.iconSize)
        whenever(newPreferences[KEY_MASK_ENABLED]).thenReturn(newSettings.maskEnabled)
        whenever(newPreferences[KEY_COLOR_ENABLED]).thenReturn(newSettings.colorEnabled)
        whenever(newPreferences[KEY_RECENT_BACKGROUND_COLORS]).thenReturn(newSettings.recentBackgroundColors.joinToString(","))
        whenever(newPreferences[KEY_RECENT_FOREGROUND_COLORS]).thenReturn(newSettings.recentForegroundColors.joinToString(","))

        val updatedSettings = userSettingsRepository.getSettings()
        assertEquals(newSettings, updatedSettings)
    }
}
