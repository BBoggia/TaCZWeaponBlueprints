package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import org.junit.jupiter.api.Test;

class AnalyzerBalanceModelTest {
    @Test void geometricFullBlueprintCostAndNoResultOnly() {
        var e = AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(1, 0, 3, 0, 0), 0);
        assertEquals(4, e.expectedAttempts());
        assertTrue(e.maximumAttempts().isEmpty());
        assertFalse(AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(0, 0, 1, 0, 0), 0).reachable());
        assertEquals(1, AnalyzerBalanceModel.estimate(AnalyzerOutcomeTable.GUARANTEED_BLUEPRINT, 0).maximumAttempts().orElseThrow());
    }
    @Test void uniformFragmentsIncludeOvershootAndBothEndpoints() {
        var e = AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(0, 1, 0, 1, 2), 3);
        assertEquals(2.25, e.expectedAttempts());
        assertEquals(3, e.maximumAttempts().orElseThrow());
        assertEquals(1, AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(0, 1, 0, 5, 64), 3).expectedAttempts());
    }
    @Test void fullSuccessCompetesWithFragmentCompletionInsteadOfIgnoringIt() {
        var e = AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(1, 1, 0, 1, 1), 3);
        assertEquals(1.75, e.expectedAttempts());
        assertEquals(3, e.maximumAttempts().orElseThrow());
        var withFailure = AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(1, 1, 2, 1, 1), 3);
        assertEquals(3.5, withFailure.expectedAttempts());
        assertTrue(withFailure.maximumAttempts().isEmpty());
    }
    @Test void rollingWindowMatchesIndependentSmallStateEnumeration() {
        for (int full = 0; full <= 3; full++) for (int noResult = 0; noResult <= 3; noResult++)
            for (int min = 1; min <= 4; min++) for (int max = min; max <= 6; max++) {
                var table = new AnalyzerOutcomeTable(full, 2, noResult, min, max);
                double[] attempts = new double[31];
                for (int r = 1; r <= 30; r++) {
                    double a = 1;
                    for (int k = min; k <= max; k++) {
                        a += table.fragmentProbability() / (max-min+1) * attempts[Math.max(0, r-k)];
                    }
                    attempts[r] = a / (1-table.noResultProbability());
                    var actual = AnalyzerBalanceModel.estimate(table, r);
                    assertEquals(attempts[r], actual.expectedAttempts(), 1e-10);
                }
            }
    }
    @Test void extremeWeightsAndThresholdStayFiniteWithoutLargeStateAllocation() {
        var e = AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(0, 1, 1_000_000_000, 1, 1), 1_000_000);
        assertEquals(1_000_000 * 1_000_000_001d, e.expectedAttempts(), 1);
        assertTrue(Double.isFinite(e.expectedAttempts() * 1_000_000_000d));
        assertThrows(IllegalArgumentException.class, () -> AnalyzerBalanceModel.estimate(new AnalyzerOutcomeTable(0, 1, 0, 1, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> AnalyzerBalanceModel.estimate(AnalyzerOutcomeTable.GUARANTEED_BLUEPRINT, 1_000_001));
    }
}
