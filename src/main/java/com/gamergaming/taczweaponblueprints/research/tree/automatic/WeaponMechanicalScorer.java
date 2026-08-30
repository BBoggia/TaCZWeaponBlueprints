package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.MechanicalRating;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.MechanicalMetric.Component;

/** Pure appeal-free scorer. It performs no TaCZ lookup and creates no progression authority. */
public final class WeaponMechanicalScorer {
    public static final int NEUTRAL_METRIC_SCORE = 50;
    public static final int MIN_REFERENCE_SAMPLES = 2;
    public static final int SCRIPT_CONFIDENCE_CAP = 50;

    public WeaponMechanicalScore score(
            WeaponStatEvidence evidence,
            WeaponMetricReference reference) {
        if (evidence == null || reference == null) {
            throw new IllegalArgumentException(
                    "Weapon mechanical scorer inputs cannot be null");
        }
        WeaponMechanicalMetrics metrics = WeaponMechanicalMetrics.derive(evidence);
        Map<String, Double> observed = new LinkedHashMap<>();
        Map<String, Double> resolved = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(evidence.warnings());
        int availableConfidenceWeight = 0;

        for (MechanicalMetric metric : MechanicalMetric.values()) {
            var observedValue = metrics.value(metric);
            observedValue.ifPresent(value -> observed.put(metric.serializedName(), value));
            reference.median(metric).ifPresent(value ->
                    resolved.put(metric.serializedName(),
                            observedValue.isPresent() ? observedValue.getAsDouble() : value));

            int metricScore = NEUTRAL_METRIC_SCORE;
            if (observedValue.isEmpty()) {
                warnings.add("missing_metric:" + metric.serializedName());
            } else if (reference.sampleCount(metric) == 0) {
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
                warnings.add("missing_reference:" + metric.serializedName());
            } else if (reference.sampleCount(metric) < MIN_REFERENCE_SAMPLES) {
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
                warnings.add("insufficient_reference:" + metric.serializedName());
            } else {
                metricScore = reference.percentile(metric, observedValue.getAsDouble());
                if (metric.lowerIsBetter()) {
                    metricScore = ResearchTechTreeContract.SCORE_MAX - metricScore;
                }
                availableConfidenceWeight += confidenceWeight(metric);
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
            }
            scores.put(metric.serializedName(), metricScore);
        }

        int combat = componentScore(Component.COMBAT, scores);
        int utility = componentScore(Component.UTILITY, scores);
        int confidence = roundedDivide(availableConfidenceWeight, 100);
        if (evidence.scriptControlled()) {
            confidence = Math.min(confidence, SCRIPT_CONFIDENCE_CAP);
            warnings.add("script_controlled");
        }
        MechanicalRating rating = new MechanicalRating(
                combat,
                utility,
                confidence,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                reference.version());
        return new WeaponMechanicalScore(
                evidence, rating, observed, resolved, scores, warnings);
    }

    private static int componentScore(
            Component component,
            Map<String, Integer> scores) {
        int weighted = 0;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            if (metric.component() == component) {
                weighted += metric.weight() * scores.get(metric.serializedName());
            }
        }
        return roundedDivide(weighted, 100);
    }

    private static int confidenceWeight(MechanicalMetric metric) {
        int componentWeight = metric.component() == Component.COMBAT
                ? ResearchTechTreeContract.AUTOMATIC_COMBAT_WEIGHT
                : ResearchTechTreeContract.AUTOMATIC_UTILITY_WEIGHT;
        return componentWeight * metric.weight();
    }

    private static int roundedDivide(int numerator, int denominator) {
        return (numerator + denominator / 2) / denominator;
    }
}
