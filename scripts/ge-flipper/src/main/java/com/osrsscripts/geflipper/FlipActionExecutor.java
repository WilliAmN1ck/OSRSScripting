package com.osrsscripts.geflipper;

import com.osrsscripts.core.ge.FlipAction;
import java.util.List;
import java.util.Objects;

/**
 * Carries out the abstract {@link FlipAction}s emitted by the engine against a {@link GeClient}.
 * This is the only translation from decision to game action; it holds no policy of its own.
 *
 * <p>The engine emits one {@code COLLECT} per collectable slot, but the SDK's {@code collectAll}
 * drains every finished offer at once, so collects are deduplicated to a single call per batch.
 *
 * <p>A failed placement can leave the in-game offer-setup screen open (observed live with an item
 * the GE could not resolve), wedging every later placement; closing the GE resets the interface
 * and the open-GE task reopens it fresh next tick.
 */
public final class FlipActionExecutor {

    private final GeClient client;
    private final Runnable reactionBeat;

    public FlipActionExecutor(GeClient client) {
        this(client, () -> { });
    }

    /**
     * As {@link #FlipActionExecutor(GeClient)}, with a {@code reactionBeat} run once before a
     * non-empty batch of actions — a short human pause before reacting to the offers.
     */
    public FlipActionExecutor(GeClient client, Runnable reactionBeat) {
        this.client = Objects.requireNonNull(client, "client");
        this.reactionBeat = Objects.requireNonNull(reactionBeat, "reactionBeat");
    }

    /**
     * Executes each action, collapsing any number of {@code COLLECT}s into one {@code collect()}.
     * Returns whether every action succeeded, so the caller can back off on failure instead of
     * retrying every tick. Only failed placements close the GE — they are the failures that can
     * leave the offer-setup screen wedged.
     */
    public boolean execute(List<FlipAction> actions) {
        if (!actions.isEmpty()) {
            reactionBeat.run(); // pause a beat before reacting, like a person noticing the offers
        }
        boolean collected = false;
        boolean actionsOk = true;
        for (FlipAction action : actions) {
            switch (action.type()) {
                case PLACE_BUY:
                    if (!client.placeBuy(action.itemId(), (int) action.pricePerItem(),
                            action.quantity())) {
                        client.close();
                        actionsOk = false;
                    }
                    break;
                case PLACE_SELL:
                    if (!client.placeSell(action.itemId(), (int) action.pricePerItem(),
                            action.quantity())) {
                        client.close();
                        actionsOk = false;
                    }
                    break;
                case CANCEL:
                    if (!client.abort(action.slot())) {
                        actionsOk = false;
                    }
                    break;
                case COLLECT:
                    if (!collected) {
                        if (!client.collect()) {
                            actionsOk = false;
                        }
                        collected = true;
                    }
                    break;
                default:
                    throw new IllegalStateException("Unhandled action type: " + action.type());
            }
        }
        return actionsOk;
    }
}
