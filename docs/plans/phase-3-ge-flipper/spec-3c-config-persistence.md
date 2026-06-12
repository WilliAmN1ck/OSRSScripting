# Phase 3 — GE Flipper · Step 3c (config UI, persistence, breaks)
# Spec

**Date:** 2026-06-12
**Status:** Draft — awaiting confirmation
**Inputs:** [3b handoff](./handoff-3b-executor-loop.md) (scope + carried tech debt),
[SDK reference](../../reference/tribot-sdk.md), jar inspection of `automation-sdk-1.0.19`
(this spec), Q&A decisions recorded below.

---

## 1. Scope

3c delivers all four items carried out of 3b:

1. **Sidebar config panel** — replaces the hardcoded `GeFlipperScript.defaultConfig()`;
   shows live stats.
2. **Persistence** — `BuyLimitLedger`, profit stats, offer placement timestamps, and the
   bought-items record survive restarts via the existing `StateStore`.
3. **Staleness made real** — `FlipConfig.maxOfferAge` actually cancels old offers, using
   persisted placement timestamps.
4. **`stock()` fix** — the sell pass only offers items the flipper itself bought, tracked
   in the persisted state.

Out of scope: the live Echo run (3d), any engine ranking changes, gson migration
(Jackson bundles fine — settled in 3a), multi-account, walking/banking, and any
sell-exit escalation beyond cancel-and-relist (decision 9 — revisit after 3d).

## 2. Confirmed SDK facts (from `automation-sdk-1.0.19.jar`, 2026-06-12)

- `ScriptContext.getSidebar()` → `Sidebar`:
  - `addSidebarTab(String name, java.awt.image.BufferedImage icon, javax.swing.JPanel panel)`
  - `removeSidebarTab(String name)`
  - **Swing, not Compose.** The 3b handoff's "enable `useCompose`" note is obsolete;
    `useCompose` stays `false`.
- `ScriptContext.getSidecars()` → `Sidecars`:
  - `getBreakHandler()` → `BreakHandler extends ScriptSidecar`:
    `isOnBreak()`, `getMillisUntilNextBreak()`, `getMillisUntilBreakEnd()`
  - `getLoginHandler()` → `LoginHandler extends ScriptSidecar` (no extra methods)
  - `ScriptSidecar`: `getName()`, `isActive()`, `isEnabled()`, `setEnabled(boolean)`
  - Break **scheduling** is configured in the TRiBot client UI; scripts can only
    enable/observe.
- `ScriptContext` also exposes `getLoginHandler()` directly.

## 3. Decisions (Q&A, 2026-06-12)

| # | Question | Decision |
|---|---|---|
| 1 | 3c scope | All four items (UI, persistence, breaks/login, stock fix) in one step — the stock fix and staleness share the same `PersistedState` extension. |
| 2 | Config editability | **Live-editable.** Sidebar edits apply on the next tick; the engine reads config through a shared mutable reference (e.g. `AtomicReference<FlipConfig>`). |
| 3 | Persistence location & cadence | Per-script settings dir — `%APPDATA%\.tribot\settings\ge-flipper\state.json` (or the closest per-script dir the client exposes). Save after any tick that executed actions, plus on shutdown. Atomic write is already handled by `StateStore`. |
| 4 | Break policy | **Pause loop, leave offers.** While `isOnBreak()` is true, flip ticks are skipped; open offers keep filling passively and are collected after the break. |
| 5 | Pre-owned inventory | **Ignored.** Only items recorded as bought by the flipper (persisted) are ever offered for sale. |
| 6 | Offers with unknown age | **First-seen = placed.** When an offer has no recorded timestamp, record "now" on first observation and age from there. Untracked/manual offers are never instantly cancelled. |
| 7 | Sidebar stats | **Standard set:** runtime, realized profit (session + all-time), flips completed, current cash, per-slot offer summary. Plain Swing labels refreshed each tick. |
| 8 | Sidecar enablement | **Respect the client setting.** The script never calls `setEnabled(...)`; it only observes `isOnBreak()`. |
| 9 | Unsold-item exit policy | **Defer.** Cancel → collect → relist at the *current* wiki high (the engine's existing behavior, made live by the timestamps) is the only mechanism in 3c. No insta-sell/undercut escalation until 3d live runs show whether capital actually gets stuck. |

## 4. Requirements

### 4.1 Sidebar config panel (Swing)
- One sidebar tab ("GE Flipper") registered via `addSidebarTab` at script start,
  removed on shutdown.
- Editable fields mirror `FlipConfig`: `capitalCap`, `perItemCapitalCap`, `minMarginGp`,
  `minMarginPct`, `minVolume`, `maxSlots`, `maxOfferAge`.
- Edits are validated (numeric, sensible bounds) and swapped into the shared config
  reference; the next `FlipTask` tick uses them. Invalid input never replaces the
  current config.
- Stats area shows the standard set (decision 7), refreshed each tick. Swing updates
  must happen on the EDT (`SwingUtilities.invokeLater`).
- The panel must not pull in any SDK types beyond the `Sidebar` registration point —
  keep it testable headless where practical; rendering itself is verified in 3d.

### 4.2 Persistence
- Extend `PersistedState` with:
  - **placement timestamps** for live offers (keyed so an offer can be re-matched after
    restart — slot + item id + side + price is the candidate key; exact shape is a plan
    decision);
  - **bought-items record**: per item, the quantity the flipper bought and has not yet
    sold (drives the `stock()` fix).
  - Existing fields (`ledgerEntries`, `realizedProfit`, `flipsCompleted`) unchanged.
- Load at startup (corrupt/missing file → `PersistedState.empty()`, existing `StateStore`
  behavior). Save after any tick that executed at least one action, and on shutdown.
- Path: per-script settings dir under `%APPDATA%\.tribot` (decision 3); the exact
  directory is confirmed during planning (check what the SDK/client exposes for
  per-script storage before hardcoding).

### 4.3 Staleness
- `FlipTask` ages each live offer from its persisted placement timestamp; offers older
  than `maxOfferAge` are cancelled by the engine as already designed.
- Unknown offers get a first-seen timestamp (decision 6).
- Timestamps are pruned when their offer leaves the 8-slot view.

### 4.4 stock() fix
- The sell pass consumes the persisted bought-items record instead of raw inventory:
  an item is sellable only up to the quantity the flipper bought minus what it already
  sold. Pre-existing inventory is never offered (decision 5).
- The record is updated on collected buys (+qty) and placed/filled sells (−qty);
  exact update points are a plan decision and must be TDD'd in `libraries:core`
  or the script module as appropriate.

### 4.5 Breaks / login
- `FlipTask.shouldRun()` (or a guard task ahead of it) returns false while
  `BreakHandler.isOnBreak()` is true. No offer cancellation around breaks (decision 4).
- No `setEnabled` calls (decision 8). The `BreakHandler` access goes through a small
  port interface (same pattern as `GeClient`) so the pause behavior is unit-testable.

## 5. Acceptance criteria

- `:scripts:ge-flipper:test` and `:libraries:core:test` green, including new tests for:
  config live-swap, persistence round-trip of the new fields, staleness cancellation
  with persisted/first-seen timestamps, sell pass limited to bought items, and the
  break pause guard.
- `fatJar` builds; the sidebar panel and live behavior are visually verified in 3d
  (not in 3c).
- Handoff `handoff-3c-config-persistence.md` written on completion.

## 6. Open items for the plan (not blockers)

- Exact per-script settings directory (inspect SDK/client for a storage helper before
  hardcoding the `%APPDATA%` path).
- Offer re-match key shape for placement timestamps (slot+item+side+price vs slot-only).
- Whether profit accounting (realizedProfit updates) lands in the engine or the script
  module — it must be TDD'd either way.
- Sidebar tab icon: `addSidebarTab` takes a `BufferedImage`; confirm whether `null` is
  accepted or a placeholder icon is required.
