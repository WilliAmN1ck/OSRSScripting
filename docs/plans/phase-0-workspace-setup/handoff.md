# Phase 0 — Workspace Setup → Phase 1 (Repository Scaffold)
# Handoff

**Date:** 2026-06-11
**Branch:** main
**Plan reference:** [`plan.md`](./plan.md) — "Phase 1 — Repository Scaffold (Track A)"
**Status:** ✅ Phase 1 complete. Phase 2 (core library) pending approval.

---

## What Was Built

A green, self-contained Gradle multi-module scaffold:

- **Gradle wrapper** pinned to **8.10.2** (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/*`). `./gradlew` bootstraps its own distribution — verified by
  a clean run that downloaded and built without a system Gradle install.
- **`settings.gradle.kts`** — root `osrs-scripts-suite`, includes `libraries:core`
  (placeholder; populated in Phase 2). `scripts:ge-flipper` intentionally absent
  until Phase 3.
- **`build.gradle.kts`** — `base` plugin at root for lifecycle tasks;
  `mavenCentral` for all projects; a Java-toolchain convention (Java 11) applied
  only to modules that use the Java plugin.
- **`gradle.properties`** — JVM args + build cache.
- **`.gitignore`** — build/IDE/OS artifacts and local state; wrapper jar is
  explicitly **not** ignored (confirmed via `git check-ignore`).
- **`README.md`** — project summary, structure, build commands, and the required
  **botting-risk warning**.
- **`.github/workflows/ci.yml`** — GitHub Actions: Temurin JDK 11 + `./gradlew build`.
- **`libraries/core/.gitkeep`** — keeps the empty module dir tracked so the build
  stays green before Phase 2.

---

## What Changed From the Plan

- **Gradle version pinned to 8.10.2** (plan said "current"). Rationale: 8.10.2
  *runs* on the locally installed JDK 11; Gradle 9.0 requires JDK 17+ to run.
- **Root `build.gradle.kts` differs from the plan §1.3 snippet.** The plan applied
  the `java` plugin at root and `apply(plugin = "java")` to every subproject
  unconditionally. Implemented instead: `base` at root + a
  `plugins.withType<JavaPlugin> { configure<JavaPluginExtension> { … } }` guard.
  Rationale: the empty `libraries:core` placeholder shouldn't get the Java plugin
  (and a compile step) before Phase 2; this keeps the scaffold green and cleaner.
  Net effect on the plan's intent is unchanged — Java modules still get the
  Java 11 toolchain automatically.

---

## What the Next Phase (Phase 2) Needs to Know

- **Local toolchain:** JDK **11.0.2** at `C:\Program Files\Java\jdk-11.0.2`
  (also present: JDK 1.8, RuneLite's bundled JRE). No `JAVA_HOME` is set system
  wide — set it per shell when invoking Gradle:
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"`.
- **Phase 2 entry point:** create `libraries/core/build.gradle.kts`
  (`java-library` + Jackson + JUnit 5 + Mockito), then build the pure logic
  TDD-first per plan §2.1–2.9. The toolchain is inherited from the root convention
  — the module script does **not** need to repeat it.
- **Package root:** `com.osrsscripts.core`.
- **No TRiBot dependency** in `libraries/core` — it must stay buildable/testable
  on plain `mavenCentral` (Track A). SDK coupling arrives only in Phase 3.

---

## Files Changed

| File | Change | Notes |
|---|---|---|
| `settings.gradle.kts` | added | root name + `libraries:core` include + foojay toolchain resolver |
| `build.gradle.kts` | added | `base` + repos + Java 11 toolchain convention |
| `gradle.properties` | added | `-Xmx2g`, build cache |
| `.gitignore` | added | build/IDE/OS/local-state; keeps wrapper jar |
| `.gitattributes` | added | LF for text/`gradlew`, CRLF for `*.bat`, binary `*.jar`/`*.zip` |
| `README.md` | added | summary + botting warning |
| `.github/workflows/ci.yml` | added | CI: JDK 11, Gradle cache, scoped triggers + concurrency |
| `gradlew` | added | staged executable (mode `100755`); `gradlew.bat`, `gradle/wrapper/*` |
| `gradle/wrapper/gradle-wrapper.properties` | added | distribution URL **+ pinned SHA-256** |
| `libraries/core/.gitkeep` | added | keeps empty module tracked |
| `tasks/todo.md` | added | live task tracking |
| `docs/plans/_archive-copilot-drafts/*` | moved | outdated root drafts archived |

---

## Test Coverage

None yet — Phase 1 is build scaffold only (no production code). Test
infrastructure (JUnit 5 + Mockito) is introduced with the first code in Phase 2,
which is strict TDD.

---

## Post-Review Hardening (`/code-review max`)

A max-effort review of the working tree surfaced 8 findings, all fixed at root
before commit:

1. **CI exec bit (high):** `gradlew` staged with mode `100755` (`git add
   --chmod=+x`) — `core.filemode=false` would otherwise commit it non-executable
   and break `./gradlew` on Linux CI.
2. **Toolchain provisioning:** added the foojay resolver so a missing local JDK
   is auto-downloaded instead of failing the build.
3. **Line endings:** added `.gitattributes` (autocrlf=true was relying on
   heuristics for the wrapper).
4. **Supply chain:** pinned `distributionSha256Sum` for the Gradle distribution
   (verified against the published checksum: `31c5…4c26`).
5. Removed a stray backslash in this doc's `$env:JAVA_HOME` commands.
6. Removed a README reference to the not-yet-existent `:libraries:core:test` task.
7. CI now caches Gradle (`cache: gradle`).
8. CI triggers scoped (push→main, plus `pull_request`) with a `concurrency`
   group to stop duplicate runs.

Re-verified: `./gradlew build` → BUILD SUCCESSFUL (exit 0) with the foojay plugin
and checksum validation active.

---

## Known Issues / Tech Debt

- **Gradle-on-JDK-11 deprecation:** Gradle 8.10.2 warns that *running* Gradle on
  JVM ≤ 16 will fail in Gradle 9.0 (projects can still *target* Java 11 via
  toolchains). Benign now. Revisit if/when a dependency (e.g. the TRiBot SDK)
  forces a Gradle 9 / JDK 17 upgrade — at which point Gradle itself needs JDK 17+
  to run, even if the project keeps targeting 11.
- **`subprojects {}` cross-config** is mildly discouraged in favor of convention
  plugins (buildSrc). Acceptable at this size; reconsider if the module count grows.

---

## Verification Commands

    $env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"
    .\gradlew.bat tasks      # lists build/clean — confirms configuration
    .\gradlew.bat build      # BUILD SUCCESSFUL, exit 0

CI runs `./gradlew build` on Temurin JDK 11 on every push/PR.

> Not yet committed — Phase 1 changes are staged in the working tree only.
> Per project process, run `/code-review max` before committing.
