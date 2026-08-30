package com.gamergaming.taczweaponblueprints.loot;

import java.util.HashSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

public final class BlueprintLootRuntimeConfig {
    private BlueprintLootRuntimeConfig() {
    }

    public static BlueprintLootPolicyResolver.RuntimeDefaults capture() {
        BlueprintConfig config = ModConfigs.BLUEPRINT;
        Set<String> excluded = new HashSet<>();
        excluded.addAll(config.gunBlacklist);
        excluded.addAll(config.ammoBlacklist);
        excluded.addAll(config.attachmentBlacklist);
        BlueprintProgressionAccess.exemptBlueprintIds(
                        config.accessSnapshot(),
                        BlueprintDataManager.SERVER.getBlueprintDataMap())
                .forEach(id -> excluded.add(id.toString()));
        Set<String> stableExcluded = Set.copyOf(excluded);
        var balance = config.balanceSettings();
        return new BlueprintLootPolicyResolver.RuntimeDefaults(
                config.enableBlueprints.get(),
                balance.lootChance(),
                balance.minimumLootRolls(),
                balance.maximumLootRolls(),
                blueprintId -> stableExcluded.contains(blueprintId.toString()));
    }
}
