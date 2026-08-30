package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

class AutomaticWeaponRoleAnalyzerTest {
    @Test
    void separatesEqualStrengthWeaponsWithDifferentMechanicalRoles() {
        List<WeaponMechanicalScore> scores = cases("equal_power_different_roles");
        Map<String, AutomaticWeaponRoleSignature> signatures = analyze(scores, false);
        AutomaticWeaponRoleSignature close = signature(signatures, scores.get(0));
        AutomaticWeaponRoleSignature ranged = signature(signatures, scores.get(1));

        assertEquals(close.mechanicalScore(), ranged.mechanicalScore());
        assertNotEquals(close.relativeMetricOffsets(), ranged.relativeMetricOffsets());
        assertEquals(65, close.similarityTo(ranged).orElseThrow());
        assertTrue(close.maySeedBranch());
        assertTrue(ranged.maySeedBranch());
    }

    @Test
    void removesAbsoluteStrengthFromOtherwiseEquivalentRoles() {
        List<WeaponMechanicalScore> scores = cases("same_role_different_power");
        Map<String, AutomaticWeaponRoleSignature> signatures = analyze(scores, false);
        AutomaticWeaponRoleSignature weak = signature(signatures, scores.get(0));
        AutomaticWeaponRoleSignature strong = signature(signatures, scores.get(1));

        assertTrue(weak.mechanicalScore() < strong.mechanicalScore());
        assertEquals(weak.relativeMetricOffsets(), strong.relativeMetricOffsets());
        assertEquals(100, weak.similarityTo(strong).orElseThrow());
    }

    @Test
    void preservesEquivalentTerminalCandidatesWithoutInventingAnOrder() {
        List<WeaponMechanicalScore> scores = cases("terminal_ties");
        List<AutomaticWeaponRoleSignature> signatures =
                new ArrayList<>(analyze(scores, false).values());

        assertEquals(5, signatures.size());
        assertEquals(1L, signatures.stream()
                .map(AutomaticWeaponRoleSignature::mechanicalScore).distinct().count());
        assertEquals(1L, signatures.stream()
                .map(AutomaticWeaponRoleSignature::relativeMetricOffsets).distinct().count());
        assertTrue(signatures.stream().allMatch(AutomaticWeaponRoleSignature::maySeedBranch));
        assertTrue(signatures.stream().allMatch(value ->
                value.similarityTo(signatures.get(0)).orElseThrow() == 100));
    }

    @Test
    void lowConfidenceAndScriptControlledEvidenceCannotSeedBranches() {
        List<WeaponMechanicalScore> scores = cases("low_confidence");
        Map<String, AutomaticWeaponRoleSignature> signatures = analyze(scores, false);
        AutomaticWeaponRoleSignature incomplete = signature(signatures, scores.get(0));
        AutomaticWeaponRoleSignature scripted = signature(signatures, scores.get(1));

        assertFalse(incomplete.maySeedBranch());
        assertTrue(incomplete.branchSeedBlockReasons().contains("low_confidence"));
        assertFalse(scripted.maySeedBranch());
        assertTrue(scripted.branchSeedBlockReasons().contains("low_confidence"));
        assertTrue(scripted.branchSeedBlockReasons().contains("script_controlled"));
    }

    @Test
    void skewedPopulationsAndInputOrderDoNotChangeRoleSignatures() {
        List<WeaponMechanicalScore> scores = cases("skewed_roles");
        Map<String, AutomaticWeaponRoleSignature> forward = analyze(scores, false);
        Map<String, AutomaticWeaponRoleSignature> reverse = analyze(scores, true);

        assertEquals(forward, reverse);
        assertEquals(37, forward.size());
        List<AutomaticWeaponRoleSignature> rifles = signaturesForPrefix(
                forward, "phase_zero:skewed_rifle_");
        List<AutomaticWeaponRoleSignature> snipers = signaturesForPrefix(
                forward, "phase_zero:skewed_sniper_");
        List<AutomaticWeaponRoleSignature> launchers = signaturesForPrefix(
                forward, "phase_zero:skewed_launcher_");
        assertEquals(30, rifles.size());
        assertEquals(5, snipers.size());
        assertEquals(2, launchers.size());
        assertEquals(1L, rifles.stream()
                .map(AutomaticWeaponRoleSignature::relativeMetricOffsets).distinct().count());
        assertEquals(100, rifles.get(0).similarityTo(rifles.get(1)).orElseThrow());
        assertTrue(rifles.get(0).similarityTo(snipers.get(0)).orElseThrow()
                < rifles.get(0).similarityTo(rifles.get(1)).orElseThrow());
        assertTrue(rifles.get(0).similarityTo(launchers.get(0)).orElseThrow()
                < rifles.get(0).similarityTo(rifles.get(1)).orElseThrow());
    }

    @Test
    void unscoredFallbacksReceiveNeutralNonSeedingSignatures() {
        AutomaticWeaponPlacementProposal proposal = proposal(
                "test:unscored", 45, 0, 0, List.of("unscored_fallback"));
        AutomaticWeaponRoleSignature signature = new AutomaticWeaponRoleAnalyzer().analyze(
                Map.of(proposal.blueprintId(), proposal),
                Map.of(),
                Map.of(proposal.blueprintId(), "shotgun"))
                .get(proposal.blueprintId());

        assertFalse(signature.scoredEvidence());
        assertFalse(signature.maySeedBranch());
        assertEquals("shotgun", signature.archetype());
        assertTrue(signature.relativeMetricOffsets().values().stream()
                .allMatch(value -> value == 0));
        assertTrue(signature.similarityTo(signature).isEmpty());
    }

    @Test
    void authoredWeaponsRetainExhaustiveReadOnlyRoleContext() {
        WeaponMechanicalScore scored = cases("same_role_different_power").get(0);
        String missing = "test:authored_without_evidence";

        Map<String, AutomaticWeaponRoleSignature> signatures =
                new AutomaticWeaponRoleAnalyzer().analyzeAuthored(
                        java.util.Set.of(scored.evidence().blueprintId(), missing),
                        Map.of(scored.evidence().blueprintId(), scored),
                        Map.of(
                                scored.evidence().blueprintId(), "rifle",
                                missing, "shotgun"));

        assertEquals(2, signatures.size());
        assertTrue(signatures.get(scored.evidence().blueprintId()).scoredEvidence());
        assertFalse(signatures.get(missing).maySeedBranch());
        assertEquals("shotgun", signatures.get(missing).archetype());
        assertTrue(signatures.get(missing).branchSeedBlockReasons()
                .contains("unscored_authored_role_evidence"));
    }

    private static Map<String, AutomaticWeaponRoleSignature> analyze(
            List<WeaponMechanicalScore> original,
            boolean reverse) {
        List<WeaponMechanicalScore> scores = new ArrayList<>(original);
        if (reverse) {
            Collections.reverse(scores);
        }
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        Map<String, WeaponMechanicalScore> scoreMap = new LinkedHashMap<>();
        Map<String, String> archetypes = new LinkedHashMap<>();
        for (WeaponMechanicalScore score : scores) {
            String blueprintId = score.evidence().blueprintId();
            List<String> reviewReasons = new ArrayList<>();
            if (score.rating().confidence()
                    < AutomaticWeaponRoleAnalyzer.MIN_BRANCH_SEED_CONFIDENCE) {
                reviewReasons.add("low_confidence");
            }
            if (score.evidence().scriptControlled()) {
                reviewReasons.add("script_controlled");
            }
            proposals.put(blueprintId, proposal(
                    blueprintId,
                    score.score(),
                    score.rating().confidence(),
                    Integer.toUnsignedLong(blueprintId.hashCode()),
                    reviewReasons));
            scoreMap.put(blueprintId, score);
            archetypes.put(blueprintId, score.evidence().archetype());
        }
        return new AutomaticWeaponRoleAnalyzer().analyze(proposals, scoreMap, archetypes);
    }

    private static AutomaticWeaponPlacementProposal proposal(
            String blueprintId,
            int score,
            int confidence,
            long siblingOrder,
            List<String> reviewReasons) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId,
                score,
                confidence,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        siblingOrder),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                reviewReasons);
    }

    private static AutomaticWeaponRoleSignature signature(
            Map<String, AutomaticWeaponRoleSignature> signatures,
            WeaponMechanicalScore score) {
        return signatures.get(score.evidence().blueprintId());
    }

    private static List<AutomaticWeaponRoleSignature> signaturesForPrefix(
            Map<String, AutomaticWeaponRoleSignature> signatures,
            String prefix) {
        return signatures.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static List<WeaponMechanicalScore> cases(String name) {
        return AutomaticWeaponTopologyPhaseZeroFixture.mechanicalCases().get(name);
    }
}
