# Post-Phase-3 — Slot & capital utilization
# Handoff

**Date:** 2026-06-13
**Branch:** `flipper-slot-utilization`
**PR:** [#9](https://github.com/WilliAmN1ck/OSRSScripting/pull/9) (merged, `535a355`)
**Spec:** [`spec.md`](./spec.md) — Batches 3 and 4
**Status:** ✅ Complete and merged. Live-verified on Echo end-to-end: the advisory renders with
the correct reason, and the capital-aware ranking deployed **~1.93M gp into a single slot**
(Wooden shield (g) @ 92k × 21) on a real F2P account.

---

## Background — the problem

The flipper left GE slots empty and cash idle. Two root causes were separated:

1. **Wasted slots / cash from config**, with no signal to the user about which setting was the
   limiter.
2. **The ranking never considered capital deployed** — it ranked by profit-per-cycle
   (`netMargin × min(buyLimit, volume)`). Expensive items have small buy limits, so they always
   ranked below cheap, high-limit commodities, parking a large bankroll behind tiny flips.

A third idea — opening multiple concurrent offers on the same item to fill slots — was
investigated and **rejected**: a single offer already maxes the per-item cap and the 4h buy limit
(both totals), so reuse deploys no extra gp; it only splits one trade into dust offers. (See
[lessons.md](../../lessons.md).)

## What Was Built

### Batch 3 — idle-reason diagnostics (`ccd55c0`, `81db603`)

- **`FlipEngine.plan()` → `FlipPlan(actions, IdleReason)`**; `decide()` now delegates to it, so
  action behaviour is unchanged. Buying stays **one offer per item**.
- **`IdleReason` {NONE, MAX_SLOTS, CAPITAL_CAP, PER_ITEM_CAP, NO_CANDIDATES}** computed from the
  tick's free slots, remaining capacity, budget, and candidate presence. Only config-driven causes
  report; being out of gold or capped by a 4h buy limit is the system working as intended → NONE.
- **`FlipTask` exposes `idleReason()`** → fed through `GeFlipperScript.refreshStats` into
  `StatsSnapshot` → `FlipperPanel` renders an **amber, HTML-wrapped advisory** naming the setting
  and the direction to move it.
- **Sidebar readability:** config fields stacked **label-above-input** so the narrow RuneLite
  column stops clipping the text boxes to unreadable slivers.

### Batch 4 — capital-aware ranking + UI clarity (`00355e5`, `e1413ee`, `861f7da`)

- **`FlipScanner` ranks by capital deployed per offer**:
  `buyPrice × min(buyLimit, volume, perItemCap/buyPrice)`, capped at the per-item cap so items
  that can fill a slot tie at the top, broken by **profit-per-cycle**. A big bankroll now buys
  expensive items that actually use the cap.
- **Config relabels** for clarity: "Per-item capital cap" → **"Max spend per item (gp)"**;
  "Min margin (fraction)" → **"Min ROI (%)"** (now entered as a real percent — type `2` for 2% —
  while still stored as a fraction); "Min volume (units/h)" → **"Min hourly volume (units)"**;
  "Min buy deployment (gp)" → **"Min spend per buy (gp)"**.

## What Changed From the Spec

- The original Batch-3 idea (same-item reuse to fill slots) was dropped after analysis — the
  diagnostic is the real deliverable. Decision captured in the spec and lessons.
- Capital-aware ranking (Batch 4) was added once live testing showed the ranking, not the cap,
  was burying expensive items. It was confirmed via the OSRS wiki that the limiter for the
  100k–900k band on F2P is **liquidity (hourly volume)**, not item membership.

## Files Changed (key)

| File | Change | Notes |
|---|---|---|
| `libraries/core/.../ge/FlipEngine.java` | modified | `plan()` + `IdleReason` computation; `decide()` delegates |
| `libraries/core/.../ge/FlipPlan.java` | new | actions + idle reason |
| `libraries/core/.../ge/IdleReason.java` | new | the five reasons |
| `libraries/core/.../ge/FlipScanner.java` | modified | capital-deployed ranking |
| `scripts/ge-flipper/.../FlipTask.java` | modified | exposes `idleReason()` |
| `scripts/ge-flipper/.../GeFlipperScript.java` | modified | feeds reason into the snapshot |
| `scripts/ge-flipper/.../StatsSnapshot.java` | modified | carries `IdleReason` |
| `scripts/ge-flipper/.../FlipperPanel.java` | modified | amber advisory, label-stacking, relabels, percent input |

## Test Coverage

All suites green + `fatJar`. New/updated: `FlipEngineTest` (5 `plan()` reason cases),
`FlipScannerTest` (capital ranking, capital-vs-profit discriminator, per-item-cap tie-break),
`FlipperPanelTest` (advisory render, Min-ROI percent round-trip). `/code-review max` over the full
PR: no correctness bugs.

## Known Issues / Tech Debt

- **Ranking deprioritizes illiquid items.** `deployableUnits` caps by hourly volume, so a
  high-value item that barely trades ranks below what the engine could technically deploy into it.
  Intentional (conservative), but it means the very illiquid armour *sets* (0 volume) never get
  picked even when liquid pieces do.
- **`trimNumber` rounds the displayed ROI %** to 4 decimal places — not reachable with realistic
  settings.
- Deploying more than `maxSlots × perItemCap` of cash needs both dials raised; the advisory now
  surfaces which one binds.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally   # -> %APPDATA%\.tribot\automations
