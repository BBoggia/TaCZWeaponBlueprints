package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootDataManager;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        TaCZWeaponBlueprints.LOGGER.info("Server starting, initializing BlueprintDataManager...");
        if (BlueprintDataManager.SERVER.initialize(server)) {
            TaCZWeaponBlueprints.LOGGER.info("BlueprintDataManager initialized.");
        } else {
            TaCZWeaponBlueprints.LOGGER.error("BlueprintDataManager initialization failed.");
        }

        BlueprintLootSnapshot lootSnapshot = BlueprintLootDataManager.INSTANCE.snapshot();
        if (lootSnapshot.active()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Dynamic blueprint loot distribution active: {} tags, {} pools, {} rules, "
                            + "{} exact bindings, {} selector rules",
                    lootSnapshot.tags().size(),
                    lootSnapshot.pools().size(),
                    lootSnapshot.rules().size(),
                    lootSnapshot.bindingCount(),
                    lootSnapshot.selectorBindings().size());
        } else if (lootSnapshot.globallyDisablesDistribution()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Dynamic blueprint loot distribution owns the system with no enabled bindings; "
                            + "blueprint loot is disabled by datapack data");
        } else if (lootSnapshot.ownsDistribution()) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Only targeted disabled blueprint loot rules are present; "
                            + "legacy fallback remains active for untargeted loot tables");
        } else {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "No dynamic blueprint loot rules are active; retaining legacy modifier behavior");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BlueprintProgressionSyncScheduler.clearAll();
    }
}
