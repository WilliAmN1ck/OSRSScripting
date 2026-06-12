# Phase 3 — GE Flipper · Step 3c (config UI, persistence, breaks)
# Handoff

**Date:** 2026-06-12
**Branch:** `phase-3c-config-persistence`
**Plan reference:** [`plan-3c-config-persistence.md`](./plan-3c-config-persistence.md) (approved);
spec: [`spec-3c-config-persistence.md`](./spec-3c-config-persistence.md)
**Status:** ✅ Build + 38 ge-flipper/core tests green (13 → 24 script-side assertions grew across
suites); fatJar packages at ~2.3 MB. Live verification (sidebar render, break behavior,
ScriptSettings path) is Step 3d.

---

## What Was Built

All four scope items from the spec, plus two bugs found and fixed along the way.

### `libraries/core`

- **`StockLedger`** (`core.ge`) — what the flipper bought and hasn't sold, as FIFO lots with
  cost basis. `recordSell` consumes oldest lots first and returns the basis consumed (drives
  profit). Pre-owned inventory never enters it, so the sell pass can't touch it (decision 5).
- **`OfferTracker`** (`core.ge`) — the heart of 3c. Diffs the 8 GE slots tick over tick:
  - stamps `placedAt` first-seen (decision 6), re-matched by **slot + item + side + price**;
    stamps carry the last-seen fill count so restarts don't re-record old fills;
  - buy-fill deltas → `BuyLimitLedger.recordPurchase` + `StockLedger.recordBuy`;
  - sell-fill deltas → `StockLedger.recordSell` + realized profit
    (`price × delta − GeTax − cost basis`); `flipsCompleted++` when a SELL completes;
  - `stamps()`/`restore()` round-trip through persistence.
- **`PersistedState` v2** — adds `stockEntries` + `offerStamps` (new value classes `StockEntry`,
  `OfferStampEntry`). v1 files load with the new fields empty; null list elements inside
  otherwise-valid JSON are dropped on load (fail-safe).
- **`StateMapper`** (`core.persistence`) — converts runtime ledgers/tracker ↔ `PersistedState`
  both ways; stamps with unreadable `side` values are skipped (re-stamped first-seen later).

### `scripts/ge-flipper`

- **`FlipTask`** — now observes offers through the tracker (placement times flow into the
  engine's `maxOfferAge` staleness check, finally live), filters sellable stock to
  `min(inventory, ledger-owned)`, re-reads config each tick via `Supplier<FlipConfig>`, and
  snapshots state to the persister after any tick that executed actions.
- **`BreakSource` / `BreakIdleTask` / `SdkBreakSource`** — `BreakIdleTask` sits first in the
  `TaskRunner` list and shadows the flip tasks while `BreakHandler.isOnBreak()`; offers keep
  filling passively (decision 4). No `setEnabled` calls (decision 8).
- **`FlipperPanel` + `StatsSnapshot`** — plain-Swing sidebar tab (no SDK imports): the 7
  `FlipConfig` fields with validation + Apply (invalid input shows an error and leaves the
  running config untouched), and a stats readout (runtime, session/all-time profit, flips,
  cash, per-slot offers) marshalled onto the EDT.
- **`GeFlipperScript`** — composition root: loads state from
  `ScriptSettings.getDefault().getDirectory()/ge-flipper-state.json` via `StateStore`, restores
  ledgers/tracker, registers the sidebar tab, runs the loop, refreshes stats each tick, and on
  shutdown saves state *before* sidebar teardown.
- **`build.gradle.kts`** — declares the `fatJar → :libraries:core:jar` task dependency the dev
  plugin omits (lazily, via `tasks.matching`, since the plugin registers `fatJar` after
  evaluation). `useCompose` stays `false` — see below.

## What Changed From the Spec/Plan

- **Sidebar is Swing, not Compose** (already corrected in the spec): `Sidebar.addSidebarTab`
  takes a `JPanel`; the icon parameter is `@Nullable` so we pass `null`.
- **`OfferStampEntry` carries `filled`** — the plan's stamp shape gained a fill baseline so a
  restart doesn't double-record fills that happened before shutdown.
- **Persistence twins instead of shared classes** — `StockLedger.Lot`/`OfferTracker.Stamp`
  (core.ge, Jackson-free) mirror `StockEntry`/`OfferStampEntry` (core.persistence), following
  the existing `Purchase`/`LedgerEntry` precedent; `StateMapper` (new, not in the plan) owns
  both conversions instead of a `FlipTask.snapshot()` method.
- **Stats are assembled in `GeFlipperScript`** from tracker counters + client reads each loop,
  not pushed through a holder from `FlipTask` as the plan sketched. Costs one extra (local,
  in-process) `client.offers()` read per 2 s tick — judged negligible.

## Bugs found and fixed (beyond the planned scope)

1. **`BuyLimitLedger.recordPurchase` was never called at runtime** (3b gap): buy limits were
   silently unenforced in live runs. Buy-fill deltas now feed the ledger via `OfferTracker`.
2. **Dev-plugin `fatJar` task dependency missing**: `gradlew build fatJar` in one invocation
   failed Gradle's implicit-dependency validation (and the plain `jar` task shares the
   `ge-flipper.jar` output path). Fixed with an explicit `dependsOn`; because of the shared
   output path, **run `fatJar` (or `deployLocally`) as the final task** when producing a
   deploy artifact.

## Code-review findings fixed (max-effort review, 9 finder angles)

- `FlipperPanel` max-slots bound check now happens before the `int` cast (overflowing input
  could wrap negative and bypass the 1–8 validation) — regression test added.
- `PersistedState` drops null list elements (valid-JSON corruption crashed `restore` instead
  of failing safe) — regression test added.
- Shutdown save reordered before `removeSidebarTab` so a teardown failure can't skip it.

Refuted (for the record): "fill-only ticks aren't persisted → double-count after crash" —
stamps and ledgers snapshot **together**, so post-restart replay recomputes the same deltas
from the saved baseline; recovery is idempotent by construction.

## Test Coverage

`:libraries:core:test` + `:scripts:ge-flipper:test`, all green:
- `StockLedgerTest` (5) — FIFO basis, clamping, round-trip.
- `OfferTrackerTest` (9) — first-seen stamping, identity re-match, phantom-delta guard,
  buy/sell fill recording, tax-aware profit, flip completion, restore without double-count.
- `StateMapperTest` (2) + `StateStoreTest` (5, incl. v1-compat fixture and null-element file).
- `FlipTaskTest` (8) — staleness cancel (live), pre-owned stock excluded, persister cadence,
  live config swap, market-failure swallow.
- `BreakIdleTaskTest` (2) — shadowing semantics via a real `TaskRunner`.
- `FlipperPanelTest` (4) — headless apply/validation (incl. overflow), stats rendering.

SDK-coupled classes (`SdkGeClient`, `SdkBreakSource`, `GeFlipperScript` wiring) are verified
by compilation + the 3d live run, per the established seam.

## Files Changed

| File | Change | Notes |
|---|---|---|
| `libraries/core/.../ge/StockLedger.java` | new | FIFO bought-stock + cost basis |
| `libraries/core/.../ge/OfferTracker.java` | new | stamps, fills, profit; nested `Stamp` |
| `libraries/core/.../persistence/StockEntry.java` | new | persistence twin of `Lot` |
| `libraries/core/.../persistence/OfferStampEntry.java` | new | persistence twin of `Stamp` |
| `libraries/core/.../persistence/PersistedState.java` | modified | v2 fields, null-safe load |
| `libraries/core/.../persistence/StateMapper.java` | new | runtime ↔ persisted, both ways |
| `scripts/ge-flipper/.../FlipTask.java` | modified | tracker, stock filter, supplier config, persister |
| `scripts/ge-flipper/.../BreakSource.java`, `BreakIdleTask.java`, `SdkBreakSource.java` | new | break pause |
| `scripts/ge-flipper/.../FlipperPanel.java`, `StatsSnapshot.java` | new | Swing sidebar tab |
| `scripts/ge-flipper/.../GeFlipperScript.java` | modified | composition root |
| `scripts/ge-flipper/build.gradle.kts` | modified | fatJar task dependency |
| `README.md` | modified | structure + build commands |

## Known Issues / Tech Debt

- **Profit under-counts better-price fills**: proceeds are `price × fill delta`; the SDK's
  `getTransferredGoldQuantity` is not mapped into `GeOffer`. Extend the model if accuracy
  matters after live observation.
- **Fills missed while the script is down** (offer completes *and* is collected before
  restart) are unrecoverable — inherent to polling without a client-side history API.
- **No sell-exit escalation** (spec decision 9, deliberate): stale sells cancel and relist at
  the current wiki high; revisit insta-sell/undercut after 3d.
- **`jar` and `fatJar` share the `ge-flipper.jar` output path** (dev-plugin design): a plain
  `jar` run after `fatJar` clobbers the fat artifact. Always produce deploy JARs with a final
  `fatJar`/`deployLocally` invocation.
- **Loop stop mechanism still assumed** (carried from 3b): relies on thread interruption;
  confirm the real TRiBot stop signal in 3d — the `finally` save depends on it.

## What the Next Step (3d) Needs to Know

- Verify in Echo: sidebar tab renders and Apply works; `ScriptSettings.getDefault()`
  resolves to the expected per-script directory and `ge-flipper-state.json` appears after the
  first action tick; break handler pause; stale-offer cancellation with a short
  `maxOfferAge`; and the stop signal triggers the `finally` save.
- Deploy: `./gradlew :scripts:ge-flipper:deployLocally` (run it last; see output-path note).
- The default config is conservative (1 M cap, 4 slots); use the sidebar to tune live.

## Verification Commands

    # JAVA_HOME may sit on JDK 11; the daemon auto-selects JDK 21 (criteria file)
    .\gradlew.bat :libraries:core:test :scripts:ge-flipper:test   # all suites green
    .\gradlew.bat build :scripts:ge-flipper:fatJar                # one invocation now works
    # -> scripts/ge-flipper/build/libs/ge-flipper.jar (~2.3 MB)
