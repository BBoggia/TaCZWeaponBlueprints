package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

/** Complete explainable result of the non-authoritative v3 capability scorer. */
public record WeaponCapabilityScore(
        WeaponStatEvidence evidence,
        int progressionScore,
        int combatStrength,
        int handling,
        int versatility,
        int confidence,
        String formulaVersion,
        String referenceVersion,
        Map<WeaponCapabilityPackage, Integer> packageScores,
        Map<WeaponCapabilityPackage, Integer> packageConfidence,
        Map<String, Double> observedMetrics,
        Map<String, Double> resolvedMetrics,
        Map<String, Integer> metricScores,
        List<String> warnings) {
    public WeaponCapabilityScore {
        if (evidence == null || !score(progressionScore) || !score(combatStrength)
                || !score(handling) || !score(versatility) || !score(confidence)
                || !validText(formulaVersion) || !validText(referenceVersion)
                || packageScores == null || packageConfidence == null
                || observedMetrics == null || resolvedMetrics == null
                || metricScores == null || warnings == null) {
            throw new IllegalArgumentException("Weapon capability score is invalid");
        }
        packageScores = immutablePackages(packageScores);
        packageConfidence = immutablePackages(packageConfidence);
        if (!packageScores.keySet().equals(packageConfidence.keySet())
                || packageScores.values().stream().anyMatch(value -> !score(value))
                || packageConfidence.values().stream().anyMatch(value -> !score(value))) {
            throw new IllegalArgumentException("Weapon capability packages are invalid");
        }
        observedMetrics = immutableDoubles(observedMetrics);
        resolvedMetrics = immutableDoubles(resolvedMetrics);
        metricScores = immutableScores(metricScores);
        if (warnings.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Weapon capability warnings are invalid");
        }
        warnings = warnings.stream().distinct().sorted().toList();
    }

    public Tier suggestedTier() {
        return Tier.forScore(progressionScore);
    }

    public int suggestedLevel(int levelsPerTier) {
        return ResearchTechTreeContract.levelForScore(progressionScore, levelsPerTier);
    }

    private static Map<WeaponCapabilityPackage, Integer> immutablePackages(
            Map<WeaponCapabilityPackage, Integer> source) {
        EnumMap<WeaponCapabilityPackage, Integer> copy =
                new EnumMap<>(WeaponCapabilityPackage.class);
        copy.putAll(source);
        if (copy.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null)) {
            throw new IllegalArgumentException("Weapon capability package map is invalid");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Double> immutableDoubles(Map<String, Double> source) {
        Map<String, Double> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!validText(key) || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Weapon capability metric map is invalid");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableScores(Map<String, Integer> source) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!validText(key) || value == null || !score(value)) {
                throw new IllegalArgumentException("Weapon capability score map is invalid");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean score(int value) {
        return value >= 0 && value <= 100;
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
