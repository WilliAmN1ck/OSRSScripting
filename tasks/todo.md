# Tasks

## Phase 1 — Repository Scaffold  ✅ COMPLETE (2026-06-11)
- [x] Gradle wrapper, settings/build/properties, .gitignore, README, CI
- [x] Verified green locally + on GitHub Actions; committed + pushed

## Phase 2 — Core Library: pure logic  ✅ COMPLETE (2026-06-11)
- [x] 2.0 libraries/core/build.gradle.kts (java-library, Jackson, JUnit 5)
- [x] 2.1 Domain models (ItemMeta, PricePoint, VolumePoint, GeOffer, AccountState, FlipConfig, FlipCandidate, OfferSide/Status)
- [x] 2.2 OSRS Wiki prices client (HttpFetcher, WikiHttpFetcher, WikiPriceClient) + caching
- [x] 2.3 GE tax calculator (GeTaxRules, GeTax)
- [x] 2.4 Buy-limit tracker (BuyLimitLedger)
- [x] 2.5 Flip scanner / ranking (FlipScanner)
- [x] 2.6 Flip decision engine (FlipEngine, FlipAction, ActionType)
- [x] 2.7 Persistence (PersistedState, LedgerEntry, StateStore)
- [x] 2.8 Humanization (DelayDistribution, BreakScheduler)
- [x] 2.9 Task / state-machine framework (Task, TaskRunner)
- [x] 35 tests, all green (`:libraries:core:test`)

## Phase 3 prep — branch `phase-3-prep` (2026-06-11)
- [x] Confirmed development is FREE — SDK via the `org.tribot.dev` plugin (JitPack), no subscription
- [x] Re-baselined JDK 11 → 21 (build.gradle.kts, CI, README); verified 35 tests green on 21
- [x] Updated spec.md (decisions + Open Items) and plan.md (Phase 3 unblocked, real `TribotScript` API, fatJar/deployLocally)

## Phase 3 research — branch `phase-3-research` (2026-06-11)
- [x] Mapped the TribotRS repos + SDK docs; captured in docs/reference/tribot-sdk.md
- [x] GE API found in the Script SDK (org.tribot.script.sdk.GrandExchange) — executor is thin
- [x] GUI = ScriptContext.sidebar; breaks/login = ScriptContext.sidecars; flagged gson-vs-Jackson
- [x] Marked spec §5 Open Items resolved

## Phase 3 — SDK integration & GE flipper  (dev UNBLOCKED; live run needs local TRiBot Echo)
- [x] 3a Module skeleton: ge-flipper wired to org.tribot.dev + trivial TribotScript; build + fatJar verified (daemon on JDK 21)
- [x] 3b GeClient port + SdkGeClient adapter + FlipActionExecutor; FlipEngine wired via TaskRunner (EnsureGeOpenTask + FlipTask) in execute(); 13 tests green, build + fatJar verified
- [ ] 3c Config UI + persistence + breaks — branch `phase-3c-config-persistence`; spec + plan approved (docs/plans/phase-3-ge-flipper/{spec,plan}-3c-config-persistence.md)
  - [x] 3c.1 StockLedger (core, TDD) — 5 tests green
  - [x] 3c.2 OfferTracker (core, TDD) — 9 tests green; fixes never-called recordPurchase
  - [x] 3c.3 PersistedState v2 (stockEntries + offerStamps; v1-compat) + StateMapper
  - [x] 3c.4 FlipTask integration (stamped offers, stock filter, Supplier<FlipConfig>, per-tick persist) — 8 tests green
  - [x] 3c.5 Break guard (BreakSource port, BreakIdleTask, SdkBreakSource) — 2 tests green
  - [x] 3c.6 FlipperPanel (Swing) + StatsSnapshot + GeFlipperScript composition root (ScriptSettings dir, load/save, sidebar tab, break sidecar); fixed fatJar↔core:jar missing task dependency
  - [x] 3c.7 Full verification green; /code-review max (3 findings fixed w/ regression tests); README updated; handoff written (docs/plans/phase-3-ge-flipper/handoff-3c-config-persistence.md); PR open
- [ ] 3d Validation-first: load/run trivial script in Echo
- Reference: docs/reference/tribot-sdk.md ; handoff: docs/plans/phase-3-ge-flipper/handoff.md
## Phase 4 — Publish / distribution  (later / optional)
