package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingAccessPolicy;

/** Approachable server-config strategy for ammo crafting access. */
public enum AmmoCraftingStrategy {
    PROFILE,
    LINKED_WEAPON,
    TIER_1,
    TIER_2,
    TIER_3,
    UNRESTRICTED,
    DISABLED;

    public Optional<BlueprintCraftingAccessPolicy> fixedAccess() {
        return switch (this) {
            case PROFILE, LINKED_WEAPON -> Optional.empty();
            case TIER_1 -> Optional.of(BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_1));
            case TIER_2 -> Optional.of(BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_2));
            case TIER_3 -> Optional.of(BlueprintCraftingAccessPolicy.tiered(
                    ResearchWorkbenchTier.TIER_3));
            case UNRESTRICTED -> Optional.of(BlueprintCraftingAccessPolicy.UNRESTRICTED);
            case DISABLED -> Optional.of(BlueprintCraftingAccessPolicy.DISABLED);
        };
    }
}
