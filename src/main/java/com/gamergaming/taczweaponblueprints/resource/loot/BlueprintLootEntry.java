package com.gamergaming.taczweaponblueprints.resource.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootEntry(ResourceLocation blueprint, float weight) {
    private static final Codec<Float> POSITIVE_FINITE_FLOAT = Codec.FLOAT.flatXmap(
            BlueprintLootEntry::validateWeight,
            BlueprintLootEntry::validateWeight);

    private static final Codec<BlueprintLootEntry> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("blueprint").forGetter(BlueprintLootEntry::blueprint),
                    POSITIVE_FINITE_FLOAT.fieldOf("weight").forGetter(BlueprintLootEntry::weight))
                    .apply(instance, BlueprintLootEntry::new));

    public static final Codec<BlueprintLootEntry> CODEC = StrictRecordCodec.wrap(
            "blueprint loot entry",
            RAW_CODEC,
            "blueprint",
            "weight");

    public BlueprintLootEntry {
        if (blueprint == null) {
            throw new IllegalArgumentException("blueprint cannot be null");
        }
        if (!Float.isFinite(weight) || weight <= 0.0f) {
            throw new IllegalArgumentException("weight must be finite and greater than zero");
        }
    }

    private static DataResult<Float> validateWeight(float value) {
        return Float.isFinite(value) && value > 0.0f
                ? DataResult.success(value)
                : DataResult.error(() -> "weight must be finite and greater than zero");
    }
}
