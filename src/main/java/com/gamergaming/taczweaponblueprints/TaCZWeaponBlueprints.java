package com.gamergaming.taczweaponblueprints;

import org.slf4j.Logger;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.init.ModBlocks;
import com.gamergaming.taczweaponblueprints.init.ModCreativeTabs;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.init.ModMenus;
import com.gamergaming.taczweaponblueprints.loot.ModLootModifier;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TaCZWeaponBlueprints.MODID)
public class TaCZWeaponBlueprints {

    public static final String MODID = "taczweaponblueprints";

    public static final Logger LOGGER = LogUtils.getLogger();

    public TaCZWeaponBlueprints(IEventBus modEventBus) {

        ModConfigs.init();
        com.gamergaming.taczweaponblueprints.resource.StationRecipeCondition.register(modEventBus);
        com.gamergaming.taczweaponblueprints.init.ModAttachments.register(modEventBus);
        modEventBus.addListener(NetworkHandler::registerPackets);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        com.gamergaming.taczweaponblueprints.init.ModVillagers.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModLootModifier.register(modEventBus);

        modEventBus.addListener(ModCreativeTabs::buildCreativeModeTabs);
    }

    public static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
