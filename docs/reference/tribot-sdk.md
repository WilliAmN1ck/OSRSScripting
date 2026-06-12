# TRiBot SDK — Development Reference

How TRiBot scripts are built and what the SDK provides, from researching the
[TribotRS GitHub org](https://github.com/TribotRS) and the API docs at
<https://docs.tribot.org/tribotsdk/>. This is the source of truth for Phase 3
(the GE flipper). It is reference material, not a phase plan.

---

## Repositories (github.com/TribotRS)

| Repo | Role |
|---|---|
| [`automation-sdk`](https://github.com/TribotRS/automation-sdk) | The context-based API (`org.tribot.automation.*`): `TribotScript`, `ScriptContext`. Kotlin. **Primary.** |
| [`tribot-dev-plugin`](https://github.com/TribotRS/tribot-dev-plugin) | The `org.tribot.dev` Gradle plugin — wires the SDKs and the `fatJar`/`deployLocally` tasks. **Primary.** |
| [`Tribot-Community-Automations`](https://github.com/TribotRS/Tribot-Community-Automations) | Worked examples (the cam-torum miner, etc.). |
| `Runescape-Web-Walker-Engine` | Legacy walker, superseded by the SDK's DentistWalker addon. |
| `tribot-script-template`, `tribot-gradle-plugin` | Older template + plugin, superseded by the two primaries. |
| `tribot-client-automation` | Client internals; no GE API here. |
| `jvm-explorer` | JVM-inspection tool. Not relevant. |

The GE API lives in a **Script SDK** (`org.tribot.script.sdk`, documented at
docs.tribot.org but not in a public repo); the dev plugin provides it as
`compileOnly` by default.

---

## Build & deploy (`org.tribot.dev` plugin)

**`settings.gradle.kts`** — redirect the plugin id to JitPack (no published marker):

```kotlin
pluginManagement {
    repositories { gradlePluginPortal(); maven("https://jitpack.io") }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.tribot.dev") {
                useModule("com.github.TribotRS.tribot-dev-plugin:plugin:${requested.version}")
            }
        }
    }
}
```

**Script module `build.gradle.kts`:**

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    id("org.tribot.dev") version "latest.release"
}
repositories { mavenCentral(); maven("https://repo.runelite.net"); maven("https://jitpack.io") }
tribot {
    // useScriptSdk = true (default) -> provides the GrandExchange API
    // useLegacyApi = false, useCompose = true, useJavaFx = true
    scripts {
        register("main") {
            className = "com.osrsscripts.geflipper.GeFlipperScript"
            scriptName = "GE Flipper"; version = "1.0.0"; author = "..."; category = "Money"
        }
    }
}
dependencies {
    bundled(project(":libraries:core")) // shared lib goes into the fat JAR
}
```

- The plugin adds **as `compileOnly`** automatically: `automation-sdk`, `script-sdk`,
  `legacy-api`, `runelite-client`, okhttp, **gson**, Guava, Caffeine, JavaFX, Compose,
  and more. No manual SDK deps, no auth, no subscription.
- `bundled(...)` = the only things packed into the fat JAR (your classes + bundled libs).
- Tasks: **`fatJar`**, **`deployLocally`** (→ automations dir), `generateManifest`
  (writes `echo-scripts.json`), `zipSources` (for repo-compiler upload).
- Automations dir (Windows): `%APPDATA%/.tribot/automations`.
- **JDK 21**, Kotlin **2.1.21**. `latest.release` keeps the compile-time API in lockstep
  with Echo's runtime.
- **The Gradle daemon must run on JDK 21+** — the plugin rejects older JVMs ("Dependency
  requires at least JVM runtime version 21"). This repo pins it via
  `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`), so the wrapper auto-selects a
  JDK 21 daemon regardless of `JAVA_HOME` (a JDK 21 must be installed/discoverable).

---

## Programming model (`automation-sdk`)

```kotlin
interface TribotScript { fun execute(context: ScriptContext) }
```

A script implements `TribotScript` (Java: `implements TribotScript`). `execute` runs the
whole script (its own loop). `ScriptContext` is the API handle, exposing (among others):

- RuneLite: `client`, `clientRaw`, `clientThread`
- input: `mouse`, `keyboard`, `interaction`, `chooseOption`
- core: `banking`, `inventory`, `equipment`, `tabs`, `enterAmount`, `login`, `camera`, `minimap`
- skills/combat: `skills`, `prayer`, `combat`, `magic`
- world: `worldViews`, `worldCache`, `navigation`
- client/UI: `window`, **`sidebar`**, `screen`, `runtime`, `scripts`, **`sidecars`** (`BreakHandler`, `LoginHandler`)
- misc: `events`, `waiting`, `gameCache`, **`permissions`**, `automation`, `logger`

### What the SDK provides that we don't need to build

- **Config GUI:** `context.sidebar` (+ `useCompose`/`useJavaFx`).
- **Breaks & login:** `context.sidecars` (`BreakHandler`, `LoginHandler`) — makes our
  `core.humanize.BreakScheduler` **optional**.
- **Mouse humanization:** `context.mouse` / `MouseSettings`.
- **Walking:** `context.navigation` + the DentistWalker addon (future scripts).
- **Permissions:** `context.permissions` / `ScriptPermission` — scripts request permissions.

---

## Grand Exchange API (`org.tribot.script.sdk`)

> The Automation SDK has **no** GE API; it lives in the Script SDK (wired by default).

`GrandExchange`:
- `open(): Boolean`, `close(): Boolean`, `isOpen(): Boolean`, `isNearby(): Boolean`
- `placeOffer(config: CreateOfferConfig): Boolean`
- `abort(slot: GrandExchangeOffer.Slot): Boolean`
- `collectAll(): Boolean`, `collectAll(method: CollectMethod): Boolean` — `CollectMethod = {INVENTORY, BANK}`

`GrandExchangeOffer` (`org.tribot.script.sdk.types`) — read offer state:
`getItemId(): Int`, `getItemName(): String`, `getPrice(): Int`, `getTotalQuantity(): Int`,
`getTransferredItemQuantity(): Int`, `getTransferredGoldQuantity(): Int`,
`getCollectableItemQuantity(): Int`, `getCollectableGoldQuantity(): Int`,
`getPercentComplete(): Double`, `getSlot(): Slot`, `getStatus(): Status`, `getType(): Type`.
- `Status = {EMPTY, IN_PROGRESS, COMPLETED, CANCELLED}`
- `Type = {BUY, SELL}`
- `Slot` = the 8 GE slots
- Read offers via `GrandExchangeOfferQuery`.

`CreateOfferConfig` carries: offer type (buy/sell), item (id or name/search text), price,
quantity, optional slot. (Exact builder shape — constructor vs DSL — confirm in IntelliJ
with the plugin applied.)

---

## Model → SDK mapping (the `FlipActionExecutor`)

| `libraries/core` | Script SDK |
|---|---|
| `AccountState.offers` | `GrandExchangeOfferQuery` → `List<GrandExchangeOffer>` |
| `GeOffer` slot/status/side/item/price/qty/filled | `getSlot` / `getStatus` / `getType` / `getItemId` / `getPrice` / `getTotalQuantity` / `getTransferredItemQuantity` |
| `OfferStatus` EMPTY / ACTIVE / PARTIAL / COMPLETE / CANCELLED | `Status` EMPTY / IN_PROGRESS(0 transferred) / IN_PROGRESS(partial) / COMPLETED / CANCELLED |
| `OfferSide` BUY/SELL | `Type` BUY/SELL |
| `FlipAction.PLACE_BUY` / `PLACE_SELL` | `GrandExchange.placeOffer(CreateOfferConfig{type,item,price,qty})` |
| `FlipAction.COLLECT` | `GrandExchange.collectAll(BANK\|INVENTORY)` |
| `FlipAction.CANCEL(slot)` | `GrandExchange.abort(slot)` |
| `AccountState.cash` | coins via the SDK (inventory/bank — confirm exact call) |

Adaptations (no change needed in the pure engine):
- `placeOffer` **picks its own slot**, so the executor ignores the slot our engine assigns
  to new offers; real slots are only used for `abort` and for reading state.
- The SDK's 4-state `Status` collapses ACTIVE/PARTIAL into `IN_PROGRESS`; re-derive PARTIAL
  from `getTransferredItemQuantity() > 0`.
- Our per-slot `COLLECT` maps to a single `collectAll(...)` when any slot is collectable.

---

## Gotchas

- **gson, not Jackson.** Echo provides gson on the runtime classpath but **not** Jackson.
  `libraries/core` uses `jackson-databind` (`WikiPriceClient`, `StateStore`). At runtime in
  Echo we must either `bundled("com.fasterxml.jackson.core:jackson-databind:…")` (bloats the
  fat JAR) or **migrate those two classes to gson** (provided, lean). Recommended: gson.
- `java.net.http.HttpClient` (our price client) is fine on JDK 21; okhttp is also provided
  if preferred.
- `bundled` vs `implementation`: a 3rd-party lib declared `implementation` compiles but is
  **not** in the fat JAR → `ClassNotFoundException` at runtime. Use `bundled` for anything
  Echo doesn't already provide.

---

## Phase 3 shape (informed by the above)

`scripts/ge-flipper` → `GeFlipperScript : TribotScript`. In `execute()`:
1. Read GE offers (`GrandExchangeOfferQuery`) + coins → build `AccountState`.
2. `WikiPriceClient` → market; `FlipScanner` → candidates; `FlipEngine.decide(...)` → `FlipAction`s.
3. `FlipActionExecutor` runs each action via `GrandExchange` (thin calls).
4. Config via `context.sidebar`; breaks via `context.sidecars`; persist the buy-limit ledger via `StateStore`.

Run/test needs a local **TRiBot Echo** install (free; confirm whether a login is required).

---

## Live testing via the TRiBot CLI (for 3d)

From <https://tribot.org/learn/guides/tribot-cli> (researched 2026-06-12): a standalone
command-line launcher (`tribot.exe` on Windows; download from the TRiBot Downloads page,
extract anywhere). **Prerequisite:** log into the TRiBot Launcher at least once first — the
CLI reuses its saved credentials.

The repeatable verification loop:

    .\gradlew.bat :scripts:ge-flipper:deployLocally     # fat JAR -> %APPDATA%/.tribot/automations
    .\tribot.exe run --script-name "GE Flipper" `
        --jagex-character-name "<character>" --world <world> `
        --break-profile-name "<profile>" --heap-mb 1024

Useful `run` flags:

| Flag | Use for 3d |
|---|---|
| `--script-name` | Loads the script by its registered `scriptName` ("GE Flipper"). |
| `--break-profile-name` | Deterministic `BreakIdleTask` testing: a profile with a near-immediate short break exercises the pause while offers keep filling. |
| `--jagex-character-name` / `--jagex-character-id` | Saved-account selection; `--legacy-username` + `--legacy-password-raw` bypass the account manager. |
| `--world` | Pin a quiet world for consistent runs. |
| `--heap-mb`, `--fps-limit` (1–50), `--minimized` | Lean unattended soak runs. |
| `--script-args` | Unused (our config is the sidebar panel); future option for launch presets. |

Also available: `bulk-launch` (CSV-driven multi-client — not needed, single account),
`accounts`/`proxies` (CSV import/export), `--no-update`, `--version`.

**To confirm in 3d:**
- Whether `--script-name` resolves **local automations** from `%APPDATA%/.tribot/automations`
  (our `deployLocally` output) or only repository scripts. If local resolves, the whole
  verification loop is scriptable end to end.
- The CLI does not answer the open stop-signal question (how a script stop maps to thread
  interruption — the shutdown save depends on it); exercise stopping manually in-client.
