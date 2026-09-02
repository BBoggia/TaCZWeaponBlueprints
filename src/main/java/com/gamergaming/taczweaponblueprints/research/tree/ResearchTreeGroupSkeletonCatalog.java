package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Immutable local skeletons plus the truthful edges that cross between them. */
public final class ResearchTreeGroupSkeletonCatalog {
    public static final ResearchTreeGroupSkeletonCatalog EMPTY =
            new ResearchTreeGroupSkeletonCatalog(List.of(), List.of());

    private final List<ResearchTreeGroupSkeleton> groups;
    private final List<CrossGroupEdge> crossGroupEdges;
    private final Map<ResourceLocation, ResearchTreeGroupSkeleton> groupsById;
    private final Map<ResourceLocation, List<CrossGroupEdge>> incidentEdgesByGroupId;
    private final Set<ResearchTreeGraph.Edge> crossGroupEdgeSet;

    public ResearchTreeGroupSkeletonCatalog(
            List<ResearchTreeGroupSkeleton> groups,
            List<CrossGroupEdge> crossGroupEdges) {
        if (groups == null || crossGroupEdges == null
                || groups.stream().anyMatch(Objects::isNull)
                || crossGroupEdges.stream().anyMatch(Objects::isNull)
                || groups.size() > ResearchTreePresentation.MAX_GROUPS
                || crossGroupEdges.size() > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("invalid Research Tree skeleton catalog fields");
        }
        this.groups = List.copyOf(groups);
        this.crossGroupEdges = List.copyOf(crossGroupEdges);

        Map<ResourceLocation, ResearchTreeGroupSkeleton> groupIndex = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> groupByNodeId = new LinkedHashMap<>();
        Map<ResourceLocation, List<CrossGroupEdge>> incidentEdgeIndex = new LinkedHashMap<>();
        Set<Integer> sourceOrdinals = new HashSet<>();
        for (int groupIndexValue = 0; groupIndexValue < this.groups.size(); groupIndexValue++) {
            ResearchTreeGroupSkeleton group = this.groups.get(groupIndexValue);
            if (group.sourceGroupOrder() != groupIndexValue
                    || groupIndex.put(group.groupId(), group) != null) {
                throw new IllegalArgumentException(
                        "Research Tree skeleton groups are not uniquely and contiguously ordered");
            }
            incidentEdgeIndex.put(group.groupId(), new ArrayList<>());
            for (ResearchTreeGroupSkeleton.PositionedNode node : group.nodes()) {
                if (groupByNodeId.put(node.nodeId(), group.groupId()) != null
                        || !sourceOrdinals.add(node.sourceOrdinal())) {
                    throw new IllegalArgumentException(
                            "Research Tree skeleton catalog contains a duplicate source node");
                }
            }
        }

        Set<CrossGroupEdge> uniqueEdges = new HashSet<>();
        Set<ResearchTreeGraph.Edge> graphEdges = new HashSet<>();
        for (CrossGroupEdge edge : this.crossGroupEdges) {
            if (!uniqueEdges.add(edge)
                    || !edge.prerequisiteGroupId().equals(
                            groupByNodeId.get(edge.prerequisiteId()))
                    || !edge.dependentGroupId().equals(
                            groupByNodeId.get(edge.dependentId()))) {
                throw new IllegalArgumentException(
                        "Research Tree skeleton catalog contains an invalid cross-group edge");
            }
            ResearchTreeGraph.Edge graphEdge = edge.edge();
            if (!graphEdges.add(graphEdge)) {
                throw new IllegalArgumentException(
                        "Research Tree skeleton catalog contains a duplicate graph edge");
            }
            incidentEdgeIndex.get(edge.prerequisiteGroupId()).add(edge);
            incidentEdgeIndex.get(edge.dependentGroupId()).add(edge);
        }
        groupsById = Map.copyOf(groupIndex);
        incidentEdgeIndex.replaceAll((ignored, edges) -> List.copyOf(edges));
        incidentEdgesByGroupId = Map.copyOf(incidentEdgeIndex);
        crossGroupEdgeSet = Set.copyOf(graphEdges);
    }

    public List<ResearchTreeGroupSkeleton> groups() {
        return groups;
    }

    public List<CrossGroupEdge> crossGroupEdges() {
        return crossGroupEdges;
    }

    public Optional<ResearchTreeGroupSkeleton> group(ResourceLocation groupId) {
        return groupId == null ? Optional.empty() : Optional.ofNullable(groupsById.get(groupId));
    }

    /** Cross-group edges touching this group, in authoritative graph order. */
    public List<CrossGroupEdge> incidentEdges(ResourceLocation groupId) {
        return groupId == null
                ? List.of()
                : incidentEdgesByGroupId.getOrDefault(groupId, List.of());
    }

    /** Constant-time membership check for an edge represented by this catalog. */
    public boolean containsCrossGroupEdge(ResearchTreeGraph.Edge edge) {
        return edge != null && crossGroupEdgeSet.contains(edge);
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof ResearchTreeGroupSkeletonCatalog other
                && groups.equals(other.groups)
                && crossGroupEdges.equals(other.crossGroupEdges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groups, crossGroupEdges);
    }

    @Override
    public String toString() {
        return "ResearchTreeGroupSkeletonCatalog[groups=" + groups
                + ", crossGroupEdges=" + crossGroupEdges + "]";
    }

    public record CrossGroupEdge(
            ResourceLocation prerequisiteId,
            ResourceLocation dependentId,
            ResourceLocation prerequisiteGroupId,
            ResourceLocation dependentGroupId) {
        public CrossGroupEdge {
            if (prerequisiteId == null || dependentId == null
                    || prerequisiteGroupId == null || dependentGroupId == null
                    || prerequisiteId.equals(dependentId)
                    || prerequisiteGroupId.equals(dependentGroupId)) {
                throw new IllegalArgumentException("invalid Research Tree cross-group edge");
            }
        }

        public ResearchTreeGraph.Edge edge() {
            return new ResearchTreeGraph.Edge(prerequisiteId, dependentId);
        }
    }
}
