# AIO Account Builder — North-Star Roadmap & Backlog

**The master feature list.** Source of truth for the full scope (from the
[TRiBot AIO product](https://tribot.org/shop/products/6-aio-account-builder)). We build engine-first
and add tasks incrementally — **nothing is "done" until it is checked off here.** Every PR should
update the relevant line. This exists so the north star is never lost to incremental work.

**Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done & live-verified · `[-]` explicitly out of scope

> **Current phase:** Engine + Woodcutting vertical slice (see [spec.md](./spec.md)). Everything else
> below is backlog until then.

---

## 1. Engine & framework  ← current phase
- [x] Task engine: `TaskSpec` (isComplete/validate) + `BuilderScheduler` (skip-complete, validate-gate, seeded shuffle) — tested + live
- [x] Prioritized-task pattern (tut-island ≈ core.task.Task); behavior trees optional per-task
- [x] Manual ordered task list + **task shuffling** (seeded, deterministic)
- [x] State persistence — config (trees + target) saved/restored via ProfileStore in the script-settings dir; wired + tested (resume pending a live-verify)
- [ ] Auto-planner on top of the manual list (declarative target → ordering) — *deferred, design must not preclude*
- [ ] Extract reusable engine/helpers to `libraries/sdk-support` (Path A fast-follow)

## 2. Skills (1–99, all methods)
F2P-reachable first. One line per skill; expand into per-method sub-tasks as built.
- [x] **Woodcutting** — F2P chop→bank→target level. **LIVE-VERIFIED 2026-06-13** (chop, Lumbridge multi-floor bank+return, stop-at-target, hands-off "unlocks at N" progression, 10+ min stable).
- [ ] Attack · [ ] Strength · [ ] Defence · [ ] Hitpoints · [ ] Ranged · [ ] Magic · [ ] Prayer
- [ ] Mining · [ ] Fishing · [ ] Cooking · [ ] Firemaking · [ ] Smithing · [ ] Crafting
- [ ] Runecraft · [ ] Agility · [ ] Herblore · [ ] Thieving · [ ] Fletching · [ ] Slayer
- [ ] Hunter · [ ] Construction · [ ] Farming (skill training, distinct from run-task below)

## 3. Quests (target: 174 tasks / 247 quest points)
- [ ] **F2P quests** (21): Dragon Slayer, Demon Slayer, Vampyre Slayer, … (enumerate as built)
- [ ] **P2P quests**: Monkey Madness 1, Desert Treasure 1, Legends Quest, … (enumerate as built)
- [ ] **Ultimate-exclusive quests**: Devious Minds, Song of the Elves, Dragon Slayer 2 (separate key purchases)
- [ ] **Misc / non-quest tasks** (28): (enumerate as built)

## 4. Minigames & training methods
- [ ] Wintertodt (Firemaking)
- [ ] Nightmare Zone (NMZ)
- [ ] Pest Control
- [ ] Drift Net Fishing
- [ ] Aerial Fishing
- [ ] Arceuus Library (Magic & Runecraft)
- [ ] Slayer via Vannaka

## 5. Run-tasks (smart scheduling between main tasks)
- [ ] Farming runs (herb/tree runs on timers)
- [ ] Birdhouse runs

## 6. Money-making & GE
- [ ] GE buy/sell support (consider reusing the ge-flipper engine for supply purchasing)
- [ ] Supply restocking driven by the active task's needs

## 7. Misc acquisitions / diaries
- [ ] Ice Gloves
- [ ] Barrows Gloves
- [ ] Rock Cake (for NMZ HP-drop)
- [ ] Graceful outfit purchase
- [ ] Easy Ardougne Diary
- [ ] Easy Varrock Diary

## 8. Antiban & humanization
- [x] Behavioural antiban in the builder: fatigue-scaled cadence + look-away AFK (pure core.humanize).
- [ ] SDK fidgets (camera/tab/mouse) in the builder — pending the sdk-support extraction of SdkFidget.
- [x] Break-aware idling via SDK BreakHandler sidecar (reused)
- [ ] MiniBreak system (short in-task micro-breaks)
- [ ] "Huge antiban / anti-pattern" coverage review across all task types

## 9. Configuration, profiles & CLI
- [~] Minimal config panel for the slice (Swing)
- [ ] Custom **gear profiles per task**
- [ ] GUI **save/load profiles**
- [ ] CLI argument support (launch presets; some provided by the dev plugin)

## 10. Integrations
- [ ] Discord webhook notifications — text messages
- [ ] Discord webhook notifications — images/screenshots

## 11. Account types
- [~] Mains / F2P first
- [ ] Members content
- [-] Ironman mode (the reference product excludes it; out of scope unless we decide otherwise)

---

_Last updated: 2026-06-13 (initial creation). Keep this current with every AIO PR._
