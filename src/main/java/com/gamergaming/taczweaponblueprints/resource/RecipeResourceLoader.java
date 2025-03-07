package com.gamergaming.taczweaponblueprints.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.ibm.icu.impl.Pair;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.resource_legacy.CommonGunPackLoader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

public class RecipeResourceLoader {

    private static final Marker MARKER = MarkerManager.getMarker("RecipeLoader");

    /**
     * Loads all recipes from the resource manager.
     *
     * @return Map of ResourceLocation to Triple of GunSmithTableRecipe, nameKey, and Pair of itemType and displayId
     */
    public static Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> loadRecipes(MinecraftServer server) {
        Map<ResourceLocation, Triple<GunSmithTableRecipe, String, Pair<String, String>>> recipes = new HashMap<>();
        ResourceManager resourceManager = server.getResourceManager();

        // List all recipe JSON files
        try {
            // You might need to adjust the path here
            List<GunSmithTableRecipe> recipeLocations = CommonAssetsManager.getInstance().recipeManager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get());

            for (GunSmithTableRecipe recipeLocation : recipeLocations) {
                // Process only recipes under the "tac" namespace or other gun pack namespaces
                if (!isGunPackNamespace(recipeLocation.getId().getNamespace())) {
                    continue;
                }

                Optional<Resource> optionalResource = resourceManager.getResource(recipeLocation.getId());
                if (optionalResource.isEmpty()) {
                    continue;
                }
                Resource resource = optionalResource.get();

                try (InputStream stream = resource.open()) {
                    JsonObject json = GsonHelper.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    GunSmithTableRecipe recipe = parseRecipe(recipeLocation.getId(), json);
                    if (recipe == null) {
                        continue;
                    }

                    // Get corresponding index data
                    Triple<String, String, String> indexData = loadIndexData(resourceManager, recipeLocation.getId());
                    if (indexData == null) {
                        continue;
                    }

                    recipes.put(recipeLocation.getId(), Triple.of(recipe, indexData.getLeft(), Pair.of(indexData.getMiddle(), indexData.getRight())));
                }
            }

        } catch (IOException e) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Error listing or processing recipes", e);
        }

        return recipes;
    }

    private static boolean isGunPackNamespace(String namespace) {
        // Check if the namespace corresponds to TaCZ gun packs
        return true;
    }

    private static GunSmithTableRecipe parseRecipe(ResourceLocation id, JsonObject json) {
        try {
            TableRecipe tableRecipe = CommonGunPackLoader.GSON.fromJson(json, TableRecipe.class);
            return new GunSmithTableRecipe(id, tableRecipe);
        } catch (JsonSyntaxException e) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to parse recipe JSON: {}", id, e);
            return null;
        }
    }

    private static Triple<String, String, String> loadIndexData(ResourceManager resourceManager, ResourceLocation recipeLocation) {
        try {
            // Construct the path to the index file based on the recipe ID
            String itemPath = recipeLocation.getPath().substring("recipes/".length());
            String indexPath = "guns/index/" + itemPath;

            ResourceLocation indexLocation = new ResourceLocation(recipeLocation.getNamespace(), indexPath);

            Optional<Resource> optionalResource = resourceManager.getResource(indexLocation);
            if (optionalResource.isEmpty()) {
                return null;
            }
            Resource resource = optionalResource.get();

            // Load index JSON
            try (InputStream stream = resource.open()) {
                JsonObject json = GsonHelper.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String name = GsonHelper.getAsString(json, "name");
                String type = GsonHelper.getAsString(json, "type", "gun"); 
                String displayId = GsonHelper.getAsString(json, "display");
                if (displayId != null) {
                    displayId = new ResourceLocation(displayId).getPath();
                }
                return Triple.of(name, type, displayId);
            }

        } catch (IOException e) {
            TaCZWeaponBlueprints.LOGGER4J.error(MARKER, "Failed to load index data for recipe: {}", recipeLocation, e);
            return null;
        }
    }
}
