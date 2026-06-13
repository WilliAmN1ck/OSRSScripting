# Sell-side crash exit
# Handoff

**Date:** 2026-06-13
**Branch:** `sell-crash-exit`
**Status:** ✅ Code complete, unit + integration tested, reviewed (0 findings). Live soak pending.

---

## What Was Built

The mirror of the buy-side falling-knife guard, applied to **held positions**. When a held item's
price is crashing in real time (its 5-minute average sell-side price more than **5%** below the
1-hour average — the same `MarketTrend.isFallingKnife` rule the buy scanner uses), its sell is
listed at the **insta-sell (low) price** to dump the position immediately, instead of listing at the
high and watching the value fall. This is a second, faster trigger for the low-price exit, on top of
the existing "after N stale relists" escalation.

## Design

- **`MarketTrend` (new, core)** — extracts the falling-knife rule (and the 5% `DEFAULT_DROP`
  constant) into one shared helper, so the buy scanner and the sell path judge a crash identically.
  `FlipScanner` now calls it; its private copy was removed.
- **`FlipEngine.plan(...)`** — a new overload takes `Set<Integer> crashingItems`; the existing
  overloads delegate with an empty set, so every current caller and test is unchanged. `sellPrice`
  exits at the low when **either** the relist threshold is reached **or** the item is crashing.
- **`FlipTask`** — computes the crashing set over the *held stock* each tick from the 5-minute and
  hourly stats it already fetches, and passes it to the engine.

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../ge/MarketTrend.java` | new — shared falling-knife rule + `DEFAULT_DROP` |
| `libraries/core/.../ge/FlipScanner.java` | uses `MarketTrend` (private copy removed) |
| `libraries/core/.../ge/FlipEngine.java` | `plan(... crashingItems)` overload; crash trigger in `sellPrice` |
| `scripts/ge-flipper/.../FlipTask.java` | computes the crashing held-stock set, passes it |
| `README.md` | sell description updated |

## Test Coverage
- `MarketTrendTest` — sharp drop / mild dip / rising / null & zero cases.
- `FlipEngineTest.crashingHeldItemExitsAtTheInstaSellPriceEvenWithoutStaleRelists` — crash dumps at
  the low with relist count 0.
- `FlipTaskTest.aCrashingHeldItemIsDumpedAtTheInstaSellPrice` — end-to-end: a held item with a
  crashing 5-minute price sells at the low, not the high.
- Existing sell tests (relist escalation, exit-disabled) confirm no regression. Full suite + `fatJar`
  green.

## Known Issues / Tech Debt
- Live soak pending: confirm a held item caught in a real crash is dumped at the low.
- The 5% threshold is shared with the buy guard and internal (no sidebar knob), consistent with the
  prior "keep it internal" preference.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
