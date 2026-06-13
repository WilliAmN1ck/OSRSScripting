# Item-selection rework (smarter candidate finding)
# Spec

**Branch:** `item-selection-rework` · **Date:** 2026-06-13

## Background

Research into OSRS flipping practice (GE Tracker, 07Flip, OSRS Flipping Pro, LootHelper) plus the
wiki real-time API surfaced three gaps in `FlipScanner`. The user chose the **full A+B+C** rework
with margins computed from **1h averaged prices**.

Sources:
- https://www.ge-tracker.com/
- https://07flip.com/guides/flipping
- https://osrsflippingpro.com/osrs-flipping-guide-2025-the-ultimate-osrs-profit-strategy/
- https://loothelper.com/games/runescape/osrs-flipping-guide/
- https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices

## Current behaviour (baseline)

`FlipScanner` uses raw `/latest` `low`/`high` for the margin, filters on **total** 1h volume
(`high + low`), and ranks by **capital deployed per cycle** (PR #9), tie-broken by profit/cycle.

## Decisions

### A — Directional liquidity (the dominant flip risk)
A round-trip flip fills its **buy** against insta-sellers (`lowPriceVolume`) and its **sell**
against insta-buyers (`highPriceVolume`). The binding throughput is the **lesser** of the two, not
the sum. So:
- Liquidity measure becomes `balancedVolume = min(highPriceVolume, lowPriceVolume)`.
- The `minVolume` filter compares against `balancedVolume` (stricter, more honest — "Min hourly
  volume" now means the lesser side). The user's current `minVolume = 5` is unaffected in practice.

### B — Reliable prices from 1h averages
The margin decision moves off the noisy single-trade `/latest` onto the hour's
`avgHighPrice`/`avgLowPrice` (already returned by the `/1h` payload we fetch for volume). This
resists single-trade outliers and stale listings.
- **Decide on averaged, place at live.** Margin / ROI / ranking use the averaged prices; the
  **offer is still placed at the current `/latest` price** so it fills at market. Averaging the
  placement price would make us bid under market and fill slowly — the opposite of the goal.
- An item with no hourly average on either side is skipped (can't price it reliably; it would fail
  the liquidity floor anyway).

### C — Rank by estimated GP/hr
Replace the capital-first primary key with the community-standard **profit per unit time**:
- `sustainableUnitsPerHour = buyLimit > 0 ? min(balancedVolume, buyLimit / 4.0) : balancedVolume`
  (buy limit is per 4 h → per-hour rate is `buyLimit / 4`).
- `gpPerHour = netMarginPerItem(avg) × sustainableUnitsPerHour` — **primary ranking key, desc**.
- **Tie-break: capital deployed desc**, then `itemId` asc — so among equally profitable items the
  one that soaks more of the bankroll wins, preserving the PR #9 intent.
- Capital still gets deployed because the engine's existing **min-spend-per-buy floor**
  (`minDeploymentGp`) rejects buys that deploy too little; GP/hr ranking only reorders what passes.

## Data-model changes

The `/1h` response carries `avgHighPrice, avgLowPrice, highPriceVolume, lowPriceVolume` per item.
Replace the volume-only `VolumePoint` with a `MarketStat` carrying all four, and
`WikiPriceClient.volumesOneHour()` with `hourlyStats()` returning `Map<Integer, MarketStat>`. The
scanner takes `latest` (for placement prices) **and** `hourly` stats (for averaged margin +
directional volume). `/latest` is still fetched — the engine uses it to price sells of owned stock.

## Acceptance criteria

- Scanner discards an item whose **lesser** directional volume is below `minVolume`, even when the
  summed volume clears it (directional-liquidity test).
- Margin/ROI computed from `avgLow`/`avgHigh`; a candidate that looks profitable on a single
  outlier `/latest` trade but not on the hour average is rejected (averaged-margin test).
- The placed buy offer's price equals the live `/latest` low, not the average (placement test).
- Ranking orders by `gpPerHour`: a faster, more profitable item outranks a larger but slower one;
  ties break toward larger capital deployed (ranking test).
- Full suite + `fatJar` green; `/code-review max` clean; live soak shows it picking liquid,
  balanced, profitable items and deploying the bankroll.

## Out of scope (noted for later)
- `/timeseries`-based trend/volatility guards (detect mid-crash items).
- `/5m` freshness blending (user chose 1h averages).
- Recency/staleness rejection on `/latest` timestamps (not needed once margins use averages).
