package com.gamergaming.taczweaponblueprints.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import org.junit.jupiter.api.Test;

class CapabilityEventHandlerTest {
    @Test
    void cloneCopiesAnIndependentLearnedRecipeSnapshot() {
        PlayerRecipeData original = new PlayerRecipeData();
        original.addRecipe("tacz:gun/ak47");
        original.addBlueprint("tacz:ak47");
        PlayerRecipeData clone = new PlayerRecipeData();

        CapabilityEventHandler.copyRecipeData(original, clone);
        original.addRecipe("tacz:gun/m4a1");

        assertEquals(Set.of("tacz:gun/ak47"), clone.getLearnedRecipes());
        assertEquals(Set.of("tacz:ak47"), clone.getLearnedBlueprints());
        assertFalse(clone.hasRecipe("tacz:gun/m4a1"));
    }
}
