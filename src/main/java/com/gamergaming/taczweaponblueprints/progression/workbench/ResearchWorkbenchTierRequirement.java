package com.gamergaming.taczweaponblueprints.progression.workbench;

/**
 * Legacy combined authoring value retained for format-3 profile migration.
 * Resolved runtime research and crafting policies use separate tier fields.
 */
public record ResearchWorkbenchTierRequirement(
        ResearchWorkbenchTier researchTier,
        ResearchWorkbenchTier craftingTier) {
    public static final ResearchWorkbenchTierRequirement TIER_1 = sameForBoth(
            ResearchWorkbenchTier.TIER_1);

    public ResearchWorkbenchTierRequirement {
        if (researchTier == null || craftingTier == null) {
            throw new IllegalArgumentException("workbench tier requirements cannot be null");
        }
    }

    public static ResearchWorkbenchTierRequirement sameForBoth(ResearchWorkbenchTier tier) {
        return new ResearchWorkbenchTierRequirement(tier, tier);
    }

}
