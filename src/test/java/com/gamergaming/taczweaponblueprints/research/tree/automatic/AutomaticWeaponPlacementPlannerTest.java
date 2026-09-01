package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.MechanicalRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.LayeringStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.MergeIntervalBehavior;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.ReviewHandling;

class AutomaticWeaponPlacementPlannerTest {
    private final AutomaticWeaponPlacementPlanner planner =
            new AutomaticWeaponPlacementPlanner();

    @Test
    void fixedScoreBandsSplitEachTierIntoMultipleProgressionLevels() {
        List<Integer> scores = List.of(
                0, 5, 6, 11, 12, 16,
                17, 22, 23, 28, 29, 33,
                34, 39, 40, 45, 46, 50,
                51, 56, 57, 62, 63, 67,
                68, 73, 74, 79, 80, 84,
                85, 90, 91, 95, 96, 100);
        Map<String, WeaponMechanicalScore> evidence = new LinkedHashMap<>();
        List<String> candidates = new ArrayList<>();
        for (int index = 0; index < scores.size(); index++) {
            String id = "addon:weapon_" + index;
            candidates.add(id);
            evidence.put(id, score(id, scores.get(index), 100, false, List.of()));
        }

        AutomaticWeaponPlacementPlan plan = planner.plan(
                evidence, candidates, AutomaticWeaponPlacementPolicy.DEFAULT);

        assertEquals("tacz-gun-placement-v13",
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION);
        assertEquals(ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                plan.placementVersion());
        assertEquals(scores.size(), plan.proposals().size());
        for (Tier tier : Tier.values()) {
            for (int level = 0; level < 3; level++) {
                assertEquals(2, plan.count(tier, level), tier + " level " + level);
            }
        }
        for (AutomaticWeaponPlacementProposal proposal : plan.proposals().values()) {
            assertEquals(Tier.forScore(proposal.mechanicalScore()),
                    proposal.position().tier());
            assertEquals(
                    ResearchTechTreeContract.levelForScore(
                            proposal.mechanicalScore(), 3),
                    proposal.position().level());
            assertFalse(proposal.reviewRequired());
        }
    }

    @Test
    void resultIsInputOrderIndependentAndSiblingOrdersAreUniqueAndBounded() {
        Map<String, WeaponMechanicalScore> scores = new LinkedHashMap<>();
        List<String> forward = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            String id = "pack" + (index % 11) + ":weapon_" + index;
            forward.add(id);
            scores.put(id, score(id, 50, 100, false, List.of()));
        }
        List<String> reverse = new ArrayList<>(forward);
        java.util.Collections.reverse(reverse);
        Map<String, WeaponMechanicalScore> reverseScores = new LinkedHashMap<>();
        reverse.forEach(id -> reverseScores.put(id, scores.get(id)));

        AutomaticWeaponPlacementPlan first = planner.plan(
                scores, forward, AutomaticWeaponPlacementPolicy.DEFAULT);
        AutomaticWeaponPlacementPlan second = planner.plan(
                reverseScores,
                reverse,
                AutomaticWeaponPlacementPolicy.DEFAULT);

        assertEquals(first, second);
        assertEquals(256, first.proposals().values().stream()
                .map(value -> value.position().siblingOrder())
                .distinct().count());
        assertTrue(first.proposals().values().stream().allMatch(value ->
                value.position().siblingOrder()
                        >= 50L * AutomaticWeaponPlacementPlanner.SIBLING_HASH_SPACE
                        && value.position().siblingOrder()
                        < 51L * AutomaticWeaponPlacementPlanner.SIBLING_HASH_SPACE));
    }

    @Test
    void maximumSupportedCandidatePopulationIsAcceptedDeterministically() {
        Map<String, WeaponMechanicalScore> scores = new LinkedHashMap<>();
        List<String> forward = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            String id = "large_pack:weapon_" + index;
            forward.add(id);
            scores.put(id, score(id, index % 101, 100, false, List.of()));
        }
        List<String> reverse = new ArrayList<>(forward);
        java.util.Collections.reverse(reverse);

        AutomaticWeaponPlacementPlan first = planner.plan(
                scores, forward, AutomaticWeaponPlacementPolicy.DEFAULT);
        AutomaticWeaponPlacementPlan second = planner.plan(
                scores, reverse, AutomaticWeaponPlacementPolicy.DEFAULT);

        assertEquals(4096, first.proposals().size());
        assertEquals(first, second);
        assertEquals(4096, first.proposals().values().stream()
                .map(value -> value.position().siblingOrder())
                .distinct().count());
    }

    @Test
    void addingAWeaponNeverMovesExistingSiblingOrders() {
        Map<String, WeaponMechanicalScore> scores = new LinkedHashMap<>();
        List<String> original = new ArrayList<>();
        for (int index = 0; index < 116; index++) {
            String id = "addon:weapon_" + index;
            original.add(id);
            scores.put(id, score(id, 50, 100, false, List.of()));
        }
        AutomaticWeaponPlacementPlan before = planner.plan(
                scores, original, AutomaticWeaponPlacementPolicy.DEFAULT);

        String added = "addon:new_weapon";
        scores.put(added, score(added, 50, 100, false, List.of()));
        List<String> expanded = new ArrayList<>(original);
        expanded.add(added);
        AutomaticWeaponPlacementPlan after = planner.plan(
                scores, expanded, AutomaticWeaponPlacementPolicy.DEFAULT);

        original.forEach(id -> assertEquals(
                before.proposals().get(id).position().siblingOrder(),
                after.proposals().get(id).position().siblingOrder(),
                id));
    }

    @Test
    void reviewSignalsDoNotEraseMechanicallyUsefulProposals() {
        Map<String, WeaponMechanicalScore> scores = Map.of(
                "addon:ready", score("addon:ready", 70, 90, false, List.of()),
                "addon:uncertain", score(
                        "addon:uncertain", 45, 40, false,
                        List.of("missing_metric:effective_range")),
                "addon:scripted", score(
                        "addon:scripted", 80, 50, true,
                        List.of("script_controlled")));

        AutomaticWeaponPlacementPlan plan = planner.plan(
                scores, scores.keySet(), AutomaticWeaponPlacementPolicy.DEFAULT);

        assertEquals(3, plan.proposals().size());
        assertEquals(1, plan.readyCount());
        assertEquals(2, plan.reviewRequiredCount());
        assertFalse(plan.proposals().get("addon:ready").reviewRequired());
        assertEquals(
                List.of("incomplete_mechanical_evidence", "low_confidence"),
                plan.proposals().get("addon:uncertain").reviewReasons());
        assertEquals(
                List.of("low_confidence", "script_controlled"),
                plan.proposals().get("addon:scripted").reviewReasons());
    }

    @Test
    void conservativeFallbackIsDeterministicBoundedAndUsesMultipleTierLevels() {
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED);
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (int index = 0; index < 96; index++) {
            String id = "addon:unscoreable_rifle_" + index;
            AutomaticWeaponPlacementProposal proposal = planner.conservativeFallback(
                    id, "rifle", "missing_tacz_gun_index", policy);
            proposals.put(id, proposal);
            assertEquals(proposal, planner.conservativeFallback(
                    id, "rifle", "missing_tacz_gun_index", policy));
            assertTrue(proposal.mechanicalScore() >= 30 && proposal.mechanicalScore() <= 62);
            assertEquals(0, proposal.confidence());
            assertTrue(proposal.reviewReasons().contains("unscored_fallback"));
        }

        assertTrue(proposals.values().stream()
                .map(value -> value.position().tier())
                .distinct().count() > 1);
        assertTrue(proposals.values().stream()
                .map(value -> value.position().tier() + ":" + value.position().level())
                .distinct().count() > 3);
        assertEquals(proposals.size(), proposals.values().stream()
                .map(value -> value.position().siblingOrder())
                .distinct().count());
        assertThrows(IllegalArgumentException.class, () -> planner.conservativeFallback(
                "addon:bad", "rifle", " ", policy));
    }

    @Test
    void invalidCandidatesAreRejectedIndividuallyAndPolicyIsBounded() {
        WeaponMechanicalScore valid = score("addon:valid", 50, 100, false, List.of());
        WeaponMechanicalScore mismatched = score("addon:other", 50, 100, false, List.of());
        WeaponMechanicalScore oldFormula = score(
                "addon:old_formula", 50, 100, false, List.of(),
                "old-formula", ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION);
        WeaponMechanicalScore oldReference = score(
                "addon:old_reference", 50, 100, false, List.of(),
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION, "old-reference");
        Map<String, WeaponMechanicalScore> scores = Map.of(
                "addon:valid", valid,
                "addon:mismatched", mismatched,
                "addon:old_formula", oldFormula,
                "addon:old_reference", oldReference);

        AutomaticWeaponPlacementPlan plan = planner.plan(
                scores,
                List.of(
                        "addon:valid",
                        "addon:missing",
                        "addon:mismatched",
                        "addon:old_formula",
                        "addon:old_reference"),
                AutomaticWeaponPlacementPolicy.DEFAULT);

        assertEquals(List.of("addon:valid"), plan.proposals().keySet().stream().toList());
        assertEquals("missing_mechanical_score",
                plan.rejectedCandidates().get("addon:missing"));
        assertEquals("mechanical_score_identity_mismatch",
                plan.rejectedCandidates().get("addon:mismatched"));
        assertEquals("incompatible_formula_version",
                plan.rejectedCandidates().get("addon:old_formula"));
        assertEquals("incompatible_reference_version",
                plan.rejectedCandidates().get("addon:old_reference"));

        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(scores,
                        List.of("addon:valid", "addon:valid"),
                        AutomaticWeaponPlacementPolicy.DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomaticWeaponPlacementPolicy(0, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomaticWeaponPlacementPolicy(3, 101));
        assertThrows(IllegalArgumentException.class,
                () -> new AutomaticWeaponPlacementPolicy(3, 60, null));
        List<String> oversized = java.util.stream.IntStream.range(0, 4097)
                .mapToObj(index -> "addon:weapon_" + index)
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(Map.of(), oversized,
                        AutomaticWeaponPlacementPolicy.DEFAULT));
        Map<String, String> oversizedRejections = new LinkedHashMap<>();
        oversized.forEach(id -> oversizedRejections.put(id, "missing_mechanical_score"));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementPlan(
                        ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                        ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER,
                        AutomaticWeaponPlacementPolicy.DEFAULT_REVIEW_CONFIDENCE_THRESHOLD,
                        4097,
                        Map.of(),
                        oversizedRejections));
    }

    @Test
    void groupedRouteRolloutControlsAreExplicitAndBounded() {
        AutomaticWeaponPlacementPolicy grouped = policy(
                LayeringStrategy.DYNAMIC_STAT_LAYERS,
                2,
                4,
                PrerequisiteStrategy.GROUPED_ROUTES_V1);
        assertEquals(
                MergeIntervalBehavior.IGNORED_GROUPED_ROUTES_V1,
                grouped.mergeIntervalBehavior());
        assertFalse(grouped.schedulesPeriodicMerge());
        assertEquals(
                "conservative_legacy_and_union_closure_v1",
                AutomaticWeaponPlacementPolicy.GENERATED_PARENT_COST_GUARD);
        assertEquals(
                AutomaticWeaponAlternativeRouteGuard.CONTRACT,
                grouped.generatedParentCostGuard());
        assertEquals(
                AutomaticWeaponPlacementPolicy.LEGACY_GENERATED_PARENT_COST_GUARD,
                policy(LayeringStrategy.DYNAMIC_STAT_LAYERS, 2, 4,
                        PrerequisiteStrategy.LEGACY_AND)
                        .generatedParentCostGuard());

        assertEquals(
                MergeIntervalBehavior.DISABLED,
                policy(LayeringStrategy.DYNAMIC_STAT_LAYERS, 2, 0,
                        PrerequisiteStrategy.GROUPED_ROUTES_V1)
                        .mergeIntervalBehavior());
        assertEquals(
                MergeIntervalBehavior.INERT_PREREQUISITE_CEILING,
                policy(LayeringStrategy.DYNAMIC_STAT_LAYERS, 2, 4,
                        PrerequisiteStrategy.LEGACY_AND)
                        .mergeIntervalBehavior());
        assertEquals(
                MergeIntervalBehavior.LEGACY_THIRD_PARENT_SCHEDULE,
                policy(LayeringStrategy.DYNAMIC_STAT_LAYERS, 3, 4,
                        PrerequisiteStrategy.LEGACY_AND)
                        .mergeIntervalBehavior());
        assertEquals(
                MergeIntervalBehavior.LEGACY_SECOND_PARENT_SCHEDULE,
                policy(LayeringStrategy.LEGACY_SCORE_BUCKETS, 2, 4,
                        PrerequisiteStrategy.LEGACY_AND)
                        .mergeIntervalBehavior());
        AutomaticWeaponPlacementPolicy hybrid = policy(
                LayeringStrategy.DYNAMIC_STAT_LAYERS,
                3,
                4,
                PrerequisiteStrategy.HYBRID_ROUTES_V1);
        assertEquals(
                MergeIntervalBehavior.HYBRID_MANDATORY_GATEWAY_SCHEDULE,
                hybrid.mergeIntervalBehavior());
        assertTrue(hybrid.schedulesPeriodicMerge());
        assertEquals(
                AutomaticWeaponPlacementPolicy.HYBRID_GENERATED_PARENT_COST_GUARD,
                hybrid.generatedParentCostGuard());
        assertThrows(IllegalArgumentException.class, () -> policy(
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                3,
                4,
                PrerequisiteStrategy.HYBRID_ROUTES_V1));
    }

    private static AutomaticWeaponPlacementPolicy policy(
            LayeringStrategy layeringStrategy,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            PrerequisiteStrategy prerequisiteStrategy) {
        return new AutomaticWeaponPlacementPolicy(
                3,
                60,
                ReviewHandling.PLACE_CONNECTED,
                maxGeneratedPrerequisites,
                mergeInterval,
                layeringStrategy,
                9,
                List.of(),
                2,
                prerequisiteStrategy);
    }

    private static WeaponMechanicalScore score(
            String id,
            int score,
            int confidence,
            boolean scripted,
            List<String> warnings) {
        return score(
                id,
                score,
                confidence,
                scripted,
                warnings,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION);
    }

    private static WeaponMechanicalScore score(
            String id,
            int score,
            int confidence,
            boolean scripted,
            List<String> warnings,
            String formulaVersion,
            String referenceVersion) {
        WeaponStatEvidence evidence = new WeaponStatEvidence(
                id,
                "test",
                8.0,
                0.0,
                600.0,
                20,
                2.0,
                100.0,
                50.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                2.0,
                0.2,
                0.4,
                -0.2,
                1,
                2,
                null,
                "magazine",
                false,
                scripted,
                List.of());
        return new WeaponMechanicalScore(
                evidence,
                new MechanicalRating(
                        score,
                        score,
                        confidence,
                        formulaVersion,
                        referenceVersion),
                Map.of(),
                Map.of(),
                Map.of(),
                warnings);
    }
}
