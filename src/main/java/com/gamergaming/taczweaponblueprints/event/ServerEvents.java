package com.gamergaming.taczweaponblueprints.event;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        TaCZWeaponBlueprints.LOGGER.info("Server starting, initializing BlueprintDataManager...");
        BlueprintDataManager.INSTANCE.initialize(server);
        TaCZWeaponBlueprints.LOGGER.info("BlueprintDataManager initialized.");

        Set<ResourceLocation> chestLootTables;

        try {
            chestLootTables = getAllChestLootTables(server);
        } catch (IOException e) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to get chest loot tables: " + e.getMessage());
            return;
        }

        TaCZWeaponBlueprints.LOGGER.info("Found following chest loot tables: " + chestLootTables.toArray().toString());
    }

    public static Set<ResourceLocation> getAllChestLootTables(MinecraftServer server) throws IOException {
        Set<ResourceLocation> chestLootTables = new HashSet<>();
        ResourceManager resourceManager = server.getResourceManager();

        String lootTablePrefix = "loot_tables/chests";

        // Lists all resources under "loot_tables/chests" that end with ".json"
        Collection<ResourceLocation> resources = resourceManager.listResources(lootTablePrefix, resourcePath -> resourcePath.getPath().endsWith(".json")).keySet();

        for (ResourceLocation resourceLocation : resources) {

            String path = resourceLocation.getPath();

            if (path.startsWith("loot_tables/") && path.endsWith(".json")) {
                String lootTablePath = path.substring("loot_tables/".length(), path.length() - ".json".length());

                ResourceLocation lootTableId = new ResourceLocation(resourceLocation.getNamespace(), lootTablePath);
                chestLootTables.add(lootTableId);
            }
        }

        return chestLootTables;
    }
}
