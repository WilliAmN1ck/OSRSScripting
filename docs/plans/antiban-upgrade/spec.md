# Antiban Upgrade
# Spec

**Branch:** `antiban-upgrade`
**Scope:** Moderate (chosen via Q&A) — richer behaviour during idle *and* active flipping,
humanized cadence/reactions, and session-level variation. Lean on Echo's AI antiban for
mouse-level realism; **no walking away from the booth** (deferred to a future "Heavy" pass).
**Status:** ✅ Confirmed — ready to plan.

## Goal

Make the flipper read as a person rather than a metronome at the GE, without ever disrupting
flipping correctness. The current antiban only fidgets when fully idle and has two variations; a
flipper's bot signature is the constant, instant, identical loop at the booth.

## Decisions

### 1. Architecture (mirror the existing seam)
Pure, unit-testable policy/scheduler logic lives in `libraries/core/humanize` (like
`DelayDistribution` / `BreakScheduler`); SDK-coupled fidget *actions* live in the script (like
`SdkFidget`) and are verified in a live soak, not unit tests.

### 2. Expanded fidget repertoire
Weighted random selection with **no immediate repeats**. All fidgets are guaranteed
**side-effect-free** — no click that changes game or offer state:
- Camera drift *(existing)*
- Side-tab glance, then return to a "home" tab *(existing, enhanced to return)*
- Mouse idle-drift / brief move toward a screen edge *(new)*
- Brief world-map open then close *(new)*
- Hover (no click) over a GE slot or inventory item *(new)*
- *(Deferred unless approved)* examine an item / the GE clerk — right-click → examine is closer
  to the offer buttons, so higher misclick risk.

### 3. Fidgets during active flipping
A small per-tick probability, evaluated **only when safe**: after the tick's actions complete, and
never mid-placement or in a way that touches the open GE while offers are being set up. Idle
fidgets are unchanged.

### 4. Humanized cadence & reaction
- Vary the loop sleep via a `DelayDistribution` (≈1.5–3.5 s) instead of a fixed 2 s.
- A short randomized **reaction delay** before acting on a newly completed/filled offer — a human
  notices after a beat.

### 5. Session-level variation (fatigue)
- A **fatigue factor** that scales idle/fidget/reaction delays upward as session time grows.
- Occasional spontaneous **look-away micro-AFKs** (≈20–90 s, a few per hour), distinct from
  scheduled breaks: the script simply stops acting and idles. Tolerance is a tunable (see Open).

### 6. Safety invariants (hard rules)
- Antiban **never** closes the GE mid-placement, misclicks an offer, or leaves the player stuck.
- **No walking/pathing** in this scope.
- A fidget failure is swallowed — it can never kill the flip loop or skip a needed flip action.

### 7. Configuration
Default: keep antiban internal (no new sidebar fields) to avoid clutter. (Open question below.)

### 8. Testing
Pure schedulers — fatigue scaling, AFK scheduling, reaction delays, weighted no-repeat fidget
selection — unit-tested with an injected `Random`/clock. SDK fidget actions verified in a live
soak with the break profile enabled.

## Resolved decisions (confirmed via Q&A)

1. **Spontaneous micro-AFKs:** a few per hour, 20–90 s each. Accept the small throughput cost.
2. **"Examine" fidget:** excluded (keep clear of the offer buttons).
3. **Antiban intensity knob:** kept internal — no sidebar control for now.

## Out of scope
Walking diversions, world-map travel, NPC dialogue, chat — all "Heavy" behaviours deferred.
Mouse-curve/micro-timing realism stays with Echo's AI antiban; we do not reinvent it.
