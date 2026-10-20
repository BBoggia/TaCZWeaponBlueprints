package com.gamergaming.taczweaponblueprints.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import org.junit.jupiter.api.Test;

class PlayerAttachmentSnapshotTest {
    @Test
    void cloneCopiesAnIndependentLearnedRecipeSnapshot() {
        PlayerRecipeData original = new PlayerRecipeData();
        original.addRecipe("tacz:gun/ak47");
        original.addBlueprint("tacz:ak47");
        original.discoverBlueprint("tacz:m4a1");
        original.setResearchPoints(75);
        ClaimKey copiedClaim = ClaimKey.once(
                net.minecraft.resources.ResourceLocation.parse("test:copied_claim"));
        original.applyResearchPointTransaction(0, 100, Mutation.claim(copiedClaim));
        original.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit("test:fragment", 0, 6));
        original.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:criterion", 0, 2));
        var analysis = new com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts(1,2,3,4);
        original.applyAnalysisFragmentMutation(new com.gamergaming.taczweaponblueprints.capabilities.AnalysisFragmentMutation(
                PlayerProgressValueMutation.Operation.COMMIT, "removed:analysis", com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts.EMPTY, analysis));
        PlayerRecipeData clone = new PlayerRecipeData();

        clone.deserializeNBT(original.serializeNBT().copy());
        original.addRecipe("tacz:gun/m4a1");
        original.clearResearchPointAwardLedger();
        original.clearArchivedBlueprintFragments();
        original.clearProgressionCriteria(); original.clearArchivedAnalysisFragments();

        assertEquals(Set.of("tacz:gun/ak47"), clone.getLearnedRecipes());
        assertEquals(Set.of("tacz:ak47"), clone.getLearnedBlueprints());
        assertEquals(Set.of("tacz:ak47", "tacz:m4a1"), clone.getDiscoveredBlueprints());
        assertEquals(75, clone.getResearchPoints());
        assertEquals(Set.of(copiedClaim), clone.getResearchPointAwardLedger().claims());
        assertEquals(Map.of("test:fragment", 6), clone.getArchivedBlueprintFragments());
        assertEquals(Map.of("test:criterion", 2), clone.getProgressionCriteria());
        assertEquals(Map.of("removed:analysis", analysis), clone.getArchivedAnalysisFragments());
        assertFalse(clone.hasRecipe("tacz:gun/m4a1"));
    }
}
