# Lessons

Patterns learned from corrections, per CLAUDE.md. Each entry: **Trigger → Rule.** Review at
session start.

## Domain facts

- **Verify item facts against the OSRS wiki, not from memory.** Trigger: asserted "Zamorak armour
  is members"; the user pushed back; the wiki `mapping` endpoint reported `members=False`. The
  scanner reads each item's `members`/`limit` flag from the wiki — so reason from that data, not
  from recalled game knowledge. When an item *isn't* being traded, check the wiki for its
  membership, buy limit, recent price, and 1h volume before concluding why.

- **Investigate before defending an assumption.** Trigger: the user said "Zamorak sets are F2P
  though." The right move was to pull the live wiki data, not restate the guess. The data showed
  the real blocker was hourly volume (liquidity), not membership.

## Design judgement

- **Re-examine an approved design when evidence contradicts it; don't ship a no-op.** Trigger:
  same-item reuse (opening multiple concurrent offers on one item) was approved to "use more
  slots", but a single offer already maxes the per-item cap and the 4h buy limit (both totals), so
  reuse deploys zero extra gp and only creates dust offers. Surfaced the contradiction and
  re-confirmed with the user → dropped reuse, kept the diagnostic.

- **Prefer a visibility feature over an automated "fix" when the real levers are user config.**
  Trigger: idle GE slots / unused cash. Instead of auto-relaxing filters, the engine reports *why*
  (`IdleReason`) and the sidebar names the setting to adjust. The user stays in control.

- **A ranking optimises exactly what it scores.** Trigger: the scanner ranked by profit-per-cycle
  and never deployed capital, because expensive items have small buy limits. To make a big bankroll
  buy expensive items, capital deployed had to become the *primary* ranking key.

## Process

- **Edit the live run config in the state file while the script is stopped**, not by typing into
  the sidebar mid-run. An earlier session corrupted `capitalCap` (`50006000000`) via a panel edit
  on a clipped field; file edits while stopped are deterministic. (Fields are full-width now, but
  the file path is still the safe one for scripted edits.)

- **Match the established doc lifecycle.** Every merged PR gets a handoff under
  `docs/plans/...`; running spec files get their status flipped to Complete with handoff links.
