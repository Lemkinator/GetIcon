package de.lemke.geticon.domain

import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetUserSettingsUseCaseTest {

    private lateinit var getUserSettingsUseCase: GetUserSettingsUseCase
    private val mockUserSettingsRepository: UserSettingsRepository = mock()

    @Before
    fun setUp() {
        getUserSettingsUseCase = GetUserSettingsUseCase(mockUserSettingsRepository)
    }

    @Test
    fun `invoke returns UserSettings from repository`() = runTest {
        val expectedUserSettings = UserSettings(
            showSystemApps = true,
            iconSize = 256,
            maskEnabled = true,
            colorEnabled = false,
            recentBackgroundColors = listOf(1, 2),
            recentForegroundColors = listOf(3, 4)
        )
        whenever(mockUserSettingsRepository.getSettings()).thenReturn(expectedUserSettings)

        val result = getUserSettingsUseCase()

        assertEquals(expectedUserSettings, result)
    }
}
