# Buy-side trend guard + 5-minute freshness
# Handoff

**Date:** 2026-06-13
**Branch:** `buy-trend-guard`
**Spec:** [`spec.md`](./spec.md)
**Status:** ✅ Code complete, unit-tested, reviewed (1 self-finding fixed). Verified live 2026-06-13.

---

## What Was Built

Two deferred buying improvements, both powered by a new `/5m` fetch:

### Downtrend guard (falling knife)
A buy is skipped when the item's recent (5-minute) average sell-side price has dropped more than
**5%** below its 1-hour average — a crash in progress the bot would buy into only to sell lower.
`FlipScanner.isFallingKnife(recent, hour)`; a rising 5-minute price does not skip (buying into an
uptrend is fine for a flip). Threshold is an internal constant (no sidebar knob).

### 5-minute freshness
When an item is actively trading **both sides** in the last 5 minutes, the margin/ROI decision uses
those fresher averages instead of the laggier 1-hour; otherwise it falls back to the hour.
Liquidity (`minVolume`, the GP/hr rate) still uses the 1-hour balanced volume — more data.

## Data source
`WikiPriceClient.fiveMinuteStats()` parses `/5m` into the existing `MarketStat` (shared
`parseStats` helper with `hourlyStats()`, separate cache). `FlipScanner.scan` takes the new
`fiveMinute` map. **Graceful degradation:** a `/5m` fetch failure in `FlipTask` falls back to an
empty map (hourly-only) rather than skipping the tick — the enhancement can't halt core flipping.

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../prices/WikiPriceClient.java` | `fiveMinuteStats()` + shared `parseStats` |
| `libraries/core/.../ge/FlipScanner.java` | falling-knife guard, 5-minute-fresh pricing, new param |
| `scripts/ge-flipper/.../FlipTask.java` | fetch `/5m`, degrade to hourly on failure |
| `README.md` | feature description updated |

## Test Coverage
`FlipScannerTest`: skips a falling knife (5m low >5% below hour); a mild <5% dip does not trip the
guard; uses the fresher 5-minute averages when both sides traded (an item that fails on the hour
average passes on the 5-minute). `WikiPriceClientTest.parsesFiveMinuteStats`. `FlipTaskTest`: a 5m
fetch failure still places the buy (degrades, doesn't skip). Full suite + `fatJar` green.

## Known Issues / Tech Debt
- Verified live 2026-06-13: skips a clearly falling item and still fills slots normally.
- Dynamic bid (bidding above the live low to fill faster) intentionally left out — trades margin for
  speed; a possible opt-in later.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
