package com.gamergaming.taczweaponblueprints.resource;

import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModLootTables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class LootTableResourceLoader implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        
    }

    public static Map<String, JsonArray> getAllLootTableSpawnRates() {
        return getResourceLocationPathsFromClassFields(ModLootTables.BlueprintLootTableSpawnRates.class).stream()
                .collect(Collectors.toMap(path -> path.split("/")[path.split("/").length - 1].split(".json")[0], LootTableResourceLoader::getJsonArrayFromFile));
    }

    public static Map<String, JsonObject> getAllLootTableLists() {
        return getResourceLocationPathsFromClassFields(ModLootTables.BlueprintLootTableLists.class).stream()
                .collect(Collectors.toMap(path -> path.split("/")[path.split("/").length - 1].split(".json")[0], LootTableResourceLoader::getJsonObjectFromFile));
    }

    public static JsonArray getJsonArrayFromFile(String lootTableListPath) {
        TaCZWeaponBlueprints.LOGGER.info("Loading loot table list: " + lootTableListPath);
        InputStream stream = LootTableResourceLoader.class.getResourceAsStream(lootTableListPath);
        if (stream == null) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to load loot table list: " + lootTableListPath);
            return null;
        }
        return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonArray();
    }

    public static JsonObject getJsonObjectFromFile(String lootTableListPath) {
        TaCZWeaponBlueprints.LOGGER.info("Loading loot table list: " + lootTableListPath);
        InputStream stream = LootTableResourceLoader.class.getResourceAsStream(lootTableListPath);
        if (stream == null) {
            TaCZWeaponBlueprints.LOGGER.error("Failed to load loot table list: " + lootTableListPath);
            return null;
        }
        return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
    }

    public static Collection<String> getResourceLocationPathsFromClassFields(Class<?> clazz) {
        return Arrays.stream(clazz.getFields())
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .collect(Collectors.toList());
    }

    // Register your listener
    // public static void register(ResourceManagerReloadListener listener) {
    //     // Registration logic, depends on your mod setup
    //     // For Forge mods, you can register during the common setup event
    // }
}