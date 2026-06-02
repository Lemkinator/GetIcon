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

package de.lemke.geticon.domain

import androidx.picker.di.AppPickerContext
import androidx.picker.model.AppInfo
import androidx.picker.model.AppInfoDataImpl
import androidx.picker.model.viewdata.AppInfoViewData
import androidx.test.core.app.ApplicationProvider
import de.lemke.geticon.App
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
class AppPickerStrategyTest {
    private lateinit var strategy: AppPickerStrategy

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<App>()
        strategy = AppPickerStrategy(AppPickerContext(context))
    }

    @Test
    fun `convert sets searchable to label and packageName`() {
        val appInfo = AppInfo(packageName = "de.lemke.geticon", activityName = "")
        val data = AppInfoDataImpl(appInfo, label = "GetIcon")
        val results = strategy.convert(listOf(data), null).filterIsInstance<AppInfoViewData>()
        results.first().searchable shouldBe listOf("GetIcon", "de.lemke.geticon")
    }

    @Test
    fun `convert always includes packageName in searchable`() {
        val appInfo = AppInfo(packageName = "de.lemke.geticon", activityName = "")
        val data = AppInfoDataImpl(appInfo, label = null)
        val results = strategy.convert(listOf(data), null).filterIsInstance<AppInfoViewData>()
        results.first().searchable shouldContain "de.lemke.geticon"
    }

    @Test
    fun `convert searchable contains no null values`() {
        val appInfo = AppInfo(packageName = "de.lemke.geticon", activityName = "")
        val data = AppInfoDataImpl(appInfo, label = null)
        val results = strategy.convert(listOf(data), null).filterIsInstance<AppInfoViewData>()
        results.first().searchable shouldNotContain null
    }
}
