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
    void unifiedLayoutSharesForkAndMergeTrunksWhileBranchesKeepDistinctPorts() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:root", 0),
                        node(1, "test:left", 1),
                        node(2, "test:right", 1),
                        node(3, "test:merge", 2)),
                List.of(
                        edge("test:root", "test:left"),
                        edge("test:root", "test:right"),
                        edge("test:left", "test:merge"),
                        edge("test:right", "test:merge")));
        ResearchTreeLayout unified = new ResearchTreeLayout(
                180,
                192,
                3,
                List.of(
                        positioned(0, "test:root", 0, 0, 78, 148),
                        positioned(1, "test:left", 1, 0, 20, 84),
                        positioned(2, "test:right", 1, 1, 136, 84),
                        positioned(3, "test:merge", 2, 0, 78, 20)));

        List<ResearchTreeEdgeIndex.PositionedEdge> routes = ResearchTreeEdgeIndex
                .create(graph, unified)
                .visible(0, 0, unified.width(), unified.height());

        Set<Integer> forkStarts = new HashSet<>();
        Set<Integer> mergeEnds = new HashSet<>();
        routes.stream()
                .filter(route -> route.edge().prerequisiteId().equals(id("test:root")))
                .forEach(route -> forkStarts.add(route.startX()));
        routes.stream()
                .filter(route -> route.edge().dependentId().equals(id("test:merge")))
                .forEach(route -> mergeEnds.add(route.endX()));
        assertEquals(Set.of(unified.position(id("test:root")).orElseThrow().centerX()), forkStarts);
        assertEquals(Set.of(unified.position(id("test:merge")).orElseThrow().centerX()), mergeEnds);
    }

    @Test
    void unifiedLongEdgeMovesItsTrackAroundIntermediateNodeCards() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:root", 0),
                        node(1, "test:blocker", 0),
                        node(2, "test:target", 1)),
                List.of(edge("test:root", "test:target")));
        ResearchTreeLayout layout = new ResearchTreeLayout(
                180,
                192,
                3,
                List.of(
                        positioned(0, "test:root", 0, 0, 78, 148),
                        positioned(1, "test:blocker", 1, 0, 78, 84),
                        positioned(2, "test:target", 2, 0, 78, 20)));

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex
                .create(graph, layout)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);
        ResearchTreeLayout.PositionedNode blocker =
                layout.position(id("test:blocker")).orElseThrow();

        assertTrue(route.trackX() != blocker.centerX());
        assertSegmentAvoidsNode(
                route.trackX(), route.sourceExitY(),
                route.trackX(), route.targetApproachY(), blocker);
    }

    @Test
    void explicitBranchRoutingDoesNotUseItsVisualFrameAsAnEdgeTrack() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, "test:source", 0), node(1, "test:target", 1)),
                List.of(edge("test:source", "test:target")));
        ResearchTreeLayout layout = new ResearchTreeLayout(
                180,
                180,
                2,
                List.of(
                        positioned(0, "test:source", 0, 0, 28, 132),
                        positioned(1, "test:target", 1, 0, 92, 44)),
                List.of(),
                List.of(),
                List.of(new ResearchTreeLayout.GroupRegion(
                        id("test:group"), 10, 10, 160, 160)));

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex.create(
                        graph, layout, ResearchTreeEdgeIndex.RoutingProfile.LOCAL_BRANCH)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);

        assertTrue(route.trackX() != 13 && route.trackX() != 167);
        assertTrue(route.minimumX() > 10);
        assertTrue(route.maximumX() < 170);
    }

    @Test
    void skippedRankHintProducesAReusableOrthogonalPolyline() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, "test:source", 0), node(1, "test:target", 1)),
                List.of(edge("test:source", "test:target")));
        ResearchTreeLayout layout = new ResearchTreeLayout(
                180,
                220,
                2,
                List.of(
                        positioned(0, "test:source", 0, 0, 24, 172),
                        positioned(1, "test:target", 1, 0, 112, 20)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ResearchTreeLayout.EdgeRouteHint(
                        id("test:source"),
                        id("test:target"),
                        List.of(new ResearchTreeLayout.RouteWaypoint(1, 84, 108)))));

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex.create(
                        graph, layout, ResearchTreeEdgeIndex.RoutingProfile.LOCAL_BRANCH)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);

        assertTrue(route.points().contains(new ResearchTreeEdgeIndex.RoutePoint(84, 90)));
        for (int index = 1; index < route.points().size(); index++) {
            ResearchTreeEdgeIndex.RoutePoint previous = route.points().get(index - 1);
            ResearchTreeEdgeIndex.RoutePoint next = route.points().get(index);
            assertTrue(previous.x() == next.x() || previous.y() == next.y());
        }
    }

    @Test
    void skippedRankHintFallsBackWhenItsFinalAtlasRouteCrossesAnotherNode() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, "test:source", 0),
                        node(1, "test:target", 1),
                        node(2, "test:obstacle", 0)),
                List.of(edge("test:source", "test:target")));
        ResearchTreeLayout.PositionedNode obstacle =
                positioned(2, "test:obstacle", 1, 1, 76, 96);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                180,
                220,
                2,
                List.of(
                        positioned(0, "test:source", 0, 0, 24, 172),
                        positioned(1, "test:target", 1, 0, 112, 20),
                        obstacle),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ResearchTreeLayout.EdgeRouteHint(
                        id("test:source"),
                        id("test:target"),
                        List.of(new ResearchTreeLayout.RouteWaypoint(1, 84, 126)))));

        ResearchTreeEdgeIndex.PositionedEdge route = ResearchTreeEdgeIndex.create(
                        graph,
                        layout,
                        ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW)
                .visible(0, 0, layout.width(), layout.height())
                .get(0);

        assertTrue(!route.points().contains(new ResearchTreeEdgeIndex.RoutePoint(84, 108)),
                "the colliding authored turn must be discarded");
        for (int index = 1; index < route.points().size(); index++) {
            ResearchTreeEdgeIndex.RoutePoint previous = route.points().get(index - 1);
            ResearchTreeEdgeIndex.RoutePoint next = route.points().get(index);
            assertSegmentAvoidsNode(
                    previous.x(), previous.y(), next.x(), next.y(), obstacle);
        }
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

    @Test
    void maximumUnifiedFanOutKeepsSharedRoutingBounded() {
        List<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        List<ResearchTreeGraph.Edge> edges = new java.util.ArrayList<>();
        List<ResearchTreeLayout.PositionedNode> positions = new java.util.ArrayList<>();
        int leafCount = ResearchTreeGraph.MAX_NODES - 1;
        int width = 40 + (leafCount - 1) * 48 + ResearchTreeLayout.NODE_WIDTH;
        nodes.add(node(0, "test:unified_root", 0));
        positions.add(positioned(
                0,
                "test:unified_root",
                0,
                0,
                width / 2 - ResearchTreeLayout.NODE_WIDTH / 2,
                108));
        for (int ordinal = 1; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            String value = "test:unified_leaf_" + ordinal;
            nodes.add(node(ordinal, value, 1));
            edges.add(edge("test:unified_root", value));
            positions.add(positioned(
                    ordinal,
                    value,
                    1,
                    ordinal - 1,
                    20 + (ordinal - 1) * 48,
                    20));
        }
        ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
        ResearchTreeLayout layout = new ResearchTreeLayout(
                width, 152, 2, positions);

        assertTimeout(Duration.ofSeconds(3), () -> {
            List<ResearchTreeEdgeIndex.PositionedEdge> routes =
                    ResearchTreeEdgeIndex.create(graph, layout)
                            .visible(0, 0, layout.width(), layout.height());
            assertEquals(leafCount, routes.size());
            assertEquals(1L, routes.stream()
                    .map(ResearchTreeEdgeIndex.PositionedEdge::startX)
                    .distinct()
                    .count());
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

    private static ResearchTreeLayout.PositionedNode positioned(
            int ordinal,
            String raw,
            int tier,
            int order,
            int x,
            int y) {
        return new ResearchTreeLayout.PositionedNode(
                ordinal, id(raw), 0, tier, order, x, y);
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
