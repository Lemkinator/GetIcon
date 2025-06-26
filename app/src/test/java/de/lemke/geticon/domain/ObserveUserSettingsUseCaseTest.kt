package de.lemke.geticon.domain

import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveUserSettingsUseCaseTest {

    private lateinit var observeUserSettingsUseCase: ObserveUserSettingsUseCase
    private val mockUserSettingsRepository: UserSettingsRepository = mock()

    @Before
    fun setUp() {
        observeUserSettingsUseCase = ObserveUserSettingsUseCase(mockUserSettingsRepository)
    }

    @Test
    fun `invoke returns Flow of UserSettings from repository`() = runTest {
        val expectedUserSettings = UserSettings(
            showSystemApps = false,
            iconSize = 512,
            maskEnabled = false,
            colorEnabled = true,
            recentBackgroundColors = listOf(10),
            recentForegroundColors = listOf(20)
        )
        whenever(mockUserSettingsRepository.observeSettings()).thenReturn(flowOf(expectedUserSettings))

        val resultFlow = observeUserSettingsUseCase()
        val result = resultFlow.first() // Collect the first emitted value

        assertEquals(expectedUserSettings, result)
    }
}
