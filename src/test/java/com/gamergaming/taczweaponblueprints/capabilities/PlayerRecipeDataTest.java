package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;

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
    void recentHistoryCapabilityExtensionsRemainOptionalForLegacyImplementations()
            throws NoSuchMethodException {
        assertTrue(IPlayerRecipeData.class
                .getMethod("getRecentUnlockBatches").isDefault());
        assertTrue(IPlayerRecipeData.class.getMethod(
                "recordRecentUnlockBatch",
                RecentBlueprintUnlockBatch.Source.class,
                String.class,
                java.util.Collection.class).isDefault());
        assertTrue(IPlayerRecipeData.class
                .getMethod("clearRecentUnlockHistory").isDefault());
        assertTrue(IPlayerRecipeData.class
                .getMethod("getArchivedBlueprintFragments").isDefault());
        assertTrue(IPlayerRecipeData.class
                .getMethod("getProgressionCriteria").isDefault());
        assertTrue(IPlayerRecipeData.class.getMethod(
                "applyArchivedFragmentMutation",
                PlayerProgressValueMutation.Request.class).isDefault());
        assertTrue(IPlayerRecipeData.class.getMethod(
                "applyProgressionCriterionMutation",
                PlayerProgressValueMutation.Request.class).isDefault());
        assertTrue(IPlayerRecipeData.class.getMethod(
                "replaceSupplementalProgression",
                Map.class,
                Map.class).isDefault());
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
        original.discoverBlueprint("tacz:m4a1");
        assertTrue(original.setResearchPoints(125));
        ClaimKey claim = ClaimKey.targeted(
                new net.minecraft.resources.ResourceLocation("test:first_discovery"),
                new net.minecraft.resources.ResourceLocation("tacz:ak47"));
        assertTrue(original.applyResearchPointTransaction(0, 1000, Mutation.claim(claim)));
        assertTrue(original.recordRecentUnlockBatch(
                RecentBlueprintUnlockBatch.Source.TREE_RESEARCH,
                "tacz:ak47",
                List.of("tacz:m4a1", "tacz:ak47")));
        assertTrue(original.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit("tacz:m4a1", 0, 17))
                .changed());
        assertTrue(original.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:trial_wins", 0, 3))
                .changed());

        CompoundTag serialized = original.serializeNBT();
        assertEquals(PlayerProgressionLimits.DATA_VERSION, serialized.getInt("DataVersion"));
        ListTag recipes = serialized.getList("Recipes", Tag.TAG_STRING);
        assertEquals("tacz:gun/ak47", recipes.getString(0));
        assertEquals("tacz:gun/m4a1", recipes.getString(1));
        assertEquals("tacz:ak47", serialized.getList("Blueprints", Tag.TAG_STRING).getString(0));
        assertEquals(
                List.of("tacz:ak47", "tacz:m4a1"),
                serialized.getList("DiscoveredBlueprints", Tag.TAG_STRING).stream()
                        .map(Tag::getAsString)
                        .toList());
        assertEquals(125, serialized.getInt("ResearchPoints"));
        assertTrue(serialized.contains("ResearchPointAwards", Tag.TAG_COMPOUND));
        assertEquals("tacz:m4a1", serialized
                .getList("ArchivedBlueprintFragments", Tag.TAG_COMPOUND)
                .getCompound(0).getString("Id"));
        assertEquals(17, serialized
                .getList("ArchivedBlueprintFragments", Tag.TAG_COMPOUND)
                .getCompound(0).getInt("Value"));
        assertEquals("test:trial_wins", serialized
                .getList("ProgressionCriteria", Tag.TAG_COMPOUND)
                .getCompound(0).getString("Id"));

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);
        assertEquals(original.getLearnedRecipes(), restored.getLearnedRecipes());
        assertEquals(original.getLearnedBlueprints(), restored.getLearnedBlueprints());
        assertEquals(original.getDiscoveredBlueprints(), restored.getDiscoveredBlueprints());
        assertEquals(original.getResearchPoints(), restored.getResearchPoints());
        assertEquals(original.getResearchPointAwardLedger().claims(),
                restored.getResearchPointAwardLedger().claims());
        assertEquals(original.getRecentUnlockBatches(), restored.getRecentUnlockBatches());
        assertEquals(original.getArchivedBlueprintFragments(),
                restored.getArchivedBlueprintFragments());
        assertEquals(original.getProgressionCriteria(), restored.getProgressionCriteria());
    }

    @Test
    void supplementalProgressUsesCompareAndSetPreflightCommitAndRollback() {
        PlayerRecipeData data = new PlayerRecipeData();

        var preflight = data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.preflight("test:rifle", 0, 12));
        assertEquals(PlayerProgressValueMutation.Status.READY, preflight.status());
        assertTrue(data.getArchivedBlueprintFragments().isEmpty());

        var committed = data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit("test:rifle", 0, 12));
        assertEquals(PlayerProgressValueMutation.Status.APPLIED, committed.status());
        assertEquals(Map.of("test:rifle", 12), data.getArchivedBlueprintFragments());
        assertEquals(PlayerProgressValueMutation.Status.STALE,
                data.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.commit("test:rifle", 0, 20))
                        .status());

        var rolledBack = data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.rollback("test:rifle", 12, 0));
        assertEquals(PlayerProgressValueMutation.Status.ROLLED_BACK, rolledBack.status());
        assertTrue(data.getArchivedBlueprintFragments().isEmpty());

        assertEquals(PlayerProgressValueMutation.Status.INVALID_IDENTITY,
                data.applyProgressionCriterionMutation(
                        PlayerProgressValueMutation.Request.commit(" ", 0, 1))
                        .status());
        assertEquals(PlayerProgressValueMutation.Status.UNCHANGED,
                data.applyProgressionCriterionMutation(
                        PlayerProgressValueMutation.Request.commit("test:unused", 0, 0))
                        .status());
        assertThrows(IllegalArgumentException.class,
                () -> PlayerProgressValueMutation.Request.commit(
                        "test:invalid", 0, PlayerProgressionLimits.MAX_PROGRESS_VALUE + 1));
    }

    @Test
    void supplementalReplacementIsAtomicValidatedAndReadOnly() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.replaceSupplementalProgression(
                Map.of("test:rifle", 4),
                Map.of("test:trial", 2)));
        Map<String, Integer> fragmentView = data.getArchivedBlueprintFragments();
        assertThrows(UnsupportedOperationException.class,
                () -> fragmentView.put("test:pistol", 1));

        assertFalse(data.replaceSupplementalProgression(
                Map.of("test:new", 8),
                Map.of("invalid id", 3)));
        assertEquals(Map.of("test:rifle", 4), data.getArchivedBlueprintFragments());
        assertEquals(Map.of("test:trial", 2), data.getProgressionCriteria());

        assertFalse(data.replaceSupplementalProgression(
                Map.of("test:new", 0),
                Map.of()));
        assertEquals(Map.of("test:rifle", 4), data.getArchivedBlueprintFragments());
    }

    @Test
    void supplementalMutationRejectsANewEntryAtCapacityButCanUpdateExistingState() {
        PlayerRecipeData data = new PlayerRecipeData();
        java.util.LinkedHashMap<String, Integer> fragments = new java.util.LinkedHashMap<>();
        for (int index = 0; index < PlayerProgressionLimits.MAX_FRAGMENT_TARGETS; index++) {
            fragments.put("test:fragment_" + index, 1);
        }
        assertTrue(data.replaceSupplementalProgression(fragments, Map.of()));

        assertEquals(PlayerProgressValueMutation.Status.CAPACITY_REACHED,
                data.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.preflight("test:overflow", 0, 1))
                        .status());
        assertEquals(PlayerProgressValueMutation.Status.APPLIED,
                data.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.commit("test:fragment_0", 1, 2))
                        .status());
        assertEquals(2, data.getArchivedBlueprintFragments().get("test:fragment_0"));
        assertEquals(PlayerProgressionLimits.MAX_FRAGMENT_TARGETS,
                data.getArchivedBlueprintFragments().size());
    }

    @Test
    void versionThreeMigratesWithEmptySupplementalProgressWithoutTrustingFutureTags() {
        CompoundTag versionThree = new CompoundTag();
        versionThree.putInt("DataVersion", 3);
        versionThree.put("ArchivedBlueprintFragments", progressList("test:injected", 12));
        versionThree.put("ProgressionCriteria", progressList("test:injected", 7));

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(versionThree);

        assertTrue(restored.getArchivedBlueprintFragments().isEmpty());
        assertTrue(restored.getProgressionCriteria().isEmpty());
        assertEquals(4, restored.serializeNBT().getInt("DataVersion"));
    }

    @Test
    void malformedSupplementalNbtIsRepairedDeterministically() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("DataVersion", 4);
        ListTag fragments = new ListTag();
        for (int index = PlayerProgressionLimits.MAX_FRAGMENT_TARGETS + 1;
                index >= 0;
                index--) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", String.format("test:fragment_%04d", index));
            entry.putInt("Value", index + 1);
            fragments.add(entry);
        }
        fragments.addAll(progressList("test:fragment_0000", 999));
        fragments.addAll(progressList("invalid id", 5));
        fragments.addAll(progressList("test:negative", -1));
        CompoundTag clamped = new CompoundTag();
        clamped.putString("Id", "test:clamped");
        clamped.putLong("Value", Long.MAX_VALUE);
        fragments.add(clamped);
        serialized.put("ArchivedBlueprintFragments", fragments);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(PlayerProgressionLimits.MAX_FRAGMENT_TARGETS,
                restored.getArchivedBlueprintFragments().size());
        assertEquals(999, restored.getArchivedBlueprintFragments().get("test:fragment_0000"));
        assertFalse(restored.getArchivedBlueprintFragments().containsKey("test:fragment_4097"));
        assertFalse(restored.getArchivedBlueprintFragments().containsKey("invalid id"));
    }

    @Test
    void implausiblyLargeSupplementalNbtListIsRejectedWithoutAffectingKnowledge() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("DataVersion", 4);
        serialized.put("Blueprints", stringList("test:learned"));
        ListTag criteria = new ListTag();
        for (int index = 0;
                index <= PlayerProgressionLimits.MAX_PERSISTED_PROGRESS_ENTRIES_TO_INSPECT;
                index++) {
            criteria.addAll(progressList("test:criterion_" + index, 1));
        }
        serialized.put("ProgressionCriteria", criteria);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(Set.of("test:learned"), restored.getLearnedBlueprints());
        assertEquals(Set.of("test:learned"), restored.getDiscoveredBlueprints());
        assertTrue(restored.getProgressionCriteria().isEmpty());
    }

    @Test
    void learningDiscoversAndResetPreservesDiscoveryAndPoints() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("tacz:ak47"));
        assertFalse(data.addBlueprint("invalid id"));
        assertTrue(data.hasBlueprint("tacz:ak47"));
        assertTrue(data.hasDiscoveredBlueprint("tacz:ak47"));
        assertTrue(data.setResearchPoints(50));

        data.addRecipe("tacz:gun/ak47");
        data.clearRecipes();

        assertTrue(data.getLearnedRecipes().isEmpty());
        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertEquals(Set.of("tacz:ak47"), data.getDiscoveredBlueprints());
        assertEquals(50, data.getResearchPoints());
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

    @Test
    void migratesVersionZeroAndRepairsLearnedDiscoveryInvariant() {
        CompoundTag versionZero = new CompoundTag();
        ListTag recipes = new ListTag();
        recipes.add(StringTag.valueOf("tacz:gun/ak47"));
        versionZero.put("Recipes", recipes);
        ListTag blueprints = new ListTag();
        blueprints.add(StringTag.valueOf("tacz:ak47"));
        versionZero.put("Blueprints", blueprints);
        versionZero.putInt("ResearchPoints", 999);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(versionZero);

        assertEquals(Set.of("tacz:gun/ak47"), restored.getLearnedRecipes());
        assertEquals(Set.of("tacz:ak47"), restored.getLearnedBlueprints());
        assertEquals(Set.of("tacz:ak47"), restored.getDiscoveredBlueprints());
        assertEquals(0, restored.getResearchPoints());
        assertEquals(PlayerProgressionLimits.DATA_VERSION, restored.serializeNBT().getInt("DataVersion"));
    }

    @Test
    void migratesVersionOneWithAnEmptyAwardLedgerWithoutTrustingFutureState() {
        CompoundTag versionOne = new CompoundTag();
        versionOne.putInt("DataVersion", 1);
        versionOne.putInt("ResearchPoints", 37);
        ResearchPointAwardLedger unexpectedLedger = new ResearchPointAwardLedger();
        assertTrue(unexpectedLedger.apply(Mutation.claim(
                ClaimKey.once(new net.minecraft.resources.ResourceLocation("test:unexpected")))));
        versionOne.put("ResearchPointAwards", unexpectedLedger.serializeNBT());

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(versionOne);

        assertEquals(37, restored.getResearchPoints());
        assertTrue(restored.getResearchPointAwardLedger().isEmpty());
        assertEquals(PlayerProgressionLimits.DATA_VERSION,
                restored.serializeNBT().getInt("DataVersion"));
    }

    @Test
    void recentUnlockHistoryIsBoundedOrderedAndRetainsLargeBatchTotals() {
        PlayerRecipeData data = new PlayerRecipeData();
        List<String> largeBatch = new java.util.ArrayList<>();
        for (int index = 0; index < 80; index++) {
            largeBatch.add("test:member_" + index);
        }
        assertTrue(data.recordRecentUnlockBatch(
                RecentBlueprintUnlockBatch.Source.TREE_RESEARCH,
                "test:member_79",
                largeBatch));
        RecentBlueprintUnlockBatch first = data.getRecentUnlockBatches().get(0);
        assertEquals(80, first.totalMemberCount());
        assertEquals(PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBERS_PER_BATCH,
                first.memberBlueprintIds().size());
        assertTrue(first.memberBlueprintIds().contains("test:member_79"));
        assertTrue(first.truncated());

        for (int batch = 0; batch < 40; batch++) {
            assertTrue(data.recordRecentUnlockBatch(
                    RecentBlueprintUnlockBatch.Source.PHYSICAL_BLUEPRINT,
                    "test:single_" + batch,
                    List.of("test:single_" + batch)));
        }
        assertEquals(PlayerProgressionLimits.MAX_RECENT_UNLOCK_BATCHES,
                data.getRecentUnlockBatches().size());
        assertEquals(41L, data.getRecentUnlockBatches().get(31).sequence());
        assertTrue(data.getRecentUnlockBatches().stream()
                .mapToInt(batch -> batch.memberBlueprintIds().size()).sum()
                <= PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBER_IDS);
        assertFalse(data.recordRecentUnlockBatch(
                RecentBlueprintUnlockBatch.Source.TREE_RESEARCH,
                "test:not_a_member",
                List.of("test:different")));
    }

    @Test
    void researchPointTransactionsAreValidatedAndAtomic() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertTrue(data.addResearchPoints(40, 100));
        assertEquals(40, data.getResearchPoints());
        assertFalse(data.addResearchPoints(61, 100));
        assertFalse(data.addResearchPoints(-1, 100));
        assertFalse(data.addResearchPoints(1, PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertEquals(40, data.getResearchPoints());

        assertTrue(data.spendResearchPoints(15));
        assertEquals(25, data.getResearchPoints());
        assertFalse(data.spendResearchPoints(26));
        assertFalse(data.spendResearchPoints(-1));
        assertEquals(25, data.getResearchPoints());

        assertFalse(data.setResearchPoints(-1));
        assertFalse(data.setResearchPoints(PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertEquals(25, data.getResearchPoints());
        assertTrue(data.setResearchPoints(PlayerProgressionLimits.MAX_RESEARCH_POINTS));
        assertFalse(data.addResearchPoints(1, PlayerProgressionLimits.MAX_RESEARCH_POINTS));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINTS, data.getResearchPoints());
    }

    @Test
    void corruptedPointBalancesAreClampedOnLoad() {
        CompoundTag negative = new CompoundTag();
        negative.putInt("DataVersion", 1);
        negative.putInt("ResearchPoints", -10);
        PlayerRecipeData data = new PlayerRecipeData();
        data.deserializeNBT(negative);
        assertEquals(0, data.getResearchPoints());

        CompoundTag oversized = new CompoundTag();
        oversized.putInt("DataVersion", 1);
        oversized.putInt("ResearchPoints", Integer.MAX_VALUE);
        data.deserializeNBT(oversized);
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINTS, data.getResearchPoints());
    }

    @Test
    void progressionReplacementCommitsAsOneValidatedSnapshot() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint("test:old");
        data.setResearchPoints(10);

        assertFalse(data.replaceProgression(
                List.of("test:new"),
                List.of("test:new"),
                -1));
        assertEquals(Set.of("test:old"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:old"), data.getDiscoveredBlueprints());
        assertEquals(10, data.getResearchPoints());

        assertFalse(data.replaceProgression(
                List.of("test:new", "not a resource location"),
                List.of("test:new"),
                25));
        assertEquals(Set.of("test:old"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:old"), data.getDiscoveredBlueprints());
        assertEquals(10, data.getResearchPoints());

        assertTrue(data.replaceProgression(
                List.of("test:new"),
                List.of("test:history"),
                25));
        assertEquals(Set.of("test:new"), data.getLearnedBlueprints());
        assertEquals(Set.of("test:new", "test:history"), data.getDiscoveredBlueprints());
        assertEquals(25, data.getResearchPoints());

        ClaimKey preserved = ClaimKey.once(
                new net.minecraft.resources.ResourceLocation("test:preserved_claim"));
        assertTrue(data.applyResearchPointTransaction(0, 100, Mutation.claim(preserved)));
        assertTrue(data.replaceProgression(List.of(), List.of(), 5));
        assertTrue(data.spendResearchPoints(2));
        assertTrue(data.getResearchPointAwardLedger().hasClaim(preserved));
        assertEquals(3, data.getResearchPoints());
    }

    @Test
    void allProgressionCollectionsEnforceTheSharedHardLimit() {
        PlayerRecipeData data = new PlayerRecipeData();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            assertTrue(data.addRecipe("test:recipe_" + index));
            assertTrue(data.discoverBlueprint("test:discovered_" + index));
        }

        assertFalse(data.addRecipe("test:recipe_overflow"));
        assertFalse(data.discoverBlueprint("test:discovered_overflow"));
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, data.getLearnedRecipes().size());
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, data.getDiscoveredBlueprints().size());
    }

    @Test
    void loadingPrioritizesLearnedIdsWhileBoundingDiscovery() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("DataVersion", 1);
        ListTag learned = new ListTag();
        ListTag discovered = new ListTag();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            learned.add(StringTag.valueOf("learned:blueprint_" + index));
            discovered.add(StringTag.valueOf("discovered:blueprint_" + index));
        }
        serialized.put("Blueprints", learned);
        serialized.put("DiscoveredBlueprints", discovered);

        PlayerRecipeData restored = new PlayerRecipeData();
        restored.deserializeNBT(serialized);

        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, restored.getLearnedBlueprints().size());
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, restored.getDiscoveredBlueprints().size());
        assertTrue(restored.getDiscoveredBlueprints().containsAll(restored.getLearnedBlueprints()));
    }

    private static ListTag progressList(String id, long value) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Id", id);
        entry.putLong("Value", value);
        ListTag result = new ListTag();
        result.add(entry);
        return result;
    }

    private static ListTag stringList(String... values) {
        ListTag result = new ListTag();
        for (String value : values) {
            result.add(StringTag.valueOf(value));
        }
        return result;
    }
}
