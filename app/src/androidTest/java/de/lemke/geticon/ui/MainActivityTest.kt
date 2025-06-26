package de.lemke.geticon.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.data.CommonUtilsSettings // Import for direct access if needed for setup
import de.lemke.commonutils.data.PreferencesDataStoreModule
import de.lemke.geticon.PersistenceModule
import de.lemke.geticon.R
import de.lemke.geticon.data.AppsRepository
import de.lemke.geticon.data.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.inject.Inject
import javax.inject.Named


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
// Uninstall original modules and rely on TestAppModule for DataStore provisions
@UninstallModules(PersistenceModule::class, PreferencesDataStoreModule::class)
class MainActivityTest {

    private val hiltRule = HiltAndroidRule(this)
    private val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(hiltRule)
        .around(activityRule)

    // UserSettingsRepository will be injected by Hilt using the DataStore from TestAppModule
    // No need for @BindValue for DataStores if TestAppModule provides them.
    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    // commonUtilsSettings is an object, so we access it directly after its DataStore is managed by Hilt
    // We need the DataStore instance that common-utils will use, provided by TestAppModule.
    @Inject
    @Named("commonUtilsDataStore") // Ensure this matches the @Provides in TestAppModule
    lateinit var commonUtilsDataStore: DataStore<Preferences>

    // Mock AppsRepository
    @BindValue // Still useful to BindValue for mocks of our direct dependencies
    @JvmField
    val mockAppsRepository: AppsRepository = mock()

    @Inject
    @ApplicationContext // For accessing application context if needed
    lateinit var context: Context


    @Before
    fun setUp() = runTest {
        hiltRule.inject() // Ensures all @Inject fields are populated

        // Ensure TOS is accepted to bypass OOBE for most tests
        // We now have access to the DataStore instance common-utils will use.
        commonUtilsDataStore.edit { settings ->
            settings[CommonUtilsSettings.KEY_TOS_ACCEPTED] = true
        }
        // To ensure the value is written before the activity starts commonUtils code that reads it,
        // it's good practice to make sure this coroutine completes.
        // However, runTest should handle this for edit.

        // Setup mock apps for AppsRepository
        val mockPackageManager: PackageManager = mock()
        val appInfo1 = ApplicationInfo().apply {
            packageName = "com.example.app1"
            flags = 0 // Not a system app
            name = "App One"
        }
        whenever(appInfo1.loadLabel(any())).thenReturn("App One")

        val packageInfo1 = PackageInfo().apply {
            packageName = "com.example.app1"
            applicationInfo = appInfo1
        }

        val appInfo2 = ApplicationInfo().apply {
            packageName = "com.example.systemapp"
            flags = ApplicationInfo.FLAG_SYSTEM // System app
            name = "System App"
        }
        whenever(appInfo2.loadLabel(any())).thenReturn("System App")

        val packageInfo2 = PackageInfo().apply {
            packageName = "com.example.systemapp"
            applicationInfo = appInfo2
        }

        val installedPackages = listOf(packageInfo1, packageInfo2)
        whenever(mockPackageManager.getInstalledPackages(PackageManager.GET_META_DATA)).thenReturn(installedPackages)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            whenever(mockPackageManager.getInstalledPackages(any<PackageManager.PackageInfoFlags>())).thenReturn(installedPackages)
        }
        whenever(context.packageManager).thenReturn(mockPackageManager) // Provide the mocked PackageManager

        // Configure AppsRepository mock
        val app1Data = AppsRepository.App("com.example.app1", "App One", false)
        val app2Data = AppsRepository.App("com.example.systemapp", "System App", true)
        whenever(mockAppsRepository.get()).thenReturn(listOf(app1Data, app2Data))
    }

    @After
    fun tearDown() = runTest {
        // Clear the DataStores after each test
        userSettingsRepository.updateSettings { UserSettings(true, 512, true, false, listOf(-16547330), listOf(-1)) } // Reset to defaults
        commonUtilsDataStore.edit { it.clear() } // Clear common utils settings
    }

    @Test
    fun appLaunchesAndDisplaysAppList_whenTosAccepted() = runTest {
        // TOS is accepted in setUp() via commonUtilsDataStore

        // Check if app list is displayed
        onView(withId(R.id.app_picker_list)).check(matches(isDisplayed()))
        // Check for one of our mock apps
        onView(withText("App One")).check(matches(isDisplayed()))
        onView(withText("System App")).check(matches(isDisplayed())) // Assuming default is to show system apps initially or it's set in test
    }

    @Test
    fun searchFiltersAppList() = runTest {
        // TOS accepted in setUp

        // Open search
        onView(withId(R.id.menu_item_search)).perform(click())
        // Type search query
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(androidx.test.espresso.action.ViewActions.typeText("System"))

        // Check that only "System App" is visible
        onView(withText("System App")).check(matches(isDisplayed()))
        onView(withText("App One")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
    }

    @Test
    fun toggleShowSystemAppsFiltersAppList() = runTest {
        // TOS accepted in setUp
        // Initially, UserSettings default to showSystemApps = true, so both apps are shown by mockRepo
        // (Assuming ObserveAppsUseCase passes through AppsRepository.get() when showSystemApps is true)

        // Click "Hide system apps"
        onView(withId(R.id.menu_item_show_system_apps)).perform(click())
        // After clicking, userSettings.showSystemApps should be false.
        // ObserveAppsUseCase should now filter out system apps.

        // Check that "System App" is no longer visible
        onView(withText("System App")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withText("App One")).check(matches(isDisplayed()))

        // Click "Show system apps" again
        onView(withId(R.id.menu_item_show_system_apps)).perform(click())
        // userSettings.showSystemApps should be true again.

        // Check that "System App" is visible again
        onView(withText("System App")).check(matches(isDisplayed()))
        onView(withText("App One")).check(matches(isDisplayed()))
    }


    @Test
    fun navigationToAboutActivity() {
        // TOS accepted in setUp
        onView(withId(R.id.drawer_layout)).perform(androidx.test.espresso.contrib.DrawerActions.open())
        onView(withId(R.id.navigation_view)).perform(androidx.test.espresso.contrib.NavigationViewActions.navigateTo(R.id.about_app_dest))
        // Check if AboutActivity is displayed (e.g., by checking for a view unique to AboutActivity)
        // This requires knowing a view ID from common-utils' AboutActivity.
        // For example, if it has a toolbar with a specific title:
        // onView(withText(de.lemke.commonutils.R.string.commonutils_about_app)).check(matches(isDisplayed()))
        // Or check for a known view ID:
        onView(withId(de.lemke.commonutils.R.id.commonutils_toolbar_layout)).check(matches(isDisplayed()))
    }

     @Test
    fun navigationToSettingsActivity() {
        // TOS accepted in setUp
        onView(withId(R.id.drawer_layout)).perform(androidx.test.espresso.contrib.DrawerActions.open())
        onView(withId(R.id.navigation_view)).perform(androidx.test.espresso.contrib.NavigationViewActions.navigateTo(R.id.settings_dest))
        // Check if SettingsActivity is displayed
        onView(withId(de.lemke.commonutils.R.id.commonutils_toolbar_layout)).check(matches(isDisplayed())) // Assuming same toolbar
        onView(withText(de.lemke.commonutils.R.string.commonutils_settings)).check(matches(isDisplayed())) // Check for title
    }

    // Test for OOBE flow
    @Test
    fun oobeFlow_whenTosNotAccepted() = runTest {
        // Explicitly set TOS to not accepted for this test
        commonUtilsDataStore.edit { settings ->
            settings[CommonUtilsSettings.KEY_TOS_ACCEPTED] = false
        }
        // Re-launch activity or ensure this is the first launch in this state
        // ActivityScenarioRule should handle launching a new activity instance
        activityRule.scenario.relaunch() // Relaunch to ensure OOBE is shown

        // Check if OOBEActivity is displayed by looking for a view unique to OOBE
        onView(withId(R.id.oobe_intro_footer_button)).check(matches(isDisplayed()))
        // Click accept button
        onView(withId(R.id.oobe_intro_footer_button)).perform(click())
        // Check if MainActivity is now displayed (e.g. app_picker_list)
        // Adding a small delay for the transition and data loading might be necessary
        Thread.sleep(1000) // Not ideal, but can help with UI test flakiness for transitions
        onView(withId(R.id.app_picker_list)).check(matches(isDisplayed()))
    }

    // Note: Test for IconActivity navigation and APK extraction will be more involved
    // and might require mocking ActivityResultLauncher, file system interactions, etc.
    // For IconActivity navigation:
    @Test
    fun clickingApp_navigatesToIconActivity() = runTest {
        // TOS accepted in setUp
        // Ensure user settings don't hide the app we want to click
        userSettingsRepository.updateSettings { it.copy(showSystemApps = true) }

        // Mock PackageManager for getApplicationInfo in IconActivity transition
        val appInfo = ApplicationInfo().apply {
            packageName = "com.example.app1"
            // other fields if necessary for IconActivity
        }
        whenever(context.packageManager.getApplicationInfo("com.example.app1", 0)).thenReturn(appInfo)


        onView(withText("App One")).perform(click())
        // Check if IconActivity is displayed by checking for the ImageView that shows the icon
        onView(withId(R.id.icon)).check(matches(isDisplayed()))
    }

}
