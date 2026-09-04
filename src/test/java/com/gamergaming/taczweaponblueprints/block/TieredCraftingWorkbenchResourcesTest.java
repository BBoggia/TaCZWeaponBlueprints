package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Resource, placement, and UI ownership contracts for dedicated crafting Workbenches. */
class TieredCraftingWorkbenchResourcesTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final List<String> WORKBENCH_IDS = List.of(
            "workbench_lvl1",
            "workbench_lvl2",
            "workbench_lvl3");

    @Test
    void allThreeBlocksAndItemsUseTheDedicatedCraftingTypes() throws IOException {
        String blocks = source("init/ModBlocks.java");
        String items = source("init/ModItems.java");

        for (String id : WORKBENCH_IDS) {
            assertTrue(blocks.contains('"' + id + '"'), id);
            assertTrue(items.contains('"' + id + '"'), id);
        }
        assertTrue(blocks.contains("new CraftingWorkbenchBlock("));
        assertTrue(items.contains("new CraftingWorkbenchItem("));
    }

    @Test
    void normalizedModelsOccupyOneTwoBlockSideBySideFootprint() throws IOException {
        for (String id : WORKBENCH_IDS) {
            double[] bounds = objBounds("src/main/resources/assets/taczweaponblueprints/models/block/"
                    + id + ".obj");
            assertTrue(bounds[0] >= -0.001D, id + " minimum X");
            assertTrue(bounds[1] <= 2.001D, id + " maximum X");
            assertTrue(bounds[2] >= -0.126D, id + " minimum Z");
            assertTrue(bounds[3] <= 1.126D, id + " maximum Z");

            String obj = Files.readString(PROJECT.resolve(
                    "src/main/resources/assets/taczweaponblueprints/models/block/"
                            + id + ".obj"));
            assertTrue(obj.contains("mtllib " + id + ".mtl"), id);
            assertTrue(obj.contains("\nf "), id);
        }
    }

    @Test
    void blockstatesRenderOnlyTheRootAndCoverEveryFacing() throws IOException {
        Set<String> expected = Set.of(
                "extension=false,facing=north",
                "extension=false,facing=east",
                "extension=false,facing=south",
                "extension=false,facing=west",
                "extension=true,facing=north",
                "extension=true,facing=east",
                "extension=true,facing=south",
                "extension=true,facing=west");

        for (String id : WORKBENCH_IDS) {
            JsonObject variants = json("src/main/resources/assets/taczweaponblueprints/blockstates/"
                    + id + ".json").getAsJsonObject("variants");
            assertEquals(expected, variants.keySet(), id);
            for (String key : expected) {
                String model = variants.getAsJsonObject(key).get("model").getAsString();
                assertEquals(
                        key.startsWith("extension=true")
                                ? "minecraft:block/air"
                                : "taczweaponblueprints:block/" + id,
                        model,
                        id + ' ' + key);
            }
        }
    }

    @Test
    void packagedTexturesAreBoundedAndVoxelModelsUseTheForgeAlias() throws IOException {
        for (String id : WORKBENCH_IDS) {
            BufferedImage texture = ImageIO.read(PROJECT.resolve(
                    "src/main/resources/assets/taczweaponblueprints/textures/block/"
                            + id + ".png").toFile());
            assertNotNull(texture, id);
            assertEquals(1024, texture.getWidth(), id);
            assertEquals(1024, texture.getHeight(), id);

            for (String domain : List.of("block", "item")) {
                JsonObject model = json(
                        "src/main/resources/assets/taczweaponblueprints/models/"
                                + domain + '/' + id + ".json");
                assertEquals("forge:obj", model.get("loader").getAsString());
                assertEquals("taczweaponblueprints:block/voxel_white",
                        model.getAsJsonObject("textures").get("texture0").getAsString());
                assertEquals("taczweaponblueprints:block/" + id,
                        model.getAsJsonObject("textures").get("particle").getAsString());
                if (domain.equals("item")) {
                    JsonObject display = model.getAsJsonObject("display");
                    assertEquals(-35, display.getAsJsonObject("gui")
                            .getAsJsonArray("rotation").get(1).getAsInt(), id + " GUI yaw");
                    assertEquals(-45, display.getAsJsonObject("firstperson_righthand")
                            .getAsJsonArray("rotation").get(1).getAsInt(), id + " held yaw");
                    assertEquals(-145, display.getAsJsonObject("thirdperson_righthand")
                            .getAsJsonArray("rotation").get(1).getAsInt(), id + " third-person yaw");
                }
                String material = Files.readString(PROJECT.resolve(
                        "src/main/resources/assets/taczweaponblueprints/models/"
                                + domain + '/' + id + ".mtl"));
                assertTrue(material.contains("map_Kd #texture0"), domain + '/' + id);
                assertTrue(material.contains("newmtl RGB_"), domain + '/' + id);
            }
        }

        BufferedImage white = ImageIO.read(PROJECT.resolve(
                "src/main/resources/assets/taczweaponblueprints/textures/block/voxel_white.png")
                .toFile());
        assertNotNull(white);
        assertEquals(16, white.getWidth());
        assertEquals(16, white.getHeight());
    }

    @Test
    void nativeTaCZDataAndRecipesBelongOnlyToCraftingWorkbenches() throws IOException {
        for (String id : WORKBENCH_IDS) {
            JsonObject data = json("src/main/resources/data/taczweaponblueprints/data/blocks/"
                    + id + ".json");
            JsonObject index = json("src/main/resources/data/taczweaponblueprints/index/blocks/"
                    + id + ".json");
            JsonObject recipe = json("src/main/resources/data/taczweaponblueprints/recipes/"
                    + id + ".json");

            assertEquals("taczweaponblueprints:workbench_all",
                    data.get("filter").getAsString());
            assertEquals("taczweaponblueprints:" + id, index.get("id").getAsString());
            assertEquals("taczweaponblueprints:" + id,
                    recipe.getAsJsonObject("result").get("item").getAsString());
            assertFalse(recipe.getAsJsonObject("key").toString().contains("workbench_lvl"));
        }
        for (String researchBench : List.of(
                "research_bench",
                "advanced_research_bench",
                "experimental_research_bench")) {
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/data/taczweaponblueprints/data/blocks/"
                            + researchBench + ".json")));
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/data/taczweaponblueprints/index/blocks/"
                            + researchBench + ".json")));
        }
    }

    @Test
    void directRecipesAreTheOnlyPublishedWorkbenchBuildPaths() throws IOException {
        String items = source("init/ModItems.java");
        String jei = source("compat/jei/TaCZWeaponBlueprintsJeiPlugin.java");
        String emi = source("compat/emi/TaCZWeaponBlueprintsEmiPlugin.java");
        assertFalse(items.contains("WORKBENCH_LVL2_UPGRADE_KIT"));
        assertFalse(items.contains("WORKBENCH_LVL3_UPGRADE_KIT"));
        assertFalse(items.contains("CraftingWorkbenchUpgradeKitItem"));
        assertTrue(jei.contains("Topic.CRAFTING_WORKBENCH"));
        assertTrue(emi.contains("Topic.CRAFTING_WORKBENCH"));

        for (String id : WORKBENCH_IDS) {
            JsonObject directRecipe = json(
                    "src/main/resources/data/taczweaponblueprints/recipes/"
                            + id + ".json");
            assertFalse(directRecipe.getAsJsonObject("key").toString()
                    .contains("taczweaponblueprints:workbench_lvl"));
        }
        for (String id : List.of(
                "workbench_lvl2_upgrade_kit",
                "workbench_lvl3_upgrade_kit")) {
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/data/taczweaponblueprints/recipes/" + id + ".json")));
            assertFalse(Files.exists(PROJECT.resolve(
                    "src/main/resources/assets/taczweaponblueprints/models/item/" + id + ".json")));
        }
    }

    @Test
    void researchAndCraftingScreensNoLongerOfferModeSwitching() throws IOException {
        String researchScreen = source("client/ResearchBenchScreen.java");
        String craftingScreen = source("mixin/GunSmithTableScreenMixin.java");
        String legacyTransition = source(
                "progression/workbench/ResearchWorkbenchMenuTransitions.java");

        assertFalse(researchScreen.contains("ResearchWorkbenchModePacket"));
        assertFalse(craftingScreen.contains("ResearchWorkbenchModePacket"));
        assertFalse(legacyTransition.contains("NetworkHooks.openScreen"));
    }

    @Test
    void nativeCraftingMenusRetainPhysicalAuthorityWhenProgressionIsDisabled()
            throws IOException {
        String menuMixin = source("mixin/GunSmithTableMenuMixin.java");
        String eligibility = source("progression/CraftingEligibilityService.java");

        assertTrue(menuMixin.contains(
                "ModConfigs.BLUEPRINT.enableBlueprints.get() || nativeWorkbench"));
        assertTrue(eligibility.contains("blueprintsEnabled || nativeWorkbench"));
        assertTrue(eligibility.contains("Evaluation.blocked(Status.INVALID_WORKSTATION)"));
        assertTrue(eligibility.contains("return Snapshot.unavailable()"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/" + relativePath));
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(relativePath)))
                .getAsJsonObject();
    }

    private static double[] objBounds(String relativePath) throws IOException {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        try (var lines = Files.lines(PROJECT.resolve(relativePath))) {
            for (String line : lines.filter(value -> value.startsWith("v ")).toList()) {
                String[] parts = line.trim().split("\\s+");
                double x = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[3]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        return new double[] {minX, maxX, minZ, maxZ};
    }
}
