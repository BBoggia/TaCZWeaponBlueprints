package com.gamergaming.taczweaponblueprints.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Packaged-resource and integration anchors for the Phase 9 fragment supply. */
class BlueprintFragmentPhaseNineContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void fragmentHasOneStackableRegistryIdentityAndAReadableIconModel()
            throws IOException {
        String items = read("src/main/java/com/gamergaming/taczweaponblueprints/init/ModItems.java");
        JsonObject model = readJson(
                "src/main/resources/assets/taczweaponblueprints/models/item/blueprint_fragment.json");

        assertTrue(items.contains("ITEMS.register(\"blueprint_fragment\""));
        assertTrue(items.contains("new Item.Properties().stacksTo(64)"));
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals(
                "taczweaponblueprints:item/blueprint",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void bothDynamicAndLegacyDistributionUseReplacementRatherThanExtraRolls()
            throws IOException {
        String dynamic = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/loot/DynamicBlueprintLootModifier.java");
        String legacy = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/loot/AddItemsModifier.java");

        for (String source : java.util.List.of(dynamic, legacy)) {
            assertTrue(source.contains("BlueprintFragmentLootResolver.resolveRuntime"));
            assertTrue(source.contains("replaceWithFragment"));
            assertTrue(source.contains("BlueprintFragmentItem.create"));
            assertTrue(source.contains("fragmentPlan.canReplace()"));
        }
        assertTrue(dynamic.indexOf("replaceWithFragment")
                < dynamic.indexOf("BlueprintItem.createBlueprint"));
        assertTrue(legacy.indexOf("replaceWithFragment")
                < legacy.lastIndexOf("selected.value().copy()"));
    }

    @Test
    void fragmentCopyExplainsValidMalformedAndRemovedTargets() throws IOException {
        JsonObject language = readJson(
                "src/main/resources/assets/taczweaponblueprints/lang/en_us.json");
        for (String key : java.util.List.of(
                "item.taczweaponblueprints.blueprint_fragment",
                "item.taczweaponblueprints.blueprint_fragment.named",
                "item.taczweaponblueprints.blueprint_fragment.invalid_name",
                "item.taczweaponblueprints.blueprint_fragment.unknown_name",
                "item.taczweaponblueprints.blueprint_fragment.tooltip.target",
                "item.taczweaponblueprints.blueprint_fragment.tooltip.archive",
                "item.taczweaponblueprints.blueprint_fragment.tooltip.invalid",
                "item.taczweaponblueprints.blueprint_fragment.tooltip.unknown",
                "commands.taczweaponblueprints.loot.preview.fragments")) {
            assertTrue(language.has(key), key);
        }
    }

    @Test
    void diagnosticsRetainTierThresholdsAndExactOverrideCounts() throws IOException {
        String command = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/command/sub/BlueprintLootCommand.java");
        assertTrue(command.contains("fragments.thresholdCounts()"));
        assertTrue(command.contains("Candidate::exactThreshold"));
        assertTrue(command.contains("fragments.expectedFragments(policy.expectedAdditions())"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(PROJECT.resolve(relativePath))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
