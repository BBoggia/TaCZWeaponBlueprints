package com.gamergaming.taczweaponblueprints.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BlueprintDataManagerProgressionTest {
    @Test
    void catalogReplacementAdvancesRevisionAndDropsStaleRecipeMappings() {
        ResourceLocation oldBlueprintId = ResourceLocation.fromNamespaceAndPath("test", "old");
        ResourceLocation newBlueprintId = ResourceLocation.fromNamespaceAndPath("test", "new");
        BlueprintData oldBlueprint = blueprint(oldBlueprintId, "recipe/old");
        BlueprintData newBlueprint = blueprint(newBlueprintId, "recipe/new");

        try {
            BlueprintDataManager.SERVER.setBlueprintDataMap(
                    Map.of(oldBlueprintId, oldBlueprint));
            long oldRevision = BlueprintDataManager.SERVER.catalogRevision();
            assertEquals(oldBlueprintId, BlueprintDataManager.SERVER
                    .getBlueprintIdForRecipe(oldBlueprint.getRecipeId()));

            BlueprintDataManager.SERVER.setBlueprintDataMap(
                    Map.of(newBlueprintId, newBlueprint));

            assertTrue(BlueprintDataManager.SERVER.catalogRevision() > oldRevision);
            assertNull(BlueprintDataManager.SERVER
                    .getBlueprintIdForRecipe(oldBlueprint.getRecipeId()));
            assertEquals(newBlueprintId, BlueprintDataManager.SERVER
                    .getBlueprintIdForRecipe(newBlueprint.getRecipeId()));
        } finally {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of());
        }
    }

    @Test
    void catalogMigrationDiscoversLegacyRecipeOutputsAndRetainsCanonicalRecipes() {
        ResourceLocation blueprintId = ResourceLocation.fromNamespaceAndPath("test", "ak47");
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("test", "gun/ak47");
        BlueprintData blueprint = new BlueprintData(
                blueprintId.toString(),
                "item.test.ak47",
                "item.test.blueprint.tooltip",
                recipeId,
                null,
                "rifle",
                ResourceLocation.fromNamespaceAndPath("test", "display/rifle"));
        PlayerRecipeData data = new PlayerRecipeData();
        data.addRecipe(recipeId.toString());

        try {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(blueprintId, blueprint));

            assertEquals(1, BlueprintDataManager.SERVER.migrateLegacyUnlocks(data));
            assertEquals(Set.of(blueprintId.toString()), data.getLearnedBlueprints());
            assertEquals(Set.of(blueprintId.toString()), data.getDiscoveredBlueprints());
            assertTrue(data.hasRecipe(recipeId.toString()));
        } finally {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of());
        }
    }

    @Test
    void catalogMigrationRepairsMissingLegacyRecipeWithoutNewLearning() {
        ResourceLocation blueprintId = ResourceLocation.fromNamespaceAndPath("test", "repair");
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("test", "gun/repair");
        BlueprintData blueprint = new BlueprintData(
                blueprintId.toString(),
                "item.test.repair",
                "item.test.blueprint.tooltip",
                recipeId,
                null,
                "rifle",
                ResourceLocation.fromNamespaceAndPath("test", "display/rifle"));
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.addBlueprint(blueprintId.toString()));
        assertTrue(!data.hasRecipe(recipeId.toString()));

        try {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(blueprintId, blueprint));

            BlueprintLearningService.MigrationResult migration =
                    BlueprintLearningService.migrateLegacyUnlocksDetailed(
                            BlueprintDataManager.SERVER,
                            data);
            assertEquals(0, migration.learnedBlueprints());
            assertEquals(1, migration.repairedEntries());
            assertTrue(migration.changed());
            assertEquals(0, BlueprintDataManager.SERVER.migrateLegacyUnlocks(data));
            assertEquals(Set.of(blueprintId.toString()), data.getLearnedBlueprints());
            assertEquals(Set.of(blueprintId.toString()), data.getDiscoveredBlueprints());
            assertTrue(data.hasRecipe(recipeId.toString()));
        } finally {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of());
        }
    }

    private static BlueprintData blueprint(
            ResourceLocation blueprintId,
            String recipePath) {
        return new BlueprintData(
                blueprintId.toString(),
                "item.test." + blueprintId.getPath(),
                "item.test.blueprint.tooltip",
                ResourceLocation.fromNamespaceAndPath(blueprintId.getNamespace(), recipePath),
                null,
                "rifle",
                ResourceLocation.fromNamespaceAndPath("test", "display/rifle"));
    }
}
