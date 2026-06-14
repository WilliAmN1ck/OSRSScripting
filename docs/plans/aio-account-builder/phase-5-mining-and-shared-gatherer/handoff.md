# Phase 5 — Mining via a shared Gatherer — Handoff

**Date:** 2026-06-14 · **Status:** ✅ Code complete, 82 tests, `/code-review max` clean · **live-verify of
Mining PENDING** (do first next session). Spec: [spec.md](./spec.md) · Plan: [plan.md](./plan.md).

**PRs:** **A merged** — [#31](https://github.com/WilliAmN1ck/OSRSScripting/pull/31) (shared gatherer).
**B+C** — [#32](https://github.com/WilliAmN1ck/OSRSScripting/pull/32), branch `account-builder-mining-data`.

## What Was Built

- **Shared gatherer (A):** extracted `WoodcuttingTask`'s logic into a generic `GatheringTask`
  (gather→bank→return + tool-from-bank + spot-persistence), parameterized by `key` / `skill` /
  `gatherAction` / `tool` / `enabled` / `allowedResources` / `targetLevel`. Pure seams: `GatherResource`
  (`engine/`) and `ToolModel` (`task/`). `Axes` → `ToolModel`; `SdkSkills` extracted (engine→SDK map).
  Woodcutting is now a thin `woodcuttingTask(...)` factory — behaviour-preserving, re-verified live.
- **Mining (B):** `RockType` (F2P ladder Copper→Adamantite) + `Picks` (`ToolModel`; pickaxe Mining reqs;
  degradable/rare picks skipped) + `miningTask(...)` factory.
- **Wiring (C):** generalized panel → `GatherConfigPanel` (one tab per skill); two sidebar tabs
  (Woodcutting, Mining); scheduler `[woodcutting, mining]`; `composeProfile` generalized to N skills
  (per-skill selection + target + gather-anchor; backward-compatible param keys `trees`/`rocks`);
  watchdog signal → total XP across configured skills.
- **Per-skill "Train this skill" toggle** (user request): each tab enables/disables its skill; only
  enabled skills are scheduled. Defaults: Woodcutting on, Mining off (opt-in). Persisted (`enabled` param).
- **`allComplete` → `allDone`** (review fix): `backlog.all { isComplete || !validate }` — a run stops
  cleanly once configured skills hit target, even if another (disabled/unconfigured) skill never completes.
- **Rock-id diagnostic:** logs nearby `Mine`-able objects' `name#id` in view when none selected match.

## What Changed From the Spec — IMPORTANT

The spec assumed mineable rocks are all named `"Rocks"` → ore identified by **object ID** (a curated,
per-mine, brittle table). **In-game the diagnostic disproved this:** rocks are named **per ore** —
`Copper rocks`, `Tin rocks`, `Iron rocks`, `Coal rocks`, `Silver rocks`, `Gold rocks`, `Mithril rocks`,
`Adamantite rocks`. So `RockType` matches **by name, exactly like `TreeType`** (`objectNames`), with **no
id table** — robust at every mine. (This also dropped sub-phase D's "capture rock ids" goal; the only
remaining D step is a live confirmation that Mining mines.)

## Next Session (first thing)

Restart the AIO at the Al-Kharid mine (jar deployed; profile pre-set Mining-on / Woodcutting-off) and
confirm: finds `Copper/Tin rocks` by name → mines → banks when full → returns; auto-progresses by level.
Then merge #32 (or it may already be merged — if Mining needs a fix, a follow-up PR). See
[[tribot-echo-live-testing]] for the computer-use caveats (let the user drive the game).

## Tests

82 green (`:scripts:account-builder:test`): `RockTypeTest` (name-matching), `PicksTest`,
`GatherConfigPanelTest` (incl. per-skill toggle round-trip), `BuilderSchedulerTest` (`allDone`), plus the
existing engine/profile/tree/axe suites. The SDK-coupled `GatheringTask.execute()` is live-verified.

## Known Issues / Deferred

- Mining live-verify pending (above).
- A brand-new account that has never gathered can't auto-navigate to a resource from a cold bank start
  (no resource-area navigation; you start at the resource). Persistent anchor covers the after-first-gather case.
- Deferred: `sdk-support` extraction, cross-location progression, bank PIN / random-event / login-recovery, paint/stats.

## Verification Commands

    .\gradlew.bat :scripts:account-builder:test
    .\gradlew.bat :scripts:account-builder:fatJar :scripts:account-builder:deployLocally
