plugins {
    kotlin("jvm") version "2.1.21"
    id("org.tribot.dev") version "latest.release"
}

repositories {
    mavenCentral()
    maven("https://repo.runelite.net")
    maven("https://jitpack.io")
}

tribot {
    // No in-client GUI yet; enable when we build the config panel.
    useCompose = false

    scripts {
        register("geFlipper") {
            className = "com.osrsscripts.geflipper.GeFlipperScript"
            scriptName = "GE Flipper"
            version = "0.1.0"
            author = "WilliAmN1ck"
            description = "Grand Exchange flipper"
            category = "Money"
        }
    }
}

dependencies {
    // The pure flipper brain; bundled into the fat JAR (Echo does not provide it).
    bundled(project(":libraries:core"))
}
