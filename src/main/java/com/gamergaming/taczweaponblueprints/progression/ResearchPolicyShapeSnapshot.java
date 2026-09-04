package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.AutomaticWorkbenchTierPercentiles;

import net.minecraft.resources.ResourceLocation;

/**
 * The subset of live configuration that changes resolved per-blueprint policy.
 * Runtime enforcement and workstation access switches deliberately do not
 * participate, so toggling them does not rebuild an otherwise identical policy.
 */
public record ResearchPolicyShapeSnapshot(
        BlueprintFragmentPreset fragmentPreset,
        AutomaticWorkbenchTierPercentiles automaticTierPercentiles,
        Map<ResourceLocation, Integer> exactFragmentThresholds,
        BlueprintFragmentPolicy.CompletionMode customFragmentMode,
        Map<ResearchWorkbenchTier, Integer> customFragmentThresholds,
        int customFragmentRetainedCap,
        BlueprintFragmentDiscount customFragmentDiscount,
        int customLearnedTargetResearchPoints,
        CraftingPolicyConfigSnapshot craftingPolicy) {
    public ResearchPolicyShapeSnapshot {
        if (fragmentPreset == null || automaticTierPercentiles == null
                || exactFragmentThresholds == null || customFragmentMode == null
                || customFragmentThresholds == null || customFragmentDiscount == null
                || craftingPolicy == null) {
            throw new IllegalArgumentException("research policy shape cannot contain null");
        }
        exactFragmentThresholds = Map.copyOf(exactFragmentThresholds);
        customFragmentThresholds = Map.copyOf(customFragmentThresholds);
    }

    public static ResearchPolicyShapeSnapshot from(ResearchFeatureConfigSnapshot config) {
        if (config == null) {
            throw new IllegalArgumentException("research feature config cannot be null");
        }
        boolean customFragments = config.fragmentPreset() == BlueprintFragmentPreset.CUSTOM;
        boolean fragmentsEnabled = config.fragmentPreset() != BlueprintFragmentPreset.DISABLED
                && (!customFragments
                        || config.customFragmentMode()
                                != BlueprintFragmentPolicy.CompletionMode.DISABLED);
        return new ResearchPolicyShapeSnapshot(
                config.fragmentPreset(),
                config.automaticTierPercentiles(),
                fragmentsEnabled ? config.exactFragmentThresholds() : Map.of(),
                customFragments
                        ? config.customFragmentMode()
                        : BlueprintFragmentPolicy.CompletionMode.DISABLED,
                fragmentsEnabled && customFragments
                        ? config.customFragmentThresholds()
                        : Map.of(),
                fragmentsEnabled && customFragments ? config.customFragmentRetainedCap() : 1,
                fragmentsEnabled && customFragments
                        ? config.customFragmentDiscount()
                        : BlueprintFragmentDiscount.NONE,
                fragmentsEnabled && customFragments
                        ? config.customLearnedTargetResearchPoints()
                        : 0,
                config.craftingPolicy());
    }
}
