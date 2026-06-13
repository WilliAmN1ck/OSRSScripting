# Phase 3 — GE Flipper · Step 3d (live Echo verification)
# Spec

**Date:** 2026-06-12
**Status:** Draft — awaiting confirmation
**Inputs:** [3c handoff](./handoff-3c-config-persistence.md) (carried verification items),
[SDK reference — CLI section](../../reference/tribot-sdk.md), Q&A decisions below.

---

## 1. Goal

Prove the flipper works against the real client: the script loads in Echo, the sidebar
configures it, state persists where expected, breaks pause it, staleness cancels offers,
shutdown saves, and at least one complete buy → sell → profit cycle executes on the GE.
This is the Phase 3 acceptance gate ("flipper runs a real cycle").

## 2. Decisions (Q&A, 2026-06-12)

| # | Question | Decision |
|---|---|---|
| 1 | Environment | TRiBot Launcher installed and logged in. CLI download happens during 3d if the CLI route is used. |
| 2 | Account | **Throwaway/test account** — soak runs acceptable; no main-account caution constraints. |
| 3 | Capital / membership | **F2P, ~100k–1M cash stack.** Live config caps capital at the actual stack. |
| 4 | Driver | **User drives, Claude supports**: Claude produces the step-by-step checklist with expected results per step; the user runs the client and reports output/screenshots; findings get diagnosed and fixed at root, with regression tests where code changes. |

## 3. Code prerequisite found during spec: F2P item filter

`ItemMeta.members` is parsed from the wiki mapping but **`FlipScanner` ignores it**. On an
F2P account, members items (most high-volume flips) would be ranked and bought; the GE
would reject the offer, and the engine would retry the same buy every tick — stalling the
whole live test.

Required change (TDD, before the live run):
- `FlipConfig.membersItemsAllowed` (boolean; default `true` to preserve current behavior).
- `FlipScanner` skips members items when `membersItemsAllowed` is false.
- `FlipperPanel` gains a checkbox for it.

## 4. Verification checklist (the substance of 3d)

Run on the throwaway F2P account, user driving:

1. **Deploy + load** — `deployLocally` puts `ge-flipper.jar` in
   `%APPDATA%/.tribot/automations`; the script appears in Echo and starts. *(Phase 3
   "validation-first checkpoint", finally executed live.)*
2. **CLI resolution** — whether `tribot run --script-name "GE Flipper"` resolves the local
   automation (open question from the CLI reference). If yes, the loop is scriptable;
   if no, GUI launch is the documented path.
3. **Sidebar** — tab renders; stats update ~2 s; invalid config input shows the error and
   keeps the running config; a valid edit (e.g. capital cap) takes effect next tick.
4. **Persistence** — `ge-flipper-state.json` appears under
   `ScriptSettings.getDefault().getDirectory()` after the first action tick (record the
   actual resolved path in the handoff); restart the script and confirm ledger/profit/
   stamps survive (stats show all-time profit; re-attached offers keep their age).
5. **Flip cycle** — with F2P-filtered candidates and capital ≤ stack: a buy places, fills,
   collects; the sell places at wiki high; on sell completion, profit/flips update and
   only flipper-bought stock was ever offered.
6. **Staleness** — set `maxOfferAge` low (e.g. 2 min) via the sidebar; confirm an unfilled
   offer cancels, collects, and relists at the current price.
7. **Breaks** — break profile with a near-immediate short break (via CLI
   `--break-profile-name` or the client UI); flipping pauses (no new actions), open offers
   keep filling, and collection resumes after the break.
8. **Stop signal** — stop the script from the client; confirm the `finally` save runs
   (state file mtime updates) and the sidebar tab is removed. This resolves the
   "interruption assumed" tech-debt item from 3b/3c.
9. **Soak (optional, account is expendable)** — 1–2 h attended-ish run; watch for stuck
   states, log noise, profit drift vs. expectation.

## 5. Acceptance criteria

- Items 1, 3, 4, 5, 8 pass — these are the Phase 3 gate. Items 2, 6, 7, 9 are recorded
  with outcomes (pass/fail/deferred) in the handoff.
- The F2P filter lands with tests before the live run; any defects found live are fixed at
  root with regression tests where the fix is code.
- Handoff `handoff-3d-live-verification.md` records: resolved settings path, CLI
  resolution answer, stop-signal behavior, and any defects found/fixed.

## 6. Out of scope

Sell-exit escalation (decision 9 of 3c — revisit *after* the soak data), proxy/bulk-launch
CLI features, members-world testing, performance tuning beyond what the soak surfaces.

## 7. Addendum — features added during 3d (user request, Q&A 2026-06-12)

| # | Question | Decision |
|---|---|---|
| A1 | Idle behavior when all slots are committed | **Close GE + light fidgets**: after 5 consecutive idle ticks the GE closes; humanized fidgets (camera drift / side-tab glance) fire at randomized 15–45 s intervals via `core.humanize.DelayDistribution`; the SDK's Script AI Antiban is enabled at startup. Full activity simulation (wandering etc.) rejected as disproportionate. |
| A2 | "Prioritize best items" meaning | **Min-deployment floor** (`FlipConfig.minDeploymentGp`, sidebar field, default 1,000 gp on fresh installs / 0 on restored old configs): a buy below the floor never takes a slot. **Sells preempt weak buys**: when stock waits to sell and no slot is free, the live buy with the smallest remaining gp commitment is cancelled (one per tick). Capital concentration declined (already current behavior). |
| A3 | Delivery | Same PR (#7), on `phase-3d-live-verification`. |

Note: A1 deliberately goes beyond 3c decision 8 (which still holds for *sidecars*) — the
Script AI Antiban flag is a different toggle and enabling it is the point of the feature.

## 8. Risk note

Botting violates Jagex's rules; the account is expendable by decision 2. Keep the cash
stack at what you're willing to lose to a ban mid-flip (open offers are recoverable via
the GE on any client, but a ban is not).
