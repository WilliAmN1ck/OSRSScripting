# Offer-age split + panel scroll
# Spec

**Branch:** `offer-age-split-and-panel-scroll` · **Date:** 2026-06-13

## Requirements

1. **Split "Max offer age" into buy vs sell.** A single `maxOfferAge` currently
   governs when *any* stale live offer is cancelled. Split it so buys and sells can
   age out on different schedules (e.g. cancel an unfilled buy sooner, give a sell
   longer to find a buyer).

2. **Smaller, scrollable config section → larger trade-history table.** The stacked
   label-above-input config section is tall and pushes the trade-history table down
   to a single visible row. Cap the config section's height and make it scrollable so
   the trade-history table can show several rows at once.

## Decisions

- **Two settings:** `maxOfferAgeBuy` and `maxOfferAgeSell` (both `Duration`),
  replacing `maxOfferAge`. The engine picks the threshold by `offer.side()`.
- **Defaults (fresh install):** 30 min for both (unchanged from the prior single value).
- **Migration:** existing state files store `maxOfferAgeMinutes`. On load, that legacy
  value seeds *both* buy and sell so a current user keeps their setting. The legacy
  property must remain a recognised JSON field — Jackson fails on unknown properties
  here and `StateStore.load()` swallows that into an empty state, which would wipe the
  user's ledger/history/profit, not just the offer-age value.
- **UI:** the config section goes into a height-capped `JScrollPane`; the
  stats+history `CENTER` region absorbs the freed space. No change to the config
  fields beyond replacing the one age field with two.

## Acceptance

- Engine cancels a stale BUY by `maxOfferAgeBuy` and a stale SELL by `maxOfferAgeSell`,
  independently (unit test with divergent thresholds).
- A legacy state file with `maxOfferAgeMinutes` round-trips: both new durations equal
  the legacy value; the rest of the state is preserved.
- Panel exposes both fields, parses them, and builds a config with the two durations.
- Full suite + `fatJar` green; live visual check that the config scrolls and the
  history table shows multiple rows.
