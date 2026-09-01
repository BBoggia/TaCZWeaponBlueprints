package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;

import net.minecraft.resources.ResourceLocation;

class ResearchSelectionPreviewTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:rifle");
    private static final ResourceLocation PAPER = new ResourceLocation("minecraft:paper");

    @Test
    void researchStateIsRepresentedWithoutWorkstationTurnInFields() {
        ResearchSelectionPreview research = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                8,
                12,
                true,
                true,
                true,
                true,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(PAPER), Optional.empty(), 4, 4)));

        assertEquals(Optional.of(BLUEPRINT), research.blueprintId());
        assertEquals(1, research.ingredients().size());
        assertEquals(1, research.unlockCount());
        assertTrue(research.researchable());
    }

    @Test
    void boundedEmptyPreviewHasNoSelectionOrPolicyDetails() {
        assertTrue(ResearchSelectionPreview.EMPTY.blueprintId().isEmpty());
        assertTrue(ResearchSelectionPreview.EMPTY.ingredients().isEmpty());
    }

    @Test
    void rejectsDetailsWithoutASelectionAndInconsistentMaterialSummaries() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.empty(), 1, 10, false, false, false, false, false, List.of()));

        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                0,
                true,
                true,
                true,
                false,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(PAPER), Optional.empty(), 1, 0))));
    }

    @Test
    void rejectsAReadySelectionThatCannotPayItsPointCost() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                8,
                7,
                true,
                true,
                true,
                true,
                false,
                List.of()));
    }

    @Test
    void pathPreviewCanBoundItsVisibleMaterialDetailsWithoutClaimingCompletion() {
        ResearchSelectionPreview path = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                18,
                20,
                true,
                false,
                true,
                false,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(PAPER), Optional.empty(), 8, 8)),
                4,
                3);

        assertTrue(path.pathPurchase());
        assertEquals(2, path.additionalIngredientTypes());
    }

    @Test
    void boundedPathPlanningFailureRequiresASelectedDisabledPreview() {
        ResearchSelectionPreview failure = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                20,
                false,
                true,
                true,
                false,
                false,
                List.of(),
                1,
                0,
                ResearchSelectionPreview.PathPlanningState.ROUTE_TOO_COMPLEX);

        assertEquals(
                ResearchSelectionPreview.PathPlanningState.ROUTE_TOO_COMPLEX,
                failure.pathPlanningState());
        assertEquals(0, failure.pointCost());
        assertTrue(failure.ingredients().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                20,
                true,
                true,
                true,
                false,
                false,
                List.of(),
                1,
                0,
                ResearchSelectionPreview.PathPlanningState.PATH_TOO_LARGE));
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                1,
                20,
                false,
                true,
                true,
                false,
                false,
                List.of(),
                1,
                0,
                ResearchSelectionPreview.PathPlanningState.PATH_TOO_LARGE));
    }

    @Test
    void inactiveCostChannelsCannotLeakEffectiveCostsIntoThePreview() {
        ResearchSelectionPreview itemsOnly = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                20,
                true,
                true,
                true,
                true,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(PAPER), Optional.empty(), 2, 2)),
                1,
                1,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.ITEMS_ONLY);

        assertEquals(ResearchCostMode.ITEMS_ONLY, itemsOnly.costMode());
        assertTrue(!itemsOnly.pointsEnabled());
        assertTrue(itemsOnly.materialsEnabled());
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT), 1, 20, true, true, true, true, false,
                List.of(), 1, 0, ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.ITEMS_ONLY));
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT), 1, 20, true, true, true, true, false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(PAPER), Optional.empty(), 1, 1)),
                1, 1, ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.POINTS_ONLY));
    }
}
