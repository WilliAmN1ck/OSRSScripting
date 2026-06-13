# AIO Account Builder
# Spec

**Date:** 2026-06-13 · **Status:** 🟡 Draft — pending confirmation · **Branch:** _TBD_

Reference (north star): the TRiBot AIO Account Builder product
<https://tribot.org/shop/products/6-aio-account-builder>. Scripting-strategy background in the
[tribot-scripting-guides memory] and [docs/reference/tribot-sdk.md](../../reference/tribot-sdk.md).

---

## Vision (north star — NOT this phase)

An all-in-one account builder: all skills 1–99, ~174 tasks across 247 quest points, minigames,
money-makers, timed farm/birdhouse runs, GE buying, gear profiles, CLI, Discord webhooks. This is the
largest script category in OSRS. **~90% of the value is in the engine, not any single task** — so we
build the engine + a thin vertical slice first and add tasks incrementally over many small PRs. The
feature list is the destination, not the first deliverable.

**The full feature set is tracked in [roadmap.md](./roadmap.md)** — the master backlog. Nothing is
"done" until checked off there; every AIO PR updates it. This guarantees the north star is never lost
to incremental work.

## Scope of THIS phase

The **task engine** plus **one Woodcutting task end-to-end**, fully wired (antiban, breaks,
persistence, minimal config), as a "walking skeleton" that proves the framework. When it runs and is
live-verified, adding skills/quests becomes additive.

## Confirmed decisions (from Q&A 2026-06-13)

| Decision | Choice |
|---|---|
| Approach | Engine-first vertical slice; 174-task list is a north star |
| First slice | **Woodcutting**, F2P, chop → bank → repeat, stop at a target level |
| Goal model | **Manual ordered task list + shuffle** (no auto-planner; design must not preclude one later) |
| Account scope | **F2P first**; members later |
| Language | **Kotlin** for the new script (+ any SDK-bound shared layer); interops with the Java `core` |
| Task backbone | **Prioritized tasks** (tut-island ≈ our `core.task.Task`); behavior trees optional per-task |

## Architecture

### Modules
```
libraries/core          pure Java logic — reused as-is (task.Task, humanize math, persistence)
libraries/sdk-support   SDK-bound shared layer (see "Module sequencing" below)
scripts/account-builder  Kotlin · TribotScript · bundles core (+ sdk-support)
```

### The task engine
A scheduler over a user-configured, optionally shuffled, ordered list of tasks. Each task:

```
TaskSpec (PURE — engine/, unit-tested):
  isComplete(view): Boolean   // goal reached (e.g. Woodcutting level >= target) -> scheduler skips it
  validate(view): Boolean     // can it run now? (level / items / location / membership prerequisites)
BuilderTask : TaskSpec (SDK — task/, verified live):
  execute()   // one step of work via SDK statics (Query/Bank/Waiting/…); plain Kotlin
```

The scheduler ranks pure `TaskSpec`s (so it's SDK-free and testable); the runner calls the chosen
task's `execute()`. `view` is the read-side `GameView` (skills/inventory/location/quests) — the only
seam the engine's decisions need; `execute()` calls the SDK statics directly. Per tick the scheduler
drops completed tasks and runs the first that validates. Break/antiban tasks shadow everything (reuse the ge-flipper pattern). State persists
via the `StateStore` pattern so a restart resumes mid-list. The contract is deliberately friendly to
a future auto-planner sitting on top — but none is built now.

### Task execution
Each task's `execute()` is plain Kotlin over the SDK statics — the proven tut-island pattern: small,
`canRun()`/`isComplete`-gated steps. World interaction uses the SDK **Query** system
(`Query.gameObjects()…findBestInteractable()`), waits use `Waiting.waitUntil(cond, timeout)` — never
fixed sleeps. Higher-priority tasks (breaks, farm runs) preempt any tick because steps re-evaluate
from observable state. Behavior trees (`frameworks.behaviortree`) stay available as an optional tool
inside a genuinely complex task. We do **not** adopt the community `Vars` global-singleton pattern.

### Woodcutting slice (the proving ground)
- Chop normal trees in an F2P bank-adjacent area → walk to bank → deposit logs → repeat.
- **Bank, not power-drop** — banking exercises walking + banking + inventory, the surface we want
  proven.
- Completion: Woodcutting level ≥ a configurable target.
- Minimal config panel (Swing, like the flipper): target level, tree type, area.

### Cross-cutting
- **Antiban / breaks:** reuse the existing humanize layer (fidgets, fatigue, break-aware idling).
- **Persistence:** `StateStore`/`StateMapper` pattern — resume the task list and progress.
- **GUI:** minimal Swing sidebar for the slice; full profile save/load is north-star.

## Module sequencing (open recommendation)

The Script SDK (BT framework, Query, banking, walking) is **not public** — only the `org.tribot.dev`
plugin provides it (`compileOnly`). So any module using those frameworks needs the plugin applied.
Two paths for the shared `sdk-support` module:

- **A — Build the engine/helpers inside `scripts/account-builder` first, extract to `sdk-support`
  once the slice is green.** (Recommended.) Keeps the plugin-on-a-library unknown off the critical
  path; follows "prove, then extract"; still delivers the shared module as a fast-follow.
- **B — Stand up `sdk-support` now with the dev plugin applied (no scripts registered).** Requires a
  spike to confirm the plugin tolerates a script-less module. Higher upfront risk.

Recommendation: **A.** Structure engine/helpers in their own packages so extraction is mechanical.

## Out of scope (deferred / north-star)

Other skills, quests, minigames, money-makers, farm/birdhouse run-scheduling, auto-planner, gear
profiles, CLI presets, Discord webhooks, members content, Ironman. Each becomes its own later PR.

## Open technical questions (resolve in planning / spike)

1. Exact SDK API shapes: `behaviorTree`/node return types from Kotlin, `Query.gameObjects` filters,
   `Banking` deposit calls, F2P walking via `context.navigation`/DentistWalker. Verify against the
   [Kdocs](https://runeautomation.com/docs/sdk/kdocs/).
2. Kotlin source set + script registration in a module that currently only compiles Java.
3. Plugin-on-a-library feasibility (only if we pick path B).
4. Bank-adjacent F2P woodcutting spot (e.g. Draynor willows are members-adjacent; pick an F2P-clean
   tree+bank pair — confirm location).

## Acceptance criteria

- Engine runs an ordered task list, skips completed tasks, ticks the running task's behavior tree.
- The Woodcutting task chops → banks → repeats and **stops at the configured target level**, verified
  live in TRiBot Echo.
- Antiban/breaks active; state persists and resumes across a restart.
- Full test suite green for all pure/Java-testable logic (engine scheduling, completion checks);
  SDK-coupled behavior verified live. `fatJar` + `deployLocally` produce a loadable jar.

## Sources

- [Behavior Trees in TRiBot (Nullable)](https://community.tribot.org/index.php?/topic/12-using-behavior-trees-in-your-scripts-kotlin/)
- [The Query System (Naton)](https://community.tribot.org/index.php?/topic/15-the-query-system/)
- [TRiBot SDK Kdocs](https://runeautomation.com/docs/sdk/kdocs/) · [Javadocs](https://runeautomation.com/docs/sdk/javadocs/org/tribot/script/sdk/package-summary.html)
