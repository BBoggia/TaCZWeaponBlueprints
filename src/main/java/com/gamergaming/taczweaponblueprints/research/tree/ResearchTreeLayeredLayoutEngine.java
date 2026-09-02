package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Deterministic, policy-driven layered layout for a published research DAG. */
public final class ResearchTreeLayeredLayoutEngine {
    /** Prevent adversarial high-fan-out data from expanding into millions of objects. */
    private static final int MAX_VIRTUAL_VERTICES = ResearchTreeGraph.MAX_NODES * 8;

    private ResearchTreeLayeredLayoutEngine() {
    }

    public static ResearchTreeLayout layout(
            ResearchTreePublication publication,
            ResearchTreeLayoutPolicy policy) {
        if (publication == null) {
            throw new IllegalArgumentException("research publication cannot be null");
        }
        return layoutInput(ResearchTreeLayoutInput.from(publication), policy);
    }

    public static ResearchTreeLayout layoutInput(
            ResearchTreeLayoutInput input,
            ResearchTreeLayoutPolicy policy) {
        if (input == null) {
            throw new IllegalArgumentException("research layout input cannot be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("research layout policy cannot be null");
        }
        if (input.nodes().isEmpty()) {
            return ResearchTreeLayout.EMPTY;
        }

        PreparedGraph prepared = prepareGraph(input, policy);
        Map<Integer, Integer> yByRank = rankOffsets(prepared.occupiedRanks(), policy);
        List<PreparedComponent> components = prepareComponents(prepared, policy);
        PackedComponents packed = packComponents(
                prepared.input(), prepared.stableOrders(), prepared.occupiedRanks(),
                components, yByRank, policy);
        return materialize(prepared, components, packed, policy);
    }

    private static PreparedGraph prepareGraph(
            ResearchTreeLayoutInput input,
            ResearchTreeLayoutPolicy policy) {
        NormalizedGraph normalized = normalize(input, policy);
        Map<Integer, StableOrder> stableOrders = stableOrders(normalized.vertices());
        Comparator<LayerVertex> stableComparator =
                stableComparator(stableOrders);
        Map<Integer, List<Integer>> prerequisites = adjacency(normalized, false);
        Map<Integer, List<Integer>> dependents = adjacency(normalized, true);
        List<Integer> occupiedRanks = normalized.vertices().stream()
                .map(LayerVertex::rank)
                .distinct()
                .sorted()
                .toList();
        return new PreparedGraph(
                input,
                normalized,
                stableOrders,
                prerequisites,
                dependents,
                components(normalized, stableOrders, stableComparator),
                occupiedRanks);
    }

    private static List<PreparedComponent> prepareComponents(
            PreparedGraph prepared,
            ResearchTreeLayoutPolicy policy) {
        Comparator<LayerVertex> stableComparator =
                stableComparator(prepared.stableOrders());
        List<PreparedComponent> result = new ArrayList<>(prepared.components().size());
        for (ComponentVertices component : prepared.components()) {
            TreeMap<Integer, List<LayerVertex>> nodesByRank = new TreeMap<>();
            for (LayerVertex node : component.vertices()) {
                nodesByRank.computeIfAbsent(
                        node.rank(),
                        ignored -> new ArrayList<>()).add(node);
            }
            nodesByRank.values().forEach(nodes -> nodes.sort(stableComparator));
            orderRanks(
                    nodesByRank,
                    prepared.prerequisites(),
                    prepared.dependents(),
                    stableComparator,
                    policy.orderingSweeps());
            Map<Integer, Double> centers = compactRanks(
                    nodesByRank,
                    prepared.prerequisites(),
                    prepared.dependents(),
                    policy.nodeGap(),
                    policy.compactionSweeps());

            double minimumCenter = centers.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .min()
                    .orElseThrow();
            double maximumCenter = centers.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElseThrow();
            int width = Math.max(
                    ResearchTreeLayout.NODE_WIDTH,
                    (int) Math.ceil(maximumCenter - minimumCenter)
                            + ResearchTreeLayout.NODE_WIDTH);
            result.add(new PreparedComponent(
                    component.vertices(), Map.copyOf(centers), minimumCenter, width));
        }
        return List.copyOf(result);
    }

    private static PackedComponents packComponents(
            ResearchTreeLayoutInput input,
            Map<Integer, StableOrder> stableOrders,
            List<Integer> occupiedRanks,
            List<PreparedComponent> components,
            Map<Integer, Integer> yByRank,
            ResearchTreeLayoutPolicy policy) {
        int cellWidth = components.stream()
                .mapToInt(PreparedComponent::width)
                .max()
                .orElseThrow();
        int cellHeight = Math.addExact(
                Math.addExact(policy.canvasPadding() * 2, ResearchTreeLayout.NODE_HEIGHT),
                Math.multiplyExact(
                        occupiedRanks.size() - 1,
                        ResearchTreeLayout.NODE_HEIGHT + policy.tierGap()));
        int columns = packingColumns(components.size(), cellWidth, cellHeight);
        int rows = divideRoundUp(components.size(), columns);

        List<DraftPosition> drafts = new ArrayList<>(
                input.nodes().size() + Math.min(MAX_VIRTUAL_VERTICES, input.edges().size()));
        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            PreparedComponent component = components.get(componentIndex);
            int componentColumn = componentIndex % columns;
            int componentRow = componentIndex / columns;
            int originX = Math.addExact(
                    policy.canvasPadding(),
                    Math.addExact(
                            Math.multiplyExact(
                                    componentColumn,
                                    cellWidth + policy.intraGroupGap()),
                            (cellWidth - component.width()) / 2));
            int originY = Math.multiplyExact(
                    componentRow,
                    cellHeight + policy.intraGroupGap());
            for (LayerVertex node : component.vertices()) {
                int x = Math.addExact(
                        originX,
                        (int) Math.round(component.centers().get(node.id())
                                - component.minimumCenter()));
                drafts.add(new DraftPosition(
                        node,
                        componentIndex,
                        stableOrders.get(node.id()).rank(),
                        x,
                        Math.addExact(
                                yByRank.get(stableOrders.get(node.id()).rank()),
                                originY)));
            }
        }
        return new PackedComponents(List.copyOf(drafts), cellWidth, cellHeight, columns, rows);
    }

    private static ResearchTreeLayout materialize(
            PreparedGraph prepared,
            List<PreparedComponent> components,
            PackedComponents packed,
            ResearchTreeLayoutPolicy policy) {
        ResearchTreeLayout.PositionedNode[] positioned =
                new ResearchTreeLayout.PositionedNode[prepared.input().nodes().size()];
        List<DraftPosition> realDrafts = packed.drafts().stream()
                .filter(draft -> draft.vertex().isReal())
                .toList();
        Map<Integer, Integer> visualTierByY = packed.rows() > 1
                ? visualTierByY(realDrafts)
                : Map.of();
        Map<Integer, List<DraftPosition>> draftsByTier = new TreeMap<>();
        realDrafts.forEach(draft -> draftsByTier.computeIfAbsent(
                packed.rows() > 1
                        ? visualTierByY.get(draft.y())
                        : draft.rank(),
                ignored -> new ArrayList<>()).add(draft));
        for (Map.Entry<Integer, List<DraftPosition>> entry : draftsByTier.entrySet()) {
            List<DraftPosition> tierDrafts = entry.getValue();
            tierDrafts.sort(Comparator
                    .comparingInt(DraftPosition::y)
                    .thenComparingInt(DraftPosition::x)
                    .thenComparing(draft -> prepared.stableOrders()
                            .get(draft.vertex().id()))
                    .thenComparing(draft -> draft.vertex().stableKey()));
            for (int order = 0; order < tierDrafts.size(); order++) {
                DraftPosition draft = tierDrafts.get(order);
                ResearchTreeLayoutInput.Node source = draft.vertex().sourceNode();
                positioned[source.ordinal()] = new ResearchTreeLayout.PositionedNode(
                        source.ordinal(),
                        source.nodeId(),
                        draft.component(),
                        entry.getKey(),
                        order,
                        draft.x(),
                        draft.y());
            }
        }

        int columnsInWidestRow = Math.min(packed.columns(), components.size());
        int width = Math.addExact(
                policy.canvasPadding() * 2,
                Math.addExact(
                        Math.multiplyExact(columnsInWidestRow, packed.cellWidth()),
                        Math.multiplyExact(
                                columnsInWidestRow - 1,
                                policy.intraGroupGap())));
        int height = Math.addExact(
                Math.multiplyExact(packed.rows(), packed.cellHeight()),
                Math.multiplyExact(packed.rows() - 1, policy.intraGroupGap()));
        ensureDimension(width);
        ensureDimension(height);
        Map<Integer, DraftPosition> draftsByVertex = packed.drafts().stream().collect(
                java.util.stream.Collectors.toMap(
                        draft -> draft.vertex().id(), draft -> draft));
        List<ResearchTreeLayout.EdgeRouteHint> routeHints = new ArrayList<>();
        for (Map.Entry<ResearchTreeLayoutInput.Edge, List<Integer>> entry
                : prepared.normalized().waypointsByEdge().entrySet()) {
            List<ResearchTreeLayout.RouteWaypoint> waypoints = entry.getValue().stream()
                    .map(draftsByVertex::get)
                    .map(draft -> new ResearchTreeLayout.RouteWaypoint(
                            draft.rank(),
                            draft.x() + ResearchTreeLayout.NODE_WIDTH / 2,
                            draft.y() + ResearchTreeLayout.NODE_HEIGHT / 2))
                    .toList();
            routeHints.add(new ResearchTreeLayout.EdgeRouteHint(
                    entry.getKey().prerequisiteId(),
                    entry.getKey().dependentId(),
                    waypoints));
        }
        return new ResearchTreeLayout(
                width,
                height,
                packed.rows() > 1
                        ? visualTierByY.size()
                        : prepared.occupiedRanks().get(prepared.occupiedRanks().size() - 1) + 1,
                List.of(positioned),
                List.of(),
                List.of(),
                List.of(),
                routeHints);
    }

    private static Map<Integer, Integer> rankOffsets(
            List<Integer> occupiedRanks,
            ResearchTreeLayoutPolicy policy) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int rankIndex = 0; rankIndex < occupiedRanks.size(); rankIndex++) {
            int rank = occupiedRanks.get(rankIndex);
            int verticalIndex = occupiedRanks.size() - 1 - rankIndex;
            result.put(rank, Math.addExact(
                    policy.canvasPadding(),
                    Math.multiplyExact(
                            verticalIndex,
                            ResearchTreeLayout.NODE_HEIGHT + policy.tierGap())));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, StableOrder> stableOrders(List<LayerVertex> vertices) {
        Map<Integer, StableOrder> result = new LinkedHashMap<>();
        for (LayerVertex node : vertices) {
            StableOrder previous = result.put(
                    node.id(),
                    new StableOrder(
                            node.rank(),
                            node.groupOrder(),
                            node.siblingOrder(),
                            node.stableKey()));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "research layout input assigns a layered node more than once");
            }
        }
        return Map.copyOf(result);
    }

    private static Comparator<LayerVertex> stableComparator(
            Map<Integer, StableOrder> stableOrders) {
        return Comparator
                .comparing((LayerVertex node) -> stableOrders.get(node.id()))
                .thenComparing(LayerVertex::stableKey);
    }

    private static Map<Integer, List<Integer>> adjacency(
            NormalizedGraph normalized,
            boolean reverse) {
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        normalized.vertices().forEach(node -> result.put(node.id(), new ArrayList<>()));
        for (LayerEdge edge : normalized.edges()) {
            int key = reverse ? edge.prerequisiteId() : edge.dependentId();
            int value = reverse ? edge.dependentId() : edge.prerequisiteId();
            result.get(key).add(value);
        }
        result.replaceAll((ignored, ids) -> ids.stream()
                .sorted()
                .toList());
        return Map.copyOf(result);
    }

    private static void orderRanks(
            TreeMap<Integer, List<LayerVertex>> nodesByRank,
            Map<Integer, List<Integer>> prerequisites,
            Map<Integer, List<Integer>> dependents,
            Comparator<LayerVertex> stableComparator,
            int sweeps) {
        List<Integer> ranks = List.copyOf(nodesByRank.keySet());
        for (int sweep = 0; sweep < sweeps; sweep++) {
            Map<Integer, Integer> order = orderIndex(nodesByRank);
            for (int rankIndex = 1; rankIndex < ranks.size(); rankIndex++) {
                sortRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        prerequisites,
                        order,
                        stableComparator);
                updateOrder(nodesByRank.get(ranks.get(rankIndex)), order);
            }
            order = orderIndex(nodesByRank);
            for (int rankIndex = ranks.size() - 2; rankIndex >= 0; rankIndex--) {
                sortRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        dependents,
                        order,
                        stableComparator);
                updateOrder(nodesByRank.get(ranks.get(rankIndex)), order);
            }
        }
    }

    private static void sortRank(
            List<LayerVertex> nodes,
            Map<Integer, List<Integer>> neighbors,
            Map<Integer, Integer> order,
            Comparator<LayerVertex> stableComparator) {
        Map<Integer, Double> scores = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            int nodeId = nodes.get(index).id();
            scores.put(nodeId, barycenter(nodeId, neighbors, order).orElse((double) index));
        }
        nodes.sort(Comparator
                .comparingInt(LayerVertex::corridorSide)
                .thenComparingDouble(node -> scores.get(node.id()))
                .thenComparing(stableComparator));
    }

    private static Map<Integer, Integer> orderIndex(
            TreeMap<Integer, List<LayerVertex>> nodesByRank) {
        Map<Integer, Integer> result = new HashMap<>();
        nodesByRank.values().forEach(nodes -> updateOrder(nodes, result));
        return result;
    }

    private static void updateOrder(
            List<LayerVertex> nodes,
            Map<Integer, Integer> order) {
        for (int index = 0; index < nodes.size(); index++) {
            order.put(nodes.get(index).id(), index);
        }
    }

    private static Map<Integer, Double> compactRanks(
            TreeMap<Integer, List<LayerVertex>> nodesByRank,
            Map<Integer, List<Integer>> prerequisites,
            Map<Integer, List<Integer>> dependents,
            int nodeGap,
            int sweeps) {
        double nodePitch = ResearchTreeLayout.NODE_WIDTH + nodeGap;
        Map<Integer, Double> centers = new HashMap<>();
        for (List<LayerVertex> nodes : nodesByRank.values()) {
            double firstCenter = -((nodes.size() - 1) * nodePitch) / 2.0D;
            for (int index = 0; index < nodes.size(); index++) {
                centers.put(nodes.get(index).id(), firstCenter + index * nodePitch);
            }
        }
        List<Integer> ranks = List.copyOf(nodesByRank.keySet());
        for (int sweep = 0; sweep < sweeps; sweep++) {
            for (int rankIndex = 1; rankIndex < ranks.size(); rankIndex++) {
                compactRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        prerequisites,
                        centers,
                        nodePitch);
            }
            for (int rankIndex = ranks.size() - 2; rankIndex >= 0; rankIndex--) {
                compactRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        dependents,
                        centers,
                        nodePitch);
            }
        }
        return centers;
    }

    private static void compactRank(
            List<LayerVertex> nodes,
            Map<Integer, List<Integer>> neighbors,
            Map<Integer, Double> centers,
            double nodePitch) {
        double[] desired = new double[nodes.size()];
        double[] placed = new double[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            int nodeId = nodes.get(index).id();
            desired[index] = centerBarycenter(nodeId, neighbors, centers)
                    .orElse(centers.get(nodeId));
            placed[index] = index == 0
                    ? desired[index]
                    : Math.max(desired[index], placed[index - 1] + nodePitch);
        }
        double desiredAverage = java.util.Arrays.stream(desired).average().orElse(0.0D);
        double placedAverage = java.util.Arrays.stream(placed).average().orElse(0.0D);
        double shift = desiredAverage - placedAverage;
        for (int index = 0; index < nodes.size(); index++) {
            centers.put(nodes.get(index).id(), placed[index] + shift);
        }
    }

    private static OptionalDouble barycenter(
            int nodeId,
            Map<Integer, List<Integer>> neighbors,
            Map<Integer, Integer> values) {
        return neighbors.getOrDefault(nodeId, List.of()).stream()
                .map(values::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
    }

    private static OptionalDouble centerBarycenter(
            int nodeId,
            Map<Integer, List<Integer>> neighbors,
            Map<Integer, Double> values) {
        return neighbors.getOrDefault(nodeId, List.of()).stream()
                .map(values::get)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
    }

    private static List<ComponentVertices> components(
            NormalizedGraph normalized,
            Map<Integer, StableOrder> stableOrders,
            Comparator<LayerVertex> stableComparator) {
        int[] parent = new int[normalized.vertices().size()];
        Map<Integer, Integer> firstOrdinalByHint = new HashMap<>();
        for (LayerVertex node : normalized.vertices()) {
            parent[node.id()] = node.id();
            if (node.isReal()) {
                Integer firstOrdinal = firstOrdinalByHint.putIfAbsent(
                        node.componentHint(), node.id());
                if (firstOrdinal != null) {
                    union(parent, firstOrdinal, node.id());
                }
            }
        }
        for (LayerEdge edge : normalized.edges()) {
            union(parent, edge.prerequisiteId(), edge.dependentId());
        }
        Map<Integer, List<LayerVertex>> nodesByRoot = new HashMap<>();
        for (LayerVertex node : normalized.vertices()) {
            nodesByRoot.computeIfAbsent(find(parent, node.id()), ignored -> new ArrayList<>())
                    .add(node);
        }
        return nodesByRoot.values().stream()
                .map(nodes -> {
                    nodes.sort(stableComparator);
                    return new ComponentVertices(List.copyOf(nodes));
                })
                .sorted(Comparator
                        .comparing((ComponentVertices component) -> component.vertices().stream()
                                .map(node -> stableOrders.get(node.id()))
                                .min(Comparator.naturalOrder())
                                .orElseThrow())
                        .thenComparing(component ->
                                component.vertices().get(0).stableKey()))
                .toList();
    }

    private static NormalizedGraph normalize(
            ResearchTreeLayoutInput input,
            ResearchTreeLayoutPolicy policy) {
        List<LayerVertex> vertices = new ArrayList<>(input.nodes().size());
        Map<ResourceLocation, ResearchTreeLayoutInput.Node> nodesById = new HashMap<>();
        Map<Integer, Integer> entriesByRank = new HashMap<>();
        Map<GroupRank, List<ResearchTreeLayoutInput.Node>> realNodesByGroupRank =
                new HashMap<>();
        for (ResearchTreeLayoutInput.Node node : input.nodes()) {
            nodesById.put(node.nodeId(), node);
            entriesByRank.merge(node.rank(), 1, Integer::sum);
            realNodesByGroupRank.computeIfAbsent(
                    new GroupRank(node.groupOrder(), node.rank()),
                    ignored -> new ArrayList<>()).add(node);
            vertices.add(new LayerVertex(
                    node.ordinal(), node, node.rank(), node.groupOrder(), node.orderInRank(),
                    node.componentHint(), 0, node.nodeId().toString()));
        }
        realNodesByGroupRank.values().forEach(nodes -> nodes.sort(Comparator
                .comparingInt(ResearchTreeLayoutInput.Node::orderInRank)
                .thenComparing(node -> node.nodeId().toString())));

        List<LayerEdge> normalizedEdges = new ArrayList<>();
        Map<ResearchTreeLayoutInput.Edge, List<Integer>> waypointsByEdge =
                new LinkedHashMap<>();
        int rankCapacity = Math.max(1, Math.toIntExact(
                (policy.maxRankBlockWidth() + (long) policy.nodeGap())
                        / (ResearchTreeLayout.NODE_WIDTH + (long) policy.nodeGap())));
        int virtualCount = 0;
        for (ResearchTreeLayoutInput.Edge edge : input.edges()) {
            ResearchTreeLayoutInput.Node prerequisite = nodesById.get(edge.prerequisiteId());
            ResearchTreeLayoutInput.Node dependent = nodesById.get(edge.dependentId());
            int waypointCount = dependent.rank() - prerequisite.rank() - 1;
            List<ResearchTreeLayoutInput.Node> sourceRank = realNodesByGroupRank.getOrDefault(
                    new GroupRank(prerequisite.groupOrder(), prerequisite.rank()),
                    List.of(prerequisite));
            int sourceIndex = sourceRank.indexOf(prerequisite);
            int corridorSide = sourceIndex * 2 >= sourceRank.size() - 1 ? 1 : -1;
            boolean normalizeEdge = waypointCount > 0
                    && waypointCount < BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH
                    && virtualCount <= MAX_VIRTUAL_VERTICES - waypointCount;
            if (normalizeEdge) {
                for (int rank = prerequisite.rank() + 1; rank < dependent.rank(); rank++) {
                    if (entriesByRank.getOrDefault(rank, 0) >= rankCapacity) {
                        normalizeEdge = false;
                        break;
                    }
                }
            }
            int previousId = prerequisite.ordinal();
            List<Integer> waypointIds = new ArrayList<>(Math.max(0, waypointCount));
            if (normalizeEdge) {
                for (int rank = prerequisite.rank() + 1; rank < dependent.rank(); rank++) {
                    int vertexId = vertices.size();
                    String stableKey = edge.prerequisiteId() + "->" + edge.dependentId()
                            + "#" + rank;
                    vertices.add(new LayerVertex(
                            vertexId,
                            null,
                            rank,
                            prerequisite.groupOrder(),
                            prerequisite.orderInRank(),
                            prerequisite.componentHint(),
                            corridorSide,
                            stableKey));
                    normalizedEdges.add(new LayerEdge(previousId, vertexId));
                    waypointIds.add(vertexId);
                    entriesByRank.merge(rank, 1, Integer::sum);
                    previousId = vertexId;
                    virtualCount++;
                }
                waypointsByEdge.put(edge, List.copyOf(waypointIds));
            }
            normalizedEdges.add(new LayerEdge(previousId, dependent.ordinal()));
        }
        return new NormalizedGraph(
                List.copyOf(vertices),
                List.copyOf(normalizedEdges),
                Map.copyOf(waypointsByEdge));
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

    private static int packingColumns(int componentCount, int cellWidth, int cellHeight) {
        double aspectAdjusted = componentCount * (double) cellHeight / cellWidth;
        return Math.max(1, Math.min(
                componentCount,
                (int) Math.ceil(Math.sqrt(aspectAdjusted))));
    }

    private static int divideRoundUp(int value, int divisor) {
        return 1 + (value - 1) / divisor;
    }

    private static void ensureDimension(int value) {
        if (value <= 0 || value > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "layered research layout exceeds its dimension limit");
        }
    }

    /** Convert multi-row component packing into compact visual tiers ordered by Y. */
    private static Map<Integer, Integer> visualTierByY(List<DraftPosition> drafts) {
        List<Integer> occupiedY = drafts.stream()
                .map(DraftPosition::y)
                .distinct()
                .sorted()
                .toList();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < occupiedY.size(); index++) {
            result.put(occupiedY.get(index), occupiedY.size() - 1 - index);
        }
        return Map.copyOf(result);
    }

    private record PreparedGraph(
            ResearchTreeLayoutInput input,
            NormalizedGraph normalized,
            Map<Integer, StableOrder> stableOrders,
            Map<Integer, List<Integer>> prerequisites,
            Map<Integer, List<Integer>> dependents,
            List<ComponentVertices> components,
            List<Integer> occupiedRanks) {
    }

    private record StableOrder(
            int rank,
            int groupOrder,
            int siblingOrder,
            String nodeId) implements Comparable<StableOrder> {
        @Override
        public int compareTo(StableOrder other) {
            int compared = Integer.compare(groupOrder, other.groupOrder);
            if (compared == 0) {
                compared = Integer.compare(rank, other.rank);
            }
            if (compared == 0) {
                compared = Integer.compare(siblingOrder, other.siblingOrder);
            }
            if (compared == 0) {
                compared = nodeId.compareTo(other.nodeId);
            }
            return compared;
        }
    }

    private record NormalizedGraph(
            List<LayerVertex> vertices,
            List<LayerEdge> edges,
            Map<ResearchTreeLayoutInput.Edge, List<Integer>> waypointsByEdge) {
    }

    private record LayerVertex(
            int id,
            ResearchTreeLayoutInput.Node sourceNode,
            int rank,
            int groupOrder,
            int siblingOrder,
            int componentHint,
            int corridorSide,
            String stableKey) {
        private boolean isReal() {
            return sourceNode != null;
        }
    }

    private record GroupRank(int groupOrder, int rank) {
    }

    private record LayerEdge(int prerequisiteId, int dependentId) {
    }

    private record ComponentVertices(List<LayerVertex> vertices) {
    }

    private record PreparedComponent(
            List<LayerVertex> vertices,
            Map<Integer, Double> centers,
            double minimumCenter,
            int width) {
    }

    private record PackedComponents(
            List<DraftPosition> drafts,
            int cellWidth,
            int cellHeight,
            int columns,
            int rows) {
    }

    private record DraftPosition(
            LayerVertex vertex,
            int component,
            int rank,
            int x,
            int y) {
    }
}
