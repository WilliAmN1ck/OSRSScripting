package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.GeOffer;
import com.osrsscripts.core.model.OfferSide;
import com.osrsscripts.core.model.OfferStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure translation between the Script SDK's offer representation and the engine's {@link GeOffer}.
 * Methods take primitives and enum <em>names</em> rather than SDK types, so they are testable
 * without the game client. Two shapes differ and are reconciled here:
 *
 * <ul>
 *   <li>The SDK's four-state status has no {@code PARTIAL}; it is re-derived from the transferred
 *       quantity of an {@code IN_PROGRESS} offer.</li>
 *   <li>The SDK has no placement timestamp, so {@link GeOffer#placedAt()} is left {@code null}.</li>
 * </ul>
 */
public final class OfferMapper {

    /** Item id of coins, which are cash rather than sellable stock. */
    public static final int COINS_ITEM_ID = 995;

    /** GE slots available on a members world. */
    public static final int MEMBERS_SLOT_COUNT = 8;

    /** GE slots available on a free-to-play world. */
    public static final int FREE_SLOT_COUNT = 3;

    private OfferMapper() {
    }

    /** Maps the SDK status name (plus transferred quantity, for the PARTIAL split) to {@link OfferStatus}. */
    public static OfferStatus statusOf(String sdkStatusName, int transferredItemQty) {
        switch (sdkStatusName) {
            case "EMPTY":
                return OfferStatus.EMPTY;
            case "IN_PROGRESS":
                return transferredItemQty > 0 ? OfferStatus.PARTIAL : OfferStatus.ACTIVE;
            case "COMPLETED":
                return OfferStatus.COMPLETE;
            case "CANCELLED":
                return OfferStatus.CANCELLED;
            default:
                throw new IllegalArgumentException("Unknown GE offer status: " + sdkStatusName);
        }
    }

    /** Maps the SDK type name to {@link OfferSide}. */
    public static OfferSide sideOf(String sdkTypeName) {
        switch (sdkTypeName) {
            case "BUY":
                return OfferSide.BUY;
            case "SELL":
                return OfferSide.SELL;
            default:
                throw new IllegalArgumentException("Unknown GE offer type: " + sdkTypeName);
        }
    }

    /** Assembles a {@link GeOffer} from SDK-extracted primitives ({@code placedAt} is unavailable). */
    public static GeOffer toGeOffer(int slot, String statusName, String typeName, int itemId,
                                    int price, int totalQty, int transferredQty,
                                    long transferredGold) {
        OfferStatus status = statusOf(statusName, transferredQty);
        OfferSide side = status == OfferStatus.EMPTY ? null : sideOf(typeName);
        return new GeOffer(slot, status, side, itemId, price, totalQty, transferredQty,
                transferredGold, null);
    }

    /** Returns all eight slots in order, filling any slot absent from {@code present} with empty. */
    public static List<GeOffer> fillEightSlots(List<GeOffer> present) {
        return fillSlots(present, MEMBERS_SLOT_COUNT);
    }

    /**
     * Returns {@code slotCount} slots in order, filling any slot absent from {@code present} with an
     * empty offer. The count reflects the world: a free-to-play world exposes only the first three
     * GE slots, so the engine must not see the other five as idle capacity to fill.
     */
    public static List<GeOffer> fillSlots(List<GeOffer> present, int slotCount) {
        Map<Integer, GeOffer> bySlot = new HashMap<>();
        for (GeOffer offer : present) {
            bySlot.put(offer.slot(), offer);
        }
        List<GeOffer> result = new ArrayList<>(slotCount);
        for (int slot = 1; slot <= slotCount; slot++) {
            GeOffer offer = bySlot.get(slot);
            result.add(offer != null ? offer : GeOffer.empty(slot));
        }
        return result;
    }
}
