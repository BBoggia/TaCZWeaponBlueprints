package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchTreeContextCardLayoutTest {
    @Test
    void cardPrefersTheRightThenFlipsAtTheScreenEdge() {
        ResearchTreeContextCardLayout.Layout right = ResearchTreeContextCardLayout.place(
                640, 360,
                new ResearchTreeContextCardLayout.Anchor(180, 150, 24, 24),
                List.of(), 3, true);
        ResearchTreeContextCardLayout.Layout left = ResearchTreeContextCardLayout.place(
                640, 360,
                new ResearchTreeContextCardLayout.Anchor(600, 150, 24, 24),
                List.of(), 3, true);

        assertEquals(ResearchTreeContextCardLayout.Placement.RIGHT, right.placement());
        assertEquals(ResearchTreeContextCardLayout.Placement.LEFT, left.placement());
        assertTrue(right.card().inside(640, 360));
        assertTrue(left.card().inside(640, 360));
    }

    @Test
    void exactSixIngredientCardUsesAContainedTwoColumnGrid() {
        ResearchTreeContextCardLayout.Layout layout = ResearchTreeContextCardLayout.place(
                320, 240,
                new ResearchTreeContextCardLayout.Anchor(36, 100, 24, 24),
                List.of(), 6, true);

        assertTrue(layout.exactPreview());
        assertEquals(2, layout.columns());
        assertEquals(6, layout.ingredients().size());
        assertTrue(layout.card().contains(layout.action()));
        for (int first = 0; first < layout.ingredients().size(); first++) {
            assertTrue(layout.card().contains(layout.ingredients().get(first)));
            for (int second = first + 1; second < layout.ingredients().size(); second++) {
                assertFalse(layout.ingredients().get(first).overlaps(
                        layout.ingredients().get(second)));
            }
        }
    }

    @Test
    void anonymousCardCannotAccidentallyAllocateExactActionGeometry() {
        ResearchTreeContextCardLayout.Layout layout = ResearchTreeContextCardLayout.place(
                320, 240,
                new ResearchTreeContextCardLayout.Anchor(120, 100, 24, 24),
                List.of(), 0, false);

        assertFalse(layout.exactPreview());
        assertTrue(layout.ingredients().isEmpty());
        assertEquals(null, layout.action());
        assertEquals(null, layout.balance());
    }

    @Test
    void obstaclesInfluencePlacementAndOffscreenNodesReceiveANonOverlappingChip() {
        ResearchTreeScreenLayout.Rect obstacle = new ResearchTreeScreenLayout.Rect(
                240, 60, 224, 170);
        ResearchTreeContextCardLayout.Layout layout = ResearchTreeContextCardLayout.place(
                640, 360,
                new ResearchTreeContextCardLayout.Anchor(180, 150, 24, 24),
                List.of(obstacle), 2, true);
        assertFalse(layout.card().overlaps(obstacle));

        ResearchTreeContextCardLayout.Anchor offscreen =
                new ResearchTreeContextCardLayout.Anchor(-80, 100, 24, 24);
        assertFalse(ResearchTreeContextCardLayout.isAnchorVisible(
                offscreen, 640, 360, List.of()));
        ArrayList<ResearchTreeScreenLayout.Rect> avoided = new ArrayList<>();
        avoided.add(layout.card());
        ResearchTreeScreenLayout.Rect chip = ResearchTreeContextCardLayout.returnChip(
                640, 360, avoided);
        assertTrue(chip.inside(640, 360));
        assertFalse(chip.overlaps(layout.card()));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeContextCardLayout.place(
                        320, 240,
                        new ResearchTreeContextCardLayout.Anchor(10, 10, 24, 24),
                        List.of(), 7, true));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeContextCardLayout.place(
                        259, 240,
                        new ResearchTreeContextCardLayout.Anchor(10, 10, 24, 24),
                        List.of(), 0, false));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeContextCardLayout.place(
                        320, 240,
                        new ResearchTreeContextCardLayout.Anchor(10, 10, 24, 24),
                        List.of(), 1, false));
    }
}
