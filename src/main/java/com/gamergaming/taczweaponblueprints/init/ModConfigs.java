package com.gamergaming.taczweaponblueprints.init;

import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.GeneralConfig;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;

public class ModConfigs {

    public static final String BASE_KEY = "config." + TaCZWeaponBlueprints.MODID + ".";

    // public static final GeneralConfig GENERAL = register(GeneralConfig::new);
    public static final BlueprintConfig BLUEPRINT = register(BlueprintConfig::new);

    public static void init() {
    }

    private static <T extends Config> T register(Supplier<T> supplier) {
        return ConfigApiJava.registerAndLoadConfig(supplier, RegisterType.CLIENT);
    }
}