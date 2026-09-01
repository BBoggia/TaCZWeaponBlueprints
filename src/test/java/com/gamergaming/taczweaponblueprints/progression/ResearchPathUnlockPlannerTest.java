package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class ResearchPathUnlockPlannerTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TARGET = id("test:target");
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");
    private static final ResourceLocation C = id("test:c");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anyOfChoosesFewestNewNodesBeforeConfiguredPointCost() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(C)),
                spec(B, 20, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(B, TARGET), ids(plan));
        assertEquals(21, plan.pointCost());
    }

    @Test
    void anyOfTiesUseLowerRpThenCanonicalId() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> lowerCost = policies(data,
                spec(A, 7, ResearchRequirements.EMPTY),
                spec(B, 3, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        assertEquals(List.of(B, TARGET), ids(plan(data, lowerCost, TARGET)));

        Map<ResourceLocation, BlueprintResearchPolicy> lexical = policies(data,
                spec(A, 3, ResearchRequirements.EMPTY),
                spec(B, 3, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(B, A)));
        assertEquals(List.of(A, TARGET), ids(plan(data, lexical, TARGET)));
    }

    @Test
    void mandatoryBranchesDeduplicateSharedPrerequisitesAndKeepTopologicalOrder() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(A, 2, legacy(C)),
                spec(B, 3, legacy(C)),
                spec(TARGET, 4, and(A, B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(C, A, B, TARGET), ids(plan));
        assertEquals(10, plan.pointCost());
    }

    @Test
    void exemptPrerequisitesSatisfyGroupsWithoutBeingPurchased() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> singleton = policies(data,
                spec(A, 40, ResearchRequirements.EMPTY),
                spec(TARGET, 3, legacy(A)));

        ResearchPathUnlockPlanner.Result singletonResult = ResearchPathUnlockPlanner.plan(
                TARGET, data, singleton::get, A::equals, false);

        assertTrue(singletonResult.successful());
        assertEquals(List.of(TARGET), ids(singletonResult.plan().orElseThrow()));
        assertEquals(3, singletonResult.plan().orElseThrow().pointCost());

        Map<ResourceLocation, BlueprintResearchPolicy> choice = policies(data,
                spec(A, 40, ResearchRequirements.EMPTY),
                spec(B, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 3, anyOf(A, B)));
        ResearchPathUnlockPlanner.Result choiceResult = ResearchPathUnlockPlanner.plan(
                TARGET, data, choice::get, A::equals, false);
        assertEquals(List.of(TARGET), ids(choiceResult.plan().orElseThrow()));

        ResearchPathUnlockPlanner.Result exemptTarget = ResearchPathUnlockPlanner.plan(
                TARGET, data, choice::get, TARGET::equals, false);
        assertEquals(BlueprintResearchService.Status.POLICY_INELIGIBLE,
                exemptTarget.status());
    }

    @Test
    void mandatoryChoiceGroupsAreOptimizedAsOneGlobalClosure() {
        ResourceLocation a1 = id("test:a1");
        ResourceLocation a2 = id("test:a2");
        ResourceLocation c1 = id("test:c1");
        ResourceLocation c2 = id("test:c2");
        ResourceLocation d = id("test:d");
        ResourceLocation s1 = id("test:s1");
        ResourceLocation s2 = id("test:s2");
        ResourceLocation s3 = id("test:s3");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(a1, 1, ResearchRequirements.EMPTY),
                spec(a2, 1, ResearchRequirements.EMPTY),
                spec(c1, 1, ResearchRequirements.EMPTY),
                spec(c2, 1, ResearchRequirements.EMPTY),
                spec(s1, 1, ResearchRequirements.EMPTY),
                spec(s2, 1, ResearchRequirements.EMPTY),
                spec(s3, 1, ResearchRequirements.EMPTY),
                spec(A, 1, and(a1, a2)),
                spec(B, 1, and(s1, s2, s3)),
                spec(C, 1, and(c1, c2)),
                spec(d, 1, and(s1, s2, s3)),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(6, plan.unlockCount());
        assertEquals(Set.of(s1, s2, s3, B, d, TARGET), Set.copyOf(ids(plan)));
    }

    @Test
    void equallyShortRoutesPreferOneAffordableWithCurrentInventory() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(B, 2, ResearchRequirements.EMPTY, false, iron(1)),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false,
                List.of(new ItemStack(Items.IRON_INGOT)));

        assertTrue(result.successful());
        assertEquals(List.of(B, TARGET), ids(result.plan().orElseThrow()));
    }

    @Test
    void learnedAndInvalidAlternativesAreHandledWithoutChargingThem() {
        PlayerRecipeData learnedData = data(100);
        learnedData.addBlueprint(A.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> learnedPolicies = policies(learnedData,
                spec(A, 9, ResearchRequirements.EMPTY),
                spec(B, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        assertEquals(List.of(TARGET), ids(plan(learnedData, learnedPolicies, TARGET)));

        PlayerRecipeData rerouteData = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> reroutePolicies = policies(rerouteData,
                spec(A, 1, ResearchRequirements.EMPTY, true),
                spec(B, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        assertEquals(List.of(B, TARGET), ids(plan(rerouteData, reroutePolicies, TARGET)));
    }

    @Test
    void learnedMiddleNodeCannotBridgePastMissingAncestry() {
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        data.addBlueprint(C.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 9, ResearchRequirements.EMPTY),
                spec(B, 4, legacy(A)),
                spec(C, 30, legacy(B)),
                spec(TARGET, 7, legacy(C)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(B, TARGET), ids(plan));
        assertEquals(11, plan.pointCost());
    }

    @Test
    void selectingLearnedDisconnectedNodeRepairsOnlyItsMissingRoute() {
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        data.addBlueprint(C.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 9, ResearchRequirements.EMPTY),
                spec(B, 4, legacy(A)),
                spec(C, 30, legacy(B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, C);

        assertEquals(List.of(B), ids(plan));
        assertEquals(4, plan.pointCost());
    }

    @Test
    void pathCommitDoesNotChargeOrMutateLearnedSupportNodes() {
        PlayerRecipeData data = data(30);
        data.addBlueprint(A.toString());
        data.addBlueprint(C.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 9, ResearchRequirements.EMPTY),
                spec(B, 4, legacy(A)),
                spec(C, 30, legacy(B)),
                spec(TARGET, 7, legacy(C)));

        BlueprintResearchService.Result result = BlueprintResearchService.researchPath(
                TARGET,
                data,
                policies::get,
                ResearchPathUnlockPlannerTest::learningTarget,
                new TestInput(List.of()),
                false,
                true,
                ignored -> false);

        assertTrue(result.successful());
        assertEquals(11, result.spentPoints());
        assertEquals(List.of(B, TARGET), result.transitions().stream()
                .map(BlueprintResearchService.LearningTransition::blueprintId).toList());
        assertTrue(data.hasBlueprint(C.toString()));
    }

    @Test
    void atomicPathPurchaseChargesEveryDistinctNodeAndLearnsTheWholeClosure() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        TestInput input = new TestInput(List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)));

        BlueprintResearchService.Result result = BlueprintResearchService.researchPath(
                TARGET,
                data,
                policies::get,
                ResearchPathUnlockPlannerTest::learningTarget,
                input,
                false,
                true,
                ignored -> false);

        assertTrue(result.successful());
        assertEquals(5, result.spentPoints());
        assertEquals(5, result.balanceAfterCost());
        assertEquals(List.of(A, TARGET), result.transitions().stream()
                .map(BlueprintResearchService.LearningTransition::blueprintId).toList());
        assertTrue(data.hasBlueprint(A.toString()));
        assertTrue(data.hasBlueprint(TARGET.toString()));
        assertTrue(input.stacks.stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void laterLearningFailureRollsBackKnowledgePointsRecipesAndInventory() {
        RejectSecondCommitData data = new RejectSecondCommitData();
        data.setResearchPoints(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        TestInput input = new TestInput(List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)));

        BlueprintResearchService.Result result = BlueprintResearchService.researchPath(
                TARGET,
                data,
                policies::get,
                ResearchPathUnlockPlannerTest::learningTarget,
                input,
                false,
                true,
                ignored -> false);

        assertEquals(BlueprintResearchService.Status.PROGRESSION_CAPACITY_EXHAUSTED,
                result.status());
        assertEquals(10, data.getResearchPoints());
        assertFalse(data.hasBlueprint(A.toString()));
        assertFalse(data.hasBlueprint(TARGET.toString()));
        assertTrue(data.getLearnedRecipes().isEmpty());
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(1, input.stacks.get(1).getCount());
    }

    @Test
    void aggregateEconomicFailuresDoNotMutateAnyNode() {
        PlayerRecipeData data = data(4);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        TestInput input = new TestInput(List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)));

        BlueprintResearchService.Result result = BlueprintResearchService.researchPath(
                TARGET,
                data,
                policies::get,
                ResearchPathUnlockPlannerTest::learningTarget,
                input,
                false,
                true,
                ignored -> false);

        assertEquals(BlueprintResearchService.Status.POINTS_REQUIRED, result.status());
        assertEquals(4, data.getResearchPoints());
        assertFalse(data.hasBlueprint(A.toString()));
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(1, input.stacks.get(1).getCount());
    }

    private static ResearchPathUnlockPlanner.Plan plan(
            PlayerRecipeData data,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation target) {
        return ResearchPathUnlockPlanner.plan(
                target, data, policies::get, ignored -> false, false)
                .plan().orElseThrow();
    }

    private static List<ResourceLocation> ids(ResearchPathUnlockPlanner.Plan plan) {
        return plan.nodes().stream()
                .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId)
                .toList();
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> policies(
            PlayerRecipeData data,
            PolicySpec... specs) {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = new LinkedHashMap<>();
        for (PolicySpec spec : specs) {
            boolean learned = data.hasBlueprint(spec.id().toString());
            policies.put(spec.id(), new BlueprintResearchPolicy(
                    spec.id(),
                    PROFILE,
                    true,
                    spec.blocked(),
                    true,
                    learned,
                    data.hasDiscoveredBlueprint(spec.id().toString()),
                    data.getResearchPoints(),
                    100,
                    spec.requirements().satisfiedBy(
                            id -> data.hasBlueprint(id.toString())),
                    true,
                    true,
                    JournalVisibility.FULL,
                    true,
                    true,
                    false,
                    1,
                    spec.cost(),
                    false,
                    spec.requirements(),
                    spec.requirements().conservativeAlternatives(),
                    true,
                    false,
                    Optional.empty(),
                    MatchSpecificity.NONE));
        }
        return Map.copyOf(policies);
    }

    private static PolicySpec spec(
            ResourceLocation id,
            int points,
            ResearchRequirements requirements) {
        return spec(id, points, requirements, false);
    }

    private static PolicySpec spec(
            ResourceLocation id,
            int points,
            ResearchRequirements requirements,
            boolean blocked,
            BlueprintResearchIngredient... ingredients) {
        return new PolicySpec(
                id,
                new BlueprintResearchCost(points, List.of(ingredients)),
                requirements,
                blocked);
    }

    private static PolicySpec spec(
            ResourceLocation id,
            int points,
            ResearchRequirements requirements,
            boolean blocked) {
        return spec(id, points, requirements, blocked, new BlueprintResearchIngredient[0]);
    }

    private static ResearchRequirements legacy(ResourceLocation prerequisite) {
        return ResearchRequirements.fromLegacy(List.of(prerequisite));
    }

    private static ResearchRequirements anyOf(ResourceLocation... alternatives) {
        return new ResearchRequirements(List.of(
                new ResearchPrerequisiteGroup(List.of(alternatives))));
    }

    private static ResearchRequirements and(ResourceLocation... prerequisites) {
        return ResearchRequirements.fromLegacy(List.of(prerequisites));
    }

    private static ResearchRequirements grouped(ResearchRequirements... requirements) {
        return new ResearchRequirements(java.util.Arrays.stream(requirements)
                .flatMap(value -> value.allOf().stream())
                .toList());
    }

    private static BlueprintResearchIngredient paper(int count) {
        return ingredient(Items.PAPER, count);
    }

    private static BlueprintResearchIngredient iron(int count) {
        return ingredient(Items.IRON_INGOT, count);
    }

    private static BlueprintResearchIngredient ingredient(
            net.minecraft.world.item.Item item,
            int count) {
        return new BlueprintResearchIngredient(
                List.of(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item)),
                Optional.empty(),
                count);
    }

    private static BlueprintLearningService.LearningTarget learningTarget(
            ResourceLocation blueprintId) {
        return new BlueprintLearningService.LearningTarget(
                blueprintId,
                id("test:recipe/" + blueprintId.getPath()));
    }

    private static PlayerRecipeData data(int points) {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(points);
        return data;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private record PolicySpec(
            ResourceLocation id,
            BlueprintResearchCost cost,
            ResearchRequirements requirements,
            boolean blocked) {
    }

    private static final class TestInput implements BlueprintResearchService.ResearchInput {
        private final List<ItemStack> stacks = new ArrayList<>();

        private TestInput(List<ItemStack> stacks) {
            stacks.forEach(stack -> this.stacks.add(stack.copy()));
        }

        @Override
        public List<ItemStack> stacks() {
            return stacks.stream().map(ItemStack::copy).toList();
        }

        @Override
        public boolean canAcceptOutput() {
            return true;
        }

        @Override
        public void consume(ResearchIngredientPlanner.Plan plan) {
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.get(slot).shrink(plan.decrement(slot));
            }
        }

        @Override
        public void restore(List<ItemStack> snapshot) {
            stacks.clear();
            snapshot.forEach(stack -> stacks.add(stack.copy()));
        }

        @Override
        public ItemStack createOutput(ResourceLocation blueprintId) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean deliver(ItemStack output) {
            return false;
        }
    }

    private static final class RejectSecondCommitData extends PlayerRecipeData {
        private int commits;

        @Override
        public synchronized BlueprintLearningMutation.Result applyBlueprintLearning(
                BlueprintLearningMutation.Request request) {
            if (request.operation() == BlueprintLearningMutation.Operation.COMMIT
                    && ++commits == 2) {
                return BlueprintLearningMutation.Result.unchanged(
                        BlueprintLearningMutation.Status.CAPACITY_REACHED,
                        request.operation());
            }
            return super.applyBlueprintLearning(request);
        }
    }
}
