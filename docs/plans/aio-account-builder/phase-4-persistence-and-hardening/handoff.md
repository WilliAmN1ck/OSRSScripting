# AIO Account Builder — Phase 4 (Persistence foundation + hardening)
# Handoff

**Date:** 2026-06-13 · **Branch:** `account-builder-phase4` ([PR #25](https://github.com/WilliAmN1ck/OSRSScripting/pull/25), stacked on #24) · **Status:** ✅ Code complete, 35 tests, three review passes; integrated script **live-verified** (see [../phase-3-woodcutting-task/handoff.md](../phase-3-woodcutting-task/handoff.md)).

Project docs: [../spec.md](../spec.md) · [../plan.md](../plan.md) · [../roadmap.md](../roadmap.md)

---

## What Was Built
- **Persistence foundation** (`engine/profile/`, pure): `BuildProfile` + `TaskConfig` + `SCHEMA_VERSION`,
  `ProfileCodec` (gson; normalizes missing/older fields), `ProfileStore` (file I/O; missing or corrupt
  file → default). gson added as `testImplementation`.
- **Review-driven hardening:**
  - Build-complete **stop** (logs + stops instead of idling at the trees forever).
  - **Watchdog** wired (no WC XP for 10 min → stop) **+ reset on break/logout** so a break can't
    false-stop the run.
  - `SdkGameView` explicit compile-checked **Skill → SDK** map (no runtime `valueOf` throw).
  - `MainBacklogTask` guard (warn, don't silently stall, on a non-`BuilderTask`); "nothing runnable"
    surfaced as a single periodic status line.
  - **Antiban parity** via pure `core.humanize`: fatigue-scaled cadence + look-away AFKs.
  - Robust `Axes.isAxe()` matcher (all tiers incl. felling; excludes pickaxe/battleaxe); logged-out guard.
- **Hands-off tree progression:** all trees pre-selectable (locked ones labelled "(unlocks at N)");
  chop the **best** qualified reachable tree; auto-upgrades as Woodcutting levels.

## What Changed From the Plan
- No formal upfront spec — this phase grew from the deferred persistence item plus three review passes
  (correctness, edge-case, thorough). Captured here instead.
- **Deferred (need the client or the sdk-support extraction):** persistence *wiring* (load-on-start /
  save-on-change / resume) + paint/stats; SDK fidgets (need the shared `SdkFidget` extraction);
  cross-location tree progression (walking to higher-tree areas as you level).

## Tests / Review
- **35 tests** green (engine, profile codec/store, panel auto-activation, axe matcher, watchdog incl.
  reset). `engine/` still has zero SDK imports.
- Three review passes; the key catch was the **watchdog false-stop after breaks** (fixed with reset).

## Live Verification
The integrated tip — engine + Woodcutting + persistence foundation + hardening + hands-off progression
— was live-verified in TRiBot Echo on 2026-06-13 (details in the Phase 3 handoff). Specifically
confirmed for this phase's work: the **antiban cadence/AFK** ran, the **break-reset watchdog** did not
false-stop over 10+ minutes, **build-complete stop** fired cleanly at the target, and the **hands-off
"(unlocks at N)" UI** rendered correctly.

## Known Issues / Queued for the next live run
- **no-axe pre-check — DONE** (post-#25 follow-up, see below).
- **withdraw axe from bank — DONE** (post-#25 follow-up, see below).
- **persistent chop-location — DONE** (post-#25 follow-up, see below).
- bank PIN handling; random-event / non-break-logout (login handler) behaviour.

## Follow-ups (post-#25)
- **Persistence wiring (#26):** load-on-start / save-on-change wired into `AccountBuilderScript` via
  `ProfileStore` in the script-settings dir; `AccountBuilderPanel.toProfile()`/`applyProfile()` map the
  tree selection + target. **Resume LIVE-VERIFIED 2026-06-13** — restart restores Oak/Willow/Yew + target 50.
- **No-axe runnability guard:** Woodcutting `validate()` now also requires `Axes.hasAxe(view)` — an axe in
  inventory **or** equipped — via a new read-seam (`GameView.equipment` + `InventoryView.itemNames()`,
  bound in `SdkGameView` to `Equipment.getAll()` / `Query.inventory()`). An axe-less account is now
  *not runnable* (scheduler skips it, single "check axe…" status line, clean watchdog stop) instead of
  spinning on an unchoppable tree or printing the misleading "start at the trees" bank message. The fix
  also tightened `Axes.isAxe()` to exclude throwing axes (`thrownaxe`). **42 tests** green.
- **Auto-fetch axe from bank:** the no-axe handling evolved from "stop" to "acquire": `validate()`'s static
  axe gate was dropped (so the task may run to fetch one) and `execute()` gained an `acquireAxe()` step —
  walk to the nearest bank → withdraw the best usable axe (`Axes.bestUsableAxe`: highest **non-degradable**
  tier ≤ WC level; Crystal/Infernal/3rd-age/Gilded deliberately skipped to avoid burning/risking a
  degradable or rare axe) → return. If started *at* the trees it remembers the spot and walks back; a cold
  start *at* a bank withdraws the axe but then logs "start at the trees" (no tree-finding — see persistent
  chop-location below). Full-inventory-with-no-axe deposits to free a slot before withdrawing (no silent
  deadlock). Bank has no usable axe → throttled warn + watchdog backstop. **50 tests** green.
- **Persistent chop-location:** the last chop tile is saved to the woodcutting `TaskConfig` params
  (`chopTile = "x,y,plane"`, additive — no schema bump) and restored on start to seed
  `WoodcuttingTask.chopSpot`, so a restart (or a cold start at a bank) walks back to the trees and resumes.
  Pure `TileCodec` (string ↔ x,y,plane) + `BuildProfile.withTaskParam` (both unit-tested); `WorldTile`
  bridging is SDK-coupled → live-verified. Saves are **throttled by distance** (`stabilizedChopTile`:
  re-persist only on a >8-tile move or plane change) so config still saves instantly without disk spam.
  Review-driven hardening folded in: the `"woodcutting"` task key is now a single shared `WOODCUTTING_KEY`
  constant (was a 3-way literal), and the save now preserves the loaded `shuffleSeed` instead of dropping it.
  **61 tests** green. Limitation: a brand-new account that has never chopped still can't locate trees from a
  cold bank start (no tree-finding exists).

## Verification Commands
    .\gradlew.bat :scripts:account-builder:test
    .\gradlew.bat :scripts:account-builder:fatJar :scripts:account-builder:deployLocally
