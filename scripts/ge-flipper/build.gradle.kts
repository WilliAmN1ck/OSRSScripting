plugins {
    kotlin("jvm") version "2.1.21"
    // Intentionally `latest.release`: keeps the compile-time SDK in lockstep with Echo's
    // runtime, so signature drift surfaces as a compile error here rather than a silent
    // NoSuchMethodError in-game. Pin a version only when targeting a specific Echo release.
    id("org.tribot.dev") version "latest.release"
}

repositories {
    mavenCentral()
    maven("https://repo.runelite.net")
    maven("https://jitpack.io")
}

tribot {
    // The config panel is plain Swing (Sidebar.addSidebarTab takes a JPanel); Compose stays off.
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

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The dev plugin's fatJar reads the bundled project's jar without declaring the task
// dependency; Gradle fails the build when both are scheduled together (e.g. `build fatJar`).
// (Configured lazily: the plugin registers fatJar after project evaluation.)
tasks.matching { it.name == "fatJar" }.configureEach {
    dependsOn(":libraries:core:jar")
}

// The plugin's fatJar writes ge-flipper.jar — the plain jar task's default name. If jar runs
// after fatJar in the same invocation it silently clobbers the deployable fat artifact with the
// thin module jar (observed live: NoClassDefFoundError in Echo). A classifier keeps them apart.
tasks.jar {
    archiveClassifier.set("thin")
}
