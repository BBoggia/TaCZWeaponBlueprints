package com.gamergaming.taczweaponblueprints.config.common;

import java.util.List;
import java.util.stream.Collectors;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeConfigSpec;

public class BlueprintConfig {
    public static ForgeConfigSpec.DoubleValue BLUEPRINT_SPAWN_RATE;
    public static ForgeConfigSpec.Range<Integer> MIN_MAX_BLUEPRINTS_TO_SPAWN;

    public static void init(ForgeConfigSpec.Builder builder) {
            builder.push("TaCZ Blueprint Config");

            BLUEPRINT_SPAWN_RATE = builder
                    .comment("Blueprint spawn rate")
                    .defineInRange("blueprintSpawnRate", 0.1, 0.0, 1.0);

            // MIN_MAX_BLUEPRINTS_TO_SPAWN = builder
            //         .comment("Min and max blueprints to spawn")
            //         .

            builder.pop();
    }

    // Returns list of all chest loot tables pulled using forge
    private static List<String> getLootTables() {
        List<String> lootTables = LootTable.DEFAULT_PARAM_SET.getAllowed().stream().filter((resourceLocation) -> {
            return resourceLocation.getName().getPath().startsWith("chests/");
        }).map((resourceLocation) -> {
            return resourceLocation.toString();
        }).collect(Collectors.toList());
        TaCZWeaponBlueprints.LOGGER.info("Found loot tables: " + lootTables);
        return lootTables;
    }
}