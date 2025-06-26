package de.lemke.geticon.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.data.CommonUtilsSettings
import de.lemke.commonutils.data.PreferencesDataStoreModule
import de.lemke.geticon.PersistenceModule
import de.lemke.geticon.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Named
import de.lemke.commonutils.R as commonutilsR

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
@UninstallModules(PersistenceModule::class, PreferencesDataStoreModule::class) // Use TestAppModule
class OOBEActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(OOBEActivity::class.java)

    @Inject
    @Named("commonUtilsDataStore")
    lateinit var commonUtilsDataStore: DataStore<Preferences>

    @Before
    fun setUp() = runTest {
        hiltRule.inject()
        // Ensure TOS is NOT accepted before each OOBE test
        commonUtilsDataStore.edit { settings ->
            settings[CommonUtilsSettings.KEY_TOS_ACCEPTED] = false
        }
        Intents.init()
    }

    @After
    fun tearDown() = runTest {
        // Clear DataStores
        commonUtilsDataStore.edit { it.clear() }
        Intents.release()
    }

    @Test
    fun oobeActivityDisplaysCorrectly() {
        onView(withText(R.string.oobe_onboard_msg1_title)).check(matches(isDisplayed()))
        onView(withId(R.id.oobe_intro_footer_tos_text)).check(matches(isDisplayed()))
        onView(withId(R.id.oobe_intro_footer_button)).check(matches(isDisplayed()))
    }

    @Test
    fun clickingTosText_showsTosDialog() {
        onView(withId(R.id.oobe_intro_footer_tos_text)).perform(click()) // Click on the specific spannable part is tricky
        // Instead, we check if clicking the TextView (which contains the link) shows the dialog
        // This requires a more specific click on the link if the whole TextView is not clickable for the dialog.
        // For now, assuming general click on TextView area containing link works, or a specific part of it.
        // A better way is to click the link text directly if possible.
        // onView(withText(commonutilsR.string.commonutils_tos)).perform(click()); // This might be too generic

        // Check if AlertDialog is shown
        onView(withText(commonutilsR.string.commonutils_tos_content)).check(matches(isDisplayed()))
        onView(withText(commonutilsR.string.commonutils_ok)).perform(click()) // Dismiss dialog
    }

    @Test
    fun clickingAcceptButton_setsTosAcceptedAndNavigatesToMain() = runTest {
        // Check initial state
        var tosAccepted = false
        commonUtilsDataStore.data.first {
            tosAccepted = it[CommonUtilsSettings.KEY_TOS_ACCEPTED] ?: false
            true
        }
        assert(!tosAccepted)

        onView(withId(R.id.oobe_intro_footer_button)).perform(click())

        // Wait for coroutine in activity to complete and DataStore to update
        // A brief sleep or IdlingResource is typically needed here for reliable check
        Thread.sleep(1000) // Use IdlingResource in a real app

        commonUtilsDataStore.data.first {
            tosAccepted = it[CommonUtilsSettings.KEY_TOS_ACCEPTED] ?: false
            true
        }
        assert(tosAccepted)

        // Check if MainActivity is launched
        Intents.intended(hasComponent(MainActivity::class.java.name))
    }
}
