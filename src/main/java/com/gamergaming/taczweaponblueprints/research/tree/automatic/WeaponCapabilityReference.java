package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/** Immutable, tie-aware distributions for the versioned v3 capability model. */
public final class WeaponCapabilityReference {
    private final String version;
    private final Map<CapabilityMetric, List<Double>> distributions;

    private WeaponCapabilityReference(
            String version,
            Map<CapabilityMetric, List<Double>> distributions) {
        if (!validVersion(version)) {
            throw new IllegalArgumentException("Weapon capability reference version is invalid");
        }
        EnumMap<CapabilityMetric, List<Double>> copy =
                new EnumMap<>(CapabilityMetric.class);
        for (CapabilityMetric metric : CapabilityMetric.values()) {
            List<Double> values = distributions.getOrDefault(metric, List.of()).stream()
                    .sorted()
                    .toList();
            if (values.stream().anyMatch(value -> value == null
                    || !Double.isFinite(value))) {
                throw new IllegalArgumentException(
                        "Weapon capability reference contains an invalid value");
            }
            copy.put(metric, values);
        }
        this.version = version;
        this.distributions = Collections.unmodifiableMap(copy);
    }

    public static WeaponCapabilityReference fromEvidence(
            String version,
            Collection<WeaponStatEvidence> evidence) {
        if (evidence == null || evidence.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Weapon capability evidence cannot be null");
        }
        Set<String> blueprintIds = new LinkedHashSet<>();
        EnumMap<CapabilityMetric, java.util.ArrayList<Double>> values =
                new EnumMap<>(CapabilityMetric.class);
        for (CapabilityMetric metric : CapabilityMetric.values()) {
            values.put(metric, new java.util.ArrayList<>());
        }
        for (WeaponStatEvidence weapon : evidence) {
            if (!blueprintIds.add(weapon.blueprintId())) {
                throw new IllegalArgumentException(
                        "Weapon capability evidence contains duplicate blueprint "
                                + weapon.blueprintId());
            }
            WeaponCapabilityMetrics metrics = WeaponCapabilityMetrics.derive(weapon);
            for (CapabilityMetric metric : CapabilityMetric.values()) {
                metrics.value(metric).ifPresent(value -> values.get(metric).add(value));
            }
        }
        EnumMap<CapabilityMetric, List<Double>> immutable =
                new EnumMap<>(CapabilityMetric.class);
        values.forEach((metric, samples) -> immutable.put(metric, List.copyOf(samples)));
        return new WeaponCapabilityReference(version, immutable);
    }

    public static WeaponCapabilityReference fromMetricValues(
            String version,
            Map<CapabilityMetric, ? extends Collection<Double>> values) {
        if (values == null || values.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Weapon capability reference values cannot be null");
        }
        EnumMap<CapabilityMetric, List<Double>> copy =
                new EnumMap<>(CapabilityMetric.class);
        values.forEach((metric, samples) -> copy.put(metric, List.copyOf(samples)));
        return new WeaponCapabilityReference(version, copy);
    }

    public String version() {
        return version;
    }

    public int percentile(CapabilityMetric metric, double value) {
        requireMetricValue(metric, value);
        List<Double> available = distributions.get(metric);
        if (available.size() < 2) {
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

    public OptionalDouble median(CapabilityMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon capability metric cannot be null");
        }
        List<Double> values = distributions.get(metric);
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        int middle = values.size() / 2;
        return values.size() % 2 == 1
                ? OptionalDouble.of(values.get(middle))
                : OptionalDouble.of(values.get(middle - 1) / 2.0
                        + values.get(middle) / 2.0);
    }

    public int sampleCount(CapabilityMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("Weapon capability metric cannot be null");
        }
        return distributions.get(metric).size();
    }

    public Map<CapabilityMetric, List<Double>> distributions() {
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

    private static void requireMetricValue(CapabilityMetric metric, double value) {
        if (metric == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Weapon capability lookup is invalid");
        }
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static boolean validVersion(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim())
                && value.length() <= 96
                && value.chars().noneMatch(character ->
                        Character.isWhitespace(character)
                                || Character.isISOControl(character));
    }
}
