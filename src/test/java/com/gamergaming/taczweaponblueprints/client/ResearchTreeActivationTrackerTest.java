package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeActivationTrackerTest {
    private static final ResourceLocation FIRST = new ResourceLocation("test:first");
    private static final ResourceLocation SECOND = new ResourceLocation("test:second");

    @Test
    void onlyTwoQuickClicksOnTheSameNodeActivate() {
        ResearchTreeActivationTracker tracker = new ResearchTreeActivationTracker(350L);

        assertFalse(tracker.click(FIRST, 1_000L));
        assertFalse(tracker.click(SECOND, 1_100L));
        assertTrue(tracker.click(SECOND, 1_449L));
        assertFalse(tracker.click(SECOND, 1_450L));
        assertFalse(tracker.click(SECOND, 1_801L));
    }

    @Test
    void activationAndExplicitResetBothRequireANewPair() {
        ResearchTreeActivationTracker tracker = new ResearchTreeActivationTracker(350L);

        assertFalse(tracker.click(FIRST, 10L));
        assertTrue(tracker.click(FIRST, 20L));
        assertFalse(tracker.click(FIRST, 30L));
        tracker.reset();
        assertFalse(tracker.click(FIRST, 40L));
    }

    @Test
    void invalidInputAndClockRegressionNeverActivate() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeActivationTracker(0L));
        ResearchTreeActivationTracker tracker = new ResearchTreeActivationTracker(350L);
        assertThrows(IllegalArgumentException.class, () -> tracker.click(null, 0L));
        assertThrows(IllegalArgumentException.class, () -> tracker.click(FIRST, -1L));
        assertFalse(tracker.click(FIRST, 100L));
        assertFalse(tracker.click(FIRST, 99L));
    }
}
