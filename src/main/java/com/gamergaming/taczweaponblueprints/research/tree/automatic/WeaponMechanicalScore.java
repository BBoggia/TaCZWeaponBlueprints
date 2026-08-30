package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.MechanicalRating;

/** Auditable result from the pure mechanical scoring core. */
public record WeaponMechanicalScore(
        WeaponStatEvidence evidence,
        MechanicalRating rating,
        Map<String, Double> observedMetrics,
        Map<String, Double> resolvedMetrics,
        Map<String, Integer> metricScores,
        List<String> warnings) {
    public WeaponMechanicalScore {
        if (evidence == null || rating == null
                || observedMetrics == null || resolvedMetrics == null
                || metricScores == null || warnings == null) {
            throw new IllegalArgumentException("Weapon mechanical score fields cannot be null");
        }
        observedMetrics = immutableDoubleMap(observedMetrics);
        resolvedMetrics = immutableDoubleMap(resolvedMetrics);
        metricScores = immutableScoreMap(metricScores);
        if (warnings.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "Weapon mechanical score warnings cannot contain blank values");
        }
        warnings = warnings.stream().distinct().sorted().toList();
    }

    public int score() {
        return rating.score();
    }

    private static Map<String, Double> immutableDoubleMap(Map<String, Double> values) {
        LinkedHashMap<String, Double> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Weapon mechanical score metric map is invalid");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableScoreMap(Map<String, Integer> values) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 0 || value > 100) {
                throw new IllegalArgumentException(
                        "Weapon mechanical metric score map is invalid");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
