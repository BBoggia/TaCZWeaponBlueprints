package com.gamergaming.taczweaponblueprints.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;
import org.junit.jupiter.api.Test;

class CapabilityEventHandlerTest {
    @Test
    void cloneCopiesAnIndependentLearnedRecipeSnapshot() {
        PlayerRecipeData original = new PlayerRecipeData();
        original.addRecipe("tacz:gun/ak47");
        original.addBlueprint("tacz:ak47");
        original.discoverBlueprint("tacz:m4a1");
        original.setResearchPoints(75);
        ClaimKey copiedClaim = ClaimKey.once(
                new net.minecraft.resources.ResourceLocation("test:copied_claim"));
        original.applyResearchPointTransaction(0, 100, Mutation.claim(copiedClaim));
        PlayerRecipeData clone = new PlayerRecipeData();

        CapabilityEventHandler.copyRecipeData(original, clone);
        original.addRecipe("tacz:gun/m4a1");
        original.clearResearchPointAwardLedger();

        assertEquals(Set.of("tacz:gun/ak47"), clone.getLearnedRecipes());
        assertEquals(Set.of("tacz:ak47"), clone.getLearnedBlueprints());
        assertEquals(Set.of("tacz:ak47", "tacz:m4a1"), clone.getDiscoveredBlueprints());
        assertEquals(75, clone.getResearchPoints());
        assertEquals(Set.of(copiedClaim), clone.getResearchPointAwardLedger().claims());
        assertFalse(clone.hasRecipe("tacz:gun/m4a1"));
    }
}
