package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

class AutomaticWeaponBranchAnalyzerTest {
    @Test
    void targetCapacityGrowsWithPopulationAndRemainsBounded() {
        assertEquals(0, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(0));
        assertEquals(1, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(6));
        assertEquals(2, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(7));
        assertEquals(2, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(24));
        assertEquals(3, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(25));
        assertEquals(3, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(54));
        assertEquals(4, AutomaticWeaponBranchAnalyzer.targetBranchCapacity(55));
        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_BRANCHES,
                AutomaticWeaponBranchAnalyzer.targetBranchCapacity(4096));
        assertEquals(4, AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(8));
        assertEquals(8, AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(16));
        assertEquals(10, AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(20));
    }

    @Test
    void skewedPopulationPreservesMinorityRolesAndInputOrder() {
        Map<String, AutomaticWeaponRoleSignature> forward = signatures(
                "skewed_roles", false);
        Map<String, AutomaticWeaponRoleSignature> reverse = signatures(
                "skewed_roles", true);

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(forward);
        assertEquals(model, new AutomaticWeaponBranchAnalyzer().discover(reverse));
        assertEquals(37, model.candidateCount());
        assertEquals(37, model.seedSignatureCount());
        assertEquals(3, model.branchCapacity());
        assertEquals(3, model.branches().size());
        assertEquals(List.of(2, 5, 30), model.branches().stream()
                .map(branch -> branch.memberBlueprintIds().size())
                .sorted().toList());

        for (String archetype : List.of("launcher", "rifle", "sniper")) {
            List<AutomaticWeaponBranchModel.Branch> matching = model.branches().stream()
                    .filter(branch -> branch.medoidBlueprintId()
                            .map(forward::get)
                            .map(AutomaticWeaponRoleSignature::archetype)
                            .filter(archetype::equals)
                            .isPresent())
                    .toList();
            assertEquals(1, matching.size());
            assertTrue(matching.get(0).memberBlueprintIds().stream()
                    .allMatch(id -> forward.get(id).archetype().equals(archetype)));
        }
    }

    @Test
    void equivalentTerminalTiesRemainOneUnorderedPeerCohort() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                "terminal_ties", false);
        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);

        assertEquals(1, model.branchCapacity());
        assertEquals(1, model.branches().size());
        assertTrue(signatures.keySet().containsAll(
                model.branches().get(0).terminalBlueprintIds()));
        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_TERMINAL_PEERS,
                model.branches().get(0).terminalBlueprintIds().size());
        assertEquals(AutomaticWeaponTerminalCluster.Resolution.TRUNCATED,
                model.branches().get(0).terminalCluster().resolution());
        assertEquals(2,
                model.branches().get(0).terminalCluster().deferredEquivalentCount());
        assertEquals(AutomaticWeaponTerminalCluster.TRUNCATED_DIAGNOSTIC,
                model.branches().get(0).terminalCluster().diagnostic().orElseThrow());
    }

    @Test
    void uncertainCandidatesCannotCreateBranchesOrTerminalCohorts() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                "low_confidence", false);
        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);

        assertEquals(0, model.seedSignatureCount());
        assertEquals(1, model.branches().size());
        assertTrue(model.branches().get(0).medoidBlueprintId().isEmpty());
        assertTrue(model.branches().get(0).terminalBlueprintIds().isEmpty());
        assertEquals(signatures.keySet(), model.branchIndexByBlueprint().keySet());
    }

    @Test
    void unscoredFallbackJoinsAnExistingArchetypeWithoutChangingBranchCount() {
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>(
                signatures("skewed_roles", false));
        AutomaticWeaponRoleSignature fallback = fallback("test:unscored_rifle", "rifle");
        AutomaticWeaponBranchModel before = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);
        signatures.put(fallback.blueprintId(), fallback);

        AutomaticWeaponBranchModel after = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);

        assertEquals(before.branches().size(), after.branches().size());
        AutomaticWeaponBranchModel.Branch branch = after.branchFor(
                fallback.blueprintId()).orElseThrow();
        assertEquals("rifle", branch.medoidBlueprintId()
                .map(signatures::get)
                .map(AutomaticWeaponRoleSignature::archetype)
                .orElseThrow());
        assertFalse(branch.terminalBlueprintIds().contains(fallback.blueprintId()));
    }

    @Test
    void identicalLargePopulationDoesNotSplitToFillCapacity() {
        AutomaticWeaponRoleSignature template = signatures(
                "terminal_ties", false).values().iterator().next();
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        for (int index = 0; index < 100; index++) {
            String id = "identical:weapon_" + index;
            signatures.put(id, new AutomaticWeaponRoleSignature(
                    id,
                    template.mechanicalScore(),
                    template.confidence(),
                    template.archetype(),
                    template.explosive(),
                    template.strengthBaseline(),
                    template.relativeMetricOffsets(),
                    true,
                    List.of()));
        }

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);

        assertEquals(5, model.branchCapacity());
        assertEquals(1, model.branches().size());
        assertEquals(100, model.branches().get(0).memberBlueprintIds().size());
        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_TERMINAL_PEERS,
                model.branches().get(0).terminalBlueprintIds().size());
        assertTrue(model.branches().get(0).terminalCluster().truncated());
        assertEquals(97,
                model.branches().get(0).terminalCluster().deferredEquivalentCount());
        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_LAYOUT_STRANDS_PER_BRANCH,
                model.branches().get(0).layoutStrandCount());
    }

    @Test
    void maximumPopulationRemainsExhaustiveAndBounded() {
        AutomaticWeaponRoleSignature template = signatures(
                "terminal_ties", false).values().iterator().next();
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        for (int index = 0; index < WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS;
                index++) {
            String id = "maximum:weapon_" + index;
            signatures.put(id, new AutomaticWeaponRoleSignature(
                    id,
                    template.mechanicalScore(),
                    template.confidence(),
                    template.archetype(),
                    template.explosive(),
                    template.strengthBaseline(),
                    template.relativeMetricOffsets(),
                    true,
                    List.of()));
        }

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures);

        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_BRANCHES, model.branchCapacity());
        assertEquals(1, model.branches().size());
        assertEquals(AutomaticWeaponBranchAnalyzer.MAX_TERMINAL_PEERS,
                model.branches().get(0).terminalBlueprintIds().size());
        assertTrue(model.branches().get(0).terminalCluster().truncated());
        assertEquals(
                WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                        - AutomaticWeaponBranchAnalyzer.MAX_TERMINAL_PEERS,
                model.branches().get(0).terminalCluster().deferredEquivalentCount());
        assertEquals(signatures.keySet(), model.branchIndexByBlueprint().keySet());
        assertTrue(model.matches(signatures));
    }

    @Test
    void largeUntrustedPopulationUsesBalancedNeutralFallbackFamilies() {
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        for (int index = 0; index < 144; index++) {
            AutomaticWeaponRoleSignature value = fallback(
                    "fallback:weapon_" + index,
                    index % 2 == 0 ? "rifle" : "smg");
            signatures.put(value.blueprintId(), value);
        }

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures, Map.of(), 8);

        assertEquals(5, model.branchCapacity());
        assertEquals(5, model.branches().size());
        assertTrue(model.branches().stream()
                .allMatch(branch -> branch.medoidBlueprintId().isEmpty()
                        && branch.terminalBlueprintIds().isEmpty()));
        int smallest = model.branches().stream()
                .mapToInt(branch -> branch.memberBlueprintIds().size()).min().orElseThrow();
        int largest = model.branches().stream()
                .mapToInt(branch -> branch.memberBlueprintIds().size()).max().orElseThrow();
        assertTrue(largest - smallest <= 1);
    }

    @Test
    void authoredEvidenceCanAnchorFamiliesWithoutMovingAuthoredWeapons() {
        Map<String, AutomaticWeaponRoleSignature> source = signatures(
                "skewed_roles", false);
        Map<String, AutomaticWeaponRoleSignature> authored = Map.of(
                "phase_zero:skewed_rifle_0", source.get("phase_zero:skewed_rifle_0"),
                "phase_zero:skewed_sniper_30", source.get("phase_zero:skewed_sniper_30"));
        Map<String, AutomaticWeaponRoleSignature> automatic = new LinkedHashMap<>();
        automatic.put(
                "phase_zero:skewed_rifle_1",
                source.get("phase_zero:skewed_rifle_1"));
        for (int index = 1; index < 20; index++) {
            AutomaticWeaponRoleSignature value = fallback(
                    "automatic:weapon_" + index,
                    index < 10 ? "rifle" : "sniper");
            automatic.put(value.blueprintId(), value);
        }

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(automatic, authored, 8);

        assertEquals(3, model.seedSignatureCount());
        assertEquals(2, model.branches().size());
        assertEquals(authored.keySet(), model.branches().stream()
                .flatMap(branch -> branch.authoredAnchorBlueprintIds().stream())
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(model.branches().stream().anyMatch(branch ->
                branch.medoidBlueprintId().filter(authored::containsKey).isPresent()));
        assertTrue(model.branchFor("phase_zero:skewed_rifle_1").orElseThrow()
                .authoredAnchorBlueprintIds().contains("phase_zero:skewed_rifle_0"));
        assertTrue(model.matches(automatic, authored));
    }

    @Test
    void equallySimilarCandidatesDoNotAllFallIntoTheFirstBranch() {
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        signatures.put("balance:left", shaped("balance:left", 40, true));
        signatures.put("balance:right", shaped("balance:right", -40, true));
        for (int index = 0; index < 80; index++) {
            String id = "balance:middle_" + index;
            signatures.put(id, shaped(id, 0, false));
        }

        AutomaticWeaponBranchModel model = new AutomaticWeaponBranchAnalyzer()
                .discover(signatures, Map.of(), 8);

        assertEquals(2, model.branches().size());
        assertTrue(model.branches().stream()
                .allMatch(branch -> branch.memberBlueprintIds().size() > 25));
    }

    @Test
    void addingAnUntrustedWeaponPreservesExistingStableFamilyKeys() {
        Map<String, AutomaticWeaponRoleSignature> beforeSignatures = new LinkedHashMap<>(
                signatures("skewed_roles", false));
        AutomaticWeaponBranchModel before = new AutomaticWeaponBranchAnalyzer()
                .discover(beforeSignatures, Map.of(), 8);
        Map<String, AutomaticWeaponRoleSignature> afterSignatures =
                new LinkedHashMap<>(beforeSignatures);
        AutomaticWeaponRoleSignature added = fallback("aaa:new_weapon", "rifle");
        afterSignatures.put(added.blueprintId(), added);
        AutomaticWeaponBranchModel after = new AutomaticWeaponBranchAnalyzer()
                .discover(afterSignatures, Map.of(), 8);

        for (String id : beforeSignatures.keySet()) {
            assertEquals(
                    before.branchFor(id).orElseThrow().stableKey(),
                    after.branchFor(id).orElseThrow().stableKey());
        }
    }

    private static Map<String, AutomaticWeaponRoleSignature> signatures(
            String name,
            boolean reverse) {
        return AutomaticWeaponTopologyPhaseZeroFixture.roleSignatures(name, reverse);
    }

    private static AutomaticWeaponRoleSignature fallback(String blueprintId, String archetype) {
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                blueprintId,
                50,
                0,
                new ProgressionPosition(
                        Tier.forScore(50),
                        ResearchTechTreeContract.levelForScore(50, 3),
                        1L),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of("unscored_fallback"));
        return new AutomaticWeaponRoleAnalyzer().analyze(
                Map.of(blueprintId, proposal),
                Map.of(),
                Map.of(blueprintId, archetype))
                .get(blueprintId);
    }

    private static AutomaticWeaponRoleSignature shaped(
            String blueprintId,
            int direction,
            boolean trusted) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int value = metric.component() == MechanicalMetric.Component.COMBAT
                    ? direction : -direction;
            offsets.put(metric.serializedName(), value);
        }
        return new AutomaticWeaponRoleSignature(
                blueprintId,
                50,
                trusted ? 100 : 50,
                "rifle",
                false,
                50,
                offsets,
                true,
                trusted ? List.of() : List.of("low_confidence"));
    }
}
