package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchTreeGuidanceLayoutTest {
    @Test
    void guidanceStaysInsideEveryCompactAndFullscreenCanvas() {
        for (ResearchTreeScreenLayout.Layout layout : List.of(
                ResearchTreeScreenLayout.compact(),
                ResearchTreeScreenLayout.fullscreen(854, 480, true),
                ResearchTreeScreenLayout.fullscreen(640, 360, true),
                ResearchTreeScreenLayout.fullscreen(320, 240, true))) {
            ResearchTreeGuidanceLayout.Guide guide =
                    ResearchTreeGuidanceLayout.forLayout(layout);

            assertTrue(layout.canvas().contains(guide.panel()));
            assertTrue(guide.panel().contains(guide.dismiss()));
            assertFalse(guide.panel().overlaps(layout.toolbar()));
            assertFalse(guide.panel().overlaps(layout.details()));
        }
    }

    @Test
    void fullscreenGuideUsesItsDedicatedOverlayWithoutCoveringTheRail() {
        ResearchTreeFullscreenLayout.Layout fullscreen =
                ResearchTreeFullscreenLayout.forScreen(320, 240);
        ResearchTreeGuidanceLayout.Guide guide =
                ResearchTreeGuidanceLayout.forFullscreen(fullscreen);

        assertEquals(fullscreen.coachmark(), guide.panel());
        assertTrue(guide.panel().contains(guide.dismiss()));
        assertFalse(guide.panel().overlaps(fullscreen.rail()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeGuidanceLayout.forFullscreen(null));
    }

    @Test
    void guidanceUsesHalfOpenPointContainment() {
        ResearchTreeScreenLayout.Rect panel = ResearchTreeGuidanceLayout.forLayout(
                ResearchTreeScreenLayout.compact()).panel();

        assertTrue(panel.contains(panel.x(), panel.y()));
        assertFalse(panel.contains(panel.right(), panel.y()));
        assertFalse(panel.contains(panel.x(), panel.bottom()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeGuidanceLayout.forLayout(null));
    }
}
