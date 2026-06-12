package com.osrsscripts.geflipper;

import com.osrsscripts.core.model.GeOffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link GeClient} that records mutating calls, for testing the executor and tasks. */
final class FakeGeClient implements GeClient {

    boolean open = true;
    boolean placementsSucceed = true;
    long coins;
    List<GeOffer> offers = new ArrayList<>();
    Map<Integer, Integer> stock = new LinkedHashMap<>();

    int openCalls;
    int closeCalls;
    int collectCalls;
    final List<int[]> buys = new ArrayList<>();   // {itemId, price, quantity}
    final List<int[]> sells = new ArrayList<>();   // {itemId, price, quantity}
    final List<Integer> aborts = new ArrayList<>();

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean open() {
        openCalls++;
        open = true;
        return true;
    }

    @Override
    public List<GeOffer> offers() {
        return offers;
    }

    @Override
    public long coins() {
        return coins;
    }

    @Override
    public Map<Integer, Integer> stock() {
        return stock;
    }

    @Override
    public boolean placeBuy(int itemId, int price, int quantity) {
        buys.add(new int[] {itemId, price, quantity});
        return placementsSucceed;
    }

    @Override
    public boolean placeSell(int itemId, int price, int quantity) {
        sells.add(new int[] {itemId, price, quantity});
        return placementsSucceed;
    }

    @Override
    public boolean close() {
        closeCalls++;
        open = false;
        return true;
    }

    @Override
    public boolean abort(int slot) {
        aborts.add(slot);
        return true;
    }

    @Override
    public boolean collect() {
        collectCalls++;
        return true;
    }
}
