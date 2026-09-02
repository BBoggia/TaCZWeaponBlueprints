package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootTag(int format, List<ResourceLocation> values) {
    public static final int CURRENT_FORMAT = 1;
    public static final int MAX_VALUES = 4096;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintLootTag::validateFormat,
            BlueprintLootTag::validateFormat);

    private static final Codec<BlueprintLootTag> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintLootTag::format),
                    ResourceLocation.CODEC.listOf().fieldOf("values").forGetter(BlueprintLootTag::values))
                    .apply(instance, BlueprintLootTag::new));

    public static final Codec<BlueprintLootTag> CODEC = StrictRecordCodec.wrap(
            "blueprint loot tag",
            RAW_CODEC.flatXmap(BlueprintLootTag::validateTag, BlueprintLootTag::validateTag),
            "format",
            "values");

    public BlueprintLootTag {
        values = values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value == CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint loot-tag format " + value);
    }

    private static DataResult<BlueprintLootTag> validateTag(BlueprintLootTag tag) {
        if (tag.values().isEmpty()) {
            return DataResult.error(() -> "blueprint loot tag must contain at least one value");
        }
        if (tag.values().size() > MAX_VALUES) {
            return DataResult.error(() -> "blueprint loot tag cannot contain more than " + MAX_VALUES + " values");
        }
        return DataResult.success(tag);
    }
}
