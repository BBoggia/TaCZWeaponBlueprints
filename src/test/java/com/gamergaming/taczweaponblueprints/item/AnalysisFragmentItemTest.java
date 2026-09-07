package com.gamergaming.taczweaponblueprints.item;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnalysisFragmentItemTest {
    static final ResourceLocation TARGET = new ResourceLocation("test:rifle");
    @BeforeAll static void initialize() { AnalysisTestItems.initialize(); }
    @Test void allClassesRoundTripThroughItemSaveCopyAndSplitWithExactStackIdentity() {
        for (var reward : AnalysisRewardClass.values()) {
            var stack = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, TARGET, reward); stack.setCount(12);
            var restored = ItemStack.of(stack.save(new CompoundTag()));
            var split = restored.split(3);
            assertEquals(reward, BlueprintFragmentItem.identity(split).orElseThrow().analysisClass().orElseThrow());
            assertTrue(ItemStack.isSameItemSameTags(stack, split)); assertEquals(9, restored.getCount());
            assertTrue(BlueprintFragmentItem.getOrdinaryTarget(stack).isEmpty());
            for (var other : AnalysisRewardClass.values()) {
                var candidate = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, TARGET, other);
                assertEquals(reward == other, ItemStack.isSameItemSameTags(stack, candidate));
            }
            var different = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, new ResourceLocation("test:other"), reward);
            assertFalse(ItemStack.isSameItemSameTags(stack, different));
        }
        var ordinary = BlueprintFragmentItem.create(AnalysisTestItems.fragment, TARGET);
        assertTrue(BlueprintFragmentItem.getOrdinaryTarget(ordinary).isPresent());
        assertFalse(ItemStack.isSameItemSameTags(ordinary, BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, TARGET, AnalysisRewardClass.PROTECTED_PREREQUISITES)));
    }
    @Test void presentMalformedMarkersNeverBecomeOrdinaryFragments() {
        var valid = BlueprintFragmentItem.createAnalysis(AnalysisTestItems.fragment, TARGET, AnalysisRewardClass.PROTECTED_PREREQUISITES);
        for (int mutation=0; mutation<7; mutation++) {
            var stack=valid.copy(); var root=stack.getOrCreateTag(); var marker=root.getCompound(BlueprintFragmentItem.ANALYSIS_TAG);
            switch(mutation) {
                case 0 -> marker.putInt("format",2);
                case 1 -> marker.remove("reward_class");
                case 2 -> marker.putString("reward_class","future_class");
                case 3 -> root.putString(BlueprintFragmentItem.ANALYSIS_TAG,"invalid");
                case 4 -> marker.putString("format","1");
                case 5 -> marker.putInt("seed",1);
                case 6 -> root.putInt("extra",1);
            }
            assertTrue(BlueprintFragmentItem.identity(stack).isEmpty());
            assertTrue(BlueprintFragmentItem.getTarget(stack).isEmpty());
            assertTrue(BlueprintFragmentItem.getOrdinaryTarget(stack).isEmpty());
        }
    }
    @Test void policyRestrictionCannotPromoteStoredRecyclingOrLearningPermissions() {
        for (var reward : AnalysisRewardClass.values()) {
            var restricted=reward.restrict(false,com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES);
            assertEquals(AnalysisRewardClass.PROTECTED_PREREQUISITES,restricted);
            assertEquals(reward,reward.restrict(true,com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));
        }
    }
}
