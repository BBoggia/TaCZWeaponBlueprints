package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public record BlueprintResearchPolicy(
        ResourceLocation blueprintId,
        ResourceLocation profileId,
        boolean available,
        boolean blocked,
        boolean learned,
        boolean discovered,
        int researchPoints,
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
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        ruleId = ruleId == null ? Optional.empty() : ruleId;
    }

    public boolean researchable() {
        return available
                && !blocked
                && researchEnabled
                && !learned
                && (!requiresDiscovery || discovered)
                && prerequisitesSatisfied;
    }

    public boolean canAffordPoints() {
        return researchPoints >= researchCost.points();
    }

    public boolean recyclable() {
        return available
                && !blocked
                && recyclingEnabled
                && recyclingValue > 0
                && (learned || allowUnlearnedRecycling);
    }
}
