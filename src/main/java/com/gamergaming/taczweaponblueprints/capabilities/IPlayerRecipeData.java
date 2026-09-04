package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

public interface IPlayerRecipeData {
    Set<String> getLearnedRecipes();
    Set<String> getLearnedBlueprints();
    Set<String> getDiscoveredBlueprints();
    int getResearchPoints();
    ResearchPointAwardLedger getResearchPointAwardLedger();
    /**
     * Optional Recent-history extension. The default keeps capability
     * implementations compiled before player data version 3 compatible.
     */
    default List<RecentBlueprintUnlockBatch> getRecentUnlockBatches() {
        return List.of();
    }
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
    default boolean recordRecentUnlockBatch(
            RecentBlueprintUnlockBatch.Source source,
            String targetBlueprintId,
            Collection<String> memberBlueprintIds) {
        return false;
    }
    default void clearRecentUnlockHistory() {
    }
    /**
     * Optional Blueprint Fragment extension. Empty defaults keep capability
     * implementations compiled before player data version 4 compatible.
     */
    default Map<String, Integer> getArchivedBlueprintFragments() {
        return Map.of();
    }
    /** Optional durable Progression Gate criterion extension. */
    default Map<String, Integer> getProgressionCriteria() {
        return Map.of();
    }
    default PlayerProgressValueMutation.Result applyArchivedFragmentMutation(
            PlayerProgressValueMutation.Request request) {
        if (request == null) {
            throw new IllegalArgumentException("fragment progress mutation cannot be null");
        }
        return PlayerProgressValueMutation.Result.rejected(
                PlayerProgressValueMutation.Status.UNSUPPORTED,
                request,
                0);
    }
    default PlayerProgressValueMutation.Result applyProgressionCriterionMutation(
            PlayerProgressValueMutation.Request request) {
        if (request == null) {
            throw new IllegalArgumentException("criterion progress mutation cannot be null");
        }
        return PlayerProgressValueMutation.Result.rejected(
                PlayerProgressValueMutation.Status.UNSUPPORTED,
                request,
                0);
    }
    /** Atomically replaces both client-presentable supplemental progress maps. */
    default boolean replaceSupplementalProgression(
            Map<String, Integer> archivedFragments,
            Map<String, Integer> criteria) {
        return archivedFragments != null && archivedFragments.isEmpty()
                && criteria != null && criteria.isEmpty();
    }
    default boolean clearArchivedBlueprintFragments() {
        return false;
    }
    default boolean clearProgressionCriteria() {
        return false;
    }
    void replaceRecipes(Collection<String> recipeIds);
    boolean replaceProgression(
            Collection<String> learnedBlueprintIds,
            Collection<String> discoveredBlueprintIds,
            int researchPoints);
    void clearRecipes();
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}
