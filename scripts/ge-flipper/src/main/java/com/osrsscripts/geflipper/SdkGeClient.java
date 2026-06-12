package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.GeOffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tribot.script.sdk.GrandExchange;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.types.GrandExchangeOffer;
import org.tribot.script.sdk.types.InventoryItem;

/**
 * The sole TRiBot Script SDK-backed {@link GeClient}. Every method is thin delegation to the SDK,
 * with the SDK-to-engine translation handled by the pure {@link OfferMapper}; this class is
 * therefore verified by compilation and the live Echo run rather than by unit tests.
 */
public final class SdkGeClient implements GeClient {

    @Override
    public boolean isOpen() {
        return GrandExchange.isOpen();
    }

    @Override
    public boolean open() {
        return GrandExchange.open();
    }

    @Override
    public List<GeOffer> offers() {
        List<GeOffer> present = new ArrayList<>();
        for (GrandExchangeOffer offer : Query.grandExchangeOffers().toList()) {
            // Empty slots (which may expose null slot/type) are reconstructed by fillEightSlots.
            if (offer.getStatus() == GrandExchangeOffer.Status.EMPTY) {
                continue;
            }
            present.add(OfferMapper.toGeOffer(
                    offer.getSlot().ordinal() + 1,
                    offer.getStatus().name(),
                    offer.getType().name(),
                    offer.getItemId(),
                    offer.getPrice(),
                    offer.getTotalQuantity(),
                    offer.getTransferredItemQuantity()));
        }
        return OfferMapper.fillEightSlots(present);
    }

    @Override
    public long coins() {
        return Inventory.getCount(OfferMapper.COINS_ITEM_ID);
    }

    @Override
    public Map<Integer, Integer> stock() {
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        for (InventoryItem item : Inventory.getAll()) {
            int id = item.getId();
            if (id == OfferMapper.COINS_ITEM_ID) {
                continue;
            }
            stock.merge(id, item.getStack(), Integer::sum);
        }
        return stock;
    }

    @Override
    public boolean placeBuy(int itemId, int price, int quantity) {
        return GrandExchange.placeOffer(GrandExchange.CreateOfferConfig.builder()
                .type(GrandExchangeOffer.Type.BUY)
                .itemId(itemId)
                .price(price)
                .quantity(quantity)
                .build());
    }

    @Override
    public boolean placeSell(int itemId, int price, int quantity) {
        return GrandExchange.placeOffer(GrandExchange.CreateOfferConfig.builder()
                .type(GrandExchangeOffer.Type.SELL)
                .itemId(itemId)
                .price(price)
                .quantity(quantity)
                .build());
    }

    @Override
    public boolean abort(int slot) {
        return GrandExchange.abort(GrandExchangeOffer.Slot.values()[slot - 1]);
    }

    @Override
    public boolean collect() {
        return GrandExchange.collectAll(GrandExchange.CollectMethod.INVENTORY);
    }
}
