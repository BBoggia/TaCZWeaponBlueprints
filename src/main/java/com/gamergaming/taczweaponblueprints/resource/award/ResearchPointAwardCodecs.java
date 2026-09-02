package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;

final class ResearchPointAwardCodecs {
    static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            ResearchPointAwardCodecs::validateId,
            ResearchPointAwardCodecs::validateId);
    static final Codec<Integer> POSITIVE_POINTS = Codec.INT.flatXmap(
            ResearchPointAwardCodecs::validatePositivePoints,
            ResearchPointAwardCodecs::validatePositivePoints);
    static final Codec<Integer> POSITIVE_INT = Codec.INT.flatXmap(
            ResearchPointAwardCodecs::validatePositiveInt,
            ResearchPointAwardCodecs::validatePositiveInt);
    static final Codec<Long> POSITIVE_TICKS = Codec.LONG.flatXmap(
            ResearchPointAwardCodecs::validatePositiveTicks,
            ResearchPointAwardCodecs::validatePositiveTicks);
    static final Codec<String> BOUNDED_STRING = Codec.STRING.flatXmap(
            ResearchPointAwardCodecs::validateString,
            ResearchPointAwardCodecs::validateString);
    static final Codec<String> NAMESPACE = Codec.STRING.flatXmap(
            ResearchPointAwardCodecs::validateNamespace,
            ResearchPointAwardCodecs::validateNamespace);
    static final Codec<String> PATH_PREFIX = Codec.STRING.flatXmap(
            ResearchPointAwardCodecs::validatePathPrefix,
            ResearchPointAwardCodecs::validatePathPrefix);

    private ResearchPointAwardCodecs() {
    }

    static <T> com.mojang.serialization.MapCodec<List<T>> optionalList(
            String name,
            Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(value -> value.orElse(List.of()), ResearchPointAwardCodecs::optionalList);
    }

    private static <T> Optional<List<T>> optionalList(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values);
    }

    private static DataResult<ResourceLocation> validateId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                ? DataResult.success(id)
                : DataResult.error(() -> "resource ID exceeds "
                        + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + " characters");
    }

    private static DataResult<Integer> validatePositivePoints(int points) {
        return points > 0 && points <= PlayerProgressionLimits.MAX_RESEARCH_POINTS
                ? DataResult.success(points)
                : DataResult.error(() -> "Research Point value must be between 1 and "
                        + PlayerProgressionLimits.MAX_RESEARCH_POINTS);
    }

    private static DataResult<Integer> validatePositiveInt(int value) {
        return value > 0
                ? DataResult.success(value)
                : DataResult.error(() -> "value must be greater than zero");
    }

    private static DataResult<Long> validatePositiveTicks(long value) {
        return value > 0L
                ? DataResult.success(value)
                : DataResult.error(() -> "tick duration must be greater than zero");
    }

    private static DataResult<String> validateString(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                ? DataResult.success(value)
                : DataResult.error(() -> "string must be non-blank and at most "
                        + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + " characters");
    }

    private static DataResult<String> validateNamespace(String value) {
        return value != null
                && value.length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                && ResourceLocation.tryBuild(value, "value") != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid resource namespace " + value);
    }

    private static DataResult<String> validatePathPrefix(String value) {
        return value != null
                && value.length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                && ResourceLocation.tryBuild("test", value) != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid resource path prefix " + value);
    }
}
