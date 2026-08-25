package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/** Spatial index that avoids scanning every authored prerequisite edge each frame. */
public final class ResearchTreeEdgeIndex {
    public static final ResearchTreeEdgeIndex EMPTY = new ResearchTreeEdgeIndex(null);

    private final IntervalNode root;

    private ResearchTreeEdgeIndex(IntervalNode root) {
        this.root = root;
    }

    public static ResearchTreeEdgeIndex create(ResearchTreeGraph graph, ResearchTreeLayout layout) {
        if (graph == null || layout == null || graph.edges().isEmpty()) {
            return EMPTY;
        }
        List<PositionedEdge> edges = new ArrayList<>(graph.edges().size());
        Map<ResourceLocation, List<ResearchTreeGraph.Edge>> outgoing = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResearchTreeGraph.Edge>> incoming = new LinkedHashMap<>();
        graph.nodes().forEach(node -> {
            outgoing.put(node.blueprintId(), new ArrayList<>());
            incoming.put(node.blueprintId(), new ArrayList<>());
        });
        graph.edges().forEach(edge -> {
            outgoing.get(edge.prerequisiteId()).add(edge);
            incoming.get(edge.dependentId()).add(edge);
        });
        Comparator<ResearchTreeGraph.Edge> outgoingOrder = Comparator
                .comparingInt((ResearchTreeGraph.Edge edge) -> layout.position(edge.dependentId())
                        .orElseThrow().centerX())
                .thenComparing(edge -> edge.dependentId().toString());
        Comparator<ResearchTreeGraph.Edge> incomingOrder = Comparator
                .comparingInt((ResearchTreeGraph.Edge edge) -> layout.position(edge.prerequisiteId())
                        .orElseThrow().centerX())
                .thenComparing(edge -> edge.prerequisiteId().toString());
        Map<ResearchTreeGraph.Edge, Port> outgoingPorts = indexPorts(outgoing, outgoingOrder);
        Map<ResearchTreeGraph.Edge, Port> incomingPorts = indexPorts(incoming, incomingOrder);
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            ResearchTreeLayout.PositionedNode prerequisite = layout.position(edge.prerequisiteId())
                    .orElseThrow(() -> new IllegalArgumentException("edge prerequisite is absent from layout"));
            ResearchTreeLayout.PositionedNode dependent = layout.position(edge.dependentId())
                    .orElseThrow(() -> new IllegalArgumentException("edge dependent is absent from layout"));
            edges.add(PositionedEdge.create(
                    edge,
                    prerequisite,
                    dependent,
                    outgoingPorts.get(edge),
                    incomingPorts.get(edge),
                    sourceRegion(layout, prerequisite)));
        }
        edges.sort(Comparator.comparingInt(PositionedEdge::minimumY)
                .thenComparingInt(PositionedEdge::maximumY)
                .thenComparing(value -> value.edge().prerequisiteId().toString())
                .thenComparing(value -> value.edge().dependentId().toString()));
        return new ResearchTreeEdgeIndex(IntervalNode.build(edges, 0, edges.size()));
    }

    private static Map<ResearchTreeGraph.Edge, Port> indexPorts(
            Map<ResourceLocation, List<ResearchTreeGraph.Edge>> grouped,
            Comparator<ResearchTreeGraph.Edge> order) {
        Map<ResearchTreeGraph.Edge, Port> ports = new HashMap<>();
        for (List<ResearchTreeGraph.Edge> group : grouped.values()) {
            group.sort(order);
            for (int index = 0; index < group.size(); index++) {
                ports.put(group.get(index), new Port(index, group.size()));
            }
        }
        return ports;
    }

    private static RoutingRegion sourceRegion(
            ResearchTreeLayout layout,
            ResearchTreeLayout.PositionedNode source) {
        RoutingRegion groupRegion = layout.groupRegions().stream()
                .filter(region -> source.x() >= region.x()
                        && source.x() + ResearchTreeLayout.NODE_WIDTH <= region.right())
                .map(region -> new RoutingRegion(region.x(), region.width()))
                .findFirst()
                .orElse(null);
        if (groupRegion != null) {
            return groupRegion;
        }
        return layout.categoryLanes().stream()
                .filter(lane -> source.x() >= lane.x()
                        && source.x() + ResearchTreeLayout.NODE_WIDTH <= lane.right())
                .map(lane -> new RoutingRegion(lane.x(), lane.width()))
                .findFirst()
                .orElse(null);
    }

    public List<PositionedEdge> visible(double minimumX, double minimumY, double maximumX, double maximumY) {
        if (root == null || maximumX < minimumX || maximumY < minimumY) {
            return List.of();
        }
        List<PositionedEdge> matches = new ArrayList<>();
        root.collect(minimumX, minimumY, maximumX, maximumY, matches);
        return List.copyOf(matches);
    }

    /**
     * Balanced interval tree ordered by an edge's minimum Y. The augmented
     * maximum lets a viewport query prune whole subtrees without copying long
     * connectors into every spatial bucket.
     */
    private static final class IntervalNode {
        private final PositionedEdge edge;
        private final IntervalNode left;
        private final IntervalNode right;
        private final int subtreeMinimumY;
        private final int subtreeMaximumY;

        private IntervalNode(PositionedEdge edge, IntervalNode left, IntervalNode right) {
            this.edge = edge;
            this.left = left;
            this.right = right;
            subtreeMinimumY = Math.min(
                    edge.minimumY(),
                    Math.min(left == null ? Integer.MAX_VALUE : left.subtreeMinimumY,
                            right == null ? Integer.MAX_VALUE : right.subtreeMinimumY));
            subtreeMaximumY = Math.max(
                    edge.maximumY(),
                    Math.max(left == null ? Integer.MIN_VALUE : left.subtreeMaximumY,
                            right == null ? Integer.MIN_VALUE : right.subtreeMaximumY));
        }

        private static IntervalNode build(List<PositionedEdge> edges, int start, int end) {
            if (start >= end) {
                return null;
            }
            int middle = start + (end - start) / 2;
            return new IntervalNode(
                    edges.get(middle),
                    build(edges, start, middle),
                    build(edges, middle + 1, end));
        }

        private void collect(
                double minimumX,
                double minimumY,
                double maximumX,
                double maximumY,
                List<PositionedEdge> matches) {
            if (subtreeMaximumY < minimumY || subtreeMinimumY > maximumY) {
                return;
            }
            if (left != null) {
                left.collect(minimumX, minimumY, maximumX, maximumY, matches);
            }
            if (edge.minimumY() <= maximumY
                    && edge.maximumY() >= minimumY
                    && edge.minimumX() <= maximumX
                    && edge.maximumX() >= minimumX) {
                matches.add(edge);
            }
            if (right != null) {
                right.collect(minimumX, minimumY, maximumX, maximumY, matches);
            }
        }
    }

    public record PositionedEdge(
            ResearchTreeGraph.Edge edge,
            int startX,
            int startY,
            int sourceExitY,
            int trackX,
            int targetApproachY,
            int endX,
            int endY,
            int arrowBaseY,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
        public PositionedEdge {
            if (edge == null
                    || startY <= sourceExitY
                    || sourceExitY < targetApproachY
                    || targetApproachY < arrowBaseY
                    || arrowBaseY <= endY
                    || minimumX > maximumX || minimumY > maximumY) {
                throw new IllegalArgumentException("invalid positioned research tree edge");
            }
        }

        private static PositionedEdge create(
                ResearchTreeGraph.Edge edge,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent,
                Port outgoing,
                Port incoming,
                RoutingRegion sourceRegion) {
            if (outgoing == null || incoming == null) {
                throw new IllegalArgumentException("research tree edge is missing a node port");
            }
            int startX = portX(prerequisite, outgoing);
            int startY = prerequisite.y() - 1;
            int sourceExitY = startY - 6;
            int endX = portX(dependent, incoming);
            int endY = dependent.y() + ResearchTreeLayout.NODE_HEIGHT;
            int arrowBaseY = endY + 4;
            int targetApproachY = endY + 8;
            boolean routeRight = routeRight(prerequisite, dependent, sourceRegion);
            int trackX;
            if (sourceRegion == null) {
                trackX = routeRight
                        ? Math.min(prerequisite.x() + ResearchTreeLayout.NODE_WIDTH + 4,
                                Math.max(prerequisite.centerX(), dependent.centerX()))
                        : Math.max(prerequisite.x() - 4,
                                Math.min(prerequisite.centerX(), dependent.centerX()));
            } else {
                trackX = routeRight ? sourceRegion.right() - 3 : sourceRegion.x() + 3;
            }
            return new PositionedEdge(
                    edge,
                    startX,
                    startY,
                    sourceExitY,
                    trackX,
                    targetApproachY,
                    endX,
                    endY,
                    arrowBaseY,
                    Math.min(Math.min(startX, trackX), endX - 3) - 1,
                    Math.min(startY, endY) - 1,
                    Math.max(Math.max(startX, trackX), endX + 3) + 1,
                    Math.max(startY, endY) + 1);
        }

        private static int portX(ResearchTreeLayout.PositionedNode node, Port port) {
            int usableWidth = ResearchTreeLayout.NODE_WIDTH - 8;
            int offset = 4 + (int) Math.round(
                    (port.rank() + 1) * usableWidth / (double) (port.count() + 1));
            return node.x() + offset;
        }

        private static boolean routeRight(
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent,
                RoutingRegion region) {
            if (dependent.centerX() != prerequisite.centerX()) {
                return dependent.centerX() > prerequisite.centerX();
            }
            int center = region == null
                    ? prerequisite.centerX()
                    : region.x() + region.width() / 2;
            return prerequisite.centerX() >= center;
        }
    }

    private record RoutingRegion(int x, int width) {
        private int right() {
            return x + width;
        }
    }

    private record Port(int rank, int count) {
        private Port {
            if (rank < 0 || count < 1 || rank >= count) {
                throw new IllegalArgumentException("invalid Research Tree connector port");
            }
        }
    }
}
