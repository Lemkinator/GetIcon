/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.geticon.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.geticon.R
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class IconActivityTest {
    @field:BindValue
    @get:Rule(order = 0)
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    private lateinit var appInfo: ApplicationInfo

    @Before
    fun setUp() {
        appInfo = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo
    }

    @Test
    fun activityLaunchesWithValidApplicationInfo() {
        ActivityScenario
            .launch<IconActivity>(
                Intent(ApplicationProvider.getApplicationContext(), IconActivity::class.java)
                    .putExtra(IconActivity.KEY_APPLICATION_INFO, appInfo),
            ).use { scenario ->
                scenario.state.isAtLeast(Lifecycle.State.CREATED) shouldBe true
                // setWindowTransparent(true) makes the window translucent on API 30+, which
                // causes Espresso's RootViewPicker to never get window focus. Check visibility
                // directly on the activity thread instead.
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.icon).isShown shouldBe true
                }
            }
    }
}
