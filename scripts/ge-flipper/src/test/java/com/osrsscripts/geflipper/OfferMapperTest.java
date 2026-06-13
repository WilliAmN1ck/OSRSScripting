package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfferMapperTest {

    @Test
    void statusOfMapsEachSdkStatus() {
        assertEquals(OfferStatus.EMPTY, OfferMapper.statusOf("EMPTY", 0));
        assertEquals(OfferStatus.ACTIVE, OfferMapper.statusOf("IN_PROGRESS", 0));
        assertEquals(OfferStatus.PARTIAL, OfferMapper.statusOf("IN_PROGRESS", 3));
        assertEquals(OfferStatus.COMPLETE, OfferMapper.statusOf("COMPLETED", 5));
        assertEquals(OfferStatus.CANCELLED, OfferMapper.statusOf("CANCELLED", 2));
    }

    @Test
    void sideOfMapsBuyAndSell() {
        assertEquals(OfferSide.BUY, OfferMapper.sideOf("BUY"));
        assertEquals(OfferSide.SELL, OfferMapper.sideOf("SELL"));
    }

    @Test
    void toGeOfferAssemblesOfferWithoutTimestamp() {
        GeOffer offer = OfferMapper.toGeOffer(3, "IN_PROGRESS", "BUY", 1234, 100, 50, 10, 980L);

        assertEquals(3, offer.slot());
        assertEquals(OfferStatus.PARTIAL, offer.status());
        assertEquals(OfferSide.BUY, offer.side());
        assertEquals(1234, offer.itemId());
        assertEquals(100, offer.pricePerItem());
        assertEquals(50, offer.quantity());
        assertEquals(10, offer.filled());
        assertEquals(980L, offer.transferredGold(), "actual gold moved, not price x filled");
        assertNull(offer.placedAt(), "SDK exposes no placement time");
    }

    @Test
    void toGeOfferLeavesSideNullForEmptySlot() {
        GeOffer offer = OfferMapper.toGeOffer(1, "EMPTY", "BUY", 0, 0, 0, 0, 0L);

        assertEquals(OfferStatus.EMPTY, offer.status());
        assertNull(offer.side());
    }

    @Test
    void fillEightSlotsReturnsAllSlotsInOrderFillingGaps() {
        GeOffer atTwo = OfferMapper.toGeOffer(2, "IN_PROGRESS", "SELL", 7, 10, 1, 0, 0L);
        GeOffer atFive = OfferMapper.toGeOffer(5, "COMPLETED", "BUY", 9, 20, 2, 2, 40L);

        List<GeOffer> all = OfferMapper.fillEightSlots(List.of(atFive, atTwo));

        assertEquals(8, all.size());
        for (int i = 0; i < 8; i++) {
            assertEquals(i + 1, all.get(i).slot(), "slots ordered 1..8");
        }
        assertEquals(atTwo, all.get(1));
        assertEquals(atFive, all.get(4));
        assertEquals(OfferStatus.EMPTY, all.get(0).status(), "unoccupied slot is empty");
        assertEquals(OfferStatus.EMPTY, all.get(7).status());
    }

    @Test
    void fillSlotsHonoursTheFreeToPlaySlotCount() {
        // A free-to-play world exposes only three GE slots; the other five must not appear as idle
        // capacity, or the engine wrongly reports the slot cap as the cause of unused slots.
        GeOffer atTwo = OfferMapper.toGeOffer(2, "IN_PROGRESS", "BUY", 7, 10, 1, 0, 0L);

        List<GeOffer> slots = OfferMapper.fillSlots(List.of(atTwo), OfferMapper.FREE_SLOT_COUNT);

        assertEquals(3, slots.size());
        assertEquals(atTwo, slots.get(1));
        assertEquals(OfferStatus.EMPTY, slots.get(0).status());
        assertEquals(OfferStatus.EMPTY, slots.get(2).status());
    }
}
