package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

/** Measured inspector layout with a minimum readable scrolling area. */
public record ResearchTreeWorkbenchLayout(ResearchTreeFullscreenLayout.Layout overlays, Rect header,
        Rect canvas, Rect inspector, boolean inspectorVisible, Rect inspectorContent, Rect action, Rect track,
        Rect target, Rect branch, Mode inspectorMode, boolean inspectorOverlay, Rect summary, Rect body,
        Rect back, Rect toggle, Rect review, java.util.Optional<Rect> journal) {
    public enum Mode { CLOSED, COMPACT, EXPANDED }
    public static ResearchTreeWorkbenchLayout create(int width, int height) { return create(width, height, true); }
    public static ResearchTreeWorkbenchLayout create(int width, int height, boolean selected) {
        return create(width, height, selected, null, 11, 9);
    }
    public static int panelWidth(int width) { return Math.min(260, Math.max(218, width / 3)); }
    public static ResearchTreeWorkbenchLayout create(int width, int height, boolean selected,
            Boolean expanded, int measuredSummaryHeight, int fontHeight) {
        return create(width, height, selected, expanded, measuredSummaryHeight, fontHeight, false);
    }
    public static ResearchTreeWorkbenchLayout create(int width, int height, boolean selected,
            Boolean expanded, int measuredSummaryHeight, int fontHeight, boolean portable) {
        var overlays = ResearchTreeFullscreenLayout.forScreen(width, height);
        int journalSpace = portable ? 24 : 0;
        var journal = portable ? java.util.Optional.of(new Rect(overlays.close().x() - 24,
                overlays.close().y(), 20, 20)) : java.util.Optional.<Rect>empty();
        var search = overlays.searchField();
        overlays = new ResearchTreeFullscreenLayout.Layout(width, height, overlays.canvas(),
                overlays.edgeReveal(), overlays.edgeRevealHitTarget(), overlays.rail(), overlays.searchButton(),
                new Rect(search.x(), search.y(), Math.min(search.width(), overlays.close().x() - 152 - journalSpace - search.x()), search.height()),
                overlays.close(), overlays.coachmark(), overlays.safeFocus());
        int x = overlays.rail().right() + 8, top = 48, bottom = height - 8;
        int titleHeight = Math.max(24, 2 * fontHeight + 4);
        int minimumBody = 6 * (fontHeight + 4);
        int summaryHeight = fontHeight + 2;
        int iw = panelWidth(width);
        boolean canDock = width - x - iw - 16 >= 240
                && bottom - top >= titleHeight + summaryHeight + minimumBody + 52;
        Mode mode = !selected ? Mode.CLOSED : (expanded == null || expanded) ? Mode.EXPANDED : Mode.COMPACT;
        boolean overlay = mode == Mode.EXPANDED && !canDock;
        Rect canvas = new Rect(x, top, width - x - 8, bottom - top);
        Rect inspector;
        if (mode == Mode.EXPANDED) {
            inspector = overlay ? canvas : new Rect(width - iw - 8, top, iw, bottom - top);
            if (!overlay) canvas = new Rect(x, top, inspector.x() - x - 8, bottom - top);
        } else {
            inspector = new Rect(x, bottom - 40, width - x - 8, 40);
            if (mode == Mode.COMPACT) canvas = new Rect(x, top, width - x - 8, inspector.y() - top - 6);
        }
        int row = inspector.bottom() - 26;
        Rect content = new Rect(inspector.x() + 8, inspector.y() + 8, inspector.width() - 16,
                mode == Mode.EXPANDED ? row - inspector.y() - 12 : 24);
        int actualSummary = Math.min(summaryHeight,
                Math.max(fontHeight, inspector.height() - titleHeight - minimumBody - 52));
        Rect summary = new Rect(content.x(), content.y() + titleHeight + 4, content.width(), actualSummary);
        Rect body = mode == Mode.EXPANDED
                ? new Rect(content.x(), summary.bottom() + 4, content.width(), row - 4 - summary.bottom() - 4)
                : new Rect(content.x(), content.y(), 1, 1);
        Rect review = new Rect(content.right() - 48, content.y(), 48, 20);
        Rect back = new Rect(inspector.x() + 8, row, 36, 20);
        Rect action = new Rect(inspector.x() + 48, row, inspector.width() - 144, 20);
        Rect track = new Rect(inspector.right() - 92, row, 20, 20);
        Rect target = new Rect(inspector.right() - 68, row, 36, 20);
        Rect toggle = mode == Mode.EXPANDED ? new Rect(inspector.right() - 28, row, 20, 20)
                : new Rect(inspector.right() - 78, inspector.y() + 10, 70, 20);
        if (mode != Mode.EXPANDED) back = new Rect(inspector.right() - 118, inspector.y() + 10, 36, 20);
        return new ResearchTreeWorkbenchLayout(overlays, new Rect(x, 30, width - x - 8, 14), canvas,
                inspector, mode != Mode.CLOSED, content, action, track, target,
                new Rect(overlays.close().x() - 124 - journalSpace, overlays.close().y(), 120, 20), mode, overlay,
                summary, body, back, toggle, review, journal);
    }
    public Rect targetClearance() {
        int w = Math.min(canvas.width(), 104), h = Math.min(canvas.height(), 52);
        return new Rect(canvas.x() + (canvas.width() - w) / 2, canvas.y() + (canvas.height() - h) / 2, w, h);
    }
    public ResearchTreeWorkbenchLayout {
        if (journal == null || branch.overlaps(overlays.close()) || branch.overlaps(overlays.searchField())
                || journal.filter(j -> j.overlaps(branch) || j.overlaps(overlays.close())
                    || j.overlaps(overlays.searchField())).isPresent()
                || canvas.overlaps(header) || inspector.overlaps(header)
                || inspectorVisible && !inspectorOverlay && canvas.overlaps(inspector)
                || !inspector.contains(inspectorContent) || !inspector.contains(back) || !inspector.contains(toggle)
                || inspectorMode == Mode.EXPANDED && (!inspector.contains(body) || !inspector.contains(summary)
                    || !inspector.contains(review) || !inspector.contains(action) || !inspector.contains(track) || !inspector.contains(target)))
            throw new IllegalArgumentException("conflicting workbench rectangles");
    }
    public ResearchTreeGuidanceLayout.Guide help() {
        int w = Math.min(360, overlays.screenWidth() - 48), h = Math.min(240, overlays.screenHeight() - 48);
        var panel = new Rect((overlays.screenWidth()-w)/2, (overlays.screenHeight()-h)/2, w, h);
        return new ResearchTreeGuidanceLayout.Guide(panel,new Rect(panel.right()-62,panel.bottom()-24,54,18));
    }
}
