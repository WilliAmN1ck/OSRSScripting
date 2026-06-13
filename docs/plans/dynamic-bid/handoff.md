# Dynamic bid
# Handoff

**Date:** 2026-06-13
**Branch:** `dynamic-bid`
**Status:** ✅ Code complete, tested, reviewed (0 findings). Live soak pending.

## What Was Built

Buys are placed slightly **above** the live insta-sell (low) price so they fill faster, instead of
sitting behind everyone bidding exactly the low. The bid-up is a small, self-limiting slice of the
item's margin (**5%**, internal constant `BID_FRACTION`): fat-margin items chase fills harder, thin
ones barely move, and the bid never approaches the sell price.

Crucially the bid-up is **real cost taken off the margin** the filters and ranking see —
`grossMargin → bidUp = round(grossMargin × 5%) → netMargin = grossMargin − bidUp` — so the
min-margin / min-ROI gates and the GP/hr ranking stay honest, and the candidate's stored margin and
buy price already reflect what will actually be paid.

## Design
- `FlipScanner`: compute `bidUp` from the gross margin, gate the filters on the net margin, place
  the candidate at `live.low() + bidUp`, and store the net margin. The engine sizes quantity on the
  bumped buy price, so the capital math is correct. Threshold is internal (user chose conservative).

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../ge/FlipScanner.java` | `BID_FRACTION`, bid-up applied to placement, net margin to filters/ranking |
| `README.md` | placement description updated |

## Test Coverage
- `FlipScannerTest.bidsAboveTheLiveLowByAFractionOfTheMargin` — buy = live low + 5% of margin; the
  reported margin is net of the bid-up.
- `placesAtTheLivePriceWhileDecidingOnAverages` and `breaksGpPerHourTiesTowardMoreCapitalDeployed`
  updated for the post-bid values (tie recomputed: net 76 × 300 == 228 × 100).
- `FlipTaskTest` buy assertions updated to `[100, 105, 9]` (live low 100 + bid-up 5).
- Full suite + `fatJar` green.

## Known Issues / Tech Debt
- Live soak pending: confirm buys fill faster without a noticeable margin hit. The 5% `BID_FRACTION`
  is the dial if it needs tuning.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
