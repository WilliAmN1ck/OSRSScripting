# SDK API Spike Notes (Phase 0.1)

Grounded in TRiBot's public example repo `TribotRS/Tribot-Community-Automations` (authoritative
real usage) — the Javadoc/Kdoc host (runeautomation.com) was down (HTTP 521) during the spike.
**Compile against the plugin-provided SDK is the final arbiter** of any signature marked _verify_.

## Entry point — two `TribotScript` flavors
- **automation-sdk:** `org.tribot.automation.TribotScript` → `execute(context: ScriptContext)`.
  ge-flipper + our heartbeat use this. Gives `context.logger`, `context.waiting`, `context.sidebar`,
  `context.sidecars`, `context.client.localPlayer`, `context.addonLibraries.dentistWalker`. ✅ confirmed (build green)
- **script-sdk:** `org.tribot.script.sdk.script.TribotScript` → `execute(args: String)` + `configure(ScriptConfig)`;
  uses static singletons + `Log`. This is what `tut-island` uses.
- **We stay on automation-sdk** (ScriptContext) for consistency with ge-flipper. The script-sdk
  **static singletons below are usable from it** (ge-flipper already calls `org.tribot.script.sdk.antiban.Antiban`).

## Confirmed static singletons (`org.tribot.script.sdk.*`)
- **`Query`** (`...query.Query`): `Query.gameObjects()`, `Query.npcs()`, `Query.inventory()`, …
  - filters: `.idEquals(int)`, `.nameEquals(String)`, `.actionEquals(String)`, `.isReachable`, `.filter { }`
  - terminals: `.findClosest()`, `.findBestInteractable()` → `Optional<T>`; `.isAny` (Boolean prop); `.orElse(null)`
  - interact: `obj.interact("Use")` / `npc.interact("Attack")` → `Boolean`
- **`Bank`**: `Bank.isOpen()`, `Bank.ensureOpen()`, `Bank.depositAllExcept(vararg names)`, `Bank.close()`,
  `Bank.depositAll(id)` _(verify)_, `Bank.withdraw(...)` _(verify)_
- **`Inventory`**: `getCount(id)`, `isFull()`, `getItems()`, `contains(...)`
- **`Equipment`**: `equip(itemId)`, `getCount(itemId)`
- **`MyPlayer`**: `getTile()`, `isMoving()`, `get(): Optional<…>`, `.interactingCharacter`
- **`Waiting`**: `Waiting.wait(ms)`, `Waiting.waitUntil(timeoutMs) { cond }`
- **`GameState`**: `GameState.getSetting(varbit)` (tut-island gates tasks on varbit 281 progression)
- **`Camera`**: `Camera.setRotationMethod(Camera.RotationMethod.MOUSE)`
- **`Login`**: `isLoggedIn()`, `login()`, `logout()`
- **`Skills` / `Skill`**: level/XP read — `Skills.getXp(Skill.WOODCUTTING)` style _(verify exact bare-SDK call: `Skills.getActualLevel`/`Skill.getActualLevel`)_
- **`net.runelite.api.gameval.ItemID`**, **`net.runelite.api.Skill`**, **`...types.WorldTile`/`WorldPoint`** for ids/skills/tiles

## Walking
- automation-sdk: `context.addonLibraries.dentistWalker.walkTo(WorldPoint)` ✅ (player-mover example)
- player location/distance: `context.client.localPlayer?.worldLocation`, `WorldPoint.distanceTo(other)`
- tut-island wraps its own `Walker.walkTo(tile)` util over the SDK walker.

## Behavior trees — exist, but NOT used by the closest example
- Framework is real: `org.tribot.script.sdk.frameworks.behaviortree` — DSL `behaviorTree { selector { condition{} ; perform{} } }`,
  `.tick()`, `BehaviorTreeStatus.KILL`, `ObserverAbort(AbortType.LowerPriority|Self|Both)` (Nullable forum tutorial).
- **However:** `tut-island` (the closest official analog to an account builder) does **not** use it. It
  uses a **prioritized task scheduler** instead (see below).

## ★ Key finding — the proven pattern for account-builder-style scripts
`tut-island` structure:
```kotlin
interface Task { val displayName: String; val priority: Int; fun canRun(): Boolean; fun execute() }
// ~50 small task classes (CutTree, FishShrimp, MineTin, OpenBankBooth, ...), each canRun()-gated,
// run by a TaskScheduler that ticks the first runnable task. Plus: stuck watchdog (no-movement
// timeout), micro-breaks, paint, login handler via configure{ isRandomsAndLoginHandlerEnabled }.
```
This is **almost identical to our existing `core.task.Task`** (`shouldRun`/`execute`/`name`). The
authoritative evidence is that big multi-step account-building scripts use **prioritized tasks**, not
behavior trees. → **Recommend pivoting the engine to the prioritized-task pattern** (reuse `core.task`),
with behavior trees reserved as an optional per-task tool for genuinely complex (e.g. quest) logic.
See the engine-contract impact noted back to the user / plan revision.

## Confirmed by our own build
Kotlin module + `org.tribot.dev` plugin compiles, tests, and `fatJar`s green; `ScriptContext`
`logger`/`waiting` usage correct.
