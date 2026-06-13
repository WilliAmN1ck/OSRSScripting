# Antiban Upgrade (Moderate)
# Handoff

**Date:** 2026-06-13
**Branch:** `antiban-upgrade`
**Spec / Plan:** [`spec.md`](./spec.md) · [`plan.md`](./plan.md)
**Status:** ✅ Code complete, unit-tested, `/code-review max` clean (0 findings).
⏳ **Live fidget/AFK soak still pending** a clean flipping window (see below).

---

## What Was Built

A richer, less robotic behaviour layer on top of Echo's AI antiban, all driven by pure,
unit-tested policy in `libraries/core/humanize` with the SDK-coupled actions in the script.

### Core schedulers (pure, TDD)
- **`FidgetType` + `FidgetSelector`** — weighted, no-immediate-repeat choice of the fidget
  repertoire.
- **`FatigueScaler`** — a delay multiplier ramping 1.0 → 1.6 over 3 h, so waits/fidget gaps/reaction
  beats all stretch as a session wears on.
- **`AfkScheduler`** — spontaneous 20–90 s "look-away" AFKs every 12–24 min, with a minimum gap,
  distinct from client breaks.

### SDK fidget actions + idle wiring
- **`SdkFidget.run(FidgetType)`** — camera drift, tab-glance-then-return-home, or a mouse drift over
  the canvas (`Mouse.drift`). All side-effect-free; failures logged and swallowed.
- **`HumanizedIdle`** picks fidgets via `FidgetSelector` and stretches the gap by `FatigueScaler`.

### Loop / executor integration
- **Varied cadence** — the loop sleep is a 1.5–3.5 s `DelayDistribution` × fatigue (was a fixed 2 s).
- **Reaction beat** — `FlipActionExecutor` pauses 200–800 ms × fatigue before a non-empty batch.
- **Look-away AFKs** — the loop polls `AfkScheduler` at the top; a due AFK idles the loop (in 1 s
  chunks so Stop stays responsive) and skips that flip tick.

## What Changed From the Spec / Plan

- **Fidget repertoire trimmed to three.** The SDK exposes no world-map accessor and no no-click item
  hover, so `WORLD_MAP` and `HOVER` were dropped from `FidgetType` (camera / tab-glance / mouse-drift
  remain — still up from the original two). Confirmed by introspecting the SDK jar.
- **C3 (fidget during active flipping) deferred.** A flip's waits for fills are already *idle* ticks
  that the richer idle fidgets cover; the only window C3 adds to is the brief active burst while the
  GE is open — exactly where a fidget risks a misclick. Deferred until the live soak proves the
  fidget actions are safe in-game, then a conservative (camera / mouse-drift only) version can land.

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../humanize/{FidgetType,FidgetSelector,FatigueScaler,AfkScheduler}.java` | new (pure schedulers) |
| `scripts/ge-flipper/.../SdkFidget.java` | `run(FidgetType)` repertoire, failures swallowed |
| `scripts/ge-flipper/.../HumanizedIdle.java` | selector + fatigue; `fidgetNow()` |
| `scripts/ge-flipper/.../FlipActionExecutor.java` | optional reaction-beat seam |
| `scripts/ge-flipper/.../GeFlipperScript.java` | varied cadence, reaction beat, AFK loop |

## Test Coverage

All suites green + `fatJar`. New: `FidgetSelectorTest`, `FatigueScalerTest`, `AfkSchedulerTest`
(core); updated `HumanizedIdleTest` (selector + fatigue stretch), `FlipActionExecutorTest` (reaction
beat fires once per non-empty batch). `/code-review max`: 0 correctness findings.

## Known Issues / Tech Debt
- **Live fidget/AFK soak not yet done.** The driven attempt coincided with a scheduled ~47-min Echo
  break (account logged out) — which *incidentally confirmed break-shadowing works* (the flipper
  stayed idle, did not act while logged out). The fidget actions, varied cadence, and a look-away
  AFK still need observing during a clean flipping window. A verification build with the AFK gap
  shortened to 45–75 s was used to make an AFK observable; it has been reverted to 12–24 min.
- Sidebar stats don't refresh during an AFK window (cosmetic, ≤90 s).
- C3 (active-flip fidget) deferred — see above.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
