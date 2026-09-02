package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BlueprintResearchCost(int points, List<BlueprintResearchIngredient> ingredients) {
    public static final int MAX_INGREDIENT_TYPES = 6;

    private static final Codec<BlueprintResearchCost> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlueprintResearchCodecs.POINTS.fieldOf("points").forGetter(BlueprintResearchCost::points),
                    new StrictOptionalFieldCodec<>("ingredients", BlueprintResearchIngredient.CODEC.listOf())
                            .xmap(
                                    value -> value.orElse(List.of()),
                                    value -> value == null || value.isEmpty()
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(BlueprintResearchCost::ingredients))
                    .apply(instance, BlueprintResearchCost::new));

    public static final Codec<BlueprintResearchCost> CODEC = StrictRecordCodec.wrap(
            "blueprint research cost",
            RAW_CODEC.flatXmap(BlueprintResearchCost::validateCost, BlueprintResearchCost::validateCost),
            "points",
            "ingredients");

    public BlueprintResearchCost {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        if (points < 0 || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("research point cost is outside the supported range");
        }
    }

    private static DataResult<BlueprintResearchCost> validateCost(BlueprintResearchCost cost) {
        return cost.ingredients().size() <= MAX_INGREDIENT_TYPES
                ? DataResult.success(cost)
                : DataResult.error(() -> "research cost cannot contain more than "
                        + MAX_INGREDIENT_TYPES + " ingredient types");
    }

    void validateForSnapshot() {
        if (ingredients.size() > MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException(
                    "research cost cannot contain more than " + MAX_INGREDIENT_TYPES + " ingredient types");
        }
        if (ingredients.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research cost cannot contain null ingredients");
        }
        ingredients.forEach(BlueprintResearchIngredient::validateForSnapshot);
    }
}
