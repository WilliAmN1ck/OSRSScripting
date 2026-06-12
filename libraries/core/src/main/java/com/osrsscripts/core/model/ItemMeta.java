package com.osrsscripts.core.model;

import java.util.Objects;

/**
 * Static metadata for a tradeable item, sourced from the OSRS Wiki {@code /mapping} endpoint.
 */
public final class ItemMeta {

    private final int id;
    private final String name;
    private final boolean members;
    private final int buyLimit; // 0 = unknown / no published 4-hour limit

    public ItemMeta(int id, String name, boolean members, int buyLimit) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.members = members;
        this.buyLimit = buyLimit;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean members() {
        return members;
    }

    public int buyLimit() {
        return buyLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemMeta)) {
            return false;
        }
        ItemMeta other = (ItemMeta) o;
        return id == other.id
                && members == other.members
                && buyLimit == other.buyLimit
                && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, members, buyLimit);
    }

    @Override
    public String toString() {
        return "ItemMeta{id=" + id + ", name='" + name + "', members=" + members
                + ", buyLimit=" + buyLimit + '}';
    }
}
