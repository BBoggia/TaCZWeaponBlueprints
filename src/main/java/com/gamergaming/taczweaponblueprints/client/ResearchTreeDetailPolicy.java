package com.gamergaming.taczweaponblueprints.client;

/** Screen-space detail thresholds in logical GUI pixels, with small transition buffers. */
final class ResearchTreeDetailPolicy {
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
