package com.gamergaming.taczweaponblueprints.client.ui;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One selectable line in a list: a name on the left and a short status on the right.
 * The narration message is passed separately so it can read both parts as one sentence.
 */
public final class UiListRow extends Button {
    private static final int PADDING = 4;
    private final Component label;
    private final Component trailing;
    private final int labelColor;
    private final int trailingColor;
    private final int iconInset;
    private final BooleanSupplier selected;

    public UiListRow(int x, int y, int width, int height, Component narration, Component label, int labelColor,
            Component trailing, int trailingColor, int iconInset, BooleanSupplier selected, OnPress onPress) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
        this.label = label;
        this.trailing = trailing;
        this.labelColor = labelColor;
        this.trailingColor = trailingColor;
        this.iconInset = iconInset;
        this.selected = selected;
    }

    /** Selected rows stay raised; hover is one step lighter than resting. */
    static int fillFor(boolean selected, boolean highlighted) {
        if (selected) {
            return UiTheme.STEEL_RAISED;
        }
        return highlighted ? UiTheme.STEEL_LOW : UiTheme.STEEL;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = selected.getAsBoolean();
        graphics.fill(getX(), getY(), getX() + width, getY() + height, fillFor(isSelected, isHoveredOrFocused()));
        if (isSelected) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, UiTheme.MANILA);
        }
        Font font = Minecraft.getInstance().font;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;
        int trailingWidth = font.width(trailing);
        int right = getX() + width - PADDING;
        if (trailingWidth > 0) {
            graphics.drawString(font, trailing, right - trailingWidth, textY, trailingColor, false);
            right -= trailingWidth + 6;
        }
        int left = getX() + PADDING + iconInset;
        int room = Math.max(0, right - left);
        if (font.width(label) <= room) {
            graphics.drawString(font, label, left, textY, labelColor, false);
        } else {
            String clipped = font.plainSubstrByWidth(label.getString(), Math.max(0, room - font.width("…"))) + "…";
            graphics.drawString(font, clipped, left, textY, labelColor, false);
        }
    }
}
