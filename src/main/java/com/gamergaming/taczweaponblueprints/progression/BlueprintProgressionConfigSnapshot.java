package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;

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
        FoundWeaponRecoveryMode foundWeaponRecoveryMode,
        ResearchCurrencyPolicy currencyPolicy,
        ResearchDomainSettings domainSettings,
        ResearchTierPricing tierPricing) {
    public static final int DEFAULT_POINT_CAP = 10_000;

    /** Snapshots built without tier pricing charge the datapack price at every tier. */
    public BlueprintProgressionConfigSnapshot(boolean blueprintsEnabled, boolean discoveryTrackingEnabled,
            boolean journalEnabled, JournalVisibility maximumUndiscoveredVisibility, boolean researchEnabled,
            DuplicateBlueprintPolicy duplicatePolicy, boolean allowUnlearnedRecycling, int pointCap,
            boolean creativeBypassesResearchCost, ResourceLocation activeProfileId,
            TreeResearchResultMode treeResearchResultMode, ResearchCostMode researchCostMode,
            FoundWeaponRecoveryMode foundWeaponRecoveryMode, ResearchCurrencyPolicy currencyPolicy,
            ResearchDomainSettings domainSettings) {
        this(blueprintsEnabled, discoveryTrackingEnabled, journalEnabled, maximumUndiscoveredVisibility,
                researchEnabled, duplicatePolicy, allowUnlearnedRecycling, pointCap, creativeBypassesResearchCost,
                activeProfileId, treeResearchResultMode, researchCostMode, foundWeaponRecoveryMode,
                currencyPolicy, domainSettings, ResearchTierPricing.NONE);
    }

    /** Existing integrations retain the profile's domain choices. */
    public BlueprintProgressionConfigSnapshot(boolean blueprintsEnabled, boolean discoveryTrackingEnabled,
            boolean journalEnabled, JournalVisibility maximumUndiscoveredVisibility, boolean researchEnabled,
            DuplicateBlueprintPolicy duplicatePolicy, boolean allowUnlearnedRecycling, int pointCap,
            boolean creativeBypassesResearchCost, ResourceLocation activeProfileId,
            TreeResearchResultMode treeResearchResultMode, ResearchCostMode researchCostMode,
            FoundWeaponRecoveryMode foundWeaponRecoveryMode, ResearchCurrencyPolicy currencyPolicy) {
        this(blueprintsEnabled, discoveryTrackingEnabled, journalEnabled, maximumUndiscoveredVisibility,
                researchEnabled, duplicatePolicy, allowUnlearnedRecycling, pointCap, creativeBypassesResearchCost,
                activeProfileId, treeResearchResultMode, researchCostMode, foundWeaponRecoveryMode,
                currencyPolicy, ResearchDomainSettings.PROFILE_DEFAULTS);
    }

    public BlueprintProgressionConfigSnapshot(boolean blueprintsEnabled, boolean discoveryTrackingEnabled,
            boolean journalEnabled, JournalVisibility maximumUndiscoveredVisibility, boolean researchEnabled,
            DuplicateBlueprintPolicy duplicatePolicy, boolean allowUnlearnedRecycling, int pointCap,
            boolean creativeBypassesResearchCost, ResourceLocation activeProfileId,
            TreeResearchResultMode treeResearchResultMode, ResearchCostMode researchCostMode,
            FoundWeaponRecoveryMode foundWeaponRecoveryMode) {
        this(blueprintsEnabled, discoveryTrackingEnabled, journalEnabled, maximumUndiscoveredVisibility,
                researchEnabled, duplicatePolicy, allowUnlearnedRecycling, pointCap, creativeBypassesResearchCost,
                activeProfileId, treeResearchResultMode, researchCostMode, foundWeaponRecoveryMode,
                ResearchCurrencyPolicy.banked(pointCap, 1));
    }

    public BlueprintProgressionConfigSnapshot {
        if (tierPricing == null || domainSettings == null || currencyPolicy == null || maximumUndiscoveredVisibility == null || duplicatePolicy == null
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
                config.foundWeaponRecoveryMode.get(), config.currencyPolicy(),
                new ResearchDomainSettings(config.ammoResearch.get(), config.attachmentResearch.get()),
                tierPricing(config));
    }

    /**
     * The overall price always applies. Tier prices only apply while Research Bench tiers are
     * enforced: without enforcement a player never sees a blueprint's tier, so a price that
     * followed it would look arbitrary.
     */
    private static ResearchTierPricing tierPricing(BlueprintConfig config) {
        boolean tiersEnforced = config.stationSetup().enforceResearchTiers(
                config.progressionPreset.get().enforcesResearch(config.customEnforceResearchTiers.get()));
        return new ResearchTierPricing(config.researchCostPercent.get(),
                tiersEnforced ? config.tierTwoResearchCostPercent.get() : ResearchTierPricing.UNCHANGED,
                tiersEnforced ? config.tierThreeResearchCostPercent.get() : ResearchTierPricing.UNCHANGED);
    }

    /** Applies this configuration using the tiers the running server has published. */
    public BlueprintResearchPolicy apply(BlueprintResearchPolicy policy) {
        return apply(policy, null);
    }

    /**
     * Applies this configuration. {@code tiers} supplies each blueprint's Research Bench tier for
     * tier pricing; a detached evaluation such as the Research Designer passes the tiers it
     * resolved for its own project, and {@code null} means the running server's published tiers.
     */
    public BlueprintResearchPolicy apply(BlueprintResearchPolicy policy,
            com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicySnapshot tiers) {
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
                currencyPolicy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM
                        ? PlayerProgressionLimits.MAX_RESEARCH_POINTS : pointCap)
                .withResearchCost(researchCostMode.apply(tierPriced(policy, tiers)));
    }

    /**
     * The datapack price scaled by the overall price and by the Research Bench tier this
     * blueprint needs.
     */
    private BlueprintResearchCost tierPriced(BlueprintResearchPolicy policy,
            com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicySnapshot tiers) {
        if (tierPricing.unchanged() || policy.researchCost().points() == 0) {
            return policy.researchCost();
        }
        if (tierPricing.tiersUnchanged()) {
            return tierPricing.apply(policy.researchCost(), 1);
        }
        var source = tiers != null ? tiers
                : com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionPolicyManager
                        .INSTANCE.publication().snapshot();
        var byBlueprint = source.policiesByProfile().get(policy.profileId());
        var resolved = byBlueprint == null ? null : byBlueprint.get(policy.blueprintId());
        // A blueprint with no resolved tier still pays the overall price.
        return tierPricing.apply(policy.researchCost(),
                resolved == null ? 1 : resolved.researchWorkbenchTier().level());
    }
}
