# Phase 2 — Core Library (pure logic)
# Handoff

**Date:** 2026-06-11
**Branch:** main
**Plan reference:** [`plan.md`](./plan.md) — "Phase 2 — Core Library (Track A)"
**Status:** ✅ Complete. 35 tests green. Phase 3 remains blocked on the TRiBot subscription/SDK.

---

## What Was Built

The entire SDK-independent flipper "brain" in `libraries/core`
(package root `com.osrsscripts.core`), strict TDD, no TRiBot dependency:

- **`model/`** — immutable value types: `ItemMeta`, `PricePoint`, `VolumePoint`,
  `OfferSide`, `OfferStatus`, `GeOffer`, `AccountState`, `FlipConfig` (builder),
  `FlipCandidate`.
- **`prices/`** — `HttpFetcher` seam, `WikiHttpFetcher` (real client w/ required
  User-Agent), `WikiPriceClient` reading the OSRS Wiki `/mapping`, `/latest`,
  `/1h` endpoints with per-endpoint TTL caching. Tolerant of null/missing fields.
- **`ge/`** — `GeTaxRules` + `GeTax` (basis-point, floored, capped, exempt),
  `BuyLimitLedger` (rolling 4-hour window, side-effect-free queries),
  `FlipScanner` (tax-aware margin/ROI/volume filtering + throughput ranking),
  `FlipEngine` + `FlipAction` + `ActionType` (the pure decision engine).
- **`persistence/`** — `PersistedState`, `LedgerEntry`, `StateStore`
  (atomic write, fail-safe load to empty on missing/corrupt).
- **`humanize/`** — `DelayDistribution` (seeded, bounded Gaussian),
  `BreakScheduler` (clock-driven work/break cycle).
- **`task/`** — `Task` + `TaskRunner` (prioritized first-eligible state machine).

### The key seam (for Phase 3)

`FlipEngine.decide(...)` is pure: it takes ranked candidates + prices + account
state + buy-limit ledger + config + `now`, and returns a `List<FlipAction>`
(`PLACE_BUY` / `PLACE_SELL` / `COLLECT` / `CANCEL`). Phase 3's only SDK-coupled
flipper class is the executor that carries these actions out.

---

## What Changed From the Plan (deviations, all "Simplicity First")

- **Item identity is a plain `int`**, not an `ItemId` wrapper — it's the natural
  map/JSON key and avoids Jackson key (de)serialization friction.
- **One `FlipAction` value class + `ActionType` enum**, not a class hierarchy —
  less boilerplate, equally testable; the executor switches on `type`.
- **Persistence stores epoch-millis `long`s**, not `Instant`, to avoid pulling in
  `jackson-datatype-jsr310`.
- **Mockito omitted** (plan listed it) — every collaborator is exercised with a
  hand-written fake or `java.util.Random`/`Clock`, so Mockito was unused (YAGNI).
  Add it in Phase 3 if the SDK adapters need it.
- **`BreakScheduler` is deterministic** (fixed work/break periods) rather than
  randomized, to keep it testable. Randomizing the periods (via
  `DelayDistribution`) is a later enhancement.
- **GE tax rate is provisional and parameterized** — `GeTaxRules.defaults()` uses
  2% / 5M cap / ≤100gp exempt, flagged "verify against the live game." Tests pin
  explicit rules, so they're correct regardless of the live rate.

---

## What the Next Phase (Phase 3) Needs to Know

- **Run Gradle on `jdk-11.0.2`.** Normal builds need no special flags now that
  dependencies are cached. JDK 26 (`jdk-26.0.1`) is installed but **too new for
  Gradle 8.10.2** (it runs `--version` but a real build fails parsing the version
  string); using it would require bumping the wrapper to Gradle 9.x.

      $env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"
      .\gradlew.bat :libraries:core:test

  A **TLS workaround is only needed on a cold dependency cache** (the 2019
  `jdk-11.0.2` has stale `cacerts` + buggy TLS 1.3): if a fresh download fails,
  add `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=Windows-ROOT
  -Djdk.tls.client.protocols=TLSv1.2"`. **CI is unaffected** (Temurin 11 has
  current certs).
- **The `FlipAction` seam** is intentionally provisional; expect minor shape
  changes once real SDK GE signatures are known.
- **`AccountState` is the contract with the client:** Phase 3's adapter must read
  cash, the 8 GE slots (as `GeOffer`s) and owned-but-uncommitted `stock` each tick.
- **Cost-basis / realized-profit tracking is not implemented yet** — `PersistedState`
  reserves `realizedProfit`/`flipsCompleted` fields but the engine does not compute
  them. Add when wiring the executor (it needs the buy price to compute profit on
  sell completion).
- **Verify the live GE tax rate** and populate `GeTaxRules` exempt-item ids.

---

## Files Changed

| Area | Files | Notes |
|---|---|---|
| Build | `libraries/core/build.gradle.kts` | java-library, Jackson 2.17.2, JUnit 5.10.2 |
| Models | `model/*.java` (9) | immutable value types |
| Prices | `prices/*.java` (3) | Wiki client + HTTP seam |
| GE logic | `ge/*.java` (6) | tax, ledger, scanner, engine, action(+type) |
| Persistence | `persistence/*.java` (3) | atomic JSON state store |
| Humanize | `humanize/*.java` (2) | delay + break |
| Task | `task/*.java` (2) | framework |
| Tests | `src/test/.../*.java` (9) + `testutil/AdjustableClock` | 35 tests |
| Removed | `libraries/core/.gitkeep` | orphaned once real source landed |

---

## Test Coverage

35 tests, 0 failures (`./gradlew :libraries:core:test`):

| Suite | Tests | Covers |
|---|---|---|
| GeTaxTest | 6 | rate, exemptions, cap, qty, net margin |
| BuyLimitLedgerTest | 5 | window, per-item, remaining, unknown limit, prune |
| FlipScannerTest | 4 | margin/ROI/volume filters, ranking |
| FlipEngineTest | 7 | collect, sell stock, buy + per-item cap, full slots, buy-limit, stale cancel, total cap |
| WikiPriceClientTest | 4 | mapping/latest/1h parsing, TTL caching |
| StateStoreTest | 3 | round-trip, missing → empty, corrupt → empty |
| DelayDistributionTest | 3 | bounds, determinism, invalid range |
| BreakSchedulerTest | 1 | work/break/resume cycle |
| TaskRunnerTest | 2 | first-eligible, none-eligible |

---

## Known Issues / Tech Debt

- **GE tax rate provisional** — must be verified against the live game before
  trusting profit math.
- **No realized-profit/cost-basis tracking** yet (see Phase 3 notes).
- **`BreakScheduler` periods are fixed**, not yet randomized.
- **Engine is per-tick and stateless across ticks** — it relies on the next tick's
  `AccountState` reflecting collected stock; correct, but means a one-tick lag
  between collecting a completed buy and placing its sell.
- **Gradle pinned to JDK 11 to run.** JDK 26 is installed but Gradle 8.10.2
  cannot build on it; bump the wrapper to Gradle 9.x to use a modern JDK. The
  cold-cache TLS workaround (see Phase 3 notes) is only needed when downloading
  dependencies fresh on the old JDK.
- Gradle 8.10.2-on-JDK-11 deprecation persists (documented in the Phase 1 handoff).

---

## Verification Commands

    $env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"
    .\gradlew.bat :libraries:core:test    # BUILD SUCCESSFUL — 35 tests, 0 failures
    # (only on a cold dependency cache, also set JAVA_TOOL_OPTIONS — see notes above)

> Not yet committed. Per project process, run `/code-review max` before committing.
