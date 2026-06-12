# Phase 3d — Live Verification Checklist

**Spec:** [`spec-3d-live-verification.md`](./spec-3d-live-verification.md) ·
**You drive, Claude supports.** Work top to bottom; report each item's outcome (with the
capture noted) before moving on. If an item fails, stop there — we diagnose and re-deploy
before continuing.

## Recommended live config (sidebar, after the script starts)

For a 100k–1M F2P stack (adjust capital to your actual cash):

| Field | Value | Why |
|---|---|---|
| Capital cap | your cash stack | never commits more than you hold |
| Per-item capital cap | ~25% of stack | spreads risk across slots |
| Min margin (gp) | 2 | F2P items are cheap; 5 gp filters too hard |
| Min margin (fraction) | 0.01 | 1% ROI floor |
| Min volume (units/h) | 5000 | F2P staples trade heavily; avoids dead items |
| Max GE slots | 3 | F2P accounts have 3 GE slots (members have 8) |
| Max offer age (minutes) | 30 | default; drop to 2 only for item 6 |
| Buy members items | **unchecked** | F2P account |

> Note on slots: the engine models 8 slots and `placeOffer` picks its own slot in-client,
> so with Max GE slots = 3 the flipper never asks for more than F2P provides. If you see
> repeated failed offer placements anyway, capture the offers view — that's a slot-model
> mismatch I'd need to fix.

---

## 1. Deploy + load  *(gate)*

    .\gradlew.bat :scripts:ge-flipper:deployLocally

- **Expect:** `ge-flipper.jar` (~2.3 MB) in `%APPDATA%\.tribot\automations`. Check:
  `dir $env:APPDATA\.tribot\automations`
- Launch Echo via the TRiBot Launcher, log into the test account, open the script
  list/automations UI.
- **Expect:** "GE Flipper" listed; starting it logs no errors and the client stays
  responsive.
- **Capture:** screenshot of the script list; any startup log lines mentioning the script.
- **If it fails:** copy the exact client log error — likely manifest or classpath; I'll fix
  and we re-deploy.

## 2. CLI resolution  *(record outcome)*

Download the CLI (TRiBot Downloads page), extract, then:

    .\tribot.exe run --script-name "GE Flipper" --world <quiet F2P world>

- **Expect (open question):** client launches with the script loaded. If the CLI reports
  the script as unknown, local automations don't resolve by name — record that and use GUI
  launches for the rest; not a blocker.
- **Capture:** the CLI's stdout either way.

## 3. Sidebar  *(gate)*

With the script running:

- **Expect:** a "GE Flipper" tab in the sidebar; Configuration fields prefilled with the
  defaults; Stats block updating roughly every 2 s (Runtime ticking).
- Enter `abc` in Capital cap → Apply. **Expect:** red error, config unchanged, script
  keeps running.
- Enter the recommended config above → Apply. **Expect:** error clears.
- **Capture:** screenshot of the tab after the valid apply.

## 4. Persistence  *(gate)*

- Let the script run until it places at least one offer (item 5 below), then:
  `dir $env:APPDATA\.tribot\settings -Recurse -Filter ge-flipper-state.json`
  (the exact parent dir is what `ScriptSettings` resolves — **record the real path**).
- **Expect:** the file exists and its mtime is recent; contents are JSON with
  `ledgerEntries` / `stockEntries` / `offerStamps`.
- Stop the script, restart it. **Expect:** Stats shows the prior all-time profit (not 0);
  a still-live offer keeps its age (it should cancel on schedule per its original
  placement, not 30 min after restart).
- **Capture:** the resolved path + the JSON file (paste or attach).

## 5. Flip cycle  *(gate — the Phase 3 acceptance item)*

Stand near/at the GE (Grand Exchange booth area, e.g. Varrock west bank GE).

- **Expect, in order:** GE opens if closed → a buy offer appears for a ranked F2P item at
  the wiki insta-sell price → it fills (minutes, for high-volume items) → collection →
  a sell offer at the wiki insta-buy price → on fill, Stats profit/flips increment.
- **Watch for:** any attempt to sell items the flipper didn't buy (should never happen);
  offers for members items (should never happen with the box unchecked); a slot the
  client rejects.
- **Capture:** screenshots at buy-placed, sell-placed, and after the sell completes; the
  state JSON afterwards.

## 6. Staleness  *(record outcome)*

- Sidebar: set Max offer age to `2`, Apply. Place conditions where an offer won't fill
  fast (the engine does this naturally on a slower item; or just wait for any live offer
  to age past 2 min).
- **Expect:** the offer cancels ~2 min after placement, collects, and relists at the
  current price next tick.
- Reset Max offer age to `30` afterwards.
- **Capture:** timestamps (offer placed vs cancelled) from your observation or the log.

## 7. Breaks  *(record outcome)*

- Configure a break profile in the client (or launch via CLI with
  `--break-profile-name`) that triggers a short break soon after start.
- **Expect:** when the break starts, no new flipper actions (stats Runtime keeps ticking,
  offers stay live and may fill); when it ends, collection/flipping resumes.
- **Capture:** note the break start/end and what the offers did during it.

## 8. Stop signal  *(gate)*

- Note the state file's mtime, then stop the script from the client UI.
- **Expect:** the sidebar tab disappears AND the state file mtime updates (the shutdown
  save ran). If the tab stays or the file is stale, the stop signal isn't an interrupt —
  report exactly how you stopped it and what the log says.
- **Capture:** before/after mtimes (`Get-Item <path> | Select LastWriteTime`).

## 9. Soak  *(optional)*

- 1–2 h run with the recommended config. Glance occasionally.
- **Watch for:** stuck offers that never cancel, repeated identical actions (a retry
  loop), log spam, profit going negative beyond tax noise, memory/CPU creep.
- **Capture:** final Stats screenshot + state JSON + anything odd.

---

## Reporting template (per item)

    Item N: PASS / FAIL / SKIPPED
    Observed: <one line>
    Captures: <paths/screenshots>
