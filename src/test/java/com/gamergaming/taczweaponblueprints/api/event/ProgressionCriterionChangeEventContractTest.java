package com.gamergaming.taczweaponblueprints.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.ICancellableEvent;

class ProgressionCriterionChangeEventContractTest {
    @Test
    void onlyThePreCommitEventIsCancellable() {
        assertTrue(ICancellableEvent.class.isAssignableFrom(ProgressionCriterionChangeEvent.Pre.class));
        assertFalse(ICancellableEvent.class.isAssignableFrom(ProgressionCriterionChangeEvent.Post.class));
    }

    @Test
    void EventSurfaceCannotRewriteTheCommittedTransition() {
        assertFalse(Arrays.stream(ProgressionCriterionChangeEvent.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("set")
                        && (method.getName().toLowerCase().contains("criterion")
                                || method.getName().toLowerCase().contains("operation")
                                || method.getName().toLowerCase().contains("operand")
                                || method.getName().toLowerCase().contains("value"))));
    }
}
