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
package de.lemke.geticon.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "de.lemke.geticon"
private const val TIMEOUT_MS = 5_000L
// Must match de.lemke.commonutils.EXTRA_SKIP_FIRST_RUN — cannot import from test module
private const val EXTRA_SKIP_FIRST_RUN = "commonUtilsSkipFirstRun"

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
            includeInStartupProfile = true,
        ) {
            startActivityAndWait(Intent().apply { putExtra(EXTRA_SKIP_FIRST_RUN, true) })
            navigateToIconAndBack()
        }
}

private fun MacrobenchmarkScope.navigateToIconAndBack() {
    val appItem =
        device
            .wait(Until.findObject(By.res(PACKAGE_NAME, "appPicker")), TIMEOUT_MS)
            ?.findObject(By.clazz("android.widget.TextView"))
    appItem?.click()
    device.waitForIdle()
    device.wait(Until.findObject(By.res(PACKAGE_NAME, "icon")), TIMEOUT_MS)
    device.pressBack()
    device.waitForIdle()
}
