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

class CapabilityEventHandlerTest {
    @Test
    void cloneCanReadSerializedProgressAfterProviderHandleWasInvalidated() throws Exception {
        var provider = new com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeDataProvider();
        // Plain JUnit does not run Forge's CapabilityToken bytecode transformer.
        // Inspect the actual provider handle without initializing ModCapabilities.
        var field = provider.getClass().getDeclaredField("optionalData"); field.setAccessible(true);
        var handle = (net.minecraftforge.common.util.LazyOptional<?>)field.get(provider);
        var original = (com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData)handle.resolve().orElseThrow();
        original.setResearchPoints(137); original.addBlueprint("tacz:ak47");
        original.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit("tacz:m4a1", 0, 4));
        provider.invalidate();
        assertFalse(handle.isPresent(), "invalidated references must stay invalid");
        var caps = new net.minecraft.nbt.CompoundTag();
        caps.put("taczweaponblueprints:player_recipe_data", provider.serializeNBT());
        var player = new net.minecraft.nbt.CompoundTag(); player.put("ForgeCaps", caps);
        var clone = new PlayerRecipeData();
        CapabilityEventHandler.copySerializedRecipeData(player, clone);
        assertEquals(original.serializeNBT(), clone.serializeNBT());
        assertFalse(handle.isPresent(), "copying must not revive removed-player handles");
        original.setResearchPoints(0);
        assertEquals(137, clone.getResearchPoints());
    }

    @Test
    void missingSerializedCapabilityDoesNotClearDestination() {
        var clone = new PlayerRecipeData(); clone.setResearchPoints(137);
        CapabilityEventHandler.copySerializedRecipeData(new net.minecraft.nbt.CompoundTag(), clone);
        assertEquals(137, clone.getResearchPoints());
    }

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
        original.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit("test:fragment", 0, 6));
        original.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:criterion", 0, 2));
        var analysis = new com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts(1,2,3,4);
        original.applyAnalysisFragmentMutation(new com.gamergaming.taczweaponblueprints.capabilities.AnalysisFragmentMutation(
                PlayerProgressValueMutation.Operation.COMMIT, "removed:analysis", com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts.EMPTY, analysis));
        PlayerRecipeData clone = new PlayerRecipeData();

        var caps = new net.minecraft.nbt.CompoundTag();
        caps.put("taczweaponblueprints:player_recipe_data", original.serializeNBT());
        var saved = new net.minecraft.nbt.CompoundTag(); saved.put("ForgeCaps", caps);
        CapabilityEventHandler.copySerializedRecipeData(saved, clone);
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
