package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

class ResearchTreeFullscreenLayoutTest {
    @Test
    void canvasIsAlwaysTheCompleteScreenAndOverlaysRemainInsideIt() {
        for (int[] size : List.of(
                new int[] {320, 240},
                new int[] {640, 360},
                new int[] {854, 480},
                new int[] {1920, 1080})) {
            ResearchTreeFullscreenLayout.Layout layout =
                    ResearchTreeFullscreenLayout.forScreen(size[0], size[1]);

            assertEquals(
                    new ResearchTreeScreenLayout.Rect(0, 0, size[0], size[1]),
                    layout.canvas());
            assertTrue(layout.rail().contains(layout.searchButton()));
            assertFalse(layout.rail().overlaps(layout.searchField()));
            assertFalse(layout.searchField().overlaps(layout.close()));
            assertFalse(layout.coachmark().overlaps(layout.rail()));
            assertFalse(layout.safeFocus().overlaps(layout.rail()));
            assertFalse(layout.safeFocus().overlaps(layout.close()));
        }
    }

    @Test
    void minimumFullscreenStillHasUsableSearchAndCameraFocusSpace() {
        ResearchTreeFullscreenLayout.Layout layout = ResearchTreeFullscreenLayout.forScreen(
                ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT);

        assertTrue(layout.searchField().width() >= 96);
        assertTrue(layout.safeFocus().width() >= ResearchTreeLayout.NODE_WIDTH);
        assertTrue(layout.safeFocus().height() >= ResearchTreeLayout.NODE_HEIGHT);
        assertEquals(0, layout.edgeReveal().x());
        assertEquals(ResearchTreeFullscreenLayout.EDGE_REVEAL_WIDTH,
                layout.edgeReveal().width());
        assertTrue(layout.edgeRevealHitTarget().contains(layout.edgeReveal()));
        assertTrue(layout.edgeRevealHitTarget().width()
                >= ResearchTreeFullscreenLayout.EDGE_REVEAL_HIT_WIDTH);
    }

    @Test
    void undersizedScreensAreRejectedBeforeProducingInvalidOverlays() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeFullscreenLayout.forScreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH - 1,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeFullscreenLayout.forScreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT - 1));
    }
}
