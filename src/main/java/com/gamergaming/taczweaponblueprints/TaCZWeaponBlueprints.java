package com.gamergaming.taczweaponblueprints;

import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.init.ModCreativeTabs;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.loot.ModLootModifier;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.mojang.logging.LogUtils;
import com.tacz.guns.resource.CommonAssetsManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TaCZWeaponBlueprints.MODID)
public class TaCZWeaponBlueprints {

    public static final String MODID = "taczweaponblueprints";

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final org.apache.logging.log4j.Logger LOGGER4J = LogManager.getLogger(MODID);

    public TaCZWeaponBlueprints() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        LOGGER.info("HELLO FROM TaCZ Weapon Blueprints INITIALIZATION");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        // modEventBus.addListener(this::onAddReloadListeners);

        ModConfigs.init();

        MinecraftForge.EVENT_BUS.register(this);

        // Register ForgeConfigSpec
        // ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHandler.SPEC);

        NetworkHandler.registerPackets();

        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModLootModifier.register(modEventBus);

        modEventBus.addListener(ModCreativeTabs::buildCreativeModeTabs);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
//        BlueprintDataManager.INSTANCE.initialize();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
//        BlueprintDataManager.INSTANCE.initialize();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        LOGGER.info("HELLO from player join");
        // BlueprintDataManager.INSTANCE.initialize();
        // BlueprintDataManager.INSTANCE.initialize(event.getEntity().getServer());
        // CommonAssetsManager.INSTANCE.clearRecipes();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
        }
    }

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(MODID, path);
    }
}
