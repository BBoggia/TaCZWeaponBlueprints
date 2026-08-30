package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeLayoutEngineTest {
    @Test
    void laysOutBranchesMergesAndComponentsDeterministically() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0, 0),
                        node(1, "test:b", 1, 0),
                        node(2, "test:c", 1, 0),
                        node(3, "test:d", 2, 0),
                        node(4, "test:e", 0, 0)),
                List.of(
                        edge("test:a", "test:b"),
                        edge("test:a", "test:c"),
                        edge("test:b", "test:d"),
                        edge("test:c", "test:d")));

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertEquals(layout, ResearchTreeLayoutEngine.layout(graph));
        assertEquals(3, layout.tierCount());
        assertEquals(0, layout.position(id("test:a")).orElseThrow().tier());
        assertEquals(1, layout.position(id("test:b")).orElseThrow().tier());
        assertEquals(1, layout.position(id("test:c")).orElseThrow().tier());
        assertEquals(2, layout.position(id("test:d")).orElseThrow().tier());
        assertEquals(0, layout.position(id("test:e")).orElseThrow().tier());
        assertTrue(layout.position(id("test:d")).orElseThrow().y()
                < layout.position(id("test:b")).orElseThrow().y());
        assertTrue(layout.position(id("test:b")).orElseThrow().y()
                < layout.position(id("test:a")).orElseThrow().y());
        assertEquals(List.of(id("test:a"), id("test:e")),
                layout.tier(0).stream().map(ResearchTreeLayout.PositionedNode::blueprintId).toList());
        assertEquals(List.of(id("test:b"), id("test:c")),
                layout.tier(1).stream().map(ResearchTreeLayout.PositionedNode::blueprintId).toList());
        assertEquals(List.of(0, 1, 2),
                layout.tierBounds().stream().map(ResearchTreeLayout.TierBounds::tier).toList());
        layout.tierBounds().forEach(bounds -> {
            assertEquals(
                    layout.tier(bounds.tier()).stream()
                            .mapToInt(ResearchTreeLayout.PositionedNode::y)
                            .min()
                            .orElseThrow(),
                    bounds.minimumY());
            assertEquals(
                    layout.tier(bounds.tier()).stream()
                            .mapToInt(node -> node.y() + ResearchTreeLayout.NODE_HEIGHT)
                            .max()
                            .orElseThrow(),
                    bounds.maximumBottom());
        });
        assertTrue(layout.hiddenAnchors().isEmpty());
        assertNoOverlap(layout);
    }

    @Test
    void thousandsOfDisconnectedNodesWrapIntoABoundedCanvas() {
        List<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            nodes.add(node(index, "test:node_" + index, 0, 0));
        }

        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, List.of());
        ResearchTreeLayout layout = assertTimeout(
                Duration.ofSeconds(5),
                () -> ResearchTreeLayoutEngine.layout(graph));

        int maximumWidth = ResearchTreeLayoutEngine.PADDING * 2
                + ResearchTreeLayoutEngine.TIER_GUTTER_WIDTH
                + ResearchTreeLayoutEngine.LANE_PADDING * 2
                + ResearchTreeLayoutEngine.MAX_COLUMNS * ResearchTreeLayout.NODE_WIDTH
                + (ResearchTreeLayoutEngine.MAX_COLUMNS - 1) * ResearchTreeLayoutEngine.HORIZONTAL_GAP;
        assertTrue(layout.width() <= maximumWidth);
        assertTrue(layout.height() < ResearchTreeLayout.MAX_DIMENSION);
        assertEquals(ResearchTreeGraph.MAX_NODES, layout.tier(0).size());
        assertEquals(id("test:node_4095"),
                layout.position(id("test:node_4095")).orElseThrow().blueprintId());
        assertNoOverlap(layout);
    }

    @Test
    void validatingMaximumWidthRowsRemainsLinear() {
        List<ResearchTreeLayout.PositionedNode> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            nodes.add(positioned(
                    index,
                    "test:wide_" + index,
                    0,
                    index,
                    index * ResearchTreeLayout.NODE_WIDTH,
                    0));
        }

        assertTimeout(Duration.ofSeconds(1), () -> new ResearchTreeLayout(
                ResearchTreeGraph.MAX_NODES * ResearchTreeLayout.NODE_WIDTH,
                ResearchTreeLayout.NODE_HEIGHT,
                1,
                nodes));
    }

    @Test
    void longestVisiblePathDeterminesTier() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0, 0),
                        node(1, "test:b", 1, 0),
                        node(2, "test:c", 1, 0),
                        node(3, "test:d", 2, 0)),
                List.of(
                        edge("test:a", "test:b"),
                        edge("test:b", "test:c"),
                        edge("test:a", "test:d"),
                        edge("test:c", "test:d")));

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertEquals(0, layout.position(id("test:a")).orElseThrow().tier());
        assertEquals(1, layout.position(id("test:b")).orElseThrow().tier());
        assertEquals(2, layout.position(id("test:c")).orElseThrow().tier());
        assertEquals(3, layout.position(id("test:d")).orElseThrow().tier());
        assertEquals(4, layout.tierCount());
    }

    @Test
    void publishedItemTypesProduceStableDisclosureSafeCategoryLanes() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:rifle", "rifle", 0, 0),
                        node(1, "test:pistol", "pistol", 0, 0),
                        redactedNode(2)),
                List.of());

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertEquals(
                List.of("pistol", "rifle", "undisclosed"),
                layout.categoryLanes().stream()
                        .map(ResearchTreeLayout.CategoryLane::key)
                        .toList());
        assertInsideLane(layout, id("test:pistol"), "pistol");
        assertInsideLane(layout, id("test:rifle"), "rifle");
        assertInsideLane(layout, ResearchTreeGraph.redactedNodeId(2), "undisclosed");
    }

    @Test
    void barycentricSweepsRemoveAnAvoidableCrossingWithinALane() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0, 0),
                        node(1, "test:b", 0, 0),
                        node(2, "test:c", 1, 0),
                        node(3, "test:d", 1, 0)),
                List.of(
                        edge("test:b", "test:c"),
                        edge("test:a", "test:d")));

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertTrue(layout.position(id("test:a")).orElseThrow().x()
                < layout.position(id("test:b")).orElseThrow().x());
        assertTrue(layout.position(id("test:d")).orElseThrow().x()
                < layout.position(id("test:c")).orElseThrow().x());
    }

    @Test
    void globalPublicationLayoutPreservesPublishedRanksAndSiblingOrder() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0, 0),
                        node(1, "test:b", 0, 0),
                        node(2, "test:c", 0, 0)),
                List.of());
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:weapons"),
                        "Weapons",
                        Optional.empty(),
                        Optional.of(id("test:b")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:b"), 0, 0),
                                new ResearchTreePresentation.Member(id("test:a"), 0, 1),
                                new ResearchTreePresentation.Member(id("test:c"), 2, 0)))));

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(
                new ResearchTreePublication(graph, presentation));

        assertEquals(3, layout.tierCount());
        assertEquals(0, layout.position(id("test:b")).orElseThrow().tier());
        assertEquals(2, layout.position(id("test:c")).orElseThrow().tier());
        assertTrue(layout.tier(1).isEmpty());
        assertEquals(List.of(0, 2),
                layout.tierBounds().stream().map(ResearchTreeLayout.TierBounds::tier).toList());
        assertTrue(layout.position(id("test:b")).orElseThrow().x()
                < layout.position(id("test:a")).orElseThrow().x());
        assertTrue(layout.position(id("test:c")).orElseThrow().y()
                < layout.position(id("test:b")).orElseThrow().y());
        assertTrue(layout.groupRegions().isEmpty());
        assertNoOverlap(layout);
    }

    @Test
    void sparsePublishedRanksDoNotCreateAnUnboundedCanvas() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:bottom", 0, 0),
                        node(1, "test:top", 0, 0)),
                List.of());
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:sparse"),
                        "Sparse",
                        Optional.empty(),
                        Optional.of(id("test:bottom")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:bottom"), 0, 0),
                                new ResearchTreePresentation.Member(
                                        id("test:top"), ResearchTreeGraph.MAX_NODES - 1, 0)))));

        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(
                new ResearchTreePublication(graph, presentation));

        assertEquals(ResearchTreeGraph.MAX_NODES, layout.tierCount());
        assertTrue(layout.height() < 256);
        assertTrue(layout.position(id("test:top")).orElseThrow().y()
                < layout.position(id("test:bottom")).orElseThrow().y());
    }

    @Test
    void emptyAndNullGraphsHaveAnEmptyLayout() {
        assertEquals(ResearchTreeLayout.EMPTY,
                ResearchTreeLayoutEngine.layout((ResearchTreeGraph) null));
        assertEquals(ResearchTreeLayout.EMPTY, ResearchTreeLayoutEngine.layout(ResearchTreeGraph.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeLayoutEngine.layout((ResearchTreePublication) null));
    }

    @Test
    void layoutRejectsOverlappingOrOutOfBoundsNodes() {
        ResearchTreeLayout.PositionedNode a = positioned(0, "test:a", 0, 0, 0, 0);
        ResearchTreeLayout.PositionedNode b = positioned(1, "test:b", 0, 1, 10, 0);
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeLayout(
                64, 48, 1, List.of(a, b)));

        ResearchTreeLayout.PositionedNode outside = positioned(0, "test:a", 0, 0, 50, 0);
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeLayout(
                64, 48, 1, List.of(outside)));

        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeLayout(
                80, 48, 1, List.of(a), List.of(), List.of(
                        new ResearchTreeLayout.CategoryLane("rifle", 0, 40),
                        new ResearchTreeLayout.CategoryLane("pistol", 30, 40))));
    }

    private static void assertInsideLane(
            ResearchTreeLayout layout,
            ResourceLocation blueprintId,
            String laneKey) {
        ResearchTreeLayout.PositionedNode node = layout.position(blueprintId).orElseThrow();
        ResearchTreeLayout.CategoryLane lane = layout.categoryLanes().stream()
                .filter(candidate -> candidate.key().equals(laneKey))
                .findFirst()
                .orElseThrow();
        assertTrue(node.x() >= lane.x());
        assertTrue(node.x() + ResearchTreeLayout.NODE_WIDTH <= lane.right());
    }

    private static void assertNoOverlap(ResearchTreeLayout layout) {
        for (ResearchTreeLayout.PositionedNode left : layout.nodes()) {
            for (ResearchTreeLayout.PositionedNode right : layout.nodes()) {
                if (left.nodeOrdinal() >= right.nodeOrdinal()) {
                    continue;
                }
                boolean overlap = left.x() < right.x() + ResearchTreeLayout.NODE_WIDTH
                        && left.x() + ResearchTreeLayout.NODE_WIDTH > right.x()
                        && left.y() < right.y() + ResearchTreeLayout.NODE_HEIGHT
                        && left.y() + ResearchTreeLayout.NODE_HEIGHT > right.y();
                assertTrue(!overlap, () -> left.blueprintId() + " overlaps " + right.blueprintId());
            }
        }
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            String value,
            int prerequisites,
            int hiddenPrerequisites) {
        return node(ordinal, value, "rifle", prerequisites, hiddenPrerequisites);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            String value,
            String itemType,
            int prerequisites,
            int hiddenPrerequisites) {
        ResourceLocation id = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                itemType,
                id("test:slot/" + id.getPath()),
                JournalVisibility.FULL,
                false,
                false,
                false,
                8,
                0,
                prerequisites,
                hiddenPrerequisites,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node redactedNode(int ordinal) {
        return new ResearchTreeGraph.Node(
                ordinal,
                ResearchTreeGraph.redactedNodeId(ordinal),
                "name.mystery",
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.NAME,
                false, false, false, 0, 0, 0, 0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResearchTreeGraph.Edge edge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(id(prerequisite), id(dependent));
    }

    private static ResearchTreeLayout.PositionedNode positioned(
            int ordinal,
            String value,
            int tier,
            int order,
            int x,
            int y) {
        return new ResearchTreeLayout.PositionedNode(
                ordinal, id(value), 0, tier, order, x, y);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
