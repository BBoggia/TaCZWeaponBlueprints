package com.gamergaming.taczweaponblueprints.resource.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootPoolReference(ResourceLocation pool, float weight) {
    private static final Codec<Float> WEIGHT_CODEC = Codec.FLOAT.flatXmap(
            BlueprintLootPoolReference::validateWeight,
            BlueprintLootPoolReference::validateWeight);

    private static final Codec<BlueprintLootPoolReference> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("pool").forGetter(BlueprintLootPoolReference::pool),
                    new StrictOptionalFieldCodec<>("weight", WEIGHT_CODEC)
                            .xmap(value -> value.orElse(1.0f), value -> Optional.of(value))
                            .forGetter(BlueprintLootPoolReference::weight))
                    .apply(instance, BlueprintLootPoolReference::new));

    public static final Codec<BlueprintLootPoolReference> CODEC = StrictRecordCodec.wrap(
            "blueprint loot pool reference",
            RAW_CODEC,
            "pool",
            "weight");

    public BlueprintLootPoolReference {
        if (pool == null) {
            throw new IllegalArgumentException("pool cannot be null");
        }
        if (!Float.isFinite(weight) || weight <= 0.0f) {
            throw new IllegalArgumentException("pool reference weight must be finite and greater than zero");
        }
    }

    private static DataResult<Float> validateWeight(float value) {
        return Float.isFinite(value) && value > 0.0f
                ? DataResult.success(value)
                : DataResult.error(() -> "pool reference weight must be finite and greater than zero");
    }
}
