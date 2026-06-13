# Offer-age split + panel scroll
# Handoff

**Date:** 2026-06-13
**Branch:** `offer-age-split-and-panel-scroll`
**Spec:** [`spec.md`](./spec.md)
**Status:** ✅ Code complete, unit-tested, `/code-review max` clean (0 findings). Live visual
check of the scroll/history layout still pending.

---

## What Was Built

### Buy vs sell offer-age
`FlipConfig.maxOfferAge` (one threshold for all stale offers) is split into
`maxOfferAgeBuy` and `maxOfferAgeSell`. `FlipEngine` picks the threshold by `offer.side()` at
the single staleness check, so an unfilled buy and a resting sell can age out independently.
Defaults are 30 min for both (unchanged behaviour on a fresh install).

### Persistence migration
`PersistedConfig` now stores `maxOfferAgeBuyMinutes` + `maxOfferAgeSellMinutes`. Its
`@JsonCreator` keeps a nullable legacy `maxOfferAgeMinutes` param: a pre-split state file seeds
*both* new fields from it, so an upgrading user keeps their setting. Keeping the legacy property
*recognised* is essential — Jackson fails on unknown properties and `StateStore.load()` swallows
that into an empty state, so a bare rename would have silently wiped the user's ledger, history,
and profit, not just the offer-age value. The legacy field is not written back, so it drops off
disk after the first save.

### Panel layout
The (tall, label-above-input) config section is wrapped in a height-capped `JScrollPane`
(`CONFIG_VIEWPORT_HEIGHT = 220`, vertical scrollbar as-needed). That frees vertical space for the
`CENTER` region, so the trade-history table shows several rows instead of one. The single
"Max offer age (minutes)" field became two: "Max buy offer age (minutes)" and
"Max sell offer age (minutes)".

## Files Changed (key)

| File | Change |
|---|---|
| `libraries/core/.../model/FlipConfig.java` | `maxOfferAge` → `maxOfferAgeBuy` / `maxOfferAgeSell` (fields, getters, builder) |
| `libraries/core/.../ge/FlipEngine.java` | staleness threshold chosen by `offer.side()` |
| `libraries/core/.../persistence/PersistedConfig.java` | two minute fields + nullable legacy migration param |
| `libraries/core/.../persistence/StateMapper.java` | snapshot/restore map both ages |
| `scripts/ge-flipper/.../FlipperPanel.java` | scrollable config section; split age field |
| `scripts/ge-flipper/.../GeFlipperScript.java` | `defaultConfig()` sets both ages |

## Test Coverage

- `FlipEngineTest.buyAndSellOffersAgeOutOnTheirOwnThresholds` — divergent thresholds (buy 10 /
  sell 60); a 30-min-old buy cancels, the sell stays.
- `StateStoreTest.legacyOfferAgeSeedsBothBuyAndSellWithoutWipingState` — a legacy
  `maxOfferAgeMinutes:90` migrates to both fields and the ledger/profit survive.
- `StateMapperTest` round-trip and `FlipperPanelTest` apply updated for the two fields.
- Full suite + `fatJar` green.

## Known Issues / Tech Debt
- **Live visual check pending** — confirm in the RuneLite sidebar that the config section scrolls
  and the trade-history table shows multiple rows. (Logic is unit-covered; only the Swing layout
  is unverified.)
- `CONFIG_VIEWPORT_HEIGHT` is a fixed 220 px — fine for the current field count; revisit if many
  more config fields are added.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
