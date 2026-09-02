package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

/** Composes one All Weapons atlas from reusable group-local skeletons. */
public final class ResearchTreeOverviewLayoutComposer {
    private ResearchTreeOverviewLayoutComposer() {
    }

    public static ResearchTreeLayout compose(
            ResearchTreePublication overview,
            ResearchTreeGroupSkeletonCatalog skeletons,
            ResearchTreeLayoutPolicy policy) {
        if (overview == null || skeletons == null || policy == null) {
            throw new IllegalArgumentException("Research Tree overview composition cannot be null");
        }
        if (overview.graph().nodes().isEmpty()) {
            return ResearchTreeLayout.EMPTY;
        }

        Prepared prepared = prepare(overview, skeletons);
        List<Island> islands = buildIslands(prepared, policy);
        return pack(overview.graph(), islands, policy);
    }

    private static Prepared prepare(
            ResearchTreePublication overview,
            ResearchTreeGroupSkeletonCatalog skeletons) {
        Map<ResourceLocation, SourceNode> nodes = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> groupByNode = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> groupOrders = new LinkedHashMap<>();
        Map<ResourceLocation, ResearchTreeGroupSkeleton> selectedSkeletons =
                new LinkedHashMap<>();
        List<SourceRouteHint> routeHints = new ArrayList<>();

        for (ResearchTreePresentation.Group group : overview.presentation().groups()) {
            ResearchTreeGroupSkeleton skeleton = skeletons.group(group.id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Research Tree overview group has no reusable skeleton"));
            Set<ResourceLocation> expectedIds = group.members().stream()
                    .map(ResearchTreePresentation.Member::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<ResourceLocation> skeletonIds = skeleton.nodes().stream()
                    .map(ResearchTreeGroupSkeleton.PositionedNode::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!expectedIds.equals(skeletonIds)) {
                throw new IllegalArgumentException(
                        "Research Tree overview group and skeleton membership differ");
            }
            groupOrders.put(group.id(), group.order());
            selectedSkeletons.put(group.id(), skeleton);
            for (ResearchTreeLayout.EdgeRouteHint hint : skeleton.edgeRouteHints()) {
                routeHints.add(new SourceRouteHint(
                        group.id(),
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> new SourceWaypoint(
                                        waypoint.rank(), waypoint.x(), waypoint.y()))
                                .toList()));
            }
            for (ResearchTreePresentation.Member member : group.members()) {
                ResearchTreeGraph.Node graphNode = overview.graph()
                        .node(member.nodeId())
                        .orElseThrow();
                ResearchTreeGroupSkeleton.PositionedNode skeletonNode = skeleton
                        .position(member.nodeId())
                        .orElseThrow();
                SourceNode source = new SourceNode(
                        graphNode.ordinal(),
                        graphNode.blueprintId(),
                        group.id(),
                        group.order(),
                        member.rank(),
                        member.orderInRank(),
                        skeletonNode.x(),
                        skeletonNode.y());
                if (nodes.put(source.nodeId(), source) != null
                        || groupByNode.put(source.nodeId(), group.id()) != null) {
                    throw new IllegalArgumentException(
                            "Research Tree overview composition contains a duplicate node");
                }
            }
        }
        if (nodes.size() != overview.graph().nodes().size()) {
            throw new IllegalArgumentException(
                    "Research Tree overview composition omits a graph node");
        }

        for (ResearchTreeGraph.Edge edge : overview.graph().edges()) {
            ResourceLocation prerequisiteGroup = groupByNode.get(edge.prerequisiteId());
            ResourceLocation dependentGroup = groupByNode.get(edge.dependentId());
            boolean represented = prerequisiteGroup.equals(dependentGroup)
                    ? selectedSkeletons.get(prerequisiteGroup).containsInternalEdge(edge)
                    : skeletons.containsCrossGroupEdge(edge);
            if (!represented) {
                throw new IllegalArgumentException(
                        "Research Tree overview edge is absent from the skeleton catalog");
            }
        }

        Map<ResourceLocation, List<ResourceLocation>> prerequisites = adjacency(
                overview.graph(), false);
        Map<ResourceLocation, List<ResourceLocation>> dependents = adjacency(
                overview.graph(), true);
        List<List<ResourceLocation>> islandGroups = islandGroups(
                overview, groupByNode, groupOrders);
        return new Prepared(
                Map.copyOf(nodes),
                prerequisites,
                dependents,
                List.copyOf(islandGroups),
                List.copyOf(routeHints));
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
        result.replaceAll((ignored, values) -> values.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList());
        return Map.copyOf(result);
    }

    private static List<List<ResourceLocation>> islandGroups(
            ResearchTreePublication overview,
            Map<ResourceLocation, ResourceLocation> groupByNode,
            Map<ResourceLocation, Integer> groupOrders) {
        int groupCount = overview.presentation().groups().size();
        int[] parent = new int[groupCount];
        Map<ResourceLocation, Integer> ordinals = new LinkedHashMap<>();
        for (ResearchTreePresentation.Group group : overview.presentation().groups()) {
            parent[group.order()] = group.order();
            ordinals.put(group.id(), group.order());
        }
        for (ResearchTreeGraph.Edge edge : overview.graph().edges()) {
            union(
                    parent,
                    ordinals.get(groupByNode.get(edge.prerequisiteId())),
                    ordinals.get(groupByNode.get(edge.dependentId())));
        }
        Map<Integer, List<ResourceLocation>> byRoot = new LinkedHashMap<>();
        for (ResearchTreePresentation.Group group : overview.presentation().groups()) {
            byRoot.computeIfAbsent(find(parent, group.order()), ignored -> new ArrayList<>())
                    .add(group.id());
        }
        Comparator<ResourceLocation> groupOrder = Comparator
                .comparingInt((ResourceLocation groupId) -> groupOrders.get(groupId))
                .thenComparing(ResourceLocation::toString);
        byRoot.values().forEach(groups -> groups.sort(groupOrder));
        return byRoot.values().stream()
                .sorted(Comparator
                        .comparingInt((List<ResourceLocation> groups) ->
                                groups.stream().mapToInt(groupOrders::get).min().orElseThrow())
                        .thenComparing(groups -> groups.get(0).toString()))
                .map(List::copyOf)
                .toList();
    }

    private static List<Island> buildIslands(
            Prepared prepared,
            ResearchTreeLayoutPolicy policy) {
        Map<ResourceLocation, List<SourceNode>> nodesByGroup = new LinkedHashMap<>();
        prepared.nodes().values().forEach(node -> nodesByGroup
                .computeIfAbsent(node.groupId(), ignored -> new ArrayList<>())
                .add(node));
        List<Island> result = new ArrayList<>(prepared.islandGroups().size());
        for (int islandIndex = 0; islandIndex < prepared.islandGroups().size(); islandIndex++) {
            List<SourceNode> sourceNodes = new ArrayList<>();
            prepared.islandGroups().get(islandIndex).forEach(groupId ->
                    sourceNodes.addAll(nodesByGroup.getOrDefault(groupId, List.of())));
            sourceNodes.sort(Comparator.comparingInt(SourceNode::ordinal));
            Set<ResourceLocation> islandGroupIds = Set.copyOf(
                    prepared.islandGroups().get(islandIndex));
            result.add(buildIsland(
                    islandIndex,
                    List.copyOf(sourceNodes),
                    prepared.prerequisites(),
                    prepared.dependents(),
                    prepared.routeHints().stream()
                            .filter(hint -> islandGroupIds.contains(hint.groupId()))
                            .toList(),
                    policy));
        }
        return List.copyOf(result);
    }

    private static Island buildIsland(
            int islandIndex,
            List<SourceNode> sourceNodes,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, List<ResourceLocation>> dependents,
            List<SourceRouteHint> routeHints,
            ResearchTreeLayoutPolicy policy) {
        TreeMap<Integer, List<Block>> blocksByRank = new TreeMap<>();
        Map<BlockKey, List<SourceNode>> sourcesByBlock = new LinkedHashMap<>();
        Map<BlockKey, List<SourceWaypoint>> waypointsByBlock = new LinkedHashMap<>();
        for (SourceNode source : sourceNodes) {
            sourcesByBlock.computeIfAbsent(
                    new BlockKey(source.groupId(), source.rank()),
                    ignored -> new ArrayList<>()).add(source);
        }
        for (SourceRouteHint hint : routeHints) {
            for (SourceWaypoint waypoint : hint.waypoints()) {
                waypointsByBlock.computeIfAbsent(
                        new BlockKey(hint.groupId(), waypoint.rank()),
                        ignored -> new ArrayList<>()).add(waypoint);
            }
        }
        Map<BlockKey, Block> blocksByKey = new LinkedHashMap<>();
        for (Map.Entry<BlockKey, List<SourceNode>> entry : sourcesByBlock.entrySet()) {
            Block block = block(
                    entry.getKey(),
                    entry.getValue(),
                    waypointsByBlock.getOrDefault(entry.getKey(), List.of()),
                    policy);
            blocksByKey.put(entry.getKey(), block);
            blocksByRank.computeIfAbsent(block.key().rank(), ignored -> new ArrayList<>())
                    .add(block);
        }
        Comparator<Block> stableBlockOrder = Comparator
                .comparingInt(Block::groupOrder)
                .thenComparing(block -> block.key().groupId().toString());
        blocksByRank.values().forEach(blocks -> blocks.sort(stableBlockOrder));

        Map<ResourceLocation, Block> blockByNode = new HashMap<>();
        Map<ResourceLocation, NodeOffset> offsetByNode = new HashMap<>();
        for (List<Block> blocks : blocksByRank.values()) {
            for (Block block : blocks) {
                for (NodeOffset offset : block.nodes()) {
                    blockByNode.put(offset.source().nodeId(), block);
                    offsetByNode.put(offset.source().nodeId(), offset);
                }
            }
        }
        orderBlocks(
                blocksByRank,
                blockByNode,
                prerequisites,
                dependents,
                stableBlockOrder,
                policy.orderingSweeps());
        compactBlocks(
                blocksByRank,
                blockByNode,
                offsetByNode,
                prerequisites,
                dependents,
                policy.interGroupGap(),
                policy.compactionSweeps());

        Map<Integer, Integer> yByRank = new LinkedHashMap<>();
        int nextY = 0;
        List<Integer> descendingRanks = blocksByRank.descendingKeySet().stream().toList();
        for (int rank : descendingRanks) {
            yByRank.put(rank, nextY);
            int rankHeight = blocksByRank.get(rank).stream()
                    .mapToInt(Block::height)
                    .max()
                    .orElseThrow();
            nextY = Math.addExact(nextY, rankHeight);
            if (rank != descendingRanks.get(descendingRanks.size() - 1)) {
                nextY = Math.addExact(nextY, policy.tierGap());
            }
        }

        double minimumOrigin = blocksByRank.values().stream()
                .flatMap(List::stream)
                .mapToDouble(Block::originX)
                .min()
                .orElseThrow();
        List<IslandNode> positions = new ArrayList<>(sourceNodes.size());
        int width = 0;
        for (List<Block> blocks : blocksByRank.values()) {
            for (Block block : blocks) {
                int originX = (int) Math.round(block.originX() - minimumOrigin);
                int originY = yByRank.get(block.key().rank());
                for (NodeOffset offset : block.nodes()) {
                    int x = Math.addExact(originX, offset.x());
                    int y = Math.addExact(originY, offset.y());
                    positions.add(new IslandNode(offset.source(), x, y));
                    width = Math.max(width, Math.addExact(x, ResearchTreeLayout.NODE_WIDTH));
                }
            }
        }
        positions.sort(Comparator.comparingInt(node -> node.source().ordinal()));
        Map<ResourceLocation, SourceNode> sourceById = sourceNodes.stream().collect(
                java.util.stream.Collectors.toMap(
                        SourceNode::nodeId,
                        source -> source,
                        (left, right) -> {
                            throw new IllegalArgumentException(
                                    "Research Tree overview contains a duplicate source node");
                        },
                        LinkedHashMap::new));
        List<ResearchTreeLayout.EdgeRouteHint> positionedRouteHints = new ArrayList<>();
        for (SourceRouteHint hint : routeHints) {
            List<ResearchTreeLayout.RouteWaypoint> positionedWaypoints = new ArrayList<>();
            SourceNode prerequisite = sourceById.get(hint.prerequisiteId());
            SourceNode dependent = sourceById.get(hint.dependentId());
            Block prerequisiteBlock = prerequisite == null
                    ? null
                    : blocksByKey.get(new BlockKey(hint.groupId(), prerequisite.rank()));
            Block dependentBlock = dependent == null
                    ? null
                    : blocksByKey.get(new BlockKey(hint.groupId(), dependent.rank()));
            boolean preserved = prerequisiteBlock != null
                    && dependentBlock != null
                    && prerequisiteBlock.preservesSkeletonGeometry()
                    && dependentBlock.preservesSkeletonGeometry();
            for (SourceWaypoint waypoint : hint.waypoints()) {
                if (!preserved) {
                    break;
                }
                Block block = blocksByKey.get(new BlockKey(hint.groupId(), waypoint.rank()));
                if (block == null || !block.preservesSkeletonGeometry()) {
                    preserved = false;
                    break;
                }
                int blockOriginX = (int) Math.round(block.originX() - minimumOrigin);
                positionedWaypoints.add(new ResearchTreeLayout.RouteWaypoint(
                        waypoint.rank(),
                        Math.addExact(
                                blockOriginX,
                                waypoint.skeletonX() - block.skeletonMinimumX()),
                        Math.addExact(
                                yByRank.get(waypoint.rank()),
                                waypoint.skeletonY() - block.skeletonMinimumY())));
            }
            if (preserved && !positionedWaypoints.isEmpty()) {
                positionedRouteHints.add(new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(), hint.dependentId(), positionedWaypoints));
            }
        }
        ensureDimension(width);
        ensureDimension(nextY);
        return new Island(
                islandIndex,
                width,
                nextY,
                List.copyOf(positions),
                List.copyOf(positionedRouteHints));
    }

    private static Block block(
            BlockKey key,
            List<SourceNode> sources,
            List<SourceWaypoint> routeWaypoints,
            ResearchTreeLayoutPolicy policy) {
        List<SourceNode> ordered = sources.stream()
                .sorted(Comparator
                        .comparingInt(SourceNode::skeletonY)
                        .thenComparingInt(SourceNode::skeletonX)
                        .thenComparingInt(SourceNode::orderInRank)
                        .thenComparing(node -> node.nodeId().toString()))
                .toList();
        int minimumNodeX = ordered.stream().mapToInt(SourceNode::skeletonX).min().orElseThrow();
        int minimumRouteX = routeWaypoints.stream()
                .mapToInt(waypoint -> waypoint.skeletonX() - ResearchTreeLayout.NODE_WIDTH / 2)
                .min()
                .orElse(minimumNodeX);
        int minimumX = Math.min(minimumNodeX, minimumRouteX);
        int minimumNodeY = ordered.stream().mapToInt(SourceNode::skeletonY).min().orElseThrow();
        int minimumRouteY = routeWaypoints.stream()
                .mapToInt(waypoint -> waypoint.skeletonY()
                        - ResearchTreeLayout.NODE_HEIGHT / 2)
                .min()
                .orElse(minimumNodeY);
        int minimumY = Math.min(minimumNodeY, minimumRouteY);
        int maximumRight = ordered.stream()
                .mapToInt(node -> node.skeletonX() - minimumX + ResearchTreeLayout.NODE_WIDTH)
                .max()
                .orElseThrow();
        maximumRight = Math.max(
                maximumRight,
                routeWaypoints.stream()
                        .mapToInt(waypoint -> waypoint.skeletonX() - minimumX
                                + ResearchTreeLayout.NODE_WIDTH / 2)
                        .max()
                        .orElse(maximumRight));
        int maximumBottom = ordered.stream()
                .mapToInt(node -> node.skeletonY() - minimumY
                        + ResearchTreeLayout.NODE_HEIGHT)
                .max()
                .orElseThrow();
        maximumBottom = Math.max(
                maximumBottom,
                routeWaypoints.stream()
                        .mapToInt(waypoint -> waypoint.skeletonY() - minimumY
                                + ResearchTreeLayout.NODE_HEIGHT / 2)
                        .max()
                        .orElse(maximumBottom));
        List<NodeOffset> offsets = new ArrayList<>(ordered.size());
        int width;
        int height;
        boolean preservesSkeletonGeometry = maximumRight <= policy.maxRankBlockWidth();
        if (preservesSkeletonGeometry) {
            ordered.forEach(source -> offsets.add(new NodeOffset(
                    source,
                    source.skeletonX() - minimumX,
                    source.skeletonY() - minimumY)));
            width = maximumRight;
            height = maximumBottom;
        } else {
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(ordered.size())));
            int rows = divideRoundUp(ordered.size(), columns);
            int horizontalPitch = Math.addExact(
                    ResearchTreeLayout.NODE_WIDTH, policy.nodeGap());
            int verticalPitch = Math.addExact(
                    ResearchTreeLayout.NODE_HEIGHT, policy.tierGap());
            for (int index = 0; index < ordered.size(); index++) {
                offsets.add(new NodeOffset(
                        ordered.get(index),
                        Math.multiplyExact(index % columns, horizontalPitch),
                        Math.multiplyExact(index / columns, verticalPitch)));
            }
            width = Math.addExact(
                    ResearchTreeLayout.NODE_WIDTH,
                    Math.multiplyExact(Math.min(columns, ordered.size()) - 1, horizontalPitch));
            height = Math.addExact(
                    ResearchTreeLayout.NODE_HEIGHT,
                    Math.multiplyExact(rows - 1, verticalPitch));
        }
        return new Block(
                key,
                ordered.get(0).groupOrder(),
                List.copyOf(offsets),
                width,
                height,
                minimumX,
                minimumY,
                preservesSkeletonGeometry);
    }

    private static void orderBlocks(
            TreeMap<Integer, List<Block>> blocksByRank,
            Map<ResourceLocation, Block> blockByNode,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, List<ResourceLocation>> dependents,
            Comparator<Block> stableOrder,
            int sweeps) {
        List<Integer> ranks = List.copyOf(blocksByRank.keySet());
        for (int sweep = 0; sweep < sweeps; sweep++) {
            Map<Block, Integer> order = blockOrder(blocksByRank);
            for (int rankIndex = 1; rankIndex < ranks.size(); rankIndex++) {
                sortBlocks(
                        blocksByRank.get(ranks.get(rankIndex)),
                        prerequisites,
                        blockByNode,
                        order,
                        stableOrder);
                updateBlockOrder(blocksByRank.get(ranks.get(rankIndex)), order);
            }
            order = blockOrder(blocksByRank);
            for (int rankIndex = ranks.size() - 2; rankIndex >= 0; rankIndex--) {
                sortBlocks(
                        blocksByRank.get(ranks.get(rankIndex)),
                        dependents,
                        blockByNode,
                        order,
                        stableOrder);
                updateBlockOrder(blocksByRank.get(ranks.get(rankIndex)), order);
            }
        }
    }

    private static Map<Block, Integer> blockOrder(TreeMap<Integer, List<Block>> blocksByRank) {
        Map<Block, Integer> result = new HashMap<>();
        blocksByRank.values().forEach(blocks -> updateBlockOrder(blocks, result));
        return result;
    }

    private static void updateBlockOrder(List<Block> blocks, Map<Block, Integer> order) {
        for (int index = 0; index < blocks.size(); index++) {
            order.put(blocks.get(index), index);
        }
    }

    private static void sortBlocks(
            List<Block> blocks,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Block> blockByNode,
            Map<Block, Integer> order,
            Comparator<Block> stableOrder) {
        Map<Block, Double> scores = new HashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            scores.put(block, blockBarycenter(block, neighbors, blockByNode, order)
                    .orElse((double) index));
        }
        blocks.sort(Comparator
                .comparingDouble((Block block) -> scores.get(block))
                .thenComparing(stableOrder));
    }

    private static OptionalDouble blockBarycenter(
            Block block,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Block> blockByNode,
            Map<Block, Integer> order) {
        return block.nodes().stream()
                .flatMap(node -> neighbors.getOrDefault(
                        node.source().nodeId(), List.of()).stream())
                .map(blockByNode::get)
                .filter(java.util.Objects::nonNull)
                .map(order::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
    }

    private static void compactBlocks(
            TreeMap<Integer, List<Block>> blocksByRank,
            Map<ResourceLocation, Block> blockByNode,
            Map<ResourceLocation, NodeOffset> offsetByNode,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, List<ResourceLocation>> dependents,
            int gap,
            int sweeps) {
        for (List<Block> blocks : blocksByRank.values()) {
            double next = 0.0D;
            for (Block block : blocks) {
                block.setOriginX(next);
                next += block.width() + gap;
            }
        }
        List<Integer> ranks = List.copyOf(blocksByRank.keySet());
        for (int sweep = 0; sweep < sweeps; sweep++) {
            for (int rankIndex = 1; rankIndex < ranks.size(); rankIndex++) {
                compactRank(
                        blocksByRank.get(ranks.get(rankIndex)),
                        prerequisites,
                        blockByNode,
                        offsetByNode,
                        gap);
            }
            for (int rankIndex = ranks.size() - 2; rankIndex >= 0; rankIndex--) {
                compactRank(
                        blocksByRank.get(ranks.get(rankIndex)),
                        dependents,
                        blockByNode,
                        offsetByNode,
                        gap);
            }
        }
    }

    private static void compactRank(
            List<Block> blocks,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Block> blockByNode,
            Map<ResourceLocation, NodeOffset> offsetByNode,
            int gap) {
        double[] desired = new double[blocks.size()];
        double[] placed = new double[blocks.size()];
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            desired[index] = desiredOrigin(
                    block, neighbors, blockByNode, offsetByNode).orElse(block.originX());
            placed[index] = index == 0
                    ? desired[index]
                    : Math.max(
                            desired[index],
                            placed[index - 1] + blocks.get(index - 1).width() + gap);
        }
        double desiredAverage = java.util.Arrays.stream(desired).average().orElse(0.0D);
        double placedAverage = java.util.Arrays.stream(placed).average().orElse(0.0D);
        double shift = desiredAverage - placedAverage;
        for (int index = 0; index < blocks.size(); index++) {
            blocks.get(index).setOriginX(placed[index] + shift);
        }
    }

    private static OptionalDouble desiredOrigin(
            Block block,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Block> blockByNode,
            Map<ResourceLocation, NodeOffset> offsetByNode) {
        return block.nodes().stream()
                .flatMap(offset -> neighbors.getOrDefault(
                                offset.source().nodeId(), List.of()).stream()
                        .map(neighborId -> new NodePair(offset, neighborId)))
                .mapToDouble(pair -> {
                    Block neighborBlock = blockByNode.get(pair.neighborId());
                    NodeOffset neighborOffset = offsetByNode.get(pair.neighborId());
                    return neighborBlock.originX() + neighborOffset.x() - pair.local().x();
                })
                .average();
    }

    private static ResearchTreeLayout pack(
            ResearchTreeGraph graph,
            List<Island> islands,
            ResearchTreeLayoutPolicy policy) {
        int cellWidth = islands.stream().mapToInt(Island::width).max().orElseThrow();
        int cellHeight = islands.stream().mapToInt(Island::height).max().orElseThrow();
        int columns = packingColumns(islands.size(), cellWidth, cellHeight);
        int rows = divideRoundUp(islands.size(), columns);
        int columnsInWidestRow = Math.min(columns, islands.size());
        int portalClearance = policy.portalClearance();
        int width = Math.addExact(
                Math.multiplyExact(2, policy.canvasPadding()),
                Math.addExact(
                        Math.multiplyExact(columnsInWidestRow, cellWidth),
                        Math.multiplyExact(columnsInWidestRow - 1, policy.componentGap())));
        int height = Math.addExact(
                Math.addExact(
                        Math.multiplyExact(2, policy.canvasPadding()),
                        Math.multiplyExact(2, portalClearance)),
                Math.addExact(
                        Math.multiplyExact(rows, cellHeight),
                        Math.multiplyExact(rows - 1, policy.componentGap())));
        ensureDimension(width);
        ensureDimension(height);

        List<GlobalNode> global = new ArrayList<>(graph.nodes().size());
        List<ResearchTreeLayout.EdgeRouteHint> globalRouteHints = new ArrayList<>();
        for (int islandIndex = 0; islandIndex < islands.size(); islandIndex++) {
            Island island = islands.get(islandIndex);
            int column = islandIndex % columns;
            int row = islandIndex / columns;
            int originX = Math.addExact(
                    policy.canvasPadding(),
                    Math.addExact(
                            Math.multiplyExact(column, cellWidth + policy.componentGap()),
                            (cellWidth - island.width()) / 2));
            int originY = Math.addExact(
                    Math.addExact(policy.canvasPadding(), portalClearance),
                    Math.multiplyExact(row, cellHeight + policy.componentGap()));
            for (IslandNode node : island.nodes()) {
                global.add(new GlobalNode(
                        node.source(),
                        island.index(),
                        Math.addExact(originX, node.x()),
                        Math.addExact(originY, node.y())));
            }
            for (ResearchTreeLayout.EdgeRouteHint hint : island.edgeRouteHints()) {
                globalRouteHints.add(new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> new ResearchTreeLayout.RouteWaypoint(
                                        waypoint.rank(),
                                        Math.addExact(originX, waypoint.x()),
                                        Math.addExact(originY, waypoint.y())))
                                .toList()));
            }
        }

        boolean visualTiers = islands.size() > 1;
        Map<Integer, Integer> tierByY = visualTiers ? visualTierByY(global) : Map.of();
        Map<Integer, List<GlobalNode>> nodesByTier = new TreeMap<>();
        for (GlobalNode node : global) {
            int tier = visualTiers ? tierByY.get(node.y()) : node.source().rank();
            nodesByTier.computeIfAbsent(tier, ignored -> new ArrayList<>()).add(node);
        }
        ResearchTreeLayout.PositionedNode[] positioned =
                new ResearchTreeLayout.PositionedNode[graph.nodes().size()];
        for (Map.Entry<Integer, List<GlobalNode>> entry : nodesByTier.entrySet()) {
            List<GlobalNode> tierNodes = entry.getValue();
            tierNodes.sort(Comparator
                    .comparingInt(GlobalNode::y)
                    .thenComparingInt(GlobalNode::x)
                    .thenComparingInt(node -> node.source().groupOrder())
                    .thenComparingInt(node -> node.source().orderInRank())
                    .thenComparing(node -> node.source().nodeId().toString()));
            for (int order = 0; order < tierNodes.size(); order++) {
                GlobalNode node = tierNodes.get(order);
                positioned[node.source().ordinal()] = new ResearchTreeLayout.PositionedNode(
                        node.source().ordinal(),
                        node.source().nodeId(),
                        node.component(),
                        entry.getKey(),
                        order,
                        node.x(),
                        node.y());
            }
        }
        int tierCount = visualTiers
                ? tierByY.size()
                : global.stream().mapToInt(node -> node.source().rank()).max().orElseThrow() + 1;
        return new ResearchTreeLayout(
                width,
                height,
                tierCount,
                List.of(positioned),
                List.of(),
                List.of(),
                List.of(),
                globalRouteHints);
    }

    private static Map<Integer, Integer> visualTierByY(List<GlobalNode> nodes) {
        List<Integer> occupiedY = nodes.stream()
                .map(GlobalNode::y)
                .distinct()
                .sorted()
                .toList();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < occupiedY.size(); index++) {
            result.put(occupiedY.get(index), occupiedY.size() - 1 - index);
        }
        return Map.copyOf(result);
    }

    private static int packingColumns(int count, int cellWidth, int cellHeight) {
        double aspectAdjusted = count * (double) cellHeight / cellWidth;
        return Math.max(1, Math.min(
                count,
                (int) Math.ceil(Math.sqrt(aspectAdjusted))));
    }

    private static int divideRoundUp(int value, int divisor) {
        return 1 + (value - 1) / divisor;
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

    private static void ensureDimension(int value) {
        if (value <= 0 || value > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "composed Research Tree overview exceeds its dimension limit");
        }
    }

    private record Prepared(
            Map<ResourceLocation, SourceNode> nodes,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, List<ResourceLocation>> dependents,
            List<List<ResourceLocation>> islandGroups,
            List<SourceRouteHint> routeHints) {
    }

    private record SourceNode(
            int ordinal,
            ResourceLocation nodeId,
            ResourceLocation groupId,
            int groupOrder,
            int rank,
            int orderInRank,
            int skeletonX,
            int skeletonY) {
    }

    private record SourceRouteHint(
            ResourceLocation groupId,
            ResourceLocation prerequisiteId,
            ResourceLocation dependentId,
            List<SourceWaypoint> waypoints) {
    }

    private record SourceWaypoint(int rank, int skeletonX, int skeletonY) {
    }

    private record BlockKey(ResourceLocation groupId, int rank) {
    }

    private record NodeOffset(SourceNode source, int x, int y) {
    }

    private record NodePair(NodeOffset local, ResourceLocation neighborId) {
    }

    private static final class Block {
        private final BlockKey key;
        private final int groupOrder;
        private final List<NodeOffset> nodes;
        private final int width;
        private final int height;
        private final int skeletonMinimumX;
        private final int skeletonMinimumY;
        private final boolean preservesSkeletonGeometry;
        private double originX;

        private Block(
                BlockKey key,
                int groupOrder,
                List<NodeOffset> nodes,
                int width,
                int height,
                int skeletonMinimumX,
                int skeletonMinimumY,
                boolean preservesSkeletonGeometry) {
            this.key = key;
            this.groupOrder = groupOrder;
            this.nodes = nodes;
            this.width = width;
            this.height = height;
            this.skeletonMinimumX = skeletonMinimumX;
            this.skeletonMinimumY = skeletonMinimumY;
            this.preservesSkeletonGeometry = preservesSkeletonGeometry;
        }

        private BlockKey key() {
            return key;
        }

        private int groupOrder() {
            return groupOrder;
        }

        private List<NodeOffset> nodes() {
            return nodes;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private int skeletonMinimumX() {
            return skeletonMinimumX;
        }

        private int skeletonMinimumY() {
            return skeletonMinimumY;
        }

        private boolean preservesSkeletonGeometry() {
            return preservesSkeletonGeometry;
        }

        private double originX() {
            return originX;
        }

        private void setOriginX(double originX) {
            this.originX = originX;
        }
    }

    private record Island(
            int index,
            int width,
            int height,
            List<IslandNode> nodes,
            List<ResearchTreeLayout.EdgeRouteHint> edgeRouteHints) {
    }

    private record IslandNode(SourceNode source, int x, int y) {
    }

    private record GlobalNode(SourceNode source, int component, int x, int y) {
    }
}
