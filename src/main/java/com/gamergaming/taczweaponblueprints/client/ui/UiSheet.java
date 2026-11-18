package com.gamergaming.taczweaponblueprints.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing for the drafting sheet the Tech Tree sits on: paper, grid, tiles, wires and gate bars. */
public final class UiSheet {
    public static final int GRID_MINOR_STEP = 8;
    public static final int GRID_MAJOR_STEP = 64;

    /**
     * What a blueprint tile tells the player at a glance. READY is the only amber state: the player
     * can research it now. OPEN means its prerequisites are met but something else is missing, such
     * as currency or a higher bench. LOCKED is the quietest.
     */
    public enum TileState { LEARNED, READY, OPEN, LOCKED, HIDDEN }

    private UiSheet() { }

    /** Paper and grid for a viewport. The grid scrolls with the content so panning feels anchored. */
    public static void paper(GuiGraphics graphics, int x, int y, int width, int height, int scrollX, int scrollY) {
        graphics.fill(x, y, x + width, y + height, UiTheme.SHEET);
        for (int gx = -Math.floorMod(scrollX, GRID_MINOR_STEP); gx < width; gx += GRID_MINOR_STEP) {
            if (gx >= 0) {
                boolean major = Math.floorMod(gx + scrollX, GRID_MAJOR_STEP) == 0;
                graphics.fill(x + gx, y, x + gx + 1, y + height, major ? UiTheme.GRID_MAJOR : UiTheme.GRID_MINOR);
            }
        }
        for (int gy = -Math.floorMod(scrollY, GRID_MINOR_STEP); gy < height; gy += GRID_MINOR_STEP) {
            if (gy >= 0) {
                boolean major = Math.floorMod(gy + scrollY, GRID_MAJOR_STEP) == 0;
                graphics.fill(x, y + gy, x + width, y + gy + 1, major ? UiTheme.GRID_MAJOR : UiTheme.GRID_MINOR);
            }
        }
    }

    public static int tileFill(TileState state) {
        return switch (state) {
            case LEARNED -> UiTheme.SHEET_LEARNED;
            case READY, OPEN -> UiTheme.SHEET_TILE;
            case LOCKED, HIDDEN -> UiTheme.SHEET;
        };
    }

    public static int tileBorder(TileState state) {
        return switch (state) {
            case LEARNED -> UiTheme.INK;
            case READY -> UiTheme.AMBER;
            case OPEN -> UiTheme.INK_DIM;
            case LOCKED, HIDDEN -> UiTheme.PENCIL;
        };
    }

    public static int tileText(TileState state) {
        return switch (state) {
            case LEARNED, READY, OPEN -> UiTheme.INK;
            case LOCKED, HIDDEN -> UiTheme.INK_DIM;
        };
    }

    /** The tile frame. The caller draws art and the name inside it. */
    public static void tile(GuiGraphics graphics, int x, int y, int width, int height, TileState state,
            boolean selected, boolean hovered) {
        boolean solid = state == TileState.LEARNED || state == TileState.READY || state == TileState.OPEN;
        if (solid) {
            graphics.fill(x + 1, y + height, x + width - 1, y + height + 2, UiTheme.SHADOW);
        }
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered && solid
                ? UiTheme.SHEET_LEARNED : tileFill(state));
        int border = hovered && !solid ? UiTheme.INK_DIM : tileBorder(state);
        if (solid) {
            // Notched corners: the border stops one pixel short of each corner.
            graphics.fill(x + 1, y, x + width - 1, y + 1, border);
            graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, border);
            graphics.fill(x, y + 1, x + 1, y + height - 1, border);
            graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, border);
        } else {
            dashedFrame(graphics, x, y, width, height, border);
        }
        if (state == TileState.LEARNED) {
            check(graphics, x + width - 9, y + 3);
        }
        if (selected) {
            graphics.renderOutline(x - 2, y - 2, width + 4, height + 4, UiTheme.INK);
        }
    }

    private static void dashedFrame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        for (int dx = 1; dx < width - 1; dx += 4) {
            int end = Math.min(dx + 2, width - 1);
            graphics.fill(x + dx, y, x + end, y + 1, color);
            graphics.fill(x + dx, y + height - 1, x + end, y + height, color);
        }
        for (int dy = 1; dy < height - 1; dy += 4) {
            int end = Math.min(dy + 2, height - 1);
            graphics.fill(x, y + dy, x + 1, y + end, color);
            graphics.fill(x + width - 1, y + dy, x + width, y + end, color);
        }
    }

    /** Fades a tile back into the paper, for blueprints a search does not match. */
    public static void dim(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 4, (UiTheme.SHEET & 0xFFFFFF) | 0xB8000000);
    }

    /** A 6x5 pixel tick. */
    private static void check(GuiGraphics graphics, int x, int y) {
        int color = UiTheme.GREEN;
        graphics.fill(x, y + 2, x + 1, y + 3, color);
        graphics.fill(x + 1, y + 3, x + 2, y + 4, color);
        graphics.fill(x + 2, y + 4, x + 3, y + 5, color);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, color);
        graphics.fill(x + 4, y + 2, x + 5, y + 3, color);
        graphics.fill(x + 5, y + 1, x + 6, y + 2, color);
    }

    /** A wire piece drawn as dashes, for a prerequisite the player can swap for another. */
    public static void dashedWire(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        int right = Math.max(x1, x2);
        int bottom = Math.max(y1, y2);
        if (top == bottom) {
            for (int x = left; x <= right; x += 4) {
                graphics.fill(x, top, Math.min(x + 2, right + 1), top + 1, color);
            }
        } else {
            for (int y = top; y <= bottom; y += 4) {
                graphics.fill(left, y, left + 1, Math.min(y + 2, bottom + 1), color);
            }
        }
    }

    /** A small square where a link from another lane arrives, so cross points stand out from the lane's own wires. */
    public static void crossPoint(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 2, y - 2, x + 3, y + 3, UiTheme.SHEET);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
    }

    /** Corner brackets just outside a tile, to point at the one blueprint a new player should start with. */
    public static void marker(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int left = x - 3;
        int top = y - 3;
        int right = x + width + 3;
        int bottom = y + height + 3;
        int arm = 5;
        graphics.fill(left, top, left + arm, top + 1, color);
        graphics.fill(left, top, left + 1, top + arm, color);
        graphics.fill(right - arm, top, right, top + 1, color);
        graphics.fill(right - 1, top, right, top + arm, color);
        graphics.fill(left, bottom - 1, left + arm, bottom, color);
        graphics.fill(left, bottom - arm, left + 1, bottom, color);
        graphics.fill(right - arm, bottom - 1, right, bottom, color);
        graphics.fill(right - 1, bottom - arm, right, bottom, color);
    }

    /** A 7x8 pixel closed padlock. */
    public static void padlock(GuiGraphics graphics, int x, int y) {
        int color = UiTheme.INK;
        graphics.fill(x + 2, y, x + 5, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 4, color);
        graphics.fill(x + 5, y + 1, x + 6, y + 4, color);
        graphics.fill(x, y + 4, x + 7, y + 8, color);
        graphics.fill(x + 3, y + 5, x + 4, y + 7, UiTheme.SHEET_GATE);
    }

    /** One straight piece of a wire, one pixel thick. Coordinates may be given in either order. */
    public static void wire(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    /** The bar across the sheet that opens a tier, with its name on the left and progress on the right. */
    public static void gateBar(GuiGraphics graphics, Font font, int x, int y, int width, int height, int labelX,
            Component label, int countRight, Component count) {
        gateBar(graphics, font, x, y, width, height, labelX, label, Component.empty(), false, countRight, count);
    }

    /**
     * A gate bar that can be shut. A shut gate draws a padlock and says what opens it;
     * an open gate keeps the quiet look so the player's eye stays on the tiles.
     */
    public static void gateBar(GuiGraphics graphics, Font font, int x, int y, int width, int height, int labelX,
            Component label, Component requirement, boolean shut, int countRight, Component count) {
        graphics.fill(x, y, x + width, y + height, UiTheme.SHEET_GATE);
        graphics.fill(x, y, x + width, y + 1, shut ? UiTheme.PENCIL : UiTheme.GRID_MAJOR);
        graphics.fill(x, y + height - 1, x + width, y + height, shut ? UiTheme.PENCIL : UiTheme.GRID_MAJOR);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        if (shut) {
            padlock(graphics, labelX, y + (height - 8) / 2);
            labelX += 10;
        }
        graphics.drawString(font, label, labelX, textY, shut ? UiTheme.INK : UiTheme.INK_DIM, false);
        if (!requirement.getString().isEmpty()) {
            graphics.drawString(font, requirement, labelX + font.width(label) + 8, textY,
                    shut ? UiTheme.INK_DIM : UiTheme.PENCIL, false);
        }
        graphics.drawString(font, count, countRight - font.width(count), textY, UiTheme.INK_DIM, false);
    }
}
