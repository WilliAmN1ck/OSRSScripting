# Post-Phase-3 — Tech debt + trade history
# Handoff

**Date:** 2026-06-12
**Branch:** `flipper-tech-debt`
**PR:** [#8](https://github.com/WilliAmN1ck/OSRSScripting/pull/8) (merged, `458dee9`)
**Spec:** [`spec.md`](./spec.md) — Batches 1 and 2
**Status:** ✅ Complete and merged. All items shipped with tests; live visual check of the
3d features passed (GE auto-close, fidgets, break profile, new panel fields).

---

## What Was Built

### Batch 1 — tech debt (`76f0ad4`)

- **Profit by transferred gold.** Profit is accounted from the SDK's
  `getTransferredGoldQuantity` (actual gold moved), not `price × filled`, so a better-than-asked
  fill is counted exactly. Offer stamps persist a gold baseline; a migration upgrades
  pre-existing state files.
- **Sell-exit escalation.** `FlipConfig.sellExitAfterRelists` (sidebar field; fresh default 3,
  restored configs 0 = off): after N consecutive stale relists of an item's sell, the next
  listing exits at the insta-sell (low) price rather than parking capital forever. The streak is
  counted per cancelled live sell, reset on a completed sell, and held in memory.
- **Members stock guard.** Items known to be members are never offered for sale when the members
  filter is off (`sellableStock` filter), closing the F2P churn where the GE rejects the offer
  every tick.

### Batch 2 — per-item trade history (`15c840e`)

- **`TradeHistory`** (core): per-item aggregate — net P/L, flips completed, quantity sold,
  last-traded time. No unbounded per-flip log.
- **Auto-avoid losers.** `FlipConfig.avoidAfterLossGp` (sidebar field; fresh default 1,000 gp,
  restored configs 0 = off): an item whose recorded net loss reaches the threshold is excluded
  from buy candidates until the history is cleared. No winner boost — capital ranking already
  surfaces winners.
- **Sidebar table + Clear.** A scrollable trade-history table (item, net P/L, flips, qty) sorted
  by net P/L, with a **Clear history** button that also resets the avoid list. The button (EDT)
  only raises an `AtomicBoolean`; the script thread performs the clear at the top of the next tick
  and persists immediately, so an outage cannot delay it and a crash cannot resurrect it.

## What Changed From the Spec

- Nothing material. The spec's "sell `transferredGold` assumed post-tax" note was the one open
  live-verification; confirmed live 2026-06-13 (see Known Issues).

## Files Changed (key)

| File | Change | Notes |
|---|---|---|
| `libraries/core/.../ge/OfferTracker.java` | modified | transferred-gold profit; relist streaks |
| `libraries/core/.../ge/TradeHistory.java` | new | per-item aggregate + avoid query |
| `libraries/core/.../model/FlipConfig.java` | modified | `sellExitAfterRelists`, `avoidAfterLossGp` |
| `libraries/core/.../ge/FlipEngine.java` | modified | sell-exit price escalation |
| `libraries/core/.../persistence/{PersistedState,StateMapper}.java` | modified | history persist + gold-baseline migration |
| `scripts/ge-flipper/.../FlipTask.java` | modified | clear flag drained pre-fetch; avoid filter |
| `scripts/ge-flipper/.../FlipperPanel.java` | modified | history table, Clear button, new fields |

## Test Coverage

All suites green. New/updated: `TradeHistoryTest`, `OfferTrackerTest` (transferred gold, relist
streaks), `FlipEngineTest` (sell-exit escalation, members), `FlipTaskTest` (avoid + clear,
clear-persists-on-fetch-failure), `FlipperPanelTest` (history table, Clear callback),
`StateMapperTest`/`StateStoreTest` (history round-trip + migration).

## Known Issues / Tech Debt

- **Sell `transferredGold` is assumed post-tax.** ✅ Verified live 2026-06-13 against a live sell's
  actual profit delta — the assumption holds. No open items remain.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
