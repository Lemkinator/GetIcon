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

package de.lemke.geticon.benchmarks

import android.app.UiAutomation
import android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val VULKAN_1_1 = 0x401000
private const val RENDERER_PROPERTY = "debug.hwui.renderer"
private const val TAG = "BaselineProfileGenerator"

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousRenderer: String? = null

    // The emulator's SwiftShader GLES translator crashes the emulator process when the app list draws its
    // first hardware layers. Vulkan rendering bypasses that translator for every app process started later.
    @Before
    fun renderWithVulkanOnEmulator() {
        if (Build.HARDWARE != "ranchu") return
        val hasVulkan11 = instrumentation.context.packageManager.hasSystemFeature(FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasVulkan11) {
            val uiAutomation = instrumentation.uiAutomation
            previousRenderer = uiAutomation.shell("getprop $RENDERER_PROPERTY")
            val renderer = uiAutomation.shell("setprop $RENDERER_PROPERTY skiavk; getprop $RENDERER_PROPERTY")
            Log.i(TAG, "HWUI renderer: $renderer")
        } else {
            Log.i(TAG, "HWUI renderer: GLES fallback, Vulkan 1.1 on API 31+ unavailable")
        }
    }

    @After
    fun restoreRenderer() {
        val previous = previousRenderer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val restored = instrumentation.uiAutomation.shell("setprop $RENDERER_PROPERTY '$previous'; getprop $RENDERER_PROPERTY")
            Log.i(TAG, "HWUI renderer restored: '$restored'")
        }
    }

    // Startup profile drives dex layout optimization, so it must stay limited to the actual
    // cold-start path — a secondary screen here would bloat startup-prof.txt with non-startup code.
    @Test
    fun startup() =
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
        }

    @Test
    fun generate() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
            navigateToIconAndBack()
        }
}

// UiAutomation splits a command on whitespace and runs it without a shell, so quoted or empty arguments need sh on stdin.
@RequiresApi(Build.VERSION_CODES.S)
private fun UiAutomation.shell(script: String): String {
    val (stdout, stdin) = executeShellCommandRw("sh")
    ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write(script.toByteArray()) }
    return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().decodeToString().trim() }
}

private fun MacrobenchmarkScope.navigateToIconAndBack() {
    val appItem =
        checkNotNull(
            device
                .wait(
                    Until.findObject(
                        By.res(PACKAGE_NAME, "appPicker").hasDescendant(By.clazz("android.widget.TextView")),
                    ),
                    TIMEOUT_MS,
                )?.findObject(By.clazz("android.widget.TextView")),
        ) { "appPicker list item not found within ${TIMEOUT_MS}ms" }
    appItem.click()
    device.waitForIdle()
    checkNotNull(device.wait(Until.findObject(By.res(PACKAGE_NAME, "icon")), TIMEOUT_MS)) {
        "icon screen not found within ${TIMEOUT_MS}ms"
    }
    device.pressBack()
    device.waitForIdle()
}
