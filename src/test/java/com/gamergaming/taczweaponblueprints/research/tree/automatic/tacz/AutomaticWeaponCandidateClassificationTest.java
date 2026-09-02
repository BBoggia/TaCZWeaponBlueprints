package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponRoleAnalyzer;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponCandidateClassificationTest {
    private static final ResourceLocation TREE = id("test:tree");
    private static final AutomaticWeaponPlacementPolicy POLICY =
            new AutomaticWeaponPlacementPolicy(
                    3,
                    60,
                    AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                    2,
                    4,
                    AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                    8,
                    List.of(),
                    1);

    @Test
    void preservesASortedImmutablePreTopologyPartition() {
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        proposals.put("test:b", proposal("test:b", 20, 2L));
        proposals.put("test:a", proposal("test:a", 10, 1L));
        AutomaticWeaponCandidateClassification classification = classification(
                proposals,
                Map.of("test:excluded", "missing_mechanical_score"),
                Set.of("test:authored"));

        proposals.clear();

        assertEquals(List.of("test:a", "test:b"),
                List.copyOf(classification.eligibleProposals().keySet()));
        assertEquals(3, classification.automaticCandidateCount());
        assertEquals(Set.of("test:authored"), classification.authoredBlueprintIds());
        assertEquals(classification.eligibleProposals().keySet(),
                classification.roleSignatures().keySet());
        assertEquals(classification.authoredBlueprintIds(),
                classification.authoredRoleSignatures().keySet());
        assertTrue(classification.branchModel().matches(classification.roleSignatures()));
        assertThrows(UnsupportedOperationException.class, () ->
                classification.eligibleProposals().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                classification.roleSignatures().clear());
    }

    @Test
    void rejectsOverlappingCategoriesAndAlreadyPositionedProposals() {
        AutomaticWeaponPlacementProposal raw = proposal("test:a", 10, 1L);
        assertThrows(IllegalArgumentException.class, () -> classification(
                Map.of("test:a", raw),
                Map.of("test:a", "excluded"),
                Set.of()));

        AutomaticWeaponPlacementProposal positioned = raw.withProgressionCoordinate(
                new ProgressionCoordinate(7, raw.position().siblingOrder(), java.util.Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> classification(
                Map.of("test:a", positioned),
                Map.of(),
                Set.of()));
        var rawSignatures = new AutomaticWeaponRoleAnalyzer().analyze(
                Map.of("test:a", raw),
                Map.of(),
                Map.of("test:a", "unknown"));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponCandidateClassification(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        POLICY,
                        1L,
                        1L,
                        1,
                        Map.of("test:a", raw),
                        rawSignatures,
                        Map.of(),
                        com.gamergaming.taczweaponblueprints.research.tree.automatic
                                .AutomaticWeaponBranchModel.EMPTY,
                        Map.of(),
                        Set.of(),
                        Set.of()));
    }

    @Test
    void positioningIsASeparateDeterministicTransformation() {
        AutomaticWeaponCandidateClassification classification = classification(
                Map.of(
                        "test:strong", proposal("test:strong", 90, 3L),
                        "test:weak", proposal("test:weak", 10, 1L)),
                Map.of(),
                Set.of());

        AutomaticWeaponPlacementCandidateSnapshot positioned =
                AutomaticWeaponCandidatePositioner.position(classification, null);

        assertEquals(0, positioned.eligibleProposals().get("test:weak")
                .progressionCoordinate().rank());
        assertEquals(1, positioned.eligibleProposals().get("test:strong")
                .progressionCoordinate().rank());
        assertNotEquals(
                positioned.eligibleProposals().get("test:strong").progressionCoordinate(),
                classification.eligibleProposals().get("test:strong").progressionCoordinate());
        assertEquals(POLICY, positioned.policy());
        assertEquals(classification.authoredBlueprintIds(), positioned.authoredBlueprintIds());
        assertEquals(classification.excludedAutomaticCandidates(),
                positioned.excludedAutomaticCandidates());
    }

    @Test
    void allAuthoredCatalogKeepsRoleContextWithoutInventingAutomaticBranches() {
        AutomaticWeaponCandidateClassification classification = classification(
                Map.of(), Map.of(), Set.of("test:authored"));

        assertEquals(Set.of("test:authored"),
                classification.authoredRoleSignatures().keySet());
        assertEquals(
                com.gamergaming.taczweaponblueprints.research.tree.automatic
                        .AutomaticWeaponBranchModel.EMPTY,
                classification.branchModel());
        assertTrue(AutomaticWeaponCandidatePositioner.position(classification, null)
                .eligibleProposals().isEmpty());
    }

    @Test
    void publicationRetainsTheExactPreTopologyClassification() {
        AutomaticWeaponCandidateClassification classification = classification(
                Map.of("test:automatic", proposal("test:automatic", 50, 1L)),
                Map.of(),
                Set.of("test:authored"));
        AutomaticWeaponPlacementCandidateSnapshot positioned =
                AutomaticWeaponCandidatePositioner.position(classification, null);

        var publication = new AutomaticWeaponPlacementCandidateManager.Publication(
                1L,
                1L,
                Map.of(TREE, classification),
                Map.of(TREE, positioned),
                Map.of(),
                1L);

        assertEquals(classification, publication.classificationsByTree().get(TREE));
        assertThrows(UnsupportedOperationException.class,
                () -> publication.classificationsByTree().clear());
    }

    @Test
    void currentConnectedPublicationRequiresCompleteCanonicalAndRankEvidence() {
        ResourceLocation automatic = id("test:automatic");
        AutomaticWeaponCandidateClassification classification = classification(
                Map.of(automatic.toString(), proposal(automatic.toString(), 50, 1L)),
                Map.of(),
                Set.of());
        AutomaticWeaponPlacementCandidateSnapshot positioned =
                AutomaticWeaponCandidatePositioner.position(classification, null);
        int publishedRank = positioned.eligibleProposal(automatic).orElseThrow()
                .progressionCoordinate().rank();
        AutomaticWeaponPrerequisiteDecision plannedDecision =
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
        AutomaticWeaponPrerequisitePlan complete = new AutomaticWeaponPrerequisitePlan(
                id("test:profile"),
                TREE,
                AutomaticPlacementMode.CONNECTED,
                1L,
                1L,
                1,
                Map.of(),
                Map.of(automatic, "generated_root"),
                Map.of(automatic, plannedDecision.withPublishedRank(publishedRank)),
                Map.of(automatic, new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                        0, 0, 0, 0)));

        new AutomaticWeaponPlacementCandidateManager.Publication(
                1L,
                1L,
                Map.of(TREE, classification),
                Map.of(TREE, positioned),
                Map.of(complete.profileId(), complete),
                1L);

        AutomaticWeaponPrerequisitePlan missingCoordinates =
                new AutomaticWeaponPrerequisitePlan(
                        complete.profileId(),
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        1,
                        Map.of(),
                        Map.of(automatic, "generated_root"),
                        complete.decisions());
        AutomaticWeaponPrerequisitePlan missingPublishedRank =
                new AutomaticWeaponPrerequisitePlan(
                        complete.profileId(),
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        1,
                        Map.of(),
                        Map.of(automatic, "generated_root"),
                        Map.of(automatic, plannedDecision),
                        complete.branchCoordinates());

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateManager.Publication(
                        1L,
                        1L,
                        Map.of(TREE, classification),
                        Map.of(TREE, positioned),
                        Map.of(complete.profileId(), missingCoordinates),
                        1L));
        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPlacementCandidateManager.Publication(
                        1L,
                        1L,
                        Map.of(TREE, classification),
                        Map.of(TREE, positioned),
                        Map.of(complete.profileId(), missingPublishedRank),
                        1L));
    }

    @Test
    void legacyConnectedPublicationDoesNotFabricateCanonicalBranchEvidence() {
        ResourceLocation automatic = id("test:legacy_automatic");
        AutomaticWeaponPlacementPolicy legacyPolicy =
                new AutomaticWeaponPlacementPolicy(
                        3,
                        60,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED);
        AutomaticWeaponCandidateClassification dynamic = classification(
                Map.of(automatic.toString(),
                        proposal(automatic.toString(), 50, 1L)),
                Map.of(),
                Set.of());
        AutomaticWeaponCandidateClassification legacy =
                new AutomaticWeaponCandidateClassification(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        legacyPolicy,
                        1L,
                        1L,
                        1,
                        dynamic.eligibleProposals(),
                        dynamic.roleSignatures(),
                        dynamic.authoredRoleSignatures(),
                        new AutomaticWeaponBranchAnalyzer().discover(
                                dynamic.roleSignatures(),
                                dynamic.authoredRoleSignatures(),
                                AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                                        legacyPolicy.maxNodesPerRank())),
                        Map.of(),
                        Set.of(),
                        Set.of());
        AutomaticWeaponPlacementCandidateSnapshot positioned =
                AutomaticWeaponCandidatePositioner.position(legacy, null);
        AutomaticWeaponPrerequisitePlan compatible =
                new AutomaticWeaponPrerequisitePlan(
                        id("test:profile"),
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        1,
                        Map.of(),
                        Map.of(automatic, "generated_root"));

        new AutomaticWeaponPlacementCandidateManager.Publication(
                1L,
                1L,
                Map.of(TREE, legacy),
                Map.of(TREE, positioned),
                Map.of(compatible.profileId(), compatible),
                1L);
    }

    @Test
    void canonicalCoordinatesRejectBranchlessDecisionProvenance() {
        ResourceLocation automatic = id("test:branchless");
        AutomaticWeaponPrerequisiteDecision branchlessDecision =
                new AutomaticWeaponPrerequisiteDecision(
                        automatic,
                        AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                        Optional.empty(),
                        0,
                        0,
                        0,
                        1,
                        Map.of(),
                        false,
                        false)
                        .withPublishedRank(0);

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponPrerequisitePlan(
                        id("test:profile"),
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        1L,
                        1L,
                        1,
                        Map.of(),
                        Map.of(automatic, "generated_root"),
                        Map.of(automatic, branchlessDecision),
                        Map.of(automatic,
                                new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                        0, 0, 0, 0))));
    }

    @Test
    void classificationRejectsAnInternallyConsistentButNonCanonicalBranchAssignment() {
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (int index = 0; index < 7; index++) {
            String id = "test:fallback_" + index;
            proposals.put(id, proposal(id, 20 + index, index));
        }
        var roleSignatures = new AutomaticWeaponRoleAnalyzer().analyze(
                proposals,
                Map.of(),
                proposals.keySet().stream().collect(java.util.stream.Collectors.toMap(
                        value -> value,
                        ignored -> "unknown")));
        int branchLimit = AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                POLICY.maxNodesPerRank());
        var canonical = new AutomaticWeaponBranchAnalyzer().discover(
                roleSignatures, Map.of(), branchLimit);
        var first = canonical.branches().get(0);
        var second = canonical.branches().get(1);
        java.util.ArrayList<String> firstMembers =
                new java.util.ArrayList<>(first.memberBlueprintIds());
        java.util.ArrayList<String> secondMembers =
                new java.util.ArrayList<>(second.memberBlueprintIds());
        String fromFirst = firstMembers.remove(0);
        String fromSecond = secondMembers.remove(0);
        firstMembers.add(fromSecond);
        secondMembers.add(fromFirst);
        var wrongBranches = List.of(
                new com.gamergaming.taczweaponblueprints.research.tree.automatic
                        .AutomaticWeaponBranchModel.Branch(
                                first.index(),
                                first.stableKey(),
                                first.medoidBlueprintId(),
                                firstMembers,
                                first.terminalBlueprintIds(),
                                first.terminalCluster(),
                                first.authoredAnchorBlueprintIds(),
                                first.layoutStrandCount()),
                new com.gamergaming.taczweaponblueprints.research.tree.automatic
                        .AutomaticWeaponBranchModel.Branch(
                                second.index(),
                                second.stableKey(),
                                second.medoidBlueprintId(),
                                secondMembers,
                                second.terminalBlueprintIds(),
                                second.terminalCluster(),
                                second.authoredAnchorBlueprintIds(),
                                second.layoutStrandCount()));
        Map<String, Integer> wrongAssignments = new LinkedHashMap<>();
        firstMembers.forEach(id -> wrongAssignments.put(id, 0));
        secondMembers.forEach(id -> wrongAssignments.put(id, 1));
        var wrong = new com.gamergaming.taczweaponblueprints.research.tree.automatic
                .AutomaticWeaponBranchModel(
                        canonical.candidateCount(),
                        canonical.seedSignatureCount(),
                        canonical.branchLimit(),
                        canonical.branchCapacity(),
                        wrongBranches,
                        wrongAssignments);
        assertTrue(wrong.matches(roleSignatures));

        assertThrows(IllegalArgumentException.class, () ->
                new AutomaticWeaponCandidateClassification(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        POLICY,
                        1L,
                        1L,
                        proposals.size(),
                        proposals,
                        roleSignatures,
                        Map.of(),
                        wrong,
                        Map.of(),
                        Set.of(),
                        Set.of()));
    }

    private static AutomaticWeaponCandidateClassification classification(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, String> excluded,
            Set<String> authored) {
        var roleSignatures = new AutomaticWeaponRoleAnalyzer().analyze(
                proposals,
                Map.of(),
                proposals.keySet().stream().collect(
                        java.util.stream.Collectors.toMap(
                                value -> value,
                                ignored -> "unknown")));
        var authoredRoleSignatures = new AutomaticWeaponRoleAnalyzer().analyzeAuthored(
                authored,
                Map.of(),
                authored.stream().collect(java.util.stream.Collectors.toMap(
                        value -> value,
                        ignored -> "unknown")));
        return new AutomaticWeaponCandidateClassification(
                TREE,
                AutomaticPlacementMode.CONNECTED,
                POLICY,
                1L,
                1L,
                proposals.size() + excluded.size() + authored.size(),
                proposals,
                roleSignatures,
                authoredRoleSignatures,
                roleSignatures.isEmpty()
                        ? com.gamergaming.taczweaponblueprints.research.tree.automatic
                                .AutomaticWeaponBranchModel.EMPTY
                        : new AutomaticWeaponBranchAnalyzer().discover(
                                roleSignatures,
                                authoredRoleSignatures,
                                AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                                        POLICY.maxNodesPerRank())),
                excluded,
                authored,
                Set.of());
    }

    private static AutomaticWeaponPlacementProposal proposal(
            String blueprintId,
            int score,
            long siblingOrder) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId,
                score,
                100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, POLICY.levelsPerTier()),
                        siblingOrder),
                POLICY.levelsPerTier(),
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
