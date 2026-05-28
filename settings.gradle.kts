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

// TODO: remove after common-utils is published with the fragment/nav APIs
includeBuild("../common-utils") {
    dependencySubstitution {
        substitute(module("io.github.lemkinator:common-utils")).using(project(":lib"))
    }
}
