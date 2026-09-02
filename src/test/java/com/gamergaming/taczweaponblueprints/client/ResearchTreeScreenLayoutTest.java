package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchTreeScreenLayoutTest {
    @Test
    void compactContractPreservesTheExistingTreeAndDetailsGeometry() {
        ResearchTreeScreenLayout.Layout layout = ResearchTreeScreenLayout.compact();

        assertEquals(ResearchTreeScreenLayout.ViewMode.COMPACT, layout.mode());
        assertEquals(new ResearchTreeScreenLayout.Rect(8, 64, 294, 116), layout.canvas());
        assertEquals(new ResearchTreeScreenLayout.Rect(8, 183, 294, 44), layout.details());
        assertEquals(310, layout.screenWidth());
        assertEquals(240, layout.screenHeight());
        assertUsable(layout);
    }

    @Test
    void fullscreenUsesTheWholeScreenWithContextualOverlayDetails() {
        ResearchTreeScreenLayout.Layout wide =
                ResearchTreeScreenLayout.fullscreen(854, 480, true);
        ResearchTreeScreenLayout.Layout medium =
                ResearchTreeScreenLayout.fullscreen(640, 360, true);
        ResearchTreeScreenLayout.Layout smallExpanded =
                ResearchTreeScreenLayout.fullscreen(320, 240, true);
        ResearchTreeScreenLayout.Layout smallCollapsed =
                ResearchTreeScreenLayout.fullscreen(320, 240, false);

        for (ResearchTreeScreenLayout.Layout layout :
                List.of(wide, medium, smallExpanded, smallCollapsed)) {
            assertEquals(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, layout.mode());
            assertEquals(ResearchTreeScreenLayout.DetailsPlacement.OVERLAY, layout.detailsPlacement());
            assertTrue(layout.sidebar().isPresent());
            assertEquals(
                    new ResearchTreeScreenLayout.Rect(
                            0, 0, layout.screenWidth(), layout.screenHeight()),
                    layout.canvas());
            assertTrue(layout.canvas().overlaps(layout.sidebar().orElseThrow()));
            assertTrue(layout.canvas().overlaps(layout.toolbar()));
            assertUsable(layout);
        }
        assertEquals(smallExpanded, smallCollapsed);
    }

    @Test
    void fullscreenRejectsBoundsThatCannotFitTheMinimumInteractionSurface() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeScreenLayout.fullscreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH - 1,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT,
                        false));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeScreenLayout.fullscreen(
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                        ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT - 1,
                        false));
    }

    @Test
    void minimumFullscreenKeepsSearchAndNavigationControlsDisjoint() {
        ResearchTreeScreenLayout.Layout layout = ResearchTreeScreenLayout.fullscreen(
                ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH,
                ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT,
                false);

        assertTrue(layout.search().width() >= 48);
        assertFalse(layout.search().overlaps(layout.browseView()));
        assertFalse(layout.search().overlaps(layout.groupSelector()));
        assertUsable(layout);
    }

    @Test
    void fullscreenOverlayIsDeterministicAcrossFormerBreakpoints() {
        for (int[] size : List.of(
                new int[] {700, 360},
                new int[] {699, 360},
                new int[] {700, 359},
                new int[] {480, 300},
                new int[] {479, 300},
                new int[] {480, 299})) {
            ResearchTreeScreenLayout.Layout expanded =
                    ResearchTreeScreenLayout.fullscreen(size[0], size[1], true);
            ResearchTreeScreenLayout.Layout collapsed =
                    ResearchTreeScreenLayout.fullscreen(size[0], size[1], false);
            assertEquals(expanded, collapsed);
            assertEquals(ResearchTreeScreenLayout.DetailsPlacement.OVERLAY,
                    expanded.detailsPlacement());
        }
    }

    private static void assertUsable(ResearchTreeScreenLayout.Layout layout) {
        assertTrue(layout.toolbar().contains(layout.search()));
        assertTrue(layout.toolbar().contains(layout.expand()));
        if (layout.detailsPlacement() != ResearchTreeScreenLayout.DetailsPlacement.OVERLAY) {
            assertFalse(layout.canvas().overlaps(layout.details()));
            assertFalse(layout.canvas().overlaps(layout.toolbar()));
        } else {
            assertTrue(layout.canvas().overlaps(layout.toolbar()));
        }
        layout.sidebar().ifPresent(sidebar -> {
            assertEquals(
                    layout.detailsPlacement() == ResearchTreeScreenLayout.DetailsPlacement.OVERLAY,
                    sidebar.overlaps(layout.canvas()));
            assertFalse(sidebar.overlaps(layout.toolbar()));
        });
        assertTrue(layout.canvas().inside(layout.screenWidth(), layout.screenHeight()));
        assertTrue(layout.details().inside(layout.screenWidth(), layout.screenHeight()));
        List<ResearchTreeScreenLayout.Rect> controls = List.of(
                layout.search(),
                layout.zoomOut(),
                layout.zoomIn(),
                layout.showAll(),
                layout.browseView(),
                layout.groupSelector(),
                layout.expand());
        for (int left = 0; left < controls.size(); left++) {
            for (int right = left + 1; right < controls.size(); right++) {
                assertFalse(controls.get(left).overlaps(controls.get(right)));
            }
        }
    }
}
