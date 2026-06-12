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

## Phase 3 — SDK integration & GE flipper  (dev UNBLOCKED; live run needs local TRiBot Echo)
- TribotScript/ScriptContext entry point; FlipActionExecutor over the SDK GE API; config UI; deploy via fatJar/deployLocally
## Phase 4 — Publish / distribution  (later / optional)
