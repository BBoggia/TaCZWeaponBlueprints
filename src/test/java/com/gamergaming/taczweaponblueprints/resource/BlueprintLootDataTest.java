package com.gamergaming.taczweaponblueprints.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.api.BlueprintSpawnRate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class BlueprintLootDataTest {

    private static final Set<String> TIERS = Set.of("easy", "medium", "hard", "village", "nether", "water");

    @Test
    void allRequiredTierResourcesLoadAndValidate() {
        Map<String, JsonArray> spawnRates = LootTableResourceLoader.getAllLootTableSpawnRates();
        Map<String, JsonObject> lootTables = LootTableResourceLoader.getAllLootTableLists();

        assertEquals(TIERS, spawnRates.keySet());
        assertEquals(TIERS, lootTables.keySet());

        for (String tier : TIERS) {
            assertFalse(spawnRates.get(tier).isEmpty(), () -> tier + " spawn-rate pool is empty");
            spawnRates.get(tier).forEach(element -> BlueprintSpawnRate.fromJson(element.getAsJsonObject()));

            Set<String> seenLootTables = new HashSet<>();
            for (Map.Entry<String, JsonElement> namespaceEntry : lootTables.get(tier).entrySet()) {
                for (JsonElement lootTableElement : namespaceEntry.getValue().getAsJsonArray()) {
                    String lootTableId = lootTableElement.getAsString();
                    assertTrue(
                            lootTableId.startsWith(namespaceEntry.getKey() + ":"),
                            () -> lootTableId + " is grouped under the wrong namespace");
                    assertTrue(
                            seenLootTables.add(lootTableId),
                            () -> lootTableId + " is duplicated within tier " + tier);
                }
            }
        }
    }

    @Test
    void spawnRateRejectsInvalidIdsAndWeights() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintSpawnRate("Invalid", 1.0f, "invalid id"));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintSpawnRate("Zero", 0.0f, "test:zero"));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintSpawnRate("NaN", Float.NaN, "test:nan"));
        assertThrows(IllegalArgumentException.class, () -> BlueprintSpawnRate.fromJson(new JsonObject()));
    }
}
