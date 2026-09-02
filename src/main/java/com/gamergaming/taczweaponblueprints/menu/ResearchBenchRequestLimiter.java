package com.gamergaming.taczweaponblueprints.menu;

import net.minecraft.resources.ResourceLocation;

/** Menu-local protection against duplicate and rapidly varying selection requests. */
final class ResearchBenchRequestLimiter {
    static final int WINDOW_TICKS = 20;
    static final int MAX_PLANNED_SELECTIONS_PER_WINDOW = 8;
    static final int MAX_RESEARCH_REQUESTS_PER_WINDOW = 2;
    static final int MAX_GUIDANCE_REQUESTS_PER_WINDOW = 4;
    static final int MAX_AFFORDABILITY_BATCHES_PER_WINDOW = 4;

    private long windowStartTick = Long.MIN_VALUE;
    private int plannedSelections;
    private long researchWindowStartTick = Long.MIN_VALUE;
    private int researchRequests;
    private long guidanceWindowStartTick = Long.MIN_VALUE;
    private int guidanceRequests;
    private long affordabilityWindowStartTick = Long.MIN_VALUE;
    private int affordabilityBatches;

    Decision admitSelection(
            ResourceLocation requested,
            ResourceLocation selected,
            long currentTick) {
        if (requested == null) {
            return Decision.ALLOW;
        }
        if (requested.equals(selected)) {
            return Decision.DUPLICATE;
        }
        if (currentTick < 0L) {
            return Decision.THROTTLE;
        }
        if (windowStartTick == Long.MIN_VALUE
                || currentTick < windowStartTick
                || currentTick - windowStartTick >= WINDOW_TICKS) {
            windowStartTick = currentTick;
            plannedSelections = 0;
        }
        if (plannedSelections >= MAX_PLANNED_SELECTIONS_PER_WINDOW) {
            return Decision.THROTTLE;
        }
        plannedSelections++;
        return Decision.ALLOW;
    }

    Decision admitResearch(long currentTick) {
        if (currentTick < 0L) {
            return Decision.THROTTLE;
        }
        if (researchWindowStartTick == Long.MIN_VALUE
                || currentTick < researchWindowStartTick
                || currentTick - researchWindowStartTick >= WINDOW_TICKS) {
            researchWindowStartTick = currentTick;
            researchRequests = 0;
        }
        if (researchRequests >= MAX_RESEARCH_REQUESTS_PER_WINDOW) {
            return Decision.THROTTLE;
        }
        researchRequests++;
        return Decision.ALLOW;
    }

    Decision admitGuidance(long currentTick) {
        if (currentTick < 0L) {
            return Decision.THROTTLE;
        }
        if (guidanceWindowStartTick == Long.MIN_VALUE
                || currentTick < guidanceWindowStartTick
                || currentTick - guidanceWindowStartTick >= WINDOW_TICKS) {
            guidanceWindowStartTick = currentTick;
            guidanceRequests = 0;
        }
        if (guidanceRequests >= MAX_GUIDANCE_REQUESTS_PER_WINDOW) {
            return Decision.THROTTLE;
        }
        guidanceRequests++;
        return Decision.ALLOW;
    }

    Decision admitAffordability(long currentTick) {
        if (currentTick < 0L) {
            return Decision.THROTTLE;
        }
        if (affordabilityWindowStartTick == Long.MIN_VALUE
                || currentTick < affordabilityWindowStartTick
                || currentTick - affordabilityWindowStartTick >= WINDOW_TICKS) {
            affordabilityWindowStartTick = currentTick;
            affordabilityBatches = 0;
        }
        if (affordabilityBatches >= MAX_AFFORDABILITY_BATCHES_PER_WINDOW) {
            return Decision.THROTTLE;
        }
        affordabilityBatches++;
        return Decision.ALLOW;
    }

    enum Decision {
        ALLOW,
        DUPLICATE,
        THROTTLE
    }
}
