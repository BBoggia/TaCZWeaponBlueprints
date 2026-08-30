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

public record BlueprintResearchRule(
        int format,
        ResourceLocation profile,
        int priority,
        BlueprintResearchTarget target,
        Optional<JournalVisibility> visibility,
        Optional<Boolean> treeEnabled,
        Optional<Boolean> researchEnabled,
        Optional<Boolean> recyclingEnabled,
        Optional<Boolean> allowUnlearnedRecycling,
        Optional<Integer> recyclingValue,
        Optional<BlueprintResearchCost> researchCost,
        Optional<Boolean> requiresDiscovery,
        Optional<List<ResourceLocation>> prerequisites,
        Optional<Boolean> creativeBypassesCost,
        Optional<BlueprintReverseEngineeringOverride> reverseEngineering) {
    public static final int CURRENT_FORMAT = 1;
    public static final int MAX_ABSOLUTE_PRIORITY = 1_000_000;
    public static final int MAX_PREREQUISITES = 64;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintResearchRule::validateFormat,
            BlueprintResearchRule::validateFormat);
    private static final Codec<Integer> PRIORITY_CODEC = Codec.INT.flatXmap(
            BlueprintResearchRule::validatePriority,
            BlueprintResearchRule::validatePriority);

    private static final Codec<BlueprintResearchRule> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintResearchRule::format),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("profile")
                            .forGetter(BlueprintResearchRule::profile),
                    new StrictOptionalFieldCodec<>("priority", PRIORITY_CODEC)
                            .xmap(value -> value.orElse(0), Optional::of)
                            .forGetter(BlueprintResearchRule::priority),
                    BlueprintResearchTarget.CODEC.fieldOf("target").forGetter(BlueprintResearchRule::target),
                    new StrictOptionalFieldCodec<>("visibility", JournalVisibility.CODEC)
                            .forGetter(BlueprintResearchRule::visibility),
                    new StrictOptionalFieldCodec<>("tree_enabled", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::treeEnabled),
                    new StrictOptionalFieldCodec<>("research_enabled", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::researchEnabled),
                    new StrictOptionalFieldCodec<>("recycling_enabled", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::recyclingEnabled),
                    new StrictOptionalFieldCodec<>("allow_unlearned_recycling", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::allowUnlearnedRecycling),
                    new StrictOptionalFieldCodec<>("recycling_value", BlueprintResearchCodecs.POINTS)
                            .forGetter(BlueprintResearchRule::recyclingValue),
                    new StrictOptionalFieldCodec<>("research_cost", BlueprintResearchCost.CODEC)
                            .forGetter(BlueprintResearchRule::researchCost),
                    new StrictOptionalFieldCodec<>("requires_discovery", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::requiresDiscovery),
                    new StrictOptionalFieldCodec<>(
                            "prerequisites",
                            BlueprintResearchCodecs.RESOURCE_LOCATION.listOf())
                            .forGetter(BlueprintResearchRule::prerequisites),
                    new StrictOptionalFieldCodec<>("creative_bypasses_cost", Codec.BOOL)
                            .forGetter(BlueprintResearchRule::creativeBypassesCost),
                    new StrictOptionalFieldCodec<>(
                            "reverse_engineering",
                            BlueprintReverseEngineeringOverride.CODEC)
                            .forGetter(BlueprintResearchRule::reverseEngineering))
                    .apply(instance, BlueprintResearchRule::new));

    public static final Codec<BlueprintResearchRule> CODEC = StrictRecordCodec.wrap(
            "blueprint research rule",
            RAW_CODEC.flatXmap(BlueprintResearchRule::validateRule, BlueprintResearchRule::validateRule),
            "format",
            "profile",
            "priority",
            "target",
            "visibility",
            "tree_enabled",
            "research_enabled",
            "recycling_enabled",
            "allow_unlearned_recycling",
            "recycling_value",
            "research_cost",
            "requires_discovery",
            "prerequisites",
            "creative_bypasses_cost",
            "reverse_engineering");

    /** Backwards-compatible constructor for rules authored before reverse engineering. */
    public BlueprintResearchRule(
            int format,
            ResourceLocation profile,
            int priority,
            BlueprintResearchTarget target,
            Optional<JournalVisibility> visibility,
            Optional<Boolean> treeEnabled,
            Optional<Boolean> researchEnabled,
            Optional<Boolean> recyclingEnabled,
            Optional<Boolean> allowUnlearnedRecycling,
            Optional<Integer> recyclingValue,
            Optional<BlueprintResearchCost> researchCost,
            Optional<Boolean> requiresDiscovery,
            Optional<List<ResourceLocation>> prerequisites,
            Optional<Boolean> creativeBypassesCost) {
        this(
                format,
                profile,
                priority,
                target,
                visibility,
                treeEnabled,
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                prerequisites,
                creativeBypassesCost,
                Optional.empty());
    }

    /** Backwards-compatible constructor for rules authored before tree controls. */
    public BlueprintResearchRule(
            int format,
            ResourceLocation profile,
            int priority,
            BlueprintResearchTarget target,
            Optional<JournalVisibility> visibility,
            Optional<Boolean> researchEnabled,
            Optional<Boolean> recyclingEnabled,
            Optional<Boolean> allowUnlearnedRecycling,
            Optional<Integer> recyclingValue,
            Optional<BlueprintResearchCost> researchCost,
            Optional<Boolean> requiresDiscovery,
            Optional<List<ResourceLocation>> prerequisites,
            Optional<Boolean> creativeBypassesCost) {
        this(
                format,
                profile,
                priority,
                target,
                visibility,
                Optional.empty(),
                researchEnabled,
                recyclingEnabled,
                allowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                prerequisites,
                creativeBypassesCost,
                Optional.empty());
    }

    public BlueprintResearchRule {
        if (format != CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported blueprint research-rule format " + format);
        }
        if (profile == null || target == null) {
            throw new IllegalArgumentException("rule profile and target cannot be null");
        }
        if (profile.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("rule profile ID is oversized");
        }
        if (Math.abs((long) priority) > MAX_ABSOLUTE_PRIORITY) {
            throw new IllegalArgumentException("research-rule priority is outside the supported range");
        }
        visibility = optional(visibility);
        treeEnabled = optional(treeEnabled);
        researchEnabled = optional(researchEnabled);
        recyclingEnabled = optional(recyclingEnabled);
        allowUnlearnedRecycling = optional(allowUnlearnedRecycling);
        recyclingValue = optional(recyclingValue);
        researchCost = optional(researchCost);
        requiresDiscovery = optional(requiresDiscovery);
        prerequisites = prerequisites == null
                ? Optional.empty()
                : prerequisites.map(values -> List.copyOf(new LinkedHashSet<>(values)));
        creativeBypassesCost = optional(creativeBypassesCost);
        reverseEngineering = optional(reverseEngineering);
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value == CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint research-rule format " + value);
    }

    private static DataResult<Integer> validatePriority(int value) {
        return Math.abs((long) value) <= MAX_ABSOLUTE_PRIORITY
                ? DataResult.success(value)
                : DataResult.error(() -> "research-rule priority must be between -"
                        + MAX_ABSOLUTE_PRIORITY + " and " + MAX_ABSOLUTE_PRIORITY);
    }

    private static DataResult<BlueprintResearchRule> validateRule(BlueprintResearchRule rule) {
        if (rule.prerequisites().map(List::size).orElse(0) > MAX_PREREQUISITES) {
            return DataResult.error(() -> "research rule cannot contain more than "
                    + MAX_PREREQUISITES + " prerequisites");
        }
        if (rule.prerequisites().isPresent() && !rule.target().exactOnly()) {
            return DataResult.error(() -> "prerequisite-bearing research rules must use exact blueprint targets");
        }
        return DataResult.success(rule);
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    void validateForSnapshot() {
        target.validateForSnapshot();
        researchCost.ifPresent(BlueprintResearchCost::validateForSnapshot);
        reverseEngineering.ifPresent(BlueprintReverseEngineeringOverride::validateForSnapshot);
        if (prerequisites.map(List::size).orElse(0) > MAX_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "research rule cannot contain more than " + MAX_PREREQUISITES + " prerequisites");
        }
        if (prerequisites.isPresent() && !target.exactOnly()) {
            throw new IllegalArgumentException(
                    "prerequisite-bearing research rules must use exact blueprint targets");
        }
        if (prerequisites.orElse(List.of()).stream().anyMatch(value ->
                value == null
                        || value.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
            throw new IllegalArgumentException("research rule contains an oversized prerequisite ID");
        }
    }
}
