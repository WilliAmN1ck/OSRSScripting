# osrs-scripts-suite

Closed-source, modular Java monorepo of Old School RuneScape (OSRS) automation
scripts targeting TRiBot's **Automation SDK** (Echo / RuneLite).

## ⚠️ Warning

Using automation clients violates Jagex's rules and may result in account bans.
This software is provided for educational purposes only; use at your own risk.

## Structure

- `libraries/core` — shared, SDK-independent logic (task framework, OSRS Wiki
  price client, GE tax / buy-limit / scanner, flip decision engine, persistence,
  humanization). Fully unit-tested without a game client.
- `scripts/ge-flipper` — Grand Exchange flipper (SDK-coupled). A `TaskRunner`
  loop drives the core flip engine against the live client; a Swing sidebar tab
  edits the run config and shows live stats (profit/hr, capital deployed in open
  buys, win/loss counts, items avoided); buy-limit/stock ledgers, offer
  placement times, and profit persist across restarts in the script-settings
  directory; flipping pauses during client-scheduled breaks.

  Candidates are scored from **averaged** prices (so a single outlier trade can't
  bait a bad item) — the fresher 5-minute averages when an item is actively trading
  both sides, else the trailing hour's — and ranked by **estimated profit per hour**
  — net margin times the units the buy limit and **balanced** (lesser of buy/sell
  side) volume can sustain — tie-broken toward the offer that deploys more capital,
  within per-item and total capital caps. An item whose 5-minute price has dropped
  sharply below the hour (a **falling knife**) is skipped for buying. Offers are
  still **placed at the live price** so they fill at market. A **per-item trade history** auto-avoids recorded
  losers, and stale sells **escalate to the insta-sell price** after N relists.
  When GE slots or cash sit idle because of a config setting, the sidebar shows an
  **advisory naming the setting to adjust**.

## Build

    ./gradlew build                          # compile + test everything
    ./gradlew :scripts:ge-flipper:fatJar     # deployable script JAR
    ./gradlew :scripts:ge-flipper:deployLocally  # -> %APPDATA%/.tribot/automations

Requires JDK 21 (provisioned via Gradle toolchains).

## Planning

Phase specs, plans, and handoffs live under `docs/plans/`.
