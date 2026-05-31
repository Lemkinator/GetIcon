pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Get Icon"
include(":app")
include(":baselineprofile")

val commonUtilsDir = file("../common-utils")
if (commonUtilsDir.exists()) {
    includeBuild(commonUtilsDir) {
        dependencySubstitution {
            substitute(module("io.github.lemkinator:common-utils")).using(project(":lib"))
        }
    }
}
