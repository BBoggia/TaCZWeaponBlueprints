package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal, disclosure-neutral input for deterministic layered geometry.
 *
 * <p>The layout kernel needs public node identities, authored ordering, and prerequisite edges;
 * it does not need mutable player state or blueprint policy metadata. Keeping that distinction
 * explicit lets group-local skeletons retain source publication IDs without manufacturing a
 * reindexed {@link ResearchTreeGraph}.
 */
public final class ResearchTreeLayoutInput {
    public static final ResearchTreeLayoutInput EMPTY =
            new ResearchTreeLayoutInput(List.of(), List.of());

    private static final Comparator<Edge> EDGE_ORDER = Comparator
            .comparing((Edge edge) -> edge.dependentId().toString())
            .thenComparing(edge -> edge.prerequisiteId().toString());

    private final List<Node> nodes;
    private final List<Edge> edges;

    public ResearchTreeLayoutInput(List<Node> nodes, List<Edge> edges) {
        if ((nodes != null && nodes.stream().anyMatch(Objects::isNull))
                || (edges != null && edges.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException("research layout input cannot contain null entries");
        }
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        List<Edge> sortedEdges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        sortedEdges.sort(EDGE_ORDER);
        this.edges = List.copyOf(sortedEdges);
        validate();
    }

    public static ResearchTreeLayoutInput from(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("research publication cannot be null");
        }
        publication = publication.legacyView();
        if (publication.graph().nodes().isEmpty()) {
            return EMPTY;
        }
        List<Node> nodes = new ArrayList<>(publication.graph().nodes().size());
        for (ResearchTreeGraph.Node graphNode : publication.graph().nodes()) {
            ResearchTreePresentation.Membership membership = publication.presentation()
                    .membership(graphNode.blueprintId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "research presentation omits a layout node"));
            int groupOrder = publication.presentation().group(membership.groupId())
                    .orElseThrow()
                    .order();
            nodes.add(new Node(
                    graphNode.ordinal(),
                    graphNode.blueprintId(),
                    membership.rank(),
                    groupOrder,
                    membership.orderInRank(),
                    graphNode.ordinal()));
        }
        List<Edge> edges = publication.graph().edges().stream()
                .map(edge -> new Edge(edge.prerequisiteId(), edge.dependentId()))
                .toList();
        return new ResearchTreeLayoutInput(nodes, edges);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    private void validate() {
        if (nodes.size() > ResearchTreeGraph.MAX_NODES
                || edges.size() > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("research layout input exceeds its size limit");
        }
        Map<ResourceLocation, Node> nodesById = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.ordinal() != index) {
                throw new IllegalArgumentException(
                        "research layout input ordinals must be contiguous");
            }
            if (nodesById.put(node.nodeId(), node) != null) {
                throw new IllegalArgumentException(
                        "research layout input contains a duplicate node ID");
            }
        }

        Set<Edge> uniqueEdges = new HashSet<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = new LinkedHashMap<>();
        nodesById.keySet().forEach(nodeId -> prerequisites.put(nodeId, new ArrayList<>()));
        for (Edge edge : edges) {
            if (!uniqueEdges.add(edge)) {
                throw new IllegalArgumentException(
                        "research layout input contains a duplicate edge");
            }
            if (!nodesById.containsKey(edge.prerequisiteId())
                    || !nodesById.containsKey(edge.dependentId())) {
                throw new IllegalArgumentException(
                        "research layout input edge references an unknown node");
            }
            if (edge.prerequisiteId().equals(edge.dependentId())) {
                throw new IllegalArgumentException("research layout input contains a self edge");
            }
            if (nodesById.get(edge.prerequisiteId()).rank()
                    >= nodesById.get(edge.dependentId()).rank()) {
                throw new IllegalArgumentException(
                        "research layout input edge does not advance to a higher rank");
            }
            prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
        }

        Set<ResourceLocation> complete = new LinkedHashSet<>();
        for (ResourceLocation nodeId : nodesById.keySet()) {
            visit(nodeId, prerequisites, complete, new LinkedHashSet<>());
        }
    }

    private static void visit(
            ResourceLocation nodeId,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Set<ResourceLocation> complete,
            LinkedHashSet<ResourceLocation> visiting) {
        if (complete.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("research layout input contains a cycle");
        }
        if (visiting.size() > BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH) {
            throw new IllegalArgumentException("research layout input exceeds its depth limit");
        }
        try {
            for (ResourceLocation prerequisite : prerequisites.getOrDefault(nodeId, List.of())) {
                visit(prerequisite, prerequisites, complete, visiting);
            }
            complete.add(nodeId);
        } finally {
            visiting.remove(nodeId);
        }
    }

    public record Node(
            int ordinal,
            ResourceLocation nodeId,
            int rank,
            int groupOrder,
            int orderInRank,
            int componentHint) {
        public Node {
            if (ordinal < 0 || ordinal >= ResearchTreeGraph.MAX_NODES
                    || nodeId == null
                    || rank < 0 || rank >= ResearchTreeGraph.MAX_NODES
                    || groupOrder < 0 || groupOrder >= ResearchTreePresentation.MAX_GROUPS
                    || orderInRank < 0 || orderInRank >= ResearchTreeGraph.MAX_NODES
                    || componentHint < 0 || componentHint >= ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("invalid research layout input node");
            }
        }
    }

    public record Edge(ResourceLocation prerequisiteId, ResourceLocation dependentId) {
        public Edge {
            if (prerequisiteId == null || dependentId == null) {
                throw new IllegalArgumentException("research layout input edge IDs cannot be null");
            }
        }
    }
}
