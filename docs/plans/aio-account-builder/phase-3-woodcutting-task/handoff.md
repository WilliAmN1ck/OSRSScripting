# AIO Account Builder — Phase 3 (Woodcutting on the Engine)
# Handoff

**Date:** 2026-06-13 · **Branch:** `account-builder-phase3` ([PR #24](https://github.com/WilliAmN1ck/OSRSScripting/pull/24), stacked) · **Status:** ✅ Code complete, unit-tested, **live-verified in TRiBot Echo**.

Spec: [spec.md](./spec.md) · Builds on [../phase-2-engine/handoff.md](../phase-2-engine/handoff.md)

---

## What Was Built
Refactored the Phase 1 standalone chop/bank loop into the first real task on the Phase 2 engine:
- **`BuilderTask`** = `TaskSpec` + `execute()`.
- **`WoodcuttingTask`** — `isComplete` = WC level ≥ target; `execute()` chops the best qualified reachable
  selected tree and banks all-but-axe; `progress()`.
- **`SdkGameView`** — binds the engine's `GameView` seam to live SDK reads (skills via an explicit
  `Skill → SDK Skill` map, inventory, members world).
- **`MainBacklogTask`** — drives the `BuilderScheduler` from the shared `TaskRunner`.
- Sidebar **Target Woodcutting level** field; `ChopAndBankTask` removed.

## Live Verification
**2026-06-13 · TRiBot Echo · test account williamnick420 (character "Y8Tgy4wij") · Lumbridge trees.**
User started the script + selected it; observation and config driven via remote control. Verified
end-to-end:

- **Loads & runs** — Echo panel showed "Running: AIO Account Builder".
- **Chops** the selected normal trees (engine → `BuilderScheduler` → `WoodcuttingTask` → chop);
  Woodcutting **leveled 7 → 11** during the run ("Chop down Tree", "You get some logs").
- **Banking + return (Lumbridge castle, multi-floor)** — the inventory filled to 28 logs (axe
  wielded), yet it **kept leveling**, which is only possible by walking to the castle bank, depositing,
  and returning each cycle. The `walkToBank()` multi-floor fix holds.
- **Stop-at-target** — Target set to 11; at WC 11 it stopped cleanly with the log
  **`AIO Account Builder: all tasks complete — stopping.`**
- **Config panel** rendered with the hands-off labels (Normal checked; Oak/Willow/Yew… shown as
  "(unlocks at N)" and pre-selectable).
- **Stable 10+ minutes** — no crash, **no watchdog false-stop** (the break/logout reset holding),
  antiban cadence + look-away AFK active.

## Files Changed (key)
| File | Change |
|---|---|
| `task/BuilderTask.kt`, `task/WoodcuttingTask.kt` | new — task contract + Woodcutting on the engine |
| `view/SdkGameView.kt` | new — binds `GameView` to SDK reads |
| `runner/MainBacklogTask.kt` | new — drives the scheduler from `TaskRunner` |
| `AccountBuilderPanel.kt` | Target level field; (Phase 4) hands-off labels |
| `AccountBuilderScript.kt` | wire scheduler/view/runner (extended in Phase 4) |

## Known Issues / Tech Debt
- Queued for the next live run (depend on Echo/account behaviour): no-axe pre-check, bank PIN
  handling, random-event / non-break-logout behaviour. See [../phase-4-persistence-and-hardening/handoff.md](../phase-4-persistence-and-hardening/handoff.md).

## Verification Commands
    .\gradlew.bat :scripts:account-builder:test
    .\gradlew.bat :scripts:account-builder:fatJar :scripts:account-builder:deployLocally
