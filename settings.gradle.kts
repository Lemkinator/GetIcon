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
include(":benchmarks")

// TEMPORARY: composite-build substitution to validate the common-utils settings-migration changes
// (see C:\Users\leo\.claude\plans\settings-migration.md) before a real common-utils release. Revert before merging.
includeBuild("../common-utils") {
    dependencySubstitution {
        substitute(module("io.github.lemkinator:common-utils")).using(project(":lib"))
    }
}
