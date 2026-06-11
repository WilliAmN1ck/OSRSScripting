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
- `scripts/*` — individual scripts (SDK-coupled). Added once the TRiBot SDK is
  available.

## Build

    ./gradlew build                 # compile + test everything

Requires JDK 11 (provisioned via Gradle toolchains).

## Planning

Phase specs, plans, and handoffs live under `docs/plans/`.
