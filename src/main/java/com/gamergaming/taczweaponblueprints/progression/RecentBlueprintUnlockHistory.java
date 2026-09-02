package com.gamergaming.taczweaponblueprints.progression;

import java.util.Collection;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.RecentBlueprintUnlockBatch;

import net.minecraft.resources.ResourceLocation;

/** Maps trusted learning origins into the bounded player-facing recent history. */
final class RecentBlueprintUnlockHistory {
    private RecentBlueprintUnlockHistory() {
    }

    static boolean record(
            IPlayerRecipeData playerData,
            BlueprintUnlockOrigin origin,
            ResourceLocation target,
            Collection<ResourceLocation> members) {
        if (playerData == null || origin == null || !origin.recentHistoryEligible()
                || target == null || members == null || members.isEmpty()) {
            return false;
        }
        RecentBlueprintUnlockBatch.Source source = switch (origin) {
            case TREE_RESEARCH -> RecentBlueprintUnlockBatch.Source.TREE_RESEARCH;
            case PHYSICAL_BLUEPRINT -> RecentBlueprintUnlockBatch.Source.PHYSICAL_BLUEPRINT;
            case ADMINISTRATOR -> RecentBlueprintUnlockBatch.Source.ADMINISTRATOR;
            case STARTING_GRANT, MIGRATION -> throw new IllegalArgumentException(
                    "ineligible blueprint origin cannot enter recent history");
        };
        try {
            return playerData.recordRecentUnlockBatch(
                    source,
                    target.toString(),
                    members.stream().map(ResourceLocation::toString).toList());
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Blueprint learning committed, but its recent-history record failed",
                    exception);
            return false;
        }
    }
}
