package com.gamergaming.taczweaponblueprints.client;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure, disclosure-safe policy for suggesting one next blueprint. Suggestions
 * only focus public server-published nodes; they never select or research one.
 */
public final class ResearchTreeRecommendationEngine {
    private ResearchTreeRecommendationEngine() {
    }

    public static Optional<Recommendation> recommend(
            ResearchTreeGraph graph,
            Set<ResourceLocation> navigableNodes,
            Set<ResourceLocation> preferredScope,
            int researchPoints) {
        if (graph == null || navigableNodes == null || preferredScope == null
                || researchPoints < 0
                || navigableNodes.stream().anyMatch(java.util.Objects::isNull)
                || preferredScope.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tree recommendation inputs");
        }
        Set<ResourceLocation> navigable =
                Set.copyOf(new LinkedHashSet<>(navigableNodes));
        Set<ResourceLocation> scope = Set.copyOf(new LinkedHashSet<>(preferredScope));
        ResearchTreeUnlockIndex unlocks = ResearchTreeUnlockIndex.create(graph);

        Comparator<ResearchTreeGraph.Node> order = Comparator
                .comparing((ResearchTreeGraph.Node node) ->
                        node.pointCost() <= researchPoints).reversed()
                .thenComparing(node -> scope.contains(node.blueprintId()),
                        Comparator.reverseOrder())
                .thenComparing(
                        node -> unlocks.unlocksAfterLearning(node.blueprintId()),
                        Comparator.reverseOrder())
                .thenComparingInt(ResearchTreeGraph.Node::pointCost)
                .thenComparingInt(ResearchTreeGraph.Node::ingredientTypeCount)
                .thenComparingInt(ResearchTreeGraph.Node::sourceOrdinal)
                .thenComparing(node -> node.blueprintId().toString());

        return graph.nodes().stream()
                .filter(node -> node.availability()
                        == ResearchTreeGraph.Availability.AVAILABLE)
                .filter(node -> node.visibility().revealsExactPolicy())
                .filter(node -> navigable.contains(node.blueprintId()))
                .min(order)
                .map(node -> {
                    int immediateUnlocks = unlocks.unlocksAfterLearning(
                            node.blueprintId());
                    boolean withinBudget = node.pointCost() <= researchPoints;
                    Reason reason = immediateUnlocks > 0
                            ? Reason.OPENS_PATHS
                            : withinBudget ? Reason.WITHIN_POINT_BUDGET : Reason.LOWEST_COST;
                    return new Recommendation(
                            node.blueprintId(),
                            node.pointCost(),
                            immediateUnlocks,
                            withinBudget,
                            scope.contains(node.blueprintId()),
                            reason);
                });
    }

    /**
     * Resolves the exact next step selected by authoritative route guidance.
     * Unlike the general recommendation policy, this never reorders multiple
     * simultaneously available route-frontier nodes.
     */
    public static Optional<Recommendation> recommendTrackedStep(
            ResearchTreeGraph graph,
            Set<ResourceLocation> navigableNodes,
            ResourceLocation nextStepId,
            int researchPoints) {
        if (graph == null || navigableNodes == null || nextStepId == null
                || researchPoints < 0
                || navigableNodes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "invalid tracked Research Tree recommendation inputs");
        }
        if (!navigableNodes.contains(nextStepId)) {
            return Optional.empty();
        }
        return recommend(
                graph,
                Set.of(nextStepId),
                Set.of(nextStepId),
                researchPoints);
    }

    public record Recommendation(
            ResourceLocation blueprintId,
            int pointCost,
            int immediateUnlockCount,
            boolean withinPointBudget,
            boolean inPreferredScope,
            Reason reason) {
        public Recommendation {
            if (blueprintId == null || pointCost < 0 || immediateUnlockCount < 0
                    || reason == null
                    || reason == Reason.WITHIN_POINT_BUDGET && !withinPointBudget
                    || reason == Reason.OPENS_PATHS && immediateUnlockCount == 0) {
                throw new IllegalArgumentException("invalid Research Tree recommendation");
            }
        }

        /** Compatibility accessor retained for existing UI integrations. */
        public int directUnlockCount() {
            return immediateUnlockCount;
        }
    }

    public enum Reason {
        OPENS_PATHS,
        WITHIN_POINT_BUDGET,
        LOWEST_COST
    }
}
