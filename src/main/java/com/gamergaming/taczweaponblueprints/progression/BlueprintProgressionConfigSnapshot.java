package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable view of synchronized, coarse progression policy. Datapacks still
 * own blueprint selection, costs, prerequisites, and per-target overrides.
 */
public record BlueprintProgressionConfigSnapshot(
        boolean blueprintsEnabled,
        boolean discoveryTrackingEnabled,
        boolean journalEnabled,
        JournalVisibility maximumUndiscoveredVisibility,
        boolean researchEnabled,
        DuplicateBlueprintPolicy duplicatePolicy,
        boolean allowUnlearnedRecycling,
        int pointCap,
        boolean creativeBypassesResearchCost,
        ResourceLocation activeProfileId,
        TreeResearchResultMode treeResearchResultMode,
        ResearchCostMode researchCostMode,
        FoundWeaponRecoveryMode foundWeaponRecoveryMode) {
    public static final int DEFAULT_POINT_CAP = 10_000;

    public BlueprintProgressionConfigSnapshot {
        if (maximumUndiscoveredVisibility == null || duplicatePolicy == null
                || activeProfileId == null || treeResearchResultMode == null
                || researchCostMode == null || foundWeaponRecoveryMode == null) {
            throw new IllegalArgumentException("progression configuration contains null required state");
        }
        if (pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("Research Point cap is outside the supported range");
        }
        if (activeProfileId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("active research profile ID is oversized");
        }
    }

    /**
     * Source-compatible constructor for policy-focused callers that do not
     * customize the Research Tree result. New snapshots use direct learning.
     */
    public BlueprintProgressionConfigSnapshot(
            boolean blueprintsEnabled,
            boolean discoveryTrackingEnabled,
            boolean journalEnabled,
            JournalVisibility maximumUndiscoveredVisibility,
            boolean researchEnabled,
            DuplicateBlueprintPolicy duplicatePolicy,
            boolean allowUnlearnedRecycling,
            int pointCap,
            boolean creativeBypassesResearchCost,
            ResourceLocation activeProfileId) {
        this(
                blueprintsEnabled,
                discoveryTrackingEnabled,
                journalEnabled,
                maximumUndiscoveredVisibility,
                researchEnabled,
                duplicatePolicy,
                allowUnlearnedRecycling,
                pointCap,
                creativeBypassesResearchCost,
                activeProfileId,
                TreeResearchResultMode.DIRECT_LEARN,
                ResearchCostMode.POINTS_AND_ITEMS,
                FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
    }

    /** Compatibility constructor retaining the pre-cost-mode runtime defaults. */
    public BlueprintProgressionConfigSnapshot(
            boolean blueprintsEnabled,
            boolean discoveryTrackingEnabled,
            boolean journalEnabled,
            JournalVisibility maximumUndiscoveredVisibility,
            boolean researchEnabled,
            DuplicateBlueprintPolicy duplicatePolicy,
            boolean allowUnlearnedRecycling,
            int pointCap,
            boolean creativeBypassesResearchCost,
            ResourceLocation activeProfileId,
            TreeResearchResultMode treeResearchResultMode) {
        this(
                blueprintsEnabled,
                discoveryTrackingEnabled,
                journalEnabled,
                maximumUndiscoveredVisibility,
                researchEnabled,
                duplicatePolicy,
                allowUnlearnedRecycling,
                pointCap,
                creativeBypassesResearchCost,
                activeProfileId,
                treeResearchResultMode,
                ResearchCostMode.POINTS_AND_ITEMS,
                FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
    }

    public static BlueprintProgressionConfigSnapshot from(BlueprintConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("blueprint configuration cannot be null");
        }
        ResourceLocation profileId = ResourceLocation.tryParse(config.activeResearchProfile.get());
        if (profileId == null) {
            profileId = BlueprintConfig.DEFAULT_RESEARCH_PROFILE;
        }
        return new BlueprintProgressionConfigSnapshot(
                config.enableBlueprints.get(),
                config.enableDiscoveryTracking.get(),
                config.enableJournal.get(),
                config.balanceSettings().maximumUndiscoveredVisibility(),
                config.enableResearch.get(),
                config.duplicateBlueprintPolicy.get(),
                config.allowUnlearnedRecycling.get(),
                config.researchPointCap.get(),
                config.creativeBypassesResearchCost.get(),
                profileId,
                config.treeResearchResultMode.get(),
                config.researchCostMode.get(),
                config.foundWeaponRecoveryMode.get());
    }

    public BlueprintResearchPolicy apply(BlueprintResearchPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("research policy cannot be null");
        }
        JournalVisibility visibility;
        if (!blueprintsEnabled || !journalEnabled || !policy.journalEnabled()) {
            visibility = JournalVisibility.HIDDEN;
        } else if (policy.learned()) {
            visibility = JournalVisibility.FULL;
        } else {
            visibility = policy.visibility().atMost(maximumUndiscoveredVisibility);
        }
        boolean manualRecycling = blueprintsEnabled
                && duplicatePolicy == DuplicateBlueprintPolicy.MANUAL_RECYCLING;
        return policy.withRuntimePolicy(
                blueprintsEnabled && journalEnabled && policy.journalEnabled(),
                visibility,
                blueprintsEnabled && researchEnabled && policy.researchEnabled(),
                manualRecycling && policy.recyclingEnabled(),
                manualRecycling && allowUnlearnedRecycling && policy.allowUnlearnedRecycling(),
                blueprintsEnabled
                        && researchEnabled
                        && creativeBypassesResearchCost
                        && policy.creativeBypassesCost(),
                pointCap).withResearchCost(researchCostMode.apply(policy.researchCost()));
    }
}
