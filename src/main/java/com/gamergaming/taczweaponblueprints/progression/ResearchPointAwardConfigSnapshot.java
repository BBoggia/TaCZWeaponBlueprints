package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;

import net.minecraft.resources.ResourceLocation;

/** Immutable coarse server policy for datapack-authored Research Point awards. */
public record ResearchPointAwardConfigSnapshot(
        boolean awardsEnabled,
        boolean combatAwardsEnabled,
        int pointCap,
        ResourceLocation activeProfileId) {
    public ResearchPointAwardConfigSnapshot {
        if (pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || activeProfileId == null
                || activeProfileId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid Research Point award configuration");
        }
    }

    public static ResearchPointAwardConfigSnapshot from(
            BlueprintConfig config,
            BlueprintProgressionConfigSnapshot progression) {
        if (config == null || progression == null) {
            throw new IllegalArgumentException("Research Point award configuration cannot be null");
        }
        return new ResearchPointAwardConfigSnapshot(
                config.enableResearchPointAwards.get(),
                config.enableCombatResearchPointAwards.get(),
                progression.pointCap(),
                progression.activeProfileId());
    }
}
