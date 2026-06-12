# Phase 3 — GE Flipper · Step 3c (config UI, persistence, breaks)
# Implementation Plan

**Date:** 2026-06-12
**Spec reference:** [`spec-3c-config-persistence.md`](./spec-3c-config-persistence.md) (approved)
**Branch:** `phase-3c-config-persistence` (new, off `main` after the 3b PR merges)
**Status:** Draft — awaiting confirmation

---

## Open items from the spec — now resolved

| Spec open item | Resolution |
|---|---|
| Per-script settings directory | `org.tribot.script.sdk.util.ScriptSettings.getDefault().getDirectory()` (confirmed in `script-sdk-1.0.17.jar`) returns the canonical per-script dir. We point our existing `StateStore` at `<dir>/state.json` — we keep Jackson + atomic write + corrupt-recovery rather than adopting `ScriptSettings.save/load` (gson-based, no atomicity guarantee). |
| Offer re-match key | **slot + itemId + side + price.** Timestamps are stored per slot; on each tick the stored identity is compared to the live offer in that slot. Match → keep timestamp; mismatch or empty → drop it (and stamp first-seen if a new live offer is present). |
| Where profit accounting lives | `libraries:core` (`OfferTracker`, new) — pure, `Clock`/`Instant`-driven, TDD'd. The script module only feeds it snapshots. |
| Sidebar icon | The `BufferedImage` parameter is `@Nullable` (confirmed via javap annotations) — pass `null`. |

## Bug discovered during planning (fixed by this plan)

`BuyLimitLedger.recordPurchase(...)` is **never called from the script module** — in live
runs the buy-limit ledger stays empty and limits are never enforced. The same
offer-diffing that 3c needs for the stock ledger and profit also detects buy fills, so
`OfferTracker` fixes this for free. Called out so the fix is deliberate, not incidental.

## Design overview

One new pure core class does the heavy lifting:

```
                       previous offers ──┐
GeClient.offers() ──► OfferTracker.observe(current, now)   (core, pure)
                       │  ├─ stamps placedAt (first-seen; key slot+item+side+price)
                       │  ├─ buy fill deltas  → BuyLimitLedger.recordPurchase
                       │  │                   → StockLedger.recordBuy (qty + cost)
                       │  └─ sell fill deltas → StockLedger.recordSell
                       │                      → profit += proceeds(after GeTax) − cost basis
                       ▼
            stamped offers → FlipEngine.decide(...)   (unchanged)
```

- **`StockLedger`** (core, new) — what the flipper bought and hasn't sold: per item
  `qty` + `costBasis`. The sell pass is limited to `min(inventory, ledger qty)` — the
  **engine stays unchanged**; `FlipTask` filters `stock()` before building `AccountState`.
- **Profit simplification (documented):** sell proceeds = `price × filledDelta − GeTax`.
  The GE can fill at a better price than asked; our `GeOffer` model has no
  transferred-gold field, so better-price fills are under-counted. Acceptable for 3c;
  noted as tech debt.
- **Live config:** `FlipTask` takes `Supplier<FlipConfig>` instead of `FlipConfig`; the
  sidebar panel swaps an `AtomicReference<FlipConfig>` it shares with the task.
- **Breaks:** `BreakSource` port (script module) + `BreakIdleTask` placed first in the
  `TaskRunner` list — eligible while `isOnBreak()`, does nothing, thereby blocking the
  other tasks. Unit-testable, no SDK types.
- **Persistence:** `PersistedState` v2 adds `stockEntries` + `offerStamps`. Loaded at
  startup into `BuyLimitLedger`/`StockLedger`/`OfferTracker`; saved after any tick that
  executed ≥1 action, and on shutdown (`finally`). Old v1 files load with the new fields
  empty (Jackson treats missing as null → empty list, already the constructor behavior).

Module placement rule (unchanged from 3b): everything testable is core or SDK-free
script code; `SdkGeClient`, `SdkBreakSource`, and the `GeFlipperScript` composition root
are the only SDK-touching classes.

---

## Sub-phase 3c.1 — Core: `StockLedger` (TDD)

New: `libraries/core/src/main/java/com/osrsscripts/core/ge/StockLedger.java`
Test: `libraries/core/src/test/java/com/osrsscripts/core/ge/StockLedgerTest.java`

API (mirrors `BuyLimitLedger`'s mutable-with-load/snapshot style):

```java
public final class StockLedger {
    public void recordBuy(int itemId, int qty, long pricePerItem);
    /** Removes qty (FIFO against cost basis) and returns the cost basis consumed. */
    public long recordSell(int itemId, int qty);
    public int ownedQty(int itemId);
    public Map<Integer, Integer> ownedQuantities();   // for the stock filter
    public List<StockEntry> entries();                 // persistence snapshot
    public void load(List<StockEntry> entries);
}
```

Tasks:
1. **Failing tests first:** buy adds qty+cost; sell consumes FIFO cost basis and returns
   it; selling more than owned clamps to owned (returns basis for what existed);
   `ownedQty` of unknown item = 0; load/entries round-trip.
2. Implement until green.

**Acceptance:** `:libraries:core:test` green; no engine/test regressions.

## Sub-phase 3c.2 — Core: `OfferTracker` (TDD)

New: `libraries/core/src/main/java/com/osrsscripts/core/ge/OfferTracker.java`
Test: `libraries/core/src/test/java/com/osrsscripts/core/ge/OfferTrackerTest.java`

API:

```java
public final class OfferTracker {
    public OfferTracker(BuyLimitLedger buyLimits, StockLedger stock, GeTax tax);
    /** Diffs against the previous snapshot; returns offers with placedAt stamped. */
    public List<GeOffer> observe(List<GeOffer> current, Instant now);
    public long realizedProfit();
    public long flipsCompleted();
    // persistence
    public List<OfferStamp> stamps();
    public void restore(List<OfferStamp> stamps, long realizedProfit, long flipsCompleted);
}
```

Internal state: per-slot `OfferStamp {slot, itemId, side, price, epochMillis}` + per-slot
last-seen `filled`.

Tasks (each: failing test → implement):
1. First-seen stamping — unknown live offer gets `placedAt = now`; same offer next tick
   keeps the original stamp (decision 6).
2. Re-match key — same slot but different item/side/price → old stamp dropped, fresh
   first-seen stamp; empty slot → stamp pruned (spec §4.3).
3. Buy fills — `filled` delta on a BUY offer (including COMPLETE transitions) →
   `BuyLimitLedger.recordPurchase(item, delta, now)` + `StockLedger.recordBuy(item,
   delta, price)`. Slot reset between ticks (new offer) must not produce a phantom delta.
4. Sell fills — delta on a SELL offer → `StockLedger.recordSell`; profit +=
   `delta × price − GeTax.taxOnSale(price, delta) − costBasisConsumed`;
   `flipsCompleted++` when a SELL reaches COMPLETE.
5. `restore(...)` round-trips through `stamps()`; restored stamps only re-attach to
   matching live offers (the re-match key applies on the first observe after restart).

**Acceptance:** core tests green; `FlipEngine`/`FlipEngineTest` untouched.

## Sub-phase 3c.3 — Core: `PersistedState` v2 (TDD)

Modified: `PersistedState.java` — add `List<StockEntry> stockEntries`,
`List<OfferStamp> offerStamps` (new value classes beside `LedgerEntry`, same
Jackson-creator style; `OfferStamp` lives in `core.ge`, re-used by the tracker).
Test: extend `StateStoreTest`.

Tasks:
1. Failing tests: round-trip with the new fields; **a v1 JSON fixture (no new fields)
   loads with them empty**; corrupt file still → `empty()`.
2. Implement `StockEntry`, `OfferStamp`, extend `PersistedState` (null → empty list, as
   the existing constructor does).

**Acceptance:** persistence tests green, including the v1-compat fixture.

## Sub-phase 3c.4 — Script: `FlipTask` integration + per-tick persistence

Modified: `FlipTask.java`, `FlipTaskTest.java`, `GeFlipperScript.java` (wiring only
compiles; full composition in 3c.6).

`FlipTask` constructor becomes:

```java
FlipTask(GeClient client, WikiPriceClient prices, FlipScanner scanner, FlipEngine engine,
         GeTax tax, BuyLimitLedger ledger, StockLedger stock, OfferTracker tracker,
         Supplier<FlipConfig> config, FlipActionExecutor executor,
         Consumer<PersistedState> persister)
```

`execute()` changes, in order:
1. `List<GeOffer> offers = tracker.observe(client.offers(), Instant.now())` — stamped.
2. Stock filter: `stock = min(client.stock(), stockLedger.ownedQuantities())` per item
   (items absent from the ledger are excluded entirely — decision 5).
3. Build `AccountState` from stamped offers + filtered stock; decide; execute.
4. If `actions` non-empty → `persister.accept(snapshot())` where `snapshot()` assembles
   `PersistedState` from `ledger.purchases()`, `stock.entries()`, `tracker.stamps()`,
   `tracker.realizedProfit()`, `tracker.flipsCompleted()`.

Tasks:
1. Failing tests: stale offer (stamped past `maxOfferAge` via tracker restore) emits
   CANCEL; pre-owned inventory item is **not** sold while a ledger-tracked item is;
   persister called after an action tick, not called on a no-action tick; config supplier
   re-read each tick (change the supplied config between ticks, assert behavior change).
2. Implement; update existing `FlipTaskTest` construction sites.

**Acceptance:** `:scripts:ge-flipper:test` green.

## Sub-phase 3c.5 — Script: break guard

New: `BreakSource.java` (port: `boolean isOnBreak();`), `SdkBreakSource.java`
(wraps `Sidecars.getBreakHandler().isOnBreak()`; SDK-touching, not unit-tested),
`BreakIdleTask.java` + test.

`BreakIdleTask`: `shouldRun() = breaks.isOnBreak()`, `execute()` no-op,
registered **first** in the task list so it shadows `EnsureGeOpenTask`/`FlipTask`
during breaks. No `setEnabled` calls anywhere (decision 8).

Tasks: failing test (on break → idle task selected, flip task untouched; off break →
normal flow) → implement.

**Acceptance:** script tests green.

## Sub-phase 3c.6 — Script: sidebar panel + composition root

New: `FlipperPanel.java` (Swing `JPanel`, **no SDK imports**), `StatsSnapshot.java`
(runtime, session/all-time profit, flips, cash, per-slot offer lines).
Test: `FlipperPanelTest` (headless: construct, feed text, assert config produced /
rejected — guard with `GraphicsEnvironment.isHeadless()`-safe components only).

Panel:
- Editable fields for all 7 `FlipConfig` values; "Apply" validates (numeric, > 0 bounds,
  `maxOfferAge` in minutes) and on success swaps the shared
  `AtomicReference<FlipConfig>`; invalid input shows an inline error and leaves the
  current config in place (spec §4.1).
- Stats area: plain `JLabel`s; `void update(StatsSnapshot s)` marshals via
  `SwingUtilities.invokeLater`.

`GeFlipperScript.execute()` composition:
1. `Path stateFile = ScriptSettings.getDefault().getDirectory().toPath()
   .resolve("ge-flipper-state.json")`; `StateStore store = new StateStore(stateFile)`.
2. Load `PersistedState` → `ledger.load(...)`, `stockLedger.load(...)`,
   `tracker.restore(...)`.
3. `AtomicReference<FlipConfig> config = new AtomicReference<>(defaultConfig())` (defaults
   stay as today's values; panel edits replace them).
4. Panel: `context.getSidebar().addSidebarTab("GE Flipper", null, panel)`; stats pushed
   each loop iteration; `removeSidebarTab` in `finally`.
5. Persister: `state -> { try { store.save(state); } catch (IOException e) { /* log,
   keep running */ } }`; also save unconditionally in the `finally` on shutdown.
6. Tasks: `BreakIdleTask(new SdkBreakSource(context.getSidecars()))`, `EnsureGeOpenTask`,
   `FlipTask(...)`.

Tasks:
1. `StatsSnapshot` + assembling it in the loop (cash/offers via `GeClient` reads already
   available from the tick — pass through a small mutable holder updated by `FlipTask`).
2. `FlipperPanel` + headless tests (validation logic extracted to a package-private
   method so tests don't need a display).
3. Rewrite `GeFlipperScript.execute()` per the composition above; delete the
   now-obsolete javadoc about hardcoded config.

**Acceptance:** script tests green; `:scripts:ge-flipper:build` and `fatJar` succeed.

## Sub-phase 3c.7 — Verification, review, handoff

1. Full suite: `:libraries:core:test`, `:scripts:ge-flipper:test`, root `build`, `fatJar`.
2. `/code-review max` on the working tree; fix findings at root cause.
3. Update `README.md` if the run/config story changed.
4. Write `handoff-3c-config-persistence.md` (mandatory sections per CLAUDE.md), including
   the profit-undercount tech-debt note and anything 3d needs (manifest unchanged,
   sidebar verified visually in 3d, stop-signal question still open).
5. PR `phase-3c-config-persistence` → `main`.

---

## Dependencies

```
3c.1 StockLedger ─┐
                  ├─► 3c.2 OfferTracker ─► 3c.3 PersistedState v2 ─► 3c.4 FlipTask
(GeTax exists) ───┘                                                      │
3c.5 break guard (independent) ──────────────────────────────┬──────────┤
                                                             ▼          ▼
                                                   3c.6 panel + composition ─► 3c.7
```

3c.5 can be built in parallel with 3c.1–3c.4.

## Out of scope (per spec)

Sell-exit escalation (decision 9), gson migration, engine ranking changes, the live Echo
run (3d). `GeOffer` gains no transferred-gold field (profit simplification noted above).

## Verification commands

    $env:JAVA_HOME = "C:\Program Files\Java\jdk-11.0.2"   # daemon auto-runs on JDK 21
    .\gradlew.bat :libraries:core:test
    .\gradlew.bat :scripts:ge-flipper:test
    .\gradlew.bat build
    .\gradlew.bat :scripts:ge-flipper:fatJar
