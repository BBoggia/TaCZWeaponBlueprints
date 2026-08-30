package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure client-side planning over one disclosure-filtered server publication.
 * A plan never invents hidden costs or changes research authority.
 */
public final class ResearchTreePlanner {
    private ResearchTreePlanner() {
    }

    public static Optional<Plan> plan(
            ResearchTreeGraph graph,
            ResourceLocation targetId,
            int researchPoints) {
        if (graph == null || researchPoints < 0) {
            throw new IllegalArgumentException("invalid Research Tree planner inputs");
        }
        Optional<ResearchTreeGraph.Node> target = graph.node(targetId)
                .filter(node -> node.visibility().revealsIdentity());
        if (target.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashSet<ResourceLocation> pathNodeIds = new LinkedHashSet<>();
        collectRequirements(graph, targetId, pathNodeIds);
        LinkedHashSet<ResearchTreeGraph.Edge> pathEdges = new LinkedHashSet<>();
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            if (pathNodeIds.contains(edge.prerequisiteId())
                    && pathNodeIds.contains(edge.dependentId())) {
                pathEdges.add(edge);
            }
        }

        boolean complete = target.orElseThrow().learned();
        long remainingPoints = 0L;
        long remainingIngredientTypes = 0L;
        int remainingSteps = 0;
        int learnedSteps = 0;
        int undisclosedSteps = 0;
        int summaryOnlySteps = 0;
        LinkedHashSet<ResourceLocation> recommendationCandidates = new LinkedHashSet<>();
        if (!complete) {
            for (ResearchTreeGraph.Node node : graph.nodes()) {
                if (!pathNodeIds.contains(node.blueprintId())) {
                    continue;
                }
                if (node.learned()) {
                    learnedSteps++;
                    continue;
                }
                remainingSteps++;
                if (!node.visibility().revealsIdentity()) {
                    undisclosedSteps++;
                } else if (!node.visibility().revealsExactPolicy()) {
                    summaryOnlySteps++;
                }
                if (node.visibility().revealsResearchSummary()) {
                    remainingPoints = Math.addExact(remainingPoints, node.pointCost());
                    remainingIngredientTypes = Math.addExact(
                            remainingIngredientTypes, node.ingredientTypeCount());
                }
                if (node.availability() == ResearchTreeGraph.Availability.AVAILABLE
                        && node.visibility().revealsExactPolicy()) {
                    recommendationCandidates.add(node.blueprintId());
                }
            }
        } else {
            learnedSteps = pathNodeIds.size();
        }

        Optional<ResourceLocation> nextStep = Optional.empty();
        if (!recommendationCandidates.isEmpty()) {
            ResearchTreeGraph pathGraph = graph.inducedSubgraph(pathNodeIds);
            nextStep = ResearchTreeRecommendationEngine.recommend(
                            pathGraph,
                            recommendationCandidates,
                            pathNodeIds,
                            researchPoints)
                    .map(ResearchTreeRecommendationEngine.Recommendation::blueprintId);
        }
        boolean costComplete = undisclosedSteps == 0 && summaryOnlySteps == 0;
        return Optional.of(new Plan(
                targetId,
                pathNodeIds,
                pathEdges,
                nextStep,
                remainingPoints,
                remainingIngredientTypes,
                remainingSteps,
                learnedSteps,
                undisclosedSteps,
                summaryOnlySteps,
                costComplete,
                complete));
    }

    private static void collectRequirements(
            ResearchTreeGraph graph,
            ResourceLocation blueprintId,
            LinkedHashSet<ResourceLocation> collected) {
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(blueprintId);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!collected.add(current)) {
                continue;
            }
            for (ResourceLocation prerequisite : graph.prerequisitesOf(current)) {
                if (!collected.contains(prerequisite)) {
                    pending.addLast(prerequisite);
                }
            }
        }
    }

    public record Plan(
            ResourceLocation targetId,
            Set<ResourceLocation> pathNodeIds,
            Set<ResearchTreeGraph.Edge> pathEdges,
            Optional<ResourceLocation> nextStepId,
            long remainingPoints,
            long remainingIngredientTypes,
            int remainingSteps,
            int learnedSteps,
            int undisclosedSteps,
            int summaryOnlySteps,
            boolean costComplete,
            boolean complete) {
        public Plan {
            pathNodeIds = immutableSet(pathNodeIds, "node");
            pathEdges = immutableSet(pathEdges, "edge");
            nextStepId = nextStepId == null ? Optional.empty() : nextStepId;
            Set<ResourceLocation> validatedPathNodeIds = pathNodeIds;
            boolean edgeLeavesPath = pathEdges.stream().anyMatch(edge ->
                    !validatedPathNodeIds.contains(edge.prerequisiteId())
                            || !validatedPathNodeIds.contains(edge.dependentId()));
            if (targetId == null || !pathNodeIds.contains(targetId)
                    || (nextStepId.isPresent()
                            && !pathNodeIds.contains(nextStepId.orElseThrow()))
                    || edgeLeavesPath
                    || remainingPoints < 0L || remainingIngredientTypes < 0L
                    || remainingSteps < 0 || learnedSteps < 0
                    || undisclosedSteps < 0 || summaryOnlySteps < 0
                    || undisclosedSteps + summaryOnlySteps > remainingSteps
                    || (!complete && remainingSteps + learnedSteps != pathNodeIds.size())
                    || complete && (remainingSteps != 0
                            || remainingPoints != 0L
                            || remainingIngredientTypes != 0L
                            || nextStepId.isPresent()
                            || learnedSteps != pathNodeIds.size())
                    || costComplete != (undisclosedSteps == 0 && summaryOnlySteps == 0)) {
                throw new IllegalArgumentException("invalid Research Tree plan");
            }
        }

        public boolean blocked() {
            return !complete && remainingSteps > 0 && nextStepId.isEmpty();
        }

        private static <T> Set<T> immutableSet(Set<T> values, String description) {
            if (values == null || values.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Research Tree plan contains an invalid " + description);
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }
}
