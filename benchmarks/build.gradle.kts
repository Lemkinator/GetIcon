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

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

android {
    namespace = "de.lemke.geticon.benchmarks"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // The emulator's SwiftShader GLES translator crashes the qemu host process during GetIcon cold
    // launches. ATD images set debug.hwui.drawing_enabled=0, so the app issues no GPU draws there.
    testOptions.managedDevices.localDevices {
        register("pixel9Api35Atd") {
            device = "Pixel 9"
            apiLevel = 35
            systemImageSource = "aosp-atd"
            testedAbi = "x86_64"
        }
    }
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}

baselineProfile {
    @Suppress("UnstableApiUsage")
    enableEmulatorDisplay = false
    managedDevices.clear()
    managedDevices += "pixel9Api35Atd"
    useConnectedDevices = false
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = libs.versions.jvmTarget.get()
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}
