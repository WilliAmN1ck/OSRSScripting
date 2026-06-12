package com.osrsscripts.core.model;

import java.util.Objects;

/**
 * Trade volumes for an item over a window, from the OSRS Wiki {@code /5m} or {@code /1h} endpoints.
 */
public final class VolumePoint {

    private final long highPriceVolume;
    private final long lowPriceVolume;

    public VolumePoint(long highPriceVolume, long lowPriceVolume) {
        this.highPriceVolume = highPriceVolume;
        this.lowPriceVolume = lowPriceVolume;
    }

    public long highPriceVolume() {
        return highPriceVolume;
    }

    public long lowPriceVolume() {
        return lowPriceVolume;
    }

    /** Total traded volume in the window (buys + sells). */
    public long total() {
        return highPriceVolume + lowPriceVolume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VolumePoint)) {
            return false;
        }
        VolumePoint other = (VolumePoint) o;
        return highPriceVolume == other.highPriceVolume && lowPriceVolume == other.lowPriceVolume;
    }

    @Override
    public int hashCode() {
        return Objects.hash(highPriceVolume, lowPriceVolume);
    }

    @Override
    public String toString() {
        return "VolumePoint{high=" + highPriceVolume + ", low=" + lowPriceVolume + '}';
    }
}
