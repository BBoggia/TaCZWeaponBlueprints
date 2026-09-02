package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreePlannerTest {
    @Test
    void plansOnlyTheTargetClosureAndRecommendsItsAvailableEntryStep() {
        ResourceLocation learned = id("test:learned");
        ResourceLocation entry = id("test:entry");
        ResourceLocation target = id("test:target");
        ResourceLocation unrelated = id("test:unrelated");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, learned, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 8, 1, 0),
                        node(1, entry, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 3, 2, 1),
                        node(2, target, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED, 10, 1, 1),
                        node(3, unrelated, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 1, 4, 0)),
                List.of(
                        new ResearchTreeGraph.Edge(learned, entry),
                        new ResearchTreeGraph.Edge(entry, target)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 3)
                .orElseThrow();

        assertEquals(Set.of(learned, entry, target), plan.pathNodeIds());
        assertFalse(plan.pathNodeIds().contains(unrelated));
        assertEquals(Set.of(
                new ResearchTreeGraph.Edge(learned, entry),
                new ResearchTreeGraph.Edge(entry, target)), plan.pathEdges());
        assertEquals(entry, plan.nextStepId().orElseThrow());
        assertEquals(13L, plan.remainingPoints());
        assertEquals(3L, plan.remainingIngredientTypes());
        assertEquals(2, plan.remainingSteps());
        assertEquals(1, plan.learnedSteps());
        assertTrue(plan.costComplete());
        assertFalse(plan.complete());
        assertFalse(plan.blocked());
    }

    @Test
    void learnedTargetCompletesTheObjectiveEvenForLegacyInconsistentHistory() {
        ResourceLocation prerequisite = id("test:prerequisite");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, prerequisite, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 4, 1, 0),
                        node(1, target, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 12, 2, 1)),
                List.of(new ResearchTreeGraph.Edge(prerequisite, target)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 0)
                .orElseThrow();

        assertTrue(plan.complete());
        assertEquals(0, plan.remainingSteps());
        assertEquals(0L, plan.remainingPoints());
        assertTrue(plan.nextStepId().isEmpty());
    }

    @Test
    void redactedAndPreviewStepsNeverLeakExactOrActionableInformation() {
        ResourceLocation hidden = ResearchTreeGraph.redactedNodeId(0);
        ResourceLocation preview = id("test:preview");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        redactedNode(0, hidden, JournalVisibility.SILHOUETTE),
                        node(1, preview, JournalVisibility.PREVIEW,
                                ResearchTreeGraph.Availability.PREVIEW, 4, 2, 1),
                        node(2, target, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED, 5, 1, 1)),
                List.of(
                        new ResearchTreeGraph.Edge(hidden, preview),
                        new ResearchTreeGraph.Edge(preview, target)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 100)
                .orElseThrow();

        assertEquals(3, plan.remainingSteps());
        assertEquals(1, plan.undisclosedSteps());
        assertEquals(1, plan.summaryOnlySteps());
        assertEquals(9L, plan.remainingPoints());
        assertEquals(3L, plan.remainingIngredientTypes());
        assertFalse(plan.costComplete());
        assertTrue(plan.nextStepId().isEmpty());
        assertTrue(plan.blocked());
    }

    @Test
    void anyOfRequirementSelectsOneDeterministicLowCostRoute() {
        ResourceLocation expensive = id("test:expensive");
        ResourceLocation affordable = id("test:affordable");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, expensive, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 8, 1, 0),
                        node(1, affordable, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 3, 1, 0),
                        node(2, target, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                5, 1, 2)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        target, 0, List.of(expensive, affordable), 0, false)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 3)
                .orElseThrow();

        assertEquals(Set.of(affordable, target), plan.pathNodeIds());
        assertEquals(Set.of(new ResearchTreeGraph.Edge(affordable, target)),
                plan.pathEdges());
        assertEquals(affordable, plan.nextStepId().orElseThrow());
        assertEquals(8L, plan.remainingPoints());
        assertEquals(2, plan.remainingSteps());
        assertEquals(0, plan.unresolvedRequirementGroups());
        assertTrue(plan.costComplete());
    }

    @Test
    void learnedAnyOfAlternativeStopsTheRouteAtLearnedHistory() {
        ResourceLocation learned = id("test:learned");
        ResourceLocation unused = id("test:unused");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        node(0, learned, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 8, 1, 0),
                        node(1, unused, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 3, 1, 0),
                        node(2, target, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 5, 1, 2)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        target, 0, List.of(learned, unused), 0, true)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 5)
                .orElseThrow();

        assertEquals(Set.of(learned, target), plan.pathNodeIds());
        assertFalse(plan.pathNodeIds().contains(unused));
        assertEquals(target, plan.nextStepId().orElseThrow());
        assertEquals(5L, plan.remainingPoints());
        assertEquals(1, plan.remainingSteps());
        assertEquals(1, plan.learnedSteps());
    }

    @Test
    void undisclosedRequirementWithoutAVisibleRouteIsCountedAsUnknown() {
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(new ResearchTreeGraph.Node(
                        0,
                        target,
                        "name.target",
                        "gun",
                        id("test:slot/target"),
                        JournalVisibility.FULL,
                        false,
                        true,
                        false,
                        5,
                        1,
                        0,
                        1,
                        ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        target, 0, List.of(), 1, false)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 100)
                .orElseThrow();

        assertEquals(Set.of(target), plan.pathNodeIds());
        assertEquals(2, plan.remainingSteps());
        assertEquals(1, plan.undisclosedSteps());
        assertEquals(1, plan.unresolvedRequirementGroups());
        assertFalse(plan.costComplete());
        assertTrue(plan.blocked());
    }

    @Test
    void sharedUnknownClosureIsCountedOnceAfterBranchesReconverge() {
        ResourceLocation shared = id("test:shared");
        ResourceLocation left = id("test:left");
        ResourceLocation right = id("test:right");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(
                List.of(
                        fullNode(0, shared, 0, 1),
                        fullNode(1, left, 1, 0),
                        fullNode(2, right, 1, 0),
                        fullNode(3, target, 2, 0)),
                List.of(
                        new ResearchTreeGraph.RequirementGroup(
                                shared, 0, List.of(), 1, false),
                        new ResearchTreeGraph.RequirementGroup(
                                left, 0, List.of(shared), 0, false),
                        new ResearchTreeGraph.RequirementGroup(
                                right, 0, List.of(shared), 0, false),
                        new ResearchTreeGraph.RequirementGroup(
                                target, 0, List.of(left), 0, false),
                        new ResearchTreeGraph.RequirementGroup(
                                target, 1, List.of(right), 0, false)));

        ResearchTreePlanner.Plan plan = ResearchTreePlanner.plan(graph, target, 100)
                .orElseThrow();

        assertEquals(Set.of(shared, left, right, target), plan.pathNodeIds());
        assertEquals(1, plan.unresolvedRequirementGroups());
        assertEquals(5, plan.remainingSteps());
        assertEquals(1, plan.undisclosedSteps());
    }

    @Test
    void rejectsInvalidInputsAndCannotTrackAnUnknownOrAnonymousTarget() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(redactedNode(
                        0, ResearchTreeGraph.redactedNodeId(0), JournalVisibility.NAME)),
                List.of());

        assertTrue(ResearchTreePlanner.plan(
                graph, ResearchTreeGraph.redactedNodeId(0), 0).isEmpty());
        assertTrue(ResearchTreePlanner.plan(graph, id("test:missing"), 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreePlanner.plan(null, id("test:any"), 0));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreePlanner.plan(graph, id("test:any"), -1));
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation blueprintId,
            JournalVisibility visibility,
            ResearchTreeGraph.Availability availability,
            int cost,
            int ingredientTypes,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "gun",
                id("test:slot/" + ordinal),
                visibility,
                availability == ResearchTreeGraph.Availability.LEARNED,
                visibility.revealsExactPolicy(),
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                cost,
                ingredientTypes,
                prerequisiteCount,
                0,
                availability);
    }

    private static ResearchTreeGraph.Node redactedNode(
            int ordinal,
            ResourceLocation blueprintId,
            JournalVisibility visibility) {
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                visibility.revealsName() ? "name.unknown" : ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResearchTreeGraph.Node fullNode(
            int ordinal,
            ResourceLocation blueprintId,
            int prerequisiteCount,
            int hiddenPrerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "gun",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                true,
                false,
                1,
                0,
                prerequisiteCount,
                hiddenPrerequisiteCount,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
