package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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
}
