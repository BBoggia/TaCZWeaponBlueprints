package com.gamergaming.taczweaponblueprints.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing helpers shared by every screen, so panels, tags and tooltips look the same everywhere. */
public final class UiDraw {
    public static final int TAG_HEIGHT = 10;
    private static final int SHADOW_OFFSET = 2;

    private UiDraw() { }

    /** A workshop panel with a hard drop shadow, for anything that sits on top of other content. */
    public static void raisedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + height, x + width - 2, y + height + SHADOW_OFFSET, UiTheme.SHADOW);
        panel(graphics, x, y, width, height);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        sprite(graphics, UiSprites.PANEL, x, y, width, height);
    }

    /** A recessed area for text entry or item slots. */
    public static void field(GuiGraphics graphics, int x, int y, int width, int height) {
        sprite(graphics, UiSprites.FIELD, x, y, width, height);
    }

    /** A horizontal divider inside a panel. */
    public static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, UiTheme.RULE);
    }

    /** Draws a manila label and returns its width so callers can place the next one. */
    public static int tag(GuiGraphics graphics, Font font, Component text, int x, int y) {
        int width = font.width(text) + 6;
        sprite(graphics, UiSprites.TAG, x, y, width, TAG_HEIGHT);
        graphics.drawString(font, text, x + 3, y + 1, UiTheme.MANILA_TEXT, false);
        return width;
    }

    /** A one- or two-line tooltip box. The caller chooses a position that stays on screen. */
    public static void tooltip(GuiGraphics graphics, Font font, Component title, Component detail, int x, int y) {
        boolean hasDetail = detail != null && !detail.getString().isEmpty();
        int width = Math.max(font.width(title), hasDetail ? font.width(detail) : 0) + 10;
        int height = (hasDetail ? 2 : 1) * (font.lineHeight + 1) + 7;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(x + 1, y + height, x + width - 1, y + height + SHADOW_OFFSET, UiTheme.SHADOW);
        sprite(graphics, UiSprites.TOOLTIP, x, y, width, height);
        graphics.drawString(font, title, x + 5, y + 4, UiTheme.TEXT, false);
        if (hasDetail) {
            graphics.drawString(font, detail, x + 5, y + 5 + font.lineHeight, UiTheme.TEXT_MUTED, false);
        }
        graphics.pose().popPose();
    }

    static void sprite(GuiGraphics graphics, UiSprites.Sprite sprite, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        // Stretch each slice once. Tiling a full inspector with an 8px centre would submit thousands of blits.
        int border = sprite.border();
        int size = sprite.size();
        int edgeX = Math.min(border, width / 2);
        int edgeY = Math.min(border, height / 2);
        for (int row = 0; row < 3; row++) {
            int dy = row == 0 ? 0 : row == 1 ? edgeY : height - edgeY;
            int h = row == 1 ? height - 2 * edgeY : edgeY;
            int v = row == 0 ? 0 : row == 1 ? border : size - border;
            for (int col = 0; col < 3; col++) {
                int dx = col == 0 ? 0 : col == 1 ? edgeX : width - edgeX;
                int w = col == 1 ? width - 2 * edgeX : edgeX;
                int u = col == 0 ? 0 : col == 1 ? border : size - border;
                if (w > 0 && h > 0) {
                    graphics.blit(sprite.texture(), x + dx, y + dy, w, h, u, v,
                            col == 1 ? size - 2 * border : border, row == 1 ? size - 2 * border : border, size, size);
                }
            }
        }
    }
}
