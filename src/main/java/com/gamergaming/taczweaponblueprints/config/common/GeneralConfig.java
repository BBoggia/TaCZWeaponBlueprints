package com.gamergaming.taczweaponblueprints.config.common;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GeneralConfig {

    public static ModConfigSpec.BooleanValue DEBUG_MODE;


    public static void init(ModConfigSpec.Builder builder) {
        builder.push("General Config");

        builder.comment("Debug mode");
        DEBUG_MODE = builder.define("Debug Mode", false);

        builder.pop();
    }

}
