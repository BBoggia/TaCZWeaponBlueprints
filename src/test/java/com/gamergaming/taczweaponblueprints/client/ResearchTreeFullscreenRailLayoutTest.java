package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchTreeFullscreenRailLayoutTest {
    @Test
    void railEntriesAreBoundedAndGrowOnlyWithAvailableHeight() {
        int previousCount = 0;
        for (int[] size : List.of(
                new int[] {320, 240},
                new int[] {640, 360},
                new int[] {854, 480},
                new int[] {1920, 1080})) {
            ResearchTreeFullscreenLayout.Layout fullscreen =
                    ResearchTreeFullscreenLayout.forScreen(size[0], size[1]);
            ResearchTreeFullscreenRailLayout.Layout rail =
                    ResearchTreeFullscreenRailLayout.forLayout(fullscreen);

            assertTrue(rail.entries().size() >= 3);
            assertTrue(rail.entries().size()
                    <= ResearchTreeFullscreenRailLayout.MAX_VISIBLE_ENTRIES);
            assertTrue(rail.entries().size() >= previousCount);
            previousCount = rail.entries().size();
            rail.entries().forEach(entry -> assertTrue(fullscreen.rail().contains(entry)));
            assertTrue(fullscreen.rail().contains(rail.zoomOut()));
            assertTrue(fullscreen.rail().contains(rail.zoomIn()));
            assertTrue(fullscreen.rail().contains(rail.fit()));
            assertFalse(rail.entries().get(rail.entries().size() - 1).overlaps(rail.zoomOut()));
        }
    }

    @Test
    void searchAndBottomActionsLeaveTheEntryWindowDisjoint() {
        ResearchTreeFullscreenLayout.Layout fullscreen =
                ResearchTreeFullscreenLayout.forScreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT);
        ResearchTreeFullscreenRailLayout.Layout rail =
                ResearchTreeFullscreenRailLayout.forLayout(fullscreen);

        rail.entries().forEach(entry -> {
            assertFalse(entry.overlaps(fullscreen.searchButton()));
            assertFalse(entry.overlaps(rail.zoomOut()));
            assertFalse(entry.overlaps(rail.zoomIn()));
            assertFalse(entry.overlaps(rail.fit()));
        });
    }
}
