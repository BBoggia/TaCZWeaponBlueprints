package com.gamergaming.taczweaponblueprints.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The one button every screen uses. A screen has at most one {@link Variant#PRIMARY}
 * button: the amber one marks the action the player can take now.
 */
public final class UiButton extends Button {
    public enum Variant { SECONDARY, PRIMARY }

    private static final int TEXT_MARGIN = 4;
    private final Variant variant;
    private boolean selected;

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress, Variant variant) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.variant = variant;
    }

    public static UiButton primary(int x, int y, int width, int height, Component label, OnPress onPress) {
        return new UiButton(x, y, width, height, label, onPress, Variant.PRIMARY);
    }

    public static UiButton secondary(int x, int y, int width, int height, Component label, OnPress onPress) {
        return new UiButton(x, y, width, height, label, onPress, Variant.SECONDARY);
    }

    /** Marks the current choice in a row of options, such as the open Tech Tree domain. */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** Which sprite a button shows. Disabled wins over hover, and a disabled primary is not amber. */
    static ResourceLocation spriteFor(Variant variant, boolean active, boolean highlighted) {
        if (!active) {
            return UiSprites.BUTTON_DISABLED;
        }
        if (variant == Variant.PRIMARY) {
            return highlighted ? UiSprites.BUTTON_PRIMARY_HOVER : UiSprites.BUTTON_PRIMARY;
        }
        return highlighted ? UiSprites.BUTTON_HOVER : UiSprites.BUTTON;
    }

    static int textColorFor(Variant variant, boolean active) {
        if (!active) {
            return UiTheme.TEXT_DISABLED;
        }
        return variant == Variant.PRIMARY ? UiTheme.AMBER_TEXT : UiTheme.TEXT;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiDraw.sprite(graphics, spriteFor(variant, active, selected || isHoveredOrFocused()), getX(), getY(), width, height);
        if (selected) {
            graphics.fill(getX() + 2, getY() + height - 2, getX() + width - 2, getY() + height - 1, UiTheme.MANILA);
        }
        Font font = Minecraft.getInstance().font;
        int color = textColorFor(variant, active);
        Component label = getMessage();
        // The bottom two rows of the sprite are its shaded lip, so text centres in the face above it.
        int textY = getY() + (height - 2 - font.lineHeight) / 2 + 1;
        if (font.width(label) <= width - TEXT_MARGIN * 2) {
            graphics.drawString(font, label, getX() + (width - font.width(label)) / 2, textY, color, false);
        } else {
            renderScrollingString(graphics, font, TEXT_MARGIN, color);
        }
    }
}
