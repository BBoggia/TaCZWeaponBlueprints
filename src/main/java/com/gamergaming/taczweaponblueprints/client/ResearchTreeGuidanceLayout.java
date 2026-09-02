package com.gamergaming.taczweaponblueprints.client;

/** Pure responsive geometry for the dismissible three-step first-visit guide. */
public final class ResearchTreeGuidanceLayout {
    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 88;

    private ResearchTreeGuidanceLayout() {
    }

    public static Guide forLayout(ResearchTreeScreenLayout.Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Research Tree layout cannot be null");
        }
        ResearchTreeScreenLayout.Rect canvas = layout.canvas();
        int width = Math.min(PANEL_WIDTH, canvas.width() - 8);
        int height = Math.min(PANEL_HEIGHT, canvas.height());
        if (width < 120 || height < 84) {
            throw new IllegalArgumentException("Research Tree canvas cannot fit first-visit guidance");
        }
        ResearchTreeScreenLayout.Rect panel = new ResearchTreeScreenLayout.Rect(
                canvas.x() + 4,
                canvas.y() + Math.max(0, (canvas.height() - height) / 2),
                width,
                height);
        ResearchTreeScreenLayout.Rect dismiss = new ResearchTreeScreenLayout.Rect(
                panel.right() - 60,
                panel.bottom() - 22,
                54,
                18);
        return new Guide(panel, dismiss);
    }

    /** Uses the dedicated overlay lane instead of reusing compact canvas geometry. */
    public static Guide forFullscreen(ResearchTreeFullscreenLayout.Layout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("fullscreen Research Tree layout cannot be null");
        }
        ResearchTreeScreenLayout.Rect panel = layout.coachmark();
        int dismissWidth = Math.min(54, Math.max(36, panel.width() / 4));
        int dismissHeight = Math.min(18, panel.height() - 6);
        ResearchTreeScreenLayout.Rect dismiss = new ResearchTreeScreenLayout.Rect(
                panel.right() - dismissWidth - 3,
                panel.bottom() - dismissHeight - 3,
                dismissWidth,
                dismissHeight);
        return new Guide(panel, dismiss);
    }

    public record Guide(
            ResearchTreeScreenLayout.Rect panel,
            ResearchTreeScreenLayout.Rect dismiss) {
        public Guide {
            if (panel == null || dismiss == null || !panel.contains(dismiss)) {
                throw new IllegalArgumentException("invalid Research Tree guidance geometry");
            }
        }
    }
}
