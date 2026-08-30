package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class ResearchAutomaticPlacementFixtureTest {
    @Test
    void phaseZeroFixtureCoversTheReservedAutomaticPlacementScenarios() throws Exception {
        JsonObject root;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "fixtures/research-auto-placement-phase-0.json")) {
            assertTrue(stream != null, "missing automatic placement fixture");
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        assertEquals(1, root.get("format").getAsInt());
        JsonArray catalogs = root.getAsJsonArray("catalogs");
        Set<String> catalogIds = new LinkedHashSet<>();
        for (var catalogElement : catalogs) {
            JsonObject catalog = catalogElement.getAsJsonObject();
            assertTrue(catalogIds.add(catalog.get("id").getAsString()));
            Set<ResourceLocation> blueprintIds = new LinkedHashSet<>();
            for (var weaponElement : catalog.getAsJsonArray("weapons")) {
                JsonObject weapon = weaponElement.getAsJsonObject();
                assertTrue(blueprintIds.add(new ResourceLocation(
                        weapon.get("blueprint").getAsString())));
                assertTrue(!weapon.get("archetype").getAsString().isBlank());
                assertTrue(weapon.getAsJsonObject("stats").size() > 0);
            }
        }
        assertEquals(
                Set.of(
                        "small_pack",
                        "multiple_packs",
                        "missing_statistics",
                        "script_controlled",
                        "mechanical_extremes",
                        "unusual_valid_identifier"),
                catalogIds);

        JsonObject duplicate = root.getAsJsonArray("invalid_catalogs")
                .get(0).getAsJsonObject();
        assertEquals("duplicate_blueprint", duplicate.get("id").getAsString());
        List<String> ids = duplicate.getAsJsonArray("weapons").asList().stream()
                .map(value -> value.getAsJsonObject().get("blueprint").getAsString())
                .toList();
        assertEquals(1, new LinkedHashSet<>(ids).size());
        assertEquals(2, ids.size());
    }
}
