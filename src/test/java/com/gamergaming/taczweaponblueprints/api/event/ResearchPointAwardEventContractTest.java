package com.gamergaming.taczweaponblueprints.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardService;

import net.minecraftforge.eventbus.api.Cancelable;

class ResearchPointAwardEventContractTest {
    @Test
    void onlyThePreCommitEventIsCancellable() {
        assertTrue(ResearchPointAwardEvent.Pre.class.isAnnotationPresent(Cancelable.class));
        assertFalse(ResearchPointAwardEvent.Post.class.isAnnotationPresent(Cancelable.class));
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
