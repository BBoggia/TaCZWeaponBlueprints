package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintCraftingPolicy;

/** Minimal player-facing crafting access data, stripped of authoring diagnostics. */
public record DisclosedCraftingAccess(
        BlueprintCraftingDisposition disposition,
        Optional<ResearchWorkbenchTier> requiredWorkbenchTier) {

    public DisclosedCraftingAccess {
        requiredWorkbenchTier = requiredWorkbenchTier == null
                ? Optional.empty()
                : requiredWorkbenchTier;
        if (disposition == null
                || (disposition == BlueprintCraftingDisposition.TIERED)
                        != requiredWorkbenchTier.isPresent()) {
            throw new IllegalArgumentException("invalid disclosed crafting access");
        }
    }

    public static DisclosedCraftingAccess from(ResolvedBlueprintCraftingPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("crafting policy cannot be null");
        }
        return new DisclosedCraftingAccess(
                policy.disposition(), policy.requiredWorkbenchTier());
    }
}
