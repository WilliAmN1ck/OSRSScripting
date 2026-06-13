# Item-selection rework
# Plan

**Branch:** `item-selection-rework` · **Spec:** [`spec.md`](./spec.md)

TDD: failing test first in `libraries:core`, then implement. Nothing in the engine/executor
changes — this is scanner + the data it consumes.

## Phase 1 — Data model (core)
- **1.1 `MarketStat`** — new immutable model: `avgHighPrice, avgLowPrice, highPriceVolume,
  lowPriceVolume` (+ equals/hashCode/toString). Prices of `0` mean "no trades that side".
- **1.2 `WikiPriceClient.hourlyStats()`** — replaces `volumesOneHour()`; parses all four `/1h`
  fields into `MarketStat`. Rename the cache fields. *Verify:* client test parses avg prices +
  both volumes.
- **1.3 Remove `VolumePoint`** and its test once no longer referenced.

## Phase 2 — Scanner (core, TDD)
- **2.1** Change `scan(mapping, latest, hourly, config, tax)`. Iterate `hourly`:
  - skip if `avgLow <= 0 || avgHigh <= 0`;
  - `balancedVolume = min(highVol, lowVol)`; skip if `< minVolume`;
  - `margin = tax.netMarginPerItem(id, avgLow, avgHigh)`; gate `minMarginGp`;
  - `roi = margin / avgLow`; gate `minMarginPct`;
  - membership filter;
  - placement prices from `latest` (skip if absent): `buyPrice = latest.low`, `sellPrice = latest.high`;
  - `candidate = FlipCandidate(id, latestLow, latestHigh, margin, balancedVolume, buyLimit, roi)`.
- **2.2 Ranking** — `gpPerHour = margin × (buyLimit>0 ? min(balancedVolume, buyLimit/4.0) :
  balancedVolume)` desc; tie-break `capitalDeployed` desc (`latestLow × min(min(buyLimit,
  balancedVolume), perItemCap/latestLow)`); then `itemId` asc.
- **2.3 `FlipScannerTest`** — new signature + tests: directional liquidity rejects an item with
  high total but low lesser-side; averaged margin rejects a `/latest` outlier; placement uses
  `latest`; gpPerHour orders a fast item above a larger-but-slower one with a capital tie-break.

## Phase 3 — Wire (script)
- **3.1 `FlipTask`** — `hourly = prices.hourlyStats()` (type `Map<Integer, MarketStat>`); pass to
  `scanner.scan`. `latest` still fed to `engine.plan`.
- **3.2 `FlipTaskTest`** — update canned `/1h` data to include avg prices.

## Phase 4 — Verify & hand off
- Full suite + `:scripts:ge-flipper:fatJar`. `/code-review max`; fix at root. Handoff + PR.
- Live soak: confirm it picks liquid/balanced/profitable items and deploys the bankroll.
