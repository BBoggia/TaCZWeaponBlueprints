package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/** Indexed public topology and bounded relationship paths for one focused node. */
public final class ResearchTreeRelations {
    public static final ResearchTreeRelations EMPTY = new ResearchTreeRelations(
            ResearchTreeGraph.EMPTY, Map.of(), Map.of(), Map.of());

    private final ResearchTreeGraph graph;
    private final Map<ResourceLocation, List<ResourceLocation>> requirements;
    private final Map<ResourceLocation, List<ResourceLocation>> unlocks;
    private final Map<ResourceLocation, List<ResearchTreeGraph.Edge>>
            alternativeEdgesByDependent;

    private ResearchTreeRelations(
            ResearchTreeGraph graph,
            Map<ResourceLocation, List<ResourceLocation>> requirements,
            Map<ResourceLocation, List<ResourceLocation>> unlocks,
            Map<ResourceLocation, List<ResearchTreeGraph.Edge>>
                    alternativeEdgesByDependent) {
        this.graph = graph;
        this.requirements = requirements;
        this.unlocks = unlocks;
        this.alternativeEdgesByDependent = alternativeEdgesByDependent;
    }

    public static ResearchTreeRelations create(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree graph cannot be null");
        }
        if (graph.nodes().isEmpty()) {
            return EMPTY;
        }
        Map<ResourceLocation, List<ResourceLocation>> requirements = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> unlocks = new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            requirements.put(node.blueprintId(), new ArrayList<>());
            unlocks.put(node.blueprintId(), new ArrayList<>());
        }
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            requirements.get(edge.dependentId()).add(edge.prerequisiteId());
            unlocks.get(edge.prerequisiteId()).add(edge.dependentId());
        }
        Map<ResourceLocation, List<ResearchTreeGraph.Edge>> alternativeEdges =
                new LinkedHashMap<>();
        for (ResearchTreeGraph.RequirementGroup group : graph.requirementGroups()) {
            int alternativeCount = group.visibleAlternativeIds().size()
                    + group.hiddenAlternativeCount()
                    + group.externalAlternativeCount();
            if (alternativeCount > 1) {
                group.visibleAlternativeIds().forEach(alternative ->
                        alternativeEdges.computeIfAbsent(
                                group.dependentId(), ignored -> new ArrayList<>())
                                .add(new ResearchTreeGraph.Edge(
                                        alternative, group.dependentId())));
            }
        }
        alternativeEdges.replaceAll((ignored, edges) -> edges.stream()
                .distinct()
                .toList());
        return new ResearchTreeRelations(
                graph,
                immutableLists(requirements),
                immutableLists(unlocks),
                Collections.unmodifiableMap(alternativeEdges));
    }

    public List<ResourceLocation> directRequirements(ResourceLocation blueprintId) {
        return blueprintId == null
                ? List.of()
                : requirements.getOrDefault(blueprintId, List.of());
    }

    public List<ResourceLocation> directUnlocks(ResourceLocation blueprintId) {
        return blueprintId == null ? List.of() : unlocks.getOrDefault(blueprintId, List.of());
    }

    public FocusPath focus(ResourceLocation blueprintId) {
        return focus(blueprintId, true);
    }

    public FocusPath directFocus(ResourceLocation blueprintId) {
        return focus(blueprintId, false);
    }

    private FocusPath focus(ResourceLocation blueprintId, boolean includeTransitive) {
        if (graph.node(blueprintId).isEmpty()) {
            return FocusPath.EMPTY;
        }
        Set<ResourceLocation> directRequirements = orderedSet(requirements.get(blueprintId));
        Set<ResourceLocation> directUnlocks = orderedSet(unlocks.get(blueprintId));
        Set<ResourceLocation> requirementPath = includeTransitive
                ? traverse(directRequirements, requirements)
                : new LinkedHashSet<>(directRequirements);
        Set<ResourceLocation> unlockPath = includeTransitive
                ? traverse(directUnlocks, unlocks)
                : new LinkedHashSet<>(directUnlocks);
        requirementPath.removeAll(directRequirements);
        unlockPath.removeAll(directUnlocks);
        LinkedHashSet<ResourceLocation> requirementSide = new LinkedHashSet<>();
        requirementSide.add(blueprintId);
        requirementSide.addAll(directRequirements);
        requirementSide.addAll(requirementPath);
        Set<ResearchTreeGraph.Edge> focusedAlternativeEdges = requirementSide.stream()
                .flatMap(dependent -> alternativeEdgesByDependent
                        .getOrDefault(dependent, List.of()).stream())
                .filter(edge -> requirementSide.contains(edge.prerequisiteId()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new FocusPath(
                blueprintId,
                directRequirements,
                requirementPath,
                directUnlocks,
                unlockPath,
                focusedAlternativeEdges);
    }

    private static Set<ResourceLocation> traverse(
            Set<ResourceLocation> initial,
            Map<ResourceLocation, List<ResourceLocation>> adjacency) {
        LinkedHashSet<ResourceLocation> visited = new LinkedHashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>(initial);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (ResourceLocation next : adjacency.getOrDefault(current, List.of())) {
                if (!visited.contains(next)) {
                    pending.addLast(next);
                }
            }
        }
        return visited;
    }

    private static Set<ResourceLocation> orderedSet(List<ResourceLocation> values) {
        return values == null || values.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(values);
    }

    private static Map<ResourceLocation, List<ResourceLocation>> immutableLists(
            Map<ResourceLocation, List<ResourceLocation>> values) {
        Map<ResourceLocation, List<ResourceLocation>> immutable = new LinkedHashMap<>();
        values.forEach((id, adjacent) -> immutable.put(id, List.copyOf(adjacent)));
        return Collections.unmodifiableMap(immutable);
    }

    public record FocusPath(
            ResourceLocation focusedId,
            Set<ResourceLocation> directRequirements,
            Set<ResourceLocation> requirementPath,
            Set<ResourceLocation> directUnlocks,
            Set<ResourceLocation> unlockPath,
            Set<ResearchTreeGraph.Edge> alternativeRequirementEdges) {
        public static final FocusPath EMPTY = new FocusPath(
                null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

        public FocusPath {
            directRequirements = immutableSet(directRequirements);
            requirementPath = immutableSet(requirementPath);
            directUnlocks = immutableSet(directUnlocks);
            unlockPath = immutableSet(unlockPath);
            alternativeRequirementEdges = immutableEdgeSet(alternativeRequirementEdges);
            if (focusedId == null && (!directRequirements.isEmpty()
                    || !requirementPath.isEmpty()
                    || !directUnlocks.isEmpty()
                    || !unlockPath.isEmpty()
                    || !alternativeRequirementEdges.isEmpty())) {
                throw new IllegalArgumentException("empty Research Tree focus contains relationships");
            }
        }

        public ResearchTreePresentationContract.RelationshipRole role(ResourceLocation blueprintId) {
            if (focusedId == null) {
                return ResearchTreePresentationContract.RelationshipRole.NEUTRAL;
            }
            if (focusedId.equals(blueprintId)) {
                return ResearchTreePresentationContract.RelationshipRole.SELECTED;
            }
            if (directRequirements.contains(blueprintId)) {
                return ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT;
            }
            if (requirementPath.contains(blueprintId)) {
                return ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH;
            }
            if (directUnlocks.contains(blueprintId)) {
                return ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK;
            }
            if (unlockPath.contains(blueprintId)) {
                return ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH;
            }
            return ResearchTreePresentationContract.RelationshipRole.UNRELATED;
        }

        public ResearchTreePresentationContract.RelationshipRole role(ResearchTreeGraph.Edge edge) {
            if (edge == null || focusedId == null) {
                return ResearchTreePresentationContract.RelationshipRole.NEUTRAL;
            }
            if (edge.dependentId().equals(focusedId)
                    && directRequirements.contains(edge.prerequisiteId())) {
                return alternativeRequirementEdges.contains(edge)
                        ? ResearchTreePresentationContract.RelationshipRole.ALTERNATIVE_REQUIREMENT
                        : ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT;
            }
            if (edge.prerequisiteId().equals(focusedId)
                    && directUnlocks.contains(edge.dependentId())) {
                return ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK;
            }
            if ((requirementPath.contains(edge.prerequisiteId())
                    || directRequirements.contains(edge.prerequisiteId()))
                    && (requirementPath.contains(edge.dependentId())
                    || directRequirements.contains(edge.dependentId()))) {
                return alternativeRequirementEdges.contains(edge)
                        ? ResearchTreePresentationContract.RelationshipRole.ALTERNATIVE_REQUIREMENT
                        : ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH;
            }
            if ((unlockPath.contains(edge.prerequisiteId())
                    || directUnlocks.contains(edge.prerequisiteId()))
                    && (unlockPath.contains(edge.dependentId())
                    || directUnlocks.contains(edge.dependentId()))) {
                return ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH;
            }
            return ResearchTreePresentationContract.RelationshipRole.UNRELATED;
        }

        private static Set<ResourceLocation> immutableSet(Set<ResourceLocation> values) {
            if (values == null || values.isEmpty()) {
                return Set.of();
            }
            if (values.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Research Tree relationship set contains null IDs");
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }

        private static Set<ResearchTreeGraph.Edge> immutableEdgeSet(
                Set<ResearchTreeGraph.Edge> values) {
            if (values == null || values.isEmpty()) {
                return Set.of();
            }
            if (values.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Research Tree alternative-edge set contains null edges");
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }
}
