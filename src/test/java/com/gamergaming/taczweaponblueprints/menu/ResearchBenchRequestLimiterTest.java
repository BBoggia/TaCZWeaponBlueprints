package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchBenchRequestLimiterTest {
    @Test
    void duplicateSelectionDoesNotConsumePlanningAdmission() {
        ResearchBenchRequestLimiter limiter = new ResearchBenchRequestLimiter();
        ResourceLocation selected = id("test:selected");

        assertEquals(
                ResearchBenchRequestLimiter.Decision.DUPLICATE,
                limiter.admitSelection(selected, selected, 10L));
        for (int index = 0;
                index < ResearchBenchRequestLimiter.MAX_PLANNED_SELECTIONS_PER_WINDOW;
                index++) {
            assertEquals(
                    ResearchBenchRequestLimiter.Decision.ALLOW,
                    limiter.admitSelection(id("test:target_" + index), selected, 10L));
        }
    }

    @Test
    void varyingTargetsAreBoundedUntilTheWindowAdvances() {
        ResearchBenchRequestLimiter limiter = new ResearchBenchRequestLimiter();
        for (int index = 0;
                index < ResearchBenchRequestLimiter.MAX_PLANNED_SELECTIONS_PER_WINDOW;
                index++) {
            assertEquals(
                    ResearchBenchRequestLimiter.Decision.ALLOW,
                    limiter.admitSelection(id("test:target_" + index), null, 50L));
        }
        assertEquals(
                ResearchBenchRequestLimiter.Decision.THROTTLE,
                limiter.admitSelection(id("test:blocked"), null, 50L));
        assertEquals(
                ResearchBenchRequestLimiter.Decision.ALLOW,
                limiter.admitSelection(
                        id("test:next_window"),
                        null,
                        50L + ResearchBenchRequestLimiter.WINDOW_TICKS));
    }

    @Test
    void clearingSelectionAndInvalidTimeNeverCreatePlanningWork() {
        ResearchBenchRequestLimiter limiter = new ResearchBenchRequestLimiter();

        assertEquals(
                ResearchBenchRequestLimiter.Decision.ALLOW,
                limiter.admitSelection(null, id("test:selected"), -1L));
        assertEquals(
                ResearchBenchRequestLimiter.Decision.THROTTLE,
                limiter.admitSelection(id("test:target"), null, -1L));
    }

    @Test
    void researchRequestsAreBoundedIndependentlyFromSelections() {
        ResearchBenchRequestLimiter limiter = new ResearchBenchRequestLimiter();

        for (int index = 0;
                index < ResearchBenchRequestLimiter.MAX_RESEARCH_REQUESTS_PER_WINDOW;
                index++) {
            assertEquals(
                    ResearchBenchRequestLimiter.Decision.ALLOW,
                    limiter.admitResearch(20L));
        }
        assertEquals(
                ResearchBenchRequestLimiter.Decision.THROTTLE,
                limiter.admitResearch(20L));
        assertEquals(
                ResearchBenchRequestLimiter.Decision.ALLOW,
                limiter.admitResearch(20L + ResearchBenchRequestLimiter.WINDOW_TICKS));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
