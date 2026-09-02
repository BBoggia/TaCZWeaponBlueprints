package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.network.BlueprintSyncLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;

import net.minecraft.resources.ResourceLocation;

/** Immutable logical-canvas placement for a {@link ResearchTreeGraph}. */
public final class ResearchTreeLayout {
    public static final int NODE_WIDTH = 24;
    public static final int NODE_HEIGHT = 24;
    /** Shared geometry contract used by composers and the client canvas. */
    public static final int PORTAL_SIZE = 9;
    public static final int PORTAL_GAP = 2;
    public static final int PORTAL_NODE_GAP = 5;
    public static final int PORTAL_BANK_SIDE_PADDING = 2;
    public static final int MAX_DIMENSION = 1_000_000;
    public static final ResearchTreeLayout EMPTY =
            new ResearchTreeLayout(0, 0, 0, List.of(), List.of(), List.of(), List.of());

    private final int width;
    private final int height;
    private final int tierCount;
    private final List<PositionedNode> nodes;
    private final List<HiddenAnchor> hiddenAnchors;
    private final List<CategoryLane> categoryLanes;
    private final List<GroupRegion> groupRegions;
    private final List<EdgeRouteHint> edgeRouteHints;
    private final Map<ResourceLocation, PositionedNode> positionsById;
    private final Map<Integer, List<PositionedNode>> nodesByTier;
    private final List<TierBounds> tierBounds;
    private final Map<ResourceLocation, HiddenAnchor> hiddenAnchorsById;
    private final Map<RouteKey, EdgeRouteHint> edgeRouteHintsByEdge;

    public ResearchTreeLayout(int width, int height, int tierCount, List<PositionedNode> nodes) {
        this(width, height, tierCount, nodes, List.of(), List.of(), List.of());
    }

    public ResearchTreeLayout(
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<HiddenAnchor> hiddenAnchors) {
        this(width, height, tierCount, nodes, hiddenAnchors, List.of(), List.of());
    }

    public ResearchTreeLayout(
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<HiddenAnchor> hiddenAnchors,
            List<CategoryLane> categoryLanes) {
        this(width, height, tierCount, nodes, hiddenAnchors, categoryLanes, List.of());
    }

    public ResearchTreeLayout(
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<HiddenAnchor> hiddenAnchors,
            List<CategoryLane> categoryLanes,
            List<GroupRegion> groupRegions) {
        this(width, height, tierCount, nodes, hiddenAnchors, categoryLanes, groupRegions, List.of());
    }

    public ResearchTreeLayout(
            int width,
            int height,
            int tierCount,
            List<PositionedNode> nodes,
            List<HiddenAnchor> hiddenAnchors,
            List<CategoryLane> categoryLanes,
            List<GroupRegion> groupRegions,
            List<EdgeRouteHint> edgeRouteHints) {
        if ((nodes != null && nodes.stream().anyMatch(Objects::isNull))
                || (hiddenAnchors != null && hiddenAnchors.stream().anyMatch(Objects::isNull))
                || (categoryLanes != null && categoryLanes.stream().anyMatch(Objects::isNull))
                || (groupRegions != null && groupRegions.stream().anyMatch(Objects::isNull))
                || (edgeRouteHints != null && edgeRouteHints.stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException("research tree layout cannot contain null entries");
        }
        this.width = width;
        this.height = height;
        this.tierCount = tierCount;
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.hiddenAnchors = hiddenAnchors == null ? List.of() : List.copyOf(hiddenAnchors);
        this.categoryLanes = categoryLanes == null ? List.of() : List.copyOf(categoryLanes);
        this.groupRegions = groupRegions == null ? List.of() : List.copyOf(groupRegions);
        this.edgeRouteHints = edgeRouteHints == null ? List.of() : List.copyOf(edgeRouteHints);
        validate();

        Map<ResourceLocation, PositionedNode> positionIndex = new LinkedHashMap<>();
        Map<Integer, List<PositionedNode>> tierIndex = new HashMap<>();
        for (PositionedNode node : this.nodes) {
            positionIndex.put(node.blueprintId(), node);
            tierIndex.computeIfAbsent(node.tier(), ignored -> new ArrayList<>()).add(node);
        }
        tierIndex.replaceAll((ignored, tierNodes) -> tierNodes.stream()
                .sorted(Comparator.comparingInt(PositionedNode::orderInTier)).toList());
        List<TierBounds> nextTierBounds = tierIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TierBounds(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(PositionedNode::y).min().orElseThrow(),
                        entry.getValue().stream()
                                .mapToInt(node -> node.y() + NODE_HEIGHT)
                                .max()
                                .orElseThrow()))
                .toList();
        Map<ResourceLocation, HiddenAnchor> anchorIndex = new LinkedHashMap<>();
        for (HiddenAnchor anchor : this.hiddenAnchors) {
            anchorIndex.put(anchor.dependentId(), anchor);
        }
        Map<RouteKey, EdgeRouteHint> routeHintIndex = new LinkedHashMap<>();
        for (EdgeRouteHint hint : this.edgeRouteHints) {
            routeHintIndex.put(new RouteKey(hint.prerequisiteId(), hint.dependentId()), hint);
        }
        positionsById = Map.copyOf(positionIndex);
        nodesByTier = Map.copyOf(tierIndex);
        tierBounds = nextTierBounds;
        hiddenAnchorsById = Map.copyOf(anchorIndex);
        edgeRouteHintsByEdge = Map.copyOf(routeHintIndex);
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

    public List<HiddenAnchor> hiddenAnchors() {
        return hiddenAnchors;
    }

    /** Disclosure-safe vertical bands used to visually group published item types. */
    public List<CategoryLane> categoryLanes() {
        return categoryLanes;
    }

    /** Disclosure-safe horizontal regions used by the grouped atlas. */
    public List<GroupRegion> groupRegions() {
        return groupRegions;
    }

    /** Layout-only corridors for authored edges that cross one or more ranks. */
    public List<EdgeRouteHint> edgeRouteHints() {
        return edgeRouteHints;
    }

    public Optional<PositionedNode> position(ResourceLocation blueprintId) {
        return blueprintId == null ? Optional.empty() : Optional.ofNullable(positionsById.get(blueprintId));
    }

    public List<PositionedNode> tier(int tier) {
        return tier < 0 || tier >= tierCount ? List.of() : nodesByTier.getOrDefault(tier, List.of());
    }

    /** Precomputed vertical extents for each populated tier. */
    public List<TierBounds> tierBounds() {
        return tierBounds;
    }

    public Optional<HiddenAnchor> hiddenAnchor(ResourceLocation dependentId) {
        return dependentId == null
                ? Optional.empty()
                : Optional.ofNullable(hiddenAnchorsById.get(dependentId));
    }

    public Optional<EdgeRouteHint> edgeRouteHint(
            ResourceLocation prerequisiteId,
            ResourceLocation dependentId) {
        if (prerequisiteId == null || dependentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(edgeRouteHintsByEdge.get(
                new RouteKey(prerequisiteId, dependentId)));
    }

    private void validate() {
        if (width < 0 || width > MAX_DIMENSION
                || height < 0 || height > MAX_DIMENSION
                || tierCount < 0 || tierCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || nodes.size() > ResearchTreeGraph.MAX_NODES
                || hiddenAnchors.size() > ResearchTreeGraph.MAX_NODES
                || categoryLanes.size() > ResearchTreeGraph.MAX_NODES
                || groupRegions.size() > ResearchTreeGraph.MAX_NODES
                || edgeRouteHints.size() > ResearchTreeGraph.MAX_EDGES) {
            throw new IllegalArgumentException("research tree layout bounds are invalid");
        }
        if (nodes.isEmpty()) {
            if (width != 0 || height != 0 || tierCount != 0
                    || !hiddenAnchors.isEmpty() || !categoryLanes.isEmpty()
                    || !groupRegions.isEmpty() || !edgeRouteHints.isEmpty()) {
                throw new IllegalArgumentException("empty research tree layout must have empty bounds");
            }
            return;
        }
        if (width < NODE_WIDTH || height < NODE_HEIGHT || tierCount == 0) {
            throw new IllegalArgumentException("non-empty research tree layout has empty bounds");
        }

        Set<ResourceLocation> ids = new HashSet<>();
        Set<Long> tierOrders = new HashSet<>();
        int maximumTier = -1;
        for (int index = 0; index < nodes.size(); index++) {
            PositionedNode node = nodes.get(index);
            if (node.nodeOrdinal() != index) {
                throw new IllegalArgumentException("positioned node ordinals must be contiguous");
            }
            if (node.blueprintId().toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || !ids.add(node.blueprintId())) {
                throw new IllegalArgumentException("research tree layout contains an invalid or duplicate node");
            }
            long tierOrder = ((long) node.tier() << 32) | Integer.toUnsignedLong(node.orderInTier());
            if (!tierOrders.add(tierOrder)) {
                throw new IllegalArgumentException("research tree layout contains a duplicate tier order");
            }
            if (node.x() > width - NODE_WIDTH || node.y() > height - NODE_HEIGHT) {
                throw new IllegalArgumentException("positioned node lies outside the layout bounds");
            }
            maximumTier = Math.max(maximumTier, node.tier());
        }
        if (tierCount != maximumTier + 1) {
            throw new IllegalArgumentException("research tree layout tier count is inconsistent");
        }

        Map<Long, List<PositionedNode>> spatialBuckets = new HashMap<>();
        for (PositionedNode node : nodes) {
            int minimumBucketX = node.x() / NODE_WIDTH;
            int maximumBucketX = (node.x() + NODE_WIDTH - 1) / NODE_WIDTH;
            int minimumBucketY = node.y() / NODE_HEIGHT;
            int maximumBucketY = (node.y() + NODE_HEIGHT - 1) / NODE_HEIGHT;
            Set<Integer> checkedOrdinals = new HashSet<>();
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
                    for (PositionedNode other : spatialBuckets.getOrDefault(
                            spatialBucketKey(bucketX, bucketY), List.of())) {
                        if (checkedOrdinals.add(other.nodeOrdinal())
                                && nodesOverlap(node, other)) {
                            throw new IllegalArgumentException(
                                    "research tree layout nodes overlap: "
                                            + describePosition(other)
                                            + " and "
                                            + describePosition(node));
                        }
                    }
                }
            }
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
                    spatialBuckets.computeIfAbsent(
                            spatialBucketKey(bucketX, bucketY), ignored -> new ArrayList<>()).add(node);
                }
            }
        }

        int[] minimumY = new int[tierCount];
        int[] maximumBottom = new int[tierCount];
        java.util.Arrays.fill(minimumY, Integer.MAX_VALUE);
        for (PositionedNode node : nodes) {
            minimumY[node.tier()] = Math.min(minimumY[node.tier()], node.y());
            maximumBottom[node.tier()] = Math.max(maximumBottom[node.tier()], node.y() + NODE_HEIGHT);
        }
        int previousBottom = 0;
        for (int tier = tierCount - 1; tier >= 0; tier--) {
            if (minimumY[tier] == Integer.MAX_VALUE) {
                continue;
            }
            if (minimumY[tier] < previousBottom) {
                throw new IllegalArgumentException("research tree layout tiers overlap or are out of order");
            }
            previousBottom = maximumBottom[tier];
        }

        Set<ResourceLocation> anchorIds = new HashSet<>();
        for (HiddenAnchor anchor : hiddenAnchors) {
            if (!ids.contains(anchor.dependentId()) || !anchorIds.add(anchor.dependentId())
                    || anchor.x() < 0 || anchor.x() >= width || anchor.y() < 0 || anchor.y() >= height) {
                throw new IllegalArgumentException("invalid hidden-prerequisite anchor");
            }
        }

        Set<String> laneKeys = new HashSet<>();
        int previousLaneRight = 0;
        for (CategoryLane lane : categoryLanes) {
            if (!laneKeys.add(lane.key()) || lane.x() < previousLaneRight
                    || lane.right() > width) {
                throw new IllegalArgumentException("invalid or overlapping research category lane");
            }
            previousLaneRight = lane.right();
        }

        Set<ResourceLocation> regionIds = new HashSet<>();
        int previousRegionRight = 0;
        for (GroupRegion region : groupRegions) {
            if (!regionIds.add(region.groupId())
                    || region.x() < previousRegionRight
                    || region.right() > width
                    || region.bottom() > height) {
                throw new IllegalArgumentException("invalid or overlapping research group region");
            }
            previousRegionRight = region.right();
        }
        if (!groupRegions.isEmpty()) {
            for (PositionedNode node : nodes) {
                long containingRegions = groupRegions.stream()
                        .filter(region -> node.x() >= region.x()
                                && node.x() + NODE_WIDTH <= region.right()
                                && node.y() >= region.y()
                                && node.y() + NODE_HEIGHT <= region.bottom())
                        .count();
                if (containingRegions != 1) {
                    throw new IllegalArgumentException(
                            "positioned node does not belong to exactly one research group region");
                }
            }
        }


        Set<RouteKey> routeKeys = new HashSet<>();
        Map<ResourceLocation, PositionedNode> positionedById = nodes.stream().collect(
                java.util.stream.Collectors.toMap(PositionedNode::blueprintId, node -> node));
        for (EdgeRouteHint hint : edgeRouteHints) {
            PositionedNode prerequisite = positionedById.get(hint.prerequisiteId());
            PositionedNode dependent = positionedById.get(hint.dependentId());
            RouteKey routeKey = new RouteKey(hint.prerequisiteId(), hint.dependentId());
            if (prerequisite == null || dependent == null || !routeKeys.add(routeKey)
                    || prerequisite.y() <= dependent.y()) {
                throw new IllegalArgumentException("invalid research edge route hint");
            }
            int previousY = prerequisite.centerY();
            for (RouteWaypoint waypoint : hint.waypoints()) {
                if (waypoint.x() >= width || waypoint.y() >= height
                        || waypoint.y() >= previousY
                        || waypoint.y() <= dependent.centerY()) {
                    throw new IllegalArgumentException("invalid research edge route waypoint");
                }
                previousY = waypoint.y();
            }
        }
    }

    private static long spatialBucketKey(int x, int y) {
        return ((long) x << 32) | Integer.toUnsignedLong(y);
    }

    private static boolean nodesOverlap(PositionedNode left, PositionedNode right) {
        return left.x() < right.x() + NODE_WIDTH
                && left.x() + NODE_WIDTH > right.x()
                && left.y() < right.y() + NODE_HEIGHT
                && left.y() + NODE_HEIGHT > right.y();
    }

    private static String describePosition(PositionedNode node) {
        return node.blueprintId()
                + " at (" + node.x() + ", " + node.y() + ")"
                + " tier=" + node.tier()
                + " order=" + node.orderInTier()
                + " component=" + node.component();
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof ResearchTreeLayout other
                && width == other.width && height == other.height && tierCount == other.tierCount
                && nodes.equals(other.nodes) && hiddenAnchors.equals(other.hiddenAnchors)
                && categoryLanes.equals(other.categoryLanes)
                && groupRegions.equals(other.groupRegions)
                && edgeRouteHints.equals(other.edgeRouteHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                width, height, tierCount, nodes, hiddenAnchors, categoryLanes, groupRegions,
                edgeRouteHints);
    }

    @Override
    public String toString() {
        return "ResearchTreeLayout[width=" + width + ", height=" + height
                + ", tierCount=" + tierCount + ", nodes=" + nodes
                + ", hiddenAnchors=" + hiddenAnchors
                + ", categoryLanes=" + categoryLanes
                + ", groupRegions=" + groupRegions
                + ", edgeRouteHints=" + edgeRouteHints + "]";
    }

    public record PositionedNode(
            int nodeOrdinal,
            ResourceLocation blueprintId,
            int component,
            int tier,
            int orderInTier,
            int x,
            int y) {
        public PositionedNode {
            if (nodeOrdinal < 0 || nodeOrdinal >= ResearchTreeGraph.MAX_NODES
                    || blueprintId == null
                    || component < 0 || component >= ResearchTreeGraph.MAX_NODES
                    || tier < 0 || tier >= ResearchTreeGraph.MAX_NODES
                    || orderInTier < 0 || orderInTier >= ResearchTreeGraph.MAX_NODES
                    || x < 0 || y < 0) {
                throw new IllegalArgumentException("invalid positioned research tree node");
            }
        }

        public int centerX() {
            return x + NODE_WIDTH / 2;
        }

        public int centerY() {
            return y + NODE_HEIGHT / 2;
        }
    }

    public record TierBounds(int tier, int minimumY, int maximumBottom) {
        public TierBounds {
            if (tier < 0 || tier >= ResearchTreeGraph.MAX_NODES
                    || minimumY < 0 || maximumBottom <= minimumY
                    || maximumBottom > MAX_DIMENSION) {
                throw new IllegalArgumentException("invalid research tier bounds");
            }
        }

        public int centerY() {
            return minimumY + (maximumBottom - minimumY) / 2;
        }
    }

    public record EdgeRouteHint(
            ResourceLocation prerequisiteId,
            ResourceLocation dependentId,
            List<RouteWaypoint> waypoints) {
        public EdgeRouteHint {
            if (prerequisiteId == null || dependentId == null
                    || prerequisiteId.equals(dependentId)
                    || waypoints == null || waypoints.isEmpty()
                    || waypoints.size() >= com.gamergaming.taczweaponblueprints.resource.research
                            .BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH
                    || waypoints.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("invalid research edge route hint");
            }
            waypoints = List.copyOf(waypoints);
        }
    }

    public record RouteWaypoint(int rank, int x, int y) {
        public RouteWaypoint {
            if (rank < 0
                    || rank >= ResearchTreeGraph.MAX_NODES
                    || x < 0 || y < 0) {
                throw new IllegalArgumentException("invalid research edge route waypoint");
            }
        }
    }

    private record RouteKey(ResourceLocation prerequisiteId, ResourceLocation dependentId) {
    }

    /** Anonymous layout-only gateway; it never carries a hidden blueprint identity. */
    public record HiddenAnchor(ResourceLocation dependentId, int hiddenCount, int x, int y) {
        public HiddenAnchor {
            if (dependentId == null || hiddenCount < 1
                    || hiddenCount > BlueprintResearchRule.MAX_PREREQUISITES
                    || x < 0 || y < 0) {
                throw new IllegalArgumentException("invalid hidden-prerequisite anchor");
            }
        }
    }

    /** A lane key is always sourced from the already-published node item type. */
    public record CategoryLane(String key, int x, int width) {
        public CategoryLane {
            if (key == null || key.isBlank()
                    || key.length() > BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH
                    || x < 0 || width < NODE_WIDTH
                    || x > MAX_DIMENSION - width) {
                throw new IllegalArgumentException("invalid research category lane");
            }
        }

        public int right() {
            return x + width;
        }
    }

    public record GroupRegion(ResourceLocation groupId, int x, int y, int width, int height) {
        public GroupRegion {
            if (groupId == null || x < 0 || y < 0
                    || width < NODE_WIDTH || height < NODE_HEIGHT
                    || x > MAX_DIMENSION - width || y > MAX_DIMENSION - height) {
                throw new IllegalArgumentException("invalid research group region");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }
}
