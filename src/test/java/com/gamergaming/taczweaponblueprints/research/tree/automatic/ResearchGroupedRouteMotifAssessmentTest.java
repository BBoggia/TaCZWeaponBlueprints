package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteMotifAssessment;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteQualityAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;

class ResearchGroupedRouteMotifAssessmentTest {
    @Test
    void representativeGroupedCatalogRetainsCurrentRoutes() {
        var forward = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon(), false);
        var reversed = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon(), true);

        var assessment = ResearchGroupedRouteMotifAssessment.assess(
                forward.quality(), forward.topology());
        var reversedAssessment = ResearchGroupedRouteMotifAssessment.assess(
                reversed.quality(), reversed.topology());

        assertTrue(assessment.available());
        assertEquals(
                ResearchGroupedRouteMotifAssessment.Decision
                        .RETAIN_CURRENT_GROUPED_ROUTES,
                assessment.decision());
        assertEquals(0, assessment.decisiveSignalCount());
        assertTrue(assessment.recommendedMotifs().isEmpty());
        assertEquals(9, assessment.ladderP95ReviewLimit());
        assertEquals(6, forward.quality().branchEntries().size());
        assertTrue(forward.quality().branchEntries().stream()
                .allMatch(branch -> branch.distinctEntranceCount() >= 2));
        assertEquals(5, assessment.signal(
                ResearchGroupedRouteMotifAssessment.SignalCode
                        .SINGLE_ROUTE_LADDER_P95).observed());
        assertEquals(10_000L, assessment.signal(
                ResearchGroupedRouteMotifAssessment.SignalCode
                        .ROUTE_COST_RATIO_P95).observed());
        assertFalse(assessment.visualEvidence().postJunctionMeasurementAvailable());
        assertTrue(assessment.visualEvidence().preJunctionApproximateCrossingCount() > 0L);
        assertTrue(assessment.visualEvidence().manualReviewRequired());
        assertEquals(assessment, reversedAssessment);
    }

    @Test
    void adverseSemanticEvidenceRecommendsOnlyTargetedMotifs() {
        var evidence = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon(), false);
        ResearchGroupedRouteQualityAudit.Audit quality = evidence.quality();
        ResearchGroupedRouteQualityAudit.AlternativeEvidence alternatives =
                quality.alternatives();
        ResearchGroupedRouteQualityAudit.Audit adverse = new ResearchGroupedRouteQualityAudit.Audit(
                true,
                quality.interpretation(),
                quality.weaponNodeCount(),
                quality.automaticTargetCount(),
                quality.matchedAutomaticTargetCount(),
                quality.unmatchedAutomaticTargetCount(),
                quality.alternativeGroupCount(),
                quality.effectiveAlternativeGroupCount() - 1,
                new ResearchGroupedRouteQualityAudit.AlternativeEvidence(
                        alternatives.groupCount(),
                        alternatives.effectiveGroupCount() - 1,
                        1,
                        alternatives.exactRouteCostGroupCount(),
                        alternatives.zeroCostImbalancedGroupCount(),
                        alternatives.mandatoryAncestryOverlapBasisPoints(),
                        alternatives.ancestryDivergenceBasisPoints(),
                        alternatives.routeCostRatioLowerBoundBasisPoints(),
                        new ResearchGroupedRouteQualityAudit.LongDistribution(
                                alternatives.groupCount(),
                                10_000L,
                                10_000L,
                                30_000L,
                                50_000L,
                                90_000L)),
                quality.mandatoryAncestorSharesBasisPoints(),
                new ResearchGroupedRouteQualityAudit.IntDistribution(
                        5, 1, 3, 8, 12, 20),
                quality.phases(),
                List.of(new ResearchGroupedRouteQualityAudit.BranchEntrySummary(
                        0, 1, 1, 0, 0, 0, 10_000)),
                new ResearchGroupedRouteQualityAudit.IntDistribution(
                        1, 0, 0, 0, 0, 0),
                new ResearchGroupedRouteQualityAudit.IntDistribution(
                        1, 10_000, 10_000, 10_000, 10_000, 10_000),
                quality.maximumFinitePointIncome(),
                quality.terminalRoutes(),
                quality.unaffordableTerminalCount(),
                quality.indeterminateTerminalCount(),
                quality.warnings());

        var assessment = ResearchGroupedRouteMotifAssessment.assess(
                adverse, evidence.topology());

        assertEquals(
                ResearchGroupedRouteMotifAssessment.Decision
                        .PROTOTYPE_TARGETED_MOTIFS,
                assessment.decision());
        assertEquals(7, assessment.decisiveSignalCount());
        assertEquals(List.of(
                ResearchGroupedRouteMotifAssessment.Motif.STAGGERED_DIAMONDS,
                ResearchGroupedRouteMotifAssessment.Motif.MULTI_ENTRY_BRANCH_FANS,
                ResearchGroupedRouteMotifAssessment.Motif.BRANCH_LOCAL_DIAMONDS,
                ResearchGroupedRouteMotifAssessment.Motif.COST_BALANCED_ALTERNATIVES),
                assessment.recommendedMotifs());
    }

    @Test
    void missingOrMismatchedEvidenceCannotAuthorizeMotifs() {
        var evidence = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.small(), false);

        assertEquals(
                ResearchGroupedRouteMotifAssessment.Assessment.EMPTY,
                ResearchGroupedRouteMotifAssessment.assess(
                        ResearchGroupedRouteQualityAudit.Audit.EMPTY,
                        evidence.topology()));
        var mismatch = ResearchGroupedRouteMotifAssessment.assess(
                evidence.quality(), ResearchTechTreeTopologyAudit.Audit.EMPTY);
        assertFalse(mismatch.available());
        assertEquals(
                ResearchGroupedRouteMotifAssessment.Decision.INSUFFICIENT_EVIDENCE,
                mismatch.decision());
        assertFalse(mismatch.motifPrototypeRecommended());
    }

    @Test
    void authorityDriftProducesNoPrototypeRecommendation() {
        var evidence = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon(), false);
        ResearchGroupedRouteQualityAudit.Audit quality = evidence.quality();
        List<ResearchGroupedRouteQualityAudit.PhaseSummary> phases =
                new java.util.ArrayList<>(quality.phases());
        ResearchGroupedRouteQualityAudit.PhaseSummary specialization =
                phases.get(phases.size() - 1);
        phases.set(phases.size() - 1, new ResearchGroupedRouteQualityAudit.PhaseSummary(
                specialization.phase(),
                specialization.targetCount() - 1,
                specialization.alternativeGroupCount(),
                specialization.effectiveAlternativeGroupCount(),
                specialization.sameFamilyAlternativeGroupCount(),
                specialization.crossFamilyAlternativeGroupCount(),
                specialization.unclassifiedAlternativeGroupCount(),
                specialization.parentFanOut(),
                specialization.mandatoryAncestorSharesBasisPoints()));
        ResearchGroupedRouteQualityAudit.Audit drift = copy(
                quality,
                quality.matchedAutomaticTargetCount() - 1,
                quality.unmatchedAutomaticTargetCount() + 1,
                phases);

        var assessment = ResearchGroupedRouteMotifAssessment.assess(
                drift, evidence.topology());

        assertTrue(assessment.available());
        assertEquals(
                ResearchGroupedRouteMotifAssessment.Decision.INSUFFICIENT_EVIDENCE,
                assessment.decision());
        assertTrue(assessment.recommendedMotifs().isEmpty());
        assertFalse(assessment.motifPrototypeRecommended());
    }

    @Test
    void rejectsNullInputsAndUsesScaleAwareLadderLimit() {
        var evidence = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteEvidence(
                AutomaticWeaponTopologyPhaseZeroFixture.small(), false);

        assertThrows(IllegalArgumentException.class, () ->
                ResearchGroupedRouteMotifAssessment.assess(null, evidence.topology()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchGroupedRouteMotifAssessment.assess(evidence.quality(), null));
    }

    private static ResearchGroupedRouteQualityAudit.Audit copy(
            ResearchGroupedRouteQualityAudit.Audit quality,
            int matchedAutomaticTargetCount,
            int unmatchedAutomaticTargetCount,
            List<ResearchGroupedRouteQualityAudit.PhaseSummary> phases) {
        return new ResearchGroupedRouteQualityAudit.Audit(
                quality.available(),
                quality.interpretation(),
                quality.weaponNodeCount(),
                quality.automaticTargetCount(),
                matchedAutomaticTargetCount,
                unmatchedAutomaticTargetCount,
                quality.alternativeGroupCount(),
                quality.effectiveAlternativeGroupCount(),
                quality.alternatives(),
                quality.mandatoryAncestorSharesBasisPoints(),
                quality.singleRouteChainLengths(),
                phases,
                quality.branchEntries(),
                quality.branchEntryRedundancy(),
                quality.branchEntryAncestryOverlapBasisPoints(),
                quality.maximumFinitePointIncome(),
                quality.terminalRoutes(),
                quality.unaffordableTerminalCount(),
                quality.indeterminateTerminalCount(),
                quality.warnings());
    }
}
