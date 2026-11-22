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

package de.lemke.geticon

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
class AppTest {
    private lateinit var priorPreferences: List<Int>
    private lateinit var priorAppVersion: String

    @Before
    fun saveStaticState() {
        priorPreferences = CommonUtilsSettingsActivity.preferences
        priorAppVersion = CommonUtilsAboutActivity.appVersion
    }

    @After
    fun restoreStaticState() {
        CommonUtilsSettingsActivity.preferences = priorPreferences
        CommonUtilsAboutActivity.appVersion = priorAppVersion
    }

    @Test
    fun `the application sets the settings preferences without MainActivity`() {
        CommonUtilsSettingsActivity.preferences shouldBe
            listOf(
                commonutilsR.xml.preferences_design,
                commonutilsR.xml.preferences_general_language_and_image_save_location,
                commonutilsR.xml.preferences_dev_options_delete_app_data,
                commonutilsR.xml.preferences_more_info,
            )
    }

    @Test
    fun `the application sets the about version without MainActivity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifestVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        manifestVersion.shouldNotBeNull().shouldNotBeEmpty()
        CommonUtilsAboutActivity.appVersion shouldBe manifestVersion
    }
}
