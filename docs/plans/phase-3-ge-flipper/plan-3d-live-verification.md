# Phase 3 — GE Flipper · Step 3d (live Echo verification)
# Implementation Plan

**Date:** 2026-06-12
**Spec reference:** [`spec-3d-live-verification.md`](./spec-3d-live-verification.md) (approved)
**Branch:** `phase-3d-live-verification` (off `main`)
**Status:** Draft — awaiting confirmation

---

## Sub-phase 3d.1 — F2P filter (code, TDD)

1. **`FlipConfig.membersItemsAllowed`** — new boolean field + builder method, default
   `true` (preserves all current behavior and tests).
   File: `libraries/core/src/main/java/com/osrsscripts/core/model/FlipConfig.java`.
2. **Failing test first** in `FlipScannerTest`: a members item with a profitable margin is
   excluded when `membersItemsAllowed = false`, included when `true`.
3. **`FlipScanner`** — skip candidates whose `ItemMeta.members()` is true when the config
   disallows them.
4. **`FlipperPanel`** — add a "Buy members items" `JCheckBox` (between the numeric fields
   and Apply); wire into `parseFields()`/`initialValue` equivalents. Headless test: uncheck
   → applied config has `membersItemsAllowed == false`.
5. Full suites green; commit.

**Acceptance:** `:libraries:core:test` + `:scripts:ge-flipper:test` green; default behavior
unchanged.

## Sub-phase 3d.2 — Live verification checklist document

Write `docs/plans/phase-3-ge-flipper/checklist-3d.md`: the spec §4 items expanded into
exact user steps with, per item: the command/click sequence, the expected observation, what
to capture (screenshot/log line/file path), and the fallback if it fails. Includes the
recommended live config for a 100k–1M F2P stack (capitalCap = stack, perItemCapitalCap
~25% of stack, maxSlots 4, membersItemsAllowed off, maxOfferAge 30 min — dropped to 2 min
only for the staleness test).

**Acceptance:** checklist reviewed by the user before the session.

## Sub-phase 3d.3 — Live session (user drives, Claude supports)

Work through the checklist. For each finding: root-cause diagnosis first, fix with
regression test where the fix is code, re-deploy (`deployLocally`), re-verify the failed
item. Record outcomes (incl. resolved settings path, CLI resolution answer, stop-signal
behavior) as they happen.

**Acceptance:** spec items 1, 3, 4, 5, 8 pass; 2, 6, 7, 9 recorded.

## Sub-phase 3d.4 — Handoff + PR

`handoff-3d-live-verification.md` (mandatory sections), README touch-up if the run story
changed, `/code-review max` if code changed beyond 3d.1's reviewed commit, PR to `main`.

## Dependencies

3d.1 → 3d.2 (checklist references the new checkbox) → 3d.3 → 3d.4. Nothing parallel.

## Out of scope (per spec)

Sell-exit escalation, proxies/bulk-launch, members worlds, performance tuning.
