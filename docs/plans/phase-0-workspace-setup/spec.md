# Phase 0 — Workspace Setup
# Specification Document

**Date:** 2026-06-11 (revised on `phase-3-prep`)
**Branch:** main
**Status:** Approved; Phases 1–2 complete. Revised to correct SDK/JDK/subscription facts ahead of Phase 3.

> Supersedes the Copilot-generated drafts now archived in
> `docs/plans/_archive-copilot-drafts/`. Those drafts predate the GE-flipper
> decision and got the entry point wrong (`main()` + a hand-rolled
> `tribot-script.json`), but were actually **right** that scripts deploy as
> fat JARs into the `.tribot/automations` directory. Kept for history.

---

## 1. Project Overview

A **closed-source, private** monorepo of Old School RuneScape (OSRS) automation
scripts written in **Java**, targeting **TRiBot's Automation SDK**.

### Critical platform context (changed Jan 2026)

- **2026-01-28:** Jagex retired the **Legacy Java Client** that TRiBot, DreamBot,
  OSBot, and RuneMate all ran on.
- TRiBot pivoted to **Echo** — a plugin that hosts the TRiBot scripting engine
  **inside RuneLite**. Scripts run within RuneLite via Echo, **not** a standalone
  TRiBot `.exe`.
- TRiBot now offers two APIs:
  - **Automation SDK** (Echo-first, 2026, actively developed, non-static,
    lower-level, exposes the full RuneLite API) — **our target**.
  - **Script SDK** (legacy, static, easier, **no longer actively developed**).
- **Developing/building scripts is free** and needs only **JDK 21** — the SDK is
  provided by the `org.tribot.dev` Gradle plugin (JitPack); no subscription,
  account, or token. A local **TRiBot Echo** install is needed only to *run* what
  you build, and no subscription is documented as required to run either (confirm
  on install). IntelliJ IDEA is the recommended IDE.

> **Correction (2026-06-11):** an earlier revision of this spec said a paid
> subscription was required to develop — wrong, sourced from one overview page.
> The official IntelliJ setup guide and the community automations repo confirm
> building is free and needs no account.

Sources: [TRiBot IntelliJ project setup](https://tribot.org/learn/dev/intellij-project-setup),
[Tribot-Community-Automations](https://github.com/TribotRS/Tribot-Community-Automations),
[Detuks — Java OSRS bot era is over](https://detuks.com/blog/java-osrs-bot-era-is-over-2926-overlook).

---

## 2. Confirmed Decisions

| Area | Decision |
|---|---|
| **API target** | TRiBot **Automation SDK** — `org.tribot.automation.TribotScript` + `ScriptContext` |
| **SDK access** | Free via `id("org.tribot.dev")` Gradle plugin (JitPack); it supplies Automation SDK + Script SDK + RuneLite as `compileOnly`. No manual deps, no auth. |
| **Language** | Java on **JDK 21** (hard requirement — Echo loads only JDK 21 class files) |
| **Build system** | Gradle, **Kotlin DSL**, multi-module |
| **Repo layout** | `scripts/<name>` modules + shared `libraries/<name>` (validated by the community repo's `community-commons` + per-script modules) |
| **Packaging** | `org.tribot.dev` tasks: `fatJar` + `deployLocally` → fat JAR into `%APPDATA%/.tribot/automations`; the plugin auto-generates the manifest |
| **First & only script** | **Grand Exchange flipper**, single account |
| **Dropped from old draft** | Miner, Lumbridge cow/chicken killer |
| **Architecture** | **Task / state-machine framework** as the backbone every script uses |
| **Account safety** | Humanization + break scheduling built in **from day one** as core infra |
| **Distribution** | **Private + local now**; design the `repoPackage` publish pipeline so we *can* publish to the TRiBot repo later. No RWT features. |
| **Source model** | Closed-source, private repository |
| **CI** | GitHub Actions (build + tests) |
| **Legal** | README must carry a botting-risk warning |
| **Sequencing** | **Framework first**, ending in a trivial validation script that proves load/run in Echo, **then** the GE flipper |

---

## 3. Shared Library (`libraries/`)

Built first. Provides reusable capabilities so scripts stay thin:

- **Task / state-machine framework** — scripts are a set of prioritized
  tasks/nodes, each with a "should I run?" gate.
- **Banking** — open/close, deposit/withdraw, content checks.
- **Walking / web-walking** — navigate between locations, handle obstacles.
- **Inventory management** — counts, full-detection, keep/drop logic.
- **Anti-ban / humanization** — randomized delays, mouse/camera fidget,
  break scheduling. Used by every script, including the low-action flipper.
- **OSRS Wiki real-time prices client** — see §4.
- **Persistence** — on-disk state that survives restarts/crashes.
- **Logging** and **GUI helpers**.

> The flipper exercises banking/walking only lightly; those capabilities are for
> the project as a whole and will be fully validated by later scripts.

---

## 4. Grand Exchange Flipper — Requirements

### Price intelligence
- Source: **OSRS Wiki real-time prices API**
  (`/latest`, `/5m`, `/1h`, and `/mapping` for per-item buy limits).
- Send a **descriptive User-Agent** (API etiquette) and **cache locally** to
  respect the endpoints.

### Item selection
- **Dynamic scanner** (not a static list): ranks candidates by margin %,
  trading volume, and 4-hour buy limit.

### Trade economics (baked into margin math)
- **2% GE sell tax**, capped at **5,000,000 gp per item**, items **under 100 gp
  exempt** (plus the known exempt-item set).
- **8 GE offer slots.**
- **Per-item 4-hour buy-limit** tracking.

### Configuration & state
- **In-client config UI** for capital, margin/volume thresholds, slot usage, etc.
  The module's `tribot { }` block exposes `useCompose`/`useJavaFx` toggles; prefer
  a RuneLite side panel if exposed, else Compose/JavaFX/Swing — see Open Items.
- **Persisted state on disk** so the script resumes open offers and buy-limit
  timers after a restart or crash.

### Scope
- **Single account.** No multi-account coordination or muling.

---

## 5. Open Items

The major unknowns are now **resolved** from the official IntelliJ setup guide and
the community automations repo:

- **Entry point — RESOLVED:** a script implements `org.tribot.automation.TribotScript`
  with `void execute(ScriptContext context)`; the module's `tribot { }` block
  registers it. (Replaces the invented `main()` + `tribot-script.json`.)
- **SDK / build — RESOLVED:** `id("org.tribot.dev") version "latest.release"`
  (JitPack) supplies the SDK as `compileOnly`; build with `fatJar`, deploy with
  `deployLocally`. Repos: `mavenCentral`, `repo.runelite.net`, `jitpack.io`.
- **JDK — RESOLVED:** JDK 21.
- **Subscription — RESOLVED:** not required to develop or build.

Remaining (confirm while coding Phase 3 — none block development):

1. **Grand Exchange API surface** — the exact SDK class/methods to place, collect
   and cancel GE offers and read offer/slot state (the miner example shows
   `Inventory`, `Banking`, `Interaction`, `WorldViews`… but not GE). Confirm from
   the plugin's bundled SDK / javadoc.
2. **Config UI** — whether a RuneLite side panel is exposed, else Compose/JavaFX/Swing.
3. **Running Echo** — whether a (free) account/login is needed to *run* a script.
   Affects live testing only, not building.

---

## 6. Non-Functional Requirements

- Modular multi-module Gradle structure (Kotlin DSL).
- Shared utilities isolated in `libraries/`.
- **TDD** for all non-trivial features (game-coupled code abstracted behind
  interfaces so logic — scanner, tax math, buy-limit tracking — is unit-testable
  without a live client).
- Clear logging and configuration loading.
- Handoff document on phase completion.

---

## 7. Constraints

- No implementation before spec + plan approval.
- Phase folders created only when a phase begins.
- All scripts run under the TRiBot Automation SDK (Echo / RuneLite).
- No RWT-related features.
- Private, closed-source for now.

---

## 8. Acceptance Criteria (this phase)

- This spec is complete and approved.
- `plan.md` (next step) references every decision above and explicitly carries
  the §5 Open Items as setup blockers.
- No open *decisions* remain (Open Items are verification tasks, not decisions).
