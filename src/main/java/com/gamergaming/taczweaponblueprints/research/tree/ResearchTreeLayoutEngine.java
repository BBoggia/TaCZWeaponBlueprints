package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Deterministic, category-laned bottom-to-top layout for a research DAG. */
public final class ResearchTreeLayoutEngine {
    public static final int HORIZONTAL_GAP = 24;
    public static final int ROW_GAP = 16;
    public static final int VERTICAL_GAP = 32;
    public static final int PADDING = 16;
    public static final int MAX_COLUMNS = 12;
    public static final int LANE_PADDING = 8;
    public static final int LANE_GAP = 12;
    public static final int LANE_HEADER_HEIGHT = 16;
    public static final int TIER_GUTTER_WIDTH = 28;
    private static final int ORDERING_SWEEPS = 4;
    private static final String UNDISCLOSED_LANE = "undisclosed";

    private ResearchTreeLayoutEngine() {
    }

    public static ResearchTreeLayout layout(ResearchTreeGraph graph) {
        return layout(graph, null);
    }

    /**
     * Builds the global DAG from the disclosure-safe publication while
     * preserving its authored or deterministic fallback rank and sibling order.
     */
    public static ResearchTreeLayout layout(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("research publication cannot be null");
        }
        return layout(publication.graph(), publication.presentation());
    }

    private static ResearchTreeLayout layout(
            ResearchTreeGraph graph,
            ResearchTreePresentation presentation) {
        if (graph == null || graph.nodes().isEmpty()) {
            return ResearchTreeLayout.EMPTY;
        }

        Map<ResourceLocation, ResearchTreeGraph.Node> nodesById = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = adjacency(graph, false);
        Map<ResourceLocation, List<ResourceLocation>> dependents = adjacency(graph, true);
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            nodesById.put(node.blueprintId(), node);
        }

        int[] components = components(graph);
        Map<ResourceLocation, Integer> tiers = new HashMap<>();
        int maximumTier = 0;
        if (presentation == null) {
            for (ResearchTreeGraph.Node node : graph.nodes()) {
                int tier = tier(node, prerequisites, nodesById, tiers);
                tiers.put(node.blueprintId(), tier);
                maximumTier = Math.max(maximumTier, tier);
            }
        } else {
            for (ResearchTreeGraph.Node node : graph.nodes()) {
                int tier = presentation.membership(node.blueprintId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "research presentation omits a global layout node"))
                        .rank();
                tiers.put(node.blueprintId(), tier);
                maximumTier = Math.max(maximumTier, tier);
            }
        }

        List<List<ResearchTreeGraph.Node>> nodesByTier = new ArrayList<>(maximumTier + 1);
        for (int tier = 0; tier <= maximumTier; tier++) {
            nodesByTier.add(new ArrayList<>());
        }
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            nodesByTier.get(tiers.get(node.blueprintId())).add(node);
        }

        List<String> laneKeys = graph.nodes().stream()
                .map(ResearchTreeGraph.Node::itemType)
                .distinct()
                .sorted(Comparator
                        .comparing((String key) -> key.equals(UNDISCLOSED_LANE))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        Map<String, Integer> laneOrder = new HashMap<>();
        for (int index = 0; index < laneKeys.size(); index++) {
            laneOrder.put(laneKeys.get(index), index);
        }

        Comparator<ResearchTreeGraph.Node> stableOrder;
        if (presentation == null) {
            stableOrder = Comparator
                    .comparingInt((ResearchTreeGraph.Node node) -> laneOrder.get(node.itemType()))
                    .thenComparingInt(node -> components[node.ordinal()])
                    .thenComparing(node -> node.blueprintId().toString());
        } else {
            Map<ResourceLocation, Integer> groupOrders = new HashMap<>();
            Map<ResourceLocation, Integer> siblingOrders = new HashMap<>();
            for (ResearchTreePresentation.Group group : presentation.groups()) {
                for (ResearchTreePresentation.Member member : group.members()) {
                    groupOrders.put(member.nodeId(), group.order());
                    siblingOrders.put(member.nodeId(), member.orderInRank());
                }
            }
            stableOrder = Comparator
                    .comparingInt((ResearchTreeGraph.Node node) -> laneOrder.get(node.itemType()))
                    .thenComparingInt(node -> groupOrders.get(node.blueprintId()))
                    .thenComparingInt(node -> siblingOrders.get(node.blueprintId()))
                    .thenComparing(node -> node.blueprintId().toString());
        }
        nodesByTier.forEach(tierNodes -> tierNodes.sort(stableOrder));
        if (presentation == null) {
            reduceCrossings(nodesByTier, prerequisites, dependents, laneOrder, components);
        }

        Map<String, Integer> maximumLanePopulation = new LinkedHashMap<>();
        laneKeys.forEach(key -> maximumLanePopulation.put(key, 1));
        for (List<ResearchTreeGraph.Node> tierNodes : nodesByTier) {
            Map<String, Integer> populations = new HashMap<>();
            tierNodes.forEach(node -> populations.merge(node.itemType(), 1, Integer::sum));
            populations.forEach((key, count) -> maximumLanePopulation.merge(key, count, Math::max));
        }
        Map<String, Integer> laneColumns = allocateLaneColumns(laneKeys, maximumLanePopulation);

        Map<String, LaneSpec> laneSpecs = new LinkedHashMap<>();
        List<ResearchTreeLayout.CategoryLane> categoryLanes = new ArrayList<>();
        int currentX = PADDING + TIER_GUTTER_WIDTH;
        for (String key : laneKeys) {
            int columns = laneColumns.get(key);
            int contentWidth = occupiedWidth(columns);
            int width = contentWidth + LANE_PADDING * 2;
            LaneSpec spec = new LaneSpec(key, columns, currentX, width, contentWidth);
            laneSpecs.put(key, spec);
            categoryLanes.add(new ResearchTreeLayout.CategoryLane(key, currentX, width));
            currentX += width + LANE_GAP;
        }
        int canvasWidth = currentX - LANE_GAP + PADDING;

        int currentY = PADDING + LANE_HEADER_HEIGHT;
        ResearchTreeLayout.PositionedNode[] positioned =
                new ResearchTreeLayout.PositionedNode[graph.nodes().size()];
        for (int tier = 0; tier < nodesByTier.size(); tier++) {
            List<ResearchTreeGraph.Node> tierNodes = nodesByTier.get(tier);
            Map<String, List<ResearchTreeGraph.Node>> tierByLane = new LinkedHashMap<>();
            laneKeys.forEach(key -> tierByLane.put(key, new ArrayList<>()));
            tierNodes.forEach(node -> tierByLane.get(node.itemType()).add(node));

            int tierRows = 0;
            int orderInTier = 0;
            for (String key : laneKeys) {
                LaneSpec lane = laneSpecs.get(key);
                List<ResearchTreeGraph.Node> laneNodes = tierByLane.get(key);
                tierRows = Math.max(tierRows, divideRoundUp(laneNodes.size(), lane.columns()));
                for (int laneIndex = 0; laneIndex < laneNodes.size(); laneIndex++) {
                    int row = laneIndex / lane.columns();
                    int column = laneIndex % lane.columns();
                    int rowSize = Math.min(lane.columns(), laneNodes.size() - row * lane.columns());
                    int rowWidth = occupiedWidth(rowSize);
                    int rowStartX = lane.x() + LANE_PADDING
                            + (lane.contentWidth() - rowWidth) / 2;
                    ResearchTreeGraph.Node node = laneNodes.get(laneIndex);
                    positioned[node.ordinal()] = new ResearchTreeLayout.PositionedNode(
                            node.ordinal(), node.blueprintId(), components[node.ordinal()], tier,
                            orderInTier++,
                            rowStartX + column * (ResearchTreeLayout.NODE_WIDTH + HORIZONTAL_GAP),
                            currentY + row * (ResearchTreeLayout.NODE_HEIGHT + ROW_GAP));
                }
            }
            if (tierRows > 0) {
                currentY += tierRows * ResearchTreeLayout.NODE_HEIGHT
                        + (tierRows - 1) * ROW_GAP + VERTICAL_GAP;
            }
        }

        int canvasHeight = currentY - VERTICAL_GAP + PADDING;
        int contentOriginY = PADDING + LANE_HEADER_HEIGHT;
        for (int ordinal = 0; ordinal < positioned.length; ordinal++) {
            ResearchTreeLayout.PositionedNode node = positioned[ordinal];
            positioned[ordinal] = new ResearchTreeLayout.PositionedNode(
                    node.nodeOrdinal(),
                    node.blueprintId(),
                    node.component(),
                    node.tier(),
                    node.orderInTier(),
                    node.x(),
                    canvasHeight - PADDING - ResearchTreeLayout.NODE_HEIGHT
                            - (node.y() - contentOriginY));
        }
        List<ResearchTreeLayout.PositionedNode> positionedNodes = List.of(positioned);
        return new ResearchTreeLayout(
                canvasWidth, canvasHeight, maximumTier + 1,
                positionedNodes, List.of(), categoryLanes);
    }

    private static Map<ResourceLocation, List<ResourceLocation>> adjacency(
            ResearchTreeGraph graph,
            boolean reverse) {
        Map<ResourceLocation, List<ResourceLocation>> result = new LinkedHashMap<>();
        graph.nodes().forEach(node -> result.put(node.blueprintId(), new ArrayList<>()));
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            ResourceLocation key = reverse ? edge.prerequisiteId() : edge.dependentId();
            ResourceLocation value = reverse ? edge.dependentId() : edge.prerequisiteId();
            result.get(key).add(value);
        }
        result.replaceAll((ignored, ids) -> ids.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList());
        return result;
    }

    private static void reduceCrossings(
            List<List<ResearchTreeGraph.Node>> nodesByTier,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, List<ResourceLocation>> dependents,
            Map<String, Integer> laneOrder,
            int[] components) {
        for (int sweep = 0; sweep < ORDERING_SWEEPS; sweep++) {
            Map<ResourceLocation, Integer> order = orderIndex(nodesByTier);
            for (int tier = 1; tier < nodesByTier.size(); tier++) {
                sortTier(nodesByTier.get(tier), prerequisites, order, laneOrder, components);
                updateOrder(nodesByTier.get(tier), order);
            }
            order = orderIndex(nodesByTier);
            for (int tier = nodesByTier.size() - 2; tier >= 0; tier--) {
                sortTier(nodesByTier.get(tier), dependents, order, laneOrder, components);
                updateOrder(nodesByTier.get(tier), order);
            }
        }
    }

    private static void sortTier(
            List<ResearchTreeGraph.Node> tierNodes,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Integer> order,
            Map<String, Integer> laneOrder,
            int[] components) {
        Map<ResourceLocation, Double> barycenters = new HashMap<>();
        for (ResearchTreeGraph.Node node : tierNodes) {
            barycenters.put(node.blueprintId(), barycenter(node.blueprintId(), neighbors, order));
        }
        tierNodes.sort(Comparator
                .comparingInt((ResearchTreeGraph.Node node) -> laneOrder.get(node.itemType()))
                .thenComparingDouble(node -> barycenters.get(node.blueprintId()))
                .thenComparingInt(node -> components[node.ordinal()])
                .thenComparing(node -> node.blueprintId().toString()));
    }

    private static double barycenter(
            ResourceLocation blueprintId,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Integer> order) {
        return neighbors.getOrDefault(blueprintId, List.of()).stream()
                .map(order::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static Map<ResourceLocation, Integer> orderIndex(
            List<List<ResearchTreeGraph.Node>> nodesByTier) {
        Map<ResourceLocation, Integer> order = new HashMap<>();
        nodesByTier.forEach(tier -> updateOrder(tier, order));
        return order;
    }

    private static void updateOrder(
            List<ResearchTreeGraph.Node> tier,
            Map<ResourceLocation, Integer> order) {
        for (int index = 0; index < tier.size(); index++) {
            order.put(tier.get(index).blueprintId(), index);
        }
    }

    private static int tier(
            ResearchTreeGraph.Node node,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, ResearchTreeGraph.Node> nodesById,
            Map<ResourceLocation, Integer> memo) {
        Integer existing = memo.get(node.blueprintId());
        if (existing != null) {
            return existing;
        }
        int tier = 0;
        for (ResourceLocation prerequisiteId : prerequisites.getOrDefault(node.blueprintId(), List.of())) {
            ResearchTreeGraph.Node prerequisite = nodesById.get(prerequisiteId);
            tier = Math.max(tier, tier(prerequisite, prerequisites, nodesById, memo) + 1);
        }
        memo.put(node.blueprintId(), tier);
        return tier;
    }

    private static int occupiedWidth(int nodeCount) {
        if (nodeCount <= 0) {
            return 0;
        }
        return Math.addExact(
                Math.multiplyExact(nodeCount, ResearchTreeLayout.NODE_WIDTH),
                Math.multiplyExact(nodeCount - 1, HORIZONTAL_GAP));
    }

    private static int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    /**
     * Shares a bounded column budget across lanes while guaranteeing that every
     * published category remains visible. Sparse multi-lane trees stay compact;
     * a single busy category can use the full budget instead of becoming needlessly tall.
     */
    private static Map<String, Integer> allocateLaneColumns(
            List<String> laneKeys,
            Map<String, Integer> maximumLanePopulation) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        laneKeys.forEach(key -> columns.put(key, 1));
        int remaining = Math.max(0, MAX_COLUMNS - laneKeys.size());
        while (remaining > 0) {
            String candidate = null;
            double candidatePressure = -1.0D;
            for (String key : laneKeys) {
                int current = columns.get(key);
                int population = maximumLanePopulation.get(key);
                if (current >= population) {
                    continue;
                }
                double pressure = population / (double) current;
                if (pressure > candidatePressure) {
                    candidate = key;
                    candidatePressure = pressure;
                }
            }
            if (candidate == null) {
                break;
            }
            columns.put(candidate, columns.get(candidate) + 1);
            remaining--;
        }
        return columns;
    }

    private static int[] components(ResearchTreeGraph graph) {
        int[] parent = new int[graph.nodes().size()];
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }
        Map<ResourceLocation, Integer> ordinals = new HashMap<>();
        graph.nodes().forEach(node -> ordinals.put(node.blueprintId(), node.ordinal()));
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            union(parent, ordinals.get(edge.prerequisiteId()), ordinals.get(edge.dependentId()));
        }

        Map<Integer, String> minimumId = new HashMap<>();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            int root = find(parent, node.ordinal());
            minimumId.merge(root, node.blueprintId().toString(), (left, right) ->
                    left.compareTo(right) <= 0 ? left : right);
        }
        List<Integer> roots = minimumId.keySet().stream()
                .sorted(Comparator.comparing(minimumId::get))
                .toList();
        Map<Integer, Integer> componentByRoot = new HashMap<>();
        for (int index = 0; index < roots.size(); index++) {
            componentByRoot.put(roots.get(index), index);
        }

        int[] components = new int[parent.length];
        for (int index = 0; index < parent.length; index++) {
            components[index] = componentByRoot.get(find(parent, index));
        }
        return components;
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent[Math.max(leftRoot, rightRoot)] = Math.min(leftRoot, rightRoot);
        }
    }

    private record LaneSpec(String key, int columns, int x, int width, int contentWidth) {
    }
}
