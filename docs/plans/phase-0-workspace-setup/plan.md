# Phase 0 — Workspace Setup
# Implementation Plan

**Date:** 2026-06-11
**Spec reference:** [`spec.md`](./spec.md)
**Status:** Phases 1–2 executed. Revised on `phase-3-prep` with confirmed SDK/JDK facts; Phase 3 is now unblocked for development.

---

## 0. Strategy: two tracks

Work is split into two tracks. (Originally Track B was assumed blocked on a paid
subscription — that was **wrong**; the SDK is free via the `org.tribot.dev` Gradle
plugin, so Track B is buildable now. Only a live *run* needs TRiBot Echo installed.)

- **Track A — done (Phases 1–2).** SDK-independent pure logic + scaffold. Plain
  `mavenCentral`, JUnit. No TRiBot dependency.
- **Track B — Phase 3, now unblocked.** The `org.tribot.dev` plugin, the
  `TribotScript`/`ScriptContext` entry point, the config UI, and the adapter that
  actually clicks the GE. Only live testing needs a local Echo install.

### The architectural seam that makes this clean

A **pure decision engine** consumes a snapshot of the world and emits **abstract
commands**; a thin SDK executor (Track B) is the only thing that touches the
client:

```
[ Wiki prices ] + [ account/offer state ] + [ config ]
            │
            ▼
      FlipEngine  (PURE — Track A, fully unit-tested)
            │
            ▼
   List<FlipAction>   e.g. PlaceBuyOffer, PlaceSellOffer, CollectOffer, CancelOffer
            │
            ▼
   FlipActionExecutor (SDK adapter — Track B, the ONLY SDK-coupled flipper code)
```

Everything above `FlipActionExecutor` is testable today.

### Project-wide conventions (defaults — trivially changeable)

- **Root project:** `osrs-scripts-suite`
- **Base package:** `com.osrsscripts`
- **JDK toolchain:** **Java 21** (hard requirement — Echo loads only JDK 21 class
  files; confirmed by the official setup guide). Re-baselined from 11 on
  `phase-3-prep`. `libraries/core` was written to plain Java 11 APIs and compiles
  cleanly on 21.
- **Build:** Gradle Kotlin DSL, multi-module.
- **HTTP:** `java.net.http.HttpClient` (built into Java 11 — no dependency).
- **JSON:** Jackson.
- **Test:** JUnit 5 + Mockito.

---

## Phase 1 — Repository Scaffold  *(Track A — now)*

### 1.1 Gradle wrapper
Generate the Gradle wrapper (`gradle wrapper --gradle-version <current>`), committing
`gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
**Acceptance:** `./gradlew --version` runs.

### 1.2 `settings.gradle.kts`
```kotlin
rootProject.name = "osrs-scripts-suite"
include("libraries:core")
// scripts:ge-flipper added in Phase 3 (Track B)
```

### 1.3 Root `build.gradle.kts`
```kotlin
plugins { java }
allprojects { repositories { mavenCentral() } }
subprojects {
    apply(plugin = "java")
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(11)) }
    }
}
```

### 1.4 `gradle.properties`
```
org.gradle.jvmargs=-Xmx2g
org.gradle.caching=true
```
(No `tribot.sdk.version` yet — added in Phase 3 when the real coordinate is known.)

### 1.5 `.gitignore`
Cover `/build/`, `.gradle/`, `/out/`, `*.iml`, `.idea/`, local run/state files.

### 1.6 `README.md`
Project summary + **botting-risk warning**:
> Using automation clients violates Jagex's rules and may result in account
> bans. This software is provided for educational purposes; use at your own risk.

### 1.7 `.github/workflows/ci.yml`
```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '11' }
      - run: ./gradlew build --no-daemon
```

**Phase 1 acceptance:** `./gradlew tasks` and `./gradlew build` succeed (green,
empty); CI file present.

---

## Phase 2 — Core Library: pure logic  *(Track A — now, strict TDD)*

Module `libraries/core`, package root `com.osrsscripts.core`.
**TDD rule:** write the failing test first for every item below, watch it fail,
then implement.

### 2.0 Module `libraries/core/build.gradle.kts`
```kotlin
plugins { `java-library` }
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
    testImplementation("org.mockito:mockito-core:5.+")
}
tasks.test { useJUnitPlatform() }
```

### 2.1 Domain models — `…core.model`
Immutable value types (plain classes on Java 11):
`ItemId`, `ItemMeta` (name, members, `buyLimit`, geExempt), `PricePoint`
(insta-buy, insta-sell, timestamps), `MarketSnapshot` (map of prices + volumes),
`GeOffer` (item, side, price, qty, filled, slot, status), `AccountState`
(cash, `List<GeOffer>`, free slots), `FlipConfig` (capital cap, min margin %,
min volume, max slots used, per-item capital cap).
**Acceptance:** compiles; trivial equality/serialization tests.

### 2.2 OSRS Wiki prices client — `…core.prices`
- `interface PriceSource` — `MarketSnapshot latest()`, `Map<ItemId,ItemMeta> mapping()`,
  volume views (`/5m`, `/1h`).
- `interface HttpFetcher { String getJson(String url); }` (seam for tests).
- `WikiPriceClient implements PriceSource` — uses `java.net.http.HttpClient`,
  sets a **descriptive User-Agent** (configurable, required by the API), and
  **caches** responses with a TTL (mapping cached long; latest/5m short).
  Endpoints: `https://prices.runescape.wiki/api/v1/osrs/{latest,mapping,5m,1h}`.
- **Tests:** feed canned JSON fixtures through a fake `HttpFetcher`; assert
  parsing of prices/volumes/mapping and that the cache suppresses repeat calls
  within TTL.
**Acceptance:** `:libraries:core:test` green for `prices` package.

### 2.3 GE tax calculator — `…core.ge.GeTax`
- Config object `GeTaxRules { double rate; long perItemCap; long exemptBelow;
  Set<ItemId> exemptItems; }`.
- **Default `rate` is provisional — the GE tax rate has changed over the game's
  history; verify the current live value before trusting profit math.** Defaults:
  `rate` = current known rate, `perItemCap = 5_000_000`, `exemptBelow = 100`.
- `long taxOnSale(long unitPrice, int qty)` → `min(perItemCap, floor(rate*unitPrice))`
  per unit (0 if exempt), summed.
- `long netProceeds(...)`, `long breakEvenSellPrice(long buyPrice)`.
- **Tests:** boundary cases — exempt item, sub-threshold price, per-item cap hit,
  rounding.
**Acceptance:** tax tests green.

### 2.4 Buy-limit tracker — `…core.ge.BuyLimitLedger`
- Tracks per-item purchased qty within a rolling **4-hour** window
  (timestamps injected via a `Clock` for testability).
- `int remaining(ItemId, int limit)`, `void recordPurchase(ItemId, int qty, Instant)`,
  prune expired entries. Serializable for persistence.
- **Tests:** purchases inside/outside the window; limit exhaustion; pruning.
**Acceptance:** ledger tests green.

### 2.5 Flip scanner / ranking — `…core.ge.FlipScanner`
- Input: `MarketSnapshot` + `mapping` + `FlipConfig`.
- Computes **net margin** (using `GeTax`), filters by min margin %, min volume,
  and available buy-limit; ranks candidates (e.g., by net margin × throughput).
- Output: ordered `List<FlipCandidate>`.
- **Tests:** ranking order, tax-aware margin, volume/limit filtering, empty result.
**Acceptance:** scanner tests green.

### 2.6 Flip decision engine — `…core.ge.FlipEngine` + `FlipAction`
- `sealed`-style command set (plain classes on Java 11): `PlaceBuyOffer`,
  `PlaceSellOffer`, `CollectOffer`, `CancelOffer`, `NoOp`.
- `FlipEngine.decide(MarketSnapshot, AccountState, BuyLimitLedger, FlipConfig)`
  → `List<FlipAction>`. Pure. Logic: collect completed offers, free stale offers,
  spend free slots/capital on top scanner candidates respecting buy limits,
  place sells for owned stock at target price.
- **Tests:** full slots → no new buys; completed buy → emits matching sell;
  capital cap respected; buy-limit-exhausted item skipped; stale offer cancelled.
**Acceptance:** engine tests green — this is the heart of the flipper, fully
covered without a client.

### 2.7 Persistence — `…core.persistence.StateStore`
- JSON (Jackson) save/load of `AccountState`, `BuyLimitLedger`, open offers, and
  cumulative profit stats. Atomic write (temp + rename). Path injected.
- **Tests:** round-trip to a JUnit `@TempDir`; corrupt-file recovery (fail safe
  to empty state).
**Acceptance:** persistence tests green.

### 2.8 Humanization (pure parts) — `…core.humanize`
- `DelayDistribution` (seedable RNG → randomized, non-uniform wait durations),
  `BreakScheduler` (decides when/how long to break, `Clock`-driven).
- **Tests:** distributions within bounds; break cadence over a simulated clock.
- *(Mouse/camera fidget that actually moves the cursor is Track B — it needs the
  SDK; only the timing/decision logic lives here.)*
**Acceptance:** humanize tests green.

### 2.9 Task / state-machine framework — `…core.task`
- `interface Task { boolean shouldRun(); void execute(); String name(); }`,
  `TaskRunner` that evaluates a prioritized list each loop and runs the first
  eligible task (the OSRS-bot standard pattern).
- The framework operates on **abstract tasks** — game actions are supplied by
  scripts (Track B); the runner/priority logic is testable with fake tasks.
- **Tests:** priority selection, no-eligible-task path, ordering.
**Acceptance:** framework tests green.

**Phase 2 acceptance:** `./gradlew :libraries:core:test` fully green; CI passes.
At this point the entire flipper *brain* is implemented and proven without a
subscription.

---

## Phase 3 — SDK Integration & GE Flipper Script  *(Track B — UNBLOCKED for development)*

> Confirmed from the official IntelliJ setup guide + the community automations
> repo. Building needs only JDK 21 + the JitPack plugin; only a live *run* needs a
> local TRiBot Echo install.
>
> **SDK reference** (API surface, model→SDK mapping, build wiring, gotchas):
> [`../../reference/tribot-sdk.md`](../../reference/tribot-sdk.md).

1. **`scripts/ge-flipper` module** applying `kotlin("jvm")` (or plain `java`) +
   `id("org.tribot.dev") version "latest.release"`. Repos: `mavenCentral`,
   `maven("https://repo.runelite.net")`, `maven("https://jitpack.io")` plus the
   JitPack resolutionStrategy for the plugin marker. Bundle the shared library via
   the plugin's `bundled(project(":libraries:core"))`.
2. **Entry point** — `GeFlipperScript implements org.tribot.automation.TribotScript`
   with `void execute(ScriptContext context)`, registered in the module's
   `tribot { }` block (`script(...)`). Its loop drives a `TaskRunner`.
3. **`FlipActionExecutor`** — the single SDK-coupled class: maps each `FlipAction`
   to a thin `org.tribot.script.sdk.GrandExchange` call (`placeOffer` / `abort` /
   `collectAll`); reads offers via `GrandExchangeOfferQuery`. See the model→SDK
   mapping in the reference.
4. **Account adapter** — read cash, the 8 GE slots (→ `GeOffer`s) and owned stock
   each tick to build the `AccountState` the engine consumes.
5. **Config UI** — bind `FlipConfig` + live stats; RuneLite side panel if exposed,
   else Compose/JavaFX/Swing (`tribot { }` toggles `useCompose`/`useJavaFx`).
6. **Wire persistence** (`StateStore`) so buy-limit timers survive restarts.
7. **Validation-first checkpoint:** before the full flipper, deploy a *trivial*
   `TribotScript` and confirm it loads/runs in Echo (proves entry point +
   `fatJar`/`deployLocally`).

**Build/deploy:** `./gradlew :scripts:ge-flipper:fatJar` then `deployLocally`
(fat JAR → `%APPDATA%/.tribot/automations`); the plugin auto-generates the manifest.

**Phase 3 acceptance:** trivial script loads in Echo; flipper runs a real cycle;
`deployLocally` places the JAR in the automations directory.

**Blocked only for live verification on:** a local TRiBot Echo install (and possibly
a free account/login — spec §5.3).

---

## Phase 4 — Publish / Distribution  *(later / optional)*

> Spec: "private now, publish later."

- Private use is just `deployLocally`. Public distribution would mean a PR into
  the open `Tribot-Community-Automations` repo (MIT) — a different model from a
  private closed-source script; decide if/when that's wanted.
- If publishing: licensing, versioning, support obligations.

**Phase 4 acceptance:** decision made on private vs. community-repo distribution.

---

## Dependency graph

```
Phase 1 ─► Phase 2 ─► Phase 3 ──────────► Phase 4
(done)     (done)     (dev unblocked;      (later)
                       live run needs
                       Echo installed)
```

Phases 1–2 delivered the tested flipper brain. Phase 3 development can proceed now;
only the final live run needs a local Echo install.

---

## What this plan deliberately does NOT do (YAGNI)

- No multi-account/mule support (single account — spec §4).
- No banking/walking adapters yet (flipper barely uses them; built with later
  scripts).
- No `repoPackage`/repo-zip pipeline — the real deploy is the plugin's `fatJar` +
  `deployLocally` into `.tribot/automations`.
- No speculative ports beyond what the flipper needs.
