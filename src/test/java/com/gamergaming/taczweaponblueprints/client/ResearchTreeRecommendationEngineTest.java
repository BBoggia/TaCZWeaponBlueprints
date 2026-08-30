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

class ResearchTreeRecommendationEngineTest {
    @Test
    void prefersAnAffordableCurrentTreeNodeThatOpensMorePaths() {
        ResourceLocation local = id("test:local");
        ResourceLocation global = id("test:global");
        ResourceLocation childA = id("test:child_a");
        ResourceLocation childB = id("test:child_b");
        ResearchTreeGraph graph = graph(
                List.of(
                        available(0, local, 4, 1),
                        available(1, global, 2, 0),
                        locked(2, childA, local),
                        locked(3, childB, local)),
                List.of(
                        new ResearchTreeGraph.Edge(local, childA),
                        new ResearchTreeGraph.Edge(local, childB)));

        ResearchTreeRecommendationEngine.Recommendation recommendation =
                ResearchTreeRecommendationEngine.recommend(
                        graph, ids(graph), Set.of(local), 4).orElseThrow();

        assertEquals(local, recommendation.blueprintId());
        assertEquals(2, recommendation.immediateUnlockCount());
        assertTrue(recommendation.withinPointBudget());
        assertTrue(recommendation.inPreferredScope());
        assertEquals(
                ResearchTreeRecommendationEngine.Reason.OPENS_PATHS,
                recommendation.reason());
    }

    @Test
    void anAndMergeCountsOnlyWhenTheCandidateIsItsLastMissingRequirement() {
        ResourceLocation candidate = id("test:candidate");
        ResourceLocation other = id("test:other");
        ResourceLocation merge = id("test:merge");
        List<ResearchTreeGraph.Edge> edges = List.of(
                new ResearchTreeGraph.Edge(candidate, merge),
                new ResearchTreeGraph.Edge(other, merge));
        ResearchTreeGraph bothMissing = graph(
                List.of(
                        available(0, candidate, 2, 0),
                        available(1, other, 2, 0),
                        node(2, merge, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                4, 0, 2)),
                edges);

        ResearchTreeRecommendationEngine.Recommendation beforeOther =
                ResearchTreeRecommendationEngine.recommend(
                        bothMissing,
                        Set.of(candidate, other),
                        Set.of(candidate),
                        10).orElseThrow();
        assertEquals(candidate, beforeOther.blueprintId());
        assertEquals(0, beforeOther.immediateUnlockCount());
        assertEquals(ResearchTreeRecommendationEngine.Reason.WITHIN_POINT_BUDGET,
                beforeOther.reason());

        ResearchTreeGraph oneMissing = graph(
                List.of(
                        available(0, candidate, 2, 0),
                        node(1, other, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 2, 0),
                        node(2, merge, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                4, 0, 2)),
                edges);
        ResearchTreeRecommendationEngine.Recommendation afterOther =
                ResearchTreeRecommendationEngine.recommend(
                        oneMissing,
                        Set.of(candidate),
                        Set.of(),
                        10).orElseThrow();
        assertEquals(1, afterOther.immediateUnlockCount());
        assertEquals(ResearchTreeRecommendationEngine.Reason.OPENS_PATHS,
                afterOther.reason());
    }

    @Test
    void learnedPrerequisitesCountOnlyCurrentlyAvailableDependents() {
        ResourceLocation left = id("test:left");
        ResourceLocation right = id("test:right");
        ResourceLocation merge = id("test:merge");
        ResearchTreeGraph graph = graph(
                List.of(
                        node(0, left, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 2, 0),
                        node(1, right, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 2, 0),
                        node(2, merge, JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.AVAILABLE, 4, 0, 2)),
                List.of(
                        new ResearchTreeGraph.Edge(left, merge),
                        new ResearchTreeGraph.Edge(right, merge)));

        ResearchTreeUnlockIndex unlocks = ResearchTreeUnlockIndex.create(graph);
        assertEquals(1, unlocks.immediateUnlockCount(left));
        assertEquals(1, unlocks.immediateUnlockCount(right));
    }

    @Test
    void affordabilityWinsBeforeCurrentViewAndUnavailableNodesAreIgnored() {
        ResourceLocation expensiveLocal = id("test:expensive_local");
        ResourceLocation affordableGlobal = id("test:affordable_global");
        ResourceLocation locked = id("test:locked");
        ResearchTreeGraph graph = graph(
                List.of(
                        available(0, expensiveLocal, 8, 0),
                        available(1, affordableGlobal, 2, 0),
                        locked(2, locked, affordableGlobal)),
                List.of(new ResearchTreeGraph.Edge(affordableGlobal, locked)));

        ResearchTreeRecommendationEngine.Recommendation recommendation =
                ResearchTreeRecommendationEngine.recommend(
                        graph, ids(graph), Set.of(expensiveLocal), 2).orElseThrow();

        assertEquals(affordableGlobal, recommendation.blueprintId());
        assertTrue(recommendation.withinPointBudget());
        assertFalse(recommendation.inPreferredScope());
    }

    @Test
    void fallsBackDeterministicallyToTheCheapestPublishedAvailableNode() {
        ResourceLocation later = id("test:later");
        ResourceLocation first = id("test:first");
        ResearchTreeGraph graph = graph(
                List.of(
                        available(0, first, 6, 2),
                        available(1, later, 6, 1)),
                List.of());

        ResearchTreeRecommendationEngine.Recommendation recommendation =
                ResearchTreeRecommendationEngine.recommend(
                        graph, ids(graph), Set.of(), 0).orElseThrow();

        assertEquals(later, recommendation.blueprintId());
        assertEquals(ResearchTreeRecommendationEngine.Reason.LOWEST_COST,
                recommendation.reason());
    }

    @Test
    void learnedLockedPreviewAndRedactedNodesNeverBecomeSuggestions() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, id("test:learned"), JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.LEARNED, 0, 0),
                        node(1, id("test:locked"), JournalVisibility.FULL,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED, 4, 0),
                        node(2, id("test:preview"), JournalVisibility.PREVIEW,
                                ResearchTreeGraph.Availability.PREVIEW, 4, 0),
                        new ResearchTreeGraph.Node(
                                3,
                                ResearchTreeGraph.redactedNodeId(3),
                                ResearchTreeGraph.REDACTED_NAME_KEY,
                                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                                JournalVisibility.SILHOUETTE,
                                false,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                ResearchTreeGraph.Availability.REDACTED)),
                List.of());

        assertTrue(ResearchTreeRecommendationEngine.recommend(
                graph, ids(graph), Set.of(), 100).isEmpty());
    }

    @Test
    void availableNodesOutsideThePublicNavigationBoundaryAreIgnored() {
        ResourceLocation unpublished = id("test:unpublished");
        ResourceLocation publicNode = id("test:public");
        ResearchTreeGraph graph = graph(
                List.of(
                        available(0, unpublished, 1, 0),
                        available(1, publicNode, 5, 0)),
                List.of());

        ResearchTreeRecommendationEngine.Recommendation recommendation =
                ResearchTreeRecommendationEngine.recommend(
                        graph, Set.of(publicNode), Set.of(), 10).orElseThrow();

        assertEquals(publicNode, recommendation.blueprintId());
    }

    @Test
    void rejectsInvalidInputsAndContradictoryResults() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeRecommendationEngine.recommend(
                        null, Set.of(), Set.of(), 0));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTreeRecommendationEngine.recommend(
                        ResearchTreeGraph.EMPTY, Set.of(), Set.of(), -1));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeRecommendationEngine.Recommendation(
                        id("test:a"), 1, 0, false, false,
                        ResearchTreeRecommendationEngine.Reason.WITHIN_POINT_BUDGET));
    }

    private static ResearchTreeGraph graph(
            List<ResearchTreeGraph.Node> nodes,
            List<ResearchTreeGraph.Edge> edges) {
        return new ResearchTreeGraph(nodes, edges);
    }

    private static Set<ResourceLocation> ids(ResearchTreeGraph graph) {
        return graph.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ResearchTreeGraph.Node available(
            int ordinal,
            ResourceLocation id,
            int cost,
            int ingredientTypes) {
        return node(
                ordinal,
                id,
                JournalVisibility.FULL,
                ResearchTreeGraph.Availability.AVAILABLE,
                cost,
                ingredientTypes);
    }

    private static ResearchTreeGraph.Node locked(
            int ordinal,
            ResourceLocation id,
            ResourceLocation ignoredPrerequisite) {
        return node(
                ordinal,
                id,
                JournalVisibility.FULL,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                4,
                0,
                1);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            JournalVisibility visibility,
            ResearchTreeGraph.Availability availability,
            int cost,
            int ingredientTypes) {
        return node(ordinal, id, visibility, availability, cost, ingredientTypes, 0);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            JournalVisibility visibility,
            ResearchTreeGraph.Availability availability,
            int cost,
            int ingredientTypes,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "gun",
                id("test:slot/" + id.getPath()),
                visibility,
                availability == ResearchTreeGraph.Availability.LEARNED,
                visibility.revealsExactPolicy()
                        && availability != ResearchTreeGraph.Availability.DISCOVERY_REQUIRED,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                cost,
                ingredientTypes,
                prerequisiteCount,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
