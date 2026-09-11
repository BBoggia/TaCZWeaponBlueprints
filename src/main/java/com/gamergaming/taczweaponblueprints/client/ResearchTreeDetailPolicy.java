package com.gamergaming.taczweaponblueprints.client;

/** Screen-space detail thresholds in logical GUI pixels, with small transition buffers. */
final class ResearchTreeDetailPolicy {
    enum Detail { OVERVIEW, ICONS, BROWSE }

    record Channels(boolean artwork, boolean names, boolean stateMarks, boolean junctions) {
        static final Channels EMPTY = new Channels(false, false, false, false);
    }
    static Channels channels(Channels previous, double width, double height) {
        return new Channels(
                width >= (previous.artwork() ? 16 : 18) && height >= (previous.artwork() ? 7 : 8),
                width >= (previous.names() ? 72 : 76) && height >= (previous.names() ? 34 : 36),
                width >= (previous.stateMarks() ? 28 : 32) && height >= (previous.stateMarks() ? 12 : 14),
                height >= (previous.junctions() ? 26 : 28));
    }
    /** Artwork retains the same allocation as names enter or leave the view. */
    static ResearchTreeScene.Bounds artwork(ResearchTreeScene.Bounds body) {
        return new ResearchTreeScene.Bounds(body.x() + 5, body.y() + 6, body.width() - 10, 16);
    }

    static Detail resolve(Detail previous, double width, double height) {
        boolean labels = previous == Detail.BROWSE
                ? width >= 64 && height >= 28 : width >= 68 && height >= 30;
        if (labels) return Detail.BROWSE;
        boolean icons = previous == Detail.OVERVIEW
                ? width >= 18 && height >= 8 : width >= 16 && height >= 7;
        return icons ? Detail.ICONS : Detail.OVERVIEW;
    }

    static ResearchTreeScreenLayout.Rect hoverBounds(int screenWidth, int screenHeight,
            int mouseX, int mouseY, int contentHeight) {
        int width = Math.min(240, screenWidth - 16);
        int height = Math.min(contentHeight, screenHeight - 16);
        int x = mouseX + 12;
        if (x + width > screenWidth - 8) x = mouseX - width - 12;
        return new ResearchTreeScreenLayout.Rect(Math.max(8, Math.min(x, screenWidth - width - 8)),
                Math.max(8, Math.min(mouseY + 12, screenHeight - height - 8)), width, height);
    }

    private ResearchTreeDetailPolicy() { }
}
