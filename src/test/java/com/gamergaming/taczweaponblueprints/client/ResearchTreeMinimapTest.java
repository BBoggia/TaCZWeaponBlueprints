package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeMinimapTest {
    @Test
    void automaticVisibilityRequiresMeaningfulOverflowWhileExplicitModesWin() {
        ResearchTreeCanvas canvas = canvas(400, 240);
        setContent(canvas, independentGraph(1), layout(1, 200, 100));
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();

        minimap.prepare(ResearchTreeMinimapMode.AUTOMATIC, canvas, true);
        assertFalse(minimap.visible());

        minimap.prepare(ResearchTreeMinimapMode.ALWAYS, canvas, true);
        assertTrue(minimap.visible());

        minimap.prepare(ResearchTreeMinimapMode.HIDDEN, canvas, true);
        assertFalse(minimap.visible());

        setContent(canvas, independentGraph(1), layout(1, 800, 500));
        minimap.prepare(ResearchTreeMinimapMode.AUTOMATIC, canvas, true);
        assertTrue(minimap.visible());
        minimap.prepare(ResearchTreeMinimapMode.AUTOMATIC, canvas, false);
        assertFalse(minimap.visible());
    }

    @Test
    void emptyCanvasNeverCreatesADeadMinimapPanel() {
        ResearchTreeCanvas canvas = canvas(400, 240);
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();

        minimap.prepare(ResearchTreeMinimapMode.ALWAYS, canvas, true);

        assertFalse(minimap.visible());
        assertFalse(minimap.contains(390, 230));
        assertEquals(List.of(), minimap.snapshot().markers());
    }

    @Test
    void finalizedPositionsBecomeBoundedStatusMarkersAndTrackedRouteEmphasis() {
        ResourceLocation learned = id("test:learned");
        ResourceLocation available = id("test:available");
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, learned, ResearchTreeGraph.Availability.LEARNED),
                        node(1, available, ResearchTreeGraph.Availability.AVAILABLE),
                        node(2, target, ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED, 1)),
                List.of(new ResearchTreeGraph.Edge(available, target)));
        ResearchTreeLayout layout = new ResearchTreeLayout(
                700,
                400,
                2,
                List.of(
                        positioned(0, learned, 0, 20, 330),
                        positioned(1, available, 0, 320, 330),
                        positioned(2, target, 1, 320, 20)));
        ResearchTreeCanvas canvas = canvas(420, 260);
        setContent(canvas, graph, layout);
        canvas.setTrackedPlan(ResearchTreePlanner.plan(graph, target, 100).orElseThrow());
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();

        minimap.prepare(ResearchTreeMinimapMode.ALWAYS, canvas, true);
        ResearchTreeMinimap.Snapshot snapshot = minimap.snapshot();

        assertEquals(3, snapshot.markers().size());
        assertTrue(snapshot.markers().stream().anyMatch(marker ->
                marker.kind() == ResearchTreeMinimap.MarkerKind.LEARNED));
        assertTrue(snapshot.markers().stream().anyMatch(marker ->
                marker.kind() == ResearchTreeMinimap.MarkerKind.AVAILABLE));
        assertTrue(snapshot.markers().stream().anyMatch(marker ->
                marker.kind() == ResearchTreeMinimap.MarkerKind.LOCKED));
        assertTrue(snapshot.markers().stream().filter(ResearchTreeMinimap.Marker::tracked).count()
                >= 2);
        assertEquals(1, snapshot.markers().stream()
                .filter(ResearchTreeMinimap.Marker::target).count());
        snapshot.markers().forEach(marker ->
                assertTrue(snapshot.contentBounds().overlaps(marker.bounds())));
        assertNotNull(snapshot.viewportBounds());
    }

    @Test
    void clickAndDragNavigateAtEverySupportedZoomWithoutEscapingTheCanvas() {
        ResearchTreeCanvas canvas = canvas(360, 220);
        setContent(canvas, independentGraph(4), layout(4, 2_000, 1_200));
        canvas.viewport().setAnimated(false);
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();
        minimap.prepare(ResearchTreeMinimapMode.ALWAYS, canvas, true);
        ResearchTreeScreenLayout.Rect content = minimap.snapshot().contentBounds();

        for (int zoomStep = 0; zoomStep < 7; zoomStep++) {
            canvas.viewport().zoomAt(1.0D, 180, 110);
            assertTrue(minimap.beginNavigation(
                    content.x(), content.y(), ResearchTreeGestureTracker.LEFT_BUTTON,
                    canvas.viewport()));
            assertTrue(minimap.dragNavigation(
                    content.right(), content.bottom(), ResearchTreeGestureTracker.LEFT_BUTTON,
                    canvas.viewport()));
            assertTrue(minimap.endNavigation(ResearchTreeGestureTracker.LEFT_BUTTON));
            ResearchTreeViewport.CanvasBounds visible = canvas.viewport().visibleCanvasBounds();
            assertTrue(visible.x() >= 0.0D && visible.y() >= 0.0D);
            assertTrue(visible.x() + visible.width() <= 2_000.0D);
            assertTrue(visible.y() + visible.height() <= 1_200.0D);
        }
    }

    @Test
    void maximumPublishedTreeProducesOnlyBoundedLightweightMarkers() {
        int count = ResearchTreeGraph.MAX_NODES;
        ResearchTreeCanvas canvas = canvas(640, 360);
        setContent(canvas, independentGraph(count), gridLayout(count));
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();

        minimap.prepare(ResearchTreeMinimapMode.ALWAYS, canvas, true);
        ResearchTreeMinimap.Snapshot snapshot = minimap.snapshot();

        assertEquals(count, snapshot.markers().size());
        assertTrue(snapshot.panelBounds().width() <= 168);
        assertTrue(snapshot.panelBounds().height() <= 112);
        snapshot.markers().forEach(marker -> {
            assertTrue(marker.bounds().x() >= snapshot.contentBounds().x());
            assertTrue(marker.bounds().y() >= snapshot.contentBounds().y());
            assertTrue(marker.bounds().right() <= snapshot.contentBounds().right());
            assertTrue(marker.bounds().bottom() <= snapshot.contentBounds().bottom());
        });
    }

    @Test
    void persistentBottomOverlayMovesTheMinimapToAFreeArea() {
        ResearchTreeCanvas canvas = canvas(320, 240);
        setContent(canvas, independentGraph(4), layout(4, 1_000, 700));
        ResearchTreeScreenLayout.Rect coachmark =
                new ResearchTreeScreenLayout.Rect(205, 160, 105, 70);
        ResearchTreeMinimap minimap = new ResearchTreeMinimap();

        minimap.prepare(
                ResearchTreeMinimapMode.ALWAYS, canvas, true, List.of(coachmark));

        assertTrue(minimap.visible());
        assertFalse(minimap.panelBounds().overlaps(coachmark));
        assertTrue(minimap.panelBounds().bottom() <= coachmark.y() - 10);
    }

    private static ResearchTreeCanvas canvas(int width, int height) {
        ResearchTreeCanvas canvas = new ResearchTreeCanvas(
                new ResearchTreeViewState(),
                new ResearchTreeCanvas.Style(
                        1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24));
        canvas.setBounds(
                ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                new ResearchTreeScreenLayout.Rect(0, 0, width, height));
        return canvas;
    }

    private static void setContent(
            ResearchTreeCanvas canvas,
            ResearchTreeGraph graph,
            ResearchTreeLayout layout) {
        canvas.setContent(graph, layout, Map.of(), null);
    }

    private static ResearchTreeGraph independentGraph(int count) {
        ArrayList<ResearchTreeGraph.Node> nodes = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            nodes.add(node(ordinal, id("test:node_" + ordinal),
                    ResearchTreeGraph.Availability.AVAILABLE));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ResearchTreeLayout layout(int count, int width, int height) {
        ArrayList<ResearchTreeLayout.PositionedNode> nodes = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int x = count == 1 ? Math.max(0, (width - ResearchTreeLayout.NODE_WIDTH) / 2)
                    : ordinal * (width - ResearchTreeLayout.NODE_WIDTH) / (count - 1);
            nodes.add(positioned(ordinal, id("test:node_" + ordinal), 0, x,
                    Math.max(0, height - ResearchTreeLayout.NODE_HEIGHT)));
        }
        return new ResearchTreeLayout(width, height, 1, nodes);
    }

    private static ResearchTreeLayout gridLayout(int count) {
        int columns = 64;
        int rows = (count + columns - 1) / columns;
        ArrayList<ResearchTreeLayout.PositionedNode> nodes = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int row = ordinal / columns;
            int column = ordinal % columns;
            nodes.add(positioned(
                    ordinal,
                    id("test:node_" + ordinal),
                    rows - 1 - row,
                    column * 30,
                    row * 30));
        }
        return new ResearchTreeLayout(
                columns * 30,
                rows * 30,
                rows,
                nodes);
    }

    private static ResearchTreeLayout.PositionedNode positioned(
            int ordinal,
            ResourceLocation id,
            int tier,
            int x,
            int y) {
        return new ResearchTreeLayout.PositionedNode(
                ordinal, id, 0, tier, ordinal, x, y);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            ResearchTreeGraph.Availability availability) {
        return node(ordinal, id, availability, 0);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            ResearchTreeGraph.Availability availability,
            int prerequisites) {
        boolean learned = availability == ResearchTreeGraph.Availability.LEARNED;
        boolean available = availability == ResearchTreeGraph.Availability.AVAILABLE;
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name.test." + ordinal,
                "gun",
                id("test:slot_" + ordinal),
                JournalVisibility.FULL,
                learned,
                true,
                available,
                5,
                0,
                prerequisites,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
