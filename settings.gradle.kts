plugins {
    // Auto-provisions a matching JDK when no compatible local toolchain is found.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "osrs-scripts-suite"

include("libraries:core")
// scripts:ge-flipper added in Phase 3 (Track B — blocked on the TRiBot SDK)
