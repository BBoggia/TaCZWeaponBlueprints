package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;

import net.minecraft.resources.ResourceLocation;

/** Correlates one in-flight Recycler request and bounds client-side waiting. */
public final class BlueprintRecyclerRequestTracker {
    public static final int TIMEOUT_TICKS = 100;

    private int nextRequestId = 1;
    private Pending pending;

    public Optional<Request> begin(
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation inputId,
            int inputCount) {
        return begin(action, inputId, inputCount, 1L);
    }

    public Optional<Request> begin(
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation inputId,
            int inputCount,
            long stateToken) {
        if (pending != null || action == null || inputId == null
                || inputCount < 1
                || inputCount
                        > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                || stateToken < 1L) {
            return Optional.empty();
        }
        int requestId = nextRequestId;
        nextRequestId = nextRequestId == Integer.MAX_VALUE ? 1 : nextRequestId + 1;
        Request request = new Request(requestId, action, inputId, inputCount, stateToken);
        pending = new Pending(request, 0);
        return Optional.of(request);
    }

    public boolean accept(
            int requestId,
            BlueprintRecyclerActionContract.ActionResult result) {
        if (pending == null || result == null
                || pending.request().requestId() != requestId
                || pending.request().action() != result.action()
                || result.inputId().filter(pending.request().inputId()::equals).isEmpty()) {
            return false;
        }
        pending = null;
        return true;
    }

    /** Returns true only on the tick that an outstanding request times out. */
    public boolean tick() {
        if (pending == null) {
            return false;
        }
        int age = pending.ageTicks() + 1;
        if (age >= TIMEOUT_TICKS) {
            pending = null;
            return true;
        }
        pending = new Pending(pending.request(), age);
        return false;
    }

    public boolean pending() {
        return pending != null;
    }

    public void clear() {
        pending = null;
    }

    public record Request(
            int requestId,
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation inputId,
            int inputCount,
            long stateToken) {
        public Request {
            if (requestId < 1 || action == null || inputId == null || inputCount < 1
                    || inputCount
                            > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                    || stateToken < 1L) {
                throw new IllegalArgumentException("invalid Blueprint Recycler request");
            }
        }
    }

    private record Pending(Request request, int ageTicks) {
    }
}
