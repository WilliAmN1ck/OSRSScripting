package com.osrsscripts.core.ge;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of one {@link FlipEngine} tick: the Grand Exchange actions to perform, plus the
 * reason any slots were left idle so the caller can prompt the user to adjust the binding setting.
 */
public final class FlipPlan {

    private final List<FlipAction> actions;
    private final IdleReason idleReason;

    public FlipPlan(List<FlipAction> actions, IdleReason idleReason) {
        this.actions = Collections.unmodifiableList(Objects.requireNonNull(actions, "actions"));
        this.idleReason = Objects.requireNonNull(idleReason, "idleReason");
    }

    public List<FlipAction> actions() {
        return actions;
    }

    public IdleReason idleReason() {
        return idleReason;
    }
}
