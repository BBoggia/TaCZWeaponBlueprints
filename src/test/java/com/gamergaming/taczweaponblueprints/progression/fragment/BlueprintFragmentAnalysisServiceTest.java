package com.gamergaming.taczweaponblueprints.progression.fragment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintFragmentAnalysisServiceTest {
    private static final ResourceLocation TARGET = new ResourceLocation("test:rifle");
    private static final ResourceLocation PROFILE = new ResourceLocation("test:profile");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void boostModeArchivesRawProgressWithoutCompletingResearch() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, 4);
        EvaluationFixture fixture = evaluate(
                data,
                new BlueprintFragmentPolicy(
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        5,
                        20,
                        BlueprintFragmentDiscount.percentage(2_500),
                        1),
                2,
                ItemStack.EMPTY);

        assertTrue(fixture.evaluation().ready());
        assertEquals(4, fixture.evaluation().archivedBefore());
        assertEquals(6, fixture.evaluation().archivedAfterDeposit());
        assertEquals(6, fixture.evaluation().archivedAfterAction());
        assertFalse(fixture.evaluation().consumesSet());

        BlueprintFragmentAnalysisService.Result result =
                BlueprintFragmentAnalysisService.commit(
                        fixture.evaluation(), data, fixture.transaction());
        assertTrue(result.successful());
        assertEquals(6, data.getArchivedBlueprintFragments().get(TARGET.toString()));
        assertTrue(fixture.transaction().input.isEmpty());
    }

    @Test
    void learnedTargetConsumesOneSetAndCreditsOnlyAFullRpAward() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(TARGET.toString());
        setFragments(data, 4);
        EvaluationFixture fixture = evaluate(
                data,
                reconstructionPolicy(3),
                1,
                ItemStack.EMPTY);

        assertEquals(3, fixture.evaluation().awardedPoints());
        assertEquals(0, fixture.evaluation().archivedAfterAction());
        BlueprintFragmentAnalysisService.Result result =
                BlueprintFragmentAnalysisService.commit(
                        fixture.evaluation(), data, fixture.transaction());

        assertTrue(result.successful());
        assertEquals(3, data.getResearchPoints());
        assertTrue(data.getArchivedBlueprintFragments().isEmpty());
        assertTrue(fixture.transaction().output.isEmpty());
    }

    @Test
    void failedPhysicalConsumptionRestoresPointsProgressAndBothSlots() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(TARGET.toString());
        setFragments(data, 4);
        EvaluationFixture fixture = evaluate(
                data,
                reconstructionPolicy(3),
                1,
                ItemStack.EMPTY);
        fixture.transaction().failConsumption = true;

        BlueprintFragmentAnalysisService.Result result =
                BlueprintFragmentAnalysisService.commit(
                        fixture.evaluation(), data, fixture.transaction());

        assertEquals(BlueprintFragmentAnalysisService.Status.TRANSACTION_FAILED, result.status());
        assertEquals(0, data.getResearchPoints());
        assertEquals(4, data.getArchivedBlueprintFragments().get(TARGET.toString()));
        assertEquals(1, fixture.transaction().input.getCount());
        assertTrue(fixture.transaction().output.isEmpty());
    }

    @Test
    void secondDepositPreparedFromTheSameSharedBenchStateIsRejectedAsStale() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, 4);
        FakeTransaction transaction = new FakeTransaction(fragment(2), ItemStack.EMPTY);
        ResolvedBlueprintProgressionPolicy policy = resolved(reconstructionPolicy(2));
        BlueprintFragmentAnalysisService.Evaluation first =
                BlueprintFragmentAnalysisService.evaluate(
                        TARGET, 2, ItemStack.EMPTY, data, policy, 100, 9L);
        BlueprintFragmentAnalysisService.Evaluation concurrent =
                BlueprintFragmentAnalysisService.evaluate(
                        TARGET, 2, ItemStack.EMPTY, data, policy, 100, 9L);

        assertTrue(BlueprintFragmentAnalysisService.commit(first, data, transaction).successful());
        BlueprintFragmentAnalysisService.Result rejected =
                BlueprintFragmentAnalysisService.commit(concurrent, data, transaction);

        assertEquals(BlueprintFragmentAnalysisService.Status.STALE_POLICY, rejected.status());
        assertEquals(1, data.getArchivedBlueprintFragments().get(TARGET.toString()));
        assertTrue(transaction.input.isEmpty());
        assertFalse(transaction.output.isEmpty());
    }

    @Test
    void reconstructionCreatesProtectedPrerequisiteRespectingBlueprint() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, 4);
        EvaluationFixture fixture = evaluate(
                data,
                reconstructionPolicy(2),
                1,
                ItemStack.EMPTY);

        assertTrue(fixture.evaluation().createsBlueprint());
        BlueprintFragmentAnalysisService.Result result =
                BlueprintFragmentAnalysisService.commit(
                        fixture.evaluation(), data, fixture.transaction());

        assertTrue(result.successful());
        BlueprintProvenance provenance = BlueprintProvenance
                .fromTag(fixture.transaction().output.getTag()).orElseThrow();
        assertEquals(BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION, provenance.source());
        assertFalse(provenance.recyclable());
        assertTrue(provenance.learningMode().prerequisitesRequired());
    }

    @Test
    void learnedFragmentsRejectWhenReturnIsDisabledOrFull() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(TARGET.toString());
        setFragments(data, 4);
        assertEquals(
                BlueprintFragmentAnalysisService.Status.LEARNED_TARGET_RETURN_DISABLED,
                evaluate(data, reconstructionPolicy(0), 1, ItemStack.EMPTY)
                        .evaluation().status());
        data.setResearchPoints(10);
        BlueprintFragmentAnalysisService.Evaluation full =
                evaluate(data, reconstructionPolicy(2), 1, ItemStack.EMPTY, 10)
                        .evaluation();
        assertEquals(BlueprintFragmentAnalysisService.Status.POINT_CAP_REACHED, full.status());
        assertFalse(full.consumesSet());
        assertEquals(full.archivedAfterDeposit(), full.archivedAfterAction());
    }

    @Test
    void learnedCompletedSetAtRetentionCapCanReturnRpWithoutConsumingNewInput() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.addBlueprint(TARGET.toString());
        setFragments(data, 20);
        EvaluationFixture fixture = evaluate(
                data,
                reconstructionPolicy(3),
                1,
                ItemStack.EMPTY);

        assertTrue(fixture.evaluation().ready());
        assertEquals(0, fixture.evaluation().accepted());
        assertTrue(fixture.evaluation().consumesSet());
        assertEquals(15, fixture.evaluation().archivedAfterAction());

        BlueprintFragmentAnalysisService.Result result =
                BlueprintFragmentAnalysisService.commit(
                        fixture.evaluation(), data, fixture.transaction());
        assertTrue(result.successful());
        assertEquals(0, result.consumed());
        assertEquals(3, data.getResearchPoints());
        assertEquals(15, data.getArchivedBlueprintFragments().get(TARGET.toString()));
        assertEquals(1, fixture.transaction().input.getCount());
    }

    @Test
    void blockedReconstructionDoesNotAdvertiseSetConsumption() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, 4);
        BlueprintFragmentAnalysisService.Evaluation evaluation = evaluate(
                data,
                reconstructionPolicy(2),
                1,
                new ItemStack(Items.STONE)).evaluation();

        assertEquals(BlueprintFragmentAnalysisService.Status.OUTPUT_OCCUPIED, evaluation.status());
        assertFalse(evaluation.consumesSet());
        assertEquals(evaluation.archivedAfterDeposit(), evaluation.archivedAfterAction());
    }

    private static BlueprintFragmentPolicy reconstructionPolicy(int learnedRp) {
        return new BlueprintFragmentPolicy(
                BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT,
                5,
                20,
                BlueprintFragmentDiscount.NONE,
                learnedRp);
    }

    private static EvaluationFixture evaluate(
            PlayerRecipeData data,
            BlueprintFragmentPolicy policy,
            int count,
            ItemStack output) {
        return evaluate(data, policy, count, output, 100);
    }

    private static EvaluationFixture evaluate(
            PlayerRecipeData data,
            BlueprintFragmentPolicy policy,
            int count,
            ItemStack output,
            int pointCap) {
        FakeTransaction transaction = new FakeTransaction(fragment(count), output.copy());
        BlueprintFragmentAnalysisService.Evaluation evaluation =
                BlueprintFragmentAnalysisService.evaluate(
                        TARGET,
                        count,
                        output,
                        data,
                        resolved(policy),
                        pointCap,
                        1L);
        return new EvaluationFixture(evaluation, transaction);
    }

    private static ResolvedBlueprintProgressionPolicy resolved(
            BlueprintFragmentPolicy fragments) {
        return new ResolvedBlueprintProgressionPolicy(
                PROFILE,
                TARGET,
                ResearchWorkbenchTier.TIER_1,
                fragments,
                ProgressionGateRequirements.EMPTY,
                ResolvedBlueprintProgressionPolicy.TierSource.FALLBACK,
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    private static ItemStack fragment(int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        CompoundTag tag = new CompoundTag();
        tag.putString(BlueprintFragmentItem.TARGET_TAG, TARGET.toString());
        stack.setTag(tag);
        return stack;
    }

    private static void setFragments(PlayerRecipeData data, int count) {
        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(
                TARGET.toString(), 0, count));
    }

    private record EvaluationFixture(
            BlueprintFragmentAnalysisService.Evaluation evaluation,
            FakeTransaction transaction) {
    }

    private static final class FakeTransaction
            implements BlueprintFragmentAnalysisService.WorkstationTransaction {
        private ItemStack input;
        private ItemStack output;
        private boolean failConsumption;

        private FakeTransaction(ItemStack input, ItemStack output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public ItemStack physicalInput() {
            return input;
        }

        @Override
        public Optional<ResourceLocation> fragmentTarget() {
            return Optional.of(TARGET);
        }

        @Override
        public ItemStack outputStack() {
            return output;
        }

        @Override
        public ItemStack createOutput(
                ResourceLocation blueprintId,
                BlueprintProvenance provenance) {
            ItemStack created = new ItemStack(Items.PAPER);
            CompoundTag root = new CompoundTag();
            root.put(BlueprintProvenance.TAG_KEY, provenance.toTag());
            created.setTag(root);
            return created;
        }

        @Override
        public boolean consumePhysical(ItemStack expectedInput, int count) {
            if (failConsumption || !ItemStack.matches(expectedInput, input)
                    || input.getCount() < count) {
                return false;
            }
            input.shrink(count);
            return true;
        }

        @Override
        public boolean placeOutput(ItemStack created, ItemStack expectedOutput) {
            if (!ItemStack.matches(expectedOutput, output) || !output.isEmpty()) {
                return false;
            }
            output = created.copy();
            return true;
        }

        @Override
        public boolean restore(ItemStack physicalInput, ItemStack restoredOutput) {
            input = physicalInput.copy();
            output = restoredOutput.copy();
            return true;
        }
    }
}
