package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService.ResetState;

class PlayerProgressionAdminServiceTest {
    @Test
    void learnedResetClearsRollbackAliasesButPreservesDiscoveryAndPoints() {
        PlayerRecipeData data = populated();

        assertTrue(PlayerProgressionAdminService.reset(data, ResetState.LEARNED));

        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertTrue(data.getLearnedRecipes().isEmpty());
        assertEquals(2, data.getDiscoveredBlueprints().size());
        assertEquals(9, data.getResearchPoints());
    }

    @Test
    void discoveryResetRetainsLearnedSubsetInvariant() {
        PlayerRecipeData data = populated();

        assertTrue(PlayerProgressionAdminService.reset(data, ResetState.DISCOVERED));

        assertEquals(data.getLearnedBlueprints(), data.getDiscoveredBlueprints());
        assertEquals(1, data.getDiscoveredBlueprints().size());
        assertEquals(9, data.getResearchPoints());
    }

    @Test
    void pointAndCompleteResetsAreIndependentAndInspectable() {
        PlayerRecipeData pointsOnly = populated();
        assertTrue(PlayerProgressionAdminService.reset(pointsOnly, ResetState.POINTS));
        assertEquals(0, pointsOnly.getResearchPoints());
        assertEquals(1, pointsOnly.getLearnedBlueprints().size());

        PlayerRecipeData all = populated();
        var before = PlayerProgressionAdminService.inspect(all);
        assertEquals(1, before.learnedBlueprints());
        assertEquals(2, before.discoveredBlueprints());
        assertEquals(1, before.legacyRecipes());
        assertEquals(9, before.researchPoints());
        assertTrue(PlayerProgressionAdminService.reset(all, ResetState.ALL));
        assertTrue(all.getLearnedBlueprints().isEmpty());
        assertTrue(all.getDiscoveredBlueprints().isEmpty());
        assertTrue(all.getLearnedRecipes().isEmpty());
        assertEquals(0, all.getResearchPoints());
        assertFalse(PlayerProgressionAdminService.reset(null, ResetState.ALL));
    }

    private static PlayerRecipeData populated() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addRecipe("test:recipe");
        data.addBlueprint("test:learned");
        data.discoverBlueprint("test:history");
        data.setResearchPoints(9);
        return data;
    }
}
