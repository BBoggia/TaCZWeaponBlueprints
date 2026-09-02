package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

/** Effective settings produced by a preset without mutating custom values. */
public record BlueprintBalanceSettings(
        BlueprintBalancePreset preset,
        JournalVisibility maximumUndiscoveredVisibility,
        double lootChance,
        int minimumLootRolls,
        int maximumLootRolls) {

    public BlueprintBalanceSettings {
        if (preset == null || maximumUndiscoveredVisibility == null) {
            throw new IllegalArgumentException("balance settings contain null required state");
        }
        if (!Double.isFinite(lootChance) || lootChance < 0.0 || lootChance > 1.0) {
            throw new IllegalArgumentException("blueprint loot chance must be between zero and one");
        }
        if (minimumLootRolls < 0 || maximumLootRolls < minimumLootRolls) {
            throw new IllegalArgumentException("blueprint loot roll range is invalid");
        }
    }

    public static BlueprintBalanceSettings resolve(
            BlueprintBalancePreset preset,
            JournalVisibility customVisibility,
            double customChance,
            int customMinimum,
            int customMaximum) {
        BlueprintBalancePreset stablePreset = preset == null
                ? BlueprintBalancePreset.CUSTOM
                : preset;
        return switch (stablePreset) {
            case CUSTOM -> new BlueprintBalanceSettings(
                    stablePreset,
                    customVisibility,
                    customChance,
                    customMinimum,
                    Math.max(customMinimum, customMaximum));
            case ACCESSIBLE -> new BlueprintBalanceSettings(
                    stablePreset, JournalVisibility.FULL, 0.35, 1, 3);
            case BALANCED -> new BlueprintBalanceSettings(
                    stablePreset, JournalVisibility.FULL, 0.20, 1, 2);
            case SCARCE -> new BlueprintBalanceSettings(
                    stablePreset, JournalVisibility.NAME, 0.10, 1, 1);
        };
    }
}
