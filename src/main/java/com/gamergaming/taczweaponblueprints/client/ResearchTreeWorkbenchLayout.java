package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

/** Single owner of the persistent Tech Tree canvas, inspector, header and overlay space. */
public record ResearchTreeWorkbenchLayout(ResearchTreeFullscreenLayout.Layout overlays, Rect header,
        Rect canvas, Rect inspector, Rect inspectorContent, Rect action, Rect track, Rect target, Rect branch) {
    public static ResearchTreeWorkbenchLayout create(int width, int height) {
        var overlays = ResearchTreeFullscreenLayout.forScreen(width, height);
        // Leave room for the filter and Branch controls even at the minimum GUI size.
        var search = overlays.searchField();
        int searchWidth = Math.min(search.width(), overlays.close().x() - 78 - search.x());
        overlays = new ResearchTreeFullscreenLayout.Layout(width, height, overlays.canvas(),
                overlays.edgeReveal(), overlays.edgeRevealHitTarget(), overlays.rail(), overlays.searchButton(),
                new Rect(search.x(), search.y(), searchWidth, search.height()), overlays.close(),
                overlays.coachmark(), overlays.safeFocus());
        int x = overlays.rail().right() + 8, top = 48, bottom = height - 8;
        Rect canvas, inspector;
        if (width >= 420) {
            int iw = Math.min(218, Math.max(160, width * 36 / 100));
            inspector = new Rect(width - iw - 8, top, iw, bottom - top);
            canvas = new Rect(x, top, inspector.x() - x - 8, bottom - top);
        } else {
            int ih = Math.min(120, (bottom - top) / 2);
            inspector = new Rect(x, bottom - ih, width - x - 8, ih);
            canvas = new Rect(x, top, width - x - 8, inspector.y() - top - 6);
        }
        int row = inspector.bottom() - 26;
        return new ResearchTreeWorkbenchLayout(overlays, new Rect(x, 30, width - x - 8, 14), canvas, inspector,
                new Rect(inspector.x() + 8, inspector.y() + 8, inspector.width() - 16, row - inspector.y() - 12),
                new Rect(inspector.x() + 8, row, inspector.width() - 64, 20),
                new Rect(inspector.right() - 48, row, 20, 20), new Rect(inspector.right() - 24, row, 20, 20),
                new Rect(overlays.close().x() - 50, overlays.close().y(), 46, 20));
    }
    /** Keep the focus target clear; the minimap yields when no remaining corner fits. */
    public Rect targetClearance() {
        int w = Math.min(canvas.width(), 104), h = Math.min(canvas.height(), 52);
        return new Rect(canvas.x() + (canvas.width() - w) / 2,
                canvas.y() + (canvas.height() - h) / 2, w, h);
    }
    public ResearchTreeWorkbenchLayout {

        if (canvas.overlaps(inspector) || canvas.overlaps(header) || inspector.overlaps(header)
                || !inspector.contains(inspectorContent) || !inspector.contains(action)
                || !inspector.contains(track) || !inspector.contains(target)) throw new IllegalArgumentException("conflicting workbench rectangles");
    }

    public ResearchTreeGuidanceLayout.Guide help() {
        int w = Math.min(360, overlays.screenWidth() - 48), h = Math.min(240, overlays.screenHeight() - 48);
        var panel = new Rect((overlays.screenWidth()-w)/2, (overlays.screenHeight()-h)/2, w, h);
        return new ResearchTreeGuidanceLayout.Guide(panel,new Rect(panel.right()-62,panel.bottom()-24,54,18));
    }
}
