# Phase 3 — GE Flipper · Step 3d (live Echo verification)
# Handoff

**Date:** 2026-06-12
**Branch:** `phase-3d-live-verification`
**Plan reference:** [`plan-3d-live-verification.md`](./plan-3d-live-verification.md);
spec: [`spec-3d-live-verification.md`](./spec-3d-live-verification.md);
checklist: [`checklist-3d.md`](./checklist-3d.md)
**Status:** ✅ **All five gate items passed live** (load, sidebar, persistence, full flip
cycle, stop-save). The flipper ran autonomously for hours on a real F2P account:
**18 flips, ~2,400 gp all-time profit** on a 116k stack. Seven defects found live, all
fixed at root with regression tests. Items 2 (CLI) and 7 (breaks) deferred — see below.

---

## Checklist outcomes

| Item | Result | Evidence / notes |
|---|---|---|
| 1. Deploy + load | ✅ PASS | `deployLocally` → `%APPDATA%\.tribot\automations`; "GE Flipper (Local)" in the Script Selector. First attempt failed because deploy had never been run — the two scripts already listed (`.tribot\bin\scripts\*.class`) are legacy locals, a different loading path. |
| 2. CLI resolution | ⏸ DEFERRED | Not exercised this session; GUI launch worked throughout. Still the open question for any future unattended automation. |
| 3. Sidebar | ✅ PASS | Tab renders; stats tick ~2 s; `abc` in capital cap → red "Capital cap (gp) is not a number", config untouched, script kept running; valid re-apply cleared the error and took effect next tick. |
| 4. Persistence | ✅ PASS | **Resolved path:** `%APPDATA%\.tribot\script-config\GE Flipper\ge-flipper-state.json` (`ScriptSettings.getDefault()` → `script-config\<scriptName>\`). Profit/ledgers/stamps survived 5+ restarts; a live offer aged across two restarts and cancelled on its *original* 30-min schedule. |
| 5. Flip cycle | ✅ PASS | Buys placed/filled (2 raw pike, 57 diamond amulet), collected, ledger-tracked; sells placed and filled; profit and flip counters incremented (pikes: +12 gp net of tax and FIFO cost basis, exactly as computed by hand). |
| 6. Staleness | ✅ PASS | Max offer age dropped to 2 min via sidebar → both stale buys aborted ("Sending Abort request…" in chat), collected (cash restored), re-ranked, and relisted at fresh prices within seconds. |
| 7. Breaks | ⏸ DEFERRED | No break profile was defined in the client; `BreakIdleTask` shadowing is unit-tested but unobserved live. Incidentally verified: the client **auto-relogged** after a mid-session disconnect and the script carried on. |
| 8. Stop signal | ✅ PASS | State-file mtime updated at the exact second of the Stop click ("stop() called" in the log); sidebar tab removed. TRiBot stop ⇒ thread interrupt ⇒ our `finally` save — the assumption carried since 3b is now confirmed. |
| 9. Soak | ✅ PASS (informal) | ~2.5 h unattended: 18 flips across many F2P items (pies, amulets, logs, clay, gold bars), one disconnect+relogin survived, no stuck states after the fixes below, memory/CPU unremarkable. |

## Defects found live — all fixed + regression-tested

1. **Noted-item blindness** (`SdkGeClient`): GE collections deliver stackables in noted
   form; the stock ledger (canonical ids) could never match them, so bought stock was
   unsellable. `stock()` now canonicalizes via `ItemDefinition.isNoted()/getUnnotedItemId()`,
   and `placeSell` resolves the id as it sits in the inventory.
2. **Config reset on restart** (`PersistedConfig` in `PersistedState` + `StateMapper`):
   sidebar settings now persist and load at startup — verified live (a restart came up
   configured with zero input). First run defaults to **idle** (`capitalCap = 0`).
3. **Members-item wedge on F2P**: with config reset (defect 2), a fresh start hunted a
   members item; the GE search found nothing and the abandoned offer-setup screen wedged
   every later placement. Fixed by 3d.1's F2P filter + defect 2 + defect 5.
4. **Jar clobbering shipped a broken build**: the plain `jar` task shared `fatJar`'s
   output path; running after it, it replaced the 2.3 MB artifact with the 25 KB thin jar
   (instant `NoClassDefFoundError` in Echo). The thin jar now carries a `thin` classifier —
   the deploy artifact can no longer be clobbered.
5. **Interface flapping** (user-reported): `EnsureGeOpenTask` reopened the GE every tick
   while close-on-failed-placement closed it — a 2 s open/close loop whenever a placement
   kept failing. Redesigned: **the GE is touched only with cause** — offers/inventory read
   fine with it closed, idle ticks leave it alone, a tick with actions opens it (acting
   next tick), and failures back off 10 s. `EnsureGeOpenTask` deleted.
6. **Failed abort/collect retried every tick** (review finding): all action failures now
   feed the same 10 s backoff (only placement failures additionally close the GE).
7. **Failed `open()` retried every tick** (review finding): e.g. player not at the GE
   booth; now backs off instead of spamming.

## What Changed From the Plan

- 3d.3 was driven by **Claude via computer use** (user approval mid-session) rather than
  user-driven with checklist reporting; the user supervised and reported symptoms
  (members-item hunting, interface flapping). Hybrid worked well: screenshots + direct
  state-file/log reads from the same machine.
- Scope grew by the seven live defects (the plan anticipated "fix findings at root").
  The GE-interaction redesign (defect 5) is the biggest unplanned change: `FlipTask` now
  owns open-on-demand and `EnsureGeOpenTask` is gone.

## Files Changed (beyond 3d.1's F2P filter)

| File | Change | Notes |
|---|---|---|
| `scripts/ge-flipper/.../SdkGeClient.java` | modified | noted-id canonicalization both directions; `close()` |
| `libraries/core/.../persistence/PersistedConfig.java` | new | persisted run config |
| `libraries/core/.../persistence/PersistedState.java` | modified | nullable `config` field (v2-compatible) |
| `libraries/core/.../persistence/StateMapper.java` | modified | config snapshot/restore |
| `scripts/ge-flipper/.../FlipTask.java` | modified | open-on-demand, retry backoff, config in snapshot |
| `scripts/ge-flipper/.../FlipActionExecutor.java` | modified | returns success; close on failed placements only |
| `scripts/ge-flipper/.../EnsureGeOpenTask.java` | deleted | superseded by open-on-demand |
| `scripts/ge-flipper/.../GeFlipperScript.java` | modified | restored config, idle default, backoff constant |
| `scripts/ge-flipper/build.gradle.kts` | modified | thin-jar classifier |

## Test Coverage

All suites green. New/updated: `FlipScannerTest` (members filter ×2),
`FlipperPanelTest` (members checkbox), `StateStoreTest` (config round-trip, v2-compat),
`StateMapperTest` (config restore ×2), `FlipTaskTest` (open-on-demand ×2, placement
backoff, open backoff), `FlipActionExecutorTest` (failure reporting ×2).

## Known Issues / Tech Debt

- **Sells don't preempt buys for slots**: 115k of bought stock waited ~25 min for a sell
  slot while two sub-100 gp buys occupied the GE. Staleness self-corrects, but slot
  priority (sell-first eviction) is the highest-value engine improvement on the list.
- **Members stock bought before disabling the filter** would still be offered for sale on
  F2P (the sell pass doesn't check membership) — the placement fails and backs off, so
  it's churn-bounded, but the capital stays stuck. Edge case; note for the engine.
- **The deployed build is one commit behind** (the abort/collect/open backoff fix landed
  after the last deploy); run `deployLocally` and restart at the next opportunity.
- Profit under-counts better-price fills (carried from 3c); sell-exit escalation still
  deferred (3c decision 9 — the soak showed aggressive `maxOfferAge` values churn profit
  away, which is exactly the data that decision wanted).
- Items 2 (CLI) and 7 (breaks) remain to be exercised; both are config/launch-side, no
  code expected.

## Verification Commands

    .\gradlew.bat :libraries:core:test :scripts:ge-flipper:test
    .\gradlew.bat build :scripts:ge-flipper:fatJar       # single invocation safe now
    .\gradlew.bat :scripts:ge-flipper:deployLocally      # -> %APPDATA%\.tribot\automations
