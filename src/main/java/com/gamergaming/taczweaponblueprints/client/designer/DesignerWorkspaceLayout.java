package com.gamergaming.taczweaponblueprints.client.designer;

/** Logical GUI coordinates; a lower inspector never takes space from the library. */
public record DesignerWorkspaceLayout(int width, int height, int library, int inspector, int lower, int problems) {
    public static final int TOP = 82, FOOTER = 34;
    public static DesignerWorkspaceLayout create(int width, int height, boolean library, boolean selected, boolean problems) {
        int side = library ? Math.min(168, Math.max(112, width / 4)) : 0;
        int right = selected && width >= 700 ? 210 : 0;
        int drawer = problems ? Math.min(96, Math.max(42, (height - TOP - FOOTER) / 2)) : 0;
        int lower = selected && right == 0 && !problems ? Math.min(90, Math.max(42, (height - TOP - FOOTER) / 2)) : 0;
        return new DesignerWorkspaceLayout(width, height, side, right, lower, drawer);
    }
    public int canvasX() { return library + 4; }
    public int canvasWidth() { return Math.max(1, width - library - inspector - 8); }
    public int canvasHeight() { return Math.max(1, height - TOP - FOOTER - lower - problems); }
    public int libraryRows() { return Math.max(1, (height - TOP - FOOTER - problems - 26) / 22); }
    public int libraryPagerY() { return height - FOOTER - problems - 22; }
    public int lowerY() { return height - FOOTER - lower - problems; }
}
