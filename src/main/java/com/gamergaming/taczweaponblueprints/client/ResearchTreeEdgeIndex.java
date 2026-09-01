package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/** Spatial index that avoids scanning every authored prerequisite edge each frame. */
public final class ResearchTreeEdgeIndex {
    public static final ResearchTreeEdgeIndex EMPTY = new ResearchTreeEdgeIndex(
            null, List.of(), Map.of(), Set.of());

    /**
     * Explicit connector policy. Visual layout metadata must not silently decide
     * whether an internal prerequisite is routed through a distant frame gutter.
     */
    public enum RoutingProfile {
        /** Compatibility behavior for callers that have not selected a projection policy. */
        AUTO,
        /** Group-local tree with distinct fan-in/fan-out ports and nearby tracks. */
        LOCAL_BRANCH,
        /** Curated atlas with shared fork/merge ports and nearby tracks. */
        UNIFIED_OVERVIEW
    }

    private final IntervalNode root;
    private final JunctionIntervalNode junctionRoot;
    private final List<PositionedRequirementGroup> requirementJunctions;
    private final Map<ResearchTreeGraph.Edge, List<PositionedRequirementGroup>>
            requirementJunctionsByEdge;
    private final Set<ResearchTreeGraph.Edge> directArrowEdges;

    private ResearchTreeEdgeIndex(
            IntervalNode root,
            List<PositionedRequirementGroup> requirementJunctions,
            Map<ResearchTreeGraph.Edge, List<PositionedRequirementGroup>>
                    requirementJunctionsByEdge,
            Set<ResearchTreeGraph.Edge> directArrowEdges) {
        this.root = root;
        this.requirementJunctions = List.copyOf(requirementJunctions);
        this.junctionRoot = JunctionIntervalNode.build(
                this.requirementJunctions, 0, this.requirementJunctions.size());
        Map<ResearchTreeGraph.Edge, List<PositionedRequirementGroup>> junctionIndex =
                new LinkedHashMap<>();
        requirementJunctionsByEdge.forEach((edge, junctions) ->
                junctionIndex.put(edge, List.copyOf(junctions)));
        this.requirementJunctionsByEdge = Map.copyOf(junctionIndex);
        this.directArrowEdges = Set.copyOf(directArrowEdges);
    }

    public static ResearchTreeEdgeIndex create(ResearchTreeGraph graph, ResearchTreeLayout layout) {
        return create(graph, layout, RoutingProfile.AUTO);
    }

    public static ResearchTreeEdgeIndex create(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            RoutingProfile routingProfile) {
        if (routingProfile == null) {
            throw new IllegalArgumentException("Research Tree routing profile cannot be null");
        }
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
        boolean automaticSharedPorts = layout.categoryLanes().isEmpty()
                && layout.groupRegions().isEmpty();
        boolean sharedPorts = switch (routingProfile) {
            case AUTO -> automaticSharedPorts;
            case LOCAL_BRANCH -> false;
            case UNIFIED_OVERVIEW -> true;
        };
        boolean obstacleRouting = routingProfile != RoutingProfile.AUTO || automaticSharedPorts;
        UnifiedObstacleIndex obstacleIndex = obstacleRouting
                ? UnifiedObstacleIndex.create(layout)
                : UnifiedObstacleIndex.EMPTY;
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
                    sourceRegion(layout, prerequisite),
                    layout,
                    obstacleIndex,
                    sharedPorts,
                    obstacleRouting));
        }
        edges.sort(Comparator.comparingInt(PositionedEdge::minimumY)
                .thenComparingInt(PositionedEdge::maximumY)
                .thenComparing(value -> value.edge().prerequisiteId().toString())
                .thenComparing(value -> value.edge().dependentId().toString()));
        RequirementJunctionIndex junctionIndex = positionRequirementJunctions(
                graph, layout, edges);
        return new ResearchTreeEdgeIndex(
                IntervalNode.build(edges, 0, edges.size()),
                junctionIndex.junctions(),
                junctionIndex.byEdge(),
                junctionIndex.directArrowEdges());
    }

    /** Stable OR-junction geometry. Live satisfaction remains owned by the graph. */
    public List<PositionedRequirementGroup> requirementJunctions() {
        return requirementJunctions;
    }

    public List<PositionedRequirementGroup> requirementJunctions(
            ResearchTreeGraph.Edge edge) {
        return edge == null
                ? List.of()
                : requirementJunctionsByEdge.getOrDefault(edge, List.of());
    }

    public List<PositionedRequirementGroup> visibleRequirementJunctions(
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        if (junctionRoot == null || maximumX < minimumX || maximumY < minimumY) {
            return List.of();
        }
        List<PositionedRequirementGroup> matches = new ArrayList<>();
        junctionRoot.collect(
                minimumX, minimumY, maximumX, maximumY, matches);
        return List.copyOf(matches);
    }

    /** A direct arrow remains only for a mandatory singleton representation. */
    public boolean drawsDirectArrow(ResearchTreeGraph.Edge edge) {
        return edge != null && directArrowEdges.contains(edge);
    }

    private static RequirementJunctionIndex positionRequirementJunctions(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            List<PositionedEdge> positionedEdges) {
        Map<ResearchTreeGraph.Edge, PositionedEdge> routesByEdge = new LinkedHashMap<>();
        positionedEdges.forEach(route -> routesByEdge.put(route.edge(), route));
        Map<ResourceLocation, List<ResearchTreeGraph.RequirementGroup>> groupsByDependent =
                new LinkedHashMap<>();
        graph.requirementGroups().forEach(group -> groupsByDependent
                .computeIfAbsent(group.dependentId(), ignored -> new ArrayList<>())
                .add(group));

        Map<ResearchTreeGraph.Edge, Boolean> multiMembership = new LinkedHashMap<>();
        Map<ResearchTreeGraph.Edge, Boolean> singletonMembership = new LinkedHashMap<>();
        Map<ResearchTreeGraph.Edge, List<PositionedRequirementGroup>> byEdge =
                new LinkedHashMap<>();
        List<PositionedRequirementGroup> junctions = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<ResearchTreeGraph.RequirementGroup>> entry
                : groupsByDependent.entrySet()) {
            ResearchTreeLayout.PositionedNode dependent = layout.position(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "requirement-group dependent is absent from layout"));
            List<ResearchTreeGraph.RequirementGroup> groups = entry.getValue();
            List<ResearchTreeGraph.RequirementGroup> drawableAlternativeGroups = groups.stream()
                    .filter(group -> group.visibleAlternativeIds().size()
                            + group.hiddenAlternativeCount()
                            + group.externalAlternativeCount() > 1)
                    .filter(group -> !group.visibleAlternativeIds().isEmpty())
                    .toList();
            int baseApproachY = dependent.y() + ResearchTreeLayout.NODE_HEIGHT + 8;
            int minimumSourceExitY = drawableAlternativeGroups.stream()
                    .flatMap(group -> group.visibleAlternativeIds().stream()
                            .map(alternative -> routesByEdge.get(
                                    new ResearchTreeGraph.Edge(
                                            alternative, group.dependentId()))))
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(PositionedEdge::sourceExitY)
                    .min()
                    .orElse(baseApproachY);
            int junctionSpacing = drawableAlternativeGroups.size() <= 1
                    ? 0
                    : Math.min(
                            ResearchTechTreeLayoutPolicy.REQUIREMENT_JUNCTION_SPACING,
                            Math.max(0, minimumSourceExitY - baseApproachY - 5)
                                    / (drawableAlternativeGroups.size() - 1));
            int drawableGroupIndex = 0;
            for (ResearchTreeGraph.RequirementGroup group : groups) {
                int alternativeCount = group.visibleAlternativeIds().size()
                        + group.hiddenAlternativeCount()
                        + group.externalAlternativeCount();
                List<PositionedEdge> members = group.visibleAlternativeIds().stream()
                        .map(alternative -> routesByEdge.get(new ResearchTreeGraph.Edge(
                                alternative, group.dependentId())))
                        .filter(java.util.Objects::nonNull)
                        .toList();
                for (PositionedEdge member : members) {
                    (alternativeCount > 1 ? multiMembership : singletonMembership)
                            .put(member.edge(), true);
                }
                if (alternativeCount <= 1 || members.isEmpty()) {
                    continue;
                }
                int junctionX = groupPortX(dependent, group.ordinal(), groups.size());
                int approachY = members.get(0).targetApproachY();
                if (members.stream().anyMatch(member ->
                        member.targetApproachY() != approachY)) {
                    throw new IllegalArgumentException(
                            "requirement-group alternatives do not share an approach row");
                }
                int junctionY = approachY + drawableGroupIndex++ * junctionSpacing;
                PositionedRequirementGroup positioned = new PositionedRequirementGroup(
                        new RequirementGroupKey(group.dependentId(), group.ordinal()),
                        junctionX,
                        junctionY,
                        dependent.y() + ResearchTreeLayout.NODE_HEIGHT,
                        members.stream().map(member -> new JunctionBranch(
                                member.edge(), member.endX(), member.targetApproachY())).toList(),
                        Math.min(junctionX - 4,
                                members.stream().mapToInt(PositionedEdge::endX)
                                        .min().orElseThrow() - 1),
                        dependent.y() + ResearchTreeLayout.NODE_HEIGHT,
                        Math.max(junctionX + 4,
                                members.stream().mapToInt(PositionedEdge::endX)
                                        .max().orElseThrow() + 1),
                        Math.max(approachY, junctionY + 4));
                junctions.add(positioned);
                positioned.branches().forEach(branch -> byEdge
                        .computeIfAbsent(branch.edge(), ignored -> new ArrayList<>())
                        .add(positioned));
            }
        }
        junctions.sort(Comparator
                .comparingInt(PositionedRequirementGroup::minimumY)
                .thenComparingInt(PositionedRequirementGroup::minimumX)
                .thenComparing(junction -> junction.key().dependentId().toString())
                .thenComparingInt(junction -> junction.key().ordinal()));
        Set<ResearchTreeGraph.Edge> directArrows = positionedEdges.stream()
                .map(PositionedEdge::edge)
                .filter(edge -> !multiMembership.getOrDefault(edge, false)
                        || singletonMembership.getOrDefault(edge, false))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RequirementJunctionIndex(junctions, byEdge, directArrows);
    }

    private static int groupPortX(
            ResearchTreeLayout.PositionedNode node,
            int ordinal,
            int count) {
        int usableWidth = ResearchTreeLayout.NODE_WIDTH - 8;
        int offset = 4 + (int) Math.round(
                (ordinal + 1) * usableWidth / (double) (count + 1));
        return node.x() + offset;
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

    private static final class JunctionIntervalNode {
        private final PositionedRequirementGroup junction;
        private final JunctionIntervalNode left;
        private final JunctionIntervalNode right;
        private final int subtreeMinimumY;
        private final int subtreeMaximumY;

        private JunctionIntervalNode(
                PositionedRequirementGroup junction,
                JunctionIntervalNode left,
                JunctionIntervalNode right) {
            this.junction = junction;
            this.left = left;
            this.right = right;
            subtreeMinimumY = Math.min(
                    junction.minimumY(),
                    Math.min(left == null ? Integer.MAX_VALUE : left.subtreeMinimumY,
                            right == null ? Integer.MAX_VALUE : right.subtreeMinimumY));
            subtreeMaximumY = Math.max(
                    junction.maximumY(),
                    Math.max(left == null ? Integer.MIN_VALUE : left.subtreeMaximumY,
                            right == null ? Integer.MIN_VALUE : right.subtreeMaximumY));
        }

        private static JunctionIntervalNode build(
                List<PositionedRequirementGroup> junctions,
                int start,
                int end) {
            if (start >= end) {
                return null;
            }
            int middle = start + (end - start) / 2;
            return new JunctionIntervalNode(
                    junctions.get(middle),
                    build(junctions, start, middle),
                    build(junctions, middle + 1, end));
        }

        private void collect(
                double minimumX,
                double minimumY,
                double maximumX,
                double maximumY,
                List<PositionedRequirementGroup> matches) {
            if (subtreeMaximumY < minimumY || subtreeMinimumY > maximumY) {
                return;
            }
            if (left != null) {
                left.collect(minimumX, minimumY, maximumX, maximumY, matches);
            }
            if (junction.intersects(minimumX, minimumY, maximumX, maximumY)) {
                matches.add(junction);
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
            List<RoutePoint> points,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
        public PositionedEdge {
            points = points == null ? List.of() : List.copyOf(points);
            boolean hasDiagonalSegment = false;
            for (int index = 1; index < points.size(); index++) {
                RoutePoint previous = points.get(index - 1);
                RoutePoint current = points.get(index);
                if (previous != null && current != null
                        && previous.x() != current.x()
                        && previous.y() != current.y()) {
                    hasDiagonalSegment = true;
                    break;
                }
            }
            if (edge == null
                    || points.size() < 2
                    || points.stream().anyMatch(java.util.Objects::isNull)
                    || hasDiagonalSegment
                    || !points.get(0).equals(new RoutePoint(startX, startY))
                    || !points.get(points.size() - 1).equals(
                            new RoutePoint(endX, arrowBaseY))
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
                RoutingRegion sourceRegion,
                ResearchTreeLayout layout,
                UnifiedObstacleIndex obstacleIndex,
                boolean sharedPorts,
                boolean obstacleRouting) {
            if (outgoing == null || incoming == null) {
                throw new IllegalArgumentException("research tree edge is missing a node port");
            }
            int startX = sharedPorts
                    ? prerequisite.centerX()
                    : portX(prerequisite, outgoing);
            int startY = prerequisite.y() - 1;
            int sourceExitY = startY - 6;
            int endX = sharedPorts
                    ? dependent.centerX()
                    : portX(dependent, incoming);
            int endY = dependent.y() + ResearchTreeLayout.NODE_HEIGHT;
            int arrowBaseY = endY + 4;
            int targetApproachY = endY + 8;
            boolean aligned = startX == endX;
            boolean routeRight = routeRight(prerequisite, dependent, sourceRegion);
            ResearchTreeLayout.EdgeRouteHint routeHint = layout.edgeRouteHint(
                    edge.prerequisiteId(), edge.dependentId()).orElse(null);
            int trackX = routeHint != null
                    ? routeHint.waypoints().get(0).x()
                    : trackXWithoutHint(
                            layout,
                            prerequisite,
                            dependent,
                            startX,
                            endX,
                            sourceExitY,
                            targetApproachY,
                            routeRight,
                            aligned,
                            sourceRegion,
                            obstacleIndex,
                            obstacleRouting);
            List<RoutePoint> points = routePoints(
                    routeHint,
                    startX,
                    startY,
                    sourceExitY,
                    trackX,
                    targetApproachY,
                    endX,
                    arrowBaseY);
            if (routeHint != null && routeIntersectsUnrelatedNode(
                    points, layout, prerequisite, dependent)) {
                // Reusable group-local hints cannot anticipate the final position of every
                // neighboring group in the unified atlas. Drop only the unsafe hint and let
                // the ordinary obstacle router derive a truthful connector for this edge.
                routeHint = null;
                trackX = trackXWithoutHint(
                        layout,
                        prerequisite,
                        dependent,
                        startX,
                        endX,
                        sourceExitY,
                        targetApproachY,
                        routeRight,
                        aligned,
                        sourceRegion,
                        obstacleIndex,
                        obstacleRouting);
                points = routePoints(
                        null,
                        startX,
                        startY,
                        sourceExitY,
                        trackX,
                        targetApproachY,
                        endX,
                        arrowBaseY);
            }
            int minimumX = points.stream().mapToInt(RoutePoint::x).min().orElseThrow();
            int maximumX = points.stream().mapToInt(RoutePoint::x).max().orElseThrow();
            int minimumY = Math.min(
                    points.stream().mapToInt(RoutePoint::y).min().orElseThrow(), endY);
            int maximumY = Math.max(
                    points.stream().mapToInt(RoutePoint::y).max().orElseThrow(), endY);
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
                    points,
                    Math.min(minimumX, endX - 3) - 1,
                    minimumY - 1,
                    Math.max(maximumX, endX + 3) + 1,
                    maximumY + 1);
        }

        private static int trackXWithoutHint(
                ResearchTreeLayout layout,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent,
                int startX,
                int endX,
                int sourceExitY,
                int targetApproachY,
                boolean routeRight,
                boolean aligned,
                RoutingRegion sourceRegion,
                UnifiedObstacleIndex obstacleIndex,
                boolean obstacleRouting) {
            if (obstacleRouting) {
                return unifiedTrackX(
                        layout,
                        prerequisite,
                        dependent,
                        startX,
                        endX,
                        sourceExitY,
                        targetApproachY,
                        routeRight,
                        aligned,
                        obstacleIndex);
            }
            if (sourceRegion == null) {
                return routeRight
                        ? Math.min(prerequisite.x() + ResearchTreeLayout.NODE_WIDTH + 4,
                                Math.max(prerequisite.centerX(), dependent.centerX()))
                        : Math.max(prerequisite.x() - 4,
                                Math.min(prerequisite.centerX(), dependent.centerX()));
            }
            return routeRight ? sourceRegion.right() - 3 : sourceRegion.x() + 3;
        }

        private static boolean routeIntersectsUnrelatedNode(
                List<RoutePoint> points,
                ResearchTreeLayout layout,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent) {
            for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
                RoutePoint start = points.get(pointIndex - 1);
                RoutePoint end = points.get(pointIndex);
                for (ResearchTreeLayout.PositionedNode node : layout.nodes()) {
                    if (!node.blueprintId().equals(prerequisite.blueprintId())
                            && !node.blueprintId().equals(dependent.blueprintId())
                            && segmentIntersectsInterior(start, end, node)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean segmentIntersectsInterior(
                RoutePoint start,
                RoutePoint end,
                ResearchTreeLayout.PositionedNode node) {
            int minimumX = Math.min(start.x(), end.x());
            int maximumX = Math.max(start.x(), end.x());
            int minimumY = Math.min(start.y(), end.y());
            int maximumY = Math.max(start.y(), end.y());
            if (start.x() == end.x()) {
                return start.x() > node.x()
                        && start.x() < node.x() + ResearchTreeLayout.NODE_WIDTH
                        && maximumY > node.y()
                        && minimumY < node.y() + ResearchTreeLayout.NODE_HEIGHT;
            }
            return start.y() > node.y()
                    && start.y() < node.y() + ResearchTreeLayout.NODE_HEIGHT
                    && maximumX > node.x()
                    && minimumX < node.x() + ResearchTreeLayout.NODE_WIDTH;
        }

        private static List<RoutePoint> routePoints(
                ResearchTreeLayout.EdgeRouteHint routeHint,
                int startX,
                int startY,
                int sourceExitY,
                int trackX,
                int targetApproachY,
                int endX,
                int arrowBaseY) {
            List<RoutePoint> points = new ArrayList<>();
            addPoint(points, startX, startY);
            addPoint(points, startX, sourceExitY);
            if (routeHint == null) {
                addPoint(points, trackX, sourceExitY);
                addPoint(points, trackX, targetApproachY);
                addPoint(points, endX, targetApproachY);
            } else {
                int currentY = sourceExitY;
                for (ResearchTreeLayout.RouteWaypoint waypoint : routeHint.waypoints()) {
                    addPoint(points, waypoint.x(), currentY);
                    currentY = waypoint.y() - ResearchTreeLayout.NODE_HEIGHT / 2 - 6;
                    addPoint(points, waypoint.x(), currentY);
                }
                addPoint(points, endX, currentY);
                addPoint(points, endX, targetApproachY);
            }
            addPoint(points, endX, arrowBaseY);
            return List.copyOf(points);
        }

        private static void addPoint(List<RoutePoint> points, int x, int y) {
            RoutePoint point = new RoutePoint(x, y);
            if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
                points.add(point);
            }
        }

        private static int unifiedTrackX(
                ResearchTreeLayout layout,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent,
                int startX,
                int endX,
                int sourceExitY,
                int targetApproachY,
                boolean routeRight,
                boolean aligned,
                UnifiedObstacleIndex obstacleIndex) {
            Integer directTrack = obstacleIndex.bestFeasibleBetween(
                    startX,
                    endX,
                    sourceExitY,
                    targetApproachY,
                    prerequisite,
                    dependent);
            if (directTrack != null) {
                return directTrack;
            }
            LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
            if (aligned) {
                candidates.add(startX);
            }
            candidates.add(routeRight
                    ? prerequisite.x() + ResearchTreeLayout.NODE_WIDTH + 4
                    : prerequisite.x() - 4);
            candidates.add(routeRight
                    ? dependent.x() + ResearchTreeLayout.NODE_WIDTH + 4
                    : dependent.x() - 4);
            candidates.addAll(obstacleIndex.tracks());
            candidates.add(0);
            candidates.add(layout.width());
            return candidates.stream()
                    .filter(candidate -> candidate >= 0 && candidate <= layout.width())
                    .filter(candidate -> !obstacleIndex.intersects(
                            candidate,
                            sourceExitY,
                            targetApproachY,
                            prerequisite,
                            dependent))
                    .min(Comparator
                            .comparingInt((Integer candidate) ->
                                    Math.abs(startX - candidate) + Math.abs(endX - candidate))
                            .thenComparingInt(candidate -> Math.abs(startX - candidate))
                            .thenComparingInt(Integer::intValue))
                    .orElse(routeRight
                            ? prerequisite.x() + ResearchTreeLayout.NODE_WIDTH + 4
                            : prerequisite.x() - 4);
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

    public record RoutePoint(int x, int y) {
    }

    public record RequirementGroupKey(ResourceLocation dependentId, int ordinal) {
        public RequirementGroupKey {
            if (dependentId == null || ordinal < 0
                    || ordinal >= com.gamergaming.taczweaponblueprints.resource.research
                            .ResearchRequirements.MAX_GROUPS) {
                throw new IllegalArgumentException("invalid requirement-group key");
            }
        }
    }

    public record PositionedRequirementGroup(
            RequirementGroupKey key,
            int x,
            int y,
            int dependentBottomY,
            List<JunctionBranch> branches,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
        public PositionedRequirementGroup {
            branches = branches == null ? List.of() : List.copyOf(branches);
            if (key == null || x < 0 || y < 0 || dependentBottomY < 0
                    || y <= dependentBottomY
                    || branches.isEmpty()
                    || branches.stream().anyMatch(java.util.Objects::isNull)
                    || branches.stream().anyMatch(branch ->
                            !branch.edge().dependentId().equals(key.dependentId()))
                    || minimumX > maximumX || minimumY > maximumY
                    || x < minimumX || x > maximumX
                    || dependentBottomY < minimumY || y > maximumY) {
                throw new IllegalArgumentException(
                        "invalid positioned requirement-group junction");
            }
        }

        public boolean intersects(
                double visibleLeft,
                double visibleTop,
                double visibleRight,
                double visibleBottom) {
            return maximumX >= visibleLeft && minimumX <= visibleRight
                    && maximumY >= visibleTop && minimumY <= visibleBottom;
        }
    }

    public record JunctionBranch(
            ResearchTreeGraph.Edge edge,
            int approachX,
            int approachY) {
        public JunctionBranch {
            if (edge == null || approachX < 0 || approachY < 0) {
                throw new IllegalArgumentException("invalid requirement-junction branch");
            }
        }
    }

    private record RequirementJunctionIndex(
            List<PositionedRequirementGroup> junctions,
            Map<ResearchTreeGraph.Edge, List<PositionedRequirementGroup>> byEdge,
            Set<ResearchTreeGraph.Edge> directArrowEdges) {
    }

    /** Precomputes only the track coordinates the unified router can request. */
    private static final class UnifiedObstacleIndex {
        private static final UnifiedObstacleIndex EMPTY =
                new UnifiedObstacleIndex(
                        java.util.Collections.emptyNavigableSet(), Map.of());
        private final java.util.NavigableSet<Integer> tracks;
        private final Map<Integer, List<ResearchTreeLayout.PositionedNode>> nodesByTrack;

        private UnifiedObstacleIndex(
                java.util.NavigableSet<Integer> tracks,
                Map<Integer, List<ResearchTreeLayout.PositionedNode>> nodesByTrack) {
            this.tracks = tracks;
            this.nodesByTrack = nodesByTrack;
        }

        private static UnifiedObstacleIndex create(ResearchTreeLayout layout) {
            java.util.NavigableSet<Integer> tracks = new java.util.TreeSet<>();
            tracks.add(0);
            tracks.add(layout.width());
            for (ResearchTreeLayout.PositionedNode node : layout.nodes()) {
                tracks.add(node.centerX());
                if (node.x() >= 4) {
                    tracks.add(node.x() - 4);
                }
                if (node.x() + ResearchTreeLayout.NODE_WIDTH + 4 <= layout.width()) {
                    tracks.add(node.x() + ResearchTreeLayout.NODE_WIDTH + 4);
                }
            }
            Map<Integer, List<ResearchTreeLayout.PositionedNode>> mutable =
                    new HashMap<>();
            for (ResearchTreeLayout.PositionedNode node : layout.nodes()) {
                tracks.subSet(
                                node.x() + 1,
                                true,
                                node.x() + ResearchTreeLayout.NODE_WIDTH - 1,
                                true)
                        .forEach(track -> mutable.computeIfAbsent(
                                track, ignored -> new ArrayList<>()).add(node));
            }
            mutable.replaceAll((ignored, nodes) -> nodes.stream()
                    .sorted(Comparator
                            .comparingInt(ResearchTreeLayout.PositionedNode::y)
                            .thenComparing(node -> node.blueprintId().toString()))
                    .toList());
            return new UnifiedObstacleIndex(
                    java.util.Collections.unmodifiableNavigableSet(
                            new java.util.TreeSet<>(tracks)),
                    Map.copyOf(mutable));
        }

        private java.util.NavigableSet<Integer> tracks() {
            return tracks;
        }

        /**
         * Every track between the two connector ports has the same minimum
         * Manhattan length. Walking away from the source therefore finds the
         * exact comparator winner without scanning every track on the canvas.
         */
        private Integer bestFeasibleBetween(
                int startX,
                int endX,
                int firstY,
                int secondY,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent) {
            java.util.NavigableSet<Integer> between = startX <= endX
                    ? tracks.subSet(startX, true, endX, true)
                    : tracks.subSet(endX, true, startX, true).descendingSet();
            for (Integer track : between) {
                if (!intersects(
                        track, firstY, secondY, prerequisite, dependent)) {
                    return track;
                }
            }
            return null;
        }

        private boolean intersects(
                int trackX,
                int firstY,
                int secondY,
                ResearchTreeLayout.PositionedNode prerequisite,
                ResearchTreeLayout.PositionedNode dependent) {
            int minimumY = Math.min(firstY, secondY);
            int maximumY = Math.max(firstY, secondY);
            List<ResearchTreeLayout.PositionedNode> nodes =
                    nodesByTrack.getOrDefault(trackX, List.of());
            int low = 0;
            int high = nodes.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (nodes.get(middle).y() + ResearchTreeLayout.NODE_HEIGHT <= minimumY) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            for (int index = low; index < nodes.size(); index++) {
                ResearchTreeLayout.PositionedNode node = nodes.get(index);
                if (node.y() >= maximumY) {
                    break;
                }
                if (!node.blueprintId().equals(prerequisite.blueprintId())
                        && !node.blueprintId().equals(dependent.blueprintId())) {
                    return true;
                }
            }
            return false;
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
