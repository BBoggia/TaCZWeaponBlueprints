package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

/**
 * Tech-tree-only horizontal finish pass. The shared layout kernel minimizes
 * crossings, while this pass turns its unbounded sparse corridors into a
 * compact shared base and introduces small, progressive upper-branch gutters.
 */
final class ResearchTechTreeBranchCompactor {
    private ResearchTechTreeBranchCompactor() {
    }

    static Result compact(
            ResearchTechTreeProjection projection,
            ResearchTreeLayout kernel,
            Map<ResourceLocation, Integer> visualRankByNode,
            Map<ResourceLocation, Integer> visualOrderByNode,
            ResearchTechTreeLayoutPolicy policy) {
        if (projection == null || kernel == null || visualRankByNode == null
                || visualOrderByNode == null || policy == null
                || !visualRankByNode.keySet().equals(visualOrderByNode.keySet())) {
            throw new IllegalArgumentException(
                    "Research Tech Tree branch compaction inputs cannot be null");
        }
        Map<ResourceLocation, Integer> semanticRankIndex = semanticRankIndexes(projection);
        BranchFamilies branches = branchFamilies(projection);
        TopologyPressure pressure = TopologyPressure.measure(
                projection, kernel, semanticRankIndex);
        TreeMap<Integer, List<ResearchTreeLayout.PositionedNode>> nodesByVisualRank =
                new TreeMap<>();
        kernel.nodes().forEach(node -> nodesByVisualRank.computeIfAbsent(
                visualRankByNode.get(node.blueprintId()), ignored -> new ArrayList<>())
                .add(node));
        Comparator<ResearchTreeLayout.PositionedNode> horizontalOrder = Comparator
                .comparingInt((ResearchTreeLayout.PositionedNode node) ->
                        visualOrderByNode.get(node.blueprintId()))
                .thenComparing(node -> node.blueprintId().toString());
        nodesByVisualRank.values().forEach(nodes -> nodes.sort(horizontalOrder));

        int nodePitch = Math.addExact(ResearchTreeLayout.NODE_WIDTH, policy.nodeGap());
        Map<Integer, RowDraft> rows = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<ResearchTreeLayout.PositionedNode>> entry
                : nodesByVisualRank.entrySet()) {
            List<ResearchTreeLayout.PositionedNode> nodes = entry.getValue();
            int[] originalCenters = new int[nodes.size()];
            int[] compactedCenters = new int[nodes.size()];
            int[] branchGutters = branchGutters(
                    nodes,
                    branches,
                    semanticRankIndex,
                    pressure,
                    policy.nodeGap());
            for (int index = 0; index < nodes.size(); index++) {
                originalCenters[index] = nodes.get(index).centerX();
                if (index == 0) {
                    compactedCenters[index] = originalCenters[index];
                    continue;
                }
                int compactedStep = Math.addExact(nodePitch, branchGutters[index]);
                compactedCenters[index] = Math.addExact(
                        compactedCenters[index - 1], compactedStep);
            }
            int originalMinimum = java.util.Arrays.stream(originalCenters)
                    .min().orElseThrow();
            int originalMaximum = java.util.Arrays.stream(originalCenters)
                    .max().orElseThrow();
            int originalCenter = midpoint(originalMinimum, originalMaximum);
            int compactedCenter = midpoint(
                    compactedCenters[0], compactedCenters[compactedCenters.length - 1]);
            shift(compactedCenters, originalCenter - compactedCenter);
            rows.put(entry.getKey(), new RowDraft(nodes, originalCenters, compactedCenters));
        }

        RowDraft anchor = rows.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Integer, RowDraft>>comparingInt(
                                entry -> entry.getValue().size())
                        .reversed()
                        .thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
        int maximumOuterVoid = Math.multiplyExact(2, policy.nodeGap());
        for (RowDraft row : rows.values()) {
            int rowShift = 0;
            if (row.maximumRight() < anchor.minimumLeft() - maximumOuterVoid) {
                rowShift = anchor.minimumLeft() - maximumOuterVoid - row.maximumRight();
            } else if (row.minimumLeft() > anchor.maximumRight() + maximumOuterVoid) {
                rowShift = anchor.maximumRight() + maximumOuterVoid - row.minimumLeft();
            }
            row.shift(rowShift);
        }

        int minimumLeft = rows.values().stream()
                .mapToInt(RowDraft::minimumLeft)
                .min()
                .orElseThrow();
        int normalizationShift = Math.subtractExact(policy.canvasPadding(), minimumLeft);
        rows.values().forEach(row -> row.shift(normalizationShift));
        int maximumRight = rows.values().stream()
                .mapToInt(RowDraft::maximumRight)
                .max()
                .orElseThrow();
        int width = Math.addExact(maximumRight, policy.canvasPadding());
        if (width > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree branch compaction exceeds the logical canvas");
        }

        Map<ResourceLocation, Integer> xByNode = new LinkedHashMap<>();
        rows.values().forEach(row -> {
            for (int index = 0; index < row.nodes().size(); index++) {
                xByNode.put(
                        row.nodes().get(index).blueprintId(),
                        row.compactedCenters()[index] - ResearchTreeLayout.NODE_WIDTH / 2);
            }
        });
        return new Result(Map.copyOf(xByNode), Map.copyOf(rows), width);
    }

    private static Map<ResourceLocation, Integer> semanticRankIndexes(
            ResearchTechTreeProjection projection) {
        List<Integer> ranks = projection.graph().nodes().stream()
                .map(node -> projection.placement(node.blueprintId()).orElseThrow().rank())
                .distinct()
                .sorted()
                .toList();
        Map<Integer, Integer> indexByRank = new HashMap<>();
        for (int index = 0; index < ranks.size(); index++) {
            indexByRank.put(ranks.get(index), index);
        }
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        projection.graph().nodes().forEach(node -> result.put(
                node.blueprintId(),
                indexByRank.get(projection.placement(node.blueprintId()).orElseThrow().rank())));
        return Map.copyOf(result);
    }

    private static BranchFamilies branchFamilies(
            ResearchTechTreeProjection projection) {
        Map<ResourceLocation, Integer> familyByNode = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> rankIndexByNode = new LinkedHashMap<>();
        Integer familyStart = null;
        Integer transitionEnd = null;
        for (ResearchTechTreeProjection.Placement placement
                : projection.placements().values()) {
            if (placement.automaticBranch().isEmpty()) {
                continue;
            }
            var branch = placement.automaticBranch().orElseThrow();
            if (familyStart != null && (familyStart != branch.familyStartIndex()
                    || transitionEnd != branch.transitionEndIndex())) {
                return BranchFamilies.NONE;
            }
            familyStart = branch.familyStartIndex();
            transitionEnd = branch.transitionEndIndex();
            familyByNode.put(placement.nodeId(), branch.branchIndex());
            rankIndexByNode.put(placement.nodeId(), branch.rankIndex());
        }
        if (familyStart == null
                || familyByNode.values().stream().distinct().count() < 2L) {
            return BranchFamilies.NONE;
        }
        return new BranchFamilies(
                Map.copyOf(familyByNode),
                Map.copyOf(rankIndexByNode),
                familyStart,
                rankIndexByNode.values().stream()
                        .mapToInt(Integer::intValue).max().orElse(familyStart));
    }

    /**
     * Assigns one gutter to every transition between known automatic families.
     * Authored nodes do not carry canonical automatic-branch metadata, so a run
     * of authored nodes is treated as a neutral bridge and the gutter is placed
     * once near the middle of that run instead of disappearing or being doubled.
     */
    private static int[] branchGutters(
            List<ResearchTreeLayout.PositionedNode> nodes,
            BranchFamilies branches,
            Map<ResourceLocation, Integer> semanticRankIndex,
            TopologyPressure pressure,
            int nodeGap) {
        int[] result = new int[nodes.size()];
        int previousKnownIndex = -1;
        for (int index = 0; index < nodes.size(); index++) {
            ResourceLocation nodeId = nodes.get(index).blueprintId();
            Integer family = branches.familyByNode().get(nodeId);
            if (family == null) {
                continue;
            }
            if (previousKnownIndex >= 0) {
                ResourceLocation previousId = nodes.get(previousKnownIndex).blueprintId();
                Integer previousFamily = branches.familyByNode().get(previousId);
                if (!family.equals(previousFamily)) {
                    int boundaryIndex = Math.floorDiv(
                            Math.addExact(previousKnownIndex, index + 1), 2);
                    result[boundaryIndex] = Math.max(
                            result[boundaryIndex],
                            branchGutter(
                                    previousId,
                                    nodeId,
                                    branches,
                                    semanticRankIndex,
                                    pressure,
                                    nodeGap));
                }
            }
            previousKnownIndex = index;
        }
        return result;
    }

    private static int branchGutter(
            ResourceLocation left,
            ResourceLocation right,
            BranchFamilies branches,
            Map<ResourceLocation, Integer> semanticRankIndex,
            TopologyPressure pressure,
            int nodeGap) {
        Integer leftFamily = branches.familyByNode().get(left);
        Integer rightFamily = branches.familyByNode().get(right);
        if (leftFamily == null || rightFamily == null || leftFamily.equals(rightFamily)) {
            return 0;
        }
        int automaticRank = Math.min(
                branches.rankIndexByNode().get(left),
                branches.rankIndexByNode().get(right));
        if (automaticRank < branches.familyStartIndex()) {
            return 0;
        }
        int progress = automaticRank - branches.familyStartIndex();
        int span = Math.max(
                1, branches.maximumRankIndex() - branches.familyStartIndex());
        int semanticRank = Math.min(
                semanticRankIndex.get(left), semanticRankIndex.get(right));
        RankPressure rankPressure = pressure.at(semanticRank);
        int initial = evidenceAdjustedInitialGutter(
                nodeGap,
                rankPressure.maximumFanOut(),
                rankPressure.hasCrossing(),
                rankPressure.maximumAlternativeCount());
        int mature = Math.addExact(
                nodeGap, Math.floorDiv(ResearchTreeLayout.NODE_WIDTH, 2));
        return Math.addExact(
                initial,
                Math.floorDiv(
                        Math.multiplyExact(mature - initial, progress), span));
    }

    static int evidenceAdjustedInitialGutter(
            int nodeGap,
            int maximumFanOut,
            boolean hasCrossing) {
        return evidenceAdjustedInitialGutter(
                nodeGap, maximumFanOut, hasCrossing, 1);
    }

    static int evidenceAdjustedInitialGutter(
            int nodeGap,
            int maximumFanOut,
            boolean hasCrossing,
            int maximumAlternativeCount) {
        if (nodeGap < 0 || maximumFanOut < 0 || maximumAlternativeCount < 0) {
            throw new IllegalArgumentException(
                    "Research Tech Tree branch pressure cannot be negative");
        }
        int result = divideRoundUp(Math.multiplyExact(nodeGap, 3), 4);
        if (maximumFanOut >= 3) {
            result = Math.addExact(result, divideRoundUp(nodeGap, 8));
        }
        if (hasCrossing) {
            result = Math.addExact(result, divideRoundUp(nodeGap, 8));
        }
        if (maximumAlternativeCount >= 2) {
            result = Math.addExact(result, divideRoundUp(nodeGap, 6));
        }
        return Math.min(nodeGap, result);
    }

    private static int midpoint(int left, int right) {
        return Math.toIntExact(Math.floorDiv((long) left + right, 2L));
    }

    private static void shift(int[] values, int amount) {
        if (amount == 0) {
            return;
        }
        for (int index = 0; index < values.length; index++) {
            values[index] = Math.addExact(values[index], amount);
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    static final class Result {
        private final Map<ResourceLocation, Integer> xByNode;
        private final Map<Integer, RowDraft> rows;
        private final int width;

        private Result(
                Map<ResourceLocation, Integer> xByNode,
                Map<Integer, RowDraft> rows,
                int width) {
            this.xByNode = xByNode;
            this.rows = rows;
            this.width = width;
        }

        int x(ResourceLocation nodeId) {
            Integer value = xByNode.get(nodeId);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree branch compaction omitted a node");
            }
            return value;
        }

        int waypointX(int visualRank, int originalX) {
            RowDraft row = rows.get(visualRank);
            if (row == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree branch route leaves its visual ranks");
            }
            return Math.max(0, Math.min(width - 1, row.transformX(originalX)));
        }

        int width() {
            return width;
        }
    }

    private record BranchFamilies(
            Map<ResourceLocation, Integer> familyByNode,
            Map<ResourceLocation, Integer> rankIndexByNode,
            int familyStartIndex,
            int maximumRankIndex) {
        private static final BranchFamilies NONE =
                new BranchFamilies(
                        Map.of(), Map.of(), Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private record RankPressure(
            int maximumFanOut,
            boolean hasCrossing,
            int maximumAlternativeCount) {
        private static final RankPressure NONE = new RankPressure(0, false, 0);

        private RankPressure {
            if (maximumFanOut < 0 || maximumAlternativeCount < 0) {
                throw new IllegalArgumentException(
                        "Research Tech Tree rank pressure cannot be negative");
            }
        }
    }

    private record TopologyPressure(Map<Integer, RankPressure> byRank) {
        private TopologyPressure {
            byRank = Map.copyOf(byRank);
        }

        private RankPressure at(int rank) {
            return byRank.getOrDefault(rank, RankPressure.NONE);
        }

        private static TopologyPressure measure(
                ResearchTechTreeProjection projection,
                ResearchTreeLayout kernel,
                Map<ResourceLocation, Integer> semanticRankIndex) {
            Map<Integer, Map<ResourceLocation, Integer>> fanOutByRank =
                    new LinkedHashMap<>();
            Map<Integer, List<ResearchTreeGraph.Edge>> edgesByRank = new LinkedHashMap<>();
            Map<Integer, Integer> alternativesByRank = new LinkedHashMap<>();
            projection.graph().edges().forEach(edge -> {
                int rank = semanticRankIndex.get(edge.dependentId());
                fanOutByRank.computeIfAbsent(rank, ignored -> new LinkedHashMap<>())
                        .merge(edge.prerequisiteId(), 1, Math::addExact);
                edgesByRank.computeIfAbsent(rank, ignored -> new ArrayList<>()).add(edge);
            });
            projection.graph().requirementGroups().forEach(group -> {
                int rank = semanticRankIndex.get(group.dependentId());
                int alternatives = group.visibleAlternativeIds().size()
                        + group.hiddenAlternativeCount()
                        + group.externalAlternativeCount();
                alternativesByRank.merge(rank, alternatives, Math::max);
            });
            Map<Integer, RankPressure> result = new LinkedHashMap<>();
            edgesByRank.forEach((rank, edges) -> result.put(
                    rank,
                    new RankPressure(
                            fanOutByRank.get(rank).values().stream()
                                    .mapToInt(Integer::intValue).max().orElse(0),
                            hasCrossing(edges, kernel),
                            alternativesByRank.getOrDefault(rank, 0))));
            return new TopologyPressure(result);
        }

        private static boolean hasCrossing(
                List<ResearchTreeGraph.Edge> edges,
                ResearchTreeLayout kernel) {
            List<ResearchTreeGraph.Edge> ordered = edges.stream()
                    .sorted(Comparator
                            .comparingInt((ResearchTreeGraph.Edge edge) -> kernel.position(
                                            edge.prerequisiteId()).orElseThrow().centerX())
                            .thenComparing(edge -> edge.prerequisiteId().toString())
                            .thenComparingInt(edge -> kernel.position(
                                    edge.dependentId()).orElseThrow().centerX())
                            .thenComparing(edge -> edge.dependentId().toString()))
                    .toList();
            int previousMaximumTarget = Integer.MIN_VALUE;
            int cursor = 0;
            while (cursor < ordered.size()) {
                ResourceLocation prerequisite = ordered.get(cursor).prerequisiteId();
                int groupMinimumTarget = Integer.MAX_VALUE;
                int groupMaximumTarget = Integer.MIN_VALUE;
                while (cursor < ordered.size()
                        && ordered.get(cursor).prerequisiteId().equals(prerequisite)) {
                    int target = kernel.position(ordered.get(cursor).dependentId())
                            .orElseThrow().centerX();
                    groupMinimumTarget = Math.min(groupMinimumTarget, target);
                    groupMaximumTarget = Math.max(groupMaximumTarget, target);
                    cursor++;
                }
                if (previousMaximumTarget != Integer.MIN_VALUE
                        && groupMinimumTarget < previousMaximumTarget) {
                    return true;
                }
                previousMaximumTarget = Math.max(
                        previousMaximumTarget, groupMaximumTarget);
            }
            return false;
        }
    }

    private static final class RowDraft {
        private final List<ResearchTreeLayout.PositionedNode> nodes;
        private final int[] originalCenters;
        private final int[] compactedCenters;

        private RowDraft(
                List<ResearchTreeLayout.PositionedNode> nodes,
                int[] originalCenters,
                int[] compactedCenters) {
            this.nodes = List.copyOf(nodes);
            this.originalCenters = originalCenters;
            this.compactedCenters = compactedCenters;
        }

        private List<ResearchTreeLayout.PositionedNode> nodes() {
            return nodes;
        }

        private int[] compactedCenters() {
            return compactedCenters;
        }

        private int size() {
            return nodes.size();
        }

        private int minimumLeft() {
            return compactedCenters[0] - ResearchTreeLayout.NODE_WIDTH / 2;
        }

        private int maximumRight() {
            return compactedCenters[compactedCenters.length - 1]
                    + ResearchTreeLayout.NODE_WIDTH / 2;
        }

        private void shift(int amount) {
            ResearchTechTreeBranchCompactor.shift(compactedCenters, amount);
        }

        private int transformX(int originalX) {
            int closest = 0;
            int closestDistance = Math.abs(originalX - originalCenters[0]);
            for (int index = 1; index < originalCenters.length; index++) {
                int distance = Math.abs(originalX - originalCenters[index]);
                if (distance < closestDistance) {
                    closest = index;
                    closestDistance = distance;
                }
            }
            return Math.addExact(
                    compactedCenters[closest],
                    Math.subtractExact(originalX, originalCenters[closest]));
        }
    }
}
