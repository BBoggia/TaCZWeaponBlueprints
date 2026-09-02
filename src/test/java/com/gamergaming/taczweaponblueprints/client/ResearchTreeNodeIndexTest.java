package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeNodeIndexTest {
    @Test
    void visibleQueriesAndHitTestingUseHalfOpenNodeBounds() {
        ResearchTreeLayout layout = layout(List.of(
                positioned(0, "test:a", 8, 8),
                positioned(1, "test:b", 96, 8),
                positioned(2, "test:c", 8, 96),
                positioned(3, "test:boundary", 56, 96)));
        ResearchTreeNodeIndex index = ResearchTreeNodeIndex.create(layout);

        assertEquals(List.of(id("test:a")), ids(index.visible(0, 0, 63, 63)));
        assertEquals(List.of(id("test:b")), ids(index.visible(80, 0, 140, 63)));
        assertEquals(List.of(id("test:boundary")), ids(index.visible(55, 100, 57, 110)));
        assertEquals(id("test:a"), index.at(8, 8).orElseThrow().blueprintId());
        assertEquals(id("test:a"), index.at(31.999, 31.999).orElseThrow().blueprintId());
        assertTrue(index.at(32, 32).isEmpty());
        assertTrue(index.at(-1, 8).isEmpty());
        assertTrue(index.bucketCount() > 1);
    }

    @Test
    void emptyInvalidAndOffCanvasQueriesAreSafe() {
        assertTrue(ResearchTreeNodeIndex.EMPTY.visible(0, 0, 10, 10).isEmpty());
        assertTrue(ResearchTreeNodeIndex.EMPTY.at(0, 0).isEmpty());
        assertEquals(ResearchTreeNodeIndex.EMPTY,
                ResearchTreeNodeIndex.create(ResearchTreeLayout.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> ResearchTreeNodeIndex.create(null));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeNodeIndex.EMPTY.visible(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeNodeIndex.EMPTY.at(Double.POSITIVE_INFINITY, 0));
        ResearchTreeNodeIndex index = ResearchTreeNodeIndex.create(
                layout(List.of(positioned(0, "test:a", 8, 8))));
        assertTrue(index.visible(500, 500, 600, 600).isEmpty());
        assertTrue(index.visible(10, 10, 9, 9).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> index.at(8, 8, -1));
    }

    @Test
    void paddedHitTargetsResolveToTheNearestPublishedNode() {
        ResearchTreeNodeIndex index = ResearchTreeNodeIndex.create(layout(List.of(
                positioned(0, "test:left", 8, 8),
                positioned(1, "test:right", 40, 8))));

        assertTrue(index.at(3, 20).isEmpty());
        assertEquals(id("test:left"), index.at(3, 20, 6).orElseThrow().blueprintId());
        assertEquals(id("test:left"), index.at(36, 20, 6).orElseThrow().blueprintId());
        assertEquals(id("test:right"), index.at(37, 20, 6).orElseThrow().blueprintId());
    }

    @Test
    void maximumFixtureQueriesRemainBoundedAndDeterministic() {
        List<ResearchTreeLayout.PositionedNode> nodes = new ArrayList<>();
        int columns = 64;
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            int column = ordinal % columns;
            int row = ordinal / columns;
            nodes.add(positioned(
                    ordinal,
                    "test:node_" + ordinal,
                    column * 32,
                    row * 32));
        }
        ResearchTreeLayout layout = new ResearchTreeLayout(
                columns * 32,
                columns * 32,
                1,
                nodes);
        ResearchTreeNodeIndex index = assertTimeout(
                Duration.ofSeconds(2),
                () -> ResearchTreeNodeIndex.create(layout));

        List<ResearchTreeLayout.PositionedNode> visible = assertTimeout(
                Duration.ofSeconds(1),
                () -> index.visible(320, 320, 639, 639));
        assertEquals(100, visible.size());
        assertEquals(visible, index.visible(320, 320, 639, 639));
        assertEquals(id("test:node_650"),
                index.at(320, 320).orElseThrow().blueprintId());
        assertEquals(ResearchTreeGraph.MAX_NODES,
                index.visible(0, 0, layout.width(), layout.height()).size());
    }

    @Test
    void extremeFitPaddingFallsBackToPopulatedBuckets() {
        ResearchTreeNodeIndex index = ResearchTreeNodeIndex.create(layout(List.of(
                positioned(0, "test:left", 8, 8),
                positioned(1, "test:right", 96, 96))));

        assertEquals(id("test:left"), assertTimeout(
                Duration.ofSeconds(1),
                () -> index.at(20, 20, Double.MAX_VALUE))
                .orElseThrow().blueprintId());
    }

    private static List<ResourceLocation> ids(
            List<ResearchTreeLayout.PositionedNode> nodes) {
        return nodes.stream().map(ResearchTreeLayout.PositionedNode::blueprintId).toList();
    }

    private static ResearchTreeLayout layout(
            List<ResearchTreeLayout.PositionedNode> nodes) {
        return new ResearchTreeLayout(160, 160, 1, nodes);
    }

    private static ResearchTreeLayout.PositionedNode positioned(
            int ordinal,
            String value,
            int x,
            int y) {
        return new ResearchTreeLayout.PositionedNode(
                ordinal, id(value), 0, 0, ordinal, x, y);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
