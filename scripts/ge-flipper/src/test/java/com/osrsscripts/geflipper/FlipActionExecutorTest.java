package com.osrsscripts.geflipper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.osrsscripts.core.ge.FlipAction;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FlipActionExecutorTest {

    private final FakeGeClient client = new FakeGeClient();
    private final FlipActionExecutor executor = new FlipActionExecutor(client);

    @Test
    void placeBuyForwardsItemPriceAndQuantity() {
        executor.execute(List.of(FlipAction.placeBuy(0, 1234, 100L, 7)));

        assertEquals(1, client.buys.size());
        assertArrayEquals(new int[] {1234, 100, 7}, client.buys.get(0));
        assertTrue(client.sells.isEmpty());
    }

    @Test
    void placeSellForwardsItemPriceAndQuantity() {
        executor.execute(List.of(FlipAction.placeSell(0, 55, 200L, 3)));

        assertEquals(1, client.sells.size());
        assertArrayEquals(new int[] {55, 200, 3}, client.sells.get(0));
    }

    @Test
    void cancelAbortsTheActionsSlot() {
        executor.execute(List.of(FlipAction.cancel(4)));

        assertEquals(List.of(4), client.aborts);
    }

    @Test
    void multipleCollectsCollapseToSingleCollectCall() {
        executor.execute(List.of(
                FlipAction.collect(1),
                FlipAction.collect(2),
                FlipAction.collect(3)));

        assertEquals(1, client.collectCalls);
    }

    @Test
    void failedPlacementClosesTheGeToResetItsInterface() {
        client.placementsSucceed = false;

        executor.execute(List.of(FlipAction.placeBuy(0, 1234, 100L, 7)));
        assertEquals(1, client.closeCalls, "failed buy resets the GE interface");

        executor.execute(List.of(FlipAction.placeSell(0, 55, 200L, 3)));
        assertEquals(2, client.closeCalls, "failed sell resets the GE interface");

        client.placementsSucceed = true;
        executor.execute(List.of(FlipAction.placeBuy(0, 1234, 100L, 7)));
        assertEquals(2, client.closeCalls, "successful placement does not close");
    }

    @Test
    void failedAbortOrCollectReportsFailureWithoutClosingTheGe() {
        client.abortsSucceed = false;
        assertFalse(executor.execute(List.of(FlipAction.cancel(2))));
        assertEquals(0, client.closeCalls, "abort failure does not wedge the setup screen");

        client.abortsSucceed = true;
        client.collectsSucceed = false;
        assertFalse(executor.execute(List.of(FlipAction.collect(1))));
        assertEquals(0, client.closeCalls);

        client.collectsSucceed = true;
        assertTrue(executor.execute(List.of(FlipAction.cancel(2), FlipAction.collect(1))));
    }

    @Test
    void reactionBeatRunsOncePerNonEmptyBatchOnly() {
        AtomicInteger beats = new AtomicInteger();
        FlipActionExecutor withBeat = new FlipActionExecutor(client, beats::incrementAndGet);

        withBeat.execute(List.of());
        assertEquals(0, beats.get(), "no beat for an empty batch");

        withBeat.execute(List.of(FlipAction.placeBuy(0, 1234, 100L, 7)));
        assertEquals(1, beats.get(), "one beat before acting on a batch");
    }

    @Test
    void mixedBatchDispatchesEachActionKind() {
        executor.execute(List.of(
                FlipAction.collect(1),
                FlipAction.cancel(2),
                FlipAction.placeSell(0, 10, 50L, 1),
                FlipAction.placeBuy(0, 20, 30L, 2),
                FlipAction.collect(5)));

        assertEquals(1, client.collectCalls);
        assertEquals(List.of(2), client.aborts);
        assertEquals(1, client.sells.size());
        assertEquals(1, client.buys.size());
    }
}
