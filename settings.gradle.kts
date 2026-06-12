pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    // JitPack doesn't publish the Gradle plugin marker for org.tribot.dev, so map the
    // plugin id to its JitPack module coordinates.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.tribot.dev") {
                useModule("com.github.TribotRS.tribot-dev-plugin:plugin:${requested.version}")
            }
        }
    }
}

plugins {
    // Auto-provisions a matching JDK when no compatible local toolchain is found.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "osrs-scripts-suite"

include("libraries:core")
include("scripts:ge-flipper")
