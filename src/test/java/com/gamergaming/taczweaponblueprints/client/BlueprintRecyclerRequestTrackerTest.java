package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerRequestTrackerTest {
    private static final ResourceLocation INPUT = new ResourceLocation("minecraft:paper");
    private static final ResourceLocation OTHER = new ResourceLocation("minecraft:book");

    @Test
    void permitsOnlyOneRequestAtATime() {
        BlueprintRecyclerRequestTracker tracker = new BlueprintRecyclerRequestTracker();

        assertTrue(tracker.begin(
                BlueprintRecyclerActionContract.Action.REDEEM, INPUT, 3).isPresent());
        assertTrue(tracker.pending());
        assertTrue(tracker.begin(
                BlueprintRecyclerActionContract.Action.REDEEM_STACK, INPUT, 3).isEmpty());
    }

    @Test
    void onlyTheExactCorrelatedResultClearsPendingState() {
        BlueprintRecyclerRequestTracker tracker = new BlueprintRecyclerRequestTracker();
        BlueprintRecyclerRequestTracker.Request request = tracker.begin(
                BlueprintRecyclerActionContract.Action.REDEEM, INPUT, 3, 44L).orElseThrow();
        assertTrue(request.stateToken() == 44L);

        assertFalse(tracker.accept(
                request.requestId() + 1,
                result(BlueprintRecyclerActionContract.Action.REDEEM, INPUT)));
        assertFalse(tracker.accept(
                request.requestId(),
                result(BlueprintRecyclerActionContract.Action.REDEEM_STACK, INPUT)));
        assertFalse(tracker.accept(
                request.requestId(),
                result(BlueprintRecyclerActionContract.Action.REDEEM, OTHER)));
        assertTrue(tracker.pending());
        assertTrue(tracker.accept(
                request.requestId(),
                result(BlueprintRecyclerActionContract.Action.REDEEM, INPUT)));
        assertFalse(tracker.pending());
    }

    @Test
    void timeoutReleasesTheControlExactlyOnce() {
        BlueprintRecyclerRequestTracker tracker = new BlueprintRecyclerRequestTracker();
        tracker.begin(BlueprintRecyclerActionContract.Action.RECYCLE, INPUT, 1);

        for (int tick = 1; tick < BlueprintRecyclerRequestTracker.TIMEOUT_TICKS; tick++) {
            assertFalse(tracker.tick());
        }
        assertTrue(tracker.tick());
        assertFalse(tracker.pending());
        assertFalse(tracker.tick());
    }

    @Test
    void invalidRequestsNeverCreatePendingState() {
        BlueprintRecyclerRequestTracker tracker = new BlueprintRecyclerRequestTracker();

        assertTrue(tracker.begin(null, INPUT, 1).isEmpty());
        assertTrue(tracker.begin(
                BlueprintRecyclerActionContract.Action.RECYCLE, null, 1).isEmpty());
        assertTrue(tracker.begin(
                BlueprintRecyclerActionContract.Action.RECYCLE, INPUT, 0).isEmpty());
        assertTrue(tracker.begin(
                BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                INPUT,
                1,
                0L).isEmpty());
        assertFalse(tracker.pending());
    }

    private static BlueprintRecyclerActionContract.ActionResult result(
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation input) {
        return new BlueprintRecyclerActionContract.ActionResult(
                action,
                Optional.of(input),
                BlueprintRecyclerActionContract.ResultCode.SUCCESS);
    }
}
