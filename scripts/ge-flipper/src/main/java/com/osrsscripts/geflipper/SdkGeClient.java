package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.GeOffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tribot.script.sdk.GrandExchange;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.Worlds;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.types.GrandExchangeOffer;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.World;
import org.tribot.script.sdk.types.definitions.ItemDefinition;

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
    public boolean close() {
        return GrandExchange.close();
    }

    @Override
    public List<GeOffer> offers() {
        List<GeOffer> present = new ArrayList<>();
        for (GrandExchangeOffer offer : Query.grandExchangeOffers().toList()) {
            // Empty slots (which may expose null slot/type) are reconstructed by fillSlots.
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
                    offer.getTransferredItemQuantity(),
                    offer.getTransferredGoldQuantity()));
        }
        return OfferMapper.fillSlots(present, slotCount());
    }

    /**
     * GE slots the current world exposes: three on free-to-play, eight on members. Padding to a
     * fixed eight would make the engine treat the five non-existent F2P slots as idle capacity and
     * wrongly advise the user to raise their slot cap. Defaults to members when the world is
     * momentarily unknown, the same eight-slot behaviour as before.
     */
    private static int slotCount() {
        boolean members = Worlds.getCurrent().map(World::isMembers).orElse(true);
        return members ? OfferMapper.MEMBERS_SLOT_COUNT : OfferMapper.FREE_SLOT_COUNT;
    }

    @Override
    public long coins() {
        return Inventory.getCount(OfferMapper.COINS_ITEM_ID);
    }

    @Override
    public Map<Integer, Integer> stock() {
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        for (InventoryItem item : Inventory.getAll()) {
            // GE collection hands stackables over in noted form; canonicalize to the unnoted id
            // so the engine's world (ledger, wiki prices, sell offers) sees one id per item.
            ItemDefinition definition = item.getDefinition();
            int id = definition.isNoted() ? definition.getUnnotedItemId() : item.getId();
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
                .itemId(inventoryIdFor(itemId))
                .price(price)
                .quantity(quantity)
                .build());
    }

    /**
     * The engine speaks in canonical (unnoted) ids, but the sell flow must reference the item as
     * it sits in the inventory — which is the noted form when it came from a GE collection.
     */
    private static int inventoryIdFor(int canonicalItemId) {
        for (InventoryItem item : Inventory.getAll()) {
            ItemDefinition definition = item.getDefinition();
            int canonical = definition.isNoted() ? definition.getUnnotedItemId() : item.getId();
            if (canonical == canonicalItemId) {
                return item.getId();
            }
        }
        return canonicalItemId;
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
