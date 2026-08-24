package com.gamergaming.taczweaponblueprints.resource.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.api.BlueprintSpawnRate;
import com.gamergaming.taczweaponblueprints.resource.LootTableResourceLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintLootParityTest {
    private static final List<String> TIERS = List.of("easy", "medium", "hard", "village", "nether", "water");
    private static final String DATA_ROOT = "/data/taczweaponblueprints/taczweaponblueprints/";

    @Test
    void versionedDatapackDefinitionsPreserveAllLegacyTierContent() {
        Map<String, JsonArray> legacyPools = LootTableResourceLoader.getAllLootTableSpawnRates();
        Map<String, JsonObject> legacyRules = LootTableResourceLoader.getAllLootTableLists();
        Map<ResourceLocation, BlueprintLootPool> pools = new LinkedHashMap<>();
        Map<ResourceLocation, BlueprintLootRule> rules = new LinkedHashMap<>();

        for (String tier : TIERS) {
            BlueprintLootPool pool = read(
                    BlueprintLootPool.CODEC,
                    DATA_ROOT + "loot_pools/" + tier + ".json");
            BlueprintLootRule rule = read(
                    BlueprintLootRule.CODEC,
                    DATA_ROOT + "loot_rules/" + tier + ".json");
            pools.put(id("taczweaponblueprints:" + tier), pool);
            rules.put(id("taczweaponblueprints:" + tier), rule);

            assertEquals(legacyPoolEntries(legacyPools.get(tier)), pool.entries(), tier + " pool changed");
            assertEquals(legacyLootTables(legacyRules.get(tier)), rule.lootTables(), tier + " rule changed");
        }

        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(pools, rules);
        assertEquals(748, snapshot.bindingCount());
        assertEquals(744, snapshot.rulesByLootTable().size());
        assertTrue(snapshot.active());
    }

    @Test
    void packagedGlobalModifierIndexIncludesDynamicEntry() {
        JsonObject globalIndex = readJson("/data/forge/loot_modifiers/global_loot_modifiers.json").getAsJsonObject();
        Set<String> entries = new LinkedHashSet<>();
        globalIndex.getAsJsonArray("entries").forEach(element -> entries.add(element.getAsString()));

        assertTrue(entries.contains("taczweaponblueprints:dynamic_blueprints"));
        assertEquals(486, entries.size());
    }

    private static List<BlueprintLootEntry> legacyPoolEntries(JsonArray legacyPool) {
        List<BlueprintLootEntry> entries = new ArrayList<>();
        for (JsonElement element : legacyPool) {
            BlueprintSpawnRate spawnRate = BlueprintSpawnRate.fromJson(element.getAsJsonObject());
            float historicalWeight = Math.round(spawnRate.score() * 100 * 1000) / 1000.0f;
            entries.add(new BlueprintLootEntry(id(spawnRate.id()), historicalWeight));
        }
        return List.copyOf(entries);
    }

    private static List<ResourceLocation> legacyLootTables(JsonObject legacyRule) {
        List<ResourceLocation> lootTables = new ArrayList<>();
        legacyRule.entrySet().forEach(namespaceEntry -> namespaceEntry.getValue().getAsJsonArray()
                .forEach(element -> lootTables.add(id(element.getAsString()))));
        return List.copyOf(lootTables);
    }

    private static <T> T read(Codec<T> codec, String path) {
        return codec.parse(JsonOps.INSTANCE, readJson(path)).result().orElseThrow();
    }

    private static JsonElement readJson(String path) {
        try (InputStream stream = BlueprintLootParityTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(value);
        }
        return id;
    }
}
