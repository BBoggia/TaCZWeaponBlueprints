package com.gamergaming.taczweaponblueprints.resource.loot;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BlueprintLootRolls(int min, int max) {
    private static final Codec<Integer> BOUNDED_ROLL_CODEC = Codec.INT.flatXmap(
            BlueprintLootRolls::validateBound,
            BlueprintLootRolls::validateBound);

    private static final Codec<BlueprintLootRolls> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BOUNDED_ROLL_CODEC.fieldOf("min").forGetter(BlueprintLootRolls::min),
                    BOUNDED_ROLL_CODEC.fieldOf("max").forGetter(BlueprintLootRolls::max))
                    .apply(instance, BlueprintLootRolls::new));

    public static final Codec<BlueprintLootRolls> CODEC = StrictRecordCodec.wrap(
            "blueprint loot rolls",
            RAW_CODEC.flatXmap(BlueprintLootRolls::validateRolls, BlueprintLootRolls::validateRolls),
            "min",
            "max");

    private static DataResult<Integer> validateBound(int value) {
        return value >= 0 && value <= BlueprintConfig.MAX_BLUEPRINTS_PER_LOOT_CONTAINER
                ? DataResult.success(value)
                : DataResult.error(() -> "roll bound must be between 0 and "
                        + BlueprintConfig.MAX_BLUEPRINTS_PER_LOOT_CONTAINER);
    }

    private static DataResult<BlueprintLootRolls> validateRolls(BlueprintLootRolls rolls) {
        return rolls.max() >= rolls.min()
                ? DataResult.success(rolls)
                : DataResult.error(() -> "maximum rolls cannot be less than minimum rolls");
    }
}
