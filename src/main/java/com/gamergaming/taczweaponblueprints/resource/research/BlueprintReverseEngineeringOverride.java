package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-rule, field-by-field overlay for reverse-engineering policy. */
public record BlueprintReverseEngineeringOverride(
        Optional<Boolean> enabled,
        Optional<Integer> inputCount,
        Optional<BlueprintResearchCost> cost,
        Optional<Boolean> allowKnown,
        Optional<Boolean> allowModified,
        Optional<PhysicalBlueprintLearningMode> physicalBlueprintLearningMode,
        Optional<Boolean> outputRecyclable,
        Optional<Boolean> expertAllowEconomyLoop) {
    private static final Codec<Integer> INPUT_COUNT_CODEC = Codec.INT.flatXmap(
            BlueprintReverseEngineeringOverride::validateInputCount,
            BlueprintReverseEngineeringOverride::validateInputCount);
    private static final Codec<PhysicalBlueprintLearningMode> LEARNING_MODE_CODEC =
            Codec.STRING.flatXmap(
                    BlueprintReverseEngineeringOverride::parseLearningMode,
                    value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

    private static final Codec<BlueprintReverseEngineeringOverride> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    new StrictOptionalFieldCodec<>("enabled", Codec.BOOL)
                            .forGetter(BlueprintReverseEngineeringOverride::enabled),
                    new StrictOptionalFieldCodec<>("input_count", INPUT_COUNT_CODEC)
                            .forGetter(BlueprintReverseEngineeringOverride::inputCount),
                    new StrictOptionalFieldCodec<>("cost", BlueprintResearchCost.CODEC)
                            .forGetter(BlueprintReverseEngineeringOverride::cost),
                    new StrictOptionalFieldCodec<>("allow_known", Codec.BOOL)
                            .forGetter(BlueprintReverseEngineeringOverride::allowKnown),
                    new StrictOptionalFieldCodec<>("allow_modified", Codec.BOOL)
                            .forGetter(BlueprintReverseEngineeringOverride::allowModified),
                    new StrictOptionalFieldCodec<>("physical_blueprint_learning", LEARNING_MODE_CODEC)
                            .forGetter(BlueprintReverseEngineeringOverride::physicalBlueprintLearningMode),
                    new StrictOptionalFieldCodec<>("output_recyclable", Codec.BOOL)
                            .forGetter(BlueprintReverseEngineeringOverride::outputRecyclable),
                    new StrictOptionalFieldCodec<>("expert_allow_economy_loop", Codec.BOOL)
                            .forGetter(BlueprintReverseEngineeringOverride::expertAllowEconomyLoop))
                    .apply(instance, BlueprintReverseEngineeringOverride::new));

    public static final Codec<BlueprintReverseEngineeringOverride> CODEC = StrictRecordCodec.wrap(
            "blueprint reverse-engineering override",
            RAW_CODEC,
            "enabled",
            "input_count",
            "cost",
            "allow_known",
            "allow_modified",
            "physical_blueprint_learning",
            "output_recyclable",
            "expert_allow_economy_loop");

    public BlueprintReverseEngineeringOverride {
        enabled = optional(enabled);
        inputCount = optional(inputCount);
        cost = optional(cost);
        allowKnown = optional(allowKnown);
        allowModified = optional(allowModified);
        physicalBlueprintLearningMode = optional(physicalBlueprintLearningMode);
        outputRecyclable = optional(outputRecyclable);
        expertAllowEconomyLoop = optional(expertAllowEconomyLoop);
    }

    void validateForSnapshot() {
        cost.ifPresent(BlueprintResearchCost::validateForSnapshot);
    }

    private static DataResult<Integer> validateInputCount(int value) {
        return value >= 1 && value <= BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT
                ? DataResult.success(value)
                : DataResult.error(() -> "reverse-engineering input count must be between 1 and "
                        + BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT);
    }

    private static DataResult<PhysicalBlueprintLearningMode> parseLearningMode(String value) {
        if (value != null) {
            try {
                return DataResult.success(PhysicalBlueprintLearningMode.valueOf(
                        value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Return a bounded error below.
            }
        }
        return DataResult.error(() -> "unknown physical-blueprint learning mode " + value);
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }
}
