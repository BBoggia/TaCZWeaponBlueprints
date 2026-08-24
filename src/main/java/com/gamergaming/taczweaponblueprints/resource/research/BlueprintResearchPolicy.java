package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchPolicy(
        ResourceLocation blueprintId,
        ResourceLocation profileId,
        boolean available,
        boolean blocked,
        boolean playerDataAvailable,
        boolean learned,
        boolean discovered,
        int researchPoints,
        int pointCap,
        boolean prerequisitesSatisfied,
        boolean journalEnabled,
        JournalVisibility visibility,
        boolean researchEnabled,
        boolean recyclingEnabled,
        boolean allowUnlearnedRecycling,
        int recyclingValue,
        BlueprintResearchCost researchCost,
        boolean requiresDiscovery,
        List<ResourceLocation> prerequisites,
        boolean creativeBypassesCost,
        Optional<ResourceLocation> ruleId,
        BlueprintResearchTarget.MatchSpecificity specificity) {

    public BlueprintResearchPolicy {
        if (blueprintId == null || profileId == null || visibility == null
                || researchCost == null || specificity == null) {
            throw new IllegalArgumentException("resolved research policy contains null required state");
        }
        if (researchPoints < 0
                || researchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("resolved research policy contains an invalid point balance or cap");
        }
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        ruleId = ruleId == null ? Optional.empty() : ruleId;
    }

    public boolean researchable() {
        return playerDataAvailable
                && available
                && !blocked
                && researchEnabled
                && !learned
                && researchCost.points() <= pointCap
                && (!requiresDiscovery || discovered)
                && prerequisitesSatisfied;
    }

    public boolean canAffordPoints() {
        return playerDataAvailable
                && researchCost.points() <= pointCap
                && researchPoints >= researchCost.points();
    }

    public boolean recyclable() {
        return playerDataAvailable
                && available
                && !blocked
                && recyclingEnabled
                && recyclingValue > 0
                && recyclingValue <= pointCap - Math.min(researchPoints, pointCap)
                && (learned || allowUnlearnedRecycling);
    }

    public BlueprintResearchPolicy withRuntimePolicy(
            boolean effectiveJournalEnabled,
            JournalVisibility effectiveVisibility,
            boolean effectiveResearchEnabled,
            boolean effectiveRecyclingEnabled,
            boolean effectiveAllowUnlearnedRecycling,
            boolean effectiveCreativeBypassesCost,
            int effectivePointCap) {
        return new BlueprintResearchPolicy(
                blueprintId,
                profileId,
                available,
                blocked,
                playerDataAvailable,
                learned,
                discovered,
                researchPoints,
                effectivePointCap,
                prerequisitesSatisfied,
                effectiveJournalEnabled,
                effectiveVisibility,
                effectiveResearchEnabled,
                effectiveRecyclingEnabled,
                effectiveAllowUnlearnedRecycling,
                recyclingValue,
                researchCost,
                requiresDiscovery,
                prerequisites,
                effectiveCreativeBypassesCost,
                ruleId,
                specificity);
    }
}
