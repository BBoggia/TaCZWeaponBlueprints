package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Durable resource contracts; native probe covers registered blocks and menu actions. */
class AnalyzerStationResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final String ASSETS = "assets/taczweaponblueprints/";
    private static final String DATA = "data/taczweaponblueprints/";
    private static final List<String> IDS = List.of("blueprint_recycler",
            "advanced_blueprint_analyzer", "experimental_blueprint_analyzer");

    @Test void allFacingsRenderOnlyTheRootOfTheTwoBlockStation() throws Exception {
        for (String id : IDS) {
            var variants = json(ASSETS + "blockstates/" + id + ".json").getAsJsonObject("variants");
            assertEquals(8, variants.size());
            int rotation = 0;
            for (String facing : List.of("north", "east", "south", "west")) {
                var variant = variants.getAsJsonObject("extension=false,facing=" + facing);
                assertEquals("taczweaponblueprints:block/" + id, variant.get("model").getAsString());
                assertEquals(rotation, variant.has("y") ? variant.get("y").getAsInt() : 0);
                assertEquals("minecraft:block/air", variants.getAsJsonObject("extension=true,facing=" + facing)
                        .get("model").getAsString());
                rotation += 90;
            }
            for (String domain : List.of("block", "item")) {
                var model = json(ASSETS + "models/" + domain + "/" + id + ".json");
                assertEquals(id.equals("blueprint_recycler") ? "neoforge:composite" : "neoforge:obj",
                        model.get("loader").getAsString());
            }
        }
    }

    @Test void recipesUnlockAndDropTheMatchingStation() throws Exception {
        var pickaxes = json("data/minecraft/tags/block/mineable/pickaxe.json").getAsJsonArray("values");
        for (String id : IDS) {
            String item = "taczweaponblueprints:" + id;
            var recipe = json(DATA + "recipe/" + id + ".json");
            assertEquals(item, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(3, recipe.getAsJsonArray("pattern").size());
            var key = recipe.getAsJsonObject("key");
            for (var row : recipe.getAsJsonArray("pattern")) {
                assertEquals(3, row.getAsString().length());
                for (char symbol : row.getAsString().toCharArray()) assertTrue(key.has(String.valueOf(symbol)));
            }
            var advancement = json(DATA + "advancement/recipes/misc/" + id + ".json");
            assertEquals(item, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
            var loot = json(DATA + "loot_table/blocks/" + id + ".json").getAsJsonArray("pools");
            assertEquals(1, loot.size());
            assertEquals(1, loot.get(0).getAsJsonObject().get("rolls").getAsInt());
            assertEquals(item, loot.get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject().get("name").getAsString());
            var conditions = loot.get(0).getAsJsonObject().getAsJsonArray("conditions");
            assertTrue(conditions.asList().stream().map(value -> value.getAsJsonObject()).anyMatch(condition ->
                    condition.get("condition").getAsString().equals("minecraft:block_state_property")
                            && condition.getAsJsonObject("properties").get("extension").getAsString().equals("false")));
            assertTrue(pickaxes.asList().stream().anyMatch(value -> value.getAsString().equals(item)), id);
        }
    }

    @Test void stationsHaveDistinctEquipmentAndTranslatedTitles() throws Exception {
        var lang = json(ASSETS + "lang/en_us.json");
        for (int i = 0; i < IDS.size(); i++) {
            String id = IDS.get(i);
            assertEquals(lang.get("block.taczweaponblueprints." + id), lang.get("container.taczweaponblueprints." + id));
            String geometry = Files.readString(ROOT.resolve(ASSETS + "models/block/" + id + ".obj"));
            assertTrue(geometry.contains(switch (i) {
                case 0 -> "manual_magnifier_vertical_rod";
                case 1 -> "binocular_cream_eyepiece";
                default -> "crt";
            }), "Distinct inspection equipment for tier " + (i + 1));
        }
        assertTrue(lang.get("item.taczweaponblueprints.blueprint_analyzer.tooltip.tier").getAsString().contains("%s"));
    }

    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }
}
