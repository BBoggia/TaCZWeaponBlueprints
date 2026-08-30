package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.Collection;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

public interface IPlayerRecipeData {
    Set<String> getLearnedRecipes();
    Set<String> getLearnedBlueprints();
    Set<String> getDiscoveredBlueprints();
    int getResearchPoints();
    ResearchPointAwardLedger getResearchPointAwardLedger();
    BlueprintLearningMutation.Result applyBlueprintLearning(
            BlueprintLearningMutation.Request request);
    boolean addRecipe(String recipeId);
    boolean addBlueprint(String blueprintId);
    boolean discoverBlueprint(String blueprintId);
    boolean removeRecipe(String recipeId);
    boolean hasRecipe(String recipeId);
    boolean hasBlueprint(String blueprintId);
    boolean hasDiscoveredBlueprint(String blueprintId);
    boolean setResearchPoints(int points);
    boolean addResearchPoints(int amount, int pointCap);
    boolean applyResearchPointTransaction(
            int amount,
            int pointCap,
            ResearchPointAwardLedger.Mutation ledgerMutation);
    boolean spendResearchPoints(int amount);
    void clearResearchPointAwardLedger();
    void replaceRecipes(Collection<String> recipeIds);
    boolean replaceProgression(
            Collection<String> learnedBlueprintIds,
            Collection<String> discoveredBlueprintIds,
            int researchPoints);
    void clearRecipes();
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
