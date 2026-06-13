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
  edits the run config and shows live stats; buy-limit/stock ledgers, offer
  placement times, and profit persist across restarts in the script-settings
  directory; flipping pauses during client-scheduled breaks.

  Candidates are ranked by the **capital each offer would deploy** (so a large
  bankroll buys higher-value items, not just cheap high-volume flips), within
  per-item and total capital caps. A **per-item trade history** auto-avoids
  recorded losers, and stale sells **escalate to the insta-sell price** after N
  relists. When GE slots or cash sit idle because of a config setting, the
  sidebar shows an **advisory naming the setting to adjust**.

## Build

    ./gradlew build                          # compile + test everything
    ./gradlew :scripts:ge-flipper:fatJar     # deployable script JAR
    ./gradlew :scripts:ge-flipper:deployLocally  # -> %APPDATA%/.tribot/automations

Requires JDK 21 (provisioned via Gradle toolchains).

## Planning

Phase specs, plans, and handoffs live under `docs/plans/`.
