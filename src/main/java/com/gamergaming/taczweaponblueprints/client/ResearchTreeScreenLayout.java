package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

/**
 * Pure responsive geometry contract shared by the compact Research Bench tree
 * and its fullscreen presentation.
 */
public final class ResearchTreeScreenLayout {
    public static final int COMPACT_WIDTH = 310;
    public static final int COMPACT_HEIGHT = 240;
    /** Minimum bounds that can contain the rail, search, coachmark, and a full action card. */
    public static final int MIN_FULLSCREEN_WIDTH = 320;
    public static final int MIN_FULLSCREEN_HEIGHT = 240;
    public static final int PADDING = 8;
    public static final int TOOLBAR_HEIGHT = 20;
    public static final int SIDEBAR_WIDTH = 96;

    private static final int CONTROL_GAP = 2;
    private static final int ZOOM_WIDTH = 20;
    private static final int ACTION_WIDTH = 48;
    private static final int EXPAND_WIDTH = 22;
    private static final int BROWSE_VIEW_WIDTH = 22;
    private static final int MIN_SEARCH_WIDTH = 48;
    private static final Rect COMPACT_TOOLBAR = new Rect(8, 43, 294, 18);
    private static final Rect COMPACT_CANVAS = new Rect(8, 64, 294, 116);
    private static final Rect COMPACT_DETAILS = new Rect(8, 183, 294, 44);
    private static final Layout COMPACT = create(
            ViewMode.COMPACT,
            COMPACT_WIDTH,
            COMPACT_HEIGHT,
            DetailsPlacement.BOTTOM,
            COMPACT_TOOLBAR,
            Optional.empty(),
            COMPACT_CANVAS,
            COMPACT_DETAILS);

    private ResearchTreeScreenLayout() {
    }

    public static Layout compact() {
        return COMPACT;
    }

    public static Layout fullscreen(int screenWidth, int screenHeight, boolean ignoredDetailsExpanded) {
        if (screenWidth < MIN_FULLSCREEN_WIDTH || screenHeight < MIN_FULLSCREEN_HEIGHT) {
            throw new IllegalArgumentException("fullscreen Research Tree bounds are too small");
        }
        ResearchTreeFullscreenLayout.Layout fullscreenLayout =
                ResearchTreeFullscreenLayout.forScreen(screenWidth, screenHeight);
        Rect toolbar = new Rect(
                PADDING,
                PADDING,
                screenWidth - PADDING * 2,
                TOOLBAR_HEIGHT);
        int contentY = toolbar.bottom() + 4;
        Rect sidebar = new Rect(
                PADDING,
                contentY,
                SIDEBAR_WIDTH,
                screenHeight - contentY - PADDING);
        // Phase-one controls still use the legacy toolbar/sidebar geometry, but
        // they are overlays now. The graph owns every screen pixel underneath.
        Rect canvas = fullscreenLayout.canvas();
        // Fullscreen details are contextual tooltips. Retain a tiny valid region
        // for the shared layout contract instead of reserving permanent space.
        Rect details = new Rect(PADDING, screenHeight - PADDING - 1, 1, 1);
        return create(
                ViewMode.FULLSCREEN,
                screenWidth,
                screenHeight,
                DetailsPlacement.OVERLAY,
                toolbar,
                Optional.of(sidebar),
                canvas,
                details);
    }

    private static Layout create(
            ViewMode mode,
            int screenWidth,
            int screenHeight,
            DetailsPlacement detailsPlacement,
            Rect toolbar,
            Optional<Rect> sidebar,
            Rect canvas,
            Rect details) {
        ToolbarControls controls = toolbarControls(toolbar);
        return new Layout(
                mode,
                screenWidth,
                screenHeight,
                detailsPlacement,
                toolbar,
                sidebar,
                controls.search(),
                controls.zoomOut(),
                controls.zoomIn(),
                controls.showAll(),
                controls.browseView(),
                controls.groupSelector(),
                controls.expand(),
                canvas,
                details);
    }

    private static ToolbarControls toolbarControls(Rect toolbar) {
        int right = toolbar.right();
        Rect expand = new Rect(right - EXPAND_WIDTH, toolbar.y(), EXPAND_WIDTH, toolbar.height());
        right = expand.x() - CONTROL_GAP;
        Rect groupSelector = new Rect(right - ACTION_WIDTH, toolbar.y(), ACTION_WIDTH, toolbar.height());
        right = groupSelector.x() - CONTROL_GAP;
        Rect browseView = new Rect(
                right - BROWSE_VIEW_WIDTH, toolbar.y(), BROWSE_VIEW_WIDTH, toolbar.height());
        right = browseView.x() - CONTROL_GAP;
        Rect showAll = new Rect(right - ACTION_WIDTH, toolbar.y(), ACTION_WIDTH, toolbar.height());
        right = showAll.x() - CONTROL_GAP;
        Rect zoomIn = new Rect(right - ZOOM_WIDTH, toolbar.y(), ZOOM_WIDTH, toolbar.height());
        right = zoomIn.x() - CONTROL_GAP;
        Rect zoomOut = new Rect(right - ZOOM_WIDTH, toolbar.y(), ZOOM_WIDTH, toolbar.height());
        right = zoomOut.x() - CONTROL_GAP;
        Rect search = new Rect(toolbar.x(), toolbar.y(), right - toolbar.x(), toolbar.height());
        if (search.width() < MIN_SEARCH_WIDTH) {
            throw new IllegalArgumentException("Research Tree toolbar has no usable search area");
        }
        return new ToolbarControls(
                search, zoomOut, zoomIn, showAll, browseView, groupSelector, expand);
    }

    public record Layout(
            ViewMode mode,
            int screenWidth,
            int screenHeight,
            DetailsPlacement detailsPlacement,
            Rect toolbar,
            Optional<Rect> sidebar,
            Rect search,
            Rect zoomOut,
            Rect zoomIn,
            Rect showAll,
            Rect browseView,
            Rect groupSelector,
            Rect expand,
            Rect canvas,
            Rect details) {
        public Layout {
            if (mode == null || detailsPlacement == null || screenWidth <= 0 || screenHeight <= 0) {
                throw new IllegalArgumentException("invalid Research Tree screen layout identity");
            }
            sidebar = sidebar == null ? Optional.empty() : sidebar;
            List<Rect> regions = List.of(
                    toolbar, search, zoomOut, zoomIn, showAll, browseView,
                    groupSelector, expand, canvas, details);
            if (regions.stream().anyMatch(region -> region == null
                    || !region.inside(screenWidth, screenHeight))) {
                throw new IllegalArgumentException("Research Tree region lies outside its screen");
            }
            List<Rect> controls = List.of(
                    search, zoomOut, zoomIn, showAll, browseView, groupSelector, expand);
            for (int left = 0; left < controls.size(); left++) {
                if (!toolbar.contains(controls.get(left))) {
                    throw new IllegalArgumentException("Research Tree control lies outside its toolbar");
                }
                for (int right = left + 1; right < controls.size(); right++) {
                    if (controls.get(left).overlaps(controls.get(right))) {
                        throw new IllegalArgumentException("Research Tree toolbar controls overlap");
                    }
                }
            }
            if (sidebar.isPresent() && !sidebar.orElseThrow().inside(screenWidth, screenHeight)) {
                throw new IllegalArgumentException("Research Tree sidebar lies outside its screen");
            }
            if (detailsPlacement != DetailsPlacement.OVERLAY
                    && (canvas.overlaps(toolbar)
                            || details.overlaps(toolbar)
                            || canvas.overlaps(details))) {
                throw new IllegalArgumentException("Research Tree primary regions overlap");
            }
            if (sidebar.isPresent() && sidebar.orElseThrow().overlaps(toolbar)) {
                throw new IllegalArgumentException("Research Tree overlays overlap each other");
            }
            if (detailsPlacement != DetailsPlacement.OVERLAY
                    && sidebar.isPresent()
                    && sidebar.orElseThrow().overlaps(canvas)) {
                throw new IllegalArgumentException("Research Tree sidebar overlaps its canvas");
            }
            if (mode == ViewMode.FULLSCREEN
                    && !canvas.equals(new Rect(0, 0, screenWidth, screenHeight))) {
                throw new IllegalArgumentException("fullscreen Research Tree canvas is not edge-to-edge");
            }
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("invalid Research Tree rectangle");
            }
        }

        public int right() {
            return Math.addExact(x, width);
        }

        public int bottom() {
            return Math.addExact(y, height);
        }

        public boolean overlaps(Rect other) {
            return other != null
                    && x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }

        public boolean contains(Rect other) {
            return other != null
                    && other.x >= x && other.y >= y
                    && other.right() <= right() && other.bottom() <= bottom();
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right()
                    && pointY >= y && pointY < bottom();
        }

        public boolean inside(int outerWidth, int outerHeight) {
            return right() <= outerWidth && bottom() <= outerHeight;
        }
    }

    private record ToolbarControls(
            Rect search,
            Rect zoomOut,
            Rect zoomIn,
            Rect showAll,
            Rect browseView,
            Rect groupSelector,
            Rect expand) {
    }

    public enum ViewMode {
        COMPACT,
        FULLSCREEN
    }

    public enum DetailsPlacement {
        RIGHT,
        BOTTOM,
        DRAWER,
        OVERLAY
    }
}
