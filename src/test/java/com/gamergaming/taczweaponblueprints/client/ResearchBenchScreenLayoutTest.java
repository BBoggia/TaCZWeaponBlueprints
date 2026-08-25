package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchBenchScreenLayoutTest {
    @Test
    void fullscreenResearchActionFloatsInsideTheOverlayAwayFromToolbar() {
        for (ResearchTreeScreenLayout.Layout layout : java.util.List.of(
                ResearchTreeScreenLayout.fullscreen(854, 480, true),
                ResearchTreeScreenLayout.fullscreen(480, 300, true),
                ResearchTreeScreenLayout.fullscreen(260, 180, false))) {
            ResearchTreeScreenLayout.Rect action =
                    ResearchTreeDetailLayout.primaryAction(layout).orElseThrow();
            assertTrue(action.inside(layout.screenWidth(), layout.screenHeight()));
            assertFalse(action.overlaps(layout.toolbar()));
        }
    }
}
