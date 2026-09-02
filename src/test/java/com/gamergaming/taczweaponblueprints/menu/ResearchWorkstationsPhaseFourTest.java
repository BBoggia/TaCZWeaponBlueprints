package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchBenchPresentationPolicy;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Regression gates for the Phase 4 player-facing Research Bench cutover. */
class ResearchWorkstationsPhaseFourTest {
    @Test
    void researchBenchHasOnePermanentPlayerFacingPresentation() {
        assertTrue(ResearchBenchPresentationPolicy.permanentFullscreen());
        assertEquals(
                ResearchBenchPresentationPolicy.ExitAction.CLOSE_SCREEN,
                ResearchBenchPresentationPolicy.fullscreenExitAction());
    }

    @Test
    void permanentTreeOwnsEveryPixelAtSupportedReleaseSizes() {
        for (int[] size : List.of(
                new int[] {320, 240},
                new int[] {854, 480},
                new int[] {1920, 1080},
                new int[] {2560, 1080})) {
            ResearchTreeScreenLayout.Layout layout =
                    ResearchTreeScreenLayout.fullscreen(size[0], size[1], false);

            assertEquals(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, layout.mode());
            assertEquals(
                    new ResearchTreeScreenLayout.Rect(0, 0, size[0], size[1]),
                    layout.canvas());
            assertEquals(
                    ResearchTreeScreenLayout.DetailsPlacement.OVERLAY,
                    layout.detailsPlacement());
            assertTrue(layout.sidebar().isPresent());
            assertTrue(layout.canvas().overlaps(layout.toolbar()));
            assertTrue(layout.canvas().overlaps(layout.sidebar().orElseThrow()));
        }
    }

    @Test
    void presentationOnlyCutoverDoesNotChangeTheWireContract() {
        assertEquals("47", NetworkHandler.PROTOCOL_VERSION);
    }
}
