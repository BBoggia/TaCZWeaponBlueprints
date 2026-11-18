package com.gamergaming.taczweaponblueprints.client.ui;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

/** Nine-slice GUI sprites under {@code textures/gui/sprites/ui}. Resource packs can replace them. */
public final class UiSprites {
    /** Sprite file names, without extension. Tests check each one ships with its scaling metadata. */
    public static final List<String> NAMES = List.of(
            "panel", "field", "button", "button_hover", "button_disabled",
            "button_primary", "button_primary_hover", "tag", "tooltip");

    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation FIELD = sprite("field");
    public static final ResourceLocation BUTTON = sprite("button");
    public static final ResourceLocation BUTTON_HOVER = sprite("button_hover");
    public static final ResourceLocation BUTTON_DISABLED = sprite("button_disabled");
    public static final ResourceLocation BUTTON_PRIMARY = sprite("button_primary");
    public static final ResourceLocation BUTTON_PRIMARY_HOVER = sprite("button_primary_hover");
    public static final ResourceLocation TAG = sprite("tag");
    public static final ResourceLocation TOOLTIP = sprite("tooltip");

    private UiSprites() { }

    private static ResourceLocation sprite(String name) {
        return TaCZWeaponBlueprints.loc("ui/" + name);
    }
}
