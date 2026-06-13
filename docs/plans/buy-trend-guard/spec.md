# Buy-side trend guard + 5-minute freshness
# Spec

**Branch:** `buy-trend-guard` · **Date:** 2026-06-13

## Background

Follow-on to the item-selection rework. Two deferred buying improvements, both powered by a new
`/5m` fetch from the OSRS Wiki real-time API (same per-item shape as `/1h`:
`avgHighPrice, avgLowPrice, highPriceVolume, lowPriceVolume`).

## Requirements

1. **Downtrend guard.** Do not place a buy for an item whose recent (5-minute) average sell-side
   price has fallen sharply below its 1-hour average — a falling knife the bot would buy into only
   to sell lower. The strongest of the deferred ideas.
2. **5-minute freshness.** When an item is actively trading *both sides* in the last 5 minutes, use
   those fresher average prices for the margin/ROI decision instead of the laggier 1-hour average;
   fall back to the 1-hour average otherwise.

## Decisions

- **One new data source:** `WikiPriceClient.fiveMinuteStats()` parsing `/5m` into the existing
  `MarketStat`. Cached on the live TTL like the others.
- **Freshness rule:** use the 5-minute averages for the price decision only when the 5-minute stat
  has both `avgHighPrice > 0` and `avgLowPrice > 0` (i.e. it traded both sides in the window).
  Otherwise use the 1-hour averages. This restricts 5-minute pricing to genuinely fresh, two-sided
  items, where it is informative rather than noisy.
- **Liquidity stays on the 1-hour volume.** The `minVolume` filter and the GP/hr rate keep using
  the 1-hour balanced volume — more data, more stable.
- **Downtrend test:** skip the item when `fiveMin.avgLowPrice() > 0` and
  `fiveMin.avgLowPrice() < oneHour.avgLowPrice() × (1 − DOWNTREND_DROP)`, with
  `DOWNTREND_DROP = 0.05` (5%). A rising 5-minute price does not skip — buying into an uptrend is
  fine for a flip. Threshold is an internal constant, not a sidebar setting.
- **Buys only.** The guard lives in the scanner (buy-candidate selection); selling owned stock is
  unaffected and continues through the engine's sell/relist/exit logic.
- **Out of scope:** dynamic bid (bidding above the live low to fill faster) — it trades margin for
  speed; left as a possible opt-in later.

## Acceptance criteria

- An item whose 5-minute avg low is >5% below its 1-hour avg low is dropped from buy candidates,
  even when it otherwise passes margin/ROI/volume (downtrend test).
- A mild dip (<5%) or a rising 5-minute price does not drop the item (no-false-positive test).
- An item with two-sided 5-minute data has its margin computed from the 5-minute averages; an item
  without it falls back to the 1-hour averages (freshness test).
- `WikiPriceClient.fiveMinuteStats()` parses the `/5m` fields (client test).
- Full suite + `fatJar` green; `/code-review max` clean; live soak shows it skipping a clearly
  falling item.
