package com.gamergaming.taczweaponblueprints.client.ui;

/**
 * Shared ARGB colours for every screen. Two materials: the cool drafting sheet
 * the Tech Tree is drawn on, and the warm workshop panels that sit on top of it.
 * Screens read colours from here instead of declaring their own.
 */
public final class UiTheme {
    // Drafting sheet
    public static final int SHEET = 0xFF21405C;
    public static final int SHEET_LEARNED = 0xFF2C5375;
    public static final int SHEET_TILE = 0xFF274865;
    public static final int SHEET_GATE = 0xFF1A3349;
    public static final int GRID_MINOR = 0x08CDE2F5;
    public static final int GRID_MAJOR = 0x12CDE2F5;
    public static final int INK = 0xFFD3E0EA;
    public static final int INK_DIM = 0xFF8EAAC1;
    public static final int PENCIL = 0xFF5F7F9B;
    public static final int WIRE = 0x6BD3E0EA;
    /** A link that joins two lanes: warm against the cool sheet so cross points read at a glance. */
    public static final int WIRE_CROSS = 0xB0D6C49A;

    // Workshop
    public static final int STEEL_DARKEST = 0xFF1C1B1A;
    public static final int STEEL = 0xFF292827;
    public static final int STEEL_LOW = 0xFF353331;
    public static final int STEEL_RAISED = 0xFF464340;
    public static final int STEEL_HIGHLIGHT = 0xFF5A5651;
    public static final int TEXT = 0xFFE9DFCB;
    public static final int TEXT_MUTED = 0xFFA99F8D;
    public static final int TEXT_DISABLED = 0xFF77716A;
    public static final int MANILA = 0xFFD6C49A;
    public static final int MANILA_TEXT = 0xFF4A3F28;
    public static final int RULE = 0x47000000;
    public static final int SHADOW = 0x52000000;

    // Signals: amber means the player can act now, green means learned, red means an error.
    public static final int AMBER = 0xFFE6A23C;
    public static final int AMBER_HIGHLIGHT = 0xFFF3BB62;
    public static final int AMBER_LOW = 0xFF9A6A1F;
    public static final int AMBER_TEXT = 0xFF2E1F06;
    public static final int GREEN = 0xFF86B97F;
    public static final int RED = 0xFFE07B68;

    private UiTheme() { }

    /** WCAG contrast ratio between two opaque colours, from 1 to 21. */
    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05D) / (Math.min(a, b) + 0.05D);
    }

    private static double luminance(int argb) {
        return 0.2126D * channel(argb >> 16) + 0.7152D * channel(argb >> 8) + 0.0722D * channel(argb);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0D;
        return c <= 0.03928D ? c / 12.92D : Math.pow((c + 0.055D) / 1.055D, 2.4D);
    }
}
