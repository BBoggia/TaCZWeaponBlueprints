package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * Intersects TaCZ's native gunsmith result with the recipes learned by the
 * current player. The supplied maps have already been filtered and ordered by
 * TaCZ, so this operation deliberately removes entries in place without
 * rebuilding or reordering them.
 */
public final class BlueprintRecipeFilter {

    private BlueprintRecipeFilter() {}

    public static Result filterInPlace(
            Map<ResourceLocation, List<ResourceLocation>> recipes,
            Map<ResourceLocation, ?> recipeKeys,
            Set<String> learnedRecipes,
            ResourceLocation selectedType) {
        recipes.values().forEach(recipeIds ->
                recipeIds.removeIf(recipeId -> !learnedRecipes.contains(recipeId.toString())));
        recipes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        recipeKeys.keySet().removeIf(recipeType -> !recipes.containsKey(recipeType));

        ResourceLocation visibleType = selectedType;
        if (visibleType == null || !recipes.containsKey(visibleType)) {
            visibleType = recipeKeys.keySet().stream().findFirst().orElse(null);
        }

        List<ResourceLocation> visibleRecipes = visibleType == null
                ? new ArrayList<>()
                : recipes.getOrDefault(visibleType, new ArrayList<>());
        return new Result(visibleType, visibleRecipes);
    }

    public record Result(ResourceLocation selectedType, List<ResourceLocation> selectedRecipeList) {}
}
