package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
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

        Route route = selectRoute(
                graph,
                targetId,
                new LinkedHashMap<>(),
                new LinkedHashSet<>());
        Set<ResourceLocation> pathNodeIds = route.nodeIds();
        Set<ResearchTreeGraph.Edge> pathEdges = route.edges();

        boolean complete = target.orElseThrow().learned();
        long remainingPoints = 0L;
        long remainingIngredientTypes = 0L;
        int remainingSteps = route.unresolvedRequirementGroups();
        int learnedSteps = 0;
        int undisclosedSteps = route.unresolvedRequirementGroups();
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
                route.unresolvedRequirementGroups(),
                costComplete,
                complete));
    }

    /**
     * Chooses the route that may be presented to the player. A terminal server
     * failure is authoritative and must not fall back to a client estimate.
     */
    public static Optional<Plan> presentationPlan(
            ResearchTreeGraph graph,
            ResourceLocation targetId,
            int researchPoints,
            Optional<ResearchGuidanceSnapshot> authoritativeSnapshot,
            boolean authoritativeUnavailable) {
        if (authoritativeSnapshot == null) {
            throw new IllegalArgumentException("authoritative guidance cannot be null");
        }
        if (authoritativeUnavailable) {
            return Optional.empty();
        }
        Optional<ResearchGuidanceSnapshot> matching = authoritativeSnapshot
                .filter(snapshot -> snapshot.targetId().equals(targetId));
        return matching.isPresent()
                ? authoritativePlan(graph, matching.orElseThrow())
                : plan(graph, targetId, researchPoints);
    }

    /** Builds the presentation plan from a server-selected disclosure-safe route. */
    public static Optional<Plan> authoritativePlan(
            ResearchTreeGraph graph,
            ResearchGuidanceSnapshot snapshot) {
        if (graph == null || snapshot == null) {
            throw new IllegalArgumentException("invalid authoritative Research Tree plan input");
        }
        Set<ResourceLocation> purchases = Set.copyOf(snapshot.purchaseIds());
        if (!snapshot.routeAvailable()
                || snapshot.supportIds().stream().anyMatch(id -> graph.node(id)
                        .filter(node -> node.visibility().revealsIdentity())
                        .filter(node -> node.learned() != purchases.contains(id))
                        .isEmpty())
                || snapshot.selectedRequirements().stream().anyMatch(selected -> graph
                        .requirementGroupsOf(selected.dependentId()).stream()
                        .filter(group -> group.ordinal() == selected.groupOrdinal())
                        .noneMatch(group -> group.visibleAlternativeIds()
                                .contains(selected.prerequisiteId())))) {
            return Optional.empty();
        }
        LinkedHashSet<ResourceLocation> nodes = new LinkedHashSet<>(snapshot.supportIds());
        LinkedHashSet<ResearchTreeGraph.Edge> edges = new LinkedHashSet<>();
        for (var selected : snapshot.selectedRequirements()) {
            edges.add(new ResearchTreeGraph.Edge(
                    selected.prerequisiteId(), selected.dependentId()));
        }
        boolean complete = snapshot.state() == ResearchGuidanceSnapshot.State.LEARNED;
        int remainingSteps = complete ? 0 : snapshot.purchaseIds().size();
        int learnedSteps = nodes.size() - remainingSteps;
        return Optional.of(new Plan(
                snapshot.targetId(),
                nodes,
                edges,
                snapshot.nextStepId(),
                complete ? 0L : snapshot.pointCost(),
                complete ? 0L : snapshot.totalMaterialTypes(),
                remainingSteps,
                learnedSteps,
                0,
                0,
                0,
                true,
                complete));
    }

    private static Route selectRoute(
            ResearchTreeGraph graph,
            ResourceLocation blueprintId,
            Map<ResourceLocation, Route> memo,
            Set<ResourceLocation> visiting) {
        Route known = memo.get(blueprintId);
        if (known != null) {
            return known;
        }
        if (!visiting.add(blueprintId)) {
            throw new IllegalArgumentException("Research Tree planner graph contains a cycle");
        }
        try {
            ResearchTreeGraph.Node node = graph.node(blueprintId).orElseThrow();
            LinkedHashSet<ResourceLocation> nodeIds = new LinkedHashSet<>();
            LinkedHashSet<ResearchTreeGraph.Edge> edges = new LinkedHashSet<>();
            LinkedHashSet<RequirementKey> unresolvedRequirements = new LinkedHashSet<>();
            nodeIds.add(blueprintId);
            if (!node.learned()) {
                for (ResearchTreeGraph.RequirementGroup group
                        : graph.requirementGroupsOf(blueprintId)) {
                    Optional<ResourceLocation> learnedAlternative =
                            group.visibleAlternativeIds().stream()
                                    .filter(id -> graph.node(id).orElseThrow().learned())
                                    .min(Comparator.comparing(ResourceLocation::toString));
                    boolean satisfied = group.satisfactionDisclosed() && group.satisfied()
                            || learnedAlternative.isPresent();
                    if (satisfied && learnedAlternative.isEmpty()) {
                        continue;
                    }

                    Optional<Route> selected = (satisfied
                            ? learnedAlternative.stream()
                            : group.visibleAlternativeIds().stream())
                            .map(alternative -> selectRoute(
                                    graph, alternative, memo, visiting))
                            .min((left, right) -> compareRoutes(graph, left, right));
                    if (selected.isEmpty()) {
                        unresolvedRequirements.add(new RequirementKey(
                                blueprintId, group.ordinal()));
                        continue;
                    }
                    Route selectedRoute = selected.orElseThrow();
                    nodeIds.addAll(selectedRoute.nodeIds());
                    edges.addAll(selectedRoute.edges());
                    ResourceLocation alternative = selectedRoute.nodeIds().iterator().next();
                    edges.add(new ResearchTreeGraph.Edge(alternative, blueprintId));
                    unresolvedRequirements.addAll(selectedRoute.unresolvedRequirements());
                }
            }
            Route route = new Route(nodeIds, edges, unresolvedRequirements);
            memo.put(blueprintId, route);
            return route;
        } finally {
            visiting.remove(blueprintId);
        }
    }

    private static int compareRoutes(
            ResearchTreeGraph graph,
            Route left,
            Route right) {
        return ROUTE_SCORE_ORDER.compare(routeScore(graph, left), routeScore(graph, right));
    }

    private static RouteScore routeScore(ResearchTreeGraph graph, Route route) {
        int nonExactSteps = route.unresolvedRequirementGroups();
        int remainingSteps = route.unresolvedRequirementGroups();
        long remainingPoints = 0L;
        long remainingIngredientTypes = 0L;
        for (ResourceLocation id : route.nodeIds()) {
            ResearchTreeGraph.Node node = graph.node(id).orElseThrow();
            if (node.learned()) {
                continue;
            }
            remainingSteps = Math.addExact(remainingSteps, 1);
            if (!node.visibility().revealsExactPolicy()) {
                nonExactSteps = Math.addExact(nonExactSteps, 1);
            }
            if (node.visibility().revealsResearchSummary()) {
                remainingPoints = Math.addExact(remainingPoints, node.pointCost());
                remainingIngredientTypes = Math.addExact(
                        remainingIngredientTypes, node.ingredientTypeCount());
            }
        }
        String signature = route.nodeIds().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining("\u0000"));
        return new RouteScore(
                route.unresolvedRequirementGroups(),
                nonExactSteps,
                remainingPoints,
                remainingIngredientTypes,
                remainingSteps,
                route.nodeIds().size(),
                signature);
    }

    private static final Comparator<RouteScore> ROUTE_SCORE_ORDER = Comparator
            .comparingInt(RouteScore::unresolvedRequirementGroups)
            .thenComparingInt(RouteScore::nonExactSteps)
            .thenComparingLong(RouteScore::remainingPoints)
            .thenComparingLong(RouteScore::remainingIngredientTypes)
            .thenComparingInt(RouteScore::remainingSteps)
            .thenComparingInt(RouteScore::nodeCount)
            .thenComparing(RouteScore::signature);

    private record Route(
            Set<ResourceLocation> nodeIds,
            Set<ResearchTreeGraph.Edge> edges,
            Set<RequirementKey> unresolvedRequirements) {
        private Route {
            nodeIds = Collections.unmodifiableSet(new LinkedHashSet<>(nodeIds));
            edges = Collections.unmodifiableSet(new LinkedHashSet<>(edges));
            unresolvedRequirements = Collections.unmodifiableSet(
                    new LinkedHashSet<>(unresolvedRequirements));
        }

        private int unresolvedRequirementGroups() {
            return unresolvedRequirements.size();
        }
    }

    private record RequirementKey(ResourceLocation dependentId, int ordinal) {
    }

    private record RouteScore(
            int unresolvedRequirementGroups,
            int nonExactSteps,
            long remainingPoints,
            long remainingIngredientTypes,
            int remainingSteps,
            int nodeCount,
            String signature) {
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
            int unresolvedRequirementGroups,
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
                    || unresolvedRequirementGroups < 0
                    || unresolvedRequirementGroups > undisclosedSteps
                    || undisclosedSteps + summaryOnlySteps > remainingSteps
                    || (!complete && remainingSteps + learnedSteps
                            != pathNodeIds.size() + unresolvedRequirementGroups)
                    || complete && (remainingSteps != 0
                            || remainingPoints != 0L
                            || remainingIngredientTypes != 0L
                            || nextStepId.isPresent()
                            || learnedSteps != pathNodeIds.size())
                    || complete && unresolvedRequirementGroups != 0
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
