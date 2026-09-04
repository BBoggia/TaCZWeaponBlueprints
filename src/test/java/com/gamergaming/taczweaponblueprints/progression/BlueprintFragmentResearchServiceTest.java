package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentResearchService;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

class BlueprintFragmentResearchServiceTest {
    private static final ResourceLocation PROFILE = new ResourceLocation("test:profile");
    private static final ResourceLocation ROOT = new ResourceLocation("test:root");
    private static final ResourceLocation TARGET = new ResourceLocation("test:target");

    @Test
    void completeSetsDiscountEachMatchingNodeWithoutChangingRouteIdentity() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, ROOT, 5);
        setFragments(data, TARGET, 10);
        ResearchPathUnlockPlanner.Plan base = plan(data);
        Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> progression = Map.of(
                ROOT, resolved(ROOT, 5, BlueprintFragmentDiscount.fixed(3)),
                TARGET, resolved(TARGET, 10, BlueprintFragmentDiscount.percentage(5_000)));

        ResearchPathUnlockPlanner.Plan adjusted = BlueprintFragmentResearchService.adjust(
                base, data, progression);

        assertEquals(base.solution().supportIds(), adjusted.solution().supportIds());
        assertEquals(List.of(ROOT, TARGET), adjusted.nodes().stream()
                .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId).toList());
        assertEquals(12, adjusted.pointCost());
        assertEquals(2, adjusted.fragmentSetUses().size());
        assertEquals(0, adjusted.fragmentSetUses().get(0).archivedAfter());
        assertEquals(0, adjusted.fragmentSetUses().get(1).archivedAfter());
    }

    @Test
    void fragmentEvidenceChangesThePreparedRouteFingerprint() {
        PlayerRecipeData data = new PlayerRecipeData();
        setFragments(data, TARGET, 10);
        ResearchPathUnlockPlanner.Plan adjusted = BlueprintFragmentResearchService.adjust(
                plan(data),
                data,
                Map.of(TARGET, resolved(
                        TARGET, 10, BlueprintFragmentDiscount.percentage(5_000))));
        ResearchRouteFingerprint withSet = ResearchRouteFingerprint.create(
                TARGET,
                adjusted,
                data,
                false,
                ResearchRouteFingerprint.Context.EMPTY);

        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(
                TARGET.toString(), 10, 20));
        ResearchPathUnlockPlanner.Plan changedEvidence = BlueprintFragmentResearchService.adjust(
                plan(data),
                data,
                Map.of(TARGET, resolved(
                        TARGET, 10, BlueprintFragmentDiscount.percentage(5_000))));
        ResearchRouteFingerprint withTwoSets = ResearchRouteFingerprint.create(
                TARGET,
                changedEvidence,
                data,
                false,
                ResearchRouteFingerprint.Context.EMPTY);

        assertNotEquals(withSet, withTwoSets);
        assertEquals(10, changedEvidence.fragmentSetUses().get(0).archivedAfter());
    }

    @Test
    void preparedResearchConsumesEveryMatchingSetOnlyAfterLearningTheRoute() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(20);
        setFragments(data, ROOT, 5);
        setFragments(data, TARGET, 10);
        ResearchPathUnlockPlanner.Plan adjusted = BlueprintFragmentResearchService.adjust(
                plan(data),
                data,
                Map.of(
                        ROOT, resolved(ROOT, 5, BlueprintFragmentDiscount.fixed(3)),
                        TARGET, resolved(TARGET, 10,
                                BlueprintFragmentDiscount.percentage(5_000))));

        BlueprintResearchService.Result result = BlueprintResearchService.commitPreparedPath(
                TARGET,
                data,
                adjusted,
                id -> target(id),
                emptyInput(),
                true);

        assertTrue(result.successful());
        assertEquals(12, result.spentPoints());
        assertEquals(8, data.getResearchPoints());
        assertTrue(data.hasBlueprint(ROOT.toString()));
        assertTrue(data.hasBlueprint(TARGET.toString()));
        assertTrue(data.getArchivedBlueprintFragments().isEmpty());
    }

    @Test
    void failedRouteLearningRestoresPointsFragmentsAndEarlierKnowledge() {
        RejectingTargetLearningData data = new RejectingTargetLearningData();
        data.setResearchPoints(20);
        setFragments(data, ROOT, 5);
        setFragments(data, TARGET, 10);
        ResearchPathUnlockPlanner.Plan adjusted = BlueprintFragmentResearchService.adjust(
                plan(data),
                data,
                Map.of(
                        ROOT, resolved(ROOT, 5, BlueprintFragmentDiscount.fixed(3)),
                        TARGET, resolved(TARGET, 10,
                                BlueprintFragmentDiscount.percentage(5_000))));

        BlueprintResearchService.Result result = BlueprintResearchService.commitPreparedPath(
                TARGET,
                data,
                adjusted,
                BlueprintFragmentResearchServiceTest::target,
                emptyInput(),
                true);

        assertEquals(BlueprintResearchService.Status.TRANSACTION_FAILED, result.status());
        assertEquals(20, data.getResearchPoints());
        assertEquals(5, data.getArchivedBlueprintFragments().get(ROOT.toString()));
        assertEquals(10, data.getArchivedBlueprintFragments().get(TARGET.toString()));
        assertFalse(data.hasBlueprint(ROOT.toString()));
        assertFalse(data.hasBlueprint(TARGET.toString()));
    }

    private static ResearchPathUnlockPlanner.Plan plan(PlayerRecipeData data) {
        List<ResearchPathUnlockPlanner.PlannedNode> nodes = List.of(
                new ResearchPathUnlockPlanner.PlannedNode(ROOT, policy(ROOT, 10, data), false),
                new ResearchPathUnlockPlanner.PlannedNode(TARGET, policy(TARGET, 10, data), false));
        return new ResearchPathUnlockPlanner.Plan(
                new ResearchPathUnlockPlanner.SelectedUnlockSolution(nodes),
                new ResearchPathUnlockPlanner.RouteQuote(20, List.of(), false));
    }

    private static BlueprintResearchPolicy policy(
            ResourceLocation id,
            int points,
            PlayerRecipeData data) {
        return new BlueprintResearchPolicy(
                id,
                PROFILE,
                true,
                false,
                true,
                false,
                false,
                data.getResearchPoints(),
                100,
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(points, List.of()),
                false,
                ResearchRequirements.EMPTY,
                List.of(),
                true,
                false,
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE);
    }

    private static ResolvedBlueprintProgressionPolicy resolved(
            ResourceLocation id,
            int threshold,
            BlueprintFragmentDiscount discount) {
        return new ResolvedBlueprintProgressionPolicy(
                PROFILE,
                id,
                ResearchWorkbenchTier.TIER_1,
                new BlueprintFragmentPolicy(
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        threshold,
                        100,
                        discount,
                        1),
                ProgressionGateRequirements.EMPTY,
                ResolvedBlueprintProgressionPolicy.TierSource.FALLBACK,
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    private static void setFragments(
            PlayerRecipeData data,
            ResourceLocation id,
            int count) {
        data.applyArchivedFragmentMutation(PlayerProgressValueMutation.Request.commit(
                id.toString(), 0, count));
    }

    private static BlueprintLearningService.LearningTarget target(ResourceLocation id) {
        return new BlueprintLearningService.LearningTarget(
                id,
                new ResourceLocation(id.getNamespace(), "gun/" + id.getPath()));
    }

    private static BlueprintResearchService.ResearchInput emptyInput() {
        return new BlueprintResearchService.ResearchInput() {
            @Override
            public List<ItemStack> stacks() {
                return List.of();
            }

            @Override
            public boolean canAcceptOutput() {
                return false;
            }

            @Override
            public void consume(ResearchIngredientPlanner.Plan plan) {
            }

            @Override
            public void restore(List<ItemStack> snapshot) {
            }

            @Override
            public ItemStack createOutput(ResourceLocation blueprintId) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean deliver(ItemStack output) {
                return false;
            }
        };
    }

    private static final class RejectingTargetLearningData extends PlayerRecipeData {
        @Override
        public synchronized BlueprintLearningMutation.Result applyBlueprintLearning(
                BlueprintLearningMutation.Request request) {
            if (request.operation() == BlueprintLearningMutation.Operation.COMMIT
                    && TARGET.toString().equals(request.blueprintId())) {
                return BlueprintLearningMutation.Result.unchanged(
                        BlueprintLearningMutation.Status.ALREADY_LEARNED,
                        request.operation());
            }
            return super.applyBlueprintLearning(request);
        }
    }
}
