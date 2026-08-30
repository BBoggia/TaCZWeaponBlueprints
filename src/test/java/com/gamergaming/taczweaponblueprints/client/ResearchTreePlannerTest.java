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

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
