# Phase 5 — Mining, via a shared Gatherer — Spec

**Status:** Draft, awaiting confirmation. **Date:** 2026-06-13.
Project docs: [../spec.md](../spec.md) · [../plan.md](../plan.md) · [../roadmap.md](../roadmap.md)

## Goal

Add **Mining** as the second buildable skill — full F2P ore ladder, level-gated and hands-off, mirroring
the live-verified Woodcutting slice. Achieve it by **extracting Woodcutting's logic into a reusable
`GatheringTask`** and instantiating that generic task for both Woodcutting and Mining (no duplication).

## Decisions (from Q&A)

1. **Architecture — extract a shared gatherer.** Refactor `WoodcuttingTask` into a generic
   `GatheringTask` parameterized by skill / gather-action / resource-set / tool, then instantiate it for
   Woodcutting and Mining. Woodcutting must be re-verified after the refactor (no behaviour change).
2. **Ore scope — full F2P ladder:** Copper (1), Tin (1), Iron (15), Silver (20), Coal (30), Gold (40),
   Mithril (55), Adamantite (70). Runite excluded (F2P is wilderness-only). Pre-selectable with
   "(unlocks at N)" labels so a build auto-progresses, exactly like the tree list.
3. **Ore identification — curated ID table.** Mineable rocks are all named `"Rocks"`; the ore is
   distinguished by object **ID**, with multiple IDs per ore varying by mine. `RockType` carries an
   **id-set per ore**, sourced from RuneLite's Mining `Rock` / `ObjectID` data **and verified empirically
   in-game** (see "Open data"). Covered F2P mines: **Al-Kharid (primary — has the entire ladder
   copper→adamantite in one place)**, Lumbridge Swamp West (coal/mithril/adamantite), Dwarven Mine, and
   the F2P Mining Guild (mithril/adamantite/coal; requires **60 Mining** to enter). Ore selection only
   works where IDs are covered; this is a documented limitation.
4. **Everything else mirrors Woodcutting** (no new questions): pickaxe auto-withdrawn from the bank when
   none is held (best usable, non-degradable tier, gated by Mining level), reachable-rock finding wherever
   the user starts, `mineSpot` persistence + return after banking/restart, and the same
   scheduler / watchdog / antiban.

## Design

### Shared gatherer (the enabling refactor)

- **`GatherResource`** interface, kept **pure / SDK-free so the enums stay unit-testable** (as `TreeType`
  is today): `displayName`, `levelReq`, `members`, `objectNames: Set<String>`, `ids: Set<Int>`, and a pure
  `matches(name: String, id: Int): Boolean = name in objectNames || id in ids`.
  - `TreeType` populates `objectNames` (ids empty) — existing name behaviour, still testable by name.
  - `RockType` populates `ids` (objectNames empty) — the curated id-set, testable by id.
  - The SDK-coupled gatherer calls `resource.matches(obj.name, obj.id)`; the matching **data + logic stay
    pure**, only the `GameObject` read lives in the (live-verified) gatherer. (Avoids coupling the resource
    enums to the SDK `GameObject`, which would break their unit tests.)
- **`ToolModel`** abstraction (generalises today's `Axes`): `isTool(name)`, `hasTool(view)` (inventory or
  equipment), `bestUsable(bankNames, level)` (highest non-degradable tier whose requirement ≤ level).
  - `Axes` and `Picks` provide only the tier→required-level map (`toolReq(name)`); the `hasTool` /
    `bestUsable` logic is generic. Degradable/rare tools are skipped (mirrors the axe policy: e.g. crystal,
    3rd age).
- **`GatheringTask`** (generic `BuilderTask`, replaces `WoodcuttingTask`'s body): constructed with
  `key`, engine `skill`, `gatherAction` (`"Chop down"` / `"Mine"`), `tool: ToolModel`,
  `allowedResources: () -> Set<GatherResource>`, `targetLevel: () -> Int`, `initialSpot: WorldTile?`.
  Implements `isComplete` (level ≥ target), `validate` (resources selected), `execute` (no-tool → acquire
  from bank / full → bank / else → gather), best-reachable-resource selection, `mineSpot` persistence, and
  tool-from-bank acquisition — all the logic that exists in `WoodcuttingTask` today, generalised.
- `WoodcuttingTask` and `MiningTask` become thin instantiations of `GatheringTask` (or factory functions).

### Mining specifics

- **`RockType`** enum: the 8 F2P ores above, each with `displayName`, `levelReq`, `members = false`,
  `ids: Set<Int>` (from RuneLite), `matches(obj) = obj.id in ids`.
- **`Picks`** tool model: `isTool(name) = name.endsWith("pickaxe")`; tier→Mining-level map. **Note: the
  pickaxe ladder is NOT the axe ladder** — OSRS has no black or infernal pickaxe — so `Picks` supplies its
  own `toolReq` (bronze/iron, steel, mithril, adamant, rune, dragon … exact reqs verified against the wiki
  during execution), skipping degradable/rare picks (crystal, 3rd age, gilded).
- **Gather action** `"Mine"`; depleted rocks lose the `"Mine"` action, so `actionEquals("Mine")` naturally
  filters them out. A rock yields one ore then depletes (unlike a tree giving many logs), so the gatherer
  hops between rocks constantly and may briefly idle while a patch respawns — the existing no-progress idle
  + best-reachable re-query handle this, and "highest-qualified" naturally falls back to lower selected
  ores as the higher ones deplete. (Verify the respawn-lull behaviour live.)

### Resource-selection semantics (Mining vs Woodcutting)

The gatherer picks the **highest-level qualified reachable** resource (Woodcutting's auto-upgrade rule).
For trees that's unambiguous (types live in separate spots). At a mine, multiple ores coexist, so with
several ores selected the bot mines the **highest available**, falling back to lower selected ores only as
the higher ones deplete (an XP-max default). **The control is "select only the ore you want."** A
nearest-qualified-first policy is a possible later refinement; this slice keeps the highest-qualified rule
for consistency with Woodcutting.

### Config UI & scheduling

- Generalise `AccountBuilderPanel` into a reusable **`GatherConfigPanel(skillName, resources, initialLevel)`**
  (checkbox per resource + target field + "unlocks at N" labels). Two sidebar tabs: **Woodcutting** (trees)
  and **Mining** (rocks).
- **Mining is opt-in / off by default:** the Mining tab starts with **no rock checked**, so the task's
  `validate()` is false and it is skipped — an existing Woodcutting-only user is unaffected. Selecting an
  ore activates Mining.
- **Scheduler** runs the ordered list `[Woodcutting, Mining]`, first incomplete-and-runnable task each tick
  (existing behaviour). To train Mining first/only, uncheck the trees (or lower the Woodcutting target).
- **Watchdog** progress signal switches from Woodcutting-only XP to **total XP across the configured
  skills** (WC + Mining), so a non-WC task no longer looks like a WC stall.

### Persistence

- The profile gains a second `TaskConfig` (`key = "mining"`) holding the rock selection + target + the
  `mineSpot` anchor — reusing `withTaskParam` / `getTaskParam` / `TileCodec` / the distance-throttle.
- **Backward-compat:** keep **per-skill resource param keys** — Woodcutting stays `"trees"` (so existing
  saved profiles still restore), Mining uses `"rocks"`. No rename, no migration.
- `composeProfile` (today WC-specific) **generalizes to N skills** — it composes each configured skill's
  `TaskConfig` (selection + target + spot) each tick; existing woodcutting persistence is unchanged.
- The generalized config panel needs a **per-skill name→resource lookup** to restore a saved selection
  (can't call `Enum.valueOf` through the `GatherResource` interface) — pass each skill's resource set (or a
  parse function) into the panel.

## Out of scope (this phase)

- Ore selection at mines whose rock IDs aren't in the curated table (documented limitation).
- Smelting / bars, gem rocks, motherlode/volcanic/3-tick methods, members ores.
- A third skill (the gatherer abstraction should make it cheap, but it's not built here).

## Acceptance criteria

- `GatheringTask` drives **both** skills; `WoodcuttingTask` behaviour is unchanged and re-verified live.
- Mining at a covered F2P mine: select an ore → mine the best reachable selected ore ≤ level → bank when
  full → return; auto-progress as Mining levels; pickaxe auto-withdrawn from the bank when missing;
  mineSpot persists across restart.
- All existing tests stay green; new tests for `RockType`, `Picks`, the generic tool/selection logic.
- Two sidebar tabs render; Mining off until an ore is selected.

## Open data to gather during execution

- Exact pickaxe Mining-level requirements (verify on the wiki — remember the pickaxe ladder ≠ axe ladder).
- Rock object IDs per ore for the covered F2P mines: mirror RuneLite's `Rock` enum / `ObjectID`, then
  **verify empirically in-game** during live-test by logging the IDs of reachable `"Mine"`-able rocks at
  each covered mine (more reliable than scraped tables, which were inconsistent across sources).

## Review notes (holes found & resolved in this spec)

- **Testability:** matching kept pure/data-driven (`objectNames`/`ids` + `matches(name,id)`), not
  `matches(GameObject)`, so the resource enums stay unit-testable.
- **Persistence:** per-skill param keys (`trees`/`rocks`) preserve existing saved profiles — no migration.
- **Selection semantics:** highest-qualified rule documented for the multi-ore-at-one-mine case; control is
  "select only the ore you want."
- **Pickaxe ladder ≠ axe ladder** (no black/infernal pickaxe) → `Picks` has its own `toolReq`.
- **F2P ore reality verified:** all 8 ladder ores are F2P; Al-Kharid mine carries the full ladder.
