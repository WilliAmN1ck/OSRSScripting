# Phase 3 — GE Flipper · Step 3b (executor + SDK adapter + loop)
# Handoff

**Date:** 2026-06-12
**Branch:** `phase-3b-executor-loop`
**Plan reference:** [`../../../.claude/plans/melodic-juggling-bird.md`] (approved); canonical
Phase 3 in [`../phase-0-workspace-setup/plan.md`](../phase-0-workspace-setup/plan.md) items 2–4;
SDK reference: [`../../reference/tribot-sdk.md`](../../reference/tribot-sdk.md)
**Status:** ✅ Build + 13 unit tests green; fatJar packages. Live Echo run is Step 3d.

---

## What Was Built

The flipper brain is now connected to the TRiBot Script SDK. `GeFlipperScript.execute()` runs a
real scan → decide → execute loop via the `TaskRunner` framework.

All new code is in `scripts/ge-flipper/src/main/java/com/osrsscripts/geflipper/`:

- **`GeClient`** — port interface speaking only in `libraries:core` types (offers/coins/stock +
  open/placeBuy/placeSell/abort/collect). The seam that keeps the executor and tasks testable
  without a game client. Mirrors the existing `HttpFetcher`→`WikiHttpFetcher` pattern.
- **`SdkGeClient`** — the **only** class touching the Script SDK. Thin delegation to
  `GrandExchange`, `Inventory`, and `Query.grandExchangeOffers()`. Not unit-tested (verified by
  compilation + the 3d live run).
- **`OfferMapper`** — pure SDK→engine translation (primitives + enum *names*, no SDK types).
  Re-derives `PARTIAL` from transferred qty; leaves `placedAt = null`; `fillEightSlots` rebuilds
  the full 8-slot view.
- **`FlipActionExecutor`** — maps each `FlipAction` to a `GeClient` call; collapses N `COLLECT`s
  into one `collect()`.
- **`EnsureGeOpenTask`** / **`FlipTask`** (`Task`s) — the loop body. `EnsureGeOpenTask` opens the
  GE when closed; `FlipTask` reads account+market, ranks, decides, executes (and swallows a
  transient `IOException` from the price client so a wiki blip skips the tick).
- **`GeFlipperScript`** — wires the collaborators, a hardcoded `FlipConfig`, and a
  `TaskRunner(EnsureGeOpenTask, FlipTask)`; loops with `context.getWaiting().sleep(2s)` until the
  script thread is interrupted.

## SDK signatures confirmed (from the cached `script-sdk-1.0.17` jar)

- Offer query factory is **`Query.grandExchangeOffers()`** (no public `new
  GrandExchangeOfferQuery()`); terminal `.toList()` is a `Query` default method.
- `GrandExchangeOffer.Slot` is an **enum** `ONE`..`EIGHT` → mapped via `ordinal()+1`.
- `Status` = `EMPTY/IN_PROGRESS/COMPLETED/CANCELLED` (no `PARTIAL`); `Type` = `BUY/SELL`.
- Offer builder: `GrandExchange.CreateOfferConfig.builder().type(..).itemId(..).price(..).quantity(..).build()`.
- Cash = `Inventory.getCount(995)`; stock = `Inventory.getAll()` → `getId()`/`getStack()`.
- Loop wait = `ScriptContext.getWaiting().sleep(long)`.

## What Changed From the Plan

- **None substantive.** The plan's `Query.grandExchangeOffers()` factory replaced the originally
  assumed `new GrandExchangeOfferQuery()` (the no-arg constructor isn't public — caught at
  compile time). Added a guard to **skip EMPTY-status offers** in `SdkGeClient.offers()` (they're
  reconstructed by `fillEightSlots`), avoiding a possible null slot/type NPE in-client.

## Test Coverage

`:scripts:ge-flipper:test` — **13 tests, all green**:
- `OfferMapperTest` (5) — status re-derivation incl. ACTIVE-vs-PARTIAL split, side mapping,
  null `placedAt`, `fillEightSlots` gap-filling.
- `FlipActionExecutorTest` (5) — each action kind dispatches correctly; multiple COLLECTs → one.
- `FlipTaskTest` (3) — `shouldRun` follows open state; happy path buys the ranked candidate
  (canned-JSON `WikiPriceClient`); `IOException` is swallowed with no actions.

Tests reference no SDK types, so they compile/run without the SDK on the test classpath.

## Files Changed

| File | Change | Notes |
|---|---|---|
| `scripts/ge-flipper/.../GeClient.java` | new | Port interface (core types only) |
| `scripts/ge-flipper/.../SdkGeClient.java` | new | Sole SDK-backed impl |
| `scripts/ge-flipper/.../OfferMapper.java` | new | Pure SDK→engine translation |
| `scripts/ge-flipper/.../FlipActionExecutor.java` | new | FlipAction → GeClient |
| `scripts/ge-flipper/.../EnsureGeOpenTask.java` | new | Opens GE |
| `scripts/ge-flipper/.../FlipTask.java` | new | One flip tick |
| `scripts/ge-flipper/.../GeFlipperScript.java` | modified | No-op → TaskRunner loop |
| `scripts/ge-flipper/build.gradle.kts` | modified | JUnit 5 test deps + `useJUnitPlatform()` |
| `scripts/ge-flipper/src/test/.../*` | new | 3 test classes + `FakeGeClient` |

## Known Issues / Tech Debt (carried forward)

- **Staleness inert.** `placedAt = null` (SDK exposes no timestamp), so `FlipConfig.maxOfferAge`
  never cancels stale live offers. Deferred to 3c with persisted placement timestamps.
- **`stock()` = all inventory items (minus coins).** The engine's sell pass acts on any item with
  a wiki `high` price, so a stray non-flip item in the inventory could be offered for sale.
  Restricting to items we actually bought pairs with the 3c ledger work.
- **Hardcoded `FlipConfig`** — replaced by the sidebar config panel in 3c.
- **In-memory `BuyLimitLedger`** — not persisted yet; resets each run until 3c's `StateStore`.
- **Loop stop mechanism assumed** — relies on the script thread being interrupted; confirm the
  exact TRiBot stop signal during the 3d live run.

## What the Next Step (3c) Needs to Know

- Bind `FlipConfig` + live stats through `ScriptContext.getSidebar()` (enable `useCompose` in
  `build.gradle.kts`); replace `GeFlipperScript.defaultConfig()`.
- Persist `BuyLimitLedger` (and placement timestamps for staleness) via `StateStore`; load at
  startup, save periodically. Note the SDK reference's gson-vs-Jackson caveat — Phase 3a confirmed
  Jackson bundles fine into the fat JAR, so no migration is forced.
- Breaks/login via `ScriptContext.getSidecars()`.

## Verification Commands

    # JAVA_HOME on JDK 11 bootstraps the wrapper; the daemon auto-runs on JDK 21 (criteria file)
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"
    .\gradlew.bat :scripts:ge-flipper:test     # 13 tests green
    .\gradlew.bat :scripts:ge-flipper:build    # compiles SdkGeClient against the compileOnly SDK
    .\gradlew.bat :scripts:ge-flipper:fatJar   # -> scripts/ge-flipper/build/libs/ge-flipper.jar (~2.3 MB)
