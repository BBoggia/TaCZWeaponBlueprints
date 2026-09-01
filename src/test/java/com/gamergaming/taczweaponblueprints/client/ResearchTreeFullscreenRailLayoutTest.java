package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertTrue(fullscreen.rail().contains(rail.pin()));
            assertTrue(fullscreen.rail().contains(rail.help()));
            assertTrue(fullscreen.rail().contains(rail.recommendation()));
            assertFalse(rail.entries().get(rail.entries().size() - 1)
                    .overlaps(rail.recommendation()));
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
            assertFalse(entry.overlaps(rail.pin()));
            assertFalse(entry.overlaps(rail.help()));
            assertFalse(entry.overlaps(rail.recommendation()));
        });
    }

    @Test
    void viewActionRemainsPinnedWhileOnlyGroupsScroll() {
        int visibleEntries = 5;
        int groups = 12;
        int maximumScroll = ResearchTreeFullscreenRailLayout.maximumGroupScroll(
                visibleEntries, groups);

        assertEquals(8, maximumScroll);
        assertEquals(0, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                0, visibleEntries, maximumScroll, groups));
        assertEquals(9, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                1, visibleEntries, maximumScroll, groups));
        assertEquals(12, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                4, visibleEntries, maximumScroll, groups));
    }

    @Test
    void shortGroupListsLeaveUnusedSlotsWithoutMovingTheViewAction() {
        assertEquals(0, ResearchTreeFullscreenRailLayout.maximumGroupScroll(5, 2));
        assertEquals(0, ResearchTreeFullscreenRailLayout.entryIndexForSlot(0, 5, 0, 2));
        assertEquals(1, ResearchTreeFullscreenRailLayout.entryIndexForSlot(1, 5, 0, 2));
        assertEquals(2, ResearchTreeFullscreenRailLayout.entryIndexForSlot(2, 5, 0, 2));
        assertEquals(-1, ResearchTreeFullscreenRailLayout.entryIndexForSlot(3, 5, 0, 2));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeFullscreenRailLayout.entryIndexForSlot(0, 5, 1, 2));
    }

    @Test
    void hiddenViewActionGivesItsSlotToTheFirstTechTreeDomain() {
        int visibleEntries = 5;
        int domains = 12;
        int maximumScroll = ResearchTreeFullscreenRailLayout.maximumGroupScroll(
                visibleEntries, domains, false);

        assertEquals(7, maximumScroll);
        assertEquals(8, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                0, visibleEntries, maximumScroll, domains, false));
        assertEquals(12, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                4, visibleEntries, maximumScroll, domains, false));
        assertEquals(1, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                0, visibleEntries, 0, 2, false));
        assertEquals(2, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                1, visibleEntries, 0, 2, false));
        assertEquals(-1, ResearchTreeFullscreenRailLayout.entryIndexForSlot(
                2, visibleEntries, 0, 2, false));
    }
}
