# Phase 0 — Workspace Setup
# Specification Document

**Date:** 2026-06-11
**Branch:** main
**Status:** Spec — pending confirmation (no implementation until spec + plan approved)

> Supersedes the Copilot-generated drafts now archived in
> `docs/plans/_archive-copilot-drafts/`. Those drafts assumed the **retired**
> Legacy-Java-Client model (fat-JAR + `main()` + `~/.tribot/automations` +
> hand-rolled `tribot-script.json`) and predate both the Echo pivot and the
> GE-flipper decision. They are kept for history only.

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
- Developing/running local scripts requires a **paid TRiBot subscription**
  (Premium or Ultimate). IntelliJ IDEA is the recommended IDE.

Sources: [Detuks — Java OSRS bot era is over](https://detuks.com/blog/java-osrs-bot-era-is-over-2926-overlook),
[TRiBot Developer Overview](https://tribot.org/learn/dev/developer-overview),
[TRiBot script template](https://github.com/TribotRS/tribot-script-template).

---

## 2. Confirmed Decisions

| Area | Decision |
|---|---|
| **API target** | TRiBot **Automation SDK** (Echo-first) |
| **Language** | Java (version pinned to the Echo SDK requirement — see Open Items) |
| **Build system** | Gradle, **Kotlin DSL**, multi-module |
| **Repo layout** | Mirror the official TRiBot template: `scripts/<name>` modules + shared `libraries/<name>` |
| **Packaging** | Official Gradle tasks (`build`, `cleanBin`, `repoPackage`/`repoUpdate`) — **no** fat-JARs, **no** `~/.tribot/automations` copies |
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
- **RuneLite side panel** to configure capital, margin/volume thresholds, slot
  usage, etc. (Fallback to Swing if the SDK doesn't expose a panel hook —
  see Open Items.)
- **Persisted state on disk** so the script resumes open offers and buy-limit
  timers after a restart or crash.

### Scope
- **Single account.** No multi-account coordination or muling.

---

## 5. Open Items — verify against the real SDK at setup

These are **not decisions** but unknowns that cannot be closed until a paid
subscription is active and the actual template/SDK jar is in hand (the user has
nothing set up yet, and the paid SDK docs are not publicly readable):

1. **Automation SDK entry point** — the real script class / annotation /
   lifecycle (replaces the invented `main()` + `tribot-script.json`).
2. **RuneLite side-panel hook** — whether the SDK exposes one for script GUIs;
   if not, fall back to Swing/JavaFX.
3. **JDK version** — template references Java 11; old draft said 17; the Echo
   SDK is authoritative. Pin once confirmed.
4. **Current Gradle plugin coordinates / task names** for the Echo template.

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
