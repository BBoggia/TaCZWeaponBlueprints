package com.gamergaming.taczweaponblueprints.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardService;

import net.neoforged.bus.api.ICancellableEvent;

class ResearchPointAwardEventContractTest {
    @Test
    void onlyThePreCommitEventIsCancellable() {
        assertTrue(ICancellableEvent.class.isAssignableFrom(ResearchPointAwardEvent.Pre.class));
        assertFalse(ICancellableEvent.class.isAssignableFrom(ResearchPointAwardEvent.Post.class));
        assertFalse(ResearchPointAwardService.Status.CANCELLED.committed());
    }

    @Test
    void EventSurfaceHasNoAmountMutators() {
        assertFalse(Arrays.stream(ResearchPointAwardEvent.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("set")
                        && (method.getName().toLowerCase().contains("point")
                                || method.getName().toLowerCase().contains("amount"))));
    }
}
