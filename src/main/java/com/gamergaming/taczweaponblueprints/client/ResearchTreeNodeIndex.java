package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

/** Bounded two-dimensional index for visible-node queries and pointer hit testing. */
public final class ResearchTreeNodeIndex {
    static final int BUCKET_SIZE = 64;
    public static final ResearchTreeNodeIndex EMPTY = new ResearchTreeNodeIndex(
            0, 0, 0, Map.of());

    private final int width;
    private final int height;
    private final int nodeCount;
    private final Map<Long, List<ResearchTreeLayout.PositionedNode>> buckets;

    private ResearchTreeNodeIndex(
            int width,
            int height,
            int nodeCount,
            Map<Long, List<ResearchTreeLayout.PositionedNode>> buckets) {
        this.width = width;
        this.height = height;
        this.nodeCount = nodeCount;
        this.buckets = buckets;
    }

    public static ResearchTreeNodeIndex create(ResearchTreeLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Research Tree node index layout cannot be null");
        }
        if (layout.nodes().isEmpty()) {
            return EMPTY;
        }
        Map<Long, List<ResearchTreeLayout.PositionedNode>> mutable = new LinkedHashMap<>();
        for (ResearchTreeLayout.PositionedNode node : layout.nodes()) {
            int bucketX = Math.floorDiv(node.centerX(), BUCKET_SIZE);
            int bucketY = Math.floorDiv(node.centerY(), BUCKET_SIZE);
            mutable.computeIfAbsent(key(bucketX, bucketY), ignored -> new ArrayList<>()).add(node);
        }
        Map<Long, List<ResearchTreeLayout.PositionedNode>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, nodes) -> immutable.put(key, List.copyOf(nodes)));
        return new ResearchTreeNodeIndex(
                layout.width(),
                layout.height(),
                layout.nodes().size(),
                Collections.unmodifiableMap(immutable));
    }

    public List<ResearchTreeLayout.PositionedNode> visible(
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY) {
        validateFinite(minimumX, minimumY, maximumX, maximumY);
        if (nodeCount == 0 || maximumX < minimumX || maximumY < minimumY
                || maximumX < 0.0D || maximumY < 0.0D
                || minimumX > width || minimumY > height) {
            return List.of();
        }
        double clampedMinimumX = Math.max(-ResearchTreeLayout.NODE_WIDTH, minimumX);
        double clampedMinimumY = Math.max(-ResearchTreeLayout.NODE_HEIGHT, minimumY);
        double clampedMaximumX = Math.min(width + ResearchTreeLayout.NODE_WIDTH, maximumX);
        double clampedMaximumY = Math.min(height + ResearchTreeLayout.NODE_HEIGHT, maximumY);
        int minimumBucketX = bucket(clampedMinimumX - ResearchTreeLayout.NODE_WIDTH / 2.0D);
        int maximumBucketX = bucket(clampedMaximumX + ResearchTreeLayout.NODE_WIDTH / 2.0D);
        int minimumBucketY = bucket(clampedMinimumY - ResearchTreeLayout.NODE_HEIGHT / 2.0D);
        int maximumBucketY = bucket(clampedMaximumY + ResearchTreeLayout.NODE_HEIGHT / 2.0D);

        long columns = (long) maximumBucketX - minimumBucketX + 1L;
        long rows = (long) maximumBucketY - minimumBucketY + 1L;
        List<ResearchTreeLayout.PositionedNode> result = new ArrayList<>();
        if (columns * rows > buckets.size() * 4L) {
            for (List<ResearchTreeLayout.PositionedNode> bucketNodes : buckets.values()) {
                collectVisible(
                        bucketNodes,
                        clampedMinimumX,
                        clampedMinimumY,
                        clampedMaximumX,
                        clampedMaximumY,
                        result);
            }
        } else {
            for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
                for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                    collectVisible(
                            buckets.getOrDefault(key(bucketX, bucketY), List.of()),
                            clampedMinimumX,
                            clampedMinimumY,
                            clampedMaximumX,
                            clampedMaximumY,
                            result);
                }
            }
        }
        return List.copyOf(result);
    }

    public Optional<ResearchTreeLayout.PositionedNode> at(double x, double y) {
        validateFinite(x, y);
        if (nodeCount == 0 || x < 0.0D || y < 0.0D || x >= width || y >= height) {
            return Optional.empty();
        }
        int minimumBucketX = bucket(x - ResearchTreeLayout.NODE_WIDTH / 2.0D);
        int maximumBucketX = bucket(x + ResearchTreeLayout.NODE_WIDTH / 2.0D);
        int minimumBucketY = bucket(y - ResearchTreeLayout.NODE_HEIGHT / 2.0D);
        int maximumBucketY = bucket(y + ResearchTreeLayout.NODE_HEIGHT / 2.0D);
        ResearchTreeLayout.PositionedNode match = null;
        for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                for (ResearchTreeLayout.PositionedNode node
                        : buckets.getOrDefault(key(bucketX, bucketY), List.of())) {
                    if (x >= node.x() && x < node.x() + ResearchTreeLayout.NODE_WIDTH
                            && y >= node.y() && y < node.y() + ResearchTreeLayout.NODE_HEIGHT
                            && (match == null || node.nodeOrdinal() < match.nodeOrdinal())) {
                        match = node;
                    }
                }
            }
        }
        return Optional.ofNullable(match);
    }

    int bucketCount() {
        return buckets.size();
    }

    private static void collectVisible(
            List<ResearchTreeLayout.PositionedNode> candidates,
            double minimumX,
            double minimumY,
            double maximumX,
            double maximumY,
            List<ResearchTreeLayout.PositionedNode> result) {
        for (ResearchTreeLayout.PositionedNode node : candidates) {
            if (node.x() + ResearchTreeLayout.NODE_WIDTH >= minimumX
                    && node.x() <= maximumX
                    && node.y() + ResearchTreeLayout.NODE_HEIGHT >= minimumY
                    && node.y() <= maximumY) {
                result.add(node);
            }
        }
    }

    private static int bucket(double coordinate) {
        return (int) Math.floor(coordinate / BUCKET_SIZE);
    }

    private static long key(int bucketX, int bucketY) {
        return ((long) bucketX << 32) ^ Integer.toUnsignedLong(bucketY);
    }

    private static void validateFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Research Tree node query must be finite");
            }
        }
    }
}
