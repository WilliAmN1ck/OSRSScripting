# Phase 5 — Mining via a shared Gatherer — Plan

**Status:** Draft, awaiting confirmation. Spec: [spec.md](./spec.md).

Four sub-phases, each its own branch + PR, each ending green (build + tests) and `/code-review max`.
Sub-phase A is a pure refactor of live-verified code → behaviour must be identical and re-verified.
All paths are under `scripts/account-builder/src/main/kotlin/com/osrsscripts/accountbuilder/` (tests under
`.../src/test/kotlin/...`) unless noted.

---

## Sub-phase A — Extract the shared gatherer (refactor Woodcutting; no behaviour change)

Branch `account-builder-shared-gatherer`. Goal: `WoodcuttingTask`'s logic moves into a generic
`GatheringTask`; Woodcutting becomes a thin instantiation; all tests green; WC re-verified live.

**A1. `engine/GatherResource.kt` (new, pure).**
```kotlin
package com.osrsscripts.accountbuilder.engine

/** A gatherable resource (tree, rock, …). Pure data so concrete types stay unit-testable. */
interface GatherResource {
    val id: String            // stable persist key (the enum constant name)
    val displayName: String
    val levelReq: Int
    val members: Boolean
    val objectNames: Set<String>  // match by object name (trees)
    val ids: Set<Int>             // match by object id (rocks)
    fun matches(objectName: String, objectId: Int): Boolean =
        objectName in objectNames || objectId in ids
}
```

**A2. `TreeType` implements `GatherResource`.** Add `override val id get() = name`, `override val ids = emptySet<Int>()`, rename its existing object-name set to `objectNames` (or `override val objectNames`).
Keep `levelReq`/`displayName`/`members`. Update `TreeTypeTest` to assert `matches(name, 0)` and `objectNames`.

**A3. `task/ToolModel.kt` (new).** Generalises `Axes`. Pure (uses the `GameView` seam, no SDK).
```kotlin
package com.osrsscripts.accountbuilder.task

import com.osrsscripts.accountbuilder.engine.GameView

interface ToolModel {
    fun isTool(name: String): Boolean
    fun toolReq(name: String): Int?  // skill level to use; null = unrecognised/degradable/rare → never auto-withdraw
    fun hasTool(view: GameView): Boolean =
        view.inventory.itemNames().any(::isTool) || view.equipment.itemNames().any(::isTool)
    fun bestUsable(bankItemNames: List<String>, skillLevel: Int): String? =
        bankItemNames.filter(::isTool)
            .mapNotNull { n -> toolReq(n)?.let { n to it } }
            .filter { (_, req) -> skillLevel >= req }
            .maxByOrNull { (_, req) -> req }?.first
}
```

**A4. Refactor `task/Axes.kt`** to `object Axes : ToolModel` — `isTool` = current `isAxe` body, `toolReq` =
current `woodcuttingReq` body. Remove the old `hasAxe`/`bestUsableAxe` (now inherited as
`hasTool`/`bestUsable`). Keep `isAxe` as an alias only if still referenced; prefer updating callers to
`isTool`. Update `AxesTest` to the `ToolModel` API (`Axes.isTool`, `Axes.hasTool`, `Axes.bestUsable`).

**A5. `task/GatheringTask.kt` (new).** Move `WoodcuttingTask`'s whole body here, generalised:
```kotlin
internal class GatheringTask(
    keyValue: String,
    private val skill: Skill,                 // engine Skill
    private val gatherAction: String,         // "Chop down" / "Mine"
    private val tool: ToolModel,
    private val allowedResources: () -> Set<GatherResource>,
    private val targetLevel: () -> Int,
    initialSpot: WorldTile? = null,
) : BuilderTask {
    override val key = TaskKey(keyValue)
    override val requirements = Requirements()
    private var spot: WorldTile? = initialSpot
    private var lastNoToolLogMs = 0L
    fun currentSpot(): WorldTile? = spot
    override fun isComplete(view) = view.skills.level(skill) >= targetLevel()
    override fun validate(view) = requirements.meets(view) && allowedResources().isNotEmpty()
    override fun progress(view) = TaskProgress("${skill.name} ${view.skills.level(skill)}/${targetLevel()}")
    override fun execute() { when { !tool.hasTool(SdkGameView) -> acquireTool(); Inventory.isFull() -> bank(); else -> gather() } }
    // gather(): findReachableResource() via Query.gameObjects().actionEquals(gatherAction)
    //           .filter { allowed.any { r -> r.matches(it.name, it.id) } } ... interact(gatherAction)
    // bank(): deposit !tool.isTool items; acquireTool(): tool.bestUsable(Bank.getAll().map{it.name}, skillLevel())
    //         using SdkSkill mapped from `skill` (reuse SdkGameView's toSdk, or expose it)
}
```
- `findReachableResource()` prefers the highest-`levelReq` qualified reachable resource (current
  best-tree logic, generalised: sort `allowedResources()` by `levelReq` desc, first reachable whose
  `matches(obj.name, obj.id)`).
- `skillLevel()` for `bestUsable`/acquire reads the live SDK level for `skill`; reuse the engine→SDK Skill
  map (extract `toSdk` from `SdkGameView` into a small `view/SdkSkills.kt` so both can use it).

**A6. Replace `WoodcuttingTask`** with a factory keeping the call shape:
```kotlin
// task/WoodcuttingTask.kt
internal const val WOODCUTTING_KEY = "woodcutting"
internal fun woodcuttingTask(allowedTrees: () -> Set<GatherResource>, targetLevel: () -> Int, initialChopSpot: WorldTile?) =
    GatheringTask(WOODCUTTING_KEY, Skill.WOODCUTTING, "Chop down", Axes, allowedTrees, targetLevel, initialChopSpot)
```
`panel::selectedTrees` (`() -> Set<TreeType>`) is assignable to `() -> Set<GatherResource>` via `Set`
covariance — verify it compiles; if not, wrap as `{ panel.selectedTrees() }`.

**A7. `AccountBuilderScript`**: construct via `woodcuttingTask(...)`; `currentChopSpot()` → `currentSpot()`.
No other behaviour change.

**A8. Verify A.** `:scripts:account-builder:test` green (TreeTypeTest, AxesTest updated; all others
unchanged). `deployLocally`; **live-verify Woodcutting is unchanged** (chop→bank→return, axe-from-bank,
chopSpot persistence). PR + `/code-review max`.

---

## Sub-phase B — `RockType` + `Picks` + ID table

Branch `account-builder-mining-data` (stacked on A or on main after A merges).

**B1. `RockType.kt` (new enum, implements `GatherResource`).** Copper, Tin (1), Iron (15), Silver (20),
Coal (30), Gold (40), Mithril (55), Adamantite (70). `members=false`, `objectNames=emptySet`,
`ids = setOf(...)` curated. `id get() = name`, `displayName`, `levelReq`.

**B2. Fill `ids` from RuneLite `Rock`/`ObjectID`** for the covered mines (Al-Kharid primary). Add a
`// source + mines covered` comment per ore. (IDs get empirically confirmed in sub-phase D live-test.)

**B3. `task/Picks.kt` (new) `object Picks : ToolModel`.** `isTool(name) = name.lowercase().endsWith("pickaxe")`;
`toolReq` = pickaxe Mining-level tiers (verify on the wiki; pickaxe ladder ≠ axe ladder — no black/infernal
pickaxe), skipping degradable/rare (crystal, 3rd age, gilded → null).

**B4. Tests:** `RockTypeTest` (levelReq, `matches(name,id)` by id, members), `PicksTest` (isTool excludes
non-picks; `bestUsable` picks highest non-degradable ≤ level; skips crystal/3rd-age). Build green.

---

## Sub-phase C — Mining task + generalized config panel + scheduler + persistence + watchdog

Branch `account-builder-mining-task`.

**C1. Generalize `AccountBuilderPanel` → `GatherConfigPanel`.** Constructor:
`(title: String, resources: List<GatherResource>, resourceParamKey: String, taskKey: String, initialLevel: Int)`.
Replace `TreeType.values()` with `resources`; title from param; persist under `taskKey`/`resourceParamKey`.
`applyProfile` parses the saved CSV back via the passed `resources` (`resources.firstOrNull { it.id == name }`)
— no `Enum.valueOf`. `selectedResources(): Set<GatherResource>`.

**C2. Instantiate two panels** in `AccountBuilderScript`:
- Woodcutting: `GatherConfigPanel("Trees to cut", TreeType.values().toList(), "trees", WOODCUTTING_KEY, wcLevel)`, NORMAL pre-checked (preserve current default).
- Mining: `GatherConfigPanel("Rocks to mine", RockType.values().toList(), "rocks", MINING_KEY, miningLevel)`, **nothing pre-checked** (opt-in).
Add both as sidebar tabs ("Woodcutting", "Mining").
(Default pre-check is per-panel config — add a `defaultSelected: Set<String>` ctor param.)

**C3. Build the two tasks + scheduler:**
```kotlin
val woodcutting = woodcuttingTask({ wcPanel.selectedResources() }, wcPanel::targetLevel, savedChopSpot)
val mining = miningTask({ minePanel.selectedResources() }, minePanel::targetLevel, savedMineSpot)
val scheduler = BuilderScheduler(listOf(woodcutting, mining))
```
`miningTask(...)` factory mirrors `woodcuttingTask`: `GatheringTask(MINING_KEY, Skill.MINING, "Mine", Picks, ...)`.
`MINING_KEY = "mining"` next to `Picks`/`RockType`.

**C4. Generalize persistence (`composeProfile` → N skills).** Compose each skill's `TaskConfig`
(selection + target + spot) each tick:
```kotlin
private fun composeProfile(panels: List<GatherConfigPanel>, loaded: BuildProfile, spots: Map<String,String?>): BuildProfile =
    panels.fold(BuildProfile(shuffleSeed = loaded.shuffleSeed)) { acc, p -> acc.merge(p.toProfile()) }
        .let { base -> spots.entries.fold(base) { acc, (key, tile) -> acc.withTaskParam(key, CHOP_TILE_PARAM, tile) } }
```
(Or simpler: build from `wcPanel.toProfile()` + `minePanel.toProfile()` merged, then inject each spot.)
Load each skill's saved spot via `getTaskParam(key, CHOP_TILE_PARAM)`; seed each task; stabilize each spot
independently against `lastSavedProfile`.

**C5. Watchdog total XP.** Replace `woodcuttingXp()` with `trainedXp()` = sum of `Skill.WOODCUTTING.getXp()`
+ `Skill.MINING.getXp()` (the configured skills), so Mining progress doesn't trip the WC stall guard.

**C6. Tests:** `GatherConfigPanelTest` (round-trip select/target for a generic resource list; opt-in default
empty); any pure persistence-merge helper. Build green.

---

## Sub-phase D — Verify + handoff

Branch as needed (docs).

**D1.** Full `:scripts:account-builder:test` green. `deployLocally`.
**D2. Live-verify (test account, Al-Kharid mine):**
- Woodcutting unchanged (regression check).
- Mining: select an ore → mines the best reachable selected ore ≤ level → banks when full → returns;
  pickaxe auto-withdrawn when missing; auto-progress as Mining levels; mineSpot persists across restart.
- **Log reachable rock IDs at Al-Kharid (and any other covered mine)** to confirm/extend `RockType.ids`.
**D3.** Update `roadmap.md` (Mining → in progress/live-verified; shared gatherer noted), write
`phase-5-.../handoff.md`, update `tasks/todo.md`.

---

## Risks / watch-points

- **A is a refactor of live code** — keep it behaviour-preserving; the WC live re-verify is the gate.
- **Rock IDs** may be incomplete per mine — D2 empirically confirms; document covered mines.
- **`Set` covariance** for the `() -> Set<GatherResource>` lambdas — verify compile; wrap if needed.
- **Two `mineSpot`/`chopSpot` anchors** persisted independently — ensure no cross-skill mix-up (keyed by task).
