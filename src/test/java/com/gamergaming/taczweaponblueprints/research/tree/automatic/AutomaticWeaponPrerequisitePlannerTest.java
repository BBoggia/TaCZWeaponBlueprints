package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalBuilder;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidateClassification;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchAutomaticPlacementProfile;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponPrerequisitePlannerTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation PROFILE_B = id("test:profile_b");
    private static final ResourceLocation TREE = id("test:tree");
    private static final ResourceLocation LANE = id("test:weapons");
    private static final ResourceLocation ROOT_A = id("test:root_a");
    private static final ResourceLocation ROOT_B = id("test:root_b");

    @Test
    void connectedModeDistributesCandidatesAcrossEarlierAuthoredAnchors() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> id("addon:weapon_" + index))
                .toList();
        Map<ResourceLocation, BlueprintData> catalog = catalog(addOns);
        BlueprintResearchSnapshot research = snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profile(), PROFILE_B, profile()));
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                AutomaticPlacementMode.CONNECTED, addOns);

        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research, catalog, PROFILE, candidates);

        assertEquals(addOns.size(), plan.prerequisites().size());
        assertTrue(plan.omittedCandidates().isEmpty());
        Map<ResourceLocation, Integer> addOnOrder = new LinkedHashMap<>();
        for (int index = 0; index < addOns.size(); index++) {
            addOnOrder.put(addOns.get(index), index);
        }
        assertTrue(plan.prerequisites().entrySet().stream().allMatch(entry ->
                entry.getValue().stream().allMatch(prerequisite ->
                        Set.of(ROOT_A, ROOT_B).contains(prerequisite)
                                || addOnOrder.get(prerequisite) < addOnOrder.get(entry.getKey()))));
        assertTrue(plan.prerequisites().values().stream()
                .anyMatch(value -> value.size() > 1));
        assertTrue(plan.prerequisites().values().stream()
                .flatMap(List::stream)
                .anyMatch(addOnOrder::containsKey));
        assertTrue(plan.prerequisites().values().stream().distinct().count() > 1);
        for (int index = 1; index < addOns.size(); index++) {
            ResourceLocation target = addOns.get(index);
            assertTrue(
                    addOns.subList(0, index).contains(
                            plan.prerequisitesFor(target).get(0)),
                    () -> "same-level automatic anchor was not preferred for " + target);
        }
        plan.prerequisites().values().forEach(prerequisites -> {
            for (int left = 0; left < prerequisites.size(); left++) {
                for (int right = left + 1; right < prerequisites.size(); right++) {
                    ResourceLocation first = prerequisites.get(left);
                    ResourceLocation second = prerequisites.get(right);
                    assertFalse(dependsOn(plan, first, second));
                    assertFalse(dependsOn(plan, second, first));
                }
            }
        });

        AutomaticWeaponPlacementCandidateSnapshot distributed = candidates(
                AutomaticPlacementMode.DISTRIBUTED, addOns);
        AutomaticWeaponPrerequisitePlan distributedPlan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        snapshot(AutomaticPlacementMode.DISTRIBUTED),
                        catalog,
                        PROFILE,
                        distributed);
        assertTrue(distributedPlan.prerequisites().isEmpty());
        assertEquals(addOns.size(), distributedPlan.omittedCandidates().size());
    }

    @Test
    void dynamicStatLayersJoinTheAuthoredBaseThenBranchWithinNineNodeRanks() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 27)
                .mapToObj(index -> id("dynamic:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot candidates = dynamicCandidates(addOns);
        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        dynamicSnapshot(AutomaticPlacementMode.CONNECTED),
                        catalog(addOns),
                        PROFILE,
                        candidates);

        Map<Integer, List<ResourceLocation>> layers = candidates.eligibleProposals().entrySet()
                .stream().collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getValue().progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.mapping(
                                entry -> id(entry.getKey()),
                                java.util.stream.Collectors.toList())));
        assertEquals(List.of(2, 9, 9, 7), layers.values().stream()
                .map(List::size).toList());
        assertTrue(plan.omittedCandidates().isEmpty());
        assertTrue(layers.get(0).stream().allMatch(id ->
                plan.prerequisitesFor(id).size() == 1
                        && Set.of(ROOT_A, ROOT_B).contains(
                                plan.prerequisiteFor(id).orElseThrow())));
        assertTrue(layers.get(1).stream()
                .allMatch(id -> plan.prerequisitesFor(id).size() == 2));
        assertTrue(layers.get(2).stream()
                .allMatch(id -> plan.prerequisitesFor(id).size() == 2));
        assertTrue(layers.get(3).stream()
                .allMatch(id -> plan.prerequisitesFor(id).size() == 1));
        assertTrue(layers.get(1).stream().allMatch(id ->
                layers.get(0).containsAll(plan.prerequisitesFor(id))));
        assertTrue(layers.get(2).stream().allMatch(id ->
                layers.get(1).containsAll(plan.prerequisitesFor(id))));
        assertTrue(layers.get(3).stream().allMatch(id ->
                layers.get(2).containsAll(plan.prerequisitesFor(id))));
        assertTrue(layers.get(0).stream().anyMatch(root ->
                layers.get(1).stream().filter(child ->
                        plan.prerequisitesFor(child).contains(root)).count() > 1));
        plan.prerequisites().forEach((dependent, prerequisites) -> prerequisites.forEach(
                prerequisite -> candidates.eligibleProposal(prerequisite).ifPresentOrElse(
                        proposal -> assertTrue(
                                proposal.progressionCoordinate().rank()
                                        < candidates.eligibleProposal(dependent).orElseThrow()
                                                .progressionCoordinate().rank()),
                        () -> assertTrue(Set.of(ROOT_A, ROOT_B).contains(prerequisite)))));
    }

    @Test
    void automaticCostOutliersRetainOneBoundedParentInsteadOfBecomingStrayRoots() {
        for (ResourceLocation target : List.of(
                id("addon:rpg7"), id("addon:db_long"))) {
            ResourceLocation foundationA = id("addon:foundation_a");
            ResourceLocation foundationB = id("addon:foundation_b");
            List<ResourceLocation> weapons = List.of(
                    foundationA, foundationB, target);
            AutomaticWeaponPlacementCandidateSnapshot candidates =
                    dynamicCandidates(weapons, false);
            BlueprintResearchSnapshot research = snapshot(
                    AutomaticPlacementMode.CONNECTED,
                    Map.of(PROFILE, profile()),
                    new ResearchAutomaticPlacementProfile(
                            2,
                            TREE,
                            AutomaticPlacementMode.CONNECTED,
                            3,
                            0,
                            AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                            2,
                            4,
                            9,
                            List.of()),
                    Map.of(
                            id("test:cost_outlier"),
                            costRule(target, 6)));

            AutomaticWeaponPrerequisitePlan plan =
                    new AutomaticWeaponPrerequisitePlanner().plan(
                            research,
                            automaticOnlyCatalog(weapons),
                            PROFILE,
                            candidates);

            assertEquals(
                    Set.of(foundationA, foundationB),
                    plan.omittedCandidates().keySet());
            assertTrue(plan.omittedCandidates().values().stream()
                    .allMatch("generated_root"::equals));
            assertEquals(1, plan.prerequisitesFor(target).size());
            ResourceLocation parent = plan.prerequisiteFor(target).orElseThrow();
            assertTrue(Set.of(foundationA, foundationB).contains(parent));
            assertTrue(occupiedRankDistance(candidates, target, parent)
                    <= ResearchTechTreeContract.MAX_AUTOMATIC_EDGE_RANK_SPAN);
        }
    }

    @Test
    void groupedRoutesReuseSelectedParentsAsOneAnyOfGroup() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 27)
                .mapToObj(index -> id("grouped:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot legacyCandidates =
                dynamicCandidates(addOns);
        AutomaticWeaponPlacementCandidateSnapshot groupedCandidates =
                dynamicCandidates(addOns, true, PrerequisiteStrategy.GROUPED_ROUTES_V1);

        AutomaticWeaponPrerequisitePlan legacy =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        dynamicSnapshot(AutomaticPlacementMode.CONNECTED),
                        catalog(addOns),
                        PROFILE,
                        legacyCandidates);
        AutomaticWeaponPrerequisitePlan grouped =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        groupedDynamicSnapshot(),
                        catalog(addOns),
                        PROFILE,
                        groupedCandidates);

        assertEquals(PrerequisiteStrategy.LEGACY_AND, legacy.prerequisiteStrategy());
        assertEquals(
                PrerequisiteStrategy.GROUPED_ROUTES_V1,
                grouped.prerequisiteStrategy());
        assertEquals(legacy.prerequisites().keySet(), grouped.prerequisites().keySet());
        grouped.prerequisites().forEach((target, parents) -> assertEquals(
                legacy.prerequisitesFor(target).get(0),
                parents.get(0),
                "Phase 9 must preserve the primary route while reviewing only the OR alternative"));
        assertTrue(grouped.prerequisites().values().stream()
                .allMatch(parents -> parents.size() <= 2));
        assertTrue(grouped.prerequisites().values().stream()
                .anyMatch(parents -> parents.size() == 2));
        grouped.prerequisites().forEach((target, parents) -> {
            var requirements = grouped.requirementsFor(target);
            assertEquals(1, requirements.allOf().size());
            assertEquals(
                    Set.copyOf(parents),
                    Set.copyOf(requirements.allOf().get(0).anyOf()));
            assertEquals(
                    parents.size(),
                    legacy.requirementsFor(target).allOf().size());
        });
        assertTrue(legacy.decisions().values().stream()
                .allMatch(decision -> decision.alternativeRouteReview().isEmpty()));
        AutomaticWeaponPrerequisitePlan reconciled =
                grouped.withPublishedRanks(groupedCandidates);
        assertEquals(grouped.requirementGroups(), reconciled.requirementGroups(),
                "rank reconciliation must preserve canonical OR-group identity");
    }

    @Test
    void groupedRoutesAreIdenticalAcrossRepeatedRuns() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 287)
                .mapToObj(index -> id("repeatable:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                dynamicCandidates(addOns, true, PrerequisiteStrategy.GROUPED_ROUTES_V1);
        BlueprintResearchSnapshot research = groupedDynamicSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(addOns);

        AutomaticWeaponPrerequisitePlan first =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research, catalog, PROFILE, candidates);
        AutomaticWeaponPrerequisitePlan second =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research, catalog, PROFILE, candidates);

        assertEquals(first, second,
                "identical grouped-route inputs must publish the same complete plan");
        assertEquals(PrerequisiteStrategy.GROUPED_ROUTES_V1,
                first.prerequisiteStrategy());
        assertTrue(first.requirementGroups().values().stream().allMatch(requirements ->
                requirements.allOf().size() == 1
                        && requirements.allOf().get(0).anyOf().size() <= 2));
    }

    @Test
    void hybridRoutesPublishExplicitSparseAndOfOrGateways() {
        BranchPrerequisiteFixture fixture = branchFixture(
                List.of(42, 36, 30, 24),
                false,
                true,
                12,
                3,
                1,
                PrerequisiteStrategy.HYBRID_ROUTES_V1);

        AutomaticWeaponPrerequisitePlan first = branchPlan(fixture);
        AutomaticWeaponPrerequisitePlan second = branchPlan(fixture);

        assertEquals(first, second, "hybrid generation must be deterministic");
        assertEquals(PrerequisiteStrategy.HYBRID_ROUTES_V1,
                first.prerequisiteStrategy());
        assertTrue(first.decisions().values().stream().anyMatch(decision ->
                decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES));
        assertTrue(first.decisions().values().stream().anyMatch(decision ->
                decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY),
                "the deliberate gateway schedule must produce at least one mixed motif");

        first.prerequisites().forEach((target, parents) -> {
            AutomaticWeaponPrerequisiteDecision decision =
                    first.decisionFor(target).orElseThrow();
            assertEquals(
                    expectedHybridRequirements(
                            decision.generatedRequirementShape(), parents),
                    first.requirementsFor(target));
            for (int left = 0; left < parents.size(); left++) {
                for (int right = left + 1; right < parents.size(); right++) {
                    assertFalse(dependsOn(first, parents.get(left), parents.get(right)));
                    assertFalse(dependsOn(first, parents.get(right), parents.get(left)));
                }
            }
        });

        Map<Integer, Long> mixedByRank = first.decisions().values().stream()
                .filter(decision -> decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY)
                .peek(decision -> {
                    assertEquals(3, decision.selectedParentRelations().size());
                    assertTrue(decision.rankIndex() <= decision.transitionEndIndex());
                    assertTrue(decision.strategy()
                            != AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION);
                })
                .collect(java.util.stream.Collectors.groupingBy(
                        AutomaticWeaponPrerequisiteDecision::rankIndex,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        assertTrue(mixedByRank.values().stream().allMatch(count -> count == 1L),
                "hybrid gateways must be sparse and never global rank-wide joins");

        Set<ResourceLocation> referenced = first.prerequisites().values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toSet());
        List<ResourceLocation> terminals = automaticIds(fixture).stream()
                .filter(id -> !referenced.contains(id))
                .toList();
        assertTrue(terminals.size() > 1);
        Map<ResourceLocation, Set<ResourceLocation>> mandatoryMemo =
                new LinkedHashMap<>();
        LinkedHashSet<ResourceLocation> commonMandatory = null;
        for (ResourceLocation terminal : terminals) {
            Set<ResourceLocation> closure = mandatoryClosure(
                    first, terminal, mandatoryMemo, new LinkedHashSet<>());
            if (commonMandatory == null) {
                commonMandatory = new LinkedHashSet<>(closure);
            } else {
                commonMandatory.retainAll(closure);
            }
        }
        Set<ResourceLocation> stableCommon = commonMandatory == null
                ? Set.of() : Set.copyOf(commonMandatory);
        assertTrue(stableCommon.stream()
                .filter(id -> fixture.candidates().eligibleProposal(id).isPresent())
                .allMatch(id -> rank(fixture, id) == 0),
                "hybrid generation must not create a non-foundation global bottleneck");
    }

    @Test
    void groupedRoutesIgnoreMergeIntervalAcrossLegacyScoreLayers() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> id("grouped_interval:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementPolicy disabledInterval =
                groupedScorePolicy(0);
        AutomaticWeaponPlacementPolicy configuredInterval =
                groupedScorePolicy(1);
        AutomaticWeaponPrerequisitePlanner planner =
                new AutomaticWeaponPrerequisitePlanner();

        AutomaticWeaponPrerequisitePlan disabled = planner.plan(
                groupedDynamicSnapshot(),
                catalog(addOns),
                PROFILE,
                candidates(AutomaticPlacementMode.CONNECTED, addOns, disabledInterval));
        AutomaticWeaponPrerequisitePlan configured = planner.plan(
                groupedDynamicSnapshot(),
                catalog(addOns),
                PROFILE,
                candidates(AutomaticPlacementMode.CONNECTED, addOns, configuredInterval));

        assertEquals(disabled.prerequisites(), configured.prerequisites());
        assertEquals(disabled.requirementGroups(), configured.requirementGroups());
        assertEquals(
                AutomaticWeaponPlacementPolicy.MergeIntervalBehavior
                        .IGNORED_GROUPED_ROUTES_V1,
                configuredInterval.mergeIntervalBehavior());
    }

    @Test
    void configuredAutomaticEntryPointIsNeverGivenGeneratedParents() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> id("entry:weapon_" + index))
                .toList();
        ResourceLocation entryPoint = addOns.get(10);
        BlueprintResearchSnapshot research = snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profileWithEntryPoint(entryPoint)),
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of()));
        AutomaticWeaponPlacementCandidateSnapshot candidates = dynamicCandidates(addOns);

        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research,
                        catalog(addOns),
                        PROFILE,
                        candidates);

        assertEquals("entry_point", plan.omittedCandidates().get(entryPoint));
        assertTrue(plan.requirementsFor(entryPoint).allOf().isEmpty());
        assertEquals(addOns.size(),
                plan.prerequisites().size() + plan.omittedCandidates().size());
    }

    @Test
    void automaticAuthorityUsesItsConfiguredRootEvenWhenProfileListsAnEntryPoint() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 18)
                .mapToObj(index -> id("addon:fallback_foundation_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot baseCandidates =
                dynamicCandidates(addOns, false);
        AutomaticWeaponPlacementPolicy basePolicy = baseCandidates.policy();
        AutomaticWeaponPlacementPolicy singleFoundationPolicy =
                new AutomaticWeaponPlacementPolicy(
                        basePolicy.levelsPerTier(),
                        basePolicy.reviewConfidenceThreshold(),
                        basePolicy.reviewHandling(),
                        basePolicy.maxGeneratedPrerequisites(),
                        basePolicy.mergeInterval(),
                        basePolicy.layeringStrategy(),
                        basePolicy.maxNodesPerRank(),
                        basePolicy.progressionBands(),
                        1,
                        basePolicy.prerequisiteStrategy());
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        baseCandidates.treeId(),
                        baseCandidates.mode(),
                        singleFoundationPolicy,
                        baseCandidates.catalogRevision(),
                        baseCandidates.researchRevision(),
                        baseCandidates.catalogWeaponCount(),
                        new AutomaticWeaponLayerPlanner().assign(
                                baseCandidates.eligibleProposals(),
                                singleFoundationPolicy),
                        baseCandidates.excludedAutomaticCandidates(),
                        baseCandidates.authoredBlueprintIds(),
                        baseCandidates.unplacedBlueprintIds());
        List<ResourceLocation> foundations = candidates.eligibleProposals().entrySet().stream()
                .filter(entry -> entry.getValue().progressionCoordinate().rank() == 0)
                .map(entry -> id(entry.getKey()))
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
        assertEquals(1, foundations.size());
        ResourceLocation entryPoint = foundations.get(0);
        BlueprintResearchSnapshot research = snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profileWithEntryPoint(entryPoint)),
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of(),
                        1));

        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research,
                        automaticOnlyCatalog(addOns),
                        PROFILE,
                        candidates);

        assertEquals("generated_root", plan.omittedCandidates().get(entryPoint));
        assertEquals(Set.of(entryPoint), plan.omittedCandidates().keySet());
        foundations.stream()
                .filter(id -> !id.equals(entryPoint))
                .forEach(id -> assertEquals(
                        List.of(entryPoint),
                        plan.prerequisitesFor(id)));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates, List.of(plan));
        int entryRank = finalized.eligibleProposal(entryPoint).orElseThrow()
                .progressionCoordinate().rank();
        foundations.stream()
                .filter(id -> !id.equals(entryPoint))
                .forEach(id -> assertTrue(
                        finalized.eligibleProposal(id).orElseThrow()
                                .progressionCoordinate().rank() > entryRank));
    }

    @Test
    void deepDynamicTreesLeaveTheSharedMeshBeforePeriodicUpperMerges() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 74)
                .mapToObj(index -> id("deep_dynamic:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot candidates = dynamicCandidates(addOns);
        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        dynamicSnapshot(AutomaticPlacementMode.CONNECTED),
                        catalog(addOns),
                        PROFILE,
                        candidates);

        Map<Integer, List<ResourceLocation>> layers = candidates.eligibleProposals().entrySet()
                .stream().collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getValue().progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.mapping(
                                entry -> id(entry.getKey()),
                                java.util.stream.Collectors.toList())));
        layers.replaceAll((rank, ids) -> ids.stream()
                .sorted(java.util.Comparator.comparingLong(id -> candidates
                        .eligibleProposal(id).orElseThrow()
                        .progressionCoordinate().siblingOrder()))
                .toList());

        assertEquals(9, layers.size());
        assertEquals(3, ResearchTechTreeContract.sharedMeshTransitionCount(layers.size()));
        for (int rank = 1; rank <= 3; rank++) {
            assertTrue(layers.get(rank).stream()
                    .allMatch(id -> plan.prerequisitesFor(id).size() == 2));
        }
        for (int rank = 4; rank < layers.size(); rank++) {
            int currentRank = rank;
            assertTrue(layers.get(currentRank).stream()
                    .allMatch(id -> plan.prerequisitesFor(id).size() == 1),
                    () -> "upper rank " + currentRank
                            + " unexpectedly rejoined the shared mesh");
        }
        for (int index = 0; index < layers.get(4).size(); index++) {
            assertEquals(
                    layers.get(3).get(index),
                    plan.prerequisiteFor(layers.get(4).get(index)).orElseThrow(),
                    "specialization branches must retain their proportional parent affinity");
        }
    }

    @Test
    void dynamicStatLayersRetainGeneratedRootsWithoutAuthoredWeapons() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> id("automatic_only:weapon_" + index))
                .toList();
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                dynamicCandidates(addOns, false);

        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        dynamicSnapshot(AutomaticPlacementMode.CONNECTED),
                        automaticOnlyCatalog(addOns),
                        PROFILE,
                        candidates);

        List<ResourceLocation> foundations = candidates.eligibleProposals().entrySet().stream()
                .filter(entry -> entry.getValue().progressionCoordinate().rank() == 0)
                .map(entry -> id(entry.getKey()))
                .toList();
        assertEquals(Set.copyOf(foundations), plan.omittedCandidates().keySet());
        assertTrue(plan.omittedCandidates().values().stream()
                .allMatch("generated_root"::equals));
        assertTrue(plan.prerequisites().keySet().stream()
                .noneMatch(foundations::contains));
    }

    @Test
    void branchEvidenceProducesADenseTrunkGradualMergesAndLocalUpperPaths() {
        BranchPrerequisiteFixture fixture = branchFixture(
                List.of(72, 36, 18), false, false, 20);
        AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);
        int occupiedRanks = occupiedRankCount(fixture.candidates());
        int familyStart = AutomaticWeaponPrerequisitePlanner.branchFamilyStartIndex(
                fixture.candidates().eligibleProposals().size(),
                fixture.candidates().policy(),
                occupiedRanks);
        int transitionEnd = AutomaticWeaponPrerequisitePlanner.branchTransitionEndIndex(
                occupiedRanks, familyStart);

        List<ResourceLocation> trunk = automaticIds(fixture).stream()
                .filter(id -> {
                    int rank = rank(fixture, id);
                    return rank > 0 && rank < familyStart;
                })
                .toList();
        assertFalse(trunk.isEmpty());
        assertTrue(trunk.stream().allMatch(id ->
                plan.decisionFor(id).orElseThrow().desiredParentCount() == 2));
        assertTrue(trunk.stream().allMatch(id ->
                plan.prerequisitesFor(id).size() >= 1
                        && plan.prerequisitesFor(id).size() <= 2));
        assertTrue(trunk.stream().allMatch(id -> plan.decisionFor(id).orElseThrow()
                .strategy() == AutomaticWeaponPrerequisiteDecision.Strategy.SHARED_TRUNK));
        assertTrue(trunk.stream().anyMatch(id -> plan.prerequisitesFor(id).stream()
                .anyMatch(parent -> branch(fixture, parent) != branch(fixture, id))),
                "the shared trunk should retain cross-family routes");
        assertTrue(trunk.stream().anyMatch(id ->
                plan.decisionFor(id).orElseThrow().crossFamilyParentCount() > 0));

        List<ResourceLocation> transitionStart = automaticIds(fixture).stream()
                .filter(id -> rank(fixture, id) == familyStart)
                .toList();
        assertFalse(transitionStart.isEmpty());
        assertTrue(transitionStart.stream().allMatch(id ->
                plan.prerequisitesFor(id).size() == 2));
        assertTrue(transitionStart.stream().allMatch(id -> plan.decisionFor(id).orElseThrow()
                .strategy() == AutomaticWeaponPrerequisiteDecision.Strategy
                        .TRANSITION_CROSS_FAMILY));
        assertTrue(transitionStart.stream().anyMatch(id ->
                plan.prerequisitesFor(id).stream().anyMatch(parent ->
                        branch(fixture, parent) != branch(fixture, id))));

        assertTrue(automaticIds(fixture).stream().anyMatch(id -> {
            int targetRank = rank(fixture, id);
            List<ResourceLocation> parents = plan.prerequisitesFor(id);
            return targetRank > familyStart && targetRank <= transitionEnd
                    && parents.size() == 2
                    && parents.stream().allMatch(parent ->
                            branch(fixture, parent) == branch(fixture, id));
        }), "the later transition should retain branch-local simultaneous requirements");
        assertTrue(automaticIds(fixture).stream().anyMatch(id -> {
            AutomaticWeaponPrerequisiteDecision decision = plan.decisionFor(id).orElseThrow();
            return decision.strategy()
                    == AutomaticWeaponPrerequisiteDecision.Strategy.TRANSITION_LOCAL
                    && decision.sameFamilyParentCount() == 2;
        }));

        double firstMergeAverage = transitionStart.stream()
                .mapToInt(id -> plan.prerequisitesFor(id).size())
                .average().orElseThrow();
        double finalMergeAverage = automaticIds(fixture).stream()
                .filter(id -> rank(fixture, id) == transitionEnd)
                .mapToInt(id -> plan.prerequisitesFor(id).size())
                .average().orElseThrow();
        assertTrue(firstMergeAverage > finalMergeAverage,
                "merge density should taper instead of ending at one hard cutoff");

        List<ResourceLocation> specialization = automaticIds(fixture).stream()
                .filter(id -> rank(fixture, id) > transitionEnd)
                .toList();
        assertFalse(specialization.isEmpty());
        assertTrue(specialization.stream().allMatch(id -> {
            List<ResourceLocation> parents = plan.prerequisitesFor(id);
            return parents.size() >= 1 && parents.size() <= 2
                    && parents.stream().allMatch(parent ->
                            branch(fixture, parent) == branch(fixture, id))
                    && plan.decisionFor(id).orElseThrow().strategy()
                            == AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION
                    && plan.decisionFor(id).orElseThrow()
                            .secondParentQuotaBasisPoints() == 2_000;
        }));
        assertTrue(specialization.stream().anyMatch(id ->
                plan.prerequisitesFor(id).size() == 2),
                "specialized branches should retain sparse local convergence");
        assertTrue(specialization.stream().anyMatch(id ->
                plan.prerequisitesFor(id).size() == 1),
                "specialized branches should remain primarily single-parent");
        assertEquals(fixture.candidates().eligibleProposals().size(), plan.decisions().size());
        assertEquals(
                fixture.candidates().eligibleProposals().size(),
                plan.branchCoordinates().size());
        assertTrue(plan.branchCoordinates().entrySet().stream().allMatch(entry ->
                entry.getValue().branchIndex() == branch(fixture, entry.getKey())));
        Map<Integer, List<AutomaticWeaponPrerequisiteDecision>> decisionsByRank =
                plan.decisions().values().stream().collect(
                        java.util.stream.Collectors.groupingBy(
                                AutomaticWeaponPrerequisiteDecision::rankIndex));
        decisionsByRank.forEach((rankIndex, rankDecisions) -> {
            if (rankIndex == 0) {
                return;
            }
            int quota = AutomaticWeaponPrerequisitePlanner
                    .secondParentQuotaBasisPoints(
                            rankIndex, familyStart, transitionEnd);
            int expected = Math.floorDiv(
                    rankDecisions.size() * quota + 5_000,
                    10_000);
            assertEquals(expected, rankDecisions.stream()
                    .filter(AutomaticWeaponPrerequisiteDecision::secondParentEligible)
                    .count());
        });
    }

    @Test
    void secondParentMaturityCurveDeclinesContinuouslyThenRetainsATerminalFloor() {
        assertEquals(10_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(1, 2, 6));
        assertEquals(10_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(2, 2, 6));
        assertEquals(8_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(3, 2, 6));
        assertEquals(6_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(4, 2, 6));
        assertEquals(4_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(5, 2, 6));
        assertEquals(2_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(6, 2, 6));
        assertEquals(2_000,
                AutomaticWeaponPrerequisitePlanner.secondParentQuotaBasisPoints(9, 2, 6));
    }

    @Test
    void closureGuardAllowsLowCostRootsAndRejectsInflatedDisjointPaths() {
        assertTrue(AutomaticWeaponPrerequisitePlanner.closureInflationAllowed(
                8L, 8L, 16L, 8L));
        assertFalse(AutomaticWeaponPrerequisitePlanner.closureInflationAllowed(
                8L, 8L, 16L, 0L));
        assertTrue(AutomaticWeaponPrerequisitePlanner.closureInflationAllowed(
                40L, 40L, 60L, 8L));
        assertFalse(AutomaticWeaponPrerequisitePlanner.closureInflationAllowed(
                40L, 40L, 61L, 8L));
        assertFalse(AutomaticWeaponPrerequisitePlanner.closureInflationAllowed(
                40L, 35L, 70L, 8L));
    }

    @Test
    void stratifiedQuotaSelectsAnExactStableCountAcrossBranches() {
        List<ResourceLocation> targets = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> id("test:quota/" + index))
                .toList();
        Map<ResourceLocation, Integer> branches = new LinkedHashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            branches.put(targets.get(index), index < 5 ? 0 : index < 9 ? 1 : 2);
        }

        Set<ResourceLocation> selected = AutomaticWeaponPrerequisitePlanner
                .stratifiedQuotaSelection(targets, branches, 2_000);
        List<ResourceLocation> reversed = new ArrayList<>(targets);
        java.util.Collections.reverse(reversed);

        assertEquals(2, selected.size());
        assertEquals(selected, AutomaticWeaponPrerequisitePlanner
                .stratifiedQuotaSelection(reversed, branches, 2_000));
        assertEquals(2, selected.stream().map(branches::get).distinct().count());
        assertEquals(Set.copyOf(targets), AutomaticWeaponPrerequisitePlanner
                .stratifiedQuotaSelection(targets, branches, 10_000));
    }

    @Test
    void mergeIntervalOnlySchedulesOptionalThirdParents() {
        BranchPrerequisiteFixture periodic = branchFixture(
                List.of(72, 36, 18), false, false, 20, 3, 4);
        AutomaticWeaponPrerequisitePlan periodicPlan = branchPlan(periodic);
        assertTrue(periodicPlan.decisions().values().stream()
                .anyMatch(decision -> decision.desiredParentCount() == 3));
        assertTrue(periodicPlan.decisions().values().stream()
                .filter(decision -> decision.desiredParentCount() == 3)
                .allMatch(decision -> decision.rankIndex() > 0
                        && decision.rankIndex() % 4 == 0
                        && decision.secondParentEligible()));
        assertTrue(periodicPlan.decisions().values().stream()
                .anyMatch(decision -> decision.desiredParentCount() == 2
                        && decision.rankIndex() % 4 != 0));

        BranchPrerequisiteFixture disabled = branchFixture(
                List.of(72, 36, 18), false, false, 20, 3, 0);
        AutomaticWeaponPrerequisitePlan disabledPlan = branchPlan(disabled);
        assertTrue(disabledPlan.decisions().values().stream()
                .noneMatch(decision -> decision.desiredParentCount() == 3));
        assertTrue(disabledPlan.decisions().values().stream()
                .anyMatch(decision -> decision.desiredParentCount() == 2));
    }

    @Test
    void branchTerminalPeersShareAnApexWithoutBeingForcedIntoAChain() {
        BranchPrerequisiteFixture fixture = branchFixture(
                List.of(72, 36, 18), false, false, 20);
        AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);
        assertTrue(fixture.classification().branchModel().branches().stream()
                .anyMatch(branch -> branch.terminalBlueprintIds().size() > 1));

        fixture.classification().branchModel().branches().forEach(branch -> {
            List<ResourceLocation> terminals = branch.terminalBlueprintIds().stream()
                    .map(AutomaticWeaponPrerequisitePlannerTest::id)
                    .toList();
            if (terminals.isEmpty()) {
                return;
            }
            int apexRank = rank(fixture, terminals.get(0));
            assertTrue(terminals.stream().allMatch(id -> rank(fixture, id) == apexRank));
            assertTrue(terminals.stream().allMatch(id ->
                    !plan.prerequisitesFor(id).isEmpty()
                            && plan.prerequisitesFor(id).stream()
                                    .noneMatch(terminals::contains)
                            && plan.prerequisitesFor(id).stream()
                                    .allMatch(parent -> rank(fixture, parent) < apexRank)
                            && plan.decisionFor(id).orElseThrow().terminalPeer()));
        });
    }

    @Test
    void branchFoundationsPreferMatchingAuthoredRoleAnchors() {
        BranchPrerequisiteFixture fixture = branchFixture(
                List.of(48, 24, 12), false, true, 20);
        AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);
        List<ResourceLocation> anchoredFoundations = automaticIds(fixture).stream()
                .filter(id -> rank(fixture, id) == 0)
                .filter(id -> !fixture.classification().branchModel().branches()
                        .get(branch(fixture, id)).authoredAnchorBlueprintIds().isEmpty())
                .toList();

        assertFalse(anchoredFoundations.isEmpty());
        assertTrue(anchoredFoundations.stream().allMatch(id -> {
            Set<String> anchors = Set.copyOf(fixture.classification().branchModel().branches()
                    .get(branch(fixture, id)).authoredAnchorBlueprintIds());
            return plan.prerequisiteFor(id)
                    .map(ResourceLocation::toString)
                    .filter(anchors::contains)
                    .isPresent()
                    && plan.decisionFor(id).orElseThrow().selectedParentRelations().values()
                            .stream().allMatch(relation -> relation
                                    == AutomaticWeaponPrerequisiteDecision.ParentRelation
                                            .AUTHORED_SAME_FAMILY);
        }));
    }

    @Test
    void reportedCatalogScaleIsDeterministicReachableAndGraduallySeparated() {
        BranchPrerequisiteFixture forward = branchFixture(
                List.of(180, 60, 30, 17), false, false, 20);
        BranchPrerequisiteFixture reverse = branchFixture(
                List.of(180, 60, 30, 17), true, false, 20);
        AutomaticWeaponPrerequisitePlan first = branchPlan(forward);
        AutomaticWeaponPrerequisitePlan second = branchPlan(reverse);

        assertEquals(287, forward.candidates().eligibleProposals().size());
        assertEquals(first.prerequisites(), second.prerequisites());
        assertEquals(first.omittedCandidates(), second.omittedCandidates());
        assertEquals(first.decisions(), second.decisions());
        assertEquals(first.branchCoordinates(), second.branchCoordinates());
        assertTrue(first.prerequisites().values().stream().allMatch(parents ->
                !parents.isEmpty()
                        && parents.size() <= AutomaticWeaponPlacementPolicy
                                .MAX_GENERATED_PREREQUISITES));
        Set<ResourceLocation> foundations = automaticIds(forward).stream()
                .filter(id -> rank(forward, id) == 0)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(foundations, first.omittedCandidates().keySet());
        assertTrue(first.omittedCandidates().values().stream()
                .allMatch("generated_root"::equals));
        Map<ResourceLocation, Boolean> reachable = new LinkedHashMap<>();
        assertTrue(automaticIds(forward).stream().allMatch(id ->
                reachesAnyFoundation(
                        first, id, foundations,
                        new java.util.LinkedHashSet<>(), reachable)));

        int occupiedRanks = occupiedRankCount(forward.candidates());
        int familyStart = AutomaticWeaponPrerequisitePlanner.branchFamilyStartIndex(
                forward.candidates().eligibleProposals().size(),
                forward.candidates().policy(),
                occupiedRanks);
        int transitionEnd = AutomaticWeaponPrerequisitePlanner.branchTransitionEndIndex(
                occupiedRanks, familyStart);
        assertTrue(automaticIds(forward).stream()
                .filter(id -> rank(forward, id) > transitionEnd)
                .allMatch(id -> first.prerequisitesFor(id).stream().allMatch(parent ->
                        separatedParentIsLocalOrEmergency(forward, id, parent))));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        forward.candidates(), List.of(first));
        AutomaticWeaponPrerequisitePlan published = first.withPublishedRanks(finalized);
        assertTrue(published.prerequisites().entrySet().stream().allMatch(entry ->
                entry.getValue().stream().allMatch(parent ->
                        publishedRank(finalized, entry.getKey())
                                - publishedRank(finalized, parent)
                                <= ResearchTechTreeContract.MAX_AUTOMATIC_EDGE_RANK_SPAN)));
        AutomaticWeaponPlacementDiagnostics.PublicationSummary publication =
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, finalized, published).publicationSummary();
        assertEquals(287, publication.candidateCount());
        assertEquals(287, publication.canonicalBranchCoordinateCount());
        assertEquals(287, publication.prerequisiteDecisionCount());
        assertEquals(287, publication.publishedRankCount());
        assertEquals(10_000, publication.canonicalBranchCoverageBasisPoints());
        assertEquals(10_000, publication.publishedRankCoverageBasisPoints());
        assertTrue(publication.complete());
    }

    @Test
    void reportedMixedCatalogScaleRetainsLandscapeRankDensity() {
        BranchPrerequisiteFixture fixture = branchFixture(
                List.of(40, 35, 30, 28, 25, 22, 18, 15, 12, 9),
                false,
                true,
                20);
        AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);
        LinkedHashSet<String> authoredIds = new LinkedHashSet<>();
        authoredIds.add(ROOT_A.toString());
        authoredIds.add(ROOT_B.toString());
        for (int index = 2; index < 53; index++) {
            authoredIds.add("test:mixed_authored_" + index);
        }
        List<Integer> authoredRankWidths = List.of(
                2, 4, 5, 6, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1);
        Map<String, Integer> authoredRanks = new LinkedHashMap<>();
        int authoredCursor = 0;
        List<String> orderedAuthored = List.copyOf(authoredIds);
        for (int rank = 0; rank < authoredRankWidths.size(); rank++) {
            for (int index = 0; index < authoredRankWidths.get(rank); index++) {
                authoredRanks.put(orderedAuthored.get(authoredCursor++), rank);
            }
        }
        AutomaticWeaponPlacementCandidateSnapshot mixed =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        fixture.candidates().treeId(),
                        fixture.candidates().mode(),
                        fixture.candidates().policy(),
                        fixture.candidates().catalogRevision(),
                        fixture.candidates().researchRevision(),
                        287,
                        fixture.candidates().eligibleProposals(),
                        fixture.candidates().excludedAutomaticCandidates(),
                        authoredIds,
                        Set.of());

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        mixed,
                        List.of(plan),
                        Map.of(PROFILE, authoredRanks));
        Map<Integer, Long> combinedWidths = new java.util.TreeMap<>();
        finalized.eligibleProposals().values().forEach(value -> combinedWidths.merge(
                value.progressionCoordinate().rank(), 1L, Math::addExact));
        authoredRanks.values().forEach(rank -> combinedWidths.merge(
                rank, 1L, Math::addExact));

        assertEquals(287, combinedWidths.values().stream()
                .mapToLong(Long::longValue).sum());
        assertTrue(combinedWidths.size() <= 22,
                "the 287-node mixed topology should remain landscape-biased");
        assertEquals(20L, combinedWidths.values().stream()
                .mapToLong(Long::longValue).max().orElseThrow());
        assertTrue(combinedWidths.entrySet().stream()
                .filter(entry -> entry.getKey() >= 2 && entry.getKey() <= 18)
                .mapToLong(Map.Entry::getValue)
                .average().orElseThrow() >= 14.0D,
                "the shared and transitional body should retain broad ranks");
    }

    @Test
    void branchAwareMaximumCatalogUsesBoundedLocalParentsWithoutPlanOmissions() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            BranchPrerequisiteFixture fixture = branchFixture(
                    List.of(4096), false, false, 20);
            AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);
            Set<ResourceLocation> foundations = automaticIds(fixture).stream()
                    .filter(id -> rank(fixture, id) == 0)
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(4096, plan.candidateCount());
            assertEquals(
                    foundations,
                    plan.omittedCandidates().keySet(),
                    plan.omittedCandidates().toString());
            assertTrue(plan.omittedCandidates().values().stream()
                    .allMatch("generated_root"::equals));
            assertTrue(plan.prerequisites().values().stream().allMatch(parents ->
                    !parents.isEmpty()
                            && parents.size() <= AutomaticWeaponPlacementPolicy
                                    .MAX_GENERATED_PREREQUISITES));
            assertTrue(plan.prerequisites().entrySet().stream().allMatch(entry ->
                    entry.getValue().stream().allMatch(parent ->
                            occupiedRankDistance(fixture, entry.getKey(), parent)
                                    <= ResearchTechTreeContract.automaticEdgeRankSpanLimit(
                                            occupiedRankCount(fixture.candidates()),
                                            BlueprintResearchSnapshot
                                                    .MAX_PREREQUISITE_DEPTH))));
            Map<ResourceLocation, Boolean> reachable = new LinkedHashMap<>();
            assertTrue(automaticIds(fixture).stream().allMatch(id ->
                    reachesAnyFoundation(plan, id, foundations,
                            new java.util.LinkedHashSet<>(), reachable)));
        });
    }

    @Test
    void groupedRoutesRemainBoundedAtMaximumCatalogPopulation() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            BranchPrerequisiteFixture fixture = branchFixture(
                    List.of(4096),
                    false,
                    false,
                    20,
                    2,
                    4,
                    PrerequisiteStrategy.GROUPED_ROUTES_V1);
            AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);

            assertEquals(4096, plan.candidateCount());
            assertEquals(PrerequisiteStrategy.GROUPED_ROUTES_V1,
                    plan.prerequisiteStrategy());
            plan.requirementGroups().forEach((target, requirements) -> {
                assertEquals(1, requirements.allOf().size());
                assertTrue(requirements.allOf().get(0).anyOf().size() <= 2);
                assertEquals(
                        Set.copyOf(plan.prerequisitesFor(target)),
                        Set.copyOf(requirements.allOf().get(0).anyOf()));
            });
            assertEquals(
                    4096,
                    plan.requirementGroups().size() + plan.omittedCandidates().size());
            assertTrue(plan.decisions().values().stream()
                    .anyMatch(decision -> decision.alternativeRouteReview().isPresent()));
            assertTrue(plan.decisions().values().stream()
                    .filter(decision -> decision.alternativeRouteReview().isPresent())
                    .allMatch(decision -> decision.mergeRejection().isEmpty()));
        });
    }

    @Test
    void reviewHandlingSeparatesPlacementFromPrerequisiteAuthority() {
        ResourceLocation reviewed = id("addon:reviewed_weapon");
        Map<ResourceLocation, BlueprintData> catalog = catalog(List.of(reviewed));
        BlueprintResearchSnapshot research = snapshot(AutomaticPlacementMode.CONNECTED);

        AutomaticWeaponPrerequisitePlan independent =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research,
                        catalog,
                        PROFILE,
                        reviewedCandidates(
                                reviewed,
                                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_INDEPENDENT));
        assertTrue(independent.prerequisites().isEmpty());
        assertEquals("review_policy_independent",
                independent.omittedCandidates().get(reviewed));

        AutomaticWeaponPrerequisitePlan connected =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research,
                        catalog,
                        PROFILE,
                        reviewedCandidates(
                                reviewed,
                                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED));
        assertEquals(1, connected.prerequisites().size());
        assertTrue(Set.of(ROOT_A, ROOT_B).contains(
                connected.prerequisiteFor(reviewed).orElseThrow()));
        assertTrue(connected.omittedCandidates().isEmpty());
    }

    @Test
    void canonicalDynamicIndependentReviewRetainsCompletePublicationEvidence() {
        BranchPrerequisiteFixture base = branchFixture(
                List.of(12, 8), false, false, 9);
        ResourceLocation reviewed = automaticIds(base).stream()
                .max(java.util.Comparator.comparingInt(id -> rank(base, id)))
                .orElseThrow();
        BranchPrerequisiteFixture fixture = withIndependentReview(base, reviewed);

        AutomaticWeaponPrerequisitePlan plan = branchPlan(fixture);

        assertEquals("review_policy_independent",
                plan.omittedCandidates().get(reviewed));
        assertTrue(plan.decisionFor(reviewed).isPresent());
        assertTrue(plan.decisionFor(reviewed).orElseThrow()
                .selectedParentRelations().isEmpty());
        assertTrue(plan.branchCoordinateFor(reviewed).isPresent());

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        fixture.candidates(), List.of(plan));
        AutomaticWeaponPrerequisitePlan published =
                plan.withPublishedRanks(finalized);
        assertTrue(AutomaticWeaponPlacementDiagnostics.create(
                PROFILE, finalized, published).publicationSummary().complete());
        new AutomaticWeaponPlacementCandidateManager.Publication(
                5L,
                7L,
                Map.of(TREE, fixture.classification()),
                Map.of(TREE, finalized),
                Map.of(PROFILE, published),
                1L);
    }

    @Test
    void independentlyPlacedReviewedWeaponNeverBecomesALaterAnchor() {
        ResourceLocation reviewed = id("addon:reviewed_weapon");
        ResourceLocation regular = id("addon:regular_weapon");
        List<ResourceLocation> addOns = List.of(reviewed, regular);
        AutomaticWeaponPlacementCandidateSnapshot candidates = mixedReviewCandidates(
                reviewed, regular);

        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        snapshot(AutomaticPlacementMode.CONNECTED),
                        catalog(addOns),
                        PROFILE,
                        candidates);

        assertEquals("review_policy_independent",
                plan.omittedCandidates().get(reviewed));
        assertFalse(plan.prerequisitesFor(regular).contains(reviewed));
        assertTrue(plan.prerequisitesFor(regular).stream()
                .allMatch(Set.of(ROOT_A, ROOT_B)::contains));

        AutomaticWeaponPrerequisitePlan invalidPublicationPlan =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        2,
                        Map.of(regular, List.of(reviewed)),
                        Map.of(reviewed, "review_policy_independent"));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateManager.Publication(
                        5L,
                        7L,
                        Map.of(TREE, candidates),
                        Map.of(PROFILE, invalidPublicationPlan),
                        1L));
    }

    @Test
    void generatedEdgesFailOpenWhenTheGlobalGraphBudgetIsExhausted() {
        ResourceLocation addOn = id("addon:weapon");
        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner(0).plan(
                        snapshot(AutomaticPlacementMode.CONNECTED),
                        catalog(List.of(addOn)),
                        PROFILE,
                        candidates(AutomaticPlacementMode.CONNECTED, List.of(addOn)));

        assertTrue(plan.prerequisites().isEmpty());
        assertEquals("maximum_total_prerequisites",
                plan.omittedCandidates().get(addOn));
    }

    @Test
    void prerequisitePlanPreservesThePlannersPrimaryAnchorOrder() {
        ResourceLocation target = id("addon:target");
        ResourceLocation primary = id("test:z_primary");
        ResourceLocation secondary = id("test:a_secondary");
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                5L,
                7L,
                1,
                Map.of(target, List.of(primary, secondary)),
                Map.of());

        assertEquals(List.of(primary, secondary), plan.prerequisitesFor(target));
        assertEquals(primary, plan.prerequisiteFor(target).orElseThrow());
    }

    @Test
    void publicationRejectsModeMismatchesAndUnknownGeneratedAnchors() {
        ResourceLocation addOn = id("addon:weapon");
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                AutomaticPlacementMode.CONNECTED, List.of(addOn));
        AutomaticWeaponPrerequisitePlan wrongMode =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.DISTRIBUTED,
                        5L,
                        7L,
                        1,
                        Map.of(),
                        Map.of(addOn, "mode_does_not_create_prerequisites"));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateManager.Publication(
                        5L,
                        7L,
                        Map.of(TREE, candidates),
                        Map.of(PROFILE, wrongMode),
                        1L));

        AutomaticWeaponPrerequisitePlan unknownAnchor =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        1,
                        Map.of(addOn, List.of(id("missing:anchor"))),
                        Map.of());
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateManager.Publication(
                        5L,
                        7L,
                        Map.of(TREE, candidates),
                        Map.of(PROFILE, unknownAnchor),
                        1L));
    }

    @Test
    void connectedModeHandlesTheMaximumCatalogDeterministicallyWithinBudget() {
        List<ResourceLocation> addOns = java.util.stream.IntStream.range(0, 4094)
                .mapToObj(index -> id("large_pack:weapon_" + index))
                .toList();
        Map<ResourceLocation, BlueprintData> catalog = catalog(addOns);
        BlueprintResearchSnapshot research = snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profile(), PROFILE_B, profile()));
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                AutomaticPlacementMode.CONNECTED, addOns);

        assertTimeout(Duration.ofSeconds(10), () -> {
            AutomaticWeaponPrerequisitePlan first =
                    new AutomaticWeaponPrerequisitePlanner().plan(
                            research, catalog, PROFILE, candidates);
            AutomaticWeaponPrerequisitePlan second =
                    new AutomaticWeaponPrerequisitePlanner().plan(
                            research, catalog, PROFILE_B, candidates);

            assertEquals(4094, first.prerequisites().size());
            assertTrue(first.omittedCandidates().isEmpty());
            assertEquals(first.prerequisites(), second.prerequisites());
            assertEquals(first.omittedCandidates(), second.omittedCandidates());
            assertTrue(first.prerequisites().values().stream()
                    .allMatch(value -> !value.isEmpty()
                            && value.size() <= AutomaticWeaponPlacementPolicy
                                    .MAX_GENERATED_PREREQUISITES));
        });
    }

    @Test
    void prerequisitePlanRejectsAnOversizedDirectConstruction() {
        Map<ResourceLocation, String> omitted = java.util.stream.IntStream.range(0, 4097)
                .mapToObj(index -> id("large_pack:weapon_" + index))
                .collect(java.util.stream.Collectors.toMap(
                        value -> value,
                        ignored -> "no_earlier_anchor",
                        (left, right) -> left,
                        LinkedHashMap::new));

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        4097,
                        Map.of(),
                        omitted));
    }

    @Test
    void onePlanDrivesTreeJournalAndBlockedAnchorFailOpenBehavior() {
        ResourceLocation addOn = id("addon:weapon");
        Map<ResourceLocation, BlueprintData> catalog = catalog(List.of(addOn));
        BlueprintResearchSnapshot research = dynamicSnapshot(
                AutomaticPlacementMode.CONNECTED);
        AutomaticWeaponPlacementCandidateSnapshot candidates = dynamicCandidates(
                List.of(ROOT_A, ROOT_B, addOn), false);
        AutomaticWeaponPrerequisitePlan plan =
                new AutomaticWeaponPrerequisitePlanner().plan(
                        research, catalog, PROFILE, candidates);
        List<ResourceLocation> prerequisites = plan.prerequisitesFor(addOn);
        assertFalse(prerequisites.isEmpty());

        PlayerRecipeData player = new PlayerRecipeData();
        player.setResearchPoints(100);
        var publication = ResearchTreeBuilder.buildPublication(
                catalog,
                research,
                config(),
                player,
                ignored -> false,
                candidates,
                plan);
        assertEquals(prerequisites.size(), publication.graph().edges().size());
        assertTrue(publication.graph().edges().stream().allMatch(edge ->
                edge.dependentId().equals(addOn)
                        && prerequisites.contains(edge.prerequisiteId())));
        assertEquals(ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED,
                publication.graph().node(addOn).orElseThrow().availability());

        var journal = BlueprintJournalBuilder.build(
                catalog, research, config(), player, ignored -> false, plan);
        var journalEntry = journal.entries().stream()
                .filter(entry -> entry.blueprintId().filter(addOn::equals).isPresent())
                .findFirst().orElseThrow();
        assertFalse(journalEntry.researchable());
        assertEquals(prerequisites.size(), journalEntry.prerequisiteCount());

        prerequisites.forEach(value -> player.addBlueprint(value.toString()));
        var unlocked = BlueprintJournalBuilder.build(
                catalog, research, config(), player, ignored -> false, plan);
        assertTrue(unlocked.entries().stream()
                .filter(entry -> entry.blueprintId().filter(addOn::equals).isPresent())
                .findFirst().orElseThrow().researchable());

        var blocked = ResearchTreeBuilder.buildPublication(
                catalog,
                research,
                config(),
                new PlayerRecipeData(),
                value -> prerequisites.stream().map(ResourceLocation::toString)
                        .anyMatch(value::equals),
                candidates,
                plan);
        assertTrue(blocked.graph().edges().isEmpty());
        assertEquals(ResearchTreeGraph.Availability.AVAILABLE,
                blocked.graph().node(addOn).orElseThrow().availability());
    }

    private static BranchPrerequisiteFixture branchFixture(
            List<Integer> branchSizes,
            boolean reverse,
            boolean includeAuthored,
            int width) {
        return branchFixture(
                branchSizes,
                reverse,
                includeAuthored,
                width,
                2,
                4,
                PrerequisiteStrategy.LEGACY_AND);
    }

    private static BranchPrerequisiteFixture branchFixture(
            List<Integer> branchSizes,
            boolean reverse,
            boolean includeAuthored,
            int width,
            int maxGeneratedPrerequisites,
            int mergeInterval) {
        return branchFixture(
                branchSizes,
                reverse,
                includeAuthored,
                width,
                maxGeneratedPrerequisites,
                mergeInterval,
                PrerequisiteStrategy.LEGACY_AND);
    }

    private static BranchPrerequisiteFixture branchFixture(
            List<Integer> branchSizes,
            boolean reverse,
            boolean includeAuthored,
            int width,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            PrerequisiteStrategy prerequisiteStrategy) {
        List<AutomaticWeaponRoleSignature> values = new ArrayList<>();
        int sequence = 0;
        for (int family = 0; family < branchSizes.size(); family++) {
            int count = branchSizes.get(family);
            for (int index = 0; index < count; index++) {
                String blueprintId = "addon:family_" + family + "_weapon_" + index;
                int score = Math.min(
                        ResearchTechTreeContract.SCORE_MAX,
                        8 + family * 10
                                + index * (76 - family * 6) / Math.max(1, count - 1));
                values.add(branchSignature(
                        blueprintId,
                        score,
                        -75 + family * 150 / Math.max(1, branchSizes.size() - 1),
                        "family_" + family,
                        sequence++));
            }
        }
        if (reverse) {
            Collections.reverse(values);
        }
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        Map<String, AutomaticWeaponPlacementProposal> raw = new LinkedHashMap<>();
        values.forEach(signature -> {
            signatures.put(signature.blueprintId(), signature);
            raw.put(signature.blueprintId(), rawProposal(signature));
        });

        Map<String, AutomaticWeaponRoleSignature> authored = includeAuthored
                ? Map.of(
                        ROOT_A.toString(), branchSignature(
                                ROOT_A.toString(), 5, -75, "family_0", -2),
                        ROOT_B.toString(), branchSignature(
                                ROOT_B.toString(), 7, 75,
                                "family_" + (branchSizes.size() - 1), -1))
                : Map.of();
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                maxGeneratedPrerequisites,
                mergeInterval,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                width,
                List.of(),
                2,
                prerequisiteStrategy);
        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer().discover(
                signatures,
                authored,
                AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(width));
        int catalogCount = Math.addExact(raw.size(), authored.size());
        AutomaticWeaponCandidateClassification classification =
                new AutomaticWeaponCandidateClassification(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        5L,
                        7L,
                        catalogCount,
                        raw,
                        signatures,
                        authored,
                        model,
                        Map.of(),
                        authored.keySet(),
                        Set.of());
        Map<String, AutomaticWeaponPlacementProposal> positioned =
                new AutomaticWeaponBranchLayerPlanner().assign(
                        raw, signatures, authored, model, policy);
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        5L,
                        7L,
                        catalogCount,
                        positioned,
                        Map.of(),
                        authored.keySet(),
                        Set.of());
        List<ResourceLocation> ids = raw.keySet().stream()
                .map(AutomaticWeaponPrerequisitePlannerTest::id)
                .toList();
        Map<ResourceLocation, BlueprintData> catalog = includeAuthored
                ? catalog(ids)
                : automaticOnlyCatalog(ids);
        return new BranchPrerequisiteFixture(classification, candidates, catalog);
    }

    private static BranchPrerequisiteFixture withIndependentReview(
            BranchPrerequisiteFixture fixture,
            ResourceLocation reviewed) {
        AutomaticWeaponPlacementPolicy source = fixture.candidates().policy();
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                source.levelsPerTier(),
                source.reviewConfidenceThreshold(),
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_INDEPENDENT,
                source.maxGeneratedPrerequisites(),
                source.mergeInterval(),
                source.layeringStrategy(),
                source.maxNodesPerRank(),
                source.progressionBands(),
                source.foundationCount());
        Map<String, AutomaticWeaponPlacementProposal> raw = new LinkedHashMap<>(
                fixture.classification().eligibleProposals());
        raw.put(reviewed.toString(), withReview(raw.get(reviewed.toString())));
        AutomaticWeaponCandidateClassification classification =
                new AutomaticWeaponCandidateClassification(
                        fixture.classification().treeId(),
                        fixture.classification().mode(),
                        policy,
                        fixture.classification().catalogRevision(),
                        fixture.classification().researchRevision(),
                        fixture.classification().catalogWeaponCount(),
                        raw,
                        fixture.classification().roleSignatures(),
                        fixture.classification().authoredRoleSignatures(),
                        fixture.classification().branchModel(),
                        fixture.classification().excludedAutomaticCandidates(),
                        fixture.classification().authoredBlueprintIds(),
                        fixture.classification().unplacedBlueprintIds());
        Map<String, AutomaticWeaponPlacementProposal> positioned = new LinkedHashMap<>(
                fixture.candidates().eligibleProposals());
        positioned.put(
                reviewed.toString(),
                withReview(positioned.get(reviewed.toString())));
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        fixture.candidates().treeId(),
                        fixture.candidates().mode(),
                        policy,
                        fixture.candidates().catalogRevision(),
                        fixture.candidates().researchRevision(),
                        fixture.candidates().catalogWeaponCount(),
                        positioned,
                        fixture.candidates().excludedAutomaticCandidates(),
                        fixture.candidates().authoredBlueprintIds(),
                        fixture.candidates().unplacedBlueprintIds());
        return new BranchPrerequisiteFixture(
                classification, candidates, fixture.catalog());
    }

    private static AutomaticWeaponPlacementProposal withReview(
            AutomaticWeaponPlacementProposal proposal) {
        return new AutomaticWeaponPlacementProposal(
                proposal.blueprintId(),
                proposal.mechanicalScore(),
                proposal.confidence(),
                proposal.position(),
                proposal.progressionCoordinate(),
                proposal.levelsPerTier(),
                proposal.formulaVersion(),
                proposal.referenceVersion(),
                proposal.placementVersion(),
                List.of("low_confidence"));
    }

    private static AutomaticWeaponRoleSignature branchSignature(
            String blueprintId,
            int score,
            int direction,
            String archetype,
            int sequence) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int sign = metric.component() == MechanicalMetric.Component.COMBAT ? 1 : -1;
            offsets.put(metric.serializedName(), direction * sign);
        }
        return new AutomaticWeaponRoleSignature(
                blueprintId,
                score,
                100,
                archetype,
                false,
                Math.max(0, Math.min(100, score + Math.floorMod(sequence, 2))),
                offsets,
                true,
                List.of());
    }

    private static AutomaticWeaponPlacementProposal rawProposal(
            AutomaticWeaponRoleSignature signature) {
        int score = signature.mechanicalScore();
        long siblingOrder = Integer.toUnsignedLong(signature.blueprintId().hashCode());
        return new AutomaticWeaponPlacementProposal(
                signature.blueprintId(),
                score,
                signature.confidence(),
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        siblingOrder),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static AutomaticWeaponPrerequisitePlan branchPlan(
            BranchPrerequisiteFixture fixture) {
        BlueprintResearchSnapshot research = switch (
                fixture.candidates().policy().prerequisiteStrategy()) {
            case LEGACY_AND -> dynamicSnapshot(AutomaticPlacementMode.CONNECTED);
            case GROUPED_ROUTES_V1 -> groupedDynamicSnapshot();
            case HYBRID_ROUTES_V1 -> hybridDynamicSnapshot(
                    fixture.candidates().policy());
        };
        return new AutomaticWeaponPrerequisitePlanner().plan(
                research,
                fixture.catalog(),
                PROFILE,
                fixture.candidates(),
                fixture.classification());
    }

    private static List<ResourceLocation> automaticIds(
            BranchPrerequisiteFixture fixture) {
        return fixture.candidates().eligibleProposals().keySet().stream()
                .map(AutomaticWeaponPrerequisitePlannerTest::id)
                .toList();
    }

    private static int rank(
            BranchPrerequisiteFixture fixture,
            ResourceLocation blueprintId) {
        return fixture.candidates().eligibleProposal(blueprintId).orElseThrow()
                .progressionCoordinate().rank();
    }

    private static int branch(
            BranchPrerequisiteFixture fixture,
            ResourceLocation blueprintId) {
        return fixture.classification().branchModel().branchIndexByBlueprint()
                .get(blueprintId.toString());
    }

    private static int occupiedRankCount(
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        return Math.toIntExact(candidates.eligibleProposals().values().stream()
                .map(value -> value.progressionCoordinate().rank())
                .distinct()
                .count());
    }

    private static int occupiedRankDistance(
            BranchPrerequisiteFixture fixture,
            ResourceLocation dependent,
            ResourceLocation parent) {
        return occupiedRankDistance(
                fixture.candidates(), dependent, parent);
    }

    private static int occupiedRankDistance(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            ResourceLocation dependent,
            ResourceLocation parent) {
        List<Integer> ranks = candidates.eligibleProposals().values().stream()
                .map(value -> value.progressionCoordinate().rank())
                .distinct()
                .sorted()
                .toList();
        int dependentRank = candidates.eligibleProposal(dependent).orElseThrow()
                .progressionCoordinate().rank();
        int parentRank = candidates.eligibleProposal(parent).orElseThrow()
                .progressionCoordinate().rank();
        return ranks.indexOf(dependentRank) - ranks.indexOf(parentRank);
    }

    private static boolean separatedParentIsLocalOrEmergency(
            BranchPrerequisiteFixture fixture,
            ResourceLocation dependent,
            ResourceLocation parent) {
        if (branch(fixture, dependent) == branch(fixture, parent)) {
            return true;
        }
        List<Integer> ranks = fixture.candidates().eligibleProposals().values().stream()
                .map(value -> value.progressionCoordinate().rank())
                .distinct()
                .sorted()
                .toList();
        int dependentRankIndex = ranks.indexOf(rank(fixture, dependent));
        return automaticIds(fixture).stream()
                .filter(candidate -> branch(fixture, candidate) == branch(fixture, dependent))
                .mapToInt(candidate -> ranks.indexOf(rank(fixture, candidate)))
                .noneMatch(candidateRankIndex -> candidateRankIndex < dependentRankIndex
                        && dependentRankIndex - candidateRankIndex
                                <= ResearchTechTreeContract.MAX_AUTOMATIC_EDGE_RANK_SPAN);
    }

    private static int publishedRank(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            ResourceLocation blueprintId) {
        return candidates.eligibleProposal(blueprintId).orElseThrow()
                .progressionCoordinate().rank();
    }

    private static boolean reachesAnyFoundation(
            AutomaticWeaponPrerequisitePlan plan,
            ResourceLocation node,
            Set<ResourceLocation> foundations,
            Set<ResourceLocation> visiting,
            Map<ResourceLocation, Boolean> memo) {
        if (foundations.contains(node)) {
            return true;
        }
        Boolean known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            return false;
        }
        boolean result = plan.prerequisitesFor(node).stream().anyMatch(parent ->
                reachesAnyFoundation(plan, parent, foundations, visiting, memo));
        visiting.remove(node);
        memo.put(node, result);
        return result;
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates(
            AutomaticPlacementMode mode,
            List<ResourceLocation> addOns) {
        return candidates(mode, addOns, AutomaticWeaponPlacementPolicy.DEFAULT);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates(
            AutomaticPlacementMode mode,
            List<ResourceLocation> addOns,
            AutomaticWeaponPlacementPolicy policy) {
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (int index = 0; index < addOns.size(); index++) {
            int score = 17;
            ResourceLocation id = addOns.get(index);
            proposals.put(id.toString(), new AutomaticWeaponPlacementProposal(
                    id.toString(),
                    score,
                    100,
                    new ProgressionPosition(
                            Tier.forScore(score),
                            ResearchTechTreeContract.levelForScore(score, 3),
                            Math.addExact(
                                    Math.multiplyExact(score, 1L << 56),
                                    index + 1L)),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                mode,
                policy,
                5L,
                7L,
                addOns.size() + 2,
                proposals,
                Map.of(),
                Set.of(ROOT_A.toString(), ROOT_B.toString()),
                Set.of());
    }

    private static AutomaticWeaponPlacementPolicy groupedScorePolicy(
            int mergeInterval) {
        return new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                mergeInterval,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.LEGACY_SCORE_BUCKETS,
                9,
                List.of(),
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.GROUPED_ROUTES_V1);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot dynamicCandidates(
            List<ResourceLocation> addOns) {
        return dynamicCandidates(addOns, true);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot dynamicCandidates(
            List<ResourceLocation> addOns,
            boolean includeAuthoredWeapons) {
        return dynamicCandidates(
                addOns,
                includeAuthoredWeapons,
                PrerequisiteStrategy.LEGACY_AND);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot dynamicCandidates(
            List<ResourceLocation> addOns,
            boolean includeAuthoredWeapons,
            PrerequisiteStrategy prerequisiteStrategy) {
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                9,
                List.of(),
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                prerequisiteStrategy);
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (int index = 0; index < addOns.size(); index++) {
            int score = index * ResearchTechTreeContract.SCORE_MAX
                    / Math.max(1, addOns.size() - 1);
            ResourceLocation id = addOns.get(index);
            proposals.put(id.toString(), new AutomaticWeaponPlacementProposal(
                    id.toString(),
                    score,
                    100,
                    new ProgressionPosition(
                            Tier.forScore(score),
                            ResearchTechTreeContract.levelForScore(score, 3),
                            Math.multiplyExact(score, 1L << 56) + index),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        proposals = new LinkedHashMap<>(
                new AutomaticWeaponLayerPlanner().assign(proposals, policy));
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                policy,
                5L,
                7L,
                addOns.size() + (includeAuthoredWeapons ? 2 : 0),
                proposals,
                Map.of(),
                includeAuthoredWeapons
                        ? Set.of(ROOT_A.toString(), ROOT_B.toString())
                        : Set.of(),
                Set.of());
    }

    private static AutomaticWeaponPlacementCandidateSnapshot reviewedCandidates(
            ResourceLocation addOn,
            AutomaticWeaponPlacementPolicy.ReviewHandling reviewHandling) {
        int score = 17;
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                addOn.toString(),
                score,
                25,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        Math.multiplyExact(score, 1L << 56) + 1L),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of("low_confidence"));
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                new AutomaticWeaponPlacementPolicy(3, 60, reviewHandling),
                5L,
                7L,
                3,
                Map.of(addOn.toString(), proposal),
                Map.of(),
                Set.of(ROOT_A.toString(), ROOT_B.toString()),
                Set.of());
    }

    private static AutomaticWeaponPlacementCandidateSnapshot mixedReviewCandidates(
            ResourceLocation reviewed,
            ResourceLocation regular) {
        int score = 17;
        long baseOrder = Math.multiplyExact(score, 1L << 56);
        AutomaticWeaponPlacementProposal reviewedProposal =
                new AutomaticWeaponPlacementProposal(
                        reviewed.toString(),
                        score,
                        25,
                        new ProgressionPosition(
                                Tier.forScore(score),
                                ResearchTechTreeContract.levelForScore(score, 3),
                                baseOrder + 1L),
                        3,
                        ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                        List.of("low_confidence"));
        AutomaticWeaponPlacementProposal regularProposal =
                new AutomaticWeaponPlacementProposal(
                        regular.toString(),
                        score,
                        100,
                        new ProgressionPosition(
                                Tier.forScore(score),
                                ResearchTechTreeContract.levelForScore(score, 3),
                                baseOrder + 2L),
                        3,
                        ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                        List.of());
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                new AutomaticWeaponPlacementPolicy(
                        3,
                        60,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_INDEPENDENT),
                5L,
                7L,
                4,
                Map.of(
                        reviewed.toString(), reviewedProposal,
                        regular.toString(), regularProposal),
                Map.of(),
                Set.of(ROOT_A.toString(), ROOT_B.toString()),
                Set.of());
    }

    private static boolean dependsOn(
            AutomaticWeaponPrerequisitePlan plan,
            ResourceLocation node,
            ResourceLocation possiblePrerequisite) {
        return dependsOn(plan, node, possiblePrerequisite, new java.util.LinkedHashSet<>());
    }

    private static Set<ResourceLocation> mandatoryClosure(
            AutomaticWeaponPrerequisitePlan plan,
            ResourceLocation node,
            Map<ResourceLocation, Set<ResourceLocation>> memo,
            Set<ResourceLocation> visiting) {
        Set<ResourceLocation> known = memo.get(node);
        if (known != null) {
            return known;
        }
        assertTrue(visiting.add(node), "hybrid requirement graph must be acyclic");
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        result.add(node);
        for (ResearchPrerequisiteGroup group : plan.requirementsFor(node).allOf()) {
            LinkedHashSet<ResourceLocation> groupMandatory = null;
            for (ResourceLocation alternative : group.anyOf()) {
                Set<ResourceLocation> route = mandatoryClosure(
                        plan, alternative, memo, visiting);
                if (groupMandatory == null) {
                    groupMandatory = new LinkedHashSet<>(route);
                } else {
                    groupMandatory.retainAll(route);
                }
            }
            if (groupMandatory != null) {
                result.addAll(groupMandatory);
            }
        }
        visiting.remove(node);
        Set<ResourceLocation> frozen = Set.copyOf(result);
        memo.put(node, frozen);
        return frozen;
    }

    private static boolean dependsOn(
            AutomaticWeaponPrerequisitePlan plan,
            ResourceLocation node,
            ResourceLocation possiblePrerequisite,
            Set<ResourceLocation> visiting) {
        if (!visiting.add(node)) {
            return false;
        }
        for (ResourceLocation prerequisite : plan.prerequisitesFor(node)) {
            if (prerequisite.equals(possiblePrerequisite)
                    || dependsOn(plan, prerequisite, possiblePrerequisite, visiting)) {
                visiting.remove(node);
                return true;
            }
        }
        visiting.remove(node);
        return false;
    }

    private static BlueprintResearchSnapshot snapshot(AutomaticPlacementMode mode) {
        return snapshot(mode, Map.of(PROFILE, profile()));
    }

    private static BlueprintResearchSnapshot dynamicSnapshot(AutomaticPlacementMode mode) {
        return snapshot(
                mode,
                Map.of(PROFILE, profile()),
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        mode,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of()));
    }

    private static BlueprintResearchSnapshot groupedDynamicSnapshot() {
        return snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profile()),
                new ResearchAutomaticPlacementProfile(
                        ResearchAutomaticPlacementProfile.CURRENT_FORMAT,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of(),
                        AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                        PrerequisiteStrategy.GROUPED_ROUTES_V1));
    }

    private static BlueprintResearchSnapshot hybridDynamicSnapshot(
            AutomaticWeaponPlacementPolicy policy) {
        return snapshot(
                AutomaticPlacementMode.CONNECTED,
                Map.of(PROFILE, profile()),
                new ResearchAutomaticPlacementProfile(
                        ResearchAutomaticPlacementProfile.CURRENT_FORMAT,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy.levelsPerTier(),
                        policy.reviewConfidenceThreshold(),
                        policy.reviewHandling(),
                        policy.maxGeneratedPrerequisites(),
                        policy.mergeInterval(),
                        policy.maxNodesPerRank(),
                        policy.progressionBands(),
                        policy.foundationCount(),
                        PrerequisiteStrategy.HYBRID_ROUTES_V1));
    }

    private static ResearchRequirements expectedHybridRequirements(
            AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape shape,
            List<ResourceLocation> parents) {
        return switch (shape) {
            case MANDATORY_SINGLETONS -> ResearchRequirements.fromLegacy(parents);
            case ALTERNATIVE_ROUTES -> new ResearchRequirements(List.of(
                    new ResearchPrerequisiteGroup(parents)));
            case ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY ->
                    new ResearchRequirements(List.of(
                            new ResearchPrerequisiteGroup(parents.subList(0, 2)),
                            ResearchPrerequisiteGroup.singleton(parents.get(2))));
        };
    }

    private static BlueprintResearchSnapshot snapshot(
            AutomaticPlacementMode mode,
            Map<ResourceLocation, BlueprintResearchProfile> profiles) {
        return snapshot(
                mode,
                profiles,
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        mode,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of()));
    }

    private static BlueprintResearchSnapshot snapshot(
            AutomaticPlacementMode mode,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            ResearchAutomaticPlacementProfile automaticProfile) {
        return snapshot(mode, profiles, automaticProfile, Map.of());
    }

    private static BlueprintResearchSnapshot snapshot(
            AutomaticPlacementMode mode,
            Map<ResourceLocation, BlueprintResearchProfile> profiles,
            ResearchAutomaticPlacementProfile automaticProfile,
            Map<ResourceLocation, BlueprintResearchRule> rules) {
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(
                        exactEntry(ROOT_A, 10),
                        exactEntry(ROOT_B, 20),
                        new ResearchTechTreeEntryBundle.Entry(
                                new BlueprintResearchTarget(
                                        List.of(),
                                        List.of(),
                                        Optional.of(new BlueprintCatalogSelector(
                                                List.of("addon"),
                                                List.of(),
                                                List.of(),
                                                List.of(),
                                                List.of(BlueprintKind.GUN),
                                                1.0F))),
                                Domain.WEAPONS,
                                LANE,
                                Tier.BASIC,
                                900_000,
                                Optional.empty(),
                                Optional.empty(),
                                true)));
        return BlueprintResearchSnapshot.create(
                Map.of(),
                profiles,
                rules,
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(id("test:entries"), entries),
                Map.of(id("test:automatic"), automaticProfile));
    }

    private static BlueprintResearchRule costRule(
            ResourceLocation target,
            int points) {
        return new BlueprintResearchRule(
                BlueprintResearchRule.CURRENT_FORMAT,
                PROFILE,
                100,
                new BlueprintResearchTarget(
                        List.of(target), List.of(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintResearchCost(points, List.of())),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ResearchTechTreeEntryBundle.Entry exactEntry(
            ResourceLocation id,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                new BlueprintResearchTarget(List.of(id), List.of(), Optional.empty()),
                Domain.WEAPONS,
                LANE,
                Tier.STARTER,
                order,
                Optional.empty(),
                Optional.empty());
    }

    private static Map<ResourceLocation, BlueprintData> catalog(
            List<ResourceLocation> addOns) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        result.put(ROOT_A, data(ROOT_A));
        result.put(ROOT_B, data(ROOT_B));
        addOns.forEach(id -> result.put(id, data(id)));
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, BlueprintData> automaticOnlyCatalog(
            List<ResourceLocation> addOns) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        addOns.forEach(id -> result.put(id, data(id)));
        return Map.copyOf(result);
    }

    private static BlueprintResearchProfile profile() {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(),
                Optional.of(TREE));
    }

    private static BlueprintResearchProfile profileWithEntryPoint(
            ResourceLocation entryPoint) {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(entryPoint),
                Optional.of(TREE));
    }

    private static ResearchTechTreeDefinition tree() {
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                "Progression",
                Optional.empty(),
                Optional.empty(),
                ResearchTechTreeDefinition.WeaponPlacementMode.AUTOMATIC,
                new ResearchTechTreeDefinition.LayoutDefinition(9),
                ResearchTechTreeDefinition.BandPolicyDefinition.NONE,
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                List.of(new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        LANE,
                        Tier.STARTER,
                        List.of(new ResearchTechTreeDefinition.LaneDefinition(
                                LANE,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0)))));
    }

    private static BlueprintProgressionConfigSnapshot config() {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip." + id.getPath(),
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                "pistol",
                new ResourceLocation(id.getNamespace(), "slot/" + id.getPath()),
                BlueprintKind.GUN);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private record BranchPrerequisiteFixture(
            AutomaticWeaponCandidateClassification classification,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<ResourceLocation, BlueprintData> catalog) {
    }
}
