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
        return filterInPlace(
                recipes, recipeKeys, learnedRecipes, learnedRecipes, selectedType);
    }

    public static Result filterInPlace(
            Map<ResourceLocation, List<ResourceLocation>> recipes,
            Map<ResourceLocation, ?> recipeKeys,
            Set<String> learnedRecipes,
            Set<String> craftingAllowedRecipes,
            ResourceLocation selectedType) {
        recipes.values().forEach(recipeIds ->
                recipeIds.removeIf(recipeId -> !learnedRecipes.contains(recipeId.toString())
                        || !craftingAllowedRecipes.contains(recipeId.toString())));
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

    /**
     * Filters the native recipe lists for the current crafting-access lifecycle.
     * Before the server response arrives, TaCZ's native maps remain intact so
     * its persistent gun-pack filter can discover every available namespace,
     * while the returned selection is empty and therefore non-interactive.
     * Once a response is received, its server-authoritative allow-list is the
     * sole visibility source.
     */
    public static Result filterForCraftingAccessState(
            Map<ResourceLocation, List<ResourceLocation>> recipes,
            Map<ResourceLocation, ?> recipeKeys,
            Set<String> learnedRecipes,
            boolean craftingAccessReceived,
            Set<String> craftingAllowedRecipes,
            boolean unrestrictedCrafting,
            ResourceLocation selectedType) {
        if (recipes == null || recipeKeys == null || learnedRecipes == null
                || craftingAllowedRecipes == null) {
            throw new IllegalArgumentException("crafting recipe filter inputs cannot be null");
        }
        if (!craftingAccessReceived) {
            recipeKeys.keySet().removeIf(recipeType -> !recipes.containsKey(recipeType));
            ResourceLocation scaffoldType = selectedType;
            if (scaffoldType == null || !recipeKeys.containsKey(scaffoldType)) {
                scaffoldType = recipeKeys.keySet().stream().findFirst().orElse(null);
            }
            return new Result(scaffoldType, new ArrayList<>());
        }
        if (unrestrictedCrafting) {
            ResourceLocation visibleType = selectedType;
            if (visibleType == null || !recipes.containsKey(visibleType)) {
                visibleType = recipeKeys.keySet().stream().findFirst().orElse(null);
            }
            List<ResourceLocation> visibleRecipes = visibleType == null
                    ? new ArrayList<>()
                    : recipes.getOrDefault(visibleType, new ArrayList<>());
            return new Result(visibleType, visibleRecipes);
        }
        return filterInPlace(
                recipes,
                recipeKeys,
                craftingAllowedRecipes,
                craftingAllowedRecipes,
                selectedType);
    }

    public static boolean isVisibleForCraftingAccessState(
            String recipeId,
            Set<String> learnedRecipes,
            boolean craftingAccessReceived,
            Set<String> craftingAllowedRecipes) {
        return isVisibleForCraftingAccessState(
                recipeId,
                learnedRecipes,
                craftingAccessReceived,
                craftingAllowedRecipes,
                false);
    }

    public static boolean isVisibleForCraftingAccessState(
            String recipeId,
            Set<String> learnedRecipes,
            boolean craftingAccessReceived,
            Set<String> craftingAllowedRecipes,
            boolean unrestrictedCrafting) {
        return recipeId != null
                && learnedRecipes != null
                && craftingAllowedRecipes != null
                && craftingAccessReceived
                && (unrestrictedCrafting || craftingAllowedRecipes.contains(recipeId));
    }

    public record Result(ResourceLocation selectedType, List<ResourceLocation> selectedRecipeList) {}
}
