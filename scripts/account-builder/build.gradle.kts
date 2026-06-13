plugins {
    kotlin("jvm") version "2.1.21"
    // `latest.release` keeps the compile-time SDK in lockstep with Echo's runtime, so signature
    // drift surfaces as a compile error here rather than a silent NoSuchMethodError in-game.
    id("org.tribot.dev") version "latest.release"
}

repositories {
    mavenCentral()
    maven("https://repo.runelite.net")
    maven("https://jitpack.io")
}

tribot {
    // Config panels are plain Swing (Sidebar.addSidebarTab takes a JPanel); Compose stays off.
    useCompose = false

    scripts {
        register("accountBuilder") {
            className = "com.osrsscripts.accountbuilder.AccountBuilderScript"
            scriptName = "AIO Account Builder"
            version = "0.1.0"
            author = "WilliAmN1ck"
            description = "All-in-one account builder"
            category = "Other"
        }
    }
}

dependencies {
    // The pure engine/logic; bundled into the fat JAR (Echo does not provide it).
    bundled(project(":libraries:core"))

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The dev plugin's fatJar reads the bundled project's jar without declaring the task dependency;
// Gradle fails the build when both are scheduled together. (Plugin registers fatJar post-eval.)
tasks.matching { it.name == "fatJar" }.configureEach {
    dependsOn(":libraries:core:jar")
}

// The plugin's fatJar writes account-builder.jar — the plain jar task's default name. A classifier
// keeps the thin module jar from clobbering the deployable fat artifact in the same invocation.
tasks.jar {
    archiveClassifier.set("thin")
}
