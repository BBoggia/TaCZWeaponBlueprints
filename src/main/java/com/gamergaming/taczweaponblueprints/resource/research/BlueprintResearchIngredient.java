package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchIngredient(
        List<ResourceLocation> items,
        Optional<ResourceLocation> tag,
        int count) {
    public static final int MAX_ITEMS = 64;
    public static final int MAX_COUNT = 64;

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            BlueprintResearchIngredient::validateCount,
            BlueprintResearchIngredient::validateCount);

    private static final Codec<BlueprintResearchIngredient> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    optionalList("items", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BlueprintResearchIngredient::items),
                    new StrictOptionalFieldCodec<>("tag", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BlueprintResearchIngredient::tag),
                    COUNT_CODEC.fieldOf("count").forGetter(BlueprintResearchIngredient::count))
                    .apply(instance, BlueprintResearchIngredient::new));

    public static final Codec<BlueprintResearchIngredient> CODEC = StrictRecordCodec.wrap(
            "blueprint research ingredient",
            RAW_CODEC.flatXmap(
                    BlueprintResearchIngredient::validateIngredient,
                    BlueprintResearchIngredient::validateIngredient),
            "items",
            "tag",
            "count");

    public BlueprintResearchIngredient {
        items = items == null ? List.of() : List.copyOf(new LinkedHashSet<>(items));
        tag = tag == null ? Optional.empty() : tag;
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("ingredient count must be between one and " + MAX_COUNT);
        }
    }

    private static DataResult<Integer> validateCount(int value) {
        return value >= 1 && value <= MAX_COUNT
                ? DataResult.success(value)
                : DataResult.error(() -> "ingredient count must be between one and " + MAX_COUNT);
    }

    private static DataResult<BlueprintResearchIngredient> validateIngredient(
            BlueprintResearchIngredient ingredient) {
        if (ingredient.items().size() > MAX_ITEMS) {
            return DataResult.error(() -> "research ingredient cannot contain more than "
                    + MAX_ITEMS + " item alternatives");
        }
        if (ingredient.items().isEmpty() == ingredient.tag().isEmpty()) {
            return DataResult.error(() -> "research ingredient must define exactly one of items or tag");
        }
        return DataResult.success(ingredient);
    }

    private static boolean isOversized(ResourceLocation id) {
        return id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    void validateForSnapshot() {
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException(
                    "research ingredient cannot contain more than " + MAX_ITEMS + " item alternatives");
        }
        if (items.isEmpty() == tag.isEmpty()) {
            throw new IllegalArgumentException("research ingredient must define exactly one of items or tag");
        }
        if (items.stream().anyMatch(BlueprintResearchIngredient::isOversized)
                || tag.filter(BlueprintResearchIngredient::isOversized).isPresent()) {
            throw new IllegalArgumentException("research ingredient contains an oversized resource ID");
        }
    }

    private static <T> com.mojang.serialization.MapCodec<List<T>> optionalList(
            String name,
            Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(
                        value -> value.orElse(List.of()),
                        value -> value == null || value.isEmpty() ? Optional.empty() : Optional.of(value));
    }
}
