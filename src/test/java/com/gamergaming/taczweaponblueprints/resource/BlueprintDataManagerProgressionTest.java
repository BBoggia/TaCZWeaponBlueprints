package com.gamergaming.taczweaponblueprints.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BlueprintDataManagerProgressionTest {
    @Test
    void catalogMigrationDiscoversLegacyRecipeOutputsAndRetainsCanonicalRecipes() {
        ResourceLocation blueprintId = new ResourceLocation("test", "ak47");
        ResourceLocation recipeId = new ResourceLocation("test", "gun/ak47");
        BlueprintData blueprint = new BlueprintData(
                blueprintId.toString(),
                "item.test.ak47",
                "item.test.blueprint.tooltip",
                recipeId,
                null,
                "rifle",
                new ResourceLocation("test", "display/rifle"));
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
}
