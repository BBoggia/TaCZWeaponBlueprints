package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Explicit crafting availability for one canonical catalog entry. */
public enum BlueprintCraftingDisposition {
    TIERED,
    UNRESTRICTED,
    DISABLED;

    public static final Codec<BlueprintCraftingDisposition> CODEC = Codec.STRING.flatXmap(
            BlueprintCraftingDisposition::parse,
            value -> DataResult.success(value.serializedName()));

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<BlueprintCraftingDisposition> parse(String value) {
        try {
            return DataResult.success(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "unknown crafting disposition " + value);
        }
    }
}
