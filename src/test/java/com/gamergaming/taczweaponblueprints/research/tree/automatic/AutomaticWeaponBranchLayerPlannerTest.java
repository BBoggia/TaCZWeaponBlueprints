package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

class AutomaticWeaponBranchLayerPlannerTest {
    @Test
    void allocatesADenseSharedTrunkThenGradualMultiNodeFamilyTapers() {
        Fixture forward = fixture(List.of(72, 27, 11), false, 20);
        Fixture reverse = fixture(List.of(72, 27, 11), true, 20);

        Map<String, AutomaticWeaponPlacementProposal> first = assign(forward);
        Map<String, AutomaticWeaponPlacementProposal> second = assign(reverse);

        assertEquals(first, second);
        Map<Integer, Long> widths = widths(first);
        assertEquals(java.util.stream.IntStream.range(0, widths.size()).boxed().toList(),
                List.copyOf(widths.keySet()));
        assertTrue(widths.values().stream().allMatch(width -> width <= 20));
        assertEquals(2L, widths.get(0));

        int split = AutomaticWeaponBranchLayerPlanner.sharedRankCount(
                AutomaticWeaponBranchLayerPlanner.targetRankCount(
                        first.size(), forward.policy()));
        double lowerAverage = widths.entrySet().stream()
                .filter(entry -> entry.getKey() < split)
                .mapToLong(Map.Entry::getValue)
                .average().orElseThrow();
        double upperAverage = widths.entrySet().stream()
                .filter(entry -> entry.getKey() >= split)
                .mapToLong(Map.Entry::getValue)
                .average().orElseThrow();
        assertTrue(lowerAverage > upperAverage,
                "the shared trunk should be denser than the tapered family area");

        for (AutomaticWeaponBranchModel.Branch branch : forward.model().branches()) {
            Map<Integer, Long> familyWidths = branch.memberBlueprintIds().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            id -> first.get(id).progressionCoordinate().rank(),
                            java.util.TreeMap::new,
                            java.util.stream.Collectors.counting()));
            assertTrue(familyWidths.size() >= 3);
            assertTrue(familyWidths.entrySet().stream()
                    .filter(entry -> entry.getKey() >= split)
                    .anyMatch(entry -> entry.getValue() > 1),
                    "upper families must retain simultaneous paths instead of becoming lines");

            List<String> terminals = branch.terminalBlueprintIds();
            assertFalse(terminals.isEmpty());
            int terminalRank = first.get(terminals.get(0)).progressionCoordinate().rank();
            assertTrue(terminals.stream().allMatch(id ->
                    first.get(id).progressionCoordinate().rank() == terminalRank));
            assertEquals(
                    familyWidths.keySet().stream().mapToInt(Integer::intValue).max()
                            .orElseThrow(),
                    terminalRank,
                    "trusted peers must remain a common family apex");
        }
    }

    @Test
    void preservesTrustedScoreOrderInsideEveryFamilyAndOptionalBands() {
        Fixture fixture = fixture(List.of(48, 24, 12), false, 16);
        AutomaticWeaponProgressionBand early = new AutomaticWeaponProgressionBand(
                id("test:early"), 49, "Early", Optional.empty());
        AutomaticWeaponProgressionBand late = new AutomaticWeaponProgressionBand(
                id("test:late"), 100, "Late", Optional.empty());
        AutomaticWeaponPlacementPolicy banded = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                16,
                List.of(early, late),
                2);
        Fixture withBands = new Fixture(
                fixture.proposals(), fixture.signatures(), fixture.model(), banded);

        Map<String, AutomaticWeaponPlacementProposal> assigned = assign(withBands);

        for (AutomaticWeaponBranchModel.Branch branch : fixture.model().branches()) {
            List<AutomaticWeaponPlacementProposal> members = branch.memberBlueprintIds().stream()
                    .map(assigned::get)
                    .sorted(Comparator
                            .comparingInt((AutomaticWeaponPlacementProposal value) ->
                                    value.progressionCoordinate().rank())
                            .thenComparingInt(
                                    AutomaticWeaponPlacementProposal::mechanicalScore))
                    .toList();
            for (int index = 1; index < members.size(); index++) {
                if (members.get(index - 1).progressionCoordinate().rank()
                        < members.get(index).progressionCoordinate().rank()) {
                    assertTrue(members.get(index - 1).mechanicalScore()
                            <= members.get(index).mechanicalScore());
                }
            }
        }
        assigned.values().forEach(proposal -> assertEquals(
                Optional.of(proposal.mechanicalScore() <= 49 ? early.id() : late.id()),
                proposal.progressionCoordinate().bandId()));
    }

    @Test
    void reportedAddonScaleStartsFamilyEnvelopesBeforeTheSharedMeshFullyEnds() {
        Fixture fixture = fixture(List.of(180, 60, 30, 17), false, 20);

        Map<String, AutomaticWeaponPlacementProposal> assigned = assign(fixture);
        Map<Integer, Long> widths = widths(assigned);
        int targetRanks = AutomaticWeaponBranchLayerPlanner.targetRankCount(
                assigned.size(), fixture.policy());
        int firstFamilyRank = AutomaticWeaponBranchLayerPlanner.sharedRankCount(targetRanks);
        int sharedMeshEnd = ResearchTechTreeContract.sharedMeshTransitionCount(widths.size());

        assertEquals(287, assigned.size());
        assertEquals(19, targetRanks);
        assertTrue(firstFamilyRank <= sharedMeshEnd,
                "family tapering should overlap the final multi-parent mesh ranks");
        assertTrue(widths.size() >= targetRanks);
        assertTrue(widths.values().stream().allMatch(width -> width <= 20));
        assertTrue(fixture.model().branches().stream().allMatch(branch ->
                branch.memberBlueprintIds().stream()
                        .filter(id -> assigned.get(id).progressionCoordinate().rank()
                                >= firstFamilyRank)
                        .collect(java.util.stream.Collectors.groupingBy(id ->
                                assigned.get(id).progressionCoordinate().rank()))
                        .values().stream().anyMatch(level -> level.size() > 1)));
    }

    @Test
    void maximumPopulationRemainsWidthRankAndIterationBounded() {
        Fixture forward = fixture(List.of(4096), false, 20);
        Fixture reverse = fixture(List.of(4096), true, 20);

        Map<String, AutomaticWeaponPlacementProposal> first = assign(forward);
        Map<String, AutomaticWeaponPlacementProposal> second = assign(reverse);

        assertEquals(first, second);
        Map<Integer, Long> widths = widths(first);
        assertEquals(4096, first.size());
        assertTrue(widths.values().stream().allMatch(width -> width <= 20));
        assertTrue(widths.size() <= ResearchTechTreeContract.MAX_PROGRESSION_RANK + 1);
        assertTrue(widths.size() > divideRoundUp(4096, 20),
                "large trees should reserve modest taper room instead of filling every row");
    }

    @Test
    void deferredEquivalentWeaponsOccupyTheLayerImmediatelyBelowTheApex() {
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            AutomaticWeaponRoleSignature signature = signature(
                    "branch_layer:tied_" + index,
                    90,
                    0,
                    "rifle",
                    index);
            signatures.put(signature.blueprintId(), signature);
            proposals.put(signature.blueprintId(), new AutomaticWeaponPlacementProposal(
                    signature.blueprintId(),
                    signature.mechanicalScore(),
                    signature.confidence(),
                    new ProgressionPosition(
                            Tier.forScore(signature.mechanicalScore()),
                            ResearchTechTreeContract.levelForScore(
                                    signature.mechanicalScore(), 3),
                            index),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                8,
                List.of(),
                2);
        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer().discover(
                signatures,
                Map.of(),
                AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(8));

        Map<String, AutomaticWeaponPlacementProposal> assigned =
                new AutomaticWeaponBranchLayerPlanner().assign(
                        proposals, signatures, Map.of(), model, policy);
        AutomaticWeaponTerminalCluster cluster =
                model.branches().get(0).terminalCluster();
        int terminalRank = assigned.get(cluster.terminalBlueprintIds().get(0))
                .progressionCoordinate().rank();

        assertTrue(cluster.truncated());
        assertEquals(3, cluster.terminalBlueprintIds().size());
        assertEquals(2, cluster.deferredEquivalentCount());
        assertTrue(cluster.terminalBlueprintIds().stream().allMatch(id ->
                assigned.get(id).progressionCoordinate().rank() == terminalRank));
        assertTrue(cluster.deferredEquivalentBlueprintIds().stream().allMatch(id ->
                assigned.get(id).progressionCoordinate().rank() == terminalRank - 1));
    }

    private static Map<String, AutomaticWeaponPlacementProposal> assign(Fixture fixture) {
        return new AutomaticWeaponBranchLayerPlanner().assign(
                fixture.proposals(),
                fixture.signatures(),
                Map.of(),
                fixture.model(),
                fixture.policy());
    }

    private static Fixture fixture(
            List<Integer> branchSizes,
            boolean reverse,
            int width) {
        List<AutomaticWeaponRoleSignature> values = new ArrayList<>();
        int sequence = 0;
        for (int branch = 0; branch < branchSizes.size(); branch++) {
            int count = branchSizes.get(branch);
            for (int index = 0; index < count; index++) {
                String blueprintId = "branch_layer:family_" + branch + "_weapon_" + index;
                int score = 10 + branch * 12
                        + index * (72 - branch * 8) / Math.max(1, count - 1);
                values.add(signature(
                        blueprintId,
                        Math.min(100, score),
                        -75 + branch * 150 / Math.max(1, branchSizes.size() - 1),
                        "family_" + branch,
                        sequence++));
            }
        }
        if (reverse) {
            Collections.reverse(values);
        }
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (AutomaticWeaponRoleSignature signature : values) {
            signatures.put(signature.blueprintId(), signature);
            long siblingOrder = Integer.toUnsignedLong(signature.blueprintId().hashCode());
            proposals.put(signature.blueprintId(), new AutomaticWeaponPlacementProposal(
                    signature.blueprintId(),
                    signature.mechanicalScore(),
                    signature.confidence(),
                    new ProgressionPosition(
                            Tier.forScore(signature.mechanicalScore()),
                            ResearchTechTreeContract.levelForScore(
                                    signature.mechanicalScore(), 3),
                            siblingOrder),
                    3,
                    ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    List.of()));
        }
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                60,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                width,
                List.of(),
                2);
        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer().discover(
                signatures,
                Map.of(),
                AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(width));
        return new Fixture(proposals, signatures, model, policy);
    }

    private static AutomaticWeaponRoleSignature signature(
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
                Math.min(100, score + sequence % 2),
                offsets,
                true,
                List.of());
    }

    private static Map<Integer, Long> widths(
            Map<String, AutomaticWeaponPlacementProposal> proposals) {
        return proposals.values().stream().collect(
                java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
    }

    private static int divideRoundUp(int value, int divisor) {
        return 1 + (value - 1) / divisor;
    }

    private static net.minecraft.resources.ResourceLocation id(String value) {
        return new net.minecraft.resources.ResourceLocation(value);
    }

    private record Fixture(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, AutomaticWeaponRoleSignature> signatures,
            AutomaticWeaponBranchModel model,
            AutomaticWeaponPlacementPolicy policy) {
    }
}
