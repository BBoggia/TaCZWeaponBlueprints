package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService.ResearchInput;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService.Status;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteOverlay;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
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

class BlueprintResearchServiceTest {
    private static final ResourceLocation BLUEPRINT = id("test:rifle");
    private static final ResourceLocation RECIPE = id("test:gun/rifle");
    private static final ResourceLocation PROFILE = id("test:profile");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plannerReroutesOverlappingAlternativesInsteadOfGreedilyRejectingThem() {
        BlueprintResearchCost cost = new BlueprintResearchCost(0, List.of(
                ingredient(1, "minecraft:paper", "minecraft:iron_ingot"),
                ingredient(1, "minecraft:paper")));

        ResearchIngredientPlanner.Plan plan = ResearchIngredientPlanner.plan(
                List.of(new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)), cost)
                .orElseThrow();

        assertEquals(1, plan.decrement(0));
        assertEquals(1, plan.decrement(1));
        assertEquals(2, plan.totalConsumed());
    }

    @Test
    void partialAllocationReportsOverlapSafeBenchAndInventoryContributions() {
        BlueprintResearchCost cost = new BlueprintResearchCost(0, List.of(
                ingredient(1, "minecraft:paper", "minecraft:iron_ingot"),
                ingredient(1, "minecraft:paper")));

        ResearchIngredientPlanner.Allocation allocation = ResearchIngredientPlanner.allocation(
                List.of(new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)), cost)
                .orElseThrow();

        assertTrue(allocation.complete());
        assertEquals(2, allocation.totalRequired());
        assertEquals(2, allocation.totalAllocated());
        assertEquals(0, allocation.allocatedForIngredientFromSlots(0, 0, 1));
        assertEquals(1, allocation.allocatedForIngredientFromSlots(0, 1, 2));
        assertEquals(1, allocation.allocatedForIngredientFromSlots(1, 0, 1));
        assertEquals(0, allocation.allocatedForIngredientFromSlots(1, 1, 2));
    }

    @Test
    void successfulResearchSpendsTheCompleteCostConsumesInputsAndProducesOneBlueprint() {
        PlayerRecipeData data = data(10, true);
        TestInput input = input(new ItemStack(Items.PAPER, 4), new ItemStack(Items.IRON_INGOT, 3));
        BlueprintResearchCost cost = new BlueprintResearchCost(7, List.of(
                ingredient(3, "minecraft:paper"),
                ingredient(2, "minecraft:iron_ingot")));

        BlueprintResearchService.Result result = research(data, input, policy(data, cost), false);

        assertTrue(result.successful());
        assertEquals(7, result.spentPoints());
        assertEquals(3, result.balanceAfterCost());
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(1, input.stacks.get(1).getCount());
        assertEquals(List.of(BLUEPRINT), input.outputs);
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
    }

    @Test
    void directResearchLearnsCanonicalKnowledgeWithoutCreatingOrCheckingOutput() {
        PlayerRecipeData data = data(10, true);
        TestInput input = input(
                new ItemStack(Items.PAPER, 4),
                new ItemStack(Items.IRON_INGOT, 3));
        input.acceptOutput = false;
        BlueprintResearchCost cost = new BlueprintResearchCost(7, List.of(
                ingredient(3, "minecraft:paper"),
                ingredient(2, "minecraft:iron_ingot")));

        BlueprintResearchService.Result result = directResearch(
                data, input, policy(data, cost), false);

        assertTrue(result.successful());
        assertEquals(TreeResearchResultMode.DIRECT_LEARN, result.resultMode());
        assertEquals(7, result.spentPoints());
        assertEquals(3, result.balanceAfterCost());
        assertTrue(result.learnedChanged());
        assertFalse(result.discoveredChanged(),
                "an already discovered blueprint must not replay discovery");
        assertTrue(result.legacyRecipeChanged());
        assertTrue(data.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasDiscoveredBlueprint(BLUEPRINT.toString()));
        assertTrue(data.hasRecipe(RECIPE.toString()));
        assertEquals(1, input.stacks.get(0).getCount());
        assertEquals(1, input.stacks.get(1).getCount());
        assertTrue(input.outputs.isEmpty());
        assertEquals(0, input.createdOutputs);
    }

    @Test
    void directResearchCapacityPreflightRejectsBeforeAnyEconomicMutation() {
        RejectingLearningData data = new RejectingLearningData(true, false);
        data.setResearchPoints(5);
        data.discoverBlueprint(BLUEPRINT.toString());
        TestInput input = input(new ItemStack(Items.PAPER, 2));

        BlueprintResearchService.Result result = directResearch(
                data,
                input,
                policy(data, new BlueprintResearchCost(
                        3, List.of(ingredient(1, "minecraft:paper")))),
                false);

        assertEquals(Status.PROGRESSION_CAPACITY_EXHAUSTED, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertTrue(input.outputs.isEmpty());
    }

    @Test
    void directResearchRestoresPointsAndInventoryWhenPreparedCommitRejects() {
        RejectingLearningData data = new RejectingLearningData(false, true);
        data.setResearchPoints(5);
        data.discoverBlueprint(BLUEPRINT.toString());
        TestInput input = input(new ItemStack(Items.PAPER, 2));

        BlueprintResearchService.Result result = directResearch(
                data,
                input,
                policy(data, new BlueprintResearchCost(
                        3, List.of(ingredient(1, "minecraft:paper")))),
                false);

        assertEquals(Status.PROGRESSION_CAPACITY_EXHAUSTED, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertFalse(data.hasBlueprint(BLUEPRINT.toString()));
        assertFalse(data.hasRecipe(RECIPE.toString()));
        assertTrue(input.outputs.isEmpty());
    }

    @Test
    void successfulResearchConsumesAnExactPlanAcrossAPlayerSizedInventory() {
        PlayerRecipeData data = data(10, true);
        List<ItemStack> inventory = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            inventory.add(ItemStack.EMPTY);
        }
        inventory.set(2, new ItemStack(Items.PAPER, 4));
        inventory.set(31, new ItemStack(Items.IRON_INGOT, 3));
        TestInput input = new TestInput(inventory);
        BlueprintResearchCost cost = new BlueprintResearchCost(2, List.of(
                ingredient(3, "minecraft:paper"),
                ingredient(2, "minecraft:iron_ingot")));

        BlueprintResearchService.Result result = research(data, input, policy(data, cost), false);

        assertTrue(result.successful());
        assertEquals(1, input.stacks.get(2).getCount());
        assertEquals(1, input.stacks.get(31).getCount());
        assertTrue(input.stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .allMatch(stack -> stack.getCount() == 1));
    }

    @Test
    void creativeBypassWaivesBothPointAndIngredientCostsOnlyWhenPolicyAllowsIt() {
        PlayerRecipeData data = data(0, true);
        TestInput input = input(ItemStack.EMPTY);
        BlueprintResearchPolicy bypassPolicy = policy(
                data,
                new BlueprintResearchCost(8, List.of(ingredient(4, "minecraft:paper"))),
                true, false, true, true, true, true);

        BlueprintResearchService.Result result = research(data, input, bypassPolicy, true);

        assertTrue(result.successful());
        assertTrue(result.costBypassed());
        assertEquals(0, result.spentPoints());
        assertEquals(0, data.getResearchPoints());
        assertEquals(List.of(BLUEPRINT), input.outputs);
    }

    @Test
    void everyEconomicFailurePreservesPointsInputsAndOutput() {
        assertAtomicFailure(Status.POINTS_REQUIRED, data -> policy(
                data, new BlueprintResearchCost(6, List.of()), true, false, true, true, true, false));
        assertAtomicFailure(Status.INGREDIENTS_REQUIRED, data -> policy(
                data,
                new BlueprintResearchCost(1, List.of(ingredient(2, "minecraft:iron_ingot"))),
                true, false, true, true, true, false));

        PlayerRecipeData data = data(5, true);
        TestInput full = input(new ItemStack(Items.PAPER, 2));
        full.acceptOutput = false;
        BlueprintResearchService.Result result = research(
                data,
                full,
                policy(data, new BlueprintResearchCost(1, List.of(ingredient(1, "minecraft:paper"))), false),
                false);
        assertEquals(Status.OUTPUT_FULL, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, full.stacks.get(0).getCount());
        assertTrue(full.outputs.isEmpty());
    }

    @Test
    void unexpectedCommitFailuresRestorePointsAndEveryInputSlot() {
        PlayerRecipeData data = data(5, true);
        BlueprintResearchCost cost = new BlueprintResearchCost(
                3, List.of(ingredient(1, "minecraft:paper")));

        TestInput failedConsume = input(new ItemStack(Items.PAPER, 2));
        failedConsume.failConsumption = true;
        BlueprintResearchService.Result consumeResult = research(
                data, failedConsume, policy(data, cost), false);
        assertEquals(Status.TRANSACTION_FAILED, consumeResult.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, failedConsume.stacks.get(0).getCount());
        assertTrue(failedConsume.outputs.isEmpty());

        TestInput failedDelivery = input(new ItemStack(Items.PAPER, 2));
        failedDelivery.deliverOutput = false;
        BlueprintResearchService.Result deliveryResult = research(
                data, failedDelivery, policy(data, cost), false);
        assertEquals(Status.TRANSACTION_FAILED, deliveryResult.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, failedDelivery.stacks.get(0).getCount());
        assertTrue(failedDelivery.outputs.isEmpty());
    }

    @Test
    void restorationFailureIsReportedWithoutClaimingMaterialsWereRestored() {
        PlayerRecipeData data = data(5, true);
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        input.failConsumption = true;
        input.failRestore = true;

        BlueprintResearchService.Result result = research(
                data,
                input,
                policy(data, new BlueprintResearchCost(
                        3, List.of(ingredient(1, "minecraft:paper")))),
                false);

        assertEquals(Status.ROLLBACK_FAILED, result.status());
        assertEquals(5, data.getResearchPoints(), "RP restoration remains independent");
        assertEquals(1, input.stacks.get(0).getCount(),
                "the test double proves the failed inventory restoration is observable");
    }

    @Test
    void pointRestorationExceptionIsContainedAndReported() {
        ThrowingPointRestoreData data = new ThrowingPointRestoreData();
        data.setResearchPoints(5);
        data.discoverBlueprint(BLUEPRINT.toString());
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        input.failConsumption = true;

        BlueprintResearchService.Result result = research(
                data,
                input,
                policy(data, new BlueprintResearchCost(
                        3, List.of(ingredient(1, "minecraft:paper")))),
                false);

        assertEquals(Status.ROLLBACK_FAILED, result.status());
        assertEquals(2, data.getResearchPoints(),
                "a failed RP restore must not be represented as success");
        assertEquals(2, input.stacks.get(0).getCount(),
                "inventory restoration still completes independently");
    }

    @Test
    void outputDeliveryRequiresVerifiedInventoryInsertionOrDrop() {
        ItemStack inserted = new ItemStack(Items.PAPER);
        assertTrue(BlueprintResearchService.deliverOutput(
                inserted,
                stack -> stack.shrink(1),
                stack -> {
                    throw new AssertionError("drop fallback must not run after insertion");
                }));

        ItemStack dropped = new ItemStack(Items.PAPER);
        assertTrue(BlueprintResearchService.deliverOutput(
                dropped, stack -> { }, stack -> true));

        ItemStack rejected = new ItemStack(Items.PAPER);
        assertFalse(BlueprintResearchService.deliverOutput(
                rejected, stack -> { }, stack -> false));
        assertFalse(BlueprintResearchService.deliverOutput(
                new ItemStack(Items.PAPER, 2), stack -> { }, stack -> true));
    }

    @Test
    void policyFailuresAreTypedAndAtomic() {
        assertPolicyFailure(Status.POLICY_UNAVAILABLE, data -> null);
        assertPolicyFailure(Status.CONTENT_UNAVAILABLE,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), false, false, true, true, true, false));
        assertPolicyFailure(Status.BLOCKED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, true, true, true, true, false));
        assertPolicyFailure(Status.RESEARCH_DISABLED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, false, true, true, false));
        assertPolicyFailure(Status.DISCOVERY_REQUIRED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, true, false, true, false));
        assertPolicyFailure(Status.PREREQUISITES_REQUIRED,
                data -> policy(data, new BlueprintResearchCost(0, List.of()), true, false, true, true, false, false));

        PlayerRecipeData learned = data(5, true);
        learned.addBlueprint(BLUEPRINT.toString());
        TestInput input = input(ItemStack.EMPTY);
        BlueprintResearchService.Result alreadyLearned = research(
                learned,
                input,
                policy(
                        learned,
                        new BlueprintResearchCost(0, List.of()),
                        true, false, true, true, false, false),
                false);
        assertEquals(Status.ALREADY_LEARNED, alreadyLearned.status());
        assertTrue(input.outputs.isEmpty());
    }

    @Test
    void connectedAutomaticPrerequisiteIsEnforcedByTheAtomicResearchTransaction() {
        ResourceLocation required = id("test:auto_anchor");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(required)),
                Map.of());
        PlayerRecipeData data = data(5, true);
        BlueprintResearchPolicy base = policy(
                data, new BlueprintResearchCost(0, List.of()));
        BlueprintResearchPolicy hiddenAnchor = AutomaticWeaponPrerequisiteOverlay.apply(
                base,
                plan,
                data,
                ignored -> false,
                false);
        assertTrue(hiddenAnchor.prerequisites().isEmpty(),
                "an unselectable generated anchor must fail open");
        BlueprintResearchPolicy missingAnchor = AutomaticWeaponPrerequisiteOverlay.apply(
                base,
                plan,
                data,
                ignored -> false,
                true,
                ignored -> false);
        assertTrue(missingAnchor.prerequisites().isEmpty(),
                "an unknown generated anchor must fail open");

        BlueprintResearchPolicy authored = policy(
                data,
                new BlueprintResearchCost(0, List.of()),
                true, false, true, true, false, false);
        BlueprintResearchPolicy authoredResult = AutomaticWeaponPrerequisiteOverlay.apply(
                authored,
                plan,
                data,
                ignored -> false);
        assertEquals(List.of(id("test:required")), authoredResult.prerequisites(),
                "authored prerequisites must outrank generated prerequisites");

        BlueprintResearchPolicy locked = AutomaticWeaponPrerequisiteOverlay.apply(
                base,
                plan,
                data,
                ignored -> false);

        BlueprintResearchService.Result rejected = research(
                data, input(ItemStack.EMPTY), locked, false);
        assertEquals(Status.PREREQUISITES_REQUIRED, rejected.status());
        assertEquals(5, data.getResearchPoints());

        data.addBlueprint(required.toString());
        BlueprintResearchPolicy unlocked = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(data, new BlueprintResearchCost(0, List.of())),
                plan,
                data,
                ignored -> false);
        BlueprintResearchService.Result accepted = research(
                data, input(ItemStack.EMPTY), unlocked, false);
        assertTrue(accepted.successful());
    }

    @Test
    void automaticWeaponAuthorityClearsAuthoredRequirementsEvenWithoutALivePlan() {
        PlayerRecipeData data = data(5, true);
        BlueprintResearchPolicy authored = policy(
                data,
                new BlueprintResearchCost(0, List.of()),
                true, false, true, true, false, false);

        BlueprintResearchPolicy withoutPlan = AutomaticWeaponPrerequisiteOverlay.apply(
                authored,
                null,
                data,
                ignored -> false,
                true,
                ignored -> true,
                ignored -> false,
                Map.of(),
                true);

        assertTrue(withoutPlan.requirements().allOf().isEmpty(),
                "automatic weapon authority must not leak authored prerequisites while a plan is unavailable");

        AutomaticWeaponPrerequisitePlan otherProfile = new AutomaticWeaponPrerequisitePlan(
                id("test:other_profile"),
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(id("test:auto_anchor"))),
                Map.of());
        BlueprintResearchPolicy mismatchedPlan = AutomaticWeaponPrerequisiteOverlay.apply(
                authored,
                otherProfile,
                data,
                ignored -> false,
                true,
                ignored -> true,
                ignored -> false,
                Map.of(),
                true);

        assertTrue(mismatchedPlan.requirements().allOf().isEmpty(),
                "a stale plan from another profile must not restore authored weapon prerequisites");
    }

    @Test
    void groupedAutomaticRouteAcceptsEitherAlternativeButNotNeither() {
        ResourceLocation routeA = id("test:route_a");
        ResourceLocation routeB = id("test:route_b");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                PrerequisiteStrategy.GROUPED_ROUTES_V1,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(routeA, routeB)),
                Map.of(BLUEPRINT, new ResearchRequirements(List.of(
                        new ResearchPrerequisiteGroup(List.of(routeA, routeB))))),
                Map.of(),
                Map.of(),
                Map.of());

        PlayerRecipeData lockedData = data(5, true);
        BlueprintResearchPolicy locked = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(lockedData, new BlueprintResearchCost(0, List.of())),
                plan,
                lockedData,
                ignored -> false);
        assertEquals(
                Status.PREREQUISITES_REQUIRED,
                research(lockedData, input(ItemStack.EMPTY), locked, false).status());

        for (ResourceLocation learnedRoute : List.of(routeA, routeB)) {
            PlayerRecipeData unlockedData = data(5, true);
            unlockedData.addBlueprint(learnedRoute.toString());
            BlueprintResearchPolicy unlocked = AutomaticWeaponPrerequisiteOverlay.apply(
                    policy(unlockedData, new BlueprintResearchCost(0, List.of())),
                    plan,
                    unlockedData,
                    ignored -> false);
            assertEquals(1, unlocked.requirements().allOf().size());
            assertTrue(research(
                    unlockedData,
                    input(ItemStack.EMPTY),
                    unlocked,
                    false).successful());
        }
    }

    @Test
    void directEntryPointOwnershipSuppressesAutomaticRequirements() {
        ResourceLocation route = id("test:route");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(route)),
                Map.of());
        PlayerRecipeData data = data(5, true);
        BlueprintResearchPolicy directEntry = policy(
                data,
                new BlueprintResearchCost(0, List.of()),
                true,
                false,
                true,
                true,
                true,
                false,
                false);

        BlueprintResearchPolicy resolved = AutomaticWeaponPrerequisiteOverlay.apply(
                directEntry,
                plan,
                data,
                ignored -> false);

        assertFalse(resolved.automaticPrerequisitesAllowed());
        assertTrue(resolved.requirements().allOf().isEmpty());
        assertTrue(research(
                data,
                input(ItemStack.EMPTY),
                resolved,
                false).successful());
    }

    @Test
    void groupedOverlayFiltersAlternativesIndependentlyAndFailsOpenSafely() {
        ResourceLocation routeA = id("test:route_a");
        ResourceLocation routeB = id("test:route_b");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                PrerequisiteStrategy.GROUPED_ROUTES_V1,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(routeA, routeB)),
                Map.of(BLUEPRINT, new ResearchRequirements(List.of(
                        new ResearchPrerequisiteGroup(List.of(routeA, routeB))))),
                Map.of(),
                Map.of(),
                Map.of());

        PlayerRecipeData filteredData = data(5, true);
        BlueprintResearchPolicy filtered = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(filteredData, new BlueprintResearchCost(0, List.of())),
                plan,
                filteredData,
                routeA.toString()::equals,
                true,
                id -> id.equals(routeA) || id.equals(routeB),
                ignored -> false);
        assertEquals(
                List.of(routeB),
                filtered.requirements().allOf().get(0).anyOf());
        filteredData.addBlueprint(routeB.toString());
        BlueprintResearchPolicy filteredUnlocked = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(filteredData, new BlueprintResearchCost(0, List.of())),
                plan,
                filteredData,
                routeA.toString()::equals,
                true,
                id -> id.equals(routeA) || id.equals(routeB),
                ignored -> false);
        assertTrue(research(
                filteredData,
                input(ItemStack.EMPTY),
                filteredUnlocked,
                false).successful());

        PlayerRecipeData exemptData = data(5, true);
        BlueprintResearchPolicy exemptRoute = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(exemptData, new BlueprintResearchCost(0, List.of())),
                plan,
                exemptData,
                ignored -> false,
                true,
                id -> id.equals(routeA) || id.equals(routeB),
                routeA::equals);
        assertTrue(exemptRoute.requirements().allOf().isEmpty(),
                "an accessible-without-learning alternative satisfies the generated group");

        PlayerRecipeData unsafeData = data(5, true);
        BlueprintResearchPolicy allUnsafe = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(unsafeData, new BlueprintResearchCost(0, List.of())),
                plan,
                unsafeData,
                ignored -> false,
                true,
                ignored -> false,
                ignored -> false);
        assertTrue(allUnsafe.requirements().allOf().isEmpty(),
                "a generated group with no safe alternative must fail open");
    }

    @Test
    void automaticOverlayRebasesAnUnavailableFoundationToTheLiveEntryPoint() {
        ResourceLocation preferred = id("test:preferred_root");
        ResourceLocation fallback = id("test:fallback_root");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                id("test:tree"),
                AutomaticPlacementMode.CONNECTED,
                5L,
                7L,
                1,
                Map.of(BLUEPRINT, List.of(preferred)),
                Map.of());
        PlayerRecipeData data = data(5, true);

        BlueprintResearchPolicy rebased = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(data, new BlueprintResearchCost(0, List.of())),
                plan,
                data,
                preferred.toString()::equals,
                true,
                fallback::equals,
                ignored -> false,
                Map.of(preferred, fallback));

        assertEquals(List.of(fallback), rebased.prerequisites());
        assertEquals(
                Status.PREREQUISITES_REQUIRED,
                research(data, input(ItemStack.EMPTY), rebased, false).status());
        data.addBlueprint(fallback.toString());
        BlueprintResearchPolicy unlocked = AutomaticWeaponPrerequisiteOverlay.apply(
                policy(data, new BlueprintResearchCost(0, List.of())),
                plan,
                data,
                preferred.toString()::equals,
                true,
                fallback::equals,
                ignored -> false,
                Map.of(preferred, fallback));
        assertTrue(research(data, input(ItemStack.EMPTY), unlocked, false).successful());
    }

    private static void assertAtomicFailure(
            Status expected,
            java.util.function.Function<PlayerRecipeData, BlueprintResearchPolicy> policyFactory) {
        PlayerRecipeData data = data(5, true);
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        BlueprintResearchService.Result result = research(data, input, policyFactory.apply(data), false);
        assertEquals(expected, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertTrue(input.outputs.isEmpty());
    }

    private static void assertPolicyFailure(
            Status expected,
            java.util.function.Function<PlayerRecipeData, BlueprintResearchPolicy> policyFactory) {
        PlayerRecipeData data = data(5, false);
        TestInput input = input(new ItemStack(Items.PAPER, 2));
        BlueprintResearchService.Result result = research(data, input, policyFactory.apply(data), false);
        assertEquals(expected, result.status());
        assertEquals(5, data.getResearchPoints());
        assertEquals(2, input.stacks.get(0).getCount());
        assertTrue(input.outputs.isEmpty());
    }

    private static BlueprintResearchService.Result research(
            PlayerRecipeData data,
            ResearchInput input,
            BlueprintResearchPolicy policy,
            boolean creative) {
        return BlueprintResearchService.research(BLUEPRINT, data, ignored -> policy, input, creative);
    }

    private static BlueprintResearchService.Result directResearch(
            PlayerRecipeData data,
            ResearchInput input,
            BlueprintResearchPolicy policy,
            boolean creative) {
        return BlueprintResearchService.research(
                BLUEPRINT,
                data,
                ignored -> policy,
                ignored -> new BlueprintLearningService.LearningTarget(
                        BLUEPRINT, RECIPE),
                input,
                creative,
                true,
                TreeResearchResultMode.DIRECT_LEARN);
    }

    private static PlayerRecipeData data(int points, boolean discovered) {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(points);
        if (discovered) {
            data.discoverBlueprint(BLUEPRINT.toString());
        }
        return data;
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost) {
        return policy(data, cost, true, false, true, true, true, false);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost,
            boolean creativeBypass) {
        return policy(data, cost, true, false, true, true, true, creativeBypass);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost,
            boolean available,
            boolean blocked,
            boolean researchEnabled,
            boolean discovered,
            boolean prerequisites,
            boolean creativeBypass) {
        return policy(
                data,
                cost,
                available,
                blocked,
                researchEnabled,
                discovered,
                prerequisites,
                creativeBypass,
                true);
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            BlueprintResearchCost cost,
            boolean available,
            boolean blocked,
            boolean researchEnabled,
            boolean discovered,
            boolean prerequisites,
            boolean creativeBypass,
            boolean automaticPrerequisitesAllowed) {
        ResearchRequirements requirements = prerequisites
                ? ResearchRequirements.EMPTY
                : ResearchRequirements.fromLegacy(List.of(id("test:required")));
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                available,
                blocked,
                true,
                data.hasBlueprint(BLUEPRINT.toString()),
                discovered,
                data.getResearchPoints(),
                100,
                prerequisites,
                true,
                true,
                JournalVisibility.FULL,
                researchEnabled,
                true,
                false,
                1,
                cost,
                !discovered,
                requirements,
                requirements.conservativeAlternatives(),
                automaticPrerequisitesAllowed,
                creativeBypass,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static BlueprintResearchIngredient ingredient(int count, String... items) {
        return new BlueprintResearchIngredient(
                java.util.Arrays.stream(items).map(BlueprintResearchServiceTest::id).toList(),
                Optional.empty(),
                count);
    }

    private static TestInput input(ItemStack... stacks) {
        List<ItemStack> slots = new ArrayList<>();
        for (ItemStack stack : stacks) {
            slots.add(stack.copy());
        }
        return new TestInput(slots);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static final class TestInput implements ResearchInput {
        private final List<ItemStack> stacks;
        private final List<ResourceLocation> outputs = new ArrayList<>();
        private boolean acceptOutput = true;
        private boolean deliverOutput = true;
        private boolean failConsumption;
        private boolean failRestore;
        private int createdOutputs;

        private TestInput(List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        @Override
        public List<ItemStack> stacks() {
            return stacks.stream().map(ItemStack::copy).toList();
        }

        @Override
        public boolean canAcceptOutput() {
            return acceptOutput;
        }

        @Override
        public void consume(ResearchIngredientPlanner.Plan plan) {
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.get(slot).shrink(plan.decrement(slot));
                if (failConsumption) {
                    throw new IllegalStateException("simulated consumption failure");
                }
            }
        }

        @Override
        public void restore(List<ItemStack> snapshot) {
            if (failRestore) {
                throw new IllegalStateException("simulated restoration failure");
            }
            stacks.clear();
            snapshot.forEach(stack -> stacks.add(stack.copy()));
        }

        @Override
        public ItemStack createOutput(ResourceLocation blueprintId) {
            createdOutputs++;
            return new ItemStack(Items.PAPER);
        }

        @Override
        public boolean deliver(ItemStack output) {
            if (deliverOutput) {
                outputs.add(BLUEPRINT);
            }
            return deliverOutput;
        }
    }

    private static final class RejectingLearningData extends PlayerRecipeData {
        private final boolean rejectPreflight;
        private final boolean rejectCommit;

        private RejectingLearningData(
                boolean rejectPreflight,
                boolean rejectCommit) {
            this.rejectPreflight = rejectPreflight;
            this.rejectCommit = rejectCommit;
        }

        @Override
        public synchronized BlueprintLearningMutation.Result applyBlueprintLearning(
                BlueprintLearningMutation.Request request) {
            if ((rejectPreflight
                            && request.operation()
                                    == BlueprintLearningMutation.Operation.PREFLIGHT)
                    || (rejectCommit
                            && request.operation()
                                    == BlueprintLearningMutation.Operation.COMMIT)) {
                return BlueprintLearningMutation.Result.unchanged(
                        BlueprintLearningMutation.Status.CAPACITY_REACHED,
                        request.operation());
            }
            return super.applyBlueprintLearning(request);
        }
    }

    private static final class ThrowingPointRestoreData extends PlayerRecipeData {
        private boolean restoring;

        @Override
        public boolean spendResearchPoints(int amount) {
            boolean spent = super.spendResearchPoints(amount);
            restoring |= spent && amount > 0;
            return spent;
        }

        @Override
        public boolean setResearchPoints(int points) {
            if (restoring) {
                throw new IllegalStateException("simulated RP restoration failure");
            }
            return super.setResearchPoints(points);
        }
    }
}
