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
- no-axe pre-check; bank PIN handling; random-event / non-break-logout (login handler) behaviour.

## Verification Commands
    .\gradlew.bat :scripts:account-builder:test
    .\gradlew.bat :scripts:account-builder:fatJar :scripts:account-builder:deployLocally
