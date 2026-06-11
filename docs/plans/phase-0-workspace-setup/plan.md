# Phase 0 — Workspace Setup
# Implementation Plan

**Date:** 2026-06-11
**Spec reference:** [`spec.md`](./spec.md)
**Status:** Plan — pending confirmation (no execution until approved)

---

## 0. Strategy: two tracks

The user has **no TRiBot subscription / SDK yet**, and the paid SDK signatures
are unknown. To make progress without coding against guesses, work is split:

- **Track A — buildable & testable now.** SDK-independent pure logic + project
  scaffold. Plain `mavenCentral`, JUnit. No TRiBot dependency.
- **Track B — blocked until subscription + SDK in hand.** The TRiBot Gradle
  plugin, the Automation SDK entry point, RuneLite GUI, and the adapters that
  actually click the GE. Outlined here; **not** executed until unblocked.

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
- **JDK toolchain:** **Java 11** (conservative floor — TRiBot template states 11;
  code compiling on 11 runs on 17. Avoid records/sealed for now. Bump + adopt
  them only once the SDK confirms 17+ — see spec §5).
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

## Phase 3 — SDK Integration & GE Flipper Script  *(Track B — BLOCKED)*

> **Blocked on:** active TRiBot subscription + the real Automation SDK jar +
> the official Echo template. Resolves spec §5 unknowns. Outline only — exact
> file contents written once we can read real SDK signatures.

1. **Adopt the official template wiring:** add TRiBot's Gradle plugin + TRiBot
   Central maven repo; **confirm the JDK** and bump the toolchain if the SDK
   requires 17+ (then optionally refactor models to records/sealed).
2. **Create `scripts/ge-flipper` module**; depend on `libraries:core`.
3. **Automation SDK entry point** — implement the real script class/annotation
   /lifecycle (replaces the archived drafts' invented `main()`/`tribot-script.json`).
4. **`FlipActionExecutor`** — the single SDK-coupled class that turns each
   `FlipAction` into real GE interactions (open GE, search, set price/qty,
   confirm, collect, cancel), with humanized timing/mouse from `…core.humanize`.
5. **GE/world adapters** — implement only the ports the flipper needs
   (GE interface read, login/idle handling); banking/walking adapters deferred
   to later scripts.
6. **RuneLite side panel GUI** — bind `FlipConfig` inputs + live stats
   (profit, open offers, scanner picks). Fall back to Swing if the SDK exposes
   no panel hook.
7. **Wire persistence** so the script resumes offers + buy-limit timers on start.
8. **Validation-first checkpoint:** before full flipper wiring, ship a *trivial*
   script that loads and runs in Echo (proves the SDK entry point + build/deploy)
   — per spec sequencing.

**Phase 3 acceptance:** trivial script loads in Echo; flipper runs a real cycle;
`repoPackage` produces a deployable zip.

---

## Phase 4 — Publish Pipeline  *(Track B — later / optional)*

> Spec: "private now, publish later." Design, don't activate.

- Configure `repoPackage`/`repoUpdate` per the template.
- If/when publishing: licensing, instance limits, store metadata, support.

**Phase 4 acceptance:** `repoPackage` produces a valid repo zip; publish steps
documented but not run.

---

## Dependency graph

```
Phase 1 ─► Phase 2 ─► Phase 3 ─► Phase 4
(now)      (now)      (blocked:   (later)
                       subscription
                       + SDK)
```

Phases 1–2 are fully executable now and deliver the tested flipper brain.
Phases 3–4 wait on the subscription/SDK.

---

## What this plan deliberately does NOT do (YAGNI)

- No multi-account/mule support (single account — spec §4).
- No banking/walking adapters yet (flipper barely uses them; built with later
  scripts).
- No Shadow fat-JAR / `~/.tribot/automations` deploy (wrong model — archived).
- No speculative ports beyond what the flipper needs.
