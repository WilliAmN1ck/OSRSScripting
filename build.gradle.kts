plugins {
    base
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    // Apply the Java toolchain convention to any module that uses Java,
    // so individual module build scripts don't repeat it.
    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }
    }
}
