package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

import net.minecraft.resources.ResourceLocation;

/** Strength-relative mechanical role evidence for one automatic weapon candidate. */
public record AutomaticWeaponRoleSignature(
        String blueprintId,
        int mechanicalScore,
        int confidence,
        String archetype,
        boolean explosive,
        int strengthBaseline,
        Map<String, Integer> relativeMetricOffsets,
        boolean scoredEvidence,
        List<String> branchSeedBlockReasons) {
    static final int METRIC_SIMILARITY_WEIGHT = 85;
    static final int ARCHETYPE_SIMILARITY_WEIGHT = 15;
    private static final int EXPLOSIVE_MISMATCH_PENALTY = 10;

    public AutomaticWeaponRoleSignature {
        if (!validText(blueprintId) || ResourceLocation.tryParse(blueprintId) == null
                || mechanicalScore < 0
                || mechanicalScore > ResearchTechTreeContract.SCORE_MAX
                || confidence < 0
                || confidence > ResearchTechTreeContract.SCORE_MAX
                || !validText(archetype)
                || strengthBaseline < 0
                || strengthBaseline > ResearchTechTreeContract.SCORE_MAX
                || relativeMetricOffsets == null
                || branchSeedBlockReasons == null) {
            throw new IllegalArgumentException("Automatic weapon role signature is invalid");
        }
        archetype = archetype.toLowerCase(Locale.ROOT);
        relativeMetricOffsets = immutableOffsets(relativeMetricOffsets);
        if (branchSeedBlockReasons.stream().anyMatch(reason -> !validText(reason))) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch-seed reason is invalid");
        }
        branchSeedBlockReasons = branchSeedBlockReasons.stream().distinct().sorted().toList();
        if (!scoredEvidence && branchSeedBlockReasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unscored automatic weapon role signatures cannot seed branches");
        }
    }

    public boolean maySeedBranch() {
        return scoredEvidence && branchSeedBlockReasons.isEmpty();
    }

    /**
     * Returns confidence-independent role similarity, or empty when either role
     * lacks scored mechanical evidence.
     */
    public OptionalInt similarityTo(AutomaticWeaponRoleSignature other) {
        if (other == null || !scoredEvidence || !other.scoredEvidence) {
            return OptionalInt.empty();
        }
        long weightedDistance = 0L;
        int totalWeight = 0;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int left = relativeMetricOffsets.get(metric.serializedName());
            int right = other.relativeMetricOffsets.get(metric.serializedName());
            weightedDistance = Math.addExact(
                    weightedDistance,
                    Math.multiplyExact((long) metric.weight(), Math.abs(left - right)));
            totalWeight = Math.addExact(totalWeight, metric.weight());
        }
        int normalizedDistance = Math.min(
                ResearchTechTreeContract.SCORE_MAX,
                roundedDivide(weightedDistance, totalWeight));
        int metricSimilarity = ResearchTechTreeContract.SCORE_MAX - normalizedDistance;
        int archetypeSimilarity = archetype.equals(other.archetype)
                ? ResearchTechTreeContract.SCORE_MAX
                : 0;
        int combined = roundedDivide(
                Math.addExact(
                        Math.multiplyExact((long) metricSimilarity, METRIC_SIMILARITY_WEIGHT),
                        Math.multiplyExact((long) archetypeSimilarity,
                                ARCHETYPE_SIMILARITY_WEIGHT)),
                100L);
        if (explosive != other.explosive) {
            combined -= EXPLOSIVE_MISMATCH_PENALTY;
        }
        return OptionalInt.of(Math.max(0, Math.min(ResearchTechTreeContract.SCORE_MAX, combined)));
    }

    private static Map<String, Integer> immutableOffsets(Map<String, Integer> source) {
        Set<String> expected = java.util.Arrays.stream(MechanicalMetric.values())
                .map(MechanicalMetric::serializedName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!source.keySet().equals(expected)) {
            throw new IllegalArgumentException(
                    "Automatic weapon role signature must contain every mechanical metric");
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            Integer value = source.get(metric.serializedName());
            if (value == null
                    || value < -ResearchTechTreeContract.SCORE_MAX
                    || value > ResearchTechTreeContract.SCORE_MAX) {
                throw new IllegalArgumentException(
                        "Automatic weapon relative metric offset is out of bounds");
            }
            copy.put(metric.serializedName(), value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static int roundedDivide(long numerator, long denominator) {
        if (numerator < 0L || denominator <= 0L) {
            throw new IllegalArgumentException(
                    "Automatic weapon role division inputs are invalid");
        }
        return Math.toIntExact(Math.addExact(numerator, denominator / 2L) / denominator);
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
