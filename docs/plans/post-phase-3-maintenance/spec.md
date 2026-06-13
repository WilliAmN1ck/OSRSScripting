# Post-Phase-3 Maintenance & Features
# Spec (running)

**Status:** ✅ Batches 1–4 shipped and merged.

| Batch | Theme | Branch / PR | Handoff |
|---|---|---|---|
| 1 | Tech debt (profit accuracy, sell exit, members stock) | `flipper-tech-debt` / [#8](https://github.com/WilliAmN1ck/OSRSScripting/pull/8) | [handoff-tech-debt.md](./handoff-tech-debt.md) |
| 2 | Per-item trade history + auto-avoid | `flipper-tech-debt` / [#8](https://github.com/WilliAmN1ck/OSRSScripting/pull/8) | [handoff-tech-debt.md](./handoff-tech-debt.md) |
| 3 | Idle-reason diagnostics | `flipper-slot-utilization` / [#9](https://github.com/WilliAmN1ck/OSRSScripting/pull/9) | [handoff-slot-utilization.md](./handoff-slot-utilization.md) |
| 4 | Capital-aware ranking + UI clarity | `flipper-slot-utilization` / [#9](https://github.com/WilliAmN1ck/OSRSScripting/pull/9) | [handoff-slot-utilization.md](./handoff-slot-utilization.md) |

## Batch 1 — Tech debt (2026-06-12, decided via Q&A)

| Item | Decision |
|---|---|
| Profit accuracy | Account by transferred gold (SDK `getTransferredGoldQuantity`); sell gold assumed post-tax (live-verify pending). Stamps persist gold baselines with a migration for pre-upgrade files. |
| Sell exit | Insta-sell after N stale relists (`sellExitAfterRelists`, fresh default 3, restored configs 0=off). Streak counted per cancelled live sell, reset on a completed sell, in-memory. |
| Members stock | Never offered for sale when the members filter is off. |

## Batch 2 — Trade history (2026-06-12, decided via Q&A)

| Question | Decision |
|---|---|
| Selection use | **Auto-avoid losers**: items whose recorded net P/L is at or below `-avoidAfterLossGp` (new config; fresh default 1,000 gp, restored configs 0=off) are excluded from buy candidates until the history is cleared. No winner boost — margin ranking already finds winners. |
| Granularity | **Per-item aggregate**: net P/L, flips completed, quantity sold, last-traded time. No unbounded per-flip log. |
| Display | **Full scrollable table** in the sidebar (item name, net P/L, flips, qty), sorted by net P/L, plus a **Clear history** button that also resets the avoid list. |
| Persistence | Aggregates persist in `PersistedState` (v-compatible: older files load with empty history). Clearing persists on the next save. |
| Threading | The Clear button (EDT) only raises a flag; the script thread performs the clear at the next tick — no cross-thread mutation of the history. |

## Batch 3 — Idle-reason diagnostics (2026-06-13, decided via Q&A)

| Question | Decision |
|---|---|
| Multi-slot reuse | **Rejected.** Investigated opening multiple concurrent offers on one item to fill slots; a single offer already maxes the per-item cap and the 4h buy limit (both totals), so reuse deploys no extra gp — only dust offers. Buying stays one offer per item. |
| Thin market | **Stay idle + show why.** Don't auto-relax filters; instead surface *why* slots sit idle so the user can adjust the binding setting. |
| Mechanism | `FlipEngine.plan()` returns `FlipPlan(actions, IdleReason)`; `IdleReason` {NONE, MAX_SLOTS, CAPITAL_CAP, PER_ITEM_CAP, NO_CANDIDATES}. Only config-driven causes report (out-of-gold / buy-limit = NONE). Surfaced as an amber sidebar advisory naming the setting. |

## Batch 4 — Capital-aware ranking + UI clarity (2026-06-13, decided via Q&A)

| Question | Decision |
|---|---|
| Why small flips? | The scanner ranked by profit-per-cycle, never capital. Expensive items have small buy limits → low throughput → ranked below cheap commodities. Capital prioritization was never implemented (git-confirmed). |
| Fix | **Change the ranking (code).** Rank by capital deployed per offer — `buyPrice × min(buyLimit, volume, perItemCap/buyPrice)`, capped at the per-item cap — tie-broken by profit-per-cycle. |
| UI clarity | Relabel opaque fields: "Max spend per item (gp)", "Min ROI (%)" (entered as a real percent, type 2 for 2%; stored as a fraction), "Min hourly volume (units)", "Min spend per buy (gp)". |
| Reachability note | The 100k–900k price band on F2P is gated by **liquidity (hourly volume)**, not item membership — confirmed against the OSRS wiki. Deploying ~1–2M/slot needs a per-item cap raised to match and items with enough volume to fill.
