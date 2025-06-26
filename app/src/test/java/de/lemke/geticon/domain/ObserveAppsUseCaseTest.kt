package de.lemke.geticon.domain

import de.lemke.geticon.data.AppsRepository
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveAppsUseCaseTest {

    private lateinit var observeAppsUseCase: ObserveAppsUseCase
    private val mockAppsRepository: AppsRepository = mock()
    private val mockUserSettingsRepository: UserSettingsRepository = mock()

    private val app1 = AppsRepository.App("com.app1", "App One", false)
    private val app2 = AppsRepository.App("com.appsystem", "System App", true)
    private val app3 = AppsRepository.App("com.another", "Another App", false)
    private val allApps = listOf(app1, app2, app3)

    @Before
    fun setUp() {
        observeAppsUseCase = ObserveAppsUseCase(mockUserSettingsRepository, mockAppsRepository)
        whenever(mockAppsRepository.get()).thenReturn(allApps)
    }

    @Test
    fun `invoke with null query and showSystemApps true returns all apps`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        val searchQueryFlow = flowOf<String?>(null)

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(listOf("com.app1", "com.appsystem", "com.another"), result)
    }

    @Test
    fun `invoke with null query and showSystemApps false returns non-system apps`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(false))
        val searchQueryFlow = flowOf<String?>(null)

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(listOf("com.app1", "com.another"), result)
    }

    @Test
    fun `invoke with blank query returns all apps regardless of showSystemApps`() = runTest {
        // Test with showSystemApps = true
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        var searchQueryFlow = flowOf<String?>(" ")
        var result = observeAppsUseCase(searchQueryFlow).first()
        assertEquals(listOf("com.app1", "com.appsystem", "com.another"), result)

        // Test with showSystemApps = false
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(false))
        searchQueryFlow = flowOf<String?>("") // Empty string also blank
        result = observeAppsUseCase(searchQueryFlow).first()
        assertEquals(listOf("com.app1", "com.appsystem", "com.another"), result)
    }

    @Test
    fun `invoke with search query filters by label`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true)) // Does not matter for search
        val searchQueryFlow = flowOf<String?>("System")

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(listOf("com.appsystem"), result)
    }

    @Test
    fun `invoke with search query filters by package name`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        val searchQueryFlow = flowOf<String?>("com.another")

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(listOf("com.another"), result)
    }

    @Test
    fun `invoke with search query filters case-insensitively`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        val searchQueryFlow = flowOf<String?>("app one")

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(listOf("com.app1"), result)
    }

    @Test
    fun `invoke with non-matching search query returns empty list`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        val searchQueryFlow = flowOf<String?>("nonexistent")

        val result = observeAppsUseCase(searchQueryFlow).first()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `invoke reacts to changes in showSystemApps`() = runTest {
        val showSystemAppsFlow = flowOf(true, false) // Emits true then false
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(showSystemAppsFlow)
        val searchQueryFlow = flowOf<String?>(null)

        val results = mutableListOf<List<String>>()
        observeAppsUseCase(searchQueryFlow).collect { results.add(it) }


        // Based on the distinctUntilChanged in the UseCase, we might only get one emission if the list doesn't change.
        // However, the list *does* change when showSystemApps changes from true to false with a null query.
        assertEquals(2, results.size)
        assertEquals(listOf("com.app1", "com.appsystem", "com.another"), results[0]) // showSystemApps = true
        assertEquals(listOf("com.app1", "com.another"), results[1])          // showSystemApps = false
    }

    @Test
    fun `invoke reacts to changes in search query`() = runTest {
        whenever(mockUserSettingsRepository.observeShowSystemApps()).thenReturn(flowOf(true))
        val searchQueryFlow = flowOf<String?>(null, "System", "App One") // Emits null, then "System", then "App One"

        val results = mutableListOf<List<String>>()
        observeAppsUseCase(searchQueryFlow).collect { results.add(it) }

        assertEquals(3, results.size)
        assertEquals(listOf("com.app1", "com.appsystem", "com.another"), results[0]) // query = null
        assertEquals(listOf("com.appsystem"), results[1])                            // query = "System"
        assertEquals(listOf("com.app1"), results[2])                                 // query = "App One"
    }
}
