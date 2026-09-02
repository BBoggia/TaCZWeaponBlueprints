package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WeaponMechanicalScorerTest {
    private static final String REFERENCE_VERSION = "test-mechanical-reference-v1";

    @Test
    void strongerCompleteEvidenceScoresHigherWithoutAppeal() {
        WeaponStatEvidence weak = weapon(
                "test:weak", 4.0, 300.0, 8, 3.0, 0.7, 5.0, false);
        WeaponStatEvidence middle = weapon(
                "test:middle", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false);
        WeaponStatEvidence strong = weapon(
                "test:strong", 14.0, 900.0, 40, 1.2, 0.1, 2.0, false);
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(weak, middle, strong));
        WeaponMechanicalScorer scorer = new WeaponMechanicalScorer();

        WeaponMechanicalScore weakScore = scorer.score(weak, reference);
        WeaponMechanicalScore middleScore = scorer.score(middle, reference);
        WeaponMechanicalScore strongScore = scorer.score(strong, reference);

        assertTrue(weakScore.score() < middleScore.score());
        assertTrue(middleScore.score() < strongScore.score());
        assertEquals(100, weakScore.rating().confidence());
        assertEquals(100, middleScore.rating().confidence());
        assertEquals(100, strongScore.rating().confidence());
        assertEquals(REFERENCE_VERSION, strongScore.rating().referenceVersion());
    }

    @Test
    void referenceAndScoresAreDeterministicAcrossPopulationOrder() {
        WeaponStatEvidence weak = weapon(
                "test:weak", 4.0, 300.0, 8, 3.0, 0.7, 5.0, false);
        WeaponStatEvidence middle = weapon(
                "test:middle", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false);
        WeaponStatEvidence strong = weapon(
                "test:strong", 14.0, 900.0, 40, 1.2, 0.1, 2.0, false);
        WeaponMetricReference first = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(strong, weak, middle));
        WeaponMetricReference second = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(middle, strong, weak));

        assertEquals(first.distributions(), second.distributions());
        WeaponMechanicalScorer scorer = new WeaponMechanicalScorer();
        assertEquals(scorer.score(middle, first), scorer.score(middle, second));
    }

    @Test
    void missingMetricsUseReferenceMediansAndReduceConfidence() {
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION,
                List.of(
                        weapon("test:weak", 4.0, 300.0, 8, 3.0, 0.7, 5.0, false),
                        weapon("test:strong", 14.0, 900.0, 40, 1.0, 0.1, 2.0, false)));
        WeaponStatEvidence incomplete = new WeaponStatEvidence(
                "test:incomplete",
                "special",
                6.0,
                0.0,
                300.0,
                null,
                null,
                80.0,
                40.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                0,
                null,
                "inventory",
                false,
                false,
                List.of("extractor:ammo_amount"));

        WeaponMechanicalScore score = new WeaponMechanicalScorer().score(
                incomplete, reference);

        assertEquals(50, score.metricScores().get("magazine_capacity"));
        assertEquals(24.0, score.resolvedMetrics().get("magazine_capacity"));
        assertTrue(score.rating().confidence() < 100);
        assertTrue(score.warnings().contains("missing_metric:magazine_capacity"));
        assertTrue(score.warnings().contains("extractor:ammo_amount"));
    }

    @Test
    void scriptedEvidenceHasAnExplicitConfidenceCeiling() {
        WeaponStatEvidence scripted = weapon(
                "test:scripted", 10.0, 600.0, 30, 2.0, 0.2, 3.0, true);
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION,
                List.of(
                        scripted,
                        weapon("test:reference", 8.0, 500.0, 20, 2.5, 0.3, 4.0, false)));

        WeaponMechanicalScore score = new WeaponMechanicalScorer().score(scripted, reference);

        assertEquals(WeaponMechanicalScorer.SCRIPT_CONFIDENCE_CAP,
                score.rating().confidence());
        assertTrue(score.warnings().contains("script_controlled"));
    }

    @Test
    void oneSampleReferenceRemainsNeutralAndCannotClaimConfidence() {
        WeaponStatEvidence only = weapon(
                "test:only", 10.0, 600.0, 30, 2.0, 0.2, 3.0, false);
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(only));

        WeaponMechanicalScore score = new WeaponMechanicalScorer().score(only, reference);

        assertEquals(50, score.score());
        assertEquals(0, score.rating().confidence());
        assertTrue(score.warnings().contains("insufficient_reference:sustained_dps"));
    }

    @Test
    void explosionOnlyDamageRemainsUsableEvidence() {
        WeaponStatEvidence launcher = new WeaponStatEvidence(
                "test:launcher",
                "special",
                null,
                24.0,
                60.0,
                1,
                2.0,
                80.0,
                40.0,
                0.0,
                1.0,
                0,
                0.4,
                0.6,
                5.0,
                0.2,
                0.4,
                -0.4,
                1,
                0,
                null,
                "magazine",
                true,
                false,
                List.of());

        WeaponMechanicalMetrics metrics = WeaponMechanicalMetrics.derive(launcher);

        assertEquals(24.0, metrics.value(MechanicalMetric.EFFECTIVE_DAMAGE).orElseThrow());
        assertEquals(12.0, metrics.value(MechanicalMetric.SUSTAINED_DPS).orElseThrow());
    }

    @Test
    void manualActionAndReloadTimeLimitSustainedDamage() {
        WeaponStatEvidence manual = new WeaponStatEvidence(
                "test:manual",
                "sniper",
                20.0,
                0.0,
                600.0,
                2,
                2.0,
                200.0,
                80.0,
                0.0,
                1.5,
                1,
                0.2,
                0.4,
                4.0,
                0.1,
                1.0,
                -0.2,
                1,
                2,
                1.0,
                "magazine",
                false,
                false,
                List.of());

        double sustained = WeaponMechanicalMetrics.derive(manual)
                .value(MechanicalMetric.SUSTAINED_DPS)
                .orElseThrow();
        assertEquals(40.0 / 3.0, sustained, 0.0001);
    }

    @Test
    void headshotMultiplierContributesToCombatScore() {
        WeaponStatEvidence low = weapon(
                "test:low_headshot", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false, 1.0);
        WeaponStatEvidence high = weapon(
                "test:high_headshot", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false, 2.0);
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(low, high));
        WeaponMechanicalScorer scorer = new WeaponMechanicalScorer();

        assertTrue(scorer.score(low, reference).rating().combat()
                < scorer.score(high, reference).rating().combat());
    }

    @Test
    void referencePercentilesAreTieAwareAndInterpolateExternalValues() {
        WeaponMetricReference reference = WeaponMetricReference.fromMetricValues(
                REFERENCE_VERSION,
                Map.of(
                        MechanicalMetric.EFFECTIVE_DAMAGE,
                        List.of(0.0, 10.0, 10.0, 20.0)));

        assertEquals(0, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, -1.0));
        assertEquals(17, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, 5.0));
        assertEquals(50, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, 10.0));
        assertEquals(83, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, 15.0));
        assertEquals(100, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, 21.0));
        assertEquals(10.0, reference.median(MechanicalMetric.EFFECTIVE_DAMAGE)
                .orElseThrow());
    }

    @Test
    void referenceMathRemainsFiniteAtDoubleExtremes() {
        WeaponMetricReference reference = WeaponMetricReference.fromMetricValues(
                REFERENCE_VERSION,
                Map.of(MechanicalMetric.EFFECTIVE_DAMAGE,
                        List.of(-Double.MAX_VALUE, Double.MAX_VALUE)));

        assertEquals(50, reference.percentile(MechanicalMetric.EFFECTIVE_DAMAGE, 0.0));
        assertEquals(0.0, reference.median(MechanicalMetric.EFFECTIVE_DAMAGE).orElseThrow());

        WeaponMetricReference positive = WeaponMetricReference.fromMetricValues(
                REFERENCE_VERSION,
                Map.of(MechanicalMetric.EFFECTIVE_DAMAGE,
                        List.of(Math.nextDown(Double.MAX_VALUE), Double.MAX_VALUE)));
        assertTrue(Double.isFinite(
                positive.median(MechanicalMetric.EFFECTIVE_DAMAGE).orElseThrow()));
    }

    @Test
    void equalEvidenceRemainsNeutralAndAdversarialValuesAreRejected() {
        WeaponStatEvidence first = weapon(
                "test:first", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false);
        WeaponStatEvidence second = weapon(
                "test:second", 8.0, 600.0, 20, 2.0, 0.3, 3.0, false);
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                REFERENCE_VERSION, List.of(first, second));
        WeaponMechanicalScore score = new WeaponMechanicalScorer().score(first, reference);
        assertEquals(50, score.score());
        assertTrue(score.metricScores().values().stream().allMatch(value -> value == 50));

        assertThrows(IllegalArgumentException.class,
                () -> WeaponMetricReference.fromEvidence(
                        REFERENCE_VERSION, List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> WeaponMetricReference.fromEvidence("bad version", List.of(first)));
        assertThrows(IllegalArgumentException.class,
                () -> new WeaponStatEvidence(
                        "test:bad", "rifle", Double.NaN, 0.0, 600.0,
                        20, 2.0, 100.0, 50.0, 0.0, 1.0, 0,
                        0.2, 0.3, 3.0, 0.1, 0.1, 0.0, 1, 1, null,
                        "magazine", false, false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new WeaponStatEvidence(
                        "test:bad", "rifle", 5.0, 0.0, -1.0,
                        20, 2.0, 100.0, 50.0, 0.0, 1.0, 0,
                        0.2, 0.3, 3.0, 0.1, 0.1, 0.0, 1, 1, null,
                        "magazine", false, false, List.of()));
    }

    private static WeaponStatEvidence weapon(
            String id,
            double damage,
            double rpm,
            int capacity,
            double reload,
            double inaccuracy,
            double weight,
            boolean scriptControlled) {
        return weapon(id, damage, rpm, capacity, reload, inaccuracy, weight,
                scriptControlled, 1.5);
    }

    private static WeaponStatEvidence weapon(
            String id,
            double damage,
            double rpm,
            int capacity,
            double reload,
            double inaccuracy,
            double weight,
            boolean scriptControlled,
            double headshotMultiplier) {
        return new WeaponStatEvidence(
                id,
                "rifle",
                damage,
                0.0,
                rpm,
                capacity,
                reload,
                200.0,
                80.0,
                0.1,
                headshotMultiplier,
                1,
                0.2,
                0.4,
                weight,
                inaccuracy,
                inaccuracy,
                -0.2,
                2,
                4,
                null,
                "magazine",
                false,
                scriptControlled,
                List.of());
    }
}
