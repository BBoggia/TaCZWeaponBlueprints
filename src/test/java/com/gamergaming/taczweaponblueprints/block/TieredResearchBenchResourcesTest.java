package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Resource and placement contracts for the three directly craftable Research Benches. */
class TieredResearchBenchResourcesTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final String NAMESPACE = "taczweaponblueprints:";
    private static final List<String> RESEARCH_BENCH_IDS = List.of(
            "research_bench",
            "advanced_research_bench",
            "experimental_research_bench");
    private static final List<String> REMOVED_UPGRADE_KIT_IDS = List.of(
            "tier_2_research_bench_upgrade_kit",
            "tier_3_research_bench_upgrade_kit",
            "workbench_lvl2_upgrade_kit",
            "workbench_lvl3_upgrade_kit");

    @Test
    void everyTierHasAnIndependentDiscoverableRecipe() throws IOException {
        for (String id : RESEARCH_BENCH_IDS) {
            JsonObject recipe = json(
                    "src/main/resources/data/taczweaponblueprints/recipes/" + id + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(NAMESPACE + id,
                    recipe.getAsJsonObject("result").get("item").getAsString());
            assertFalse(recipe.getAsJsonObject("key").toString().contains(NAMESPACE));

            JsonObject advancement = json(
                    "src/main/resources/data/taczweaponblueprints/advancements/recipes/misc/"
                            + id + ".json");
            assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
            assertEquals(Set.of(NAMESPACE + id), advancement.getAsJsonObject("rewards")
                    .getAsJsonArray("recipes").asList().stream()
                    .map(value -> value.getAsString())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }

    @Test
    void removedUpgradeKitsStayOutOfTheRuntimeAndResourceSurface() throws IOException {
        String items = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/init/ModItems.java");
        String researchBlock = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/block/ResearchBenchBlock.java");
        String craftingBlock = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/block/CraftingWorkbenchBlock.java");

        assertFalse(items.contains("UpgradeKitItem"));
        assertFalse(researchBlock.contains("UpgradeOutcome"));
        assertFalse(craftingBlock.contains("UpgradeOutcome"));
        for (String id : REMOVED_UPGRADE_KIT_IDS) {
            assertFalse(items.contains(id));
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/data/taczweaponblueprints/recipes/" + id + ".json")));
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/data/taczweaponblueprints/advancements/recipes/misc/"
                            + id + ".json")));
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/assets/taczweaponblueprints/models/item/" + id + ".json")));
        }
    }

    @Test
    void initialPlacementStillCommitsOrRollsBackBothBenchHalves() throws IOException {
        String block = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/block/ResearchBenchBlock.java");
        String item = source(
                "src/main/java/com/gamergaming/taczweaponblueprints/item/ResearchBenchItem.java");

        assertTrue(item.contains("ResearchBenchBlock.placeCompleteStructure("));
        assertTrue(block.contains("public static boolean placeCompleteStructure("));
        assertTrue(block.contains("AtomicTwoPartReplacement.replace("));
        assertTrue(block.contains("publishCurrentStates(level, rootPos, extensionPos"));
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(source(path)).getAsJsonObject();
    }

    private static String source(String path) throws IOException {
        return Files.readString(PROJECT.resolve(path));
    }
}
