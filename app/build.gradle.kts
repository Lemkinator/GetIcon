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
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.junit)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

fun getProperty(key: String): String? = rootProject.findProperty(key)?.toString() ?: System.getenv(key.toEnvVarStyle())

fun com.android.build.api.dsl.ApplicationBuildType.addConstant(
    name: String,
    value: String,
) {
    manifestPlaceholders += mapOf(name to value)
    buildConfigField("String", name, "\"$value\"")
}

android {
    namespace = "de.lemke.geticon"
    compileSdk = 37
    defaultConfig {
        applicationId = "de.lemke.geticon"
        minSdk = 26
        targetSdk = 37
        versionCode = 32
        versionName = "1.4.2"
        testInstrumentationRunner = "de.lemke.geticon.HiltTestRunner"
    }
    @Suppress("UnstableApiUsage")
    androidResources.localeFilters += listOf("en", "de")
    signingConfigs {
        create("release") {
            getProperty("releaseStoreFile").apply {
                if (isNullOrEmpty()) {
                    logger.warn("Release signing configuration not found. Using debug signing config.")
                } else {
                    logger.lifecycle("Using release signing configuration from: $this")
                    storeFile = rootProject.file(this)
                    storePassword = getProperty("releaseStorePassword")
                    keyAlias = getProperty("releaseKeyAlias")
                    keyPassword = getProperty("releaseKeyPassword")
                }
            }
        }
    }
    buildTypes {
        all { signingConfig = signingConfigs.getByName(if (getProperty("releaseStoreFile").isNullOrEmpty()) "debug" else "release") }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            addConstant("APP_NAME", "Get Icon")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            //noinspection NotShrinkingResources
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            addConstant("APP_NAME", "Get Icon (Debug)")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/licenses/**"
        }
        jniLibs.useLegacyPackaging = true // sets extractNativeLibs=true; affects only APK install-time .so extraction, not AAB publishing
    }
    lint {
        warningsAsErrors = true
        // checkDependencies = false: private AAR deps (oneui-design, common-utils) surface
        // hundreds of unactionable warnings; flip to true once in-project surface is clean
        checkDependencies = false
        checkReleaseBuilds = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        sarifReport = true
        htmlReport = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                test.useJUnitPlatform()
                test.jvmArgs("-XX:+EnableDynamicAgentLoading")
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record") ?: "false")
                test.systemProperty("roborazzi.test.verify", project.findProperty("roborazzi.verify") ?: "true")
            }
        }
        animationsDisabled = true
    }
}

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.common.utils)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.profileinstaller)

    debugImplementation(libs.leakcanary)

    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.konsist)

    testImplementation(libs.bundles.robolectric.test)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
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
    kotlinGradle {
        target("*.gradle.kts")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"), "(^(?![\\/ ]\\*).*$)")
        ktlint(libs.versions.ktlint.get())
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/**")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.xml"), "(<[^!?])")
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
    jvmTarget = "21"
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

baselineProfile {
    dexLayoutOptimization = true
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("reports/roborazzi"))
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.databinding.*",
                    "*.BuildConfig",
                    "*Hilt_*",
                    "*_HiltModules*",
                    "*_Factory",
                    "*_MembersInjector",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                    "*.di.*",
                    "*Activity",
                    "*Activity\$*",
                )
            }
        }
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
