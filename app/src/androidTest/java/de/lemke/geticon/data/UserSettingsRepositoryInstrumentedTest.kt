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

package de.lemke.geticon.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Confirms end-to-end Hilt wiring with an isolated test DataStore (via TestInstallIn). */
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class UserSettingsRepositoryInstrumentedTest {
    @field:BindValue
    @get:Rule(order = 0)
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repo: UserSettingsRepository

    @Before
    fun inject() = hiltRule.inject()

    @Test
    fun iconSizeRoundTrip() =
        runTest {
            repo.updateSettings { it.copy(iconSize = 200) }
            repo.getSettings().iconSize shouldBe 200
        }
}
