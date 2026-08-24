package com.gamergaming.taczweaponblueprints.loot;

import java.util.HashSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;

public final class BlueprintLootRuntimeConfig {
    private BlueprintLootRuntimeConfig() {
    }

    public static BlueprintLootPolicyResolver.RuntimeDefaults capture() {
        BlueprintConfig config = ModConfigs.BLUEPRINT;
        Set<String> excluded = new HashSet<>();
        excluded.addAll(config.gunBlacklist);
        excluded.addAll(config.ammoBlacklist);
        excluded.addAll(config.attachmentBlacklist);
        Set<String> stableExcluded = Set.copyOf(excluded);
        return new BlueprintLootPolicyResolver.RuntimeDefaults(
                config.enableBlueprints.get(),
                config.blueprintSpawnChance.get(),
                config.minBlueprints.get(),
                config.maxBlueprints.get(),
                blueprintId -> stableExcluded.contains(blueprintId.toString()));
    }
}
