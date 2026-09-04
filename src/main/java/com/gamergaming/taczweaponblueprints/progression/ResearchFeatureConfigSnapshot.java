package com.gamergaming.taczweaponblueprints.progression;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.AutomaticWorkbenchTierPercentiles;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintFragmentProfilePolicy;

import net.minecraft.resources.ResourceLocation;

/** Immutable server-config overlay for workstation and Blueprint Fragment policy. */
public record ResearchFeatureConfigSnapshot(
        ResearchProgressionPreset progressionPreset,
        boolean enforceResearchTiers,
        boolean enforceCraftingTiers,
        BlueprintFragmentPreset fragmentPreset,
        AutomaticWorkbenchTierPercentiles automaticTierPercentiles,
        Map<ResourceLocation, ResearchWorkbenchTier> externalWorkstationTiers,
        ResearchWorkbenchTier unknownWorkstationFallbackTier,
        boolean unknownExternalWorkstationsUnrestricted,
        boolean creativeBypassesWorkbenchTiers,
        boolean creativeBypassesProgressionGates,
        Map<ResourceLocation, Integer> exactFragmentThresholds,
        BlueprintFragmentPolicy.CompletionMode customFragmentMode,
        Map<ResearchWorkbenchTier, Integer> customFragmentThresholds,
        int customFragmentRetainedCap,
        BlueprintFragmentDiscount customFragmentDiscount,
        int customLearnedTargetResearchPoints,
        int fragmentLootReplacementBasisPoints,
        CraftingPolicyConfigSnapshot craftingPolicy) {
    public static final int MAX_MAPPINGS = 4_096;

    public ResearchFeatureConfigSnapshot {
        if (progressionPreset == null || fragmentPreset == null
                || automaticTierPercentiles == null || externalWorkstationTiers == null
                || unknownWorkstationFallbackTier == null || exactFragmentThresholds == null
                || customFragmentMode == null || customFragmentThresholds == null
                || customFragmentDiscount == null
                || craftingPolicy == null
                || customFragmentRetainedCap < 1
                || customFragmentRetainedCap > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS
                || customLearnedTargetResearchPoints < 0
                || customLearnedTargetResearchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || fragmentLootReplacementBasisPoints < 0
                || fragmentLootReplacementBasisPoints > 10_000) {
            throw new IllegalArgumentException("research feature config snapshot is invalid");
        }
        externalWorkstationTiers = immutableResourceMap(externalWorkstationTiers);
        exactFragmentThresholds = immutableResourceMap(exactFragmentThresholds);
        if (externalWorkstationTiers.size() > MAX_MAPPINGS
                || exactFragmentThresholds.size() > MAX_MAPPINGS) {
            throw new IllegalArgumentException("research feature config has too many exact mappings");
        }
        if (exactFragmentThresholds.values().stream().anyMatch(value ->
                value < 1 || value > BlueprintFragmentPolicy.MAX_THRESHOLD)) {
            throw new IllegalArgumentException("exact Blueprint Fragment threshold is invalid");
        }
        EnumMap<ResearchWorkbenchTier, Integer> thresholds =
                new EnumMap<>(ResearchWorkbenchTier.class);
        customFragmentThresholds.forEach((tier, threshold) -> {
            if (tier == null || threshold == null
                    || threshold < 1 || threshold > BlueprintFragmentPolicy.MAX_THRESHOLD) {
                throw new IllegalArgumentException("custom Blueprint Fragment threshold is invalid");
            }
            thresholds.put(tier, threshold);
        });
        if (!thresholds.keySet().equals(java.util.EnumSet.allOf(ResearchWorkbenchTier.class))) {
            throw new IllegalArgumentException("custom Blueprint Fragment thresholds must cover all tiers");
        }
        customFragmentThresholds = Collections.unmodifiableMap(thresholds);
        if (customFragmentRetainedCap < thresholds.values().stream().mapToInt(Integer::intValue).max().orElseThrow()) {
            throw new IllegalArgumentException("custom Blueprint Fragment cap is below a tier threshold");
        }
    }

    public static ResearchFeatureConfigSnapshot from(BlueprintConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("blueprint config cannot be null");
        }
        ResearchProgressionPreset progression = config.progressionPreset.get();
        Map<ResearchWorkbenchTier, Integer> thresholds = Map.of(
                ResearchWorkbenchTier.TIER_1, config.tierOneFragmentThreshold.get(),
                ResearchWorkbenchTier.TIER_2, config.tierTwoFragmentThreshold.get(),
                ResearchWorkbenchTier.TIER_3, config.tierThreeFragmentThreshold.get());
        return new ResearchFeatureConfigSnapshot(
                progression,
                progression.enforcesResearch(config.customEnforceResearchTiers.get()),
                progression.enforcesCrafting(config.customEnforceCraftingTiers.get()),
                config.fragmentPreset.get(),
                new AutomaticWorkbenchTierPercentiles(
                        config.tierOneUpperPercent.get() * 100,
                        config.tierTwoUpperPercent.get() * 100),
                parseTierMappings(config.externalWorkstationTiers),
                config.unknownWorkstationFallbackTier.get(),
                config.unknownExternalWorkstationsUnrestricted.get(),
                config.creativeBypassesWorkbenchTiers.get(),
                config.creativeBypassesProgressionGates.get(),
                parseThresholdMappings(config.exactFragmentThresholds),
                config.customFragmentMode.get(),
                thresholds,
                config.fragmentRetainedProgressCap.get(),
                config.fragmentDiscountType.get().create(config.fragmentDiscountValue.get()),
                config.learnedTargetFragmentResearchPoints.get(),
                config.fragmentLootReplacementPercent.get() * 100,
                CraftingPolicyConfigSnapshot.from(config));
    }

    public ResearchPolicyShapeSnapshot policyShape() {
        return ResearchPolicyShapeSnapshot.from(this);
    }

    /** Runtime workstation mapping changes invalidate already-open external menus. */
    public boolean hasSameExternalWorkbenchResolution(ResearchFeatureConfigSnapshot other) {
        return other != null
                && externalWorkstationTiers.equals(other.externalWorkstationTiers)
                && unknownWorkstationFallbackTier == other.unknownWorkstationFallbackTier
                && unknownExternalWorkstationsUnrestricted
                        == other.unknownExternalWorkstationsUnrestricted;
    }

    /** Applies the global fragment preset while retaining an exact rule/config threshold. */
    public BlueprintFragmentPolicy fragmentPolicy(
            BlueprintFragmentProfilePolicy profilePolicy,
            ResourceLocation blueprintId,
            ResearchWorkbenchTier tier,
            Optional<Integer> ruleThreshold) {
        if (profilePolicy == null || blueprintId == null || tier == null) {
            throw new IllegalArgumentException("Blueprint Fragment policy inputs cannot be null");
        }
        Optional<Integer> exact = ruleThreshold == null ? Optional.empty() : ruleThreshold;
        Integer configuredThreshold = exactFragmentThresholds.get(blueprintId);
        if (configuredThreshold != null) {
            exact = Optional.of(configuredThreshold);
        }
        if (fragmentPreset == BlueprintFragmentPreset.DISABLED) {
            return BlueprintFragmentPolicy.DISABLED;
        }
        if (fragmentPreset == BlueprintFragmentPreset.CUSTOM) {
            if (customFragmentMode == BlueprintFragmentPolicy.CompletionMode.DISABLED) {
                return BlueprintFragmentPolicy.DISABLED;
            }
            return new BlueprintFragmentPolicy(
                    customFragmentMode,
                    exact.orElse(customFragmentThresholds.get(tier)),
                    Math.max(customFragmentRetainedCap,
                            exact.orElse(customFragmentThresholds.get(tier))),
                    customFragmentMode == BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT
                            ? BlueprintFragmentDiscount.NONE
                            : customFragmentDiscount,
                    customLearnedTargetResearchPoints);
        }
        BlueprintFragmentProfilePolicy effective = profilePolicy.mode()
                        == BlueprintFragmentPolicy.CompletionMode.DISABLED
                ? BlueprintFragmentProfilePolicy.DEFAULT
                : profilePolicy;
        BlueprintFragmentPolicy resolved = configuredThreshold == null
                ? effective.resolve(tier, exact)
                : new BlueprintFragmentPolicy(
                        effective.mode(),
                        configuredThreshold,
                        Math.max(effective.retainedProgressCap(), configuredThreshold),
                        effective.researchDiscount(),
                        effective.learnedTargetResearchPoints());
        if (fragmentPreset == BlueprintFragmentPreset.TARGETED_RESEARCH_BOOST) {
            BlueprintFragmentDiscount discount = resolved.researchDiscount().mode()
                            == BlueprintFragmentDiscount.Mode.NONE
                    ? BlueprintFragmentProfilePolicy.DEFAULT.researchDiscount()
                    : resolved.researchDiscount();
            return new BlueprintFragmentPolicy(
                    BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                    resolved.threshold(),
                    resolved.retainedProgressCap(),
                    discount,
                    resolved.learnedTargetResearchPoints());
        }
        return new BlueprintFragmentPolicy(
                BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT,
                resolved.threshold(),
                resolved.retainedProgressCap(),
                BlueprintFragmentDiscount.NONE,
                resolved.learnedTargetResearchPoints());
    }

    private static Map<ResourceLocation, ResearchWorkbenchTier> parseTierMappings(
            Map<String, ResearchWorkbenchTier> source) {
        Map<ResourceLocation, ResearchWorkbenchTier> result = new LinkedHashMap<>();
        source.forEach((id, tier) -> result.put(parseId(id), tier));
        return result;
    }

    private static Map<ResourceLocation, Integer> parseThresholdMappings(Map<String, Integer> source) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        source.forEach((id, threshold) -> {
            if (threshold == null || threshold < 1 || threshold > BlueprintFragmentPolicy.MAX_THRESHOLD) {
                throw new IllegalArgumentException("exact Blueprint Fragment threshold is invalid");
            }
            result.put(parseId(id), threshold);
        });
        return result;
    }

    private static ResourceLocation parseId(String value) {
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("configured progression resource ID is invalid: " + value);
        }
        return id;
    }

    private static <V> Map<ResourceLocation, V> immutableResourceMap(Map<ResourceLocation, V> source) {
        Map<ResourceLocation, V> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException("configured progression mapping contains null");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }
}
