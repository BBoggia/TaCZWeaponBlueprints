package com.gamergaming.taczweaponblueprints.config;


import com.gamergaming.taczweaponblueprints.config.common.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.config.common.GeneralConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigHandler {
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // GeneralConfig.init(builder);
        BlueprintConfig.init(builder);

        SPEC = builder.build();
    }
}
