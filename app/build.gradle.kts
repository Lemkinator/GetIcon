import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.android.gms.oss-licenses-plugin")
}

fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
fun getProperty(key: String): String? = rootProject.findProperty(key)?.toString() ?: System.getenv(key.toEnvVarStyle())

android {
    namespace = "de.lemke.geticon"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lemke.geticon"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.4.0"
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
        all {
            signingConfig = signingConfigs.getByName(if (getProperty("releaseStoreFile").isNullOrEmpty()) "debug" else "release")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Get Icon (Debug)")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("io.github.lemkinator:common-utils:0.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-compiler:2.57")
}

configurations.implementation {
    //Exclude official android jetpack modules
    exclude("androidx.core", "core")
    exclude("androidx.core", "core-ktx")
    exclude("androidx.customview", "customview")
    exclude("androidx.coordinatorlayout", "coordinatorlayout")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager2", "viewpager2")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.preference", "preference")
    exclude("androidx.recyclerview", "recyclerview")
    exclude("androidx.slidingpanelayout", "slidingpanelayout")
    exclude("androidx.swiperefreshlayout", "swiperefreshlayout")
    //Exclude official material components lib
    exclude("com.google.android.material", "material")
}