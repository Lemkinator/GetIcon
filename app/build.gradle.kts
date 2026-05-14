plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.junit)
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
    compileSdk = 36
    defaultConfig {
        applicationId = "de.lemke.geticon"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.4.2"
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
        jniLibs.useLegacyPackaging = true
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
    sourceSets {
        named("test") {
            resources.srcDir("src/test/screenshots")
        }
    }
}

dependencies {
    implementation(libs.common.utils)
    implementation(libs.datastore.preferences)
    implementation(libs.bundles.room)
    implementation(libs.hilt.android)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.junit.jupiter.bom))
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.junit4)
    testImplementation(libs.bundles.robolectric.test)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(platform(libs.junit.jupiter.bom))
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    debugImplementation(libs.fragment.testing)

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/**")
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
                )
            }
        }
        verify {
            rule {
                minBound(0)
            }
        }
    }
}
