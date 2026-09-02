package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

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
        assertEquals(
                ResearchPathUnlockPlanner.RouteSelectionPolicy.STABLE_MINIMUM_UNLOCKS,
                ResearchPathUnlockPlanner.routeSelectionPolicy());
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
    void selectedSolutionCarriesTheCanonicalRequirementEdgeForEachChosenGroup() {
        ResourceLocation root = id("test:proof_root");
        ResourceLocation left = id("test:proof_left");
        ResourceLocation right = id("test:proof_right");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(root, 1, ResearchRequirements.EMPTY),
                spec(left, 1, legacy(root)),
                spec(right, 20, legacy(root)),
                spec(TARGET, 1, anyOf(left, right)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(
                List.of(
                        new ResearchPathUnlockPlanner.SelectedRequirement(left, 0, root),
                        new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 0, left)),
                plan.solution().selectedRequirements());
    }

    @Test
    void sharedAlternativeIsReportedOnceForEachDependentRequirementGroup() {
        ResourceLocation shared = id("test:proof_shared");
        ResourceLocation expensive = id("test:proof_expensive");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(shared, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(shared)),
                spec(B, 1, legacy(shared)),
                spec(expensive, 20, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(A, B, expensive))));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(shared, A, TARGET), ids(plan));
        assertEquals(
                List.of(
                        new ResearchPathUnlockPlanner.SelectedRequirement(A, 0, shared),
                        new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 0, A),
                        new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 1, A)),
                plan.solution().selectedRequirements());
    }

    @Test
    void connectedLearnedSupportRetainsItsExactEdgeWithoutBeingRepurchased() {
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, legacy(A)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(TARGET), ids(plan));
        assertEquals(Set.of(A, TARGET), Set.copyOf(plan.solution().supportIds()));
        assertEquals(
                List.of(new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 0, A)),
                plan.solution().selectedRequirements());
    }

    @Test
    void nestedConnectedLearnedSupportRetainsItsCompleteAncestryProof() {
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        data.addBlueprint(B.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(B, 1, legacy(A)),
                spec(TARGET, 1, legacy(B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(TARGET), ids(plan));
        assertEquals(List.of(A, B, TARGET), plan.solution().supportIds());
        assertEquals(
                List.of(
                        new ResearchPathUnlockPlanner.SelectedRequirement(B, 0, A),
                        new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 0, B)),
                plan.solution().selectedRequirements());
    }

    @Test
    void orPathTieUsesTotalMaterialUnitsBeforeCanonicalId() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(2)),
                spec(B, 2, ResearchRequirements.EMPTY, false, iron(1)),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(B, TARGET), ids(plan));
        assertEquals(1, plan.ingredients().stream()
                .mapToInt(ResearchIngredientPlanner.Requirement::count)
                .sum());
    }

    @Test
    void orPathRepairsTheCheapestDisconnectedLearnedAlternative() {
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(A, 40, legacy(C)),
                spec(B, 5, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(C, TARGET), ids(plan));
        assertEquals(2, plan.pointCost());
    }

    @Test
    void orPathDagUsesOneIndexedPolicyResolutionPass() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 3, ResearchRequirements.EMPTY),
                spec(B, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        AtomicInteger policyLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(List.of(B, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
    }

    @Test
    void orPathSelectionUsesEffectiveCreativeBypassCosts() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> baseline = policies(data,
                spec(A, 10, ResearchRequirements.EMPTY),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                new LinkedHashMap<>(baseline);
        policies.put(A, policies.get(A).withRuntimePolicy(
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                true,
                100));

        assertEquals(List.of(B, TARGET), ids(ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false).plan().orElseThrow()));
        ResearchPathUnlockPlanner.Plan creative = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                true).plan().orElseThrow();
        assertEquals(List.of(A, TARGET), ids(creative));
        assertEquals(1, creative.pointCost());
    }

    @Test
    void compatibilityPlannerCanIgnoreACyclicAlternativeWithAValidRoute() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 1, legacy(B)),
                spec(B, 1, legacy(A)),
                spec(C, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, C)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(C, TARGET), ids(plan));
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
    void mandatoryDagUsesOneIndexedPolicyResolutionPass() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(A, 2, legacy(C)),
                spec(B, 3, legacy(C)),
                spec(TARGET, 4, and(A, B)));
        AtomicInteger policyLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(List.of(C, A, B, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
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
        assertEquals(6, plan.pointCost());
    }

    @Test
    void generalAndOrRetainsALocallyLongerRouteWhenItReducesTheGlobalUnion() {
        ResourceLocation root = id("test:root");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(root, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(root)),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), legacy(A))));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(root, A, TARGET), ids(plan));
        assertEquals(3, plan.pointCost());
    }

    @Test
    void generalAndOrSolverMatchesBruteForceAcrossGeneratedSmallDags() {
        ResourceLocation r0 = id("test:oracle_r0");
        ResourceLocation r1 = id("test:oracle_r1");
        ResourceLocation r2 = id("test:oracle_r2");
        ResourceLocation r3 = id("test:oracle_r3");
        ResourceLocation x = id("test:oracle_x");
        ResourceLocation y = id("test:oracle_y");
        ResourceLocation z = id("test:oracle_z");

        for (int seed = 0; seed < 64; seed++) {
            PlayerRecipeData data = data(100);
            Random random = new Random(seed);
            Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                    oracleSpec(r0, random, ResearchRequirements.EMPTY),
                    oracleSpec(r1, random, ResearchRequirements.EMPTY),
                    oracleSpec(r2, random, ResearchRequirements.EMPTY),
                    oracleSpec(r3, random, ResearchRequirements.EMPTY),
                    oracleSpec(x, random, anyOf(r0, r1)),
                    oracleSpec(y, random, anyOf(r0, r2)),
                    oracleSpec(z, random, anyOf(r1, r3)),
                    oracleSpec(
                            TARGET,
                            random,
                            grouped(anyOf(x, z), anyOf(y, z))));

            ResearchPathUnlockPlanner.Plan actual = plan(data, policies, TARGET);
            Set<ResourceLocation> expected = bruteForceBestPurchases(policies, TARGET);

            assertEquals(expected, Set.copyOf(ids(actual)), "seed " + seed);
            assertEquals(
                    expected.stream()
                            .map(policies::get)
                            .mapToInt(policy -> policy.researchCost().points())
                            .sum(),
                    actual.pointCost(),
                    "seed " + seed);
            assertEquals(
                    expected.stream()
                            .map(policies::get)
                            .flatMap(policy -> policy.researchCost().ingredients().stream())
                            .mapToInt(BlueprintResearchIngredient::count)
                            .sum(),
                    actual.ingredients().stream()
                            .mapToInt(ResearchIngredientPlanner.Requirement::count)
                            .sum(),
                    "seed " + seed);
        }
    }

    @Test
    void separableAndOrDagComposesEachIndependentBestRoute() {
        ResourceLocation aRoot = id("test:a_root");
        ResourceLocation d = id("test:d");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(aRoot, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(aRoot)),
                spec(B, 20, ResearchRequirements.EMPTY),
                spec(C, 8, ResearchRequirements.EMPTY),
                spec(d, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(B, d, TARGET), ids(plan));
        assertEquals(23, plan.pointCost());
    }

    @Test
    void separableAndOrDagUsesOneIndexedPolicyResolutionPass() {
        ResourceLocation d = id("test:d");
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(B, 2, ResearchRequirements.EMPTY),
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(d, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));
        AtomicInteger policyLookups = new AtomicInteger();
        AtomicInteger exemptionLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> {
                    exemptionLookups.incrementAndGet();
                    return false;
                },
                false);

        assertTrue(result.successful());
        assertEquals(List.of(A, C, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
        assertEquals(policies.size(), exemptionLookups.get());
    }

    @Test
    void separableAndOrDagRepairsDisconnectedLearnedSupportWithoutChargingIt() {
        ResourceLocation aRoot = id("test:a_root");
        ResourceLocation d = id("test:d");
        PlayerRecipeData data = data(100);
        data.addBlueprint(A.toString());
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(aRoot, 1, ResearchRequirements.EMPTY),
                spec(A, 40, legacy(aRoot)),
                spec(B, 5, ResearchRequirements.EMPTY),
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(d, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(aRoot, C, TARGET), ids(plan));
        assertEquals(3, plan.pointCost());
    }

    @Test
    void equallyShortRoutesRemainCanonicalRegardlessOfCurrentInventory() {
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
        assertEquals(List.of(A, TARGET), ids(result.plan().orElseThrow()));
    }

    @Test
    void shorterUnaffordableRouteIsNotReplacedByLongerAffordableRoute() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(C, 1, ResearchRequirements.EMPTY, false, paper(1)),
                spec(A, 1, legacy(C), false, paper(1)),
                spec(B, 50, ResearchRequirements.EMPTY, false, iron(2)),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false,
                List.of(new ItemStack(Items.PAPER, 2)));

        assertTrue(result.successful());
        assertEquals(List.of(B, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(51, result.plan().orElseThrow().pointCost());
    }

    @Test
    void ingredientTypeCountDoesNotOverrideCanonicalRouteIdentity() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1), iron(1)),
                spec(B, 2, ResearchRequirements.EMPTY, false, paper(2)),
                spec(TARGET, 1, anyOf(B, A)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(A, TARGET), ids(plan));
        assertEquals(2, plan.ingredients().size());
    }

    @Test
    void automaticAuthorityRejectsMissingPublicationAndFilteredGeneratedGroups() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> rootPolicy = policies(data,
                spec(TARGET, 1, ResearchRequirements.EMPTY));
        ResearchPathUnlockPlanner.Result unavailable = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                rootPolicy::get,
                ignored -> false,
                false,
                List.of(),
                ResearchPathAuthority.automaticUnavailable(Set.of(TARGET)));
        assertEquals(BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE,
                unavailable.status());
        BlueprintResearchService.Result blockedCommit = BlueprintResearchService.researchPath(
                TARGET,
                data,
                rootPolicy::get,
                ResearchPathUnlockPlannerTest::learningTarget,
                new TestInput(List.of()),
                false,
                true,
                ignored -> false,
                ResearchPathAuthority.automaticUnavailable(Set.of(TARGET)));
        assertEquals(BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE,
                blockedCommit.status());
        assertFalse(data.hasBlueprint(TARGET.toString()));
        assertEquals(100, data.getResearchPoints());

        ResearchPathAuthority filteredAuthority = ResearchPathAuthority.automaticReady(
                Set.of(TARGET),
                Map.of(TARGET, ResearchPathAuthority.NodeExpectation.requirements(
                        List.of(Set.of(A)))));
        ResearchPathUnlockPlanner.Result filtered = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                rootPolicy::get,
                ignored -> false,
                false,
                List.of(),
                filteredAuthority);
        assertEquals(BlueprintResearchService.Status.UNSATISFIABLE, filtered.status());
    }

    @Test
    void automaticAuthorityAcceptsOnlyExplicitFoundationsAndCompleteGroups() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, legacy(A)));
        ResearchPathAuthority authority = ResearchPathAuthority.automaticReady(
                Set.of(A, TARGET),
                Map.of(
                        A, ResearchPathAuthority.NodeExpectation.root(
                                ResearchPathAuthority.RootProvenance.GENERATED_FOUNDATION),
                        TARGET, ResearchPathAuthority.NodeExpectation.requirements(
                                List.of(Set.of(A)))));

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false,
                List.of(),
                authority);

        assertTrue(result.successful());
        assertEquals(List.of(A, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(
                Optional.of(ResearchPathAuthority.RootProvenance.GENERATED_FOUNDATION),
                authority.rootProvenance(A));
    }

    @Test
    void automaticAuthorityMatchesFilteredGroupsWithoutDependingOnSortOrder() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(legacy(C), legacy(B))));
        ResearchPathAuthority authority = ResearchPathAuthority.automaticReady(
                Set.of(TARGET),
                Map.of(TARGET, ResearchPathAuthority.NodeExpectation.requirements(
                        List.of(Set.of(A, C), Set.of(B)))));

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false,
                List.of(),
                authority);

        assertTrue(result.successful());
        assertEquals(Set.of(B, C, TARGET), Set.copyOf(ids(result.plan().orElseThrow())));
    }

    @Test
    void deterministicBudgetsAndEmergencyFuseFailClosed() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> chain = policies(data,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, legacy(A)));
        ResearchPathUnlockPlanner.PlanningLimits oneLookup =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        1, 1_000, 1_000, 1_000, 1_000, 1_000, 1_000, Long.MAX_VALUE);
        ResearchPathUnlockPlanner.Result lookupLimited =
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        data,
                        chain::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        oneLookup,
                        () -> 0L);
        assertEquals(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                lookupLimited.status());

        Map<ResourceLocation, BlueprintResearchPolicy> broad = broadMandatoryPolicies(data, 140);
        AtomicInteger clockReads = new AtomicInteger();
        ResearchPathUnlockPlanner.PlanningLimits shortFuse =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        10_000, 100_000, 100_000, 100_000,
                        1_000_000, 1_000_000, 10_000, 1);
        ResearchPathUnlockPlanner.Result timedOut = ResearchPathUnlockPlanner.planWithControls(
                TARGET,
                data,
                broad::get,
                ignored -> false,
                false,
                List.of(),
                ResearchPathAuthority.authored(),
                shortFuse,
                () -> clockReads.getAndIncrement() == 0 ? 0L : 2L);
        assertEquals(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX, timedOut.status());
    }

    @Test
    void generalAndOrDagUsesOneIndexedPolicyResolutionPass() {
        PlayerRecipeData data = data(100);
        ResourceLocation d = id("test:d");
        ResourceLocation shared = id("test:shared");
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(shared, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(shared)),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(C, 1, legacy(shared)),
                spec(d, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));
        AtomicInteger policyLookups = new AtomicInteger();
        AtomicInteger exemptionLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> {
                    exemptionLookups.incrementAndGet();
                    return false;
                },
                false);

        assertTrue(result.successful());
        assertEquals(List.of(B, d, TARGET), ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
        assertEquals(policies.size(), exemptionLookups.get());
    }

    @Test
    void generalAndOrDagSharesTheOriginalEmergencyDeadline() {
        PlayerRecipeData data = data(100);
        ResourceLocation d = id("test:d");
        ResourceLocation shared = id("test:shared");
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(shared, 1, ResearchRequirements.EMPTY),
                spec(A, 1, legacy(shared)),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(C, 1, legacy(shared)),
                spec(d, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));
        ResearchPathUnlockPlanner.PlanningLimits limits =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100, 10_000, 10_000, 10_000,
                        100_000, 100_000, 100, 1L);
        AtomicInteger clockReads = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.planWithControls(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false,
                List.of(),
                ResearchPathAuthority.authored(),
                limits,
                () -> {
                    int read = clockReads.getAndIncrement();
                    return read == 0 ? 0L : read <= 2 ? 1L : 2L;
                });

        assertEquals(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX, result.status());
        assertTrue(clockReads.get() >= 4);
    }

    @Test
    void specializedSolversHonorRequestWideDeterministicBudgets() {
        PlayerRecipeData mandatoryData = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> mandatory = policies(mandatoryData,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, legacy(A)));
        ResearchPathUnlockPlanner.PlanningLimits oneState =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100, 1, 100, 100, 100, 100, 100, Long.MAX_VALUE);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        mandatoryData,
                        mandatory::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        oneState,
                        () -> 0L).status());

        PlayerRecipeData orData = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> orPath = policies(orData,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, anyOf(A, B)));
        ResearchPathUnlockPlanner.PlanningLimits oneMerge =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100, 100, 1, 100, 100, 100, 100, Long.MAX_VALUE);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        orData,
                        orPath::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        oneMerge,
                        () -> 0L).status());

        PlayerRecipeData separableData = data(100);
        ResourceLocation d = id("test:d");
        Map<ResourceLocation, BlueprintResearchPolicy> separable = policies(separableData,
                spec(A, 1, ResearchRequirements.EMPTY),
                spec(B, 1, ResearchRequirements.EMPTY),
                spec(C, 1, ResearchRequirements.EMPTY),
                spec(d, 1, ResearchRequirements.EMPTY),
                spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        separableData,
                        separable::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        oneMerge,
                        () -> 0L).status());
    }

    @Test
    void generalSolverHonorsRetainedMemoryAndBitWordBudgets() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                overlappingChoicePolicies(data, 3);
        ResearchPathUnlockPlanner.PlanningLimits twoRetainedLabels =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100,
                        100_000,
                        100_000,
                        100_000,
                        1_000_000,
                        1_000_000,
                        1_000,
                        1_000_000,
                        2,
                        1_000,
                        Long.MAX_VALUE);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        data,
                        policies::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        twoRetainedLabels,
                        () -> 0L).status());

        ResearchPathUnlockPlanner.PlanningLimits oneBitWord =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100,
                        100_000,
                        100_000,
                        100_000,
                        1_000_000,
                        1_000_000,
                        1_000,
                        1,
                        100_000,
                        1_000_000,
                        Long.MAX_VALUE);
        assertEquals(
                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        data,
                        policies::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        oneBitWord,
                        () -> 0L).status());
    }

    @Test
    void generalSolverFrontierExplosionFailsClosedWithoutApproximation() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                overlappingChoicePolicies(data, 8);
        ResearchPathUnlockPlanner.PlanningLimits thirtyTwoOptions =
                new ResearchPathUnlockPlanner.PlanningLimits(
                        100,
                        100_000,
                        100_000,
                        1_000_000,
                        1_000_000,
                        1_000_000,
                        32,
                        Long.MAX_VALUE);

        ResearchPathUnlockPlanner.Result result =
                ResearchPathUnlockPlanner.planWithControls(
                        TARGET,
                        data,
                        policies::get,
                        ignored -> false,
                        false,
                        List.of(),
                        ResearchPathAuthority.authored(),
                        thirtyTwoOptions,
                        () -> 0L);

        assertEquals(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX, result.status());
        assertTrue(result.plan().isEmpty());
    }

    @Test
    void canonicalSupportOrderingIsPreservedByCommonDownstreamNodes() {
        int[] shortSupport = {0};
        int[] longerSupport = {0, 1};
        int before = ResearchPathUnlockPlanner.compareCanonicalSupportIds(
                shortSupport, longerSupport);
        int after = ResearchPathUnlockPlanner.compareCanonicalSupportIds(
                new int[] {0, 2}, new int[] {0, 1, 2});

        assertTrue(before > 0);
        assertEquals(Integer.signum(before), Integer.signum(after));
    }

    @Test
    void plannerAcceptsDepthSixtyFourAndRejectsDepthSixtyFive() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> valid = chainPolicies(data, 64);
        ResourceLocation validTarget = id("test:chain_63");
        assertEquals(64, plan(data, valid, validTarget).unlockCount());

        Map<ResourceLocation, BlueprintResearchPolicy> invalid = chainPolicies(data, 65);
        ResourceLocation invalidTarget = id("test:chain_64");
        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                invalidTarget, data, invalid::get, ignored -> false, false);
        assertEquals(BlueprintResearchService.Status.PATH_TOO_LARGE, result.status());
    }

    @Test
    void broadRepresentativeClosuresStayBounded() {
        for (int nodeCount : List.of(287, 454)) {
            PlayerRecipeData data = data(100);
            Map<ResourceLocation, BlueprintResearchPolicy> policies =
                    broadMandatoryPolicies(data, nodeCount);

            ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

            assertEquals(nodeCount, plan.unlockCount());
        }
    }

    @Test
    void mandatoryDagHonorsTheAtomicUnlockLimitAtItsBoundary() {
        PlayerRecipeData acceptedData = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> accepted =
                broadMandatoryPolicies(
                        acceptedData,
                        ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE);
        assertEquals(
                ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE,
                plan(acceptedData, accepted, TARGET).unlockCount());

        PlayerRecipeData rejectedData = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> rejected =
                broadMandatoryPolicies(
                        rejectedData,
                        ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE + 1);
        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                rejectedData,
                rejected::get,
                ignored -> false,
                false);

        assertEquals(BlueprintResearchService.Status.PATH_TOO_LARGE, result.status());
    }

    @Test
    void orPathSolverHandlesTheMaximumIndexedCatalog() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                broadOrPathPolicies(data, 4_096);
        AtomicInteger policyLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(
                List.of(
                        id("test:or_leaf_0000"),
                        id("test:or_branch_0000"),
                        TARGET),
                ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
    }

    @Test
    void separableSolverHandlesTheMaximumIndexedCatalog() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                broadSeparablePolicies(data);
        AtomicInteger policyLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(129, result.plan().orElseThrow().unlockCount());
        assertEquals(TARGET, ids(result.plan().orElseThrow()).get(128));
        assertEquals(4_096, policyLookups.get());
    }

    @Test
    void generalSolverHandlesARepresentativeLargeInteractingCatalog() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                representativeGeneralPolicies(data, 454);
        AtomicInteger policyLookups = new AtomicInteger();

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                id -> {
                    policyLookups.incrementAndGet();
                    return policies.get(id);
                },
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(List.of(B, id("test:general_d"), TARGET),
                ids(result.plan().orElseThrow()));
        assertEquals(policies.size(), policyLookups.get());
        assertEquals(454, policyLookups.get());
    }

    @Test
    void generalSolverCanIgnoreAnOversizedAlternativeWhenAnotherExactRouteExists() {
        PlayerRecipeData data = data(100);
        ResourceLocation requiredLeaf = id("test:oversized_leaf_0000");
        ResourceLocation small = id("test:oversized_small");
        Map<ResourceLocation, BlueprintResearchPolicy> policies =
                oversizedGeneralAlternativePolicies(data, small);

        ResearchPathUnlockPlanner.Result result = ResearchPathUnlockPlanner.plan(
                TARGET,
                data,
                policies::get,
                ignored -> false,
                false);

        assertTrue(result.successful());
        assertEquals(List.of(requiredLeaf, small, TARGET),
                ids(result.plan().orElseThrow()));
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
        assertEquals(List.of(A, B, C, TARGET), plan.solution().supportIds());
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
        assertEquals(1, data.getRecentUnlockBatches().size());
        assertEquals(TARGET.toString(),
                data.getRecentUnlockBatches().get(0).targetBlueprintId());
        assertEquals(List.of(A.toString(), TARGET.toString()),
                data.getRecentUnlockBatches().get(0).memberBlueprintIds());
    }

    @Test
    void preparedPathCommitUsesTheApprovedPlanWithoutResolvingPoliciesAgain() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        ResearchPathUnlockPlanner.Plan approved = plan(data, policies, TARGET);
        TestInput input = new TestInput(List.of(
                new ItemStack(Items.PAPER), new ItemStack(Items.IRON_INGOT)));

        BlueprintResearchService.Result result =
                BlueprintResearchService.commitPreparedPath(
                        TARGET,
                        data,
                        approved,
                        ResearchPathUnlockPlannerTest::learningTarget,
                        input,
                        true);

        assertTrue(result.successful());
        assertEquals(5, result.spentPoints());
        assertEquals(List.of(A, TARGET), result.transitions().stream()
                .map(BlueprintResearchService.LearningTransition::blueprintId).toList());
        assertTrue(input.stacks.stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void planningResultSeparatesSolutionQuoteAllocationAndTransaction() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);

        assertEquals(List.of(A, TARGET), plan.solution().nodes().stream()
                .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId).toList());
        assertEquals(2, plan.solution().unlockCount());
        assertEquals(5, plan.quote().pointCost());
        assertEquals(2, plan.quote().totalMaterialUnits());
        assertEquals(plan.solution().nodes(), plan.nodes());
        assertEquals(plan.quote().ingredients(), plan.ingredients());

        ResearchPathUnlockPlanner.InventoryAllocation partial =
                ResearchPathUnlockPlanner.allocateInventory(
                        plan, List.of(new ItemStack(Items.PAPER)))
                        .orElseThrow();
        assertEquals(plan.quote(), partial.quote());
        assertEquals(2, partial.allocation().totalRequired());
        assertEquals(1, partial.allocation().totalAllocated());
        assertFalse(partial.complete());
        assertTrue(ResearchPathUnlockPlanner.prepareTransaction(
                plan, List.of(new ItemStack(Items.PAPER))).isEmpty());

        ResearchPathUnlockPlanner.TransactionPlan transaction =
                ResearchPathUnlockPlanner.prepareTransaction(
                        plan,
                        List.of(
                                new ItemStack(Items.PAPER),
                                new ItemStack(Items.IRON_INGOT)))
                        .orElseThrow();
        assertEquals(plan.solution(), transaction.solution());
        assertEquals(plan.quote(), transaction.quote());
        assertEquals(2, transaction.unlockCount());
        assertEquals(2, transaction.ingredientPlan().totalConsumed());
    }

    @Test
    void allocationDoesNotChangeTheFrozenSelectedRouteOrQuote() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(B, 2, ResearchRequirements.EMPTY, false, iron(1)),
                spec(TARGET, 1, anyOf(A, B)));

        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);
        ResearchPathUnlockPlanner.SelectedUnlockSolution solution = plan.solution();
        ResearchPathUnlockPlanner.RouteQuote quote = plan.quote();

        ResearchPathUnlockPlanner.InventoryAllocation empty =
                ResearchPathUnlockPlanner.allocateInventory(plan, List.of()).orElseThrow();
        ResearchPathUnlockPlanner.InventoryAllocation supplied =
                ResearchPathUnlockPlanner.allocateInventory(
                        plan, List.of(new ItemStack(Items.PAPER)))
                        .orElseThrow();

        assertEquals(List.of(A, TARGET), ids(plan));
        assertEquals(solution, plan.solution());
        assertEquals(quote, empty.quote());
        assertEquals(quote, supplied.quote());
        assertFalse(empty.complete());
        assertTrue(supplied.complete());
    }

    @Test
    void routeFingerprintTracksStructuralStateButNotRpBalance() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);
        ResearchRouteFingerprint.Context context = fingerprintContext(4L, 5L, 6L);

        ResearchRouteFingerprint original = ResearchRouteFingerprint.create(
                TARGET, plan, data, false, context);
        Map<ResourceLocation, BlueprintResearchPolicy> changedPolicies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 4, legacy(A), false, iron(1)));
        ResearchPathUnlockPlanner.Plan changedQuote = plan(data, changedPolicies, TARGET);
        assertFalse(original.equals(ResearchRouteFingerprint.create(
                TARGET, changedQuote, data, false, context)));

        BlueprintProgressionConfigSnapshot config = context.progressionConfig();
        ResearchRouteFingerprint.Context changedConfig = new ResearchRouteFingerprint.Context(
                context.catalogRevision(),
                context.researchRevision(),
                context.automaticPublicationRevision(),
                new BlueprintProgressionConfigSnapshot(
                        config.blueprintsEnabled(),
                        config.discoveryTrackingEnabled(),
                        config.journalEnabled(),
                        config.maximumUndiscoveredVisibility(),
                        config.researchEnabled(),
                        config.duplicatePolicy(),
                        config.allowUnlearnedRecycling(),
                        config.pointCap() + 1,
                        config.creativeBypassesResearchCost(),
                        config.activeProfileId(),
                        config.treeResearchResultMode(),
                        config.researchCostMode(),
                        config.foundWeaponRecoveryMode()),
                context.accessConfig());
        assertFalse(original.equals(ResearchRouteFingerprint.create(
                TARGET, plan, data, false, changedConfig)));

        data.setResearchPoints(25);
        ResearchRouteFingerprint differentBalance = ResearchRouteFingerprint.create(
                TARGET, plan, data, false, context);

        assertEquals(original, differentBalance);
        data.discoverBlueprint(C.toString());
        assertFalse(original.equals(ResearchRouteFingerprint.create(
                TARGET, plan, data, false, context)));
        assertFalse(original.equals(ResearchRouteFingerprint.create(
                TARGET, plan, data, true, context)));
        assertFalse(original.equals(ResearchRouteFingerprint.create(
                TARGET, plan, data, false, fingerprintContext(4L, 5L, 7L))));
    }

    @Test
    void routeFingerprintSeparatesAdjacentVariableLengthStateSections() {
        PlayerRecipeData data = data(100);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(TARGET, 3, ResearchRequirements.EMPTY));
        ResearchPathUnlockPlanner.Plan plan = plan(data, policies, TARGET);
        ResearchRouteFingerprint.Context exemptContext = new ResearchRouteFingerprint.Context(
                4L,
                5L,
                6L,
                fingerprintContext(4L, 5L, 6L).progressionConfig(),
                new BlueprintAccessConfigSnapshot(
                        Set.of(A), Set.of(), Set.of(), Set.of()));
        ResearchRouteFingerprint.Context startingContext = new ResearchRouteFingerprint.Context(
                4L,
                5L,
                6L,
                fingerprintContext(4L, 5L, 6L).progressionConfig(),
                new BlueprintAccessConfigSnapshot(
                        Set.of(), Set.of(), Set.of(), Set.of(A)));

        assertFalse(ResearchRouteFingerprint.create(
                TARGET, plan, data, false, exemptContext).equals(
                        ResearchRouteFingerprint.create(
                                TARGET, plan, data, false, startingContext)));
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
        assertTrue(data.getRecentUnlockBatches().isEmpty());
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

    @Test
    void routeEvaluationOwnsTheSamePathQuoteAllocationAndReadinessDecision() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(2)),
                spec(TARGET, 3, legacy(A), false, paper(3)));
        ResearchRouteEvaluationService.Evaluation evaluation =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(
                                data,
                                policies,
                                TARGET,
                                List.of(new ItemStack(Items.PAPER, 4))))
                        .orElseThrow();

        assertEquals(List.of(A, TARGET), ids(evaluation.path().orElseThrow()));
        assertEquals(5, evaluation.pointCost());
        assertEquals(1, evaluation.requirements().size());
        assertEquals(5, evaluation.requirements().get(0).count());
        assertEquals(4, evaluation.allocation().allocatedForIngredient(0));
        assertFalse(evaluation.ingredientsSatisfied());
        assertFalse(evaluation.ready());
        assertTrue(evaluation.policyEligible());
        assertTrue(evaluation.transactionCapacityAvailable());
        assertTrue(evaluation.routeFingerprint().isPresent());
    }

    @Test
    void routeEvaluationChangesLiveAllocationWithoutChangingItsStructuralRoute() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        ResearchRouteEvaluationService.Evaluation empty =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(data, policies, TARGET, List.of()))
                        .orElseThrow();
        ResearchRouteEvaluationService.Evaluation supplied =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(
                                data,
                                policies,
                                TARGET,
                                List.of(new ItemStack(Items.PAPER),
                                        new ItemStack(Items.IRON_INGOT))))
                        .orElseThrow();

        assertEquals(empty.path(), supplied.path());
        assertEquals(empty.routeFingerprint(), supplied.routeFingerprint());
        assertFalse(empty.ingredientsSatisfied());
        assertTrue(supplied.ingredientsSatisfied());
        assertTrue(supplied.ready());
    }

    @Test
    void guidanceSnapshotPublishesOnlyTheExactSelectedRouteAndAllocation() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(1)),
                spec(TARGET, 3, legacy(A), false, iron(1)));
        ResearchRouteEvaluationService.Evaluation evaluation =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(
                                data,
                                policies,
                                TARGET,
                                List.of(new ItemStack(Items.PAPER))))
                        .orElseThrow();
        ResearchTreeGraph publicGraph = new ResearchTreeGraph(
                List.of(
                        guidanceNode(0, A, ResearchTreeGraph.Availability.AVAILABLE, 0),
                        guidanceNode(
                                1,
                                TARGET,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                1)),
                List.of(new ResearchTreeGraph.Edge(A, TARGET)));

        ResearchGuidanceSnapshot snapshot =
                ResearchRouteEvaluationService.guidanceSnapshot(
                        evaluation, publicGraph, ResearchCostMode.POINTS_AND_ITEMS)
                        .orElseThrow();

        assertEquals(ResearchGuidanceSnapshot.State.MISSING_MATERIALS, snapshot.state());
        assertEquals(List.of(A, TARGET), snapshot.supportIds());
        assertEquals(List.of(A, TARGET), snapshot.purchaseIds());
        assertEquals(A, snapshot.nextStepId().orElseThrow());
        assertEquals(2, snapshot.materials().size());
        assertEquals(2, snapshot.totalMaterialTypes());
        assertEquals(2, snapshot.totalMaterialUnits());
        assertEquals(1, snapshot.allocatedMaterialUnits());
        assertEquals(1, snapshot.missingMaterialTypes());
        assertEquals(1, snapshot.materials().stream()
                .mapToInt(ResearchGuidanceSnapshot.MaterialProgress::allocated)
                .sum());
        assertEquals(
                List.of(new ResearchPathUnlockPlanner.SelectedRequirement(TARGET, 0, A)),
                snapshot.selectedRequirements());
    }

    @Test
    void guidanceSnapshotOmitsDisabledCostChannels() {
        PlayerRecipeData data = data(1);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY, false, paper(2)),
                spec(TARGET, 3, legacy(A), false, iron(2)));
        ResearchRouteEvaluationService.Evaluation evaluation =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(
                                data,
                                policies,
                                TARGET,
                                List.of(new ItemStack(Items.PAPER))))
                        .orElseThrow();
        ResearchTreeGraph publicGraph = new ResearchTreeGraph(
                List.of(
                        guidanceNode(0, A, ResearchTreeGraph.Availability.AVAILABLE, 0),
                        guidanceNode(
                                1,
                                TARGET,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                1)),
                List.of(new ResearchTreeGraph.Edge(A, TARGET)));

        ResearchGuidanceSnapshot pointsOnly =
                ResearchRouteEvaluationService.guidanceSnapshot(
                        evaluation, publicGraph, ResearchCostMode.POINTS_ONLY)
                        .orElseThrow();
        assertEquals(ResearchGuidanceSnapshot.State.MISSING_POINTS, pointsOnly.state());
        assertEquals(5, pointsOnly.pointCost());
        assertEquals(0, pointsOnly.totalMaterialTypes());
        assertEquals(0, pointsOnly.totalMaterialUnits());
        assertTrue(pointsOnly.materials().isEmpty());

        ResearchGuidanceSnapshot itemsOnly =
                ResearchRouteEvaluationService.guidanceSnapshot(
                        evaluation, publicGraph, ResearchCostMode.ITEMS_ONLY)
                        .orElseThrow();
        assertEquals(ResearchGuidanceSnapshot.State.MISSING_MATERIALS, itemsOnly.state());
        assertEquals(0, itemsOnly.pointCost());
        assertEquals(0, itemsOnly.pointBalance());
        assertEquals(4, itemsOnly.totalMaterialUnits());
        assertEquals(1, itemsOnly.allocatedMaterialUnits());
        assertEquals(2, itemsOnly.missingMaterialTypes());
    }

    @Test
    void guidanceSnapshotDoesNotDiscloseASelectedEdgeAbsentFromThePublicGraph() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(A, 2, ResearchRequirements.EMPTY),
                spec(TARGET, 3, legacy(A)));
        ResearchRouteEvaluationService.Evaluation evaluation =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(data, policies, TARGET, List.of()))
                        .orElseThrow();
        ResearchTreeGraph redactedGraph = new ResearchTreeGraph(
                List.of(
                        guidanceNode(0, A, ResearchTreeGraph.Availability.AVAILABLE, 0),
                        guidanceNode(
                                1,
                                TARGET,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                                0)),
                List.of());

        ResearchGuidanceSnapshot snapshot =
                ResearchRouteEvaluationService.guidanceSnapshot(
                        evaluation, redactedGraph, ResearchCostMode.POINTS_AND_ITEMS)
                        .orElseThrow();

        assertEquals(ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE, snapshot.state());
        assertEquals(List.of(TARGET), snapshot.supportIds());
        assertTrue(snapshot.purchaseIds().isEmpty());
        assertTrue(snapshot.selectedRequirements().isEmpty());
    }

    @Test
    void routeEvaluationFailsClosedWhenTheSelectedClosureCannotBeResolved() {
        PlayerRecipeData data = data(10);
        Map<ResourceLocation, BlueprintResearchPolicy> policies = policies(data,
                spec(TARGET, 3, legacy(A)));

        ResearchRouteEvaluationService.Evaluation evaluation =
                ResearchRouteEvaluationService.evaluate(
                        evaluationRequest(data, policies, TARGET, List.of()))
                        .orElseThrow();

        assertEquals(BlueprintResearchService.Status.PREREQUISITES_REQUIRED,
                evaluation.planningStatus());
        assertTrue(evaluation.path().isEmpty());
        assertTrue(evaluation.routeFingerprint().isEmpty());
        assertFalse(evaluation.policyEligible());
        assertFalse(evaluation.ready());
    }

    private static ResearchRouteEvaluationService.Request evaluationRequest(
            PlayerRecipeData data,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation target,
            List<ItemStack> inventory) {
        return new ResearchRouteEvaluationService.Request(
                target,
                data,
                policies.get(target),
                policies::get,
                ignored -> false,
                ResearchPathAuthority.authored(),
                fingerprintContext(1L, 1L, 1L),
                ResearchPathUnlockPlannerTest::learningTarget,
                inventory,
                false,
                true,
                true);
    }

    private static ResearchTreeGraph.Node guidanceNode(
            int ordinal,
            ResourceLocation id,
            ResearchTreeGraph.Availability availability,
            int prerequisiteCount) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "gun",
                new ResourceLocation("test:slot/" + id.getPath()),
                JournalVisibility.FULL,
                availability == ResearchTreeGraph.Availability.LEARNED,
                true,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                1,
                1,
                prerequisiteCount,
                0,
                availability);
    }

    private static ResearchPathUnlockPlanner.Plan plan(
            PlayerRecipeData data,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation target) {
        return ResearchPathUnlockPlanner.plan(
                target, data, policies::get, ignored -> false, false)
                .plan().orElseThrow();
    }

    private static ResearchRouteFingerprint.Context fingerprintContext(
            long catalogRevision,
            long researchRevision,
            long automaticRevision) {
        return new ResearchRouteFingerprint.Context(
                catalogRevision,
                researchRevision,
                automaticRevision,
                new BlueprintProgressionConfigSnapshot(
                        true,
                        true,
                        true,
                        JournalVisibility.FULL,
                        true,
                        DuplicateBlueprintPolicy.KEEP,
                        false,
                        100,
                        true,
                        PROFILE),
                BlueprintAccessConfigSnapshot.EMPTY);
    }

    private static List<ResourceLocation> ids(ResearchPathUnlockPlanner.Plan plan) {
        return plan.nodes().stream()
                .map(ResearchPathUnlockPlanner.PlannedNode::blueprintId)
                .toList();
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> chainPolicies(
            PlayerRecipeData data,
            int nodeCount) {
        PolicySpec[] specs = new PolicySpec[nodeCount];
        for (int index = 0; index < nodeCount; index++) {
            ResourceLocation node = id("test:chain_" + index);
            specs[index] = spec(
                    node,
                    1,
                    index == 0
                            ? ResearchRequirements.EMPTY
                            : legacy(id("test:chain_" + (index - 1))));
        }
        return policies(data, specs);
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> broadMandatoryPolicies(
            PlayerRecipeData data,
            int nodeCount) {
        int aggregatorCount = Math.max(1, (nodeCount - 1 + 64) / 65);
        int leafCount = nodeCount - aggregatorCount - 1;
        List<PolicySpec> specs = new ArrayList<>(nodeCount);
        List<ResourceLocation> leaves = new ArrayList<>(leafCount);
        for (int index = 0; index < leafCount; index++) {
            ResourceLocation leaf = id("test:broad_" + index);
            leaves.add(leaf);
            specs.add(spec(leaf, 1, ResearchRequirements.EMPTY));
        }
        List<ResourceLocation> aggregators = new ArrayList<>(aggregatorCount);
        for (int index = 0; index < aggregatorCount; index++) {
            int from = index * leafCount / aggregatorCount;
            int to = (index + 1) * leafCount / aggregatorCount;
            ResourceLocation aggregator = id("test:aggregate_" + index);
            aggregators.add(aggregator);
            specs.add(spec(
                    aggregator,
                    1,
                    and(leaves.subList(from, to).toArray(ResourceLocation[]::new))));
        }
        specs.add(spec(TARGET, 1, and(aggregators.toArray(ResourceLocation[]::new))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> broadOrPathPolicies(
            PlayerRecipeData data,
            int nodeCount) {
        if (nodeCount < 3 || nodeCount > 4_096) {
            throw new IllegalArgumentException(
                    "broad OR-path fixture must contain between 3 and 4,096 nodes");
        }
        int branchCount = Math.max(1, (nodeCount - 1 + 64) / 65);
        int leafCount = nodeCount - branchCount - 1;
        List<PolicySpec> specs = new ArrayList<>(nodeCount);
        List<ResourceLocation> leaves = new ArrayList<>(leafCount);
        for (int index = 0; index < leafCount; index++) {
            ResourceLocation leaf = id(String.format(
                    java.util.Locale.ROOT, "test:or_leaf_%04d", index));
            leaves.add(leaf);
            specs.add(spec(leaf, 1, ResearchRequirements.EMPTY));
        }
        List<ResourceLocation> branches = new ArrayList<>(branchCount);
        for (int index = 0; index < branchCount; index++) {
            int from = index * leafCount / branchCount;
            int to = (index + 1) * leafCount / branchCount;
            ResourceLocation branch = id(String.format(
                    java.util.Locale.ROOT, "test:or_branch_%04d", index));
            branches.add(branch);
            specs.add(spec(
                    branch,
                    1,
                    anyOf(leaves.subList(from, to).toArray(ResourceLocation[]::new))));
        }
        specs.add(spec(
                TARGET,
                1,
                anyOf(branches.toArray(ResourceLocation[]::new))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> broadSeparablePolicies(
            PlayerRecipeData data) {
        int groupCount = ResearchRequirements.MAX_GROUPS;
        List<PolicySpec> specs = new ArrayList<>(4_096);
        List<ResourceLocation> branches = new ArrayList<>(groupCount);
        int createdNodes = 1;
        for (int group = 0; group < groupCount; group++) {
            int alternativesInGroup = group == groupCount - 1 ? 62 : 63;
            ResourceLocation[] alternatives = new ResourceLocation[alternativesInGroup];
            for (int alternative = 0; alternative < alternativesInGroup; alternative++) {
                ResourceLocation id = id(String.format(
                        java.util.Locale.ROOT,
                        "test:separable_%02d_%02d",
                        group,
                        alternative));
                alternatives[alternative] = id;
                specs.add(spec(id, 1, ResearchRequirements.EMPTY));
                createdNodes++;
            }
            ResourceLocation branch = id(String.format(
                    java.util.Locale.ROOT,
                    "test:separable_branch_%02d",
                    group));
            branches.add(branch);
            specs.add(spec(branch, 1, anyOf(alternatives)));
            createdNodes++;
        }
        if (createdNodes != 4_096) {
            throw new IllegalStateException("maximum separable fixture is malformed");
        }
        specs.add(spec(
                TARGET,
                1,
                and(branches.toArray(ResourceLocation[]::new))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> overlappingChoicePolicies(
            PlayerRecipeData data,
            int groupCount) {
        if (groupCount < 2 || groupCount * 2 > 64) {
            throw new IllegalArgumentException(
                    "overlapping choice fixture requires between 2 and 32 groups");
        }
        ResourceLocation shared = id("test:overlap_shared");
        List<PolicySpec> specs = new ArrayList<>();
        specs.add(spec(shared, 1, ResearchRequirements.EMPTY));
        List<ResearchRequirements> groups = new ArrayList<>();
        for (int group = 0; group < groupCount; group++) {
            ResourceLocation left = id(String.format(
                    java.util.Locale.ROOT, "test:overlap_%02d_a", group));
            ResourceLocation right = id(String.format(
                    java.util.Locale.ROOT, "test:overlap_%02d_b", group));
            specs.add(spec(left, 1, legacy(shared)));
            specs.add(spec(right, 1, legacy(shared)));
            groups.add(anyOf(left, right));
        }
        specs.add(spec(
                TARGET,
                1,
                grouped(groups.toArray(ResearchRequirements[]::new))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy> representativeGeneralPolicies(
            PlayerRecipeData data,
            int nodeCount) {
        if (nodeCount != 454) {
            throw new IllegalArgumentException(
                    "representative general fixture currently requires 454 nodes");
        }
        ResourceLocation shared = id("test:general_shared");
        ResourceLocation d = id("test:general_d");
        int aggregatorCount = 7;
        int leafCount = nodeCount - aggregatorCount - 6;
        List<PolicySpec> specs = new ArrayList<>(nodeCount);
        List<ResourceLocation> leaves = new ArrayList<>(leafCount);
        for (int index = 0; index < leafCount; index++) {
            ResourceLocation leaf = id(String.format(
                    java.util.Locale.ROOT, "test:general_leaf_%03d", index));
            leaves.add(leaf);
            specs.add(spec(leaf, 1, ResearchRequirements.EMPTY));
        }
        List<ResourceLocation> aggregators = new ArrayList<>(aggregatorCount);
        for (int index = 0; index < aggregatorCount; index++) {
            int from = index * leafCount / aggregatorCount;
            int to = (index + 1) * leafCount / aggregatorCount;
            ResourceLocation aggregator = id("test:general_aggregate_" + index);
            aggregators.add(aggregator);
            specs.add(spec(
                    aggregator,
                    1,
                    and(leaves.subList(from, to).toArray(ResourceLocation[]::new))));
        }
        specs.add(spec(shared, 1, and(aggregators.toArray(ResourceLocation[]::new))));
        specs.add(spec(A, 1, legacy(shared)));
        specs.add(spec(B, 1, ResearchRequirements.EMPTY));
        specs.add(spec(C, 1, legacy(shared)));
        specs.add(spec(d, 1, ResearchRequirements.EMPTY));
        specs.add(spec(TARGET, 1, grouped(anyOf(A, B), anyOf(C, d))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static Map<ResourceLocation, BlueprintResearchPolicy>
            oversizedGeneralAlternativePolicies(
                    PlayerRecipeData data,
                    ResourceLocation small) {
        int branchCount = 32;
        int leavesPerBranch = 32;
        ResourceLocation huge = id("test:oversized_huge");
        List<PolicySpec> specs = new ArrayList<>();
        List<ResourceLocation> aggregators = new ArrayList<>(branchCount);
        for (int branch = 0; branch < branchCount; branch++) {
            List<ResourceLocation> leaves = new ArrayList<>(leavesPerBranch);
            for (int leaf = 0; leaf < leavesPerBranch; leaf++) {
                int leafIndex = branch * leavesPerBranch + leaf;
                ResourceLocation leafId = id(String.format(
                        java.util.Locale.ROOT,
                        "test:oversized_leaf_%04d",
                        leafIndex));
                leaves.add(leafId);
                specs.add(spec(leafId, 1, ResearchRequirements.EMPTY));
            }
            ResourceLocation aggregator = id(String.format(
                    java.util.Locale.ROOT,
                    "test:oversized_aggregate_%02d",
                    branch));
            aggregators.add(aggregator);
            specs.add(spec(
                    aggregator,
                    1,
                    and(leaves.toArray(ResourceLocation[]::new))));
        }
        specs.add(spec(huge, 1, and(aggregators.toArray(ResourceLocation[]::new))));
        specs.add(spec(small, 1, ResearchRequirements.EMPTY));
        specs.add(spec(
                TARGET,
                1,
                grouped(
                        anyOf(huge, small),
                        legacy(id("test:oversized_leaf_0000")))));
        return policies(data, specs.toArray(PolicySpec[]::new));
    }

    private static PolicySpec oracleSpec(
            ResourceLocation id,
            Random random,
            ResearchRequirements requirements) {
        int materialCount = random.nextInt(3);
        return materialCount == 0
                ? spec(id, 1 + random.nextInt(5), requirements)
                : spec(
                        id,
                        1 + random.nextInt(5),
                        requirements,
                        false,
                        paper(materialCount));
    }

    private static Set<ResourceLocation> bruteForceBestPurchases(
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation target) {
        List<Set<ResourceLocation>> closures = bruteForceClosures(
                policies, target, new LinkedHashMap<>());
        return closures.stream()
                .min((left, right) -> compareBruteForceClosures(policies, left, right))
                .orElseThrow();
    }

    private static List<Set<ResourceLocation>> bruteForceClosures(
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            ResourceLocation node,
            Map<ResourceLocation, List<Set<ResourceLocation>>> memo) {
        List<Set<ResourceLocation>> cached = memo.get(node);
        if (cached != null) {
            return cached;
        }
        BlueprintResearchPolicy policy = policies.get(node);
        if (policy == null) {
            throw new IllegalArgumentException("brute-force oracle policy is missing");
        }
        List<Set<ResourceLocation>> current = List.of(Set.of());
        for (ResearchPrerequisiteGroup group : policy.requirements().allOf()) {
            List<Set<ResourceLocation>> alternatives = new ArrayList<>();
            for (ResourceLocation alternative : group.anyOf()) {
                alternatives.addAll(bruteForceClosures(policies, alternative, memo));
            }
            List<Set<ResourceLocation>> combined = new ArrayList<>();
            for (Set<ResourceLocation> base : current) {
                for (Set<ResourceLocation> alternative : alternatives) {
                    java.util.LinkedHashSet<ResourceLocation> merged =
                            new java.util.LinkedHashSet<>(base);
                    merged.addAll(alternative);
                    combined.add(Set.copyOf(merged));
                }
            }
            current = deduplicateClosures(combined);
        }
        List<Set<ResourceLocation>> result = new ArrayList<>();
        for (Set<ResourceLocation> closure : current) {
            java.util.LinkedHashSet<ResourceLocation> withNode =
                    new java.util.LinkedHashSet<>(closure);
            withNode.add(node);
            result.add(Set.copyOf(withNode));
        }
        result = deduplicateClosures(result);
        memo.put(node, result);
        return result;
    }

    private static List<Set<ResourceLocation>> deduplicateClosures(
            List<Set<ResourceLocation>> closures) {
        Map<String, Set<ResourceLocation>> unique = new LinkedHashMap<>();
        for (Set<ResourceLocation> closure : closures) {
            unique.putIfAbsent(canonicalClosure(closure), closure);
        }
        return List.copyOf(unique.values());
    }

    private static int compareBruteForceClosures(
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            Set<ResourceLocation> left,
            Set<ResourceLocation> right) {
        int comparison = Integer.compare(left.size(), right.size());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(
                bruteForcePointCost(policies, left),
                bruteForcePointCost(policies, right));
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compare(
                bruteForceMaterialCount(policies, left),
                bruteForceMaterialCount(policies, right));
        return comparison != 0
                ? comparison
                : canonicalClosure(left).compareTo(canonicalClosure(right));
    }

    private static long bruteForcePointCost(
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            Set<ResourceLocation> closure) {
        return closure.stream()
                .map(policies::get)
                .mapToLong(policy -> policy.researchCost().points())
                .sum();
    }

    private static long bruteForceMaterialCount(
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            Set<ResourceLocation> closure) {
        return closure.stream()
                .map(policies::get)
                .flatMap(policy -> policy.researchCost().ingredients().stream())
                .mapToLong(BlueprintResearchIngredient::count)
                .sum();
    }

    private static String canonicalClosure(Set<ResourceLocation> closure) {
        return closure.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining("\u0000"));
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
