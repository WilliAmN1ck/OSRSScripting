# Antiban Upgrade
# Plan

**Branch:** `antiban-upgrade` · **Spec:** [`spec.md`](./spec.md) · **Status:** Draft — awaiting approval.

TDD throughout: pure scheduler logic in `libraries/core/humanize` (failing test first), SDK fidget
actions in the script (verified in the live soak, not unit-tested). Nothing may disrupt a flip.

## Phase A — Core schedulers (pure, unit-tested)

- **A1 `FidgetType` + `FidgetSelector`.** New enum `FidgetType {CAMERA, TAB_GLANCE, MOUSE_DRIFT,
  WORLD_MAP, HOVER}` and a `FidgetSelector` that returns a weighted-random type with **no immediate
  repeat**, via an injected `Random`.
  *Verify:* never returns the previous type twice running; every type is reachable; weighting honoured
  over a large sample.
- **A2 `FatigueScaler`.** Given a session start and `now`, returns a delay **multiplier** that ramps
  from 1.0 upward over the session (configurable ramp, capped, e.g. →1.6× over 3 h).
  *Verify:* 1.0 at start; monotonic increase; never exceeds the cap.
- **A3 `AfkScheduler`.** Decides spontaneous look-away AFKs: given `now`, the last AFK time, and an
  injected `Random`, returns `Optional<Duration>` (20–90 s) at a target rate of a few per hour, with
  a minimum spacing.
  *Verify:* respects min spacing; duration within bounds; long-run rate within tolerance; never two
  back-to-back.

## Phase B — SDK fidget actions (script, live-verified)

- **B1 Extend `SdkFidget` to execute a `FidgetType`.** `run(FidgetType)` maps each type to a
  **side-effect-free** SDK action: camera drift *(have)*, tab-glance-then-return-home *(enhance)*,
  mouse idle-drift via `context.getMouse().drift(Rectangle, long)` over the canvas *(new)*. Every
  action wrapped so a failure is swallowed (logged, never rethrown).
  **Deviation:** the SDK exposes no world-map accessor and no no-click item-hover, so `WORLD_MAP`
  and `HOVER` were dropped from `FidgetType` — the repertoire is the three above (still up from the
  original two).
  *Verify:* live soak (no unit test — SDK-coupled).
- **B2 `HumanizedIdle` uses the selector + fatigue.** Pull the next `FidgetType` from `FidgetSelector`
  and scale the inter-fidget delay by `FatigueScaler`. Expose `maybeFidgetNow(...)` so the active-flip
  path (Phase C3) can reuse the same machinery.
  *Verify:* existing `HumanizedIdleTest` updated — schedules via the selector, scales by fatigue.

## Phase C — Cadence, reaction & AFK integration (script)

- **C1 Varied loop cadence.** In `GeFlipperScript`, replace the fixed `sleep(TICK_INTERVAL_MS)` with a
  `DelayDistribution` (≈1500–3500 ms) scaled by `FatigueScaler`.
- **C2 Reaction beat.** Before `FlipTask` acts on a newly completed/filled offer, apply a short
  randomized reaction delay (a `DelayDistribution`, ≈200–800 ms) through an injected `Sleeper` seam so
  it stays unit-testable.
  *Verify:* `FlipTaskTest` — the sleeper is invoked before acting; zero-delay sleeper keeps existing
  assertions green.
- **C3 Active-flip fidget. — DEFERRED.** Rationale: in a flip cycle the *waits for fills are already
  idle ticks* (no actions), so the now-richer idle fidgets cover the natural waits. The only window
  C3 would add to is the brief active bursts while the GE is open — exactly where a fidget carries
  misclick/interface risk. Deferred until the live soak proves the fidget actions are safe in-game;
  then a conservative version (camera / mouse-drift only) can be added. Noted, not silently dropped.
- **C4 Spontaneous AFK.** Wire `AfkScheduler` into the loop: when it returns a duration, the loop
  stops acting and idles (fidgets allowed) until it elapses — distinct from the break sidecar.
  *Verify:* `FlipTaskTest` / loop test — no flip actions issued during an AFK window; resumes after.

## Phase D — Verify & hand off

- **D1** Full suite + `:scripts:ge-flipper:fatJar` green.
- **D2** `/code-review max`; fix findings at root.
- **D3** Live soak with the break profile enabled: observe varied fidgets, an active-flip fidget, at
  least one micro-AFK, and clean break pause/resume — **with zero disrupted flips**. Confirm each new
  fidget action actually fires in-game (the spec's live-verify requirement).
- **D4** Handoff in `docs/plans/antiban-upgrade/handoff.md`; PR.

## Risks / guards
- **Fidgets must not touch the GE while it's open for setup.** C3 gates on a GE-safe point.
- **AFKs must not starve sells/collections indefinitely** — bounded to 20–90 s, a few per hour.
- **Reaction/cadence delays must not break the 2-min "act within" assumption** for staying logged in;
  keep the worst-case tick well under that.
