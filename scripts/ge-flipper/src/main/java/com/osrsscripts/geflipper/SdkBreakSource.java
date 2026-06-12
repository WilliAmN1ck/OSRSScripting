package com.osrsscripts.geflipper;

import java.util.Objects;
import org.tribot.automation.script.client.sidecars.Sidecars;

/**
 * {@link BreakSource} backed by the TRiBot break-handler sidecar. Observation only: enabling or
 * scheduling breaks stays a user choice in the client UI. SDK-coupled, so verified in the live
 * run rather than unit tests.
 */
final class SdkBreakSource implements BreakSource {

    private final Sidecars sidecars;

    SdkBreakSource(Sidecars sidecars) {
        this.sidecars = Objects.requireNonNull(sidecars, "sidecars");
    }

    @Override
    public boolean isOnBreak() {
        return sidecars.getBreakHandler().isOnBreak();
    }
}
