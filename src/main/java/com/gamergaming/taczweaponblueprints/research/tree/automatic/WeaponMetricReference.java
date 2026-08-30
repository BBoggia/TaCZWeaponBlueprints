package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/** Immutable tie-aware comparison distributions used by the pure mechanical scorer. */
public final class WeaponMetricReference {
    private final String version;
    private final Map<MechanicalMetric, List<Double>> distributions;

    private WeaponMetricReference(
            String version,
            Map<MechanicalMetric, List<Double>> distributions) {
        if (!validVersion(version)) {
            throw new IllegalArgumentException(
                    "Weapon metric reference version is invalid");
        }
        EnumMap<MechanicalMetric, List<Double>> copy = new EnumMap<>(MechanicalMetric.class);
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            List<Double> values = distributions.getOrDefault(metric, List.of()).stream()
                    .sorted()
                    .toList();
            if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException(
                        "Weapon metric reference contains an invalid value");
            }
            copy.put(metric, values);
        }
        this.version = version;
        this.distributions = Collections.unmodifiableMap(copy);
    }

    public static WeaponMetricReference fromEvidence(
            String version,
            Collection<WeaponStatEvidence> evidence) {
        if (evidence == null || evidence.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Weapon reference evidence cannot be null");
        }
        Set<String> blueprintIds = new LinkedHashSet<>();
        EnumMap<MechanicalMetric, java.util.ArrayList<Double>> values =
                new EnumMap<>(MechanicalMetric.class);
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            values.put(metric, new java.util.ArrayList<>());
        }
        for (WeaponStatEvidence weapon : evidence) {
            if (!blueprintIds.add(weapon.blueprintId())) {
                throw new IllegalArgumentException(
                        "Weapon reference evidence contains duplicate blueprint "
                                + weapon.blueprintId());
            }
            WeaponMechanicalMetrics metrics = WeaponMechanicalMetrics.derive(weapon);
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                metrics.value(metric).ifPresent(value -> values.get(metric).add(value));
            }
        }
        EnumMap<MechanicalMetric, List<Double>> immutable =
                new EnumMap<>(MechanicalMetric.class);
        values.forEach((metric, samples) -> immutable.put(metric, List.copyOf(samples)));
        return new WeaponMetricReference(version, immutable);
    }

    public static WeaponMetricReference fromMetricValues(
            String version,
            Map<MechanicalMetric, ? extends Collection<Double>> values) {
        if (values == null || values.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Weapon metric reference values cannot be null");
        }
        EnumMap<MechanicalMetric, List<Double>> copy = new EnumMap<>(MechanicalMetric.class);
        values.forEach((metric, samples) -> {
            List<Double> sampleCopy = new java.util.ArrayList<>(samples);
            if (sampleCopy.stream().anyMatch(value ->
                    value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException(
                        "Weapon metric reference contains an invalid value");
            }
            copy.put(metric, List.copyOf(sampleCopy));
        });
        return new WeaponMetricReference(version, copy);
    }

    public String version() {
        return version;
    }

    public int percentile(MechanicalMetric metric, double value) {
        requireMetricValue(metric, value);
        List<Double> available = distributions.get(metric);
        if (available.isEmpty() || available.size() == 1) {
            return 50;
        }
        int first = lowerBound(available, value);
        int lastExclusive = upperBound(available, value);
        if (lastExclusive > first) {
            double middleRank = (first + lastExclusive - 1) / 2.0;
            return clampScore((int) Math.round(
                    100.0 * middleRank / (available.size() - 1)));
        }
        if (first == 0) {
            return 0;
        }
        if (first == available.size()) {
            return 100;
        }
        double lower = available.get(first - 1);
        double upper = available.get(first);
        double scale = Math.max(Math.abs(lower), Math.abs(upper));
        double fraction = scale == 0.0
                ? 0.0
                : ((value / scale) - (lower / scale))
                        / ((upper / scale) - (lower / scale));
        double interpolatedRank = first - 1 + fraction;
        return clampScore((int) Math.round(
                100.0 * interpolatedRank / (available.size() - 1)));
    }

    public OptionalDouble median(MechanicalMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon reference metric cannot be null");
        }
        List<Double> values = distributions.get(metric);
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        int middle = values.size() / 2;
        return values.size() % 2 == 1
                ? OptionalDouble.of(values.get(middle))
                : OptionalDouble.of(
                        values.get(middle - 1) / 2.0 + values.get(middle) / 2.0);
    }

    public int sampleCount(MechanicalMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon reference metric cannot be null");
        }
        return distributions.get(metric).size();
    }

    public Map<MechanicalMetric, List<Double>> distributions() {
        return distributions;
    }

    private static int lowerBound(List<Double> values, double target) {
        int low = 0;
        int high = values.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values.get(middle) < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperBound(List<Double> values, double target) {
        int low = 0;
        int high = values.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values.get(middle) <= target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void requireMetricValue(MechanicalMetric metric, double value) {
        if (metric == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Weapon reference lookup is invalid");
        }
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static boolean validVersion(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= 96
                && value.chars().noneMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character));
    }
}
