package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayeredLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutInput;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic prerequisite-driven single-canvas layout for Tech Tree domains.
 * Authored lanes influence stable tie-breaking only; they never create visible
 * columns, containment regions, or prerequisite authority.
 */
public final class ResearchTechTreeLayoutEngine {
    private ResearchTechTreeLayoutEngine() {
    }

    public static ResearchTechTreeLayoutCatalog layoutCatalog(
            ResearchTechTreeProjectionCatalog projections,
            ResearchTechTreeLayoutPolicy policy) {
        return layoutCatalog(projections, policy, Integer.MAX_VALUE);
    }

    /** Builds layouts with a presentation-only capacity suitable for a viewport. */
    public static ResearchTechTreeLayoutCatalog layoutCatalog(
            ResearchTechTreeProjectionCatalog projections,
            ResearchTechTreeLayoutPolicy policy,
            int viewportWidth) {
        if (projections == null || policy == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout inputs cannot be null");
        }
        if (!projections.available()) {
            return ResearchTechTreeLayoutCatalog.EMPTY;
        }
        List<ResearchTechTreeLayout> layouts = projections.projections().stream()
                .map(projection -> layout(projection, policy, viewportWidth))
                .toList();
        return new ResearchTechTreeLayoutCatalog(projections, layouts);
    }

    public static ResearchTechTreeLayout layout(
            ResearchTechTreeProjection projection,
            ResearchTechTreeLayoutPolicy policy) {
        return layout(projection, policy, Integer.MAX_VALUE);
    }

    /**
     * Lays out one domain without ever exceeding its resolved tree-owned row maximum.
     * An unusually narrow viewport may request additional visual-only wrapping.
     */
    public static ResearchTechTreeLayout layout(
            ResearchTechTreeProjection projection,
            ResearchTechTreeLayoutPolicy policy,
            int viewportWidth) {
        if (projection == null || policy == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain layout inputs cannot be null");
        }
        if (projection.graph().nodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain layout cannot be empty");
        }
        if (viewportWidth < 1) {
            throw new IllegalArgumentException(
                    "Research Tech Tree viewport width must be positive");
        }

        validateEdgeOrder(projection);
        int rowCapacity = policy.effectiveNodesPerRow(
                projection.maxNodesPerLayer(), viewportWidth);
        VisualRows visualRows = visualRows(projection, policy, rowCapacity);
        ResearchTreeLayout kernel = ResearchTreeLayeredLayoutEngine.layoutInput(
                layoutInput(projection, visualRows), kernelPolicy(policy));
        ResearchTechTreeBranchCompactor.Result horizontal =
                ResearchTechTreeBranchCompactor.compact(
                        projection,
                        kernel,
                        visualRows.rankByNode(),
                        visualRows.orderByNode(),
                        policy);
        VerticalGeometry geometry = verticalGeometry(projection, visualRows, policy);
        ResearchTreeLayout graphLayout = remapVerticalGeometry(
                projection,
                kernel,
                horizontal,
                visualRows,
                geometry,
                componentIndexes(projection));
        List<ResearchTechTreeLayout.BoundaryPortal> portals = portals(
                projection, graphLayout, policy);
        return new ResearchTechTreeLayout(
                projection.domain(),
                graphLayout,
                geometry.tiers(),
                portals,
                geometry.bands());
    }

    private static ResearchTreeLayoutInput layoutInput(
            ResearchTechTreeProjection projection,
            VisualRows visualRows) {
        List<ResearchTreeLayoutInput.Node> nodes = projection.graph().nodes().stream()
                .map(node -> new ResearchTreeLayoutInput.Node(
                        node.ordinal(),
                        node.blueprintId(),
                        visualRows.rankByNode().get(node.blueprintId()),
                        0,
                        visualRows.orderByNode().get(node.blueprintId()),
                        0))
                .toList();
        List<ResearchTreeLayoutInput.Edge> edges = projection.graph().edges().stream()
                .map(edge -> new ResearchTreeLayoutInput.Edge(
                        edge.prerequisiteId(), edge.dependentId()))
                .toList();
        return new ResearchTreeLayoutInput(nodes, edges);
    }

    /**
     * The shared kernel owns horizontal ordering and compaction. Tech Tree
     * portals are narrower than a node, so ordinary canvas padding is enough.
     * A one-node normalization capacity suppresses virtual long-edge vertices;
     * the obstacle-aware edge router can route those edges without widening
     * ranks that the edge merely passes through.
     */
    private static ResearchTreeLayoutPolicy kernelPolicy(
            ResearchTechTreeLayoutPolicy policy) {
        return new ResearchTreeLayoutPolicy(
                policy.canvasPadding(),
                policy.nodeGap(),
                policy.tierGap(),
                policy.nodeGap(),
                policy.nodeGap(),
                policy.nodeGap(),
                0,
                0,
                policy.portalPadding(),
                ResearchTreeLayout.NODE_WIDTH,
                policy.orderingSweeps(),
                policy.compactionSweeps());
    }

    private static ResearchTreeLayout remapVerticalGeometry(
            ResearchTechTreeProjection projection,
            ResearchTreeLayout kernel,
            ResearchTechTreeBranchCompactor.Result horizontal,
            VisualRows visualRows,
            VerticalGeometry geometry,
            Map<ResourceLocation, Integer> componentByNode) {
        ResearchTreeLayout.PositionedNode[] positioned =
                new ResearchTreeLayout.PositionedNode[projection.graph().nodes().size()];
        for (ResearchTreeGraph.Node node : projection.graph().nodes()) {
            ResourceLocation nodeId = node.blueprintId();
            ResearchTreeLayout.PositionedNode kernelNode = kernel.position(nodeId).orElseThrow();
            int visualRank = visualRows.rankByNode().get(nodeId);
            positioned[node.ordinal()] = new ResearchTreeLayout.PositionedNode(
                    node.ordinal(),
                    nodeId,
                    componentByNode.get(nodeId),
                    visualRank,
                    visualRows.orderByNode().get(nodeId),
                    horizontal.x(nodeId),
                    geometry.yByVisualRank()[visualRank]);
        }

        List<ResearchTreeLayout.EdgeRouteHint> routeHints = kernel.edgeRouteHints().stream()
                .map(hint -> new ResearchTreeLayout.EdgeRouteHint(
                        hint.prerequisiteId(),
                        hint.dependentId(),
                        hint.waypoints().stream()
                                .map(waypoint -> {
                                    VisualRow row = visualRows.rowByRank().get(waypoint.rank());
                                    if (row == null) {
                                        throw new IllegalArgumentException(
                                                "Research Tech Tree route leaves its visual ranks");
                                    }
                                    return new ResearchTreeLayout.RouteWaypoint(
                                            waypoint.rank(),
                                            horizontal.waypointX(
                                                    waypoint.rank(), waypoint.x()),
                                            geometry.yByVisualRank()[waypoint.rank()]
                                                    + ResearchTreeLayout.NODE_HEIGHT / 2);
                                })
                                .toList()))
                .toList();
        return new ResearchTreeLayout(
                horizontal.width(),
                geometry.canvasHeight(),
                visualRows.rowByRank().size(),
                List.of(positioned),
                List.of(),
                List.of(),
                List.of(),
                routeHints);
    }

    /**
     * Compresses sparse semantic ranks and expands crowded ranks into balanced
     * client-only rows. Neither operation changes prerequisite authority.
     * The row index never leaves this layout adapter and therefore cannot become
     * research authority or manufacture a prerequisite.
     */
    private static VisualRows visualRows(
            ResearchTechTreeProjection projection,
            ResearchTechTreeLayoutPolicy policy,
            int rowCapacity) {
        int nextRank = 0;
        Map<Integer, VisualRow> rowByRank = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> rankByNode = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> orderByNode = new LinkedHashMap<>();
        Comparator<ResourceLocation> stableOrder = stableNodeOrder(projection);
        TreeMap<Integer, List<ResourceLocation>> nodesBySemanticRank = new TreeMap<>();
        projection.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .forEach(nodeId -> nodesBySemanticRank.computeIfAbsent(
                        projection.placement(nodeId).orElseThrow().rank(),
                        ignored -> new ArrayList<>()).add(nodeId));
        nodesBySemanticRank.values().forEach(nodes -> nodes.sort(stableOrder));
        orderSemanticRanks(
                projection,
                nodesBySemanticRank,
                stableOrder,
                policy.orderingSweeps());
        AutomaticFamilyOrder familyOrder = AutomaticFamilyOrder.from(projection);
        List<Integer> semanticRanks = List.copyOf(nodesBySemanticRank.keySet());
        for (int semanticRank : semanticRanks) {
            List<ResourceLocation> nodes = nodesBySemanticRank.get(semanticRank);
            OptionalLegacyTier legacyTier = commonLegacyTier(projection, nodes);
            java.util.Optional<ResourceLocation> bandId = commonBandId(projection, nodes);
            ResearchTechTreeVisualMotifPlanner.Plan motifPlan =
                    ResearchTechTreeVisualMotifPlanner.partition(
                            nodes, rowCapacity, familyOrder::matureFamily);
            for (int wrapRow = 0; wrapRow < motifPlan.rows().size(); wrapRow++) {
                int rank = nextRank++;
                rowByRank.put(rank,
                        new VisualRow(semanticRank, wrapRow, legacyTier, bandId));
                List<ResourceLocation> row = motifPlan.rows().get(wrapRow);
                for (int index = 0; index < row.size(); index++) {
                    ResourceLocation nodeId = row.get(index);
                    rankByNode.put(nodeId, rank);
                    orderByNode.put(nodeId, index);
                }
            }
        }
        if (nextRank < 1 || nextRank > projection.graph().nodes().size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree visual ranks exceed their node count");
        }
        if (rankByNode.size() != projection.graph().nodes().size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree visual rows omit projected nodes");
        }
        return new VisualRows(
                Map.copyOf(rankByNode),
                Map.copyOf(orderByNode),
                Map.copyOf(rowByRank));
    }

    private static OptionalLegacyTier commonLegacyTier(
            ResearchTechTreeProjection projection,
            List<ResourceLocation> nodes) {
        Tier common = null;
        for (ResourceLocation nodeId : nodes) {
            var tier = projection.placement(nodeId).orElseThrow().legacyTier();
            if (tier.isEmpty() || common != null && common != tier.orElseThrow()) {
                return OptionalLegacyTier.NONE;
            }
            common = tier.orElseThrow();
        }
        return new OptionalLegacyTier(common);
    }

    private static java.util.Optional<ResourceLocation> commonBandId(
            ResearchTechTreeProjection projection,
            List<ResourceLocation> nodes) {
        ResourceLocation common = null;
        for (ResourceLocation nodeId : nodes) {
            var bandId = projection.placement(nodeId).orElseThrow().bandId();
            if (bandId.isEmpty()
                    || common != null && !common.equals(bandId.orElseThrow())) {
                return java.util.Optional.empty();
            }
            common = bandId.orElseThrow();
        }
        return java.util.Optional.ofNullable(common);
    }

    private static Comparator<ResourceLocation> stableNodeOrder(
            ResearchTechTreeProjection projection) {
        return Comparator
                .comparingInt((ResourceLocation nodeId) -> projection
                        .placement(nodeId).orElseThrow().laneOrder())
                .thenComparingLong(nodeId -> projection
                        .placement(nodeId).orElseThrow().siblingOrder())
                .thenComparing(ResourceLocation::toString);
    }

    /**
     * Performs branch-aware ordering before a crowded semantic rank is split.
     * The shared kernel repeats the same barycentric idea inside each resulting
     * row, while this pass decides which branches should remain row-neighbors.
     */
    private static void orderSemanticRanks(
            ResearchTechTreeProjection projection,
            TreeMap<Integer, List<ResourceLocation>> nodesByRank,
            Comparator<ResourceLocation> stableOrder,
            int sweeps) {
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new LinkedHashMap<>();
        projection.graph().nodes().forEach(node -> {
            prerequisites.put(node.blueprintId(), new ArrayList<>());
            dependents.put(node.blueprintId(), new ArrayList<>());
        });
        projection.graph().edges().forEach(edge -> {
            prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
            dependents.get(edge.prerequisiteId()).add(edge.dependentId());
        });
        prerequisites.values().forEach(values -> values.sort(stableOrder));
        dependents.values().forEach(values -> values.sort(stableOrder));
        AutomaticFamilyOrder familyOrder = AutomaticFamilyOrder.from(projection);

        /*
         * Family cohesion is a presentation invariant, not an optimization
         * sweep. Apply it once even for a single-rank tree or a policy with
         * zero ordering sweeps; later barycentric passes re-apply it after
         * every rank sort.
         */
        nodesByRank.values().forEach(nodes ->
                cohereAutomaticFamilies(nodes, familyOrder, stableOrder));

        List<Integer> ranks = List.copyOf(nodesByRank.keySet());
        for (int sweep = 0; sweep < sweeps; sweep++) {
            Map<ResourceLocation, Integer> order = semanticOrderIndex(nodesByRank);
            for (int rankIndex = 1; rankIndex < ranks.size(); rankIndex++) {
                sortSemanticRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        prerequisites,
                        order,
                        stableOrder);
                cohereAutomaticFamilies(
                        nodesByRank.get(ranks.get(rankIndex)), familyOrder, stableOrder);
                updateSemanticOrder(nodesByRank.get(ranks.get(rankIndex)), order);
            }
            order = semanticOrderIndex(nodesByRank);
            for (int rankIndex = ranks.size() - 2; rankIndex >= 0; rankIndex--) {
                sortSemanticRank(
                        nodesByRank.get(ranks.get(rankIndex)),
                        dependents,
                        order,
                        stableOrder);
                cohereAutomaticFamilies(
                        nodesByRank.get(ranks.get(rankIndex)), familyOrder, stableOrder);
                updateSemanticOrder(nodesByRank.get(ranks.get(rankIndex)), order);
            }
        }
    }

    /**
     * Keeps every mature automatic family in one horizontal run while retaining
     * barycentric order both between family blocks and within each family. The
     * shared trunk remains unrestricted, and authored nodes stay neutral blocks
     * that may sit between—not split—automatic families.
     */
    private static void cohereAutomaticFamilies(
            List<ResourceLocation> nodes,
            AutomaticFamilyOrder families,
            Comparator<ResourceLocation> stableOrder) {
        Map<Integer, List<ResourceLocation>> membersByFamily = new LinkedHashMap<>();
        for (ResourceLocation nodeId : nodes) {
            families.matureFamily(nodeId).ifPresent(family ->
                    membersByFamily.computeIfAbsent(
                            family, ignored -> new ArrayList<>()).add(nodeId));
        }
        if (membersByFamily.isEmpty()) {
            return;
        }

        Map<ResourceLocation, Integer> currentIndex = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            currentIndex.put(nodes.get(index), index);
        }
        List<SemanticBlock> blocks = new ArrayList<>();
        membersByFamily.values().forEach(members -> blocks.add(
                SemanticBlock.of(members, currentIndex, stableOrder)));
        nodes.stream()
                .filter(nodeId -> families.matureFamily(nodeId).isEmpty())
                .forEach(nodeId -> blocks.add(
                        SemanticBlock.of(List.of(nodeId), currentIndex, stableOrder)));
        blocks.sort(Comparator
                .comparingDouble(SemanticBlock::barycenter)
                .thenComparing(SemanticBlock::stableFirst, stableOrder));
        nodes.clear();
        blocks.forEach(block -> nodes.addAll(block.members()));
    }

    private static void sortSemanticRank(
            List<ResourceLocation> nodes,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Integer> order,
            Comparator<ResourceLocation> stableOrder) {
        Map<ResourceLocation, Double> scores = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            ResourceLocation nodeId = nodes.get(index);
            scores.put(nodeId, semanticBarycenter(nodeId, neighbors, order)
                    .orElse((double) index));
        }
        nodes.sort(Comparator
                .comparingDouble((ResourceLocation nodeId) -> scores.get(nodeId))
                .thenComparing(stableOrder));
    }

    private static OptionalDouble semanticBarycenter(
            ResourceLocation nodeId,
            Map<ResourceLocation, List<ResourceLocation>> neighbors,
            Map<ResourceLocation, Integer> order) {
        return neighbors.getOrDefault(nodeId, List.of()).stream()
                .map(order::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
    }

    private record SemanticBlock(
            List<ResourceLocation> members,
            double barycenter,
            ResourceLocation stableFirst) {
        private static SemanticBlock of(
                List<ResourceLocation> members,
                Map<ResourceLocation, Integer> currentIndex,
                Comparator<ResourceLocation> stableOrder) {
            List<ResourceLocation> copy = List.copyOf(members);
            return new SemanticBlock(
                    copy,
                    copy.stream().mapToInt(currentIndex::get).average().orElseThrow(),
                    copy.stream().min(stableOrder).orElseThrow());
        }
    }

    private record AutomaticFamilyOrder(
            Map<ResourceLocation, Integer> familyByNode,
            Map<ResourceLocation, Integer> rankIndexByNode,
            int familyStartIndex) {
        private static final AutomaticFamilyOrder NONE = new AutomaticFamilyOrder(
                Map.of(), Map.of(), Integer.MAX_VALUE);

        private Optional<Integer> matureFamily(ResourceLocation nodeId) {
            Integer family = familyByNode.get(nodeId);
            Integer rankIndex = rankIndexByNode.get(nodeId);
            return family == null || rankIndex == null || rankIndex < familyStartIndex
                    ? Optional.empty()
                    : Optional.of(family);
        }

        private static AutomaticFamilyOrder from(
                ResearchTechTreeProjection projection) {
            Map<ResourceLocation, Integer> families = new LinkedHashMap<>();
            Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
            Integer familyStart = null;
            Integer transitionEnd = null;
            for (ResearchTechTreeProjection.Placement placement
                    : projection.placements().values()) {
                if (placement.automaticBranch().isEmpty()) {
                    continue;
                }
                var branch = placement.automaticBranch().orElseThrow();
                if (familyStart != null
                        && (familyStart != branch.familyStartIndex()
                                || transitionEnd != branch.transitionEndIndex())) {
                    return NONE;
                }
                familyStart = branch.familyStartIndex();
                transitionEnd = branch.transitionEndIndex();
                families.put(placement.nodeId(), branch.branchIndex());
                ranks.put(placement.nodeId(), branch.rankIndex());
            }
            return familyStart == null
                    ? NONE
                    : new AutomaticFamilyOrder(
                            Map.copyOf(families),
                            Map.copyOf(ranks),
                            familyStart);
        }
    }

    private static Map<ResourceLocation, Integer> semanticOrderIndex(
            TreeMap<Integer, List<ResourceLocation>> nodesByRank) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        nodesByRank.values().forEach(nodes -> updateSemanticOrder(nodes, result));
        return result;
    }

    private static void updateSemanticOrder(
            List<ResourceLocation> nodes,
            Map<ResourceLocation, Integer> order) {
        for (int index = 0; index < nodes.size(); index++) {
            order.put(nodes.get(index), index);
        }
    }

    /** Stable weak-component IDs are diagnostics only and never trigger packing. */
    private static Map<ResourceLocation, Integer> componentIndexes(
            ResearchTechTreeProjection projection) {
        Comparator<ResourceLocation> stableOrder = stableNodeOrder(projection);
        Map<ResourceLocation, List<ResourceLocation>> neighbors = new LinkedHashMap<>();
        projection.graph().nodes().forEach(node ->
                neighbors.put(node.blueprintId(), new ArrayList<>()));
        projection.graph().edges().forEach(edge -> {
            neighbors.get(edge.prerequisiteId()).add(edge.dependentId());
            neighbors.get(edge.dependentId()).add(edge.prerequisiteId());
        });
        neighbors.values().forEach(values -> values.sort(stableOrder));

        Set<ResourceLocation> remaining = new LinkedHashSet<>(
                projection.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .sorted(stableOrder)
                        .toList());
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        int component = 0;
        while (!remaining.isEmpty()) {
            ResourceLocation root = remaining.iterator().next();
            List<ResourceLocation> queue = new ArrayList<>();
            queue.add(root);
            remaining.remove(root);
            for (int cursor = 0; cursor < queue.size(); cursor++) {
                ResourceLocation nodeId = queue.get(cursor);
                result.put(nodeId, component);
                for (ResourceLocation neighbor : neighbors.get(nodeId)) {
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            component++;
        }
        return Map.copyOf(result);
    }

    private static void validateEdgeOrder(ResearchTechTreeProjection projection) {
        for (ResearchTreeGraph.Edge edge : projection.graph().edges()) {
            ResearchTechTreeProjection.Placement prerequisite = projection
                    .placement(edge.prerequisiteId()).orElseThrow();
            ResearchTechTreeProjection.Placement dependent = projection
                    .placement(edge.dependentId()).orElseThrow();
            if (!ResearchTechTreeContract.progressionTransitionAllowed(
                    prerequisite.position(), dependent.position())) {
                throw new IllegalArgumentException(
                        "Research Tech Tree layout contradicts prerequisite order");
            }
        }
    }

    private static VerticalGeometry verticalGeometry(
            ResearchTechTreeProjection projection,
            VisualRows visualRows,
            ResearchTechTreeLayoutPolicy policy) {
        if (legacyBandsAreCoherent(visualRows, projection)) {
            List<VerticalGroup> groups = Arrays.stream(Tier.values())
                    .map(tier -> new VerticalGroup(
                            tier,
                            null,
                            java.util.stream.IntStream.range(
                                            0, visualRows.rowByRank().size())
                                    .filter(rank -> visualRows.rowByRank().get(rank)
                                            .legacyTier().value() == tier)
                                    .boxed()
                                    .toList()))
                    .filter(group -> !group.visualRanks().isEmpty())
                    .toList();
            return arrangeVerticalGeometry(
                    projection, visualRows, policy, groups);
        }
        if (progressionBandsAreCoherent(visualRows, projection)) {
            List<VerticalGroup> groups = projection.bands().stream()
                    .map(band -> new VerticalGroup(
                            null,
                            band,
                            java.util.stream.IntStream.range(
                                            0, visualRows.rowByRank().size())
                                    .filter(rank -> visualRows.rowByRank().get(rank)
                                            .bandId().filter(band.id()::equals).isPresent())
                                    .boxed()
                                    .toList()))
                    .filter(group -> !group.visualRanks().isEmpty())
                    .toList();
            return arrangeVerticalGeometry(
                    projection, visualRows, policy, groups);
        }
        List<Integer> ranks = java.util.stream.IntStream.range(
                        0, visualRows.rowByRank().size())
                .boxed()
                .toList();
        return arrangeVerticalGeometry(
                projection,
                visualRows,
                policy,
                List.of(new VerticalGroup(null, null, ranks)));
    }

    /**
     * Places occupied rank groups from top to bottom. Clearance is attached to
     * the exact row and direction that owns a portal, so empty bands and rows
     * without cross-domain links consume no portal geometry.
     */
    private static VerticalGeometry arrangeVerticalGeometry(
            ResearchTechTreeProjection projection,
            VisualRows visualRows,
            ResearchTechTreeLayoutPolicy policy,
            List<VerticalGroup> groups) {
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree vertical groups cannot be empty");
        }
        boolean[] clearanceAbove = new boolean[visualRows.rowByRank().size()];
        boolean[] clearanceBelow = new boolean[visualRows.rowByRank().size()];
        int[] requirementJunctionClearanceBelow =
                requirementJunctionClearanceBelow(projection, visualRows);
        for (ResearchTechTreeProjection.BoundaryLink link : projection.boundaryLinks()) {
            int rank = visualRows.rankByNode().get(link.localNodeId());
            if (link.direction() == ResearchTechTreeProjection.Direction.UNLOCK) {
                clearanceAbove[rank] = true;
            } else {
                clearanceBelow[rank] = true;
            }
        }

        int[] yByVisualRank = new int[visualRows.rowByRank().size()];
        int[] yByGroup = new int[groups.size()];
        int[] heightByGroup = new int[groups.size()];
        int nextY = policy.canvasPadding();
        for (int groupIndex = groups.size() - 1; groupIndex >= 0; groupIndex--) {
            VerticalGroup group = groups.get(groupIndex);
            int groupY = nextY;
            for (int rowIndex = group.visualRanks().size() - 1;
                    rowIndex >= 0;
                    rowIndex--) {
                int visualRank = group.visualRanks().get(rowIndex);
                if (clearanceAbove[visualRank]) {
                    nextY = addExact(nextY, policy.portalClearance());
                }
                yByVisualRank[visualRank] = nextY;
                nextY = addExact(nextY, ResearchTreeLayout.NODE_HEIGHT);
                nextY = addExact(
                        nextY, requirementJunctionClearanceBelow[visualRank]);
                if (clearanceBelow[visualRank]) {
                    nextY = addExact(nextY, policy.portalClearance());
                }
                if (rowIndex > 0) {
                    nextY = addExact(nextY, policy.sameTierStepGap());
                }
            }
            yByGroup[groupIndex] = groupY;
            heightByGroup[groupIndex] = nextY - groupY;
            if (groupIndex > 0) {
                nextY = addExact(nextY, policy.tierGap());
            }
        }
        int canvasHeight = addExact(nextY, policy.canvasPadding());
        ensureDimension(canvasHeight);

        List<ResearchTechTreeLayout.TierBand> tiers = new ArrayList<>();
        List<ResearchTechTreeLayout.ProgressionBand> bands = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            VerticalGroup group = groups.get(groupIndex);
            if (group.legacyTier() != null) {
                tiers.add(new ResearchTechTreeLayout.TierBand(
                        group.legacyTier(),
                        yByGroup[groupIndex],
                        heightByGroup[groupIndex]));
            } else if (group.band() != null) {
                int publishedIndex = projection.bands().indexOf(group.band());
                bands.add(new ResearchTechTreeLayout.ProgressionBand(
                        group.band().id(),
                        publishedIndex,
                        yByGroup[groupIndex],
                        heightByGroup[groupIndex],
                        group.band().color(),
                        group.band().icon()));
            }
        }
        return new VerticalGeometry(
                yByVisualRank,
                List.copyOf(tiers),
                List.copyOf(bands),
                canvasHeight);
    }

    /**
     * Reserves only the additional clearance required when one dependent owns
     * several drawable any-of junctions. The ordinary row gap already carries
     * the first junction, so singleton and one-junction nodes add no height.
     */
    private static int[] requirementJunctionClearanceBelow(
            ResearchTechTreeProjection projection,
            VisualRows visualRows) {
        Map<ResourceLocation, Integer> drawableGroupsByDependent = new HashMap<>();
        projection.graph().requirementGroups().forEach(group -> {
            int alternativeCount = group.visibleAlternativeIds().size()
                    + group.hiddenAlternativeCount()
                    + group.externalAlternativeCount();
            if (alternativeCount > 1 && !group.visibleAlternativeIds().isEmpty()) {
                drawableGroupsByDependent.merge(
                        group.dependentId(), 1, Math::addExact);
            }
        });
        int[] result = new int[visualRows.rowByRank().size()];
        drawableGroupsByDependent.forEach((dependentId, groupCount) -> {
            int visualRank = visualRows.rankByNode().get(dependentId);
            result[visualRank] = Math.max(
                    result[visualRank],
                    ResearchTechTreeLayoutPolicy.requirementJunctionClearance(groupCount));
        });
        return result;
    }

    private static boolean progressionBandsAreCoherent(
            VisualRows visualRows,
            ResearchTechTreeProjection projection) {
        if (projection.bands().isEmpty()) {
            return false;
        }
        Map<ResourceLocation, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < projection.bands().size(); index++) {
            order.put(projection.bands().get(index).id(), index);
        }
        int previous = -1;
        for (int rank = 0; rank < visualRows.rowByRank().size(); rank++) {
            var bandId = visualRows.rowByRank().get(rank).bandId();
            Integer index = bandId.map(order::get).orElse(null);
            if (index == null || index < previous) {
                return false;
            }
            previous = index;
        }
        return true;
    }

    private static boolean legacyBandsAreCoherent(
            VisualRows visualRows,
            ResearchTechTreeProjection projection) {
        List<ResourceLocation> publishedIds = projection.bands().stream()
                .map(ResearchTechTreePresentation.BandLabel::id)
                .toList();
        List<ResourceLocation> legacyIds = Arrays.stream(Tier.values())
                .map(ResearchTechTreeContract::legacyBandId)
                .toList();
        if (!publishedIds.isEmpty() && !publishedIds.equals(legacyIds)) {
            return false;
        }
        int previousOrdinal = -1;
        for (int rank = 0; rank < visualRows.rowByRank().size(); rank++) {
            OptionalLegacyTier tier = visualRows.rowByRank().get(rank).legacyTier();
            if (!tier.present() || tier.value().ordinal() < previousOrdinal) {
                return false;
            }
            previousOrdinal = tier.value().ordinal();
        }
        return true;
    }

    private static List<ResearchTechTreeLayout.BoundaryPortal> portals(
            ResearchTechTreeProjection projection,
            ResearchTreeLayout graphLayout,
            ResearchTechTreeLayoutPolicy policy) {
        Map<PortalKey, List<ResearchTechTreeProjection.BoundaryLink>> grouped =
                new LinkedHashMap<>();
        for (ResearchTechTreeProjection.BoundaryLink link : projection.boundaryLinks()) {
            grouped.computeIfAbsent(new PortalKey(
                    link.localNodeId(), link.direction(), link.remoteDomain()),
                    ignored -> new ArrayList<>()).add(link);
        }
        Map<PortalBankKey, List<ResearchTechTreeLayout.PortalTarget>> banks =
                new LinkedHashMap<>();
        for (ResearchTreeGraph.Node node : projection.graph().nodes()) {
            for (ResearchTechTreeProjection.Direction direction
                    : ResearchTechTreeProjection.Direction.values()) {
                for (Domain remoteDomain : Domain.values()) {
                    List<ResearchTechTreeProjection.BoundaryLink> links = grouped.get(
                            new PortalKey(node.blueprintId(), direction, remoteDomain));
                    if (links != null) {
                        banks.computeIfAbsent(
                                new PortalBankKey(node.blueprintId(), direction),
                                ignored -> new ArrayList<>())
                                .add(new ResearchTechTreeLayout.PortalTarget(
                                        node.blueprintId(), remoteDomain, direction, links));
                    }
                }
            }
        }

        List<ResearchTechTreeLayout.BoundaryPortal> result = new ArrayList<>();
        for (Map.Entry<PortalBankKey, List<ResearchTechTreeLayout.PortalTarget>> entry
                : banks.entrySet()) {
            ResearchTreeLayout.PositionedNode local = graphLayout
                    .position(entry.getKey().localNodeId()).orElseThrow();
            List<ResearchTechTreeLayout.PortalTarget> targets = entry.getValue();
            int bankWidth = portalBankWidth(targets.size());
            int minimumX = policy.canvasPadding();
            int maximumX = graphLayout.width() - policy.canvasPadding() - bankWidth;
            if (maximumX < minimumX) {
                throw new IllegalArgumentException(
                        "Research Tech Tree portal bank exceeds its unified canvas");
            }
            int firstX = Math.max(minimumX,
                    Math.min(maximumX, local.centerX() - bankWidth / 2));
            for (int index = 0; index < targets.size(); index++) {
                int y = entry.getKey().direction()
                        == ResearchTechTreeProjection.Direction.UNLOCK
                                ? local.y() - ResearchTreeLayout.PORTAL_SIZE
                                        - ResearchTreeLayout.PORTAL_NODE_GAP
                                        - policy.portalPadding()
                                : local.y() + ResearchTreeLayout.NODE_HEIGHT
                                        + ResearchTreeLayout.PORTAL_NODE_GAP
                                        + policy.portalPadding();
                result.add(new ResearchTechTreeLayout.BoundaryPortal(
                        targets.get(index),
                        addExact(firstX, multiplyExact(index,
                                ResearchTreeLayout.PORTAL_SIZE
                                        + ResearchTreeLayout.PORTAL_GAP)),
                        y));
            }
        }
        return List.copyOf(result);
    }

    private static int portalBankWidth(int count) {
        if (count < 1 || count >= Domain.values().length) {
            throw new IllegalArgumentException(
                    "Research Tech Tree portal bank target count is invalid");
        }
        return addExact(
                multiplyExact(count, ResearchTreeLayout.PORTAL_SIZE),
                multiplyExact(count - 1, ResearchTreeLayout.PORTAL_GAP));
    }

    private static int addExact(int left, int right) {
        int value = Math.addExact(left, right);
        ensureDimension(value);
        return value;
    }

    private static int multiplyExact(int left, int right) {
        int value = Math.multiplyExact(left, right);
        ensureDimension(value);
        return value;
    }

    private static void ensureDimension(int value) {
        if (value < 0 || value > ResearchTreeLayout.MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout exceeds the logical canvas");
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private record VisualRow(
            int semanticRank,
            int wrapRow,
            OptionalLegacyTier legacyTier,
            java.util.Optional<ResourceLocation> bandId) {
    }

    private record OptionalLegacyTier(Tier value) {
        private static final OptionalLegacyTier NONE = new OptionalLegacyTier(null);

        private boolean present() {
            return value != null;
        }
    }

    private record VisualRows(
            Map<ResourceLocation, Integer> rankByNode,
            Map<ResourceLocation, Integer> orderByNode,
            Map<Integer, VisualRow> rowByRank) {
    }

    private record VerticalGroup(
            Tier legacyTier,
            ResearchTechTreePresentation.BandLabel band,
            List<Integer> visualRanks) {
        private VerticalGroup {
            if (visualRanks == null || legacyTier != null && band != null) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree vertical group");
            }
            visualRanks = List.copyOf(visualRanks);
        }
    }

    private record PortalKey(
            ResourceLocation localNodeId,
            ResearchTechTreeProjection.Direction direction,
            Domain remoteDomain) {
    }

    private record PortalBankKey(
            ResourceLocation localNodeId,
            ResearchTechTreeProjection.Direction direction) {
    }

    private record VerticalGeometry(
            int[] yByVisualRank,
            List<ResearchTechTreeLayout.TierBand> tiers,
            List<ResearchTechTreeLayout.ProgressionBand> bands,
            int canvasHeight) {
    }
}
