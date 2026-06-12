# Phase 3 — GE Flipper · Step 3a (module skeleton)
# Handoff

**Date:** 2026-06-12
**Branch:** phase-3-ge-flipper
**Plan reference:** [`../phase-0-workspace-setup/plan.md`](../phase-0-workspace-setup/plan.md) "Phase 3";
SDK reference: [`../../reference/tribot-sdk.md`](../../reference/tribot-sdk.md)
**Status:** ✅ Validation-first checkpoint passed. Executor/GUI follow in the next PR.

---

## What Was Built

The `scripts/ge-flipper` module, wired to the real TRiBot toolchain, plus a no-op
entry point — the "trivial script that builds against the SDK" checkpoint from the plan.

- **`settings.gradle.kts`** — added `pluginManagement` (JitPack + the `org.tribot.dev`
  resolutionStrategy) and `include("scripts:ge-flipper")`.
- **`scripts/ge-flipper/build.gradle.kts`** — `kotlin("jvm") 2.1.21` + `id("org.tribot.dev")
  latest.release`; repos `mavenCentral`/`repo.runelite.net`/`jitpack`; `tribot { }` registers
  the script (`useCompose = false` for now); `bundled(project(":libraries:core"))`.
- **`GeFlipperScript.java`** — `implements org.tribot.automation.TribotScript`, empty
  `execute(ScriptContext)`. (Java compiles fine inside the Kotlin module.)

## Verification

Built with the Gradle daemon on **JDK 21** (see setup note):

- `:scripts:ge-flipper:build` → BUILD SUCCESSFUL — plugin resolved from JitPack, entry point
  compiled against the SDK (`compileOnly`), `generateManifest` ran.
- `:scripts:ge-flipper:fatJar` → `build/libs/ge-flipper.jar` (~2.3 MB). Contents confirmed:
  our classes + bundled `libraries:core`, `echo-scripts.json` manifest, **Jackson bundled**
  (so the gson-vs-Jackson concern is moot — the project dep pulls it into the fat JAR),
  Kotlin stdlib stripped.

## ⚠️ Setup finding — the Gradle daemon must run on JDK 21+

The `org.tribot.dev` plugin **requires the JVM running Gradle to be ≥ 21** ("Dependency
requires at least JVM runtime version 21"). Our situation:

- JDK 11 → rejected by the plugin.
- JDK 26 → too new for Gradle 8.10.2 to run on (version-parse failure).
- **JDK 21 → works.** Used the foojay-provisioned Temurin 21 as `JAVA_HOME`:
  `C:\Users\Nikras\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`.

**Recommendation:** install a stable **JDK 21** and point `JAVA_HOME` at it for local builds
(relying on the provisioned-toolchain path is fragile). **CI is unaffected** — it already runs
on Temurin 21. The cold-cache TLS workaround (`JAVA_TOOL_OPTIONS`) is only needed when JitPack
artifacts are fetched fresh.

## What the Next PR Does (executor)

1. Inspect the now-cached SDK jars for exact `GrandExchange` / `CreateOfferConfig` /
   `GrandExchangeOfferQuery` / coins signatures.
2. `FlipActionExecutor` mapping each `FlipAction` to `GrandExchange.placeOffer/abort/collectAll`.
3. Offer/cash adapter → `AccountState`; wire `FlipScanner` + `FlipEngine` into a `TaskRunner`
   loop inside `execute()`.
4. Sidebar config UI; persistence via `StateStore`; breaks via `sidecars`.

## Verification Commands

    $env:JAVA_HOME = "C:\Users\Nikras\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"  # any JDK 21
    .\gradlew.bat :scripts:ge-flipper:build
    .\gradlew.bat :scripts:ge-flipper:fatJar   # -> scripts/ge-flipper/build/libs/ge-flipper.jar
    # add JAVA_TOOL_OPTIONS (Windows-ROOT + TLSv1.2) only on a cold dependency cache
