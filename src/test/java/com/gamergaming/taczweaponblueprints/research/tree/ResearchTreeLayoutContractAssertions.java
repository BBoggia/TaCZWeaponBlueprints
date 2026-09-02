package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Reusable Phase 0 invariants for every current and future layout strategy. */
final class ResearchTreeLayoutContractAssertions {
    private ResearchTreeLayoutContractAssertions() {
    }

    static void assertFaithfulBottomToTopLayout(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout) {
        assertNotNull(graph);
        assertNotNull(layout);
        assertEquals(graph.nodes().size(), layout.nodes().size());
        assertTrue(layout.width() >= 0 && layout.width() <= ResearchTreeLayout.MAX_DIMENSION);
        assertTrue(layout.height() >= 0 && layout.height() <= ResearchTreeLayout.MAX_DIMENSION);

        Set<ResourceLocation> positionedIds = new LinkedHashSet<>();
        for (int ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
            ResearchTreeGraph.Node graphNode = graph.nodes().get(ordinal);
            ResearchTreeLayout.PositionedNode positioned = layout.nodes().get(ordinal);
            assertEquals(ordinal, positioned.nodeOrdinal());
            assertEquals(graphNode.blueprintId(), positioned.blueprintId());
            assertTrue(positionedIds.add(positioned.blueprintId()),
                    () -> "duplicate positioned node " + positioned.blueprintId());
            assertTrue(positioned.x() >= 0
                    && positioned.x() + ResearchTreeLayout.NODE_WIDTH <= layout.width());
            assertTrue(positioned.y() >= 0
                    && positioned.y() + ResearchTreeLayout.NODE_HEIGHT <= layout.height());
            assertEquals(positioned, layout.position(graphNode.blueprintId()).orElseThrow());
        }
        assertNoOverlap(layout.nodes());

        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            ResearchTreeLayout.PositionedNode prerequisite =
                    layout.position(edge.prerequisiteId()).orElseThrow();
            ResearchTreeLayout.PositionedNode dependent =
                    layout.position(edge.dependentId()).orElseThrow();
            assertTrue(prerequisite.y() > dependent.y(),
                    () -> "prerequisite must render below dependent: " + edge);
            assertTrue(prerequisite.tier() < dependent.tier(),
                    () -> "prerequisite must have a lower visual tier: " + edge);
        }
    }

    static void assertInducedSubgraph(
            ResearchTreeGraph source,
            ResearchTreeGraph projection,
            Set<ResourceLocation> includedIds) {
        Set<ResourceLocation> expectedIds = source.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .filter(includedIds::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<ResourceLocation> actualIds = projection.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expectedIds, actualIds);

        Set<ResearchTreeGraph.Edge> expectedEdges = source.edges().stream()
                .filter(edge -> includedIds.contains(edge.prerequisiteId())
                        && includedIds.contains(edge.dependentId()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expectedEdges, new LinkedHashSet<>(projection.edges()),
                "a projection must neither invent nor discard internal prerequisite edges");
    }

    private static void assertNoOverlap(List<ResearchTreeLayout.PositionedNode> nodes) {
        Map<Long, List<ResearchTreeLayout.PositionedNode>> spatialBuckets = new HashMap<>();
        for (ResearchTreeLayout.PositionedNode node : nodes) {
            int minimumBucketX = node.x() / ResearchTreeLayout.NODE_WIDTH;
            int maximumBucketX = (node.x() + ResearchTreeLayout.NODE_WIDTH - 1)
                    / ResearchTreeLayout.NODE_WIDTH;
            int minimumBucketY = node.y() / ResearchTreeLayout.NODE_HEIGHT;
            int maximumBucketY = (node.y() + ResearchTreeLayout.NODE_HEIGHT - 1)
                    / ResearchTreeLayout.NODE_HEIGHT;
            Set<Integer> checkedOrdinals = new HashSet<>();
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
                    for (ResearchTreeLayout.PositionedNode other : spatialBuckets.getOrDefault(
                            bucketKey(bucketX, bucketY), List.of())) {
                        if (checkedOrdinals.add(other.nodeOrdinal())) {
                            assertTrue(!overlaps(node, other),
                                    () -> "layout nodes overlap: " + node.blueprintId()
                                            + " and " + other.blueprintId());
                        }
                    }
                }
            }
            for (int bucketX = minimumBucketX; bucketX <= maximumBucketX; bucketX++) {
                for (int bucketY = minimumBucketY; bucketY <= maximumBucketY; bucketY++) {
                    spatialBuckets.computeIfAbsent(
                            bucketKey(bucketX, bucketY), ignored -> new java.util.ArrayList<>())
                            .add(node);
                }
            }
        }
    }

    private static boolean overlaps(
            ResearchTreeLayout.PositionedNode left,
            ResearchTreeLayout.PositionedNode right) {
        return left.x() < right.x() + ResearchTreeLayout.NODE_WIDTH
                && right.x() < left.x() + ResearchTreeLayout.NODE_WIDTH
                && left.y() < right.y() + ResearchTreeLayout.NODE_HEIGHT
                && right.y() < left.y() + ResearchTreeLayout.NODE_HEIGHT;
    }

    private static long bucketKey(int x, int y) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(y);
    }
}
