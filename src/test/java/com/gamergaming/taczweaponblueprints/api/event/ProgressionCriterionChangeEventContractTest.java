package com.gamergaming.taczweaponblueprints.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import net.minecraftforge.eventbus.api.Cancelable;

class ProgressionCriterionChangeEventContractTest {
    @Test
    void onlyThePreCommitEventIsCancellable() {
        assertTrue(ProgressionCriterionChangeEvent.Pre.class
                .isAnnotationPresent(Cancelable.class));
        assertFalse(ProgressionCriterionChangeEvent.Post.class
                .isAnnotationPresent(Cancelable.class));
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
