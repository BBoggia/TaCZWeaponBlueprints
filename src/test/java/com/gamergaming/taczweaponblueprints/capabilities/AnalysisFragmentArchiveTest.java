package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.TreeMap;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.PlayerProgressionAdminService;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class AnalysisFragmentArchiveTest {
    static final String TARGET = "test:rifle";
    static AnalysisFragmentCounts counts(int count) { return new AnalysisFragmentCounts(count, 0, 0, 0); }
    static PlayerProgressValueMutation.Status set(PlayerRecipeData data, String target, AnalysisFragmentCounts before, AnalysisFragmentCounts after) {
        return data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT, target, before, after));
    }
    @Test void allClassesPersistIndependentlyAndVersionFourResavesWithoutAnalysis() {
        var data = new PlayerRecipeData();
        assertEquals(PlayerProgressValueMutation.Status.APPLIED, set(data, TARGET, AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(1,2,3,4)));
        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET, 0, 8));
        var saved = data.serializeNBT(); assertEquals(5, saved.getInt("DataVersion"));
        var copy = new PlayerRecipeData(); copy.deserializeNBT(saved);
        assertEquals(saved, copy.serializeNBT());
        saved.putInt("DataVersion",4); copy.deserializeNBT(saved);
        assertTrue(copy.getArchivedAnalysisFragments().isEmpty()); assertEquals(Map.of(TARGET,8), copy.getArchivedBlueprintFragments());
    }
    @Test void sharedHardLimitAndUnionOfTargetsApplyToBothMutationDirections() {
        var data = new PlayerRecipeData();
        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET, 0, PlayerProgressionLimits.MAX_PROGRESS_VALUE-2));
        assertEquals(PlayerProgressValueMutation.Status.APPLIED, set(data, TARGET, AnalysisFragmentCounts.EMPTY, counts(2)));
        assertEquals(PlayerProgressValueMutation.Status.CAPACITY_REACHED, set(data, TARGET, counts(2), counts(3)));
        assertEquals(PlayerProgressValueMutation.Status.CAPACITY_REACHED, data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit(TARGET, PlayerProgressionLimits.MAX_PROGRESS_VALUE-2, PlayerProgressionLimits.MAX_PROGRESS_VALUE)).status());
        var ordinary = new TreeMap<String,Integer>();
        for (int i=0; i<PlayerProgressionLimits.MAX_FRAGMENT_TARGETS; i++) ordinary.put("test:t"+i,1);
        assertTrue(data.replaceSupplementalProgression(ordinary, Map.of(), Map.of()));
        assertEquals(PlayerProgressValueMutation.Status.CAPACITY_REACHED, set(data, TARGET, AnalysisFragmentCounts.EMPTY, counts(1)));
        assertEquals(PlayerProgressValueMutation.Status.APPLIED, set(data, "test:t1", AnalysisFragmentCounts.EMPTY, counts(1)));
        assertTrue(data.clearArchivedBlueprintFragments());
        var analysis = new TreeMap<String,AnalysisFragmentCounts>(); ordinary.keySet().forEach(id -> analysis.put(id,counts(1)));
        assertTrue(data.replaceSupplementalProgression(Map.of(), Map.of(), analysis));
        assertEquals(PlayerProgressValueMutation.Status.CAPACITY_REACHED, data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET,0,1)).status());
    }
    @Test void staleCompareAndSetAndInvalidReplacementLeaveEveryChannelUnchanged() {
        var data=new PlayerRecipeData(); set(data,TARGET,AnalysisFragmentCounts.EMPTY,counts(2));
        var saved=data.serializeNBT();
        assertEquals(PlayerProgressValueMutation.Status.STALE,set(data,TARGET,counts(1),counts(3)));
        assertFalse(data.replaceSupplementalProgression(Map.of(TARGET,PlayerProgressionLimits.MAX_PROGRESS_VALUE),Map.of(),Map.of(TARGET,counts(1))));
        assertEquals(saved,data.serializeNBT());
        assertTrue(data.replaceSupplementalProgression(Map.of(TARGET,1),Map.of()));
        assertEquals(counts(2),data.getArchivedAnalysisFragments().get(TARGET));
        assertThrows(UnsupportedOperationException.class,()->data.getArchivedAnalysisFragments().clear());
        assertThrows(IllegalArgumentException.class,()->new AnalysisFragmentCounts(Integer.MAX_VALUE,1,0,0));
    }
    @Test void malformedOrOverflowingAnalysisNeverDisplacesOrdinaryProgress() {
        var data=new PlayerRecipeData(); data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET,0,PlayerProgressionLimits.MAX_PROGRESS_VALUE));
        var saved=data.serializeNBT(); var list=new ListTag();
        var entry=new CompoundTag(); entry.putString("Id",TARGET); entry.put("Counts",counts(1).toTag()); list.add(entry);
        saved.put("ArchivedAnalysisFragments",list); data.deserializeNBT(saved);
        assertTrue(data.getArchivedAnalysisFragments().isEmpty()); assertEquals(PlayerProgressionLimits.MAX_PROGRESS_VALUE,data.getArchivedBlueprintFragments().get(TARGET));
        entry.putString("Id","removed:weapon"); entry.getCompound("Counts").putInt("future_class",1);
        data.deserializeNBT(saved); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        entry.getCompound("Counts").remove("future_class"); list.add(entry.copy());
        data.deserializeNBT(saved); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
    }
    @Test void adminResetsAndInspectionIncludeAnalysisButLearningNeverConvertsIt() {
        var data=new PlayerRecipeData(); set(data,TARGET,AnalysisFragmentCounts.EMPTY,counts(5));
        data.addBlueprint(TARGET); assertEquals(0,data.getResearchPoints()); assertEquals(5,data.getArchivedAnalysisFragments().get(TARGET).total());
        var inspected=PlayerProgressionAdminService.inspect(data); assertEquals(1,inspected.analysisFragmentTargets()); assertEquals(5,inspected.analysisFragments());
        PlayerProgressionAdminService.reset(data,PlayerProgressionAdminService.ResetState.LEARNED);
        assertEquals(5,data.getArchivedAnalysisFragments().get(TARGET).total());
        PlayerProgressionAdminService.reset(data,PlayerProgressionAdminService.ResetState.FRAGMENTS);
        assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        set(data,TARGET,AnalysisFragmentCounts.EMPTY,counts(1)); PlayerProgressionAdminService.reset(data,PlayerProgressionAdminService.ResetState.ALL);
        assertTrue(data.getArchivedAnalysisFragments().isEmpty());
    }
}
