package com.gamergaming.taczweaponblueprints.client.ui;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

/** Nine-slice GUI sprites under {@code textures/gui/sprites/ui}. Resource packs can replace them. */
public final class UiSprites {
    public static final Sprite PANEL = sprite("panel", 16, 4);
    public static final Sprite FIELD = sprite("field", 8, 2);
    public static final Sprite BUTTON = sprite("button", 16, 3);
    public static final Sprite BUTTON_HOVER = sprite("button_hover", 16, 3);
    public static final Sprite BUTTON_DISABLED = sprite("button_disabled", 16, 3);
    public static final Sprite BUTTON_PRIMARY = sprite("button_primary", 16, 3);
    public static final Sprite BUTTON_PRIMARY_HOVER = sprite("button_primary_hover", 16, 3);
    public static final Sprite TAG = sprite("tag", 8, 2);
    public static final Sprite TOOLTIP = sprite("tooltip", 8, 3);

    static final List<Sprite> ALL = List.of(PANEL, FIELD, BUTTON, BUTTON_HOVER, BUTTON_DISABLED,
            BUTTON_PRIMARY, BUTTON_PRIMARY_HOVER, TAG, TOOLTIP);

    private UiSprites() { }

    public record Sprite(ResourceLocation texture, int size, int border) { }

    private static Sprite sprite(String name, int size, int border) {
        return new Sprite(TaCZWeaponBlueprints.loc("textures/gui/sprites/ui/" + name + ".png"), size, border);
    }
}
