# AIO Account Builder — Phase 2 (Pure Engine)
# Handoff

**Date:** 2026-06-13 · **Branch:** `account-builder-phase2` · **PR:** [#23](https://github.com/WilliAmN1ck/OSRSScripting/pull/23) · **Status:** ✅ Code complete, unit-tested, merged.

Project docs: [../spec.md](../spec.md) · [../plan.md](../plan.md) · [../roadmap.md](../roadmap.md)

---

## What Was Built
The pure, SDK-free decision engine the scheduler ranks — built and fully unit-tested before being
wired to the SDK. Package `com.osrsscripts.accountbuilder.engine` (zero SDK imports):

- **`Skill`** — the 23 OSRS skills (pure enum).
- **`GameView`** — the single read-side seam (`SkillView` / `InventoryView` / `QuestView` +
  `isMembersWorld`). Production binds it to SDK reads; tests use fakes. The engine never *acts*.
- **`Requirements`** — skill levels / items / quests / members + `meets(view)`.
- **`TaskSpec`** — `key` / `requirements` / `isComplete` / `validate` (defaults to requirements) /
  `progress`.
- **`BuilderScheduler`** — first task that is `!isComplete && validate`; skips completed; passes over
  not-yet-eligible tasks; optional **seeded deterministic shuffle**.
- **`Watchdog`** — thin stall detector (`CONTINUE`/`STOP` on a no-progress window).

## Test Coverage
19 tests green (Requirements 6, BuilderScheduler 7, Watchdog 3, + existing TreeType 3). All pure JVM
— no Echo. Fakes: `engine/FakeGameView`.

## What Changed From the Plan
- **Profile/gson `ProfileCodec` deferred** to the persistence phase (Phase 4) — no point building the
  codec until there is a profile to persist.

## Known Issues / Tech Debt
- None. Engine is pure and complete for current needs; quests/items gates are defined but only
  exercised once quest/supply tasks exist.

## What the Next Phase Needs to Know
- Phase 3 adds `BuilderTask : TaskSpec { execute() }` and an `SdkGameView` binding the seams to SDK
  reads, then drives the `BuilderScheduler` from `AccountBuilderScript`.

## Verification
    .\gradlew.bat :scripts:account-builder:test
