# Post-Phase-3 Maintenance & Features
# Spec (running)

**Branch/PR:** `flipper-tech-debt` / PR #8
**Status:** Executing — decisions recorded as they are made.

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
