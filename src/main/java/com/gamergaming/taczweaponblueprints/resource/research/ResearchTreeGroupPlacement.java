package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

/** Deterministic authored position of one blueprint within one Research Tree group. */
public record ResearchTreeGroupPlacement(
        ResourceLocation groupId,
        int rank,
        int orderInRank) {
    public ResearchTreeGroupPlacement {
        if (groupId == null
                || groupId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || rank < 0
                || rank >= ResearchTreeGroupDefinition.MAX_RANKS
                || orderInRank < 0
                || orderInRank >= ResearchTreeGroupDefinition.MAX_MEMBERS) {
            throw new IllegalArgumentException("invalid research-tree group placement");
        }
    }
}
