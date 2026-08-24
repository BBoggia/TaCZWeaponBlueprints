package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PlayerRecipeDataTest {

    @Test
    void validatesAndDeduplicatesRecipeIds() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertTrue(data.addRecipe("tacz:gun/ak47"));
        assertFalse(data.addRecipe("tacz:gun/ak47"));
        assertFalse(data.addRecipe("not a resource location"));
        assertFalse(data.addRecipe(null));
        assertEquals(Set.of("tacz:gun/ak47"), data.getLearnedRecipes());
    }

    @Test
    void exposesReadOnlyStateAndCanReplaceFromItsOwnView() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.replaceRecipes(List.of("tacz:gun/m4a1", "tacz:gun/ak47"));
        Set<String> view = data.getLearnedRecipes();

        assertThrows(UnsupportedOperationException.class, () -> view.add("tacz:gun/scar_l"));
        data.replaceRecipes(view);

        assertEquals(Set.of("tacz:gun/m4a1", "tacz:gun/ak47"), data.getLearnedRecipes());
    }

    @Test
    void serializesDeterministicallyAndRoundTrips() {
        PlayerRecipeData original = new PlayerRecipeData();
        original.addRecipe("tacz:gun/m4a1");
        original.addRecipe("tacz:gun/ak47");
        original.addBlueprint("tacz:ak47");

        CompoundTag serialized = original.serializeNBT();
        ListTag recipes = serialized.getList("Recipes", Tag.TAG_STRING);
        assertEquals("tacz:gun/ak47", recipes.getString(0));
        assertEquals("tacz:gun/m4a1", recipes.getString(1));
        assertEquals("tacz:ak47", serialized.getList("Blueprints", Tag.TAG_STRING).getString(0));

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);
        assertEquals(original.getLearnedRecipes(), restored.getLearnedRecipes());
        assertEquals(original.getLearnedBlueprints(), restored.getLearnedBlueprints());
    }

    @Test
    void durableBlueprintUnlocksAreValidatedAndClearedWithLegacyRecipes() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("invalid id"));
        assertTrue(data.hasBlueprint("tacz:ak47"));

        data.addRecipe("tacz:gun/ak47");
        data.clearRecipes();

        assertTrue(data.getLearnedRecipes().isEmpty());
        assertTrue(data.getLearnedBlueprints().isEmpty());
    }

    @Test
    void filtersInvalidIdsWhenLoadingLegacyData() {
        CompoundTag serialized = new CompoundTag();
        ListTag recipes = new ListTag();
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        recipes.add(StringTag.valueOf("invalid id"));
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        serialized.put("Recipes", recipes);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(Set.of("tacz:gun/ak47"), restored.getLearnedRecipes());
    }
}
