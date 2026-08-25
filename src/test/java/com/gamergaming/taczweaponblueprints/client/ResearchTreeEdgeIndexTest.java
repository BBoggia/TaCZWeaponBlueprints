package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeEdgeIndexTest {
    @Test
    void returnsOnlyEdgesThatIntersectTheVisibleCanvas() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0),
                        node(1, "test:b", 1),
                        node(2, "test:c", 1),
                        node(3, "test:d", 1)),
                List.of(edge("test:a", "test:b"), edge("test:b", "test:c"), edge("test:c", "test:d")));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        ResearchTreeEdgeIndex index = ResearchTreeEdgeIndex.create(graph, layout);
        ResearchTreeLayout.PositionedNode b = layout.position(id("test:b")).orElseThrow();

        List<ResearchTreeEdgeIndex.PositionedEdge> visible = index.visible(
                0,
                b.y() - 2,
                layout.width(),
                b.y() + ResearchTreeLayout.NODE_HEIGHT + 2);

        assertEquals(2, visible.size());
        assertTrue(visible.stream().anyMatch(value -> value.edge().equals(edge("test:a", "test:b"))));
        assertTrue(visible.stream().anyMatch(value -> value.edge().equals(edge("test:b", "test:c"))));
    }

    @Test
    void emptyIndexReturnsNoCandidates() {
        assertTrue(ResearchTreeEdgeIndex.EMPTY.visible(0, 0, 100, 100).isEmpty());
    }

    @Test
    void routesLeavePrerequisitesAndTerminateInUpwardArrowheadsAtDependents() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, "test:a", 0), node(1, "test:b", 1)),
                List.of(edge("test:a", "test:b")));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex
                .create(graph, layout)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);
        ResearchTreeLayout.PositionedNode dependent =
                layout.position(id("test:b")).orElseThrow();

        assertEquals(dependent.y() + ResearchTreeLayout.NODE_HEIGHT, route.endY());
        assertEquals(route.endY() + 4, route.arrowBaseY());
        assertTrue(route.startY() > route.sourceExitY());
        assertTrue(route.sourceExitY() >= route.targetApproachY());
        assertTrue(route.targetApproachY() >= route.arrowBaseY());
    }

    @Test
    void fanOutAndFanInUseDeterministicDistinctNodePorts() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0),
                        node(1, "test:b", 0),
                        node(2, "test:c", 0),
                        node(3, "test:d", 1),
                        node(4, "test:e", 1),
                        node(5, "test:f", 3)),
                List.of(
                        edge("test:a", "test:d"),
                        edge("test:a", "test:e"),
                        edge("test:a", "test:f"),
                        edge("test:b", "test:f"),
                        edge("test:c", "test:f")));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        List<ResearchTreeEdgeIndex.PositionedEdge> routes = ResearchTreeEdgeIndex
                .create(graph, layout)
                .visible(0, 0, layout.width(), layout.height());

        Set<Integer> outgoingPorts = new HashSet<>();
        Set<Integer> incomingPorts = new HashSet<>();
        routes.stream()
                .filter(route -> route.edge().prerequisiteId().equals(id("test:a")))
                .forEach(route -> outgoingPorts.add(route.startX()));
        routes.stream()
                .filter(route -> route.edge().dependentId().equals(id("test:f")))
                .forEach(route -> incomingPorts.add(route.endX()));

        assertEquals(3, outgoingPorts.size());
        assertEquals(3, incomingPorts.size());
        assertEquals(
                routes,
                ResearchTreeEdgeIndex.create(graph, layout)
                        .visible(0, 0, layout.width(), layout.height()));
    }

    @Test
    void laneGutterRoutingAvoidsNodesInWrappedRows() {
        List<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < 13; ordinal++) {
            nodes.add(node(ordinal, "test:root_" + ordinal, 0));
        }
        nodes.add(node(13, "test:target", 1));
        ResearchTreeGraph graph = new ResearchTreeGraph(
                nodes,
                List.of(edge("test:root_0", "test:target")));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex
                .create(graph, layout)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);

        for (ResearchTreeLayout.PositionedNode node : layout.nodes()) {
            if (node.blueprintId().equals(route.edge().prerequisiteId())
                    || node.blueprintId().equals(route.edge().dependentId())) {
                continue;
            }
            assertSegmentAvoidsNode(
                    route.startX(), route.sourceExitY(),
                    route.trackX(), route.sourceExitY(), node);
            assertSegmentAvoidsNode(
                    route.trackX(), route.sourceExitY(),
                    route.trackX(), route.targetApproachY(), node);
            assertSegmentAvoidsNode(
                    route.trackX(), route.targetApproachY(),
                    route.endX(), route.targetApproachY(), node);
        }
    }

    @Test
    void arrowheadBoundsParticipateInViewportCulling() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, "test:a", 0), node(1, "test:b", 1)),
                List.of(edge("test:a", "test:b")));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);
        ResearchTreeEdgeIndex index = ResearchTreeEdgeIndex.create(graph, layout);
        ResearchTreeEdgeIndex.PositionedEdge route =
                index.visible(0, 0, layout.width(), layout.height()).get(0);

        assertEquals(
                List.of(route),
                index.visible(
                        route.endX() + 3,
                        route.endY(),
                        route.endX() + 3,
                        route.arrowBaseY()));
    }

    @Test
    void anonymousPublishedTopologyRoutesWithoutPrivateMetadata() {
        ResearchTreeGraph.Node source = redactedNode(0, JournalVisibility.SILHOUETTE, 0);
        ResearchTreeGraph.Node dependent = redactedNode(1, JournalVisibility.NAME, 1);
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(source, dependent),
                List.of(new ResearchTreeGraph.Edge(source.blueprintId(), dependent.blueprintId())));
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex
                .create(graph, layout)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);

        assertEquals("undisclosed", layout.categoryLanes().get(0).key());
        assertEquals(source.blueprintId(), route.edge().prerequisiteId());
        assertEquals(dependent.blueprintId(), route.edge().dependentId());
    }

    @Test
    void maximumNodeFanOutRoutingRemainsBounded() {
        List<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        List<ResearchTreeGraph.Edge> edges = new java.util.ArrayList<>();
        nodes.add(node(0, "test:root", 0));
        for (int ordinal = 1; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            String value = "test:leaf_" + ordinal;
            nodes.add(node(ordinal, value, 1));
            edges.add(edge("test:root", value));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertTimeout(Duration.ofSeconds(2), () -> {
            ResearchTreeEdgeIndex index = ResearchTreeEdgeIndex.create(graph, layout);
            assertEquals(
                    ResearchTreeGraph.MAX_NODES - 1,
                    index.visible(0, 0, layout.width(), layout.height()).size());
        });
    }

    private static void assertSegmentAvoidsNode(
            int startX,
            int startY,
            int endX,
            int endY,
            ResearchTreeLayout.PositionedNode node) {
        int minimumX = Math.min(startX, endX);
        int maximumX = Math.max(startX, endX);
        int minimumY = Math.min(startY, endY);
        int maximumY = Math.max(startY, endY);
        boolean intersectsInterior;
        if (startX == endX) {
            intersectsInterior = startX > node.x()
                    && startX < node.x() + ResearchTreeLayout.NODE_WIDTH
                    && maximumY > node.y()
                    && minimumY < node.y() + ResearchTreeLayout.NODE_HEIGHT;
        } else {
            intersectsInterior = startY > node.y()
                    && startY < node.y() + ResearchTreeLayout.NODE_HEIGHT
                    && maximumX > node.x()
                    && minimumX < node.x() + ResearchTreeLayout.NODE_WIDTH;
        }
        assertTrue(!intersectsInterior, () -> "connector intersects " + node.blueprintId());
    }

    private static ResearchTreeGraph.Node node(int ordinal, String raw, int prerequisites) {
        ResourceLocation id = id(raw);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "gun",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                prerequisites == 0,
                4,
                0,
                prerequisites,
                0,
                prerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node redactedNode(
            int ordinal,
            JournalVisibility visibility,
            int prerequisites) {
        return new ResearchTreeGraph.Node(
                ordinal,
                ResearchTreeGraph.redactedNodeId(ordinal),
                visibility.revealsName() ? "name.mystery" : ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false,
                false,
                false,
                0,
                0,
                prerequisites,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResearchTreeGraph.Edge edge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(id(prerequisite), id(dependent));
    }

    private static ResourceLocation id(String raw) {
        return new ResourceLocation(raw);
    }
}
