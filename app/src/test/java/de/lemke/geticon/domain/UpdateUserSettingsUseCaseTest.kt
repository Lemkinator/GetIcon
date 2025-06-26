package de.lemke.geticon.domain

import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpdateUserSettingsUseCaseTest {

    private lateinit var updateUserSettingsUseCase: UpdateUserSettingsUseCase
    private val mockUserSettingsRepository: UserSettingsRepository = mock()

    @Before
    fun setUp() {
        updateUserSettingsUseCase = UpdateUserSettingsUseCase(mockUserSettingsRepository)
    }

    @Test
    fun `invoke calls repository updateSettings with correct transform function`() = runTest {
        val originalSettings = UserSettings(
            showSystemApps = true,
            iconSize = 256,
            maskEnabled = true,
            colorEnabled = false,
            recentBackgroundColors = listOf(1),
            recentForegroundColors = listOf(2)
        )
        val expectedUpdatedSettings = UserSettings(
            showSystemApps = false,
            iconSize = 512,
            maskEnabled = false,
            colorEnabled = true,
            recentBackgroundColors = listOf(10),
            recentForegroundColors = listOf(20)
        )

        // Mock the repository's updateSettings to return the settings passed to the transform
        whenever(mockUserSettingsRepository.updateSettings(any())).thenAnswer { invocation ->
            val transform = invocation.getArgument<(UserSettings) -> UserSettings>(0)
            transform(originalSettings) // Apply the transform immediately
        }


        val transformFunction: (UserSettings) -> UserSettings = {
            it.copy(
                showSystemApps = false,
                iconSize = 512,
                maskEnabled = false,
                colorEnabled = true,
                recentBackgroundColors = listOf(10),
                recentForegroundColors = listOf(20)
            )
        }

        val result = updateUserSettingsUseCase(transformFunction)

        // Verify that repository.updateSettings was called
        verify(mockUserSettingsRepository).updateSettings(any())

        // Capture the argument passed to repository.updateSettings
        val captor = argumentCaptor<(UserSettings) -> UserSettings>()
        verify(mockUserSettingsRepository).updateSettings(captor.capture())

        // Apply the captured function to the original settings and check the result
        val transformedSettings = captor.firstValue(originalSettings)
        assertEquals(expectedUpdatedSettings, transformedSettings)
        assertEquals(expectedUpdatedSettings, result)
    }
}
