package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchBenchScreenLayoutTest {
    @Test
    void fullscreenResearchActionIsOwnedByTheAdaptiveContextCard() {
        for (ResearchTreeScreenLayout.Layout layout : java.util.List.of(
                ResearchTreeScreenLayout.fullscreen(854, 480, true),
                ResearchTreeScreenLayout.fullscreen(480, 300, true),
                ResearchTreeScreenLayout.fullscreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT,
                        false))) {
            assertTrue(ResearchTreeDetailLayout.primaryAction(layout).isEmpty());
        }
    }
}
