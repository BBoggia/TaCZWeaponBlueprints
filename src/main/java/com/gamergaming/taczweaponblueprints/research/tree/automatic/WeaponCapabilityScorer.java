package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/**
 * Versioned v3 scorer. It rewards a weapon's strongest supported combat modes
 * without allowing one isolated statistic to erase all of its trade-offs.
 */
public final class WeaponCapabilityScorer {
    public static final int NEUTRAL_METRIC_SCORE = 50;
    public static final int MIN_REFERENCE_SAMPLES = 2;
    public static final int SCRIPT_CONFIDENCE_CAP = 50;
    /** Maps the empirically compressed package blend onto the full progression scale. */
    static final int PROGRESSION_CALIBRATION_FLOOR = 25;
    static final int PROGRESSION_CALIBRATION_NUMERATOR = 5;
    static final int PROGRESSION_CALIBRATION_DENOMINATOR = 3;

    private static final Map<WeaponCapabilityPackage, List<MetricWeight>> PACKAGES =
            packages();

    public WeaponCapabilityScore score(
            WeaponStatEvidence evidence,
            WeaponCapabilityReference reference) {
        if (evidence == null || reference == null) {
            throw new IllegalArgumentException("Weapon capability scorer inputs cannot be null");
        }
        WeaponCapabilityMetrics metrics = WeaponCapabilityMetrics.derive(evidence);
        Map<String, Double> observed = new LinkedHashMap<>();
        Map<String, Double> resolved = new LinkedHashMap<>();
        Map<String, Integer> metricScores = new LinkedHashMap<>();
        Set<CapabilityMetric> confidentMetrics =
                java.util.EnumSet.noneOf(CapabilityMetric.class);
        List<String> warnings = new ArrayList<>(evidence.warnings());

        for (CapabilityMetric metric : CapabilityMetric.values()) {
            if (!applicable(metric, evidence)) {
                continue;
            }
            var observedValue = metrics.value(metric);
            observedValue.ifPresent(value -> observed.put(metric.serializedName(), value));
            reference.median(metric).ifPresent(value -> resolved.put(
                    metric.serializedName(),
                    observedValue.isPresent() ? observedValue.getAsDouble() : value));

            int metricScore = NEUTRAL_METRIC_SCORE;
            if (observedValue.isEmpty()) {
                warnings.add("missing_capability_metric:" + metric.serializedName());
            } else if (reference.sampleCount(metric) == 0) {
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
                warnings.add("missing_capability_reference:" + metric.serializedName());
            } else if (reference.sampleCount(metric) < MIN_REFERENCE_SAMPLES) {
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
                warnings.add("insufficient_capability_reference:" + metric.serializedName());
            } else {
                metricScore = reference.percentile(metric, observedValue.getAsDouble());
                if (metric.lowerIsBetter()) {
                    metricScore = ResearchTechTreeContract.SCORE_MAX - metricScore;
                }
                confidentMetrics.add(metric);
                resolved.put(metric.serializedName(), observedValue.getAsDouble());
            }
            metricScores.put(metric.serializedName(), metricScore);
        }

        EnumMap<WeaponCapabilityPackage, Integer> packageScores =
                new EnumMap<>(WeaponCapabilityPackage.class);
        EnumMap<WeaponCapabilityPackage, Integer> packageConfidence =
                new EnumMap<>(WeaponCapabilityPackage.class);
        for (WeaponCapabilityPackage capabilityPackage : WeaponCapabilityPackage.values()) {
            if (capabilityPackage == WeaponCapabilityPackage.AREA_CONTROL
                    && !evidence.explosive()) {
                continue;
            }
            PackageResult result = packageResult(
                    PACKAGES.get(capabilityPackage), metricScores, confidentMetrics, evidence);
            packageScores.put(capabilityPackage, result.score());
            packageConfidence.put(capabilityPackage, result.confidence());
        }

        List<Integer> combatPackages = packageScores.entrySet().stream()
                .filter(entry -> entry.getKey().combat())
                .map(Map.Entry::getValue)
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        int combat = strongestCapabilityBlend(combatPackages);
        int handling = packageScores.get(WeaponCapabilityPackage.HANDLING);
        int versatility = packageScores.get(WeaponCapabilityPackage.VERSATILITY);
        int rawProgression = roundedDivide(
                80 * combat + 15 * handling + 5 * versatility,
                100);
        int progression = calibratedProgression(rawProgression);

        int combatConfidence = roundedDivide(
                packageConfidence.entrySet().stream()
                        .filter(entry -> entry.getKey().combat())
                        .mapToInt(Map.Entry::getValue)
                        .sum(),
                combatPackages.size());
        int confidence = roundedDivide(
                80 * combatConfidence
                        + 15 * packageConfidence.get(WeaponCapabilityPackage.HANDLING)
                        + 5 * packageConfidence.get(WeaponCapabilityPackage.VERSATILITY),
                100);
        if (evidence.scriptControlled()) {
            confidence = Math.min(confidence, SCRIPT_CONFIDENCE_CAP);
            warnings.add("script_controlled");
        }

        return new WeaponCapabilityScore(
                evidence,
                progression,
                combat,
                handling,
                versatility,
                confidence,
                ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION,
                reference.version(),
                packageScores,
                packageConfidence,
                observed,
                resolved,
                metricScores,
                warnings);
    }

    private static PackageResult packageResult(
            List<MetricWeight> weights,
            Map<String, Integer> metricScores,
            Set<CapabilityMetric> confidentMetrics,
            WeaponStatEvidence evidence) {
        int weightedScore = 0;
        int confidentWeight = 0;
        int applicableWeight = 0;
        for (MetricWeight weight : weights) {
            if (!applicable(weight.metric(), evidence)) {
                continue;
            }
            applicableWeight += weight.weight();
            weightedScore += weight.weight() * metricScores.getOrDefault(
                    weight.metric().serializedName(), NEUTRAL_METRIC_SCORE);
            if (confidentMetrics.contains(weight.metric())) {
                confidentWeight += weight.weight();
            }
        }
        if (applicableWeight == 0) {
            return new PackageResult(NEUTRAL_METRIC_SCORE, 0);
        }
        return new PackageResult(
                roundedDivide(weightedScore, applicableWeight),
                roundedDivide(100 * confidentWeight, applicableWeight));
    }

    private static int strongestCapabilityBlend(List<Integer> scores) {
        if (scores == null || scores.size() < 2) {
            throw new IllegalArgumentException(
                    "Weapon capability combat blend requires at least two packages");
        }
        int average = roundedDivide(scores.stream().mapToInt(Integer::intValue).sum(),
                scores.size());
        return roundedDivide(55 * scores.get(0) + 25 * scores.get(1) + 20 * average, 100);
    }

    static int calibratedProgression(int rawScore) {
        if (rawScore < 0 || rawScore > ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException("Raw capability progression score is invalid");
        }
        int shifted = rawScore - PROGRESSION_CALIBRATION_FLOOR;
        if (shifted <= 0) {
            return 0;
        }
        return Math.min(
                ResearchTechTreeContract.SCORE_MAX,
                roundedDivide(
                        shifted * PROGRESSION_CALIBRATION_NUMERATOR,
                        PROGRESSION_CALIBRATION_DENOMINATOR));
    }

    private static boolean applicable(
            CapabilityMetric metric,
            WeaponStatEvidence evidence) {
        return switch (metric) {
            case EXPLOSION_DAMAGE, EXPLOSION_RADIUS -> evidence.explosive();
            case CONTROL_EFFECTS -> evidence.explosive()
                    || evidence.projectileIgnitesEntities();
            default -> true;
        };
    }

    private static Map<WeaponCapabilityPackage, List<MetricWeight>> packages() {
        EnumMap<WeaponCapabilityPackage, List<MetricWeight>> result =
                new EnumMap<>(WeaponCapabilityPackage.class);
        result.put(WeaponCapabilityPackage.LETHALITY, List.of(
                metric(CapabilityMetric.IMPACT_DAMAGE, 55),
                metric(CapabilityMetric.ARMOR_IGNORE, 20),
                metric(CapabilityMetric.HEADSHOT_MULTIPLIER, 10),
                metric(CapabilityMetric.AIMED_INACCURACY, 15)));
        result.put(WeaponCapabilityPackage.SUSTAINED_PRESSURE, List.of(
                metric(CapabilityMetric.SUSTAINED_DPS, 70),
                metric(CapabilityMetric.MAGAZINE_CAPACITY, 15),
                metric(CapabilityMetric.EMPTY_RELOAD_SECONDS, 5),
                metric(CapabilityMetric.RECOIL_MAGNITUDE, 10)));
        result.put(WeaponCapabilityPackage.PRECISION_REACH, List.of(
                metric(CapabilityMetric.IMPACT_DAMAGE, 20),
                metric(CapabilityMetric.DAMAGE_RETENTION, 20),
                metric(CapabilityMetric.EFFECTIVE_RANGE, 20),
                metric(CapabilityMetric.PROJECTILE_SPEED, 15),
                metric(CapabilityMetric.PROJECTILE_GRAVITY, 5),
                metric(CapabilityMetric.AIMED_INACCURACY, 15),
                metric(CapabilityMetric.RECOIL_MAGNITUDE, 5)));
        result.put(WeaponCapabilityPackage.AREA_CONTROL, List.of(
                metric(CapabilityMetric.EXPLOSION_DAMAGE, 60),
                metric(CapabilityMetric.EXPLOSION_RADIUS, 30),
                metric(CapabilityMetric.CONTROL_EFFECTS, 10)));
        result.put(WeaponCapabilityPackage.HANDLING, List.of(
                metric(CapabilityMetric.TACTICAL_RELOAD_SECONDS, 15),
                metric(CapabilityMetric.AIM_TIME, 15),
                metric(CapabilityMetric.DRAW_TIME, 12),
                metric(CapabilityMetric.WEIGHT, 13),
                metric(CapabilityMetric.AIM_MOVEMENT, 15),
                metric(CapabilityMetric.RECOIL_MAGNITUDE, 10),
                metric(CapabilityMetric.AIMED_INACCURACY, 10),
                metric(CapabilityMetric.CHARGE_SECONDS, 10)));
        result.put(WeaponCapabilityPackage.VERSATILITY, List.of(
                metric(CapabilityMetric.FIRE_MODE_COUNT, 35),
                metric(CapabilityMetric.ATTACHMENT_TYPE_COUNT, 30),
                metric(CapabilityMetric.PROJECTILE_COUNT, 20),
                metric(CapabilityMetric.TARGET_PENETRATION, 15)));
        result.forEach((capabilityPackage, weights) -> {
            if (weights.stream().mapToInt(MetricWeight::weight).sum() != 100) {
                throw new IllegalStateException(
                        "Weapon capability package weights must total 100: "
                                + capabilityPackage.serializedName());
            }
        });
        return Map.copyOf(result);
    }

    private static MetricWeight metric(CapabilityMetric metric, int weight) {
        return new MetricWeight(metric, weight);
    }

    private static int roundedDivide(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("Weapon capability divisor must be positive");
        }
        return (numerator + denominator / 2) / denominator;
    }

    private record MetricWeight(CapabilityMetric metric, int weight) {
        private MetricWeight {
            if (metric == null || weight <= 0 || weight > 100) {
                throw new IllegalArgumentException("Weapon capability metric weight is invalid");
            }
        }
    }

    private record PackageResult(int score, int confidence) {
    }
}
