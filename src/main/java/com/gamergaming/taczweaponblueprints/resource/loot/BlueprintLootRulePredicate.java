package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootRulePredicate(
        List<ResourceLocation> dimensions,
        Optional<Float> minLuck,
        Optional<Float> maxLuck) {
    public static final int MAX_DIMENSIONS = 256;

    private static final Codec<Float> FINITE_FLOAT = Codec.FLOAT.flatXmap(
            BlueprintLootRulePredicate::validateFinite,
            BlueprintLootRulePredicate::validateFinite);

    private static final Codec<BlueprintLootRulePredicate> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    new StrictOptionalFieldCodec<>("dimensions", ResourceLocation.CODEC.listOf())
                            .xmap(value -> value.orElse(List.of()), BlueprintLootRulePredicate::optionalDimensions)
                            .forGetter(BlueprintLootRulePredicate::dimensions),
                    new StrictOptionalFieldCodec<>("min_luck", FINITE_FLOAT)
                            .forGetter(BlueprintLootRulePredicate::minLuck),
                    new StrictOptionalFieldCodec<>("max_luck", FINITE_FLOAT)
                            .forGetter(BlueprintLootRulePredicate::maxLuck))
                    .apply(instance, BlueprintLootRulePredicate::new));

    public static final Codec<BlueprintLootRulePredicate> CODEC = StrictRecordCodec.wrap(
            "blueprint loot-rule predicate",
            RAW_CODEC.flatXmap(
                    BlueprintLootRulePredicate::validatePredicate,
                    BlueprintLootRulePredicate::validatePredicate),
            "dimensions",
            "min_luck",
            "max_luck");

    public BlueprintLootRulePredicate {
        dimensions = dimensions == null ? List.of() : List.copyOf(new LinkedHashSet<>(dimensions));
        minLuck = minLuck == null ? Optional.empty() : minLuck;
        maxLuck = maxLuck == null ? Optional.empty() : maxLuck;
    }

    public boolean matches(ResourceLocation dimension, float luck) {
        if (!dimensions.isEmpty() && !dimensions.contains(dimension)) {
            return false;
        }
        if (minLuck.isPresent() && luck < minLuck.get()) {
            return false;
        }
        return maxLuck.isEmpty() || luck <= maxLuck.get();
    }

    private static DataResult<Float> validateFinite(float value) {
        return Float.isFinite(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "luck bounds must be finite");
    }

    private static DataResult<BlueprintLootRulePredicate> validatePredicate(BlueprintLootRulePredicate predicate) {
        if (predicate.dimensions().size() > MAX_DIMENSIONS) {
            return DataResult.error(() -> "loot-rule predicate cannot contain more than "
                    + MAX_DIMENSIONS + " dimensions");
        }
        if (predicate.minLuck().isPresent()
                && predicate.maxLuck().isPresent()
                && predicate.maxLuck().get() < predicate.minLuck().get()) {
            return DataResult.error(() -> "maximum luck cannot be less than minimum luck");
        }
        return DataResult.success(predicate);
    }

    private static Optional<List<ResourceLocation>> optionalDimensions(List<ResourceLocation> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values);
    }
}
