package com.gamergaming.taczweaponblueprints.config.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class GeneralConfig {

    public static ForgeConfigSpec.BooleanValue DEBUG_MODE;

    
    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("General Config");

        builder.comment("Debug mode");
        DEBUG_MODE = builder.define("Debug Mode", false);

        builder.pop();
    }
    
}
