package com.gamergaming.taczweaponblueprints.progression.analyzer;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnalysisFragmentReconstructionTest {
    static final ResourceLocation TARGET = new ResourceLocation("test:rifle");
    static BlueprintFragmentItem fragment;
    static BlueprintItem blueprint;
    @BeforeAll static void bootstrap() {
        com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.initialize();
        fragment = com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.fragment;
        blueprint = com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems.blueprint;
    }
    static AnalysisFragmentReconstructionService.Policy policy(int threshold, int cap) {
        return new AnalysisFragmentReconstructionService.Policy(TARGET, true, threshold, cap, true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES, 1);
    }
    @ParameterizedTest @EnumSource(AnalysisRewardClass.class)
    void everyClassReconstructsWithItsRecordedPermissionsEvenAfterLearning(AnalysisRewardClass reward) {
        var data = new PlayerRecipeData(); data.addBlueprint(TARGET.toString()); data.setResearchPoints(17);
        var tx = new Transaction(reward, 7);
        var evaluation = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, policy(5, 1000));
        assertTrue(evaluation.ready()); assertTrue(evaluation.createsBlueprint());
        var result = AnalysisFragmentReconstructionService.commit(evaluation, policy(5, 1000), data, tx);
        assertTrue(result.successful()); assertEquals(7, result.consumed());
        assertEquals(2, data.getArchivedAnalysisFragments().get(TARGET.toString()).get(reward));
        assertEquals(reward.provenance(BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION), BlueprintItem.getProvenance(tx.output).orElseThrow());
        assertEquals(17, data.getResearchPoints()); assertTrue(data.getArchivedBlueprintFragments().isEmpty());
        assertTrue(BlueprintFragmentItem.getOrdinaryTarget(analysisFragment(reward, 1)).isEmpty());
    }
    @Test void olderCapabilitiesFailUnsupportedEvenForASetWithNoNetArchiveChange() {
        var delegate = new PlayerRecipeData();
        var legacy = (IPlayerRecipeData) java.lang.reflect.Proxy.newProxyInstance(IPlayerRecipeData.class.getClassLoader(),
                new Class<?>[]{IPlayerRecipeData.class}, (proxy, method, args) -> {
                    if (method.getName().equals("applyAnalysisFragmentMutation") || method.getName().equals("getArchivedAnalysisFragments")) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    return method.invoke(delegate, args);
                });
        var tx = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES, 5);
        assertEquals(AnalysisFragmentReconstructionService.Status.UNSUPPORTED,
                AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, legacy, policy(5,100)).status());
        assertEquals(5,tx.input.getCount());
    }
    @Test void capAndOrdinaryProgressLimitDepositsAcrossAllRewardClasses() {
        var data = new PlayerRecipeData();
        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(TARGET.toString(),0,PlayerProgressionLimits.MAX_PROGRESS_VALUE-2));
        var tx = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES, 8);
        var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, policy(5,100));
        assertEquals(2,e.accepted()); assertFalse(e.createsBlueprint());
        assertTrue(AnalysisFragmentReconstructionService.commit(e,policy(5,100),data,tx).successful());
        assertEquals(6,tx.input.getCount());
        var next = new Transaction(AnalysisRewardClass.RECYCLABLE_BYPASS,1);
        assertEquals(AnalysisFragmentReconstructionService.Status.ARCHIVE_FULL,
                AnalysisFragmentReconstructionService.evaluate(next.input,next.output,data,policy(5,100)).status());
    }

    @Test void archiveAtOrAboveNewCapCanReconstructWithoutConsumingTheIdentifyingInput() {
        var data = new PlayerRecipeData(); var reward = AnalysisRewardClass.PROTECTED_PREREQUISITES;
        data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                TARGET.toString(), AnalysisFragmentCounts.EMPTY, AnalysisFragmentCounts.EMPTY.with(reward, 12)));
        var tx = new Transaction(reward, 3);
        var p = policy(5, 5);
        var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, p);
        assertTrue(e.ready()); assertEquals(0, e.accepted());
        assertTrue(AnalysisFragmentReconstructionService.commit(e, p, data, tx).successful());
        assertEquals(3, tx.input.getCount()); assertEquals(7, data.getArchivedAnalysisFragments().get(TARGET.toString()).total());
    }
    @Test void classesDoNotCombineAndBlockedOutputOrDisabledPolicyNeverConsumes() {
        var data = new PlayerRecipeData();
        data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                TARGET.toString(), AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(4, 0, 0, 0)));
        var tx = new Transaction(AnalysisRewardClass.RECYCLABLE_BYPASS, 1);
        var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, policy(5, 5));
        assertFalse(e.createsBlueprint()); assertTrue(e.ready());
        assertTrue(AnalysisFragmentReconstructionService.commit(e, policy(5, 5), data, tx).successful());
        assertEquals(5, data.getArchivedAnalysisFragments().get(TARGET.toString()).total());
        tx = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES, 1); tx.output = new ItemStack(Items.STONE);
        var blocked = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, policy(4, 5));
        assertEquals(AnalysisFragmentReconstructionService.Status.OUTPUT_OCCUPIED, blocked.status());
        assertEquals(0, blocked.accepted()); assertEquals(blocked.before(), blocked.after()); assertFalse(blocked.createsBlueprint());
        var disabled = new AnalysisFragmentReconstructionService.Policy(TARGET, false, 4, 5, true,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES, 2);
        assertEquals(AnalysisFragmentReconstructionService.Status.DISABLED,
                AnalysisFragmentReconstructionService.evaluate(tx.input, ItemStack.EMPTY, data, disabled).status());
    }
    @Test void rollbackRestoresInputOutputAndAllClassCountsAfterPostMutationFailure() {
        for (int failStage = 1; failStage <= 2; failStage++) {
            var data = new PlayerRecipeData(); var tx = new Transaction(AnalysisRewardClass.PROTECTED_BYPASS, 7);
            data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                    TARGET.toString(), AnalysisFragmentCounts.EMPTY, new AnalysisFragmentCounts(2,3,4,5)));
            tx.failStage = failStage;
            var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, policy(5, 100));
            var before = data.serializeNBT();
            var r = AnalysisFragmentReconstructionService.commit(e, policy(5, 100), data, tx);
            assertEquals(AnalysisFragmentReconstructionService.Status.TRANSACTION_FAILED, r.status());
            assertEquals(7, tx.input.getCount()); assertTrue(tx.output.isEmpty()); assertEquals(before, data.serializeNBT());
        }
    }
    @Test void archiveExceptionsAfterMutationRollBackAndFailedRestorationIsReported() {
        var data = new PlayerRecipeData() {
            @Override public synchronized PlayerProgressValueMutation.Status applyAnalysisFragmentMutation(AnalysisFragmentMutation request) {
                var result = super.applyAnalysisFragmentMutation(request);
                if (request.operation() == PlayerProgressValueMutation.Operation.COMMIT) throw new IllegalStateException("injected post-write failure");
                return result;
            }
        };
        var tx = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES, 7);
        var p = policy(5,100);
        var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, p);
        assertEquals(AnalysisFragmentReconstructionService.Status.TRANSACTION_FAILED,
                AnalysisFragmentReconstructionService.commit(e,p,data,tx).status());
        assertTrue(data.getArchivedAnalysisFragments().isEmpty()); assertEquals(7,tx.input.getCount());
        var broken = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES,7) {
            @Override public boolean restore(ItemStack before, ItemStack output) { return false; }
        };
        broken.failStage = 1;
        var regular = new PlayerRecipeData();
        var quote = AnalysisFragmentReconstructionService.evaluate(broken.input,broken.output,regular,p);
        assertEquals(AnalysisFragmentReconstructionService.Status.ROLLBACK_FAILED,
                AnalysisFragmentReconstructionService.commit(quote,p,regular,broken).status());
    }

    @Test void staleThresholdOrInputAndMalformedOutputsAreRejectedBeforeConsumption() {
        var data = new PlayerRecipeData(); var tx = new Transaction(AnalysisRewardClass.RECYCLABLE_BYPASS, 5);
        var p = policy(5, 100); var e = AnalysisFragmentReconstructionService.evaluate(tx.input, tx.output, data, p);
        assertEquals(AnalysisFragmentReconstructionService.Status.STALE,
                AnalysisFragmentReconstructionService.commit(e, policy(6, 100), data, tx).status());
        tx.invalidOutput = true;
        assertEquals(AnalysisFragmentReconstructionService.Status.TRANSACTION_FAILED,
                AnalysisFragmentReconstructionService.commit(e, p, data, tx).status());
        assertEquals(5, tx.input.getCount()); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        tx.invalidOutput = false; tx.input.shrink(1);
        assertEquals(AnalysisFragmentReconstructionService.Status.STALE,
                AnalysisFragmentReconstructionService.commit(e, p, data, tx).status());
    }
    @Test void stationOrPolicyInvalidationDuringPreflightOrWritingCannotCompleteReconstruction() {
        var data = new PlayerRecipeData(); var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        var tx = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES,5) {
            @Override public ItemStack createOutput(ResourceLocation target, BlueprintProvenance provenance) {
                current.set(false); return super.createOutput(target,provenance);
            }
        };
        var policy=policy(5,100); var quote=AnalysisFragmentReconstructionService.evaluate(tx.input,tx.output,data,policy);
        assertEquals(AnalysisFragmentReconstructionService.Status.STALE,
                AnalysisFragmentReconstructionService.commit(quote,policy,data,tx,current::get).status());
        assertEquals(5,tx.input.getCount()); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
        current.set(true);
        var writing = new Transaction(AnalysisRewardClass.PROTECTED_PREREQUISITES,5) {
            @Override public boolean consumePhysical(ItemStack input,int count) {
                boolean consumed=super.consumePhysical(input,count); current.set(false); return consumed;
            }
        };
        quote=AnalysisFragmentReconstructionService.evaluate(writing.input,writing.output,data,policy);
        assertEquals(AnalysisFragmentReconstructionService.Status.TRANSACTION_FAILED,
                AnalysisFragmentReconstructionService.commit(quote,policy,data,writing,current::get).status());
        assertEquals(5,writing.input.getCount()); assertTrue(writing.output.isEmpty()); assertTrue(data.getArchivedAnalysisFragments().isEmpty());
    }

    static ItemStack analysisFragment(AnalysisRewardClass reward, int count) {
        var stack = BlueprintFragmentItem.createAnalysis(fragment, TARGET, reward); stack.setCount(count); return stack;
    }
    static class Transaction implements BlueprintFragmentAnalysisService.WorkstationTransaction {
        ItemStack input, output = ItemStack.EMPTY;
        int failStage; boolean invalidOutput;
        Transaction(AnalysisRewardClass reward, int count) { input = analysisFragment(reward, count); }
        public ItemStack physicalInput() { return input; }
        public ItemStack outputStack() { return output; }
        public ItemStack createOutput(ResourceLocation target, BlueprintProvenance provenance) {
            if (invalidOutput) return new ItemStack(Items.PAPER);
            var result = new ItemStack(blueprint);
            result.getOrCreateTag().putString("bpId", target.toString());
            result.getOrCreateTag().put(BlueprintProvenance.TAG_KEY, provenance.toTag()); return result;
        }
        public boolean consumePhysical(ItemStack expected, int count) {
            if (!ItemStack.matches(input, expected)) return false;
            input.shrink(count); return failStage != 1;
        }
        public boolean placeOutput(ItemStack created, ItemStack expected) { output = created.copy(); return failStage != 2; }
        public boolean restore(ItemStack before, ItemStack oldOutput) { input = before.copy(); output = oldOutput.copy(); return true; }
    }
}
