package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

class ResearchGuidancePhaseZeroFixtureTest {
    @Test
    void andOfAnyOfFixtureRetainsTwoSelectedBranchesAndOneMerge() {
        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(
                        ResearchGuidancePhaseZeroFixture.andOfAnyOfRoute(),
                        ResearchGuidancePhaseZeroFixture.MERGE_TARGET,
                        100)
                .orElseThrow();

        assertEquals(Set.of(
                ResearchGuidancePhaseZeroFixture.LEFT_CHEAP,
                ResearchGuidancePhaseZeroFixture.RIGHT_CHEAP,
                ResearchGuidancePhaseZeroFixture.MERGE_TARGET), plan.pathNodeIds());
        assertEquals(Set.of(
                new ResearchTreeGraph.Edge(
                        ResearchGuidancePhaseZeroFixture.LEFT_CHEAP,
                        ResearchGuidancePhaseZeroFixture.MERGE_TARGET),
                new ResearchTreeGraph.Edge(
                        ResearchGuidancePhaseZeroFixture.RIGHT_CHEAP,
                        ResearchGuidancePhaseZeroFixture.MERGE_TARGET)), plan.pathEdges());
        assertEquals(10L, plan.remainingPoints());
        assertEquals(3L, plan.remainingIngredientTypes());
        assertEquals(3, plan.remainingSteps());
        assertEquals(0, plan.unresolvedRequirementGroups());
        assertTrue(plan.costComplete());
    }

    @Test
    void equalCostClientEstimateUsesOneStableLexicographicAlternative() {
        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(
                        ResearchGuidancePhaseZeroFixture.equalCostAlternatives(),
                        ResearchGuidancePhaseZeroFixture.TIED_TARGET,
                        100)
                .orElseThrow();

        assertEquals(Set.of(
                ResearchGuidancePhaseZeroFixture.ALPHA,
                ResearchGuidancePhaseZeroFixture.TIED_TARGET), plan.pathNodeIds());
        assertEquals(Set.of(new ResearchTreeGraph.Edge(
                ResearchGuidancePhaseZeroFixture.ALPHA,
                ResearchGuidancePhaseZeroFixture.TIED_TARGET)), plan.pathEdges());
        assertFalse(plan.pathNodeIds().contains(ResearchGuidancePhaseZeroFixture.BETA));
    }

    @Test
    void costModeFixturesExposeOnlyEnabledEconomicChannels() {
        Map<ResearchCostMode, ResearchSelectionPreview> previews =
                ResearchGuidancePhaseZeroFixture.previewsByCostMode();

        assertEquals(Set.of(ResearchCostMode.values()), previews.keySet());

        ResearchSelectionPreview both = previews.get(ResearchCostMode.POINTS_AND_ITEMS);
        assertTrue(both.pointsEnabled());
        assertTrue(both.materialsEnabled());
        assertEquals(6, both.pointCost());
        assertEquals(1, both.ingredientTypeCount());
        assertTrue(both.researchable());

        ResearchSelectionPreview points = previews.get(ResearchCostMode.POINTS_ONLY);
        assertTrue(points.pointsEnabled());
        assertFalse(points.materialsEnabled());
        assertEquals(6, points.pointCost());
        assertTrue(points.ingredients().isEmpty());
        assertTrue(points.researchable());

        ResearchSelectionPreview items = previews.get(ResearchCostMode.ITEMS_ONLY);
        assertFalse(items.pointsEnabled());
        assertTrue(items.materialsEnabled());
        assertEquals(0, items.pointCost());
        assertEquals(1, items.ingredientTypeCount());
        assertFalse(items.ingredientsSatisfied());
        assertFalse(items.researchable());
    }

    @Test
    void creativeFixturePreservesRealRequirementsWithoutPretendingToOwnThem() {
        ResearchSelectionPreview preview =
                ResearchGuidancePhaseZeroFixture.creativeBypassPreview();

        assertTrue(preview.creativeBypass());
        assertTrue(preview.researchable());
        assertTrue(preview.ingredientsSatisfied());
        assertEquals(6, preview.pointCost());
        assertEquals(0, preview.pointBalance());
        assertEquals(4, preview.ingredients().get(0).required());
        assertEquals(0, preview.ingredients().get(0).inventoryAvailable());
    }

    @Test
    void maximumFixtureOccupiesTheExistingHardCeilingWithoutEdges() {
        ResearchTreeGraph graph = ResearchGuidancePhaseZeroFixture.maximumIndependentGraph();

        assertEquals(ResearchTreeGraph.MAX_NODES, graph.nodes().size());
        assertTrue(graph.edges().isEmpty());
        assertTrue(graph.requirementGroups().isEmpty());
        assertEquals(
                "guidance:maximum/" + (ResearchTreeGraph.MAX_NODES - 1),
                graph.nodes().get(graph.nodes().size() - 1).blueprintId().toString());
    }
}

