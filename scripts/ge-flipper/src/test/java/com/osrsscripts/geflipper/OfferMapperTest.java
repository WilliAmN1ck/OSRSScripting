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
        GeOffer offer = OfferMapper.toGeOffer(3, "IN_PROGRESS", "BUY", 1234, 100, 50, 10);

        assertEquals(3, offer.slot());
        assertEquals(OfferStatus.PARTIAL, offer.status());
        assertEquals(OfferSide.BUY, offer.side());
        assertEquals(1234, offer.itemId());
        assertEquals(100, offer.pricePerItem());
        assertEquals(50, offer.quantity());
        assertEquals(10, offer.filled());
        assertNull(offer.placedAt(), "SDK exposes no placement time");
    }

    @Test
    void toGeOfferLeavesSideNullForEmptySlot() {
        GeOffer offer = OfferMapper.toGeOffer(1, "EMPTY", "BUY", 0, 0, 0, 0);

        assertEquals(OfferStatus.EMPTY, offer.status());
        assertNull(offer.side());
    }

    @Test
    void fillEightSlotsReturnsAllSlotsInOrderFillingGaps() {
        GeOffer atTwo = OfferMapper.toGeOffer(2, "IN_PROGRESS", "SELL", 7, 10, 1, 0);
        GeOffer atFive = OfferMapper.toGeOffer(5, "COMPLETED", "BUY", 9, 20, 2, 2);

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
}
