package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.MechanicalMetric;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.MechanicalMetric.Component;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalMetrics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMetricReference;

/**
 * Converts heterogeneous TaCZ stats into percentile-based authoring suggestions.
 * The result is evidence, never progression authority.
 */
public final class ResearchTechTreeRatingSuggester {
    public static final int DEFAULT_UNREVIEWED_APPEAL = 50;
    public static final String FORMULA_VERSION = "tacz-gun-percentiles-v1";

    private static final List<MechanicalMetric> COMBAT_METRICS = metrics(Component.COMBAT);
    private static final List<MechanicalMetric> UTILITY_METRICS = metrics(Component.UTILITY);

    public List<WeaponRatingSuggestion> suggest(
            List<TaCZGunStats> input,
            Map<String, AppealRating> appealRatings) {
        if (input == null || input.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Gun evidence cannot be null");
        }
        Map<String, AppealRating> appeals = appealRatings == null ? Map.of() : Map.copyOf(appealRatings);
        List<TaCZGunStats> guns = input.stream()
                .sorted(Comparator.comparing(TaCZGunStats::blueprintId))
                .toList();
        long distinct = guns.stream().map(TaCZGunStats::blueprintId).distinct().count();
        if (distinct != guns.size()) {
            throw new IllegalArgumentException("Gun evidence contains duplicate blueprint IDs");
        }
        String unknownAppeal = appeals.keySet().stream()
                .filter(id -> guns.stream().noneMatch(gun -> gun.blueprintId().equals(id)))
                .sorted()
                .findFirst()
                .orElse(null);
        if (unknownAppeal != null) {
            throw new IllegalArgumentException("Appeal rating has no extracted gun: " + unknownAppeal);
        }

        Map<String, WeaponMechanicalMetrics> raw = new LinkedHashMap<>();
        for (TaCZGunStats gun : guns) {
            raw.put(gun.blueprintId(), WeaponMechanicalMetrics.derive(gun.mechanicalEvidence()));
        }
        WeaponMetricReference reference = WeaponMetricReference.fromEvidence(
                FORMULA_VERSION + "-active-reference",
                guns.stream()
                        .map(TaCZGunStats::mechanicalEvidence)
                        .toList());
        Map<MechanicalMetric, Map<String, Integer>> percentiles =
                new EnumMap<>(MechanicalMetric.class);
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            percentiles.put(metric, percentileRanks(guns, raw, reference, metric));
        }
        Map<String, Integer> combatComposites = componentComposites(
                guns, percentiles, COMBAT_METRICS);
        Map<String, Integer> utilityComposites = componentComposites(
                guns, percentiles, UTILITY_METRICS);
        Map<String, Integer> combatScores = componentPercentileRanks(guns, combatComposites);
        Map<String, Integer> utilityScores = componentPercentileRanks(guns, utilityComposites);

        List<WeaponRatingSuggestion> suggestions = new ArrayList<>(guns.size());
        for (TaCZGunStats gun : guns) {
            WeaponMechanicalMetrics gunRaw = raw.get(gun.blueprintId());
            List<String> warnings = new ArrayList<>(gun.missingFields());
            addNeutralMetricWarnings(gun, raw, COMBAT_METRICS, warnings);
            addNeutralMetricWarnings(gun, raw, UTILITY_METRICS, warnings);
            int combat = combatScores.get(gun.blueprintId());
            int utility = utilityScores.get(gun.blueprintId());
            AppealRating appeal = appeals.get(gun.blueprintId());
            int appealScore = appeal == null ? DEFAULT_UNREVIEWED_APPEAL : appeal.score();
            if (appeal == null) {
                warnings.add("appeal_unreviewed");
            }
            ResearchTechTreeContract.WeaponRating rating =
                    new ResearchTechTreeContract.WeaponRating(combat, utility, appealScore);
            Map<String, Integer> gunPercentiles = new LinkedHashMap<>();
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                gunPercentiles.put(
                        metric.serializedName(),
                        percentiles.get(metric).get(gun.blueprintId()));
            }
            suggestions.add(new WeaponRatingSuggestion(
                    gun,
                    nullable(gunRaw, MechanicalMetric.EFFECTIVE_DAMAGE),
                    nullable(gunRaw, MechanicalMetric.SUSTAINED_DPS),
                    combat,
                    utility,
                    appealScore,
                    appeal != null,
                    appeal == null ? null : appeal.reason(),
                    rating.mechanicalScore(),
                    rating.weightedScore(),
                    rating.suggestedTier(),
                    gunPercentiles,
                    warnings.stream().distinct().sorted().toList()));
        }
        suggestions.sort(Comparator
                .comparingInt(WeaponRatingSuggestion::weightedScore)
                .thenComparing(suggestion -> suggestion.stats().blueprintId()));
        return List.copyOf(suggestions);
    }

    private static Map<String, Integer> percentileRanks(
            List<TaCZGunStats> guns,
            Map<String, WeaponMechanicalMetrics> raw,
            WeaponMetricReference reference,
            MechanicalMetric metric) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TaCZGunStats gun : guns) {
            var value = raw.get(gun.blueprintId()).value(metric);
            result.put(gun.blueprintId(), value.isEmpty()
                    ? WeaponMechanicalScorer.NEUTRAL_METRIC_SCORE
                    : reference.percentile(metric, value.getAsDouble()));
        }
        return Map.copyOf(result);
    }

    private static Map<String, Integer> componentComposites(
            List<TaCZGunStats> guns,
            Map<MechanicalMetric, Map<String, Integer>> percentiles,
            List<MechanicalMetric> metrics) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TaCZGunStats gun : guns) {
            int total = 0;
            for (MechanicalMetric metric : metrics) {
                int percentile = percentiles.get(metric).get(gun.blueprintId());
                int score = metric.lowerIsBetter() ? 100 - percentile : percentile;
                total += metric.weight() * score;
            }
            result.put(gun.blueprintId(), (total + 50) / 100);
        }
        return Map.copyOf(result);
    }

    private static Map<String, Integer> componentPercentileRanks(
            List<TaCZGunStats> guns,
            Map<String, Integer> composites) {
        List<Integer> ordered = composites.values().stream().sorted().toList();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (TaCZGunStats gun : guns) {
            int value = composites.get(gun.blueprintId());
            int first = java.util.Collections.binarySearch(ordered, value);
            while (first > 0 && ordered.get(first - 1) == value) {
                first--;
            }
            int last = first;
            while (last + 1 < ordered.size() && ordered.get(last + 1) == value) {
                last++;
            }
            int score = ordered.size() == 1
                    ? 50
                    : (int) Math.round(100.0 * ((first + last) / 2.0) / (ordered.size() - 1));
            result.put(gun.blueprintId(), score);
        }
        return Map.copyOf(result);
    }

    private static void addNeutralMetricWarnings(
            TaCZGunStats gun,
            Map<String, WeaponMechanicalMetrics> raw,
            List<MechanicalMetric> metrics,
            List<String> warnings) {
        for (MechanicalMetric metric : metrics) {
            if (raw.get(gun.blueprintId()).value(metric).isEmpty()) {
                warnings.add("neutral_percentile:" + metric.serializedName());
            }
        }
    }

    private static List<MechanicalMetric> metrics(Component component) {
        return java.util.Arrays.stream(MechanicalMetric.values())
                .filter(metric -> metric.component() == component)
                .toList();
    }

    private static Double nullable(
            WeaponMechanicalMetrics metrics,
            MechanicalMetric metric) {
        var value = metrics.value(metric);
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
