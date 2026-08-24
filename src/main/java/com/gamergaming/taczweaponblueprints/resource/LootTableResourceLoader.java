package com.gamergaming.taczweaponblueprints.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.init.ModLootTables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LootTableResourceLoader {

    private LootTableResourceLoader() {
    }

    public static Map<String, JsonArray> getAllLootTableSpawnRates() {
        Map<String, JsonArray> spawnRates = new LinkedHashMap<>();
        spawnRates.put("easy", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.EASY_LOOT_TABLE_ITEMS));
        spawnRates.put("medium", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.MEDIUM_LOOT_TABLE_ITEMS));
        spawnRates.put("hard", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.HARD_LOOT_TABLE_ITEMS));
        spawnRates.put("village", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.VILLAGE_LOOT_TABLE_ITEMS));
        spawnRates.put("nether", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.NETHER_LOOT_TABLE_ITEMS));
        spawnRates.put("water", readRequiredArray(ModLootTables.BlueprintLootTableSpawnRates.WATER_LOOT_TABLE_ITEMS));
        return Collections.unmodifiableMap(spawnRates);
    }

    public static Map<String, JsonObject> getAllLootTableLists() {
        Map<String, JsonObject> lootTables = new LinkedHashMap<>();
        lootTables.put("easy", readRequiredObject(ModLootTables.BlueprintLootTableLists.EASY_LOOT_TABLES));
        lootTables.put("medium", readRequiredObject(ModLootTables.BlueprintLootTableLists.MEDIUM_LOOT_TABLES));
        lootTables.put("hard", readRequiredObject(ModLootTables.BlueprintLootTableLists.HARD_LOOT_TABLES));
        lootTables.put("village", readRequiredObject(ModLootTables.BlueprintLootTableLists.VILLAGE_LOOT_TABLES));
        lootTables.put("nether", readRequiredObject(ModLootTables.BlueprintLootTableLists.NETHER_LOOT_TABLES));
        lootTables.put("water", readRequiredObject(ModLootTables.BlueprintLootTableLists.WATER_LOOT_TABLES));
        return Collections.unmodifiableMap(lootTables);
    }

    private static JsonArray readRequiredArray(String path) {
        JsonElement json = readRequiredJson(path);
        if (!json.isJsonArray()) {
            throw new IllegalStateException("Expected a JSON array in blueprint loot resource " + path);
        }
        return json.getAsJsonArray();
    }

    private static JsonObject readRequiredObject(String path) {
        JsonElement json = readRequiredJson(path);
        if (!json.isJsonObject()) {
            throw new IllegalStateException("Expected a JSON object in blueprint loot resource " + path);
        }
        return json.getAsJsonObject();
    }

    private static JsonElement readRequiredJson(String path) {
        try (InputStream stream = LootTableResourceLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing required blueprint loot resource " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Failed to read blueprint loot resource " + path, exception);
        }
    }
}
