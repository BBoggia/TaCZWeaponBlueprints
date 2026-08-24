package com.gamergaming.taczweaponblueprints.network;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import net.minecraft.resources.ResourceLocation;

final class RecipeSyncFilter {
    private RecipeSyncFilter() {
    }

    static Set<String> activeLearnedRecipes(
            Set<String> learnedRecipes,
            Set<String> learnedBlueprints,
            Map<ResourceLocation, BlueprintData> activeBlueprints,
            Map<ResourceLocation, ResourceLocation> recipeToBlueprint) {
        Map<ResourceLocation, BlueprintData> activeByBlueprint = new java.util.HashMap<>();
        if (activeBlueprints != null) {
            activeBlueprints.forEach((blueprintId, data) -> {
                if (blueprintId != null && data != null && data.getRecipeId() != null) {
                    activeByBlueprint.put(blueprintId, data);
                }
            });
        }

        Set<String> synchronizedRecipes = new TreeSet<>();
        if (learnedBlueprints != null) {
            learnedBlueprints.stream()
                    .map(ResourceLocation::tryParse)
                    .map(activeByBlueprint::get)
                    .filter(java.util.Objects::nonNull)
                    .map(data -> data.getRecipeId().toString())
                    .forEach(synchronizedRecipes::add);
        }
        if (learnedRecipes != null) {
            learnedRecipes.stream()
                    .map(ResourceLocation::tryParse)
                    .map(recipeId -> recipeToBlueprint == null ? null : recipeToBlueprint.get(recipeId))
                    .map(activeByBlueprint::get)
                    .filter(java.util.Objects::nonNull)
                    .map(data -> data.getRecipeId().toString())
                    .forEach(synchronizedRecipes::add);
        }
        return Set.copyOf(synchronizedRecipes);
    }
}
