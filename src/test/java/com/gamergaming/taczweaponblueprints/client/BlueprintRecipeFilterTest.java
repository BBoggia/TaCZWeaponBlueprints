package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecipeFilterTest {

    @Test
    void intersectsLearnedRecipesWithTaCZNativeFilteringWithoutReordering() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation ammo = id("tacz:ammo");
        ResourceLocation learnedGun = id("pack:gun/learned");
        ResourceLocation lockedGun = id("pack:gun/locked");
        ResourceLocation learnedAmmo = id("pack:ammo/learned");

        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(lockedGun, learnedGun)));
        recipes.put(ammo, new ArrayList<>(List.of(learnedAmmo)));
        Map<ResourceLocation, String> tabs = tabs(guns, ammo);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterInPlace(
                recipes,
                tabs,
                Set.of(learnedGun.toString(), learnedAmmo.toString()),
                guns);

        assertEquals(List.of(guns, ammo), List.copyOf(recipes.keySet()));
        assertEquals(List.of(learnedGun), recipes.get(guns));
        assertEquals(List.of(learnedAmmo), recipes.get(ammo));
        assertEquals(guns, result.selectedType());
        assertSame(recipes.get(guns), result.selectedRecipeList());
    }

    @Test
    void removesEmptyTabsAndSelectsTheFirstRemainingTaCZTab() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation ammo = id("tacz:ammo");
        ResourceLocation lockedGun = id("pack:gun/locked");
        ResourceLocation learnedAmmo = id("pack:ammo/learned");

        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(lockedGun)));
        recipes.put(ammo, new ArrayList<>(List.of(learnedAmmo)));
        Map<ResourceLocation, String> tabs = tabs(guns, ammo);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterInPlace(
                recipes,
                tabs,
                Set.of(learnedAmmo.toString()),
                guns);

        assertEquals(List.of(ammo), List.copyOf(recipes.keySet()));
        assertEquals(List.of(ammo), List.copyOf(tabs.keySet()));
        assertEquals(ammo, result.selectedType());
        assertSame(recipes.get(ammo), result.selectedRecipeList());
    }

    @Test
    void returnsAnEmptySelectionWhenNoLearnedRecipeSurvivesNativeFiltering() {
        ResourceLocation guns = id("tacz:gun");
        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(id("pack:gun/locked"))));
        Map<ResourceLocation, String> tabs = tabs(guns);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterInPlace(
                recipes,
                tabs,
                Set.of(),
                guns);

        assertEquals(Map.of(), recipes);
        assertEquals(Map.of(), tabs);
        assertNull(result.selectedType());
        assertEquals(List.of(), result.selectedRecipeList());
    }

    @Test
    void intersectsLearnedRecipesWithAuthoritativeCraftingAccess() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation tierOne = id("pack:gun/tier_one");
        ResourceLocation tierThree = id("pack:gun/tier_three");
        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(tierOne, tierThree)));
        Map<ResourceLocation, String> tabs = tabs(guns);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterInPlace(
                recipes,
                tabs,
                Set.of(tierOne.toString(), tierThree.toString()),
                Set.of(tierOne.toString()),
                guns);

        assertEquals(List.of(tierOne), result.selectedRecipeList());
        assertEquals(List.of(tierOne), recipes.get(guns));
    }

    @Test
    void pendingCraftingAccessPreservesNativeCatalogButReturnsNoVisibleRecipes() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation learnedGun = id("pack:gun/learned");
        ResourceLocation lockedGun = id("pack:gun/locked");
        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(learnedGun, lockedGun)));
        Map<ResourceLocation, String> tabs = tabs(guns);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterForCraftingAccessState(
                recipes,
                tabs,
                Set.of(learnedGun.toString()),
                false,
                Set.of(),
                false,
                guns);

        assertEquals(List.of(), result.selectedRecipeList());
        assertEquals(List.of(guns), List.copyOf(recipes.keySet()));
        assertEquals(List.of(learnedGun, lockedGun), recipes.get(guns));
        assertEquals(List.of(guns), List.copyOf(tabs.keySet()));
        assertEquals(guns, result.selectedType());
        assertFalse(BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                learnedGun.toString(), Set.of(learnedGun.toString()), false, Set.of()));
    }

    @Test
    void receivedCraftingAccessUsesTheAuthoritativeAllowListAlone() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation tierThree = id("pack:gun/tier_three");
        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(tierThree)));
        Map<ResourceLocation, String> tabs = tabs(guns);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterForCraftingAccessState(
                recipes,
                tabs,
                Set.of(),
                true,
                Set.of(tierThree.toString()),
                false,
                guns);

        assertEquals(List.of(tierThree), recipes.get(guns));
        assertEquals(List.of(guns), List.copyOf(tabs.keySet()));
        assertEquals(guns, result.selectedType());
        assertEquals(List.of(tierThree), result.selectedRecipeList());
    }

    @Test
    void selectedRecipeVisibilityFollowsTheSamePendingAndReceivedRules() {
        String recipeId = "pack:gun/learned";
        Set<String> learned = Set.of(recipeId);

        assertFalse(BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                recipeId, learned, false, Set.of()));
        assertFalse(BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                recipeId, learned, true, Set.of()));
        assertTrue(BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                recipeId, Set.of(), true, Set.of(recipeId)));
    }

    @Test
    void explicitClassicGrantPreservesEveryNativeRecipe() {
        ResourceLocation guns = id("tacz:gun");
        ResourceLocation cataloged = id("pack:gun/cataloged");
        ResourceLocation uncataloged = id("pack:gun/native_only");
        Map<ResourceLocation, List<ResourceLocation>> recipes = new LinkedHashMap<>();
        recipes.put(guns, new ArrayList<>(List.of(cataloged, uncataloged)));
        Map<ResourceLocation, String> tabs = tabs(guns);

        BlueprintRecipeFilter.Result result = BlueprintRecipeFilter.filterForCraftingAccessState(
                recipes,
                tabs,
                Set.of(),
                true,
                Set.of(),
                true,
                guns);

        assertEquals(List.of(cataloged, uncataloged), result.selectedRecipeList());
        assertTrue(BlueprintRecipeFilter.isVisibleForCraftingAccessState(
                uncataloged.toString(), Set.of(), true, Set.of(), true));
    }

    private static Map<ResourceLocation, String> tabs(ResourceLocation... ids) {
        Map<ResourceLocation, String> tabs = new LinkedHashMap<>();
        for (ResourceLocation id : ids) {
            tabs.put(id, id.toString());
        }
        return tabs;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
