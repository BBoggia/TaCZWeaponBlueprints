package com.gamergaming.taczweaponblueprints.client;

/**
 * Pure geometry contract for the edge-to-edge Research Tree presentation.
 * All controls intentionally overlay the full-screen canvas.
 */
public final class ResearchTreeFullscreenLayout {
    public static final int SCREEN_PADDING = 4;
    public static final int RAIL_WIDTH = 24;
    public static final int EDGE_REVEAL_WIDTH = 4;
    public static final int CONTROL_SIZE = 20;
    public static final int OVERLAY_GAP = 4;
    public static final int COACHMARK_HEIGHT = 24;
    public static final int MAX_SEARCH_WIDTH = 200;
    public static final int MAX_COACHMARK_WIDTH = 210;
    private static final int MIN_SEARCH_WIDTH = 96;
    private static final int SAFE_GAP = 8;

    private ResearchTreeFullscreenLayout() {
    }

    public static Layout forScreen(int screenWidth, int screenHeight) {
        if (screenWidth < ResearchTreeScreenLayout.MIN_FULLSCREEN_WIDTH
                || screenHeight < ResearchTreeScreenLayout.MIN_FULLSCREEN_HEIGHT) {
            throw new IllegalArgumentException("fullscreen Research Tree bounds are too small");
        }
        ResearchTreeScreenLayout.Rect canvas = new ResearchTreeScreenLayout.Rect(
                0, 0, screenWidth, screenHeight);
        ResearchTreeScreenLayout.Rect edgeReveal = new ResearchTreeScreenLayout.Rect(
                0, 0, EDGE_REVEAL_WIDTH, screenHeight);
        ResearchTreeScreenLayout.Rect rail = new ResearchTreeScreenLayout.Rect(
                SCREEN_PADDING,
                SCREEN_PADDING,
                RAIL_WIDTH,
                screenHeight - SCREEN_PADDING * 2);
        ResearchTreeScreenLayout.Rect searchButton = new ResearchTreeScreenLayout.Rect(
                rail.x() + (rail.width() - CONTROL_SIZE) / 2,
                rail.y() + 2,
                CONTROL_SIZE,
                CONTROL_SIZE);
        ResearchTreeScreenLayout.Rect close = new ResearchTreeScreenLayout.Rect(
                screenWidth - SCREEN_PADDING - CONTROL_SIZE,
                SCREEN_PADDING,
                CONTROL_SIZE,
                CONTROL_SIZE);
        int searchX = rail.right() + OVERLAY_GAP;
        int searchAvailable = close.x() - OVERLAY_GAP - searchX;
        int searchWidth = Math.min(MAX_SEARCH_WIDTH, searchAvailable);
        if (searchWidth < MIN_SEARCH_WIDTH) {
            throw new IllegalArgumentException("fullscreen Research Tree search has no usable width");
        }
        ResearchTreeScreenLayout.Rect searchField = new ResearchTreeScreenLayout.Rect(
                searchX,
                SCREEN_PADDING,
                searchWidth,
                CONTROL_SIZE);

        int coachmarkX = rail.right() + OVERLAY_GAP;
        int coachmarkWidth = Math.min(
                MAX_COACHMARK_WIDTH,
                screenWidth - coachmarkX - SCREEN_PADDING);
        ResearchTreeScreenLayout.Rect coachmark = new ResearchTreeScreenLayout.Rect(
                coachmarkX,
                screenHeight - SCREEN_PADDING - COACHMARK_HEIGHT,
                coachmarkWidth,
                COACHMARK_HEIGHT);

        int safeX = rail.right() + SAFE_GAP;
        int safeY = Math.max(searchField.bottom(), close.bottom()) + SAFE_GAP;
        int safeRight = close.x() - SAFE_GAP;
        int safeBottom = screenHeight - SAFE_GAP;
        ResearchTreeScreenLayout.Rect safeFocus = new ResearchTreeScreenLayout.Rect(
                safeX,
                safeY,
                safeRight - safeX,
                safeBottom - safeY);
        return new Layout(
                screenWidth,
                screenHeight,
                canvas,
                edgeReveal,
                rail,
                searchButton,
                searchField,
                close,
                coachmark,
                safeFocus);
    }

    public record Layout(
            int screenWidth,
            int screenHeight,
            ResearchTreeScreenLayout.Rect canvas,
            ResearchTreeScreenLayout.Rect edgeReveal,
            ResearchTreeScreenLayout.Rect rail,
            ResearchTreeScreenLayout.Rect searchButton,
            ResearchTreeScreenLayout.Rect searchField,
            ResearchTreeScreenLayout.Rect close,
            ResearchTreeScreenLayout.Rect coachmark,
            ResearchTreeScreenLayout.Rect safeFocus) {
        public Layout {
            if (screenWidth <= 0 || screenHeight <= 0
                    || canvas == null || edgeReveal == null || rail == null
                    || searchButton == null || searchField == null || close == null
                    || coachmark == null || safeFocus == null) {
                throw new IllegalArgumentException("invalid fullscreen Research Tree layout");
            }
            if (!canvas.equals(new ResearchTreeScreenLayout.Rect(
                    0, 0, screenWidth, screenHeight))) {
                throw new IllegalArgumentException("fullscreen Research Tree canvas is not edge-to-edge");
            }
            for (ResearchTreeScreenLayout.Rect overlay : java.util.List.of(
                    edgeReveal, rail, searchButton, searchField, close, coachmark, safeFocus)) {
                if (!overlay.inside(screenWidth, screenHeight)) {
                    throw new IllegalArgumentException("fullscreen Research Tree overlay is offscreen");
                }
            }
            if (!rail.contains(searchButton)
                    || rail.overlaps(searchField)
                    || rail.overlaps(close)
                    || searchField.overlaps(close)
                    || coachmark.overlaps(rail)
                    || safeFocus.overlaps(rail)
                    || safeFocus.overlaps(close)) {
                throw new IllegalArgumentException("fullscreen Research Tree overlays conflict");
            }
        }
    }
}
