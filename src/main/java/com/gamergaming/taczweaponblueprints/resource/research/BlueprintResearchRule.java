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
        Optional<ResearchRequirements> prerequisiteGroups,
        Optional<Boolean> creativeBypassesCost,
        Optional<BlueprintReverseEngineeringOverride> reverseEngineering) {
    public static final int LEGACY_FORMAT = 1;
    public static final int CURRENT_FORMAT = 2;
    public static final int MAX_ABSOLUTE_PRIORITY = 1_000_000;
    public static final int MAX_PREREQUISITES =
            ResearchRequirements.MAX_TOTAL_ALTERNATIVES;

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
                    new StrictOptionalFieldCodec<>(
                            "prerequisite_groups",
                            ResearchRequirements.CODEC)
                            .forGetter(BlueprintResearchRule::prerequisiteGroups),
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
            "prerequisite_groups",
            "creative_bypasses_cost",
            "reverse_engineering");

    /** Compatibility constructor for the format-1 flat prerequisite contract. */
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
            Optional<Boolean> creativeBypassesCost,
            Optional<BlueprintReverseEngineeringOverride> reverseEngineering) {
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
                Optional.empty(),
                creativeBypassesCost,
                reverseEngineering);
    }

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
                Optional.empty(),
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
                Optional.empty(),
                creativeBypassesCost,
                Optional.empty());
    }

    public BlueprintResearchRule {
        if (format < LEGACY_FORMAT || format > CURRENT_FORMAT) {
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
        prerequisiteGroups = optional(prerequisiteGroups);
        creativeBypassesCost = optional(creativeBypassesCost);
        reverseEngineering = optional(reverseEngineering);
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
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
        if (rule.prerequisites().isPresent() && rule.prerequisiteGroups().isPresent()) {
            return DataResult.error(() -> "research rule fields prerequisites and prerequisite_groups are mutually exclusive");
        }
        if (rule.prerequisiteGroups().isPresent() && rule.format() < CURRENT_FORMAT) {
            return DataResult.error(() -> "prerequisite_groups requires research-rule format " + CURRENT_FORMAT);
        }
        if (rule.prerequisiteRequirements().map(ResearchRequirements::alternativeCount).orElse(0)
                > MAX_PREREQUISITES) {
            return DataResult.error(() -> "research rule cannot contain more than "
                    + MAX_PREREQUISITES + " prerequisites");
        }
        if (rule.prerequisiteRequirements().isPresent() && !rule.target().exactOnly()) {
            return DataResult.error(() -> "prerequisite-bearing research rules must use exact blueprint targets");
        }
        return DataResult.success(rule);
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    /** Canonical view; presence preserves an explicit empty override. */
    public Optional<ResearchRequirements> prerequisiteRequirements() {
        return prerequisiteGroups.isPresent()
                ? prerequisiteGroups
                : prerequisites.map(ResearchRequirements::fromLegacy);
    }

    void validateForSnapshot() {
        target.validateForSnapshot();
        researchCost.ifPresent(BlueprintResearchCost::validateForSnapshot);
        reverseEngineering.ifPresent(BlueprintReverseEngineeringOverride::validateForSnapshot);
        if (prerequisites.isPresent() && prerequisiteGroups.isPresent()) {
            throw new IllegalArgumentException(
                    "research rule fields prerequisites and prerequisite_groups are mutually exclusive");
        }
        if (prerequisiteGroups.isPresent() && format < CURRENT_FORMAT) {
            throw new IllegalArgumentException(
                    "prerequisite_groups requires research-rule format " + CURRENT_FORMAT);
        }
        if (prerequisiteRequirements().map(ResearchRequirements::alternativeCount).orElse(0)
                > MAX_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "research rule cannot contain more than " + MAX_PREREQUISITES + " prerequisites");
        }
        if (prerequisiteRequirements().isPresent() && !target.exactOnly()) {
            throw new IllegalArgumentException(
                    "prerequisite-bearing research rules must use exact blueprint targets");
        }
        if (prerequisites.orElse(List.of()).stream().anyMatch(value ->
                value == null
                        || value.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)) {
            throw new IllegalArgumentException("research rule contains an oversized prerequisite ID");
        }
        // Self references and cycles are validated only after rule selection.
        // Rejecting them here would make a harmless lower-priority shadowed
        // rule invalidate an otherwise sound snapshot.
    }
}
