package com.gamergaming.taczweaponblueprints.item;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Coarse catalog category independent of TaCZ's more specific item type. */
public enum BlueprintKind {
    GUN,
    AMMO,
    ATTACHMENT;

    public static final Codec<BlueprintKind> CODEC = Codec.STRING.flatXmap(
            BlueprintKind::parse,
            value -> DataResult.success(value.serializedName()));

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<BlueprintKind> parse(String value) {
        if (value != null) {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Report the supported values below.
            }
        }
        return DataResult.error(() -> "unknown blueprint kind " + value);
    }
}
