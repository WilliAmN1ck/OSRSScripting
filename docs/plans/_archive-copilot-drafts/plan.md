# Phase 0 — Workspace Setup  
# Implementation Plan

This plan defines all tasks required to scaffold the repository and prepare for script development. Tasks are 2–5 minutes each and include exact file paths and acceptance criteria.

---

# Phase 1 — Repository Scaffold

## 1. Create root Gradle files
**Path:** `/settings.gradle.kts`
```kotlin
rootProject.name = "osrs-scripts-suite"
include("commons", "script-template", "script-miner", "script-lumbridge-killer")
```
**Acceptance:** `./gradlew tasks` runs.

**Path:** `/build.gradle.kts`
```kotlin
plugins { java }
allprojects { repositories { mavenCentral() } }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
```
**Acceptance:** Gradle recognizes Java 17 toolchain.

**Path:** `/gradle.properties`
```
org.gradle.jvmargs=-Xmx2g
tribot.sdk.version=1.0.0
```

## 2. Add README and LICENSE
**Path:** `/README.md`
```md
# osrs-scripts-suite
Closed-source Tribot Automation SDK scripts.

Warning: Botting violates Jagex rules and may lead to bans.
```
**Acceptance:** README includes warning.

## 3. Create module directories
```
commons/
script-template/
script-miner/
script-lumbridge-killer/
```
Each module gets a minimal `build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies { testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") }
java { withSourcesJar() }
```

## 4. Add CI workflow
**Path:** `.github/workflows/ci.yml`
```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17' }
      - run: ./gradlew build --no-daemon
```

---

# Phase 2 — Commons, Template, CI Enhancements

## Commons Module

### 1. Logger
**Path:** `commons/src/main/java/com/osrs/commons/Logger.java`
```java
package com.osrs.commons;
import java.time.Instant;
public final class Logger {
  public static void info(String msg) { System.out.println(Instant.now() + " INFO: " + msg); }
  public static void error(String msg) { System.err.println(Instant.now() + " ERROR: " + msg); }
}
```

### 2. ConfigLoader
**Path:** `commons/src/main/java/com/osrs/commons/ConfigLoader.java`
```java
package com.osrs.commons;
import java.nio.file.*; import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
public class ConfigLoader {
  public static Map<String,Object> load(Path p) throws Exception {
    return new ObjectMapper().readValue(Files.readAllBytes(p), Map.class);
  }
}
```

### 3. SafeSleep
**Path:** `commons/src/main/java/com/osrs/commons/SafeSleep.java`
```java
package com.osrs.commons;
public final class SafeSleep {
  public static void ms(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
  }
}
```

### 4. Unit test
**Path:** `commons/src/test/java/com/osrs/commons/ConfigLoaderTest.java`
- Writes a temp JSON file  
- Asserts parsed value matches expected  
**Acceptance:** `./gradlew :commons:test` passes.

---

# Script Template Module

## 1. Entrypoint
**Path:** `script-template/src/main/java/com/osrs/template/TemplateScript.java`
```java
package com.osrs.template;
import com.osrs.commons.Logger;
public class TemplateScript {
  public static void main(String[] args) {
    Logger.info("TemplateScript starting with args: " + String.join(" ", args));
  }
}
```

## 2. Metadata
**Path:** `script-template/src/main/resources/META-INF/tribot-script.json`
```json
{ "name":"TemplateScript", "version":"0.1.0", "entry":"com.osrs.template.TemplateScript" }
```

## 3. Fat JAR
**Path:** `script-template/build.gradle.kts`
```kotlin
plugins { id("com.github.johnrengelman.shadow") version "8.1.1" }
tasks.named("shadowJar") { archiveBaseName.set("script-template-all") }
```

---

# Deploy Tasks

## 1. deployLocally
**Path:** `script-template/build.gradle.kts`
```kotlin
tasks.register<Copy>("deployLocally") {
  dependsOn("shadowJar")
  from("$buildDir/libs")
  into(System.getProperty("user.home") + "/.tribot/automations")
}
```

---

# Verification

- `./gradlew :commons:test` passes  
- `./gradlew :script-template:shadowJar` produces fat JAR  
- `./gradlew :script-template:deployLocally` copies JAR to `~/.tribot/automations`  
- CI workflow runs successfully  

---

# Next Steps
After Phase 2, proceed to Phase 3 (Miner + Lumbridge Killer TDD implementation).  
