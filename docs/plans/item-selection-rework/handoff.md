# Item-selection rework
# Handoff

**Date:** 2026-06-13
**Branch:** `item-selection-rework`
**Spec / Plan:** [`spec.md`](./spec.md) · [`plan.md`](./plan.md)
**Status:** ✅ Code complete, unit-tested, `/code-review max` clean (0 findings). Live soak pending.

---

## What Was Built

A smarter candidate finder in `FlipScanner`, driven by research into OSRS flipping practice. Three
changes (the user chose the full A+B+C):

### A — Directional liquidity
Liquidity is now the **lesser** of the two directional volumes (`min(highPriceVolume,
lowPriceVolume)`), not their sum. A round-trip flip must fill both a buy (against insta-sellers) and
a sell (against insta-buyers), so the thinner side governs. `MarketStat.balancedVolume()` exposes
it; the `minVolume` filter compares against it.

### B — Averaged margins, live placement
The margin/ROI decision uses the trailing hour's `avgHighPrice`/`avgLowPrice` (already in the `/1h`
payload), so a single outlier `/latest` trade can't bait a bad item. Offers are still **placed at
the current `/latest` price** so they fill at market — `FlipCandidate` carries the live placement
prices while its margin/roi come from the averages. An item with no hourly average on either side,
or no live price to place against, is skipped.

### C — GP/hr ranking
Primary ranking key is now estimated **profit per hour**: `netMargin × min(balancedVolume,
buyLimit/4)` (the buy limit is per 4 h). Ties break toward **capital deployed**, then item id. The
bankroll still deploys because the engine's existing min-spend floor rejects buys that deploy too
little; GP/hr only reorders what passes. This revisits the PR #9 capital-first key by design.

## Data-model change
`VolumePoint` (volume-only) is replaced by `MarketStat` (avg high/low price + both volumes);
`WikiPriceClient.volumesOneHour()` → `hourlyStats()`. `FlipScanner.scan` now takes both `latest`
(placement) and `hourly` (decision). `/latest` is still fetched — the engine prices sells of owned
stock from it, unchanged.

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../model/MarketStat.java` | new — avg prices + directional volumes + `balancedVolume()` |
| `libraries/core/.../model/VolumePoint.java` | deleted |
| `libraries/core/.../prices/WikiPriceClient.java` | `hourlyStats()` parses avg prices + volumes |
| `libraries/core/.../ge/FlipScanner.java` | averaged margin, directional liquidity, GP/hr ranking |
| `scripts/ge-flipper/.../FlipTask.java` | feeds `hourlyStats()` to the scanner |
| `README.md` | feature description updated |

## Test Coverage
`FlipScannerTest` rewritten to the new contract: directional liquidity rejects a lopsided item that
clears the summed floor; an attractive `/latest` outlier is rejected on the hour average; offers
price at `/latest` while deciding on averages; an item with no live price is skipped; GP/hr orders a
fast item above a larger-but-slower one; ties break toward more capital. `WikiPriceClientTest` parses
the avg-price fields. Full suite + `fatJar` green.

## Known Issues / Tech Debt
- **Live soak pending** — confirm it picks liquid, balanced, profitable items and still deploys the
  bankroll, with no regression in fill rate from deciding on averages.
- `FlipCandidate.roi()` is computed from the averaged buy while `buyPrice()` is the live price — an
  intentional decide-on-average / place-at-live split, not a bug.

## Out of Scope (noted for later)
- `/timeseries` trend / volatility guards (detect mid-crash items).
- `/5m` freshness blending (user chose 1h averages).

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
