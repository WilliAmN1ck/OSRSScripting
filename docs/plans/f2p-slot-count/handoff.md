# F2P GE slot count
# Handoff

**Date:** 2026-06-13
**Branch:** `f2p-slot-count`
**Status:** ✅ Code complete, unit-tested, reviewed. Live visual check pending.

---

## Problem

On a free-to-play world the sidebar showed the amber advisory *"GE slots sit idle — raise Max GE
slots (1-8) to use them"* even with all three F2P slots full. F2P worlds expose only **3** GE
slots; members worlds expose **8**.

## Root cause

`SdkGeClient.offers()` always padded the offer list to eight via `OfferMapper.fillEightSlots`,
regardless of world. On F2P that produced **five phantom empty slots**. The engine saw open
capacity, concluded the user's `maxSlots` was the bottleneck, and emitted `IdleReason.MAX_SLOTS` —
advising the user to raise a cap that can't help, since those five slots don't exist on F2P.

## Fix

Report the real slot count from the world, so the pure engine logic just works — **no engine or
advisory change needed**:
- `OfferMapper.fillSlots(present, slotCount)` generalises `fillEightSlots` (which now delegates with
  `MEMBERS_SLOT_COUNT = 8`); `FREE_SLOT_COUNT = 3`.
- `SdkGeClient.offers()` pads to `Worlds.getCurrent().map(World::isMembers).orElse(true) ? 8 : 3`.
  Defaults to members/8 when the world is momentarily unknown (prior behaviour).

With offers() returning three slots on F2P, a full board has no open slots, so `MAX_SLOTS` no longer
fires. The advisory still fires correctly when `maxSlots` is set *below* the world's real slot count
(e.g. 2 on F2P).

## Files Changed

| File | Change |
|---|---|
| `scripts/ge-flipper/.../OfferMapper.java` | `fillSlots(present, count)` + slot-count constants |
| `scripts/ge-flipper/.../SdkGeClient.java` | `offers()` pads to the world's real slot count |
| `scripts/ge-flipper/.../OfferMapperTest.java` | `fillSlots` honours the F2P count |
| `libraries/core/.../ge/FlipEngineTest.java` | full 3-slot F2P board does not report `MAX_SLOTS` |

## Test Coverage
`OfferMapperTest.fillSlotsHonoursTheFreeToPlaySlotCount` (3 slots, no phantom);
`FlipEngineTest.planDoesNotReportMaxSlotsWhenAFullFreeToPlayBoardHasNoOpenSlots`. The world→count
branch in `SdkGeClient` is SDK-coupled (verified by compilation + live run, like the rest of that
class). Full suite + `fatJar` green.

## Known Issues / Tech Debt
- Live visual check pending: on an F2P world with all three slots full, the "raise Max GE slots"
  advisory should no longer appear.

## Verification Commands

    .\gradlew.bat test
    .\gradlew.bat :scripts:ge-flipper:fatJar
    .\gradlew.bat :scripts:ge-flipper:deployLocally
