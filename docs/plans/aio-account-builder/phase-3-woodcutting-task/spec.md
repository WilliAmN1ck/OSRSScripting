# AIO Account Builder — Phase 3 (Woodcutting on the Engine)
# Spec

**Date:** 2026-06-13 · **Branch:** `account-builder-phase3` (stacked on Phase 2) · **Status:** 🟡 In progress

Project docs: [../spec.md](../spec.md) · [../plan.md](../plan.md) · [../roadmap.md](../roadmap.md)
Builds on: [../phase-2-engine/handoff.md](../phase-2-engine/handoff.md)

---

## Goal
Refactor the standalone Phase 1 `ChopAndBankTask` into the **first real task on the engine** — a
`WoodcuttingTask : BuilderTask` driven by the `BuilderScheduler` — and bind the engine's `GameView`
seam to live SDK reads. Same live behaviour (chop → bank → repeat), now running *through* the engine,
plus a real completion goal (stop at a target Woodcutting level).

## Scope / decisions
- **`BuilderTask`** (`task/`) = `TaskSpec` + `execute()` (one unit of work). SDK-bound.
- **`WoodcuttingTask`** (`task/`):
  - `isComplete(view)` = `view.skills.level(WOODCUTTING) >= targetLevel` (target from the sidebar).
  - `validate` = default (requirements; F2P normal trees need none).
  - `execute()` = the proven chop → bank loop (moved verbatim from `ChopAndBankTask`): chop the
    nearest reachable selected/level-gated tree, bank all-but-axe at the nearest bank, return to the
    remembered chop spot.
  - `progress(view)` = "Woodcutting <lvl>/<target>".
- **`SdkGameView`** (`view/`) binds the seams: `SkillView` → `Skill.valueOf(name).getActualLevel()`;
  `InventoryView.isFull` → `Inventory.isFull()`, `contains` → inventory query; `QuestView` → stub
  (false) until quest tasks exist; `isMembersWorld` → `Worlds.getCurrent()?.isMembers`.
- **`MainBacklogTask`** (`runner/`) = a `core.task.Task` that, each tick, asks the scheduler for the
  next runnable `BuilderTask` and runs its `execute()`.
- **`AccountBuilderScript`** drives `TaskRunner([MainBacklogTask])` over the scheduler, with the
  existing antiban + break shadowing. Sidebar gains a **Target Woodcutting level** field.
- `ChopAndBankTask` is removed (its logic moves into `WoodcuttingTask`).

## Out of scope (later phases)
Persistence/profiles (Phase 4), paint/stats, multiple tasks, members trees, the quest API binding.

## Acceptance criteria
- Engine logic stays covered by the Phase 2 tests. `WoodcuttingTask` is SDK-coupled (it imports
  Bank/Inventory for `execute()`), so it is verified **live**, not unit-tested — the stop-at-target
  run confirms `isComplete`. (SDK types are compileOnly, so an SDK-referencing class can't load on the
  test classpath.)
- Full suite + `fatJar` green; `engine/` still has zero SDK imports.
- **Live (Echo):** unchanged chop → bank → repeat behaviour now running through the scheduler, and it
  **stops when Woodcutting reaches the configured target level**.

## Handoff
`handoff.md` in this folder once live-verified.
