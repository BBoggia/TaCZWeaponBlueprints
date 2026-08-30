package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Regression gates for the Phase 6 Recycler acquisition and discovery path. */
class ResearchWorkstationsPhaseSixTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final String RECIPE = "taczweaponblueprints:blueprint_recycler";

    @Test
    void survivalRecipeIsOrdinaryTaggedAndDoesNotConsumeTheResearchBench()
            throws IOException {
        JsonObject recipe = readJson(
                "src/main/resources/data/taczweaponblueprints/recipes/blueprint_recycler.json");

        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("misc", recipe.get("category").getAsString());
        assertEquals("IRI", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("IGI", recipe.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("IHI", recipe.getAsJsonArray("pattern").get(2).getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("forge:ingots/iron", key.getAsJsonObject("I").get("tag").getAsString());
        assertEquals("minecraft:redstone", key.getAsJsonObject("R").get("item").getAsString());
        assertEquals("minecraft:grindstone", key.getAsJsonObject("G").get("item").getAsString());
        assertEquals("minecraft:hopper", key.getAsJsonObject("H").get("item").getAsString());
        assertEquals(RECIPE, recipe.getAsJsonObject("result").get("item").getAsString());
        assertTrue(recipe.get("show_notification").getAsBoolean());
        assertTrue(recipe.toString().indexOf("research_bench") < 0);
    }

    @Test
    void relevantItemsUnlockOneRecipeBookNotificationThroughAnOrRequirement()
            throws IOException {
        JsonObject advancement = readJson(
                "src/main/resources/data/taczweaponblueprints/advancements/recipes/misc/blueprint_recycler.json");
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(
                Set.of("has_blueprint", "has_research_bench", "has_research_data"),
                criteria.keySet());
        criteria.entrySet().forEach(entry -> assertEquals(
                "minecraft:inventory_changed",
                entry.getValue().getAsJsonObject().get("trigger").getAsString()));

        assertEquals(
                Set.of("taczweaponblueprints:blueprint"),
                criterionItems(criteria, "has_blueprint"));
        assertEquals(
                Set.of("taczweaponblueprints:research_bench"),
                criterionItems(criteria, "has_research_bench"));
        assertEquals(
                Set.of(
                        "taczweaponblueprints:research_note",
                        "taczweaponblueprints:research_report",
                        "taczweaponblueprints:research_dossier"),
                criterionItems(criteria, "has_research_data"));
        assertEquals(
                criteria.keySet(),
                strings(advancement.getAsJsonArray("requirements").get(0).getAsJsonArray()));
        assertEquals(
                Set.of(RECIPE),
                strings(advancement.getAsJsonObject("rewards").getAsJsonArray("recipes")));
    }

    @Test
    void functionalBlocksTabPlacesTheRecyclerBesideItsResearchBench() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/init/ModCreativeTabs.java"));
        int bench = source.indexOf("event.accept(ModItems.RESEARCH_BENCH_ITEM.get());");
        int recycler = source.indexOf("event.accept(ModItems.BLUEPRINT_RECYCLER_ITEM.get());");
        int note = source.indexOf("event.accept(ModItems.RESEARCH_NOTE.get());");

        assertTrue(bench >= 0);
        assertTrue(bench < recycler);
        assertTrue(recycler < note);
    }

    private static Set<String> criterionItems(JsonObject criteria, String name) {
        return strings(criteria.getAsJsonObject(name)
                .getAsJsonObject("conditions")
                .getAsJsonArray("items")
                .get(0).getAsJsonObject()
                .getAsJsonArray("items"));
    }

    private static Set<String> strings(JsonArray array) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return Set.copyOf(values);
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(PROJECT.resolve(relativePath))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
