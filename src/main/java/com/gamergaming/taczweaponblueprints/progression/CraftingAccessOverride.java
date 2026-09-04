package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingAccessPolicy;

/** Exact server-config override for one canonical blueprint recipe. */
public enum CraftingAccessOverride {
    TIER_1,
    TIER_2,
    TIER_3,
    UNRESTRICTED,
    DISABLED;

    public BlueprintCraftingAccessPolicy accessPolicy() {
        return switch (this) {
            case TIER_1 -> BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_1);
            case TIER_2 -> BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_2);
            case TIER_3 -> BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_3);
            case UNRESTRICTED -> BlueprintCraftingAccessPolicy.UNRESTRICTED;
            case DISABLED -> BlueprintCraftingAccessPolicy.DISABLED;
        };
    }
}
