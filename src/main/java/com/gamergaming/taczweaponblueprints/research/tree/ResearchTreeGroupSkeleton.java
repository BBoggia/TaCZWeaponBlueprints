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

/**
 * Reusable group-local geometry in source-publication identity space.
 *
 * <p>A skeleton has no header, outer group padding, portal bank, or atlas offset. Branches and
 * All Weapons can therefore compose the same shape without changing progression topology.
 */
public final class ResearchTreeGroupSkeleton {
    private final ResourceLocation groupId;
    private final int sourceGroupOrder;
    private final int width;
    private final int height;
    private final int tierCount;
    private final List<PositionedNode> nodes;
    private final List<ResearchTreeGraph.Edge> internalEdges;
    private final List<ResearchTreeLayout.EdgeRouteHint> edgeRouteHints;
    private final Set<ResearchTreeGraph.Edge> internalEdgeSet;
    private final Map<ResourceLocation, PositionedNode> positionsById;
    private final ResearchTreeLayout localLayout;

    public ResearchTreeGroupSkeleton(
            ResourceLocation groupId,
            int sourceGroupOrder,
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<ResearchTreeGraph.Edge> internalEdges) {
        this(groupId, sourceGroupOrder, width, height, tierCount, nodes, internalEdges, List.of());
    }

    public ResearchTreeGroupSkeleton(
            ResourceLocation groupId,
            int sourceGroupOrder,
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<ResearchTreeGraph.Edge> internalEdges,
            List<ResearchTreeLayout.EdgeRouteHint> edgeRouteHints) {
        if (groupId == null || nodes == null || internalEdges == null || edgeRouteHints == null
                || nodes.stream().anyMatch(Objects::isNull)
                || internalEdges.stream().anyMatch(Objects::isNull)
                || edgeRouteHints.stream().anyMatch(Objects::isNull)
                || sourceGroupOrder < 0
                || sourceGroupOrder >= ResearchTreePresentation.MAX_GROUPS
                || nodes.isEmpty()
                || nodes.size() > ResearchTreeGraph.MAX_NODES
                || internalEdges.size() > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("invalid Research Tree group skeleton fields");
        }
        this.groupId = groupId;
        this.sourceGroupOrder = sourceGroupOrder;
        this.width = width;
        this.height = height;
        this.tierCount = tierCount;
        this.nodes = List.copyOf(nodes);
        this.internalEdges = List.copyOf(internalEdges);
        this.edgeRouteHints = List.copyOf(edgeRouteHints);

        Map<ResourceLocation, PositionedNode> index = new LinkedHashMap<>();
        Set<Integer> sourceOrdinals = new HashSet<>();
        List<ResearchTreeLayout.PositionedNode> layoutNodes = new ArrayList<>(nodes.size());
        for (int localOrdinal = 0; localOrdinal < this.nodes.size(); localOrdinal++) {
            PositionedNode node = this.nodes.get(localOrdinal);
            if (index.put(node.nodeId(), node) != null || !sourceOrdinals.add(node.sourceOrdinal())) {
                throw new IllegalArgumentException(
                        "Research Tree group skeleton contains a duplicate source node");
            }
            layoutNodes.add(new ResearchTreeLayout.PositionedNode(
                    localOrdinal,
                    node.nodeId(),
                    node.component(),
                    node.tier(),
                    node.orderInTier(),
                    node.x(),
                    node.y()));
        }
        positionsById = Map.copyOf(index);
        localLayout = new ResearchTreeLayout(
                width, height, tierCount, layoutNodes,
                List.of(), List.of(), List.of(), this.edgeRouteHints);
        validateEdges();
        internalEdgeSet = Set.copyOf(this.internalEdges);
    }

    public ResourceLocation groupId() {
        return groupId;
    }

    public int sourceGroupOrder() {
        return sourceGroupOrder;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int tierCount() {
        return tierCount;
    }

    public List<PositionedNode> nodes() {
        return nodes;
    }

    public List<ResearchTreeGraph.Edge> internalEdges() {
        return internalEdges;
    }

    public List<ResearchTreeLayout.EdgeRouteHint> edgeRouteHints() {
        return edgeRouteHints;
    }

    /** Constant-time membership check used while validating composed projections. */
    public boolean containsInternalEdge(ResearchTreeGraph.Edge edge) {
        return edge != null && internalEdgeSet.contains(edge);
    }

    public Optional<PositionedNode> position(ResourceLocation nodeId) {
        return nodeId == null ? Optional.empty() : Optional.ofNullable(positionsById.get(nodeId));
    }

    /** Local geometry with contiguous local ordinals for later projection composers. */
    public ResearchTreeLayout localLayout() {
        return localLayout;
    }

    private void validateEdges() {
        Set<ResearchTreeGraph.Edge> uniqueEdges = new HashSet<>();
        for (ResearchTreeGraph.Edge edge : internalEdges) {
            PositionedNode prerequisite = positionsById.get(edge.prerequisiteId());
            PositionedNode dependent = positionsById.get(edge.dependentId());
            if (!uniqueEdges.add(edge) || prerequisite == null || dependent == null
                    || prerequisite.nodeId().equals(dependent.nodeId())) {
                throw new IllegalArgumentException(
                        "Research Tree group skeleton contains an invalid internal edge");
            }
            if (prerequisite.y() <= dependent.y() || prerequisite.tier() >= dependent.tier()) {
                throw new IllegalArgumentException(
                        "Research Tree group skeleton is not bottom-to-top");
            }
        }
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof ResearchTreeGroupSkeleton other
                && sourceGroupOrder == other.sourceGroupOrder
                && width == other.width
                && height == other.height
                && tierCount == other.tierCount
                && groupId.equals(other.groupId)
                && nodes.equals(other.nodes)
                && internalEdges.equals(other.internalEdges)
                && edgeRouteHints.equals(other.edgeRouteHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                groupId, sourceGroupOrder, width, height, tierCount, nodes, internalEdges,
                edgeRouteHints);
    }

    @Override
    public String toString() {
        return "ResearchTreeGroupSkeleton[groupId=" + groupId
                + ", sourceGroupOrder=" + sourceGroupOrder
                + ", width=" + width
                + ", height=" + height
                + ", tierCount=" + tierCount
                + ", nodes=" + nodes
                + ", internalEdges=" + internalEdges
                + ", edgeRouteHints=" + edgeRouteHints + "]";
    }

    public record PositionedNode(
            int sourceOrdinal,
            ResourceLocation nodeId,
            int authoredRank,
            int component,
            int tier,
            int orderInTier,
            int x,
            int y) {
        public PositionedNode {
            if (sourceOrdinal < 0 || sourceOrdinal >= ResearchTreeGraph.MAX_NODES
                    || nodeId == null
                    || authoredRank < 0 || authoredRank >= ResearchTreeGraph.MAX_NODES
                    || component < 0 || component >= ResearchTreeGraph.MAX_NODES
                    || tier < 0 || tier >= ResearchTreeGraph.MAX_NODES
                    || orderInTier < 0 || orderInTier >= ResearchTreeGraph.MAX_NODES
                    || x < 0 || y < 0) {
                throw new IllegalArgumentException("invalid Research Tree skeleton node");
            }
        }

        public int centerX() {
            return x + ResearchTreeLayout.NODE_WIDTH / 2;
        }

        public int centerY() {
            return y + ResearchTreeLayout.NODE_HEIGHT / 2;
        }
    }
}
