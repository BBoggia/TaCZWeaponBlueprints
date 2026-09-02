package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchTreeSearchResultLayoutTest {
    @Test
    void compactResultsOverlayTheCanvasWithoutLeavingTheScreen() {
        ResearchTreeScreenLayout.Layout compact = ResearchTreeScreenLayout.compact();
        ResearchTreeSearchResultLayout.Layout results = ResearchTreeSearchResultLayout.below(
                compact.search(), compact.screenWidth(), compact.screenHeight());

        assertEquals(ResearchTreeSearchResultLayout.MAX_VISIBLE_RESULTS, results.rows().size());
        assertTrue(results.panel().inside(compact.screenWidth(), compact.screenHeight()));
        assertTrue(results.panel().overlaps(compact.canvas()));
        assertTrue(results.panel().width() >= compact.search().width());
        for (int index = 1; index < results.rows().size(); index++) {
            assertFalse(results.rows().get(index - 1).overlaps(results.rows().get(index)));
        }
    }

    @Test
    void fullscreenResultsUseThePublishedSearchWidthAndRemainBounded() {
        ResearchTreeFullscreenLayout.Layout fullscreen =
                ResearchTreeFullscreenLayout.forScreen(854, 480);
        ResearchTreeSearchResultLayout.Layout results = ResearchTreeSearchResultLayout.below(
                fullscreen.searchField(), 854, 480);

        assertEquals(fullscreen.searchField().width(), results.panel().width());
        assertEquals(ResearchTreeSearchResultLayout.MAX_VISIBLE_RESULTS, results.rows().size());
        assertTrue(results.panel().inside(854, 480));
    }

    @Test
    void unusableInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeSearchResultLayout.below(null, 320, 240));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeSearchResultLayout.below(
                        new ResearchTreeScreenLayout.Rect(0, 220, 100, 20),
                        320,
                        240));
    }
}
