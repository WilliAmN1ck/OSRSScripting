# AIO Account Builder — Implementation Plan

**Date:** 2026-06-13 · **Status:** 🟡 Draft — pending confirmation · **Spec:** [spec.md](./spec.md) · **Backlog:** [roadmap.md](./roadmap.md)

Scope: the **engine + Woodcutting vertical slice**, engineered so adding the remaining
[roadmap](./roadmap.md) tasks is additive. Sequenced to **prove the live integration before building
the engine on top of it** (walking-skeleton: skeleton walks end-to-end first, then grows).

---

## A. Guiding architecture (the invariants every future task inherits)

1. **Three-layer execution, reusing existing infra.**
   - **Composition root** (`AccountBuilderScript : TribotScript`) wires SDK → engine, runs the loop.
   - **`core.task.TaskRunner`** (existing, reused) = coarse **priority/preemption tiers**:
     `BreakIdleTask` (shadow on breaks) → interrupt tasks (farm/birdhouse/restock — *slots reserved, deferred*) → `MainBacklogTask`.
   - **`BuilderScheduler`** (new) walks the ordered backlog within the main tier.
   - **Each `BuilderTask` is a small `execute()` step** (a behavior tree only *inside* a complex task, if ever).

2. **The pure/SDK split.** The scheduler operates on a *pure* contract; the SDK-bound step lives where the SDK is:
   ```kotlin
   // engine/ — PURE, zero SDK imports, fully unit-tested with fakes
   interface TaskSpec {
       val key: TaskKey
       val requirements: Requirements
       fun isComplete(view: GameView): Boolean   // read-side views only
       fun validate(view: GameView): Boolean
       fun progress(view: GameView): TaskProgress
   }
   // task/ — SDK-bound, behavior verified live (not unit-tested)
   interface BuilderTask : TaskSpec {
       fun execute()   // one step of work, using SDK statics (Query/Bank/Waiting/…) directly
   }
   ```
   `BuilderScheduler` ranks `List<TaskSpec>` (first `!isComplete && validate`); the runner calls the
   chosen task's `execute()`. Within-task logic is plain Kotlin (if/else + `Waiting.waitUntil`) — the
   proven tut-island pattern. `engine/` stays SDK-free; SDK types live only in `task/`. (A behavior
   tree is an optional detail *inside* a complex task's `execute()`, not the backbone.)

3. **Seam the reads, not the actions.** Principle: **test decisions, verify actions live.** So we wrap
   only the *read-side state the engine's decisions need* — `SkillView`, `InventoryView`,
   `LocationView`, `QuestView` (bundled as `GameView`), pure interfaces faked in tests. Behavior-tree
   **action** leaves (walk, chop, deposit) call the SDK **directly** — they're live-verified anyway, so
   wrapping them adds indirection without test value. No speculative action abstractions.

4. **Re-entrant, condition-gated tasks (enforced rule).** Every task's `canRun()`/`execute()` must key
   off **observable game state** ("am I at the bank?", varbit/level/inventory), never "I just did X so
   Y follows." This makes preemption free (a higher-priority task — break, farm run — can take over any
   tick; the interrupted task simply re-evaluates next time) and is the #1 guard against subtle bugs
   (double-actions, skipped steps). Code-review checklist item, not an assumption.

5. **Structured `Requirements`** (levels, items, quests, members, location) on every task — drives
   `validate()`, surfaces in the UI, and feeds a future auto-planner. Designed in from task #1.

6. **Stable `TaskKey` + registry.** Config references and persistence key off it, so profiles/progress
   survive task-set growth and reordering.

7. **Reusable action helpers** (`actions/`): `walkTo`, `bankInventory`, `withdrawSet`, `handleLevelUp`,
   `interactNearest` — plain functions over the SDK statics. The 174 tasks compose these.

8. **Serialization: gson** (Echo-provided; jackson is not, per [tribot-sdk.md](../../reference/tribot-sdk.md)).
   Eyes open on Kotlin friction (defaults/nullability). Versioned schema + migration.

9. **Resilience:** a *thin* `Watchdog` seam now (no-progress timer → `Continue|SkipTask|Stop`); its
   **policy stays trivial until the Phase 5 soak** shows how real tasks actually stall — designing
   elaborate stall rules pre-soak is guesswork.

10. **TDD** on every pure layer. SDK-coupled trees verified live and documented.

### Package layout (`scripts/account-builder`, Kotlin)
```
com.osrsscripts.accountbuilder
├── AccountBuilderScript            // composition root (SDK)
├── runner/ MainBacklogTask         // core.task.Task; drives scheduler, ticks current task's tree()
├── engine/                         // PURE — no SDK. Extracted to sdk-support in Phase 5.
│   ├── TaskSpec, TaskKey, Requirements, TaskProgress, TaskStatus
│   ├── view/ GameView, SkillView, InventoryView, LocationView, QuestView   // pure read-side interfaces
│   ├── BuilderScheduler            // operates on List<TaskSpec>
│   ├── Watchdog                    // thin stall seam
│   └── profile/ BuildProfile, TaskConfig, ProfileCodec(gson), SCHEMA_VERSION
├── view/sdk/ SdkSkillView, SdkInventoryView, SdkLocationView, SdkQuestView // SDK impls of the views
├── task/ BuilderTask               // = TaskSpec + execute()  (SDK)
├── actions/                        // reusable action helpers over SDK statics (walk/bank/interact)
├── tasks/woodcutting/WoodcuttingTask
└── ui/ ConfigPanel, StatsSnapshot, Paint
```

---

## B. Sub-phases  *(reordered: live integration proven before the engine is built on it)*

### Phase 0 — Spike & scaffold
- **0.1 SDK API spike.** Confirm real signatures vs [Kdocs](https://runeautomation.com/docs/sdk/kdocs/):
  `behaviorTree { }` + node types from Kotlin; `Query.gameObjects()` filters + `findBestInteractable`;
  `Bank` open/depositAll/withdraw; walking (`Navigation`/DentistWalker); `Skills` level+XP reads;
  level-up dialog; `GameTab`. Record in `sdk-notes.md`. *No later phase guesses a signature.*
- **0.2 Module scaffold.** `build.gradle.kts` (kotlin 2.1.21 + `org.tribot.dev`, register
  `AccountBuilderScript`, `bundled(project(":libraries:core"))`, JUnit5 + `kotlin-test`); add to
  `settings.gradle.kts`; mirror ge-flipper's `jar` classifier + `fatJar dependsOn` fixes.
- **0.3 Heartbeat script** on a `TaskRunner` loop (one placeholder task, humanized delay).
- **Acceptance:** deploys + loads in Echo, logs heartbeat; `:scripts:account-builder:test` runs.

### Phase 1 — Thin end-to-end live chop  *(de-risk the architecture; intentionally throwaway-ish)*
- **1.1** A minimal hardcoded chop→bank `execute()` on a one-task `TaskRunner`: if inventory full →
  walk+bank; else `Query.gameObjects().nameEquals(tree).findBestInteractable().map { it.interact("Chop down") }`.
  Hardcode tree name + bank location.
- **1.2** Wire antiban/breaks (`BreakIdleTask`, fidgets) so the realistic loop is exercised.
- **Acceptance (the key gate):** **live in Echo** — the script chops, fills inventory, walks to bank,
  deposits, repeats. This proves SDK BT + Query + Bank + Walking + antiban integrate. *If the shape
  is wrong, we learn it here — before building the engine.*

### Phase 2 — Pure engine  *(TDD, built around the proven loop)*
- **2.1 `Requirements`** + `meets(GameView)`; per-gate tests.
- **2.2 `TaskSpec`** + `TaskKey`/`TaskProgress`/`TaskStatus`; `GameView` read-side interfaces + fakes.
- **2.3 `BuilderScheduler`** over `List<TaskSpec>`: first `!isComplete && validate`; deterministic
  **seeded** shuffle; skip-complete. Tests: ordering, shuffle determinism, gating, all-complete.
- **2.4 `Watchdog`** (thin): no-progress window → `Continue|SkipTask|Stop`; stall + recovery tests.
- **2.5 Profile model** + `ProfileCodec` (gson) round-trip + `SCHEMA_VERSION` + migration placeholder test.
- **Acceptance:** comprehensive unit tests green; **`engine/` has no SDK import** (review-enforced).

### Phase 3 — Woodcutting as a real `BuilderTask`
- **3.1** Refactor Phase 1's loop into `WoodcuttingTask : BuilderTask`: `requirements`,
  `isComplete = SkillView.level(WOODCUTTING) >= target`, `validate`, `execute()` reusing `actions/`
  helpers; add `handleLevelUp`. Config: `treeType`, `targetLevel`, `area`+`bank`.
- **3.2** Pure tests (completion, requirements, config defaults, decision branches via fakes).
- **3.3** Wire `WoodcuttingTask` → `BuilderScheduler` → `MainBacklogTask` → composition root.
- **Acceptance:** live in Echo — chops, banks, **stops at the configured target level**.

### Phase 4 — Observability, persistence, minimal GUI
- **4.1 `StatsSnapshot`** (current task, target, level/XP, XP/hr, failures, runtime) + paint/Swing readout.
- **4.2** Persist `BuildProfile` + per-task progress via `StateStore`; resume mid-backlog across restart.
- **4.3** Minimal Swing config (task list w/ Woodcutting, target level, start/stop).
- **Acceptance:** restart resumes mid-build; live stats visible.

### Phase 5 — Soak, extract, handoff
- **5.1** Multi-hour soak; tune antiban + watchdog windows with real data.
- **5.2** Extract `engine/` + `engine/view/` + `actions/` to **`libraries/sdk-support`** (Path A);
  `account-builder` depends on it; confirm plugin-on-library here; migrate ge-flipper antiban if trivial.
- **5.3** `handoff.md`, tick [roadmap](./roadmap.md) checkboxes, update README.
- **Acceptance:** full suite green; both scripts build + `fatJar`; handoff written.

---

## C. Cross-cutting standards
- **Testing:** TDD on all pure layers (requirements, scheduler, watchdog, profile codec, task decision
  logic). Pure/impure boundary = the `GameView` read-side interfaces. SDK trees verified live.
- **Package discipline:** `engine/` imports no SDK and no `view/sdk/`. Review-enforced invariant.
- **Task discipline:** every `canRun()`/`execute()` keys off observable game state (invariant #4) — review checklist.
- **Persistence:** versioned schema + migration (ge-flipper precedent).
- **Process:** branch per phase, conventional commits, `/code-review max` before each PR, handoff per phase.

## D. Risks & open items
| Risk | Mitigation |
|---|---|
| SDK API drift / wrong signatures | Phase 0.1 spike documents real signatures first |
| **Integration shape wrong** | **Phase 1 proves it live before the engine is built** |
| Task re-entrancy violations (double/skipped actions) | Invariant #4 enforced in code review |
| Plugin-on-a-library (sdk-support) | Deferred to Phase 5; slice never depends on it |
| F2P woodcutting spot (bank-adjacent, F2P tree) | Confirm a concrete tree+bank pair in Phase 0.1/1.1 |
| gson + Kotlin friction | Default gson (provided); revisit only if painful |
| Watchdog policy guesswork | Keep thin; tune at Phase 5 soak |

## E. Dependencies (build order)
`0 → 1 → 2 → 3 → 4 → 5`. Phase 2 (pure engine) may proceed in parallel with Phase 1 once the module
compiles, but Phase 3 depends on **both** 1 (proven loop) and 2 (engine).
