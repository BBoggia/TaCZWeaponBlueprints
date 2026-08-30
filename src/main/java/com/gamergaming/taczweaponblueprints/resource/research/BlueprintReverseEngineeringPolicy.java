package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Immutable, server-authored reverse-engineering policy for one blueprint.
 * An absent input-count override means one gun/attachment or the canonical
 * recipe output count for ammunition.
 */
public record BlueprintReverseEngineeringPolicy(
        boolean enabled,
        Optional<Integer> inputCount,
        BlueprintResearchCost cost,
        boolean allowKnown,
        boolean allowModified,
        PhysicalBlueprintLearningMode physicalBlueprintLearningMode,
        boolean outputRecyclable,
        boolean expertAllowEconomyLoop) {
    public static final int MAX_INPUT_COUNT = 64;
    public static final BlueprintReverseEngineeringPolicy DEFAULT =
            new BlueprintReverseEngineeringPolicy(
                    true,
                    Optional.empty(),
                    new BlueprintResearchCost(0, List.of()),
                    false,
                    true,
                    PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                    false,
                    false);
    public static final BlueprintReverseEngineeringPolicy DISABLED =
            new BlueprintReverseEngineeringPolicy(
                    false,
                    Optional.empty(),
                    new BlueprintResearchCost(0, List.of()),
                    false,
                    true,
                    PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                    false,
                    false);

    private static final Codec<Integer> INPUT_COUNT_CODEC = Codec.INT.flatXmap(
            BlueprintReverseEngineeringPolicy::validateInputCount,
            BlueprintReverseEngineeringPolicy::validateInputCount);
    private static final Codec<PhysicalBlueprintLearningMode> LEARNING_MODE_CODEC =
            Codec.STRING.flatXmap(
                    BlueprintReverseEngineeringPolicy::parseLearningMode,
                    value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

    private static final Codec<BlueprintReverseEngineeringPolicy> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    new StrictOptionalFieldCodec<>("enabled", Codec.BOOL)
                            .xmap(value -> value.orElse(DEFAULT.enabled()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::enabled),
                    new StrictOptionalFieldCodec<>("input_count", INPUT_COUNT_CODEC)
                            .forGetter(BlueprintReverseEngineeringPolicy::inputCount),
                    new StrictOptionalFieldCodec<>("cost", BlueprintResearchCost.CODEC)
                            .xmap(value -> value.orElse(DEFAULT.cost()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::cost),
                    new StrictOptionalFieldCodec<>("allow_known", Codec.BOOL)
                            .xmap(value -> value.orElse(DEFAULT.allowKnown()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::allowKnown),
                    new StrictOptionalFieldCodec<>("allow_modified", Codec.BOOL)
                            .xmap(value -> value.orElse(DEFAULT.allowModified()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::allowModified),
                    new StrictOptionalFieldCodec<>("physical_blueprint_learning", LEARNING_MODE_CODEC)
                            .xmap(
                                    value -> value.orElse(DEFAULT.physicalBlueprintLearningMode()),
                                    Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::physicalBlueprintLearningMode),
                    new StrictOptionalFieldCodec<>("output_recyclable", Codec.BOOL)
                            .xmap(value -> value.orElse(DEFAULT.outputRecyclable()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::outputRecyclable),
                    new StrictOptionalFieldCodec<>("expert_allow_economy_loop", Codec.BOOL)
                            .xmap(value -> value.orElse(DEFAULT.expertAllowEconomyLoop()), Optional::of)
                            .forGetter(BlueprintReverseEngineeringPolicy::expertAllowEconomyLoop))
                    .apply(instance, BlueprintReverseEngineeringPolicy::new));

    public static final Codec<BlueprintReverseEngineeringPolicy> CODEC = StrictRecordCodec.wrap(
            "blueprint reverse-engineering policy",
            RAW_CODEC,
            "enabled",
            "input_count",
            "cost",
            "allow_known",
            "allow_modified",
            "physical_blueprint_learning",
            "output_recyclable",
            "expert_allow_economy_loop");

    public BlueprintReverseEngineeringPolicy {
        inputCount = inputCount == null ? Optional.empty() : inputCount;
        if (cost == null || physicalBlueprintLearningMode == null) {
            throw new IllegalArgumentException(
                    "reverse-engineering cost and physical-blueprint learning mode cannot be null");
        }
        inputCount.ifPresent(value -> {
            if (value < 1 || value > MAX_INPUT_COUNT) {
                throw new IllegalArgumentException(
                        "reverse-engineering input count must be between 1 and " + MAX_INPUT_COUNT);
            }
        });
    }

    public BlueprintReverseEngineeringPolicy apply(BlueprintReverseEngineeringOverride override) {
        if (override == null) {
            return this;
        }
        return new BlueprintReverseEngineeringPolicy(
                override.enabled().orElse(enabled),
                override.inputCount().isPresent() ? override.inputCount() : inputCount,
                override.cost().orElse(cost),
                override.allowKnown().orElse(allowKnown),
                override.allowModified().orElse(allowModified),
                override.physicalBlueprintLearningMode().orElse(physicalBlueprintLearningMode),
                override.outputRecyclable().orElse(outputRecyclable),
                override.expertAllowEconomyLoop().orElse(expertAllowEconomyLoop));
    }

    void validateForSnapshot() {
        cost.validateForSnapshot();
    }

    private static DataResult<Integer> validateInputCount(int value) {
        return value >= 1 && value <= MAX_INPUT_COUNT
                ? DataResult.success(value)
                : DataResult.error(() -> "reverse-engineering input count must be between 1 and "
                        + MAX_INPUT_COUNT);
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
}
