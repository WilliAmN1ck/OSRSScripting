# Live performance stats
# Handoff

**Date:** 2026-06-13
**Branch:** `live-stats`
**Status:** ✅ Code complete, tested, reviewed (0 findings).

## What Was Built

Richer sidebar stats so a live run's performance is visible at a glance:

- **Profit/hr** — realized session profit projected to an hourly rate (computed in the panel from
  the existing runtime + session profit; zero until the clock advances).
- **Capital deployed** ("In buy offers") — gp committed to currently open buy offers, so the user
  can see how much of the bankroll is working vs idle as cash.
- **Win/loss** ("Items: U up / D down") — count of profitable vs unprofitable items in the trade
  history.
- **Avoided (losses)** — number of items the loss-guard is currently excluding from buys.

## Design

- `StatsSnapshot` gains two fields: `openBuyCapital` (long) and `itemsAvoided` (int). Profit/hr and
  win/loss are derived in the panel from data already present, so they need no new fields.
- `GeFlipperScript.refreshStats` sums open-buy commitments in its existing offer loop and counts
  avoided items via `TradeHistory.shouldAvoid`, taking the loss threshold from the live config.
- `FlipperPanel.update` renders the four new lines; `profitPerHour` is a small pure helper.

## Files Changed (key)

| File | Change |
|---|---|
| `scripts/ge-flipper/.../StatsSnapshot.java` | `openBuyCapital`, `itemsAvoided` fields/getters |
| `scripts/ge-flipper/.../GeFlipperScript.java` | compute deployed capital + avoided count |
| `scripts/ge-flipper/.../FlipperPanel.java` | render profit/hr, win/loss, avoided, deployed |
| `README.md` | stats list updated |

## Test Coverage
`FlipperPanelTest.statsUpdateLandsOnTheLabels` extended: asserts profit/hr (313 over 90 min),
`1 up / 1 down`, `Avoided (losses): 1`, and `1,800,000` capital-in-buys all render. Full suite +
`fatJar` green.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
