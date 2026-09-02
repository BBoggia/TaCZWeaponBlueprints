package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponRankFinalizerTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:tree");

    @Test
    void liftsSameRankDependenciesAndCompactsEmptyRanks() {
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(Map.of(
                "test:a", proposal("test:a", 10, 0, 0),
                "test:b", proposal("test:b", 11, 0, 1),
                "test:c", proposal("test:c", 40, 3, 2),
                "test:d", proposal("test:d", 41, 3, 3)));
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                4,
                Map.of(
                        id("test:c"), List.of(id("test:a")),
                        id("test:d"), List.of(id("test:c"))),
                Map.of(
                        id("test:a"), "generated_root",
                        id("test:b"), "generated_root"));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(candidates, List.of(plan));

        assertEquals(0, rank(finalized, "test:a"));
        assertEquals(0, rank(finalized, "test:b"));
        assertEquals(1, rank(finalized, "test:c"));
        assertEquals(2, rank(finalized, "test:d"));
        assertTrue(finalized.eligibleProposals().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()))
                .values().stream().allMatch(width -> width <= 8));
    }

    @Test
    void rejectsCyclesAcrossProfileSpecificPlans() {
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(Map.of(
                "test:a", proposal("test:a", 10, 0, 0),
                "test:b", proposal("test:b", 20, 1, 1)));
        AutomaticWeaponPrerequisitePlan first = plan(
                id("test:first"), "test:a", "test:b");
        AutomaticWeaponPrerequisitePlan second = plan(
                id("test:second"), "test:b", "test:a");

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates, List.of(first, second)));
    }

    @Test
    void reservesAuthoredOccupancyWhenBoundingMixedRanks() {
        Set<String> authored = Set.of(
                "test:authored_0",
                "test:authored_1",
                "test:authored_2",
                "test:authored_3",
                "test:authored_4",
                "test:authored_5");
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                Map.of(
                        "test:auto_0", proposal("test:auto_0", 10, 0, 0),
                        "test:auto_1", proposal("test:auto_1", 11, 0, 1),
                        "test:auto_2", proposal("test:auto_2", 12, 0, 2),
                        "test:auto_3", proposal("test:auto_3", 13, 0, 3)),
                authored);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                4,
                Map.of(),
                Map.of(
                        id("test:auto_0"), "generated_root",
                        id("test:auto_1"), "generated_root",
                        id("test:auto_2"), "generated_root",
                        id("test:auto_3"), "generated_root"));
        Map<String, Integer> authoredRanks = authored.stream().collect(
                java.util.stream.Collectors.toMap(
                        value -> value,
                        ignored -> 0));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates,
                        List.of(plan),
                        Map.of(PROFILE, authoredRanks));

        Map<Integer, Long> automaticWidths = finalized.eligibleProposals().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()));
        assertEquals(2L, automaticWidths.get(0));
        assertEquals(2L, automaticWidths.get(1));
        assertEquals(6L, authored.size());
    }

    @Test
    void fillsResidualAuthoredCapacityInsteadOfLiftingWholeAutomaticRows() {
        Map<String, AutomaticWeaponPlacementProposal> automatic = new LinkedHashMap<>();
        Map<ResourceLocation, String> omitted = new LinkedHashMap<>();
        for (int index = 0; index < 16; index++) {
            String id = "test:auto_" + index;
            automatic.put(id, proposal(id, 10 + index, index / 8, index));
            omitted.put(id(id), "generated_root");
        }
        Set<String> authored = Set.of(
                "test:authored_0",
                "test:authored_1",
                "test:authored_2",
                "test:authored_3");
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                automatic, authored);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                automatic.size(),
                Map.of(),
                omitted);
        Map<String, Integer> authoredRanks = Map.of(
                "test:authored_0", 0,
                "test:authored_1", 0,
                "test:authored_2", 1,
                "test:authored_3", 1);

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates,
                        List.of(plan),
                        Map.of(PROFILE, authoredRanks));

        Map<Integer, Long> automaticWidths = finalized.eligibleProposals().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        assertEquals(Map.of(0, 6L, 1, 6L, 2, 4L), automaticWidths);
    }

    @Test
    void keepsMatureFamilyCrossSectionsTogetherWhilePackingMixedRanks() {
        Map<String, AutomaticWeaponPlacementProposal> automatic = new LinkedHashMap<>();
        Map<ResourceLocation, String> omitted = new LinkedHashMap<>();
        Map<ResourceLocation, AutomaticWeaponPrerequisitePlan.BranchCoordinate> branches =
                new LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            String id = "test:branch_auto_" + index;
            int rank = index < 4 ? 0 : 1;
            int branch = index < 6 ? 0 : 1;
            automatic.put(id, proposal(id, 10 + index, rank, index));
            omitted.put(id(id), "generated_root");
            branches.put(id(id), new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                    branch, rank, 1, 1));
        }
        Set<String> authored = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> "test:branch_authored_" + index)
                .collect(java.util.stream.Collectors.toSet());
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                automatic, authored);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                automatic.size(),
                Map.of(),
                omitted,
                Map.of(),
                branches);
        Map<String, Integer> authoredRanks = authored.stream().collect(
                java.util.stream.Collectors.toMap(value -> value, ignored -> 1));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates,
                        List.of(plan),
                        Map.of(PROFILE, authoredRanks));

        assertEquals(2, rank(finalized, "test:branch_auto_4"));
        assertEquals(2, rank(finalized, "test:branch_auto_5"));
        assertEquals(2, rank(finalized, "test:branch_auto_6"));
        assertEquals(2, rank(finalized, "test:branch_auto_7"));
    }

    @Test
    void reconcilesPlannedRankIndexWithTheFinalPublishedRank() {
        Set<String> authored = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> "test:authored_" + index)
                .collect(java.util.stream.Collectors.toSet());
        ResourceLocation automatic = id("test:auto");
        AutomaticWeaponPlacementCandidateSnapshot candidates = candidates(
                Map.of(automatic.toString(), proposal(automatic.toString(), 10, 0, 0)),
                authored);
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        automatic,
                        AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                        Optional.of(0),
                        0,
                        0,
                        0,
                        1,
                        Map.of(),
                        false,
                        false);
        AutomaticWeaponPrerequisitePlan plan = new AutomaticWeaponPrerequisitePlan(
                PROFILE,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                1,
                Map.of(),
                Map.of(automatic, "generated_root"),
                Map.of(automatic, decision));
        Map<String, Integer> authoredRanks = authored.stream().collect(
                java.util.stream.Collectors.toMap(value -> value, ignored -> 0));

        AutomaticWeaponPlacementCandidateSnapshot finalized =
                new AutomaticWeaponRankFinalizer().finalizeRanks(
                        candidates,
                        List.of(plan),
                        Map.of(PROFILE, authoredRanks));
        AutomaticWeaponPrerequisiteDecision reconciled = plan
                .withPublishedRanks(finalized)
                .decisionFor(automatic)
                .orElseThrow();

        assertEquals(0, reconciled.rankIndex());
        assertEquals(Optional.of(1), reconciled.publishedRank());
    }

    private static AutomaticWeaponPrerequisitePlan plan(
            ResourceLocation profile,
            String dependent,
            String prerequisite) {
        ResourceLocation omitted = dependent.equals("test:a")
                ? id("test:b")
                : id("test:a");
        return new AutomaticWeaponPrerequisitePlan(
                profile,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                2,
                Map.of(id(dependent), List.of(id(prerequisite))),
                Map.of(omitted, "generated_root"));
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates(
            Map<String, AutomaticWeaponPlacementProposal> proposals) {
        return candidates(proposals, Set.of());
    }

    private static AutomaticWeaponPlacementCandidateSnapshot candidates(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Set<String> authored) {
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                8,
                List.of());
        return new AutomaticWeaponPlacementCandidateSnapshot(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                policy,
                1L,
                1L,
                proposals.size() + authored.size(),
                proposals,
                Map.of(),
                authored,
                Set.of());
    }

    private static AutomaticWeaponPlacementProposal proposal(
            String blueprintId,
            int score,
            int rank,
            long siblingOrder) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId,
                score,
                100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        siblingOrder),
                new ProgressionCoordinate(rank, siblingOrder, Optional.empty()),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static int rank(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            String blueprintId) {
        return candidates.eligibleProposals().get(blueprintId)
                .progressionCoordinate().rank();
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
