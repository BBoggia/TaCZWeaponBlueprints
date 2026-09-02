package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;

import net.minecraft.resources.ResourceLocation;

class ResearchGoalProgressPresenterTest {
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation TARGET = id("test:target");

    @Test
    void absentGuidanceIsAQuietCheckingState() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.empty());

        assertEquals(ResearchGoalProgressPresenter.Status.CHECKING, presentation.status());
        assertTrue(presentation.points().isEmpty());
        assertTrue(presentation.materials().isEmpty());
        assertTrue(presentation.displayedMaterials().isEmpty());
    }

    @Test
    void terminalGuidanceFailureIsNotPresentedAsEndlessChecking() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.empty(), true);

        assertEquals(ResearchGoalProgressPresenter.Status.ROUTE_UNAVAILABLE,
                presentation.status());
    }

    @Test
    void activeCostModesExposeOnlyTheirConfiguredProgressChannels() {
        ResearchGoalProgressPresenter.Presentation points =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.MISSING_POINTS,
                        ResearchCostMode.POINTS_ONLY,
                        10,
                        4,
                        false,
                        true,
                        0,
                        0,
                        0,
                        0,
                        List.of())));
        assertEquals(new ResearchGoalProgressPresenter.Progress(4, 10),
                points.points().orElseThrow());
        assertTrue(points.materials().isEmpty());

        ResearchGoalProgressPresenter.Presentation materials =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.MISSING_MATERIALS,
                        ResearchCostMode.ITEMS_ONLY,
                        0,
                        0,
                        false,
                        true,
                        1,
                        3,
                        1,
                        1,
                        List.of(material(3, 1)))));
        assertTrue(materials.points().isEmpty());
        assertEquals(new ResearchGoalProgressPresenter.Progress(1, 3),
                materials.materials().orElseThrow());
    }

    @Test
    void aggregateProgressRemainsExactWhenIngredientRowsAreTruncated() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.MISSING_MATERIALS,
                        ResearchCostMode.POINTS_AND_ITEMS,
                        5,
                        5,
                        false,
                        true,
                        3,
                        8,
                        5,
                        1,
                        List.of(material(2, 2)))));

        assertEquals(new ResearchGoalProgressPresenter.Progress(5, 8),
                presentation.materials().orElseThrow());
        assertEquals(1, presentation.missingMaterialTypes());
        assertEquals(2, presentation.additionalMaterialRows());
        assertEquals(1, presentation.displayedMaterials().size());
    }

    @Test
    void creativeBypassDoesNotFabricateEconomicProgress() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.AFFORDABLE,
                        ResearchCostMode.POINTS_AND_ITEMS,
                        10,
                        0,
                        true,
                        true,
                        1,
                        3,
                        0,
                        1,
                        List.of(material(3, 0)))));

        assertEquals(ResearchGoalProgressPresenter.Status.READY, presentation.status());
        assertTrue(presentation.costBypassed());
        assertTrue(presentation.points().isEmpty());
        assertTrue(presentation.materials().isEmpty());
    }

    @Test
    void satisfiedResourcesRemainDistinctFromTransactionCapacity() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.AFFORDABLE,
                        ResearchCostMode.POINTS_AND_ITEMS,
                        5,
                        9,
                        false,
                        false,
                        1,
                        2,
                        2,
                        0,
                        List.of(material(2, 2)))));

        assertEquals(ResearchGoalProgressPresenter.Status.TRANSACTION_BLOCKED,
                presentation.status());
        assertEquals(new ResearchGoalProgressPresenter.Progress(5, 5),
                presentation.points().orElseThrow());
        assertEquals(new ResearchGoalProgressPresenter.Progress(2, 2),
                presentation.materials().orElseThrow());
        assertFalse(presentation.costBypassed());
    }

    @Test
    void capacityFailureRemainsVisibleBeforeResourcesAreSatisfied() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS,
                        ResearchCostMode.POINTS_AND_ITEMS,
                        10,
                        2,
                        false,
                        false,
                        1,
                        3,
                        1,
                        1,
                        List.of(material(3, 1)))));

        assertEquals(ResearchGoalProgressPresenter.Status.TRANSACTION_BLOCKED,
                presentation.status());
        assertEquals(new ResearchGoalProgressPresenter.Progress(2, 10),
                presentation.points().orElseThrow());
        assertEquals(new ResearchGoalProgressPresenter.Progress(1, 3),
                presentation.materials().orElseThrow());
    }

    @Test
    void snapshotRejectsContradictoryAggregateAndCostModeEvidence() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                ResearchGuidanceSnapshot.State.MISSING_MATERIALS,
                ResearchCostMode.POINTS_AND_ITEMS,
                0,
                0,
                false,
                true,
                1,
                1,
                0,
                1,
                List.of(material(2, 0))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                ResearchGuidanceSnapshot.State.MISSING_POINTS,
                ResearchCostMode.ITEMS_ONLY,
                1,
                0,
                false,
                true,
                0,
                0,
                0,
                0,
                List.of()));
    }

    @Test
    void zeroConfiguredCostDoesNotProduceZeroOverZeroProgress() {
        ResearchGoalProgressPresenter.Presentation presentation =
                ResearchGoalProgressPresenter.present(Optional.of(snapshot(
                        ResearchGuidanceSnapshot.State.AFFORDABLE,
                        ResearchCostMode.POINTS_ONLY,
                        0,
                        7,
                        false,
                        true,
                        0,
                        0,
                        0,
                        0,
                        List.of())));

        assertEquals(ResearchGoalProgressPresenter.Status.READY, presentation.status());
        assertTrue(presentation.points().isEmpty());
        assertTrue(presentation.materials().isEmpty());
    }

    private static ResearchGuidanceSnapshot snapshot(
            ResearchGuidanceSnapshot.State state,
            ResearchCostMode costMode,
            int pointCost,
            int pointBalance,
            boolean bypassed,
            boolean capacity,
            int totalMaterialTypes,
            int totalMaterialUnits,
            int allocatedMaterialUnits,
            int missingMaterialTypes,
            List<ResearchGuidanceSnapshot.MaterialProgress> materials) {
        return new ResearchGuidanceSnapshot(
                TARGET,
                state,
                pointCost,
                pointBalance,
                costMode,
                bypassed,
                capacity,
                totalMaterialTypes,
                totalMaterialUnits,
                allocatedMaterialUnits,
                missingMaterialTypes,
                materials,
                List.of(ROOT, TARGET),
                List.of(ROOT, TARGET),
                List.of(),
                Optional.of(ROOT));
    }

    private static ResearchGuidanceSnapshot.MaterialProgress material(
            int required,
            int allocated) {
        return new ResearchGuidanceSnapshot.MaterialProgress(
                List.of(id("minecraft:paper")),
                Optional.empty(),
                required,
                allocated);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
