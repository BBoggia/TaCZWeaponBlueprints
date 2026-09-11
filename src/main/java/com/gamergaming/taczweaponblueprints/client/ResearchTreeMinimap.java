package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Lightweight navigation projection of one finalized Research Tree layout.
 * It never recomputes topology or renders scaled node cards.
 */
final class ResearchTreeMinimap {
    static final double AUTOMATIC_SHOW_COVERAGE = 0.80D;
    static final double AUTOMATIC_HIDE_COVERAGE = 0.92D;
    private static final int OUTER_MARGIN = 10;
    private static final int CONTENT_PADDING = 5;
    private static final int MIN_PANEL_WIDTH = 48;
    private static final int MIN_PANEL_HEIGHT = 36;
    private static final int MAX_PANEL_WIDTH = 168;
    private static final int MAX_PANEL_HEIGHT = 112;
    private static final int PANEL_COLOR = 0xD8111820;
    private static final int CONTENT_COLOR = 0xC00B0F14;
    private static final int BORDER_COLOR = 0xFF68798C;
    private static final int VIEWPORT_COLOR = 0xFFE8EDF2;
    private static final int LEARNED_COLOR = 0xFF70C98B;
    private static final int AVAILABLE_COLOR = 0xFFE4C56A;
    private static final int LOCKED_COLOR = 0xFFFFA45C;
    private static final int HIDDEN_COLOR = 0xFF536476;
    private static final int ROUTE_COLOR = 0xFF62C7D9;
    private static final int TARGET_COLOR = 0xFF62C7D9;

    private ResearchTreeMinimapMode preparedMode;
    private ResearchTreeGraph preparedGraph;
    private ResearchTreeLayout preparedLayout;
    private ResearchTreeScreenLayout.Rect preparedCanvas;
    private List<ResearchTreeScreenLayout.Rect> preparedObstacles = List.of();
    private long preparedTrackedPlanRevision = Long.MIN_VALUE;
    private ResearchTreeScreenLayout.Rect panelBounds;
    private ResearchTreeScreenLayout.Rect contentBounds;
    private List<Marker> markers = List.of();
    private List<ResearchTreeScreenLayout.Rect> regions = List.of();
    private ResearchTreeScreenLayout.Rect viewportBounds;
    private double canvasToMinimapScale;
    private boolean visible;
    private boolean navigating;

    void prepare(
            ResearchTreeMinimapMode mode,
            ResearchTreeCanvas canvas,
            boolean fullscreen) {
        prepare(mode, canvas, fullscreen, List.of());
    }

    void prepare(
            ResearchTreeMinimapMode mode,
            ResearchTreeCanvas canvas,
            boolean fullscreen,
            List<ResearchTreeScreenLayout.Rect> obstacles) {
        if (mode == null || canvas == null) {
            throw new IllegalArgumentException("Research Tree minimap input cannot be null");
        }
        List<ResearchTreeScreenLayout.Rect> placementObstacles = obstacles == null
                ? List.of()
                : obstacles.stream().filter(java.util.Objects::nonNull).toList();
        ResearchTreeGraph graph = canvas.graph();
        ResearchTreeLayout layout = canvas.layout();
        ResearchTreeScreenLayout.Rect canvasBounds = canvas.bounds();
        // Camera movement and changing overlays must not move the navigation surface
        // under a held pointer. Structural replacement/resize and explicit hiding cancel it.
        if (navigating && fullscreen && mode != ResearchTreeMinimapMode.HIDDEN
                && graph == preparedGraph && layout == preparedLayout
                && canvasBounds.equals(preparedCanvas)) return;
        long trackedRevision = canvas.trackedPlanRevision();
        if (mode == preparedMode
                && graph == preparedGraph
                && layout == preparedLayout
                && canvasBounds.equals(preparedCanvas)
                && placementObstacles.equals(preparedObstacles)
                && trackedRevision == preparedTrackedPlanRevision
                && visible == shouldShow(mode, graph, layout, canvas.viewport(), fullscreen)) {
            return;
        }
        boolean nextVisible = shouldShow(mode, graph, layout, canvas.viewport(), fullscreen);
        preparedMode = mode;
        preparedGraph = graph;
        preparedLayout = layout;
        preparedCanvas = canvasBounds;
        preparedObstacles = List.copyOf(placementObstacles);
        preparedTrackedPlanRevision = trackedRevision;
        visible = nextVisible;
        navigating = false;
        viewportBounds = null;
        if (!visible || !createBounds(canvasBounds, placementObstacles)) {
            visible = false;
            panelBounds = null;
            contentBounds = null;
            markers = List.of();
            regions = List.of();
            return;
        }

        canvasToMinimapScale = Math.min(
                contentBounds.width() / (double) layout.width(),
                contentBounds.height() / (double) layout.height());
        int projectedWidth = Math.max(1, (int) Math.round(layout.width() * canvasToMinimapScale));
        int projectedHeight = Math.max(1, (int) Math.round(layout.height() * canvasToMinimapScale));
        contentBounds = new ResearchTreeScreenLayout.Rect(
                contentBounds.x() + (contentBounds.width() - projectedWidth) / 2,
                contentBounds.y() + (contentBounds.height() - projectedHeight) / 2,
                projectedWidth,
                projectedHeight);

        ArrayList<Marker> nextMarkers = new ArrayList<>(layout.nodes().size());
        var trackedTarget = canvas.trackedTargetId().orElse(null);
        for (ResearchTreeLayout.PositionedNode positioned : layout.nodes()) {
            ResearchTreeGraph.Node node = graph.node(positioned.blueprintId()).orElse(null);
            if (node == null) {
                continue;
            }
            int centerX = projectX(positioned.centerX());
            int centerY = projectY(positioned.centerY());
            boolean tracked = canvas.isTrackedPathNode(positioned.blueprintId());
            int markerSize = Math.min(tracked ? 3 : 2, Math.min(contentBounds.width(), contentBounds.height()));
            int x = clamp(centerX - markerSize / 2,
                    contentBounds.x(), contentBounds.right() - markerSize);
            int y = clamp(centerY - markerSize / 2,
                    contentBounds.y(), contentBounds.bottom() - markerSize);
            nextMarkers.add(new Marker(
                    new ResearchTreeScreenLayout.Rect(x, y, markerSize, markerSize),
                    markerKind(node),
                    tracked,
                    positioned.blueprintId().equals(trackedTarget)));
        }
        markers = List.copyOf(nextMarkers);
        regions = canvas.scene().map(scene -> scene.regions().stream().map(region -> {
            var b = region.bounds();
            return new ResearchTreeScreenLayout.Rect(projectX(b.x() - scene.extent().x()), projectY(b.y() - scene.extent().y()),
                    Math.max(1, (int) Math.round(b.width() * canvasToMinimapScale)),
                    Math.max(1, (int) Math.round(b.height() * canvasToMinimapScale)));
        }).toList()).orElse(List.of());
        updateViewport(canvas.viewport());
    }

    void updateViewport(ResearchTreeViewport viewport) {
        if (!visible || viewport == null || contentBounds == null) {
            viewportBounds = null;
            return;
        }
        ResearchTreeViewport.CanvasBounds camera = viewport.visibleCanvasBounds();
        int left = clamp(projectX(camera.x()), contentBounds.x(), contentBounds.right() - 1);
        int top = clamp(projectY(camera.y()), contentBounds.y(), contentBounds.bottom() - 1);
        int right = clamp(projectX(camera.x() + camera.width()), left + 1, contentBounds.right());
        int bottom = clamp(projectY(camera.y() + camera.height()), top + 1, contentBounds.bottom());
        viewportBounds = new ResearchTreeScreenLayout.Rect(
                left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    void render(GuiGraphics graphics) {
        if (!visible || panelBounds == null || contentBounds == null) {
            return;
        }
        // Markers share one primitive layer; avoid a native flush for every node.
        graphics.drawManaged(() -> {
            graphics.fill(
                    panelBounds.x(), panelBounds.y(), panelBounds.right(), panelBounds.bottom(),
                    PANEL_COLOR);
            graphics.renderOutline(
                    panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(),
                    BORDER_COLOR);
            graphics.fill(
                    contentBounds.x(), contentBounds.y(), contentBounds.right(), contentBounds.bottom(),
                    CONTENT_COLOR);
            for (var region : regions) graphics.renderOutline(region.x(), region.y(), region.width(), region.height(), 0xff354653);
            for (Marker marker : markers) {
                ResearchTreeScreenLayout.Rect bounds = marker.bounds();
                graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                        markerColor(marker.kind()));
                if (marker.tracked()) {
                    graphics.renderOutline(
                            bounds.x(), bounds.y(), bounds.width(), bounds.height(), ROUTE_COLOR);
                }
            }
            if (viewportBounds != null) {
                graphics.renderOutline(
                        viewportBounds.x(),
                        viewportBounds.y(),
                        viewportBounds.width(),
                        viewportBounds.height(),
                        VIEWPORT_COLOR);
            }
            for (Marker marker : markers) if (marker.target()) {
                var b = marker.bounds();
                graphics.renderOutline(b.x() - 1, b.y() - 1, b.width() + 2, b.height() + 2, TARGET_COLOR);
                graphics.fill(b.x() + b.width() / 2, b.y() - 3,
                        b.x() + b.width() / 2 + 1, b.y() - 1, TARGET_COLOR);
            }
        });
    }

    boolean contains(double mouseX, double mouseY) {
        return visible && panelBounds != null && panelBounds.contains(mouseX, mouseY);
    }

    boolean beginNavigation(double mouseX, double mouseY, int button, ResearchTreeViewport viewport) {
        if (button != ResearchTreeGestureTracker.LEFT_BUTTON || !contains(mouseX, mouseY)) {
            return false;
        }
        navigating = true;
        navigate(mouseX, mouseY, viewport);
        return true;
    }

    boolean dragNavigation(double mouseX, double mouseY, int button, ResearchTreeViewport viewport) {
        if (!navigating || button != ResearchTreeGestureTracker.LEFT_BUTTON) {
            return false;
        }
        navigate(mouseX, mouseY, viewport);
        return true;
    }

    boolean endNavigation(int button) {
        if (!navigating || button != ResearchTreeGestureTracker.LEFT_BUTTON) {
            return false;
        }
        navigating = false;
        return true;
    }

    void cancelNavigation() {
        navigating = false;
    }

    boolean navigating() {
        return navigating;
    }

    boolean visible() {
        return visible;
    }

    ResearchTreeScreenLayout.Rect panelBounds() {
        return panelBounds;
    }

    Snapshot snapshot() {
        return new Snapshot(visible, panelBounds, contentBounds, markers, viewportBounds);
    }

    private boolean createBounds(
            ResearchTreeScreenLayout.Rect canvas,
            List<ResearchTreeScreenLayout.Rect> obstacles) {
        int availableWidth = canvas.width() - OUTER_MARGIN * 2;
        int availableHeight = canvas.height() - OUTER_MARGIN * 2;
        if (availableWidth < CONTENT_PADDING * 2 + 8
                || availableHeight < CONTENT_PADDING * 2 + 8) {
            return false;
        }
        int width = Math.min(
                availableWidth,
                Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, canvas.width() / 5)));
        int height = Math.min(
                availableHeight,
                Math.min(MAX_PANEL_HEIGHT, Math.max(MIN_PANEL_HEIGHT, canvas.height() / 5)));
        // Shrink the outer frame too; retain a small usable hit area for extreme shapes.
        double fit = Math.min((width - CONTENT_PADDING * 2) / (double) preparedLayout.width(),
                (height - CONTENT_PADDING * 2) / (double) preparedLayout.height());
        width = Math.min(width, Math.max(MIN_PANEL_WIDTH,
                (int) Math.round(preparedLayout.width() * fit) + CONTENT_PADDING * 2));
        height = Math.min(height, Math.max(MIN_PANEL_HEIGHT,
                (int) Math.round(preparedLayout.height() * fit) + CONTENT_PADDING * 2));
        panelBounds = placePanel(canvas, width, height, obstacles);
        if (panelBounds == null) {
            return false;
        }
        contentBounds = new ResearchTreeScreenLayout.Rect(
                panelBounds.x() + CONTENT_PADDING,
                panelBounds.y() + CONTENT_PADDING,
                panelBounds.width() - CONTENT_PADDING * 2,
                panelBounds.height() - CONTENT_PADDING * 2);
        return true;
    }

    private static ResearchTreeScreenLayout.Rect placePanel(
            ResearchTreeScreenLayout.Rect canvas,
            int width,
            int height,
            List<ResearchTreeScreenLayout.Rect> obstacles) {
        int rightX = canvas.right() - OUTER_MARGIN - width;
        ResearchTreeScreenLayout.Rect right = placeInColumn(
                canvas, rightX, width, height, obstacles);
        if (right != null) {
            return right;
        }
        int leftX = canvas.x() + OUTER_MARGIN;
        return leftX == rightX
                ? null
                : placeInColumn(canvas, leftX, width, height, obstacles);
    }

    private static ResearchTreeScreenLayout.Rect placeInColumn(
            ResearchTreeScreenLayout.Rect canvas,
            int x,
            int width,
            int height,
            List<ResearchTreeScreenLayout.Rect> obstacles) {
        int y = canvas.bottom() - OUTER_MARGIN - height;
        while (y >= canvas.y() + OUTER_MARGIN) {
            ResearchTreeScreenLayout.Rect candidate =
                    new ResearchTreeScreenLayout.Rect(x, y, width, height);
            ResearchTreeScreenLayout.Rect collision = obstacles.stream()
                    .filter(candidate::overlaps)
                    .max(java.util.Comparator.comparingInt(
                            ResearchTreeScreenLayout.Rect::y))
                    .orElse(null);
            if (collision == null) {
                return candidate;
            }
            y = collision.y() - OUTER_MARGIN - height;
        }
        return null;
    }

    private boolean shouldShow(
            ResearchTreeMinimapMode mode,
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            ResearchTreeViewport viewport,
            boolean fullscreen) {
        if (!fullscreen || mode == ResearchTreeMinimapMode.HIDDEN
                || graph.nodes().isEmpty() || layout.nodes().isEmpty()) {
            return false;
        }
        if (mode == ResearchTreeMinimapMode.ALWAYS) {
            return true;
        }
        ResearchTreeViewport.CanvasBounds camera = viewport.visibleCanvasBounds();
        double coverage = Math.min(1.0D, camera.width() / layout.width())
                * Math.min(1.0D, camera.height() / layout.height());
        return coverage < (visible && preparedMode == mode
                ? AUTOMATIC_HIDE_COVERAGE : AUTOMATIC_SHOW_COVERAGE);
    }

    private void navigate(double mouseX, double mouseY, ResearchTreeViewport viewport) {
        if (viewport == null || contentBounds == null || preparedLayout == null) {
            return;
        }
        double normalizedX = clamp(mouseX, contentBounds.x(), contentBounds.right())
                - contentBounds.x();
        double normalizedY = clamp(mouseY, contentBounds.y(), contentBounds.bottom())
                - contentBounds.y();
        double canvasX = normalizedX / contentBounds.width() * preparedLayout.width();
        double canvasY = normalizedY / contentBounds.height() * preparedLayout.height();
        viewport.focus(canvasX - 0.5D, canvasY - 0.5D, 1.0D, 1.0D);
    }

    private int projectX(double canvasX) {
        return contentBounds.x() + (int) Math.round(canvasX * canvasToMinimapScale);
    }

    private int projectY(double canvasY) {
        return contentBounds.y() + (int) Math.round(canvasY * canvasToMinimapScale);
    }

    private static MarkerKind markerKind(ResearchTreeGraph.Node node) {
        if (node.learned()) {
            return MarkerKind.LEARNED;
        }
        if (node.availability() == ResearchTreeGraph.Availability.AVAILABLE) {
            return MarkerKind.AVAILABLE;
        }
        if (node.availability() == ResearchTreeGraph.Availability.REDACTED
                || node.availability() == ResearchTreeGraph.Availability.PREVIEW) {
            return MarkerKind.HIDDEN;
        }
        return MarkerKind.LOCKED;
    }

    private static int markerColor(MarkerKind kind) {
        return switch (kind) {
            case LEARNED -> LEARNED_COLOR;
            case AVAILABLE -> AVAILABLE_COLOR;
            case LOCKED -> LOCKED_COLOR;
            case HIDDEN -> HIDDEN_COLOR;
        };
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    enum MarkerKind {
        LEARNED,
        AVAILABLE,
        LOCKED,
        HIDDEN
    }

    record Marker(
            ResearchTreeScreenLayout.Rect bounds,
            MarkerKind kind,
            boolean tracked,
            boolean target) {
    }

    record Snapshot(
            boolean visible,
            ResearchTreeScreenLayout.Rect panelBounds,
            ResearchTreeScreenLayout.Rect contentBounds,
            List<Marker> markers,
            ResearchTreeScreenLayout.Rect viewportBounds) {
    }
}
