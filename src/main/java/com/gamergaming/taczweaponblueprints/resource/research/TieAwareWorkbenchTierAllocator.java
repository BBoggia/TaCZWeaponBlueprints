package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

/** Assigns score cohorts to three workstation tiers without splitting equal scores. */
public final class TieAwareWorkbenchTierAllocator {
    private TieAwareWorkbenchTierAllocator() {
    }

    public static Map<String, Assignment> allocate(
            Map<String, Integer> scores,
            AutomaticWorkbenchTierPercentiles boundaries) {
        if (scores == null || boundaries == null) {
            throw new IllegalArgumentException("automatic tier allocation inputs cannot be null");
        }
        scores.entrySet().forEach(TieAwareWorkbenchTierAllocator::requireScore);
        List<Map.Entry<String, Integer>> ordered = scores.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, Integer> value) -> value.getValue())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        Map<String, Assignment> result = new LinkedHashMap<>();
        int start = 0;
        while (start < ordered.size()) {
            int score = ordered.get(start).getValue();
            int end = start + 1;
            while (end < ordered.size() && ordered.get(end).getValue() == score) {
                end++;
            }
            int midpointBasisPoints = Math.toIntExact(
                    ((long) start + (long) end) *
                            (AutomaticWorkbenchTierPercentiles.BASIS_POINTS / 2L)
                            / ordered.size());
            ResearchWorkbenchTier tier = midpointBasisPoints <= boundaries.tierOneUpperBasisPoints()
                    ? ResearchWorkbenchTier.TIER_1
                    : midpointBasisPoints <= boundaries.tierTwoUpperBasisPoints()
                            ? ResearchWorkbenchTier.TIER_2
                            : ResearchWorkbenchTier.TIER_3;
            Assignment assignment = new Assignment(tier, score, midpointBasisPoints, end - start);
            for (int index = start; index < end; index++) {
                result.put(ordered.get(index).getKey(), assignment);
            }
            start = end;
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Assigns tiers by depth in the tree, with the weapon's own score as a floor. Depth sets the
     * tier for a whole rank, so a bench reaches a contiguous stretch of the tree. A weapon whose
     * score alone would earn a higher tier is then lifted to that tier, so a strong weapon placed
     * early does not open on a starter bench. A weak weapon placed late keeps its depth tier.
     * The recorded percentile is the weapon's depth position.
     */
    public static Map<String, Assignment> allocateByDepth(
            Map<String, Integer> ranks,
            Map<String, Integer> scores,
            AutomaticWorkbenchTierPercentiles boundaries) {
        if (ranks == null || scores == null || boundaries == null || !ranks.keySet().equals(scores.keySet())
                || ranks.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                        || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("automatic tier depth map is invalid");
        }
        Map<String, Assignment> byScore = allocate(scores, boundaries);
        List<Map.Entry<String, Integer>> ordered = ranks.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, Integer> value) -> value.getValue())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        Map<String, Assignment> result = new LinkedHashMap<>();
        int start = 0;
        while (start < ordered.size()) {
            int rank = ordered.get(start).getValue();
            int end = start + 1;
            while (end < ordered.size() && ordered.get(end).getValue() == rank) {
                end++;
            }
            int midpointBasisPoints = Math.toIntExact(
                    ((long) start + (long) end) *
                            (AutomaticWorkbenchTierPercentiles.BASIS_POINTS / 2L)
                            / ordered.size());
            ResearchWorkbenchTier tier = midpointBasisPoints <= boundaries.tierOneUpperBasisPoints()
                    ? ResearchWorkbenchTier.TIER_1
                    : midpointBasisPoints <= boundaries.tierTwoUpperBasisPoints()
                            ? ResearchWorkbenchTier.TIER_2
                            : ResearchWorkbenchTier.TIER_3;
            for (int index = start; index < end; index++) {
                String id = ordered.get(index).getKey();
                ResearchWorkbenchTier floor = byScore.get(id).tier();
                result.put(id, new Assignment(floor.level() > tier.level() ? floor : tier,
                        scores.get(id), midpointBasisPoints, end - start));
            }
            start = end;
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map.Entry<String, Integer> requireScore(Map.Entry<String, Integer> entry) {
        if (entry == null || entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 100) {
            throw new IllegalArgumentException("automatic tier score map is invalid");
        }
        return entry;
    }

    public record Assignment(
            ResearchWorkbenchTier tier,
            int score,
            int percentileBasisPoints,
            int tieCount) {
        public Assignment {
            if (tier == null || score < 0 || score > 100
                    || percentileBasisPoints < 0
                    || percentileBasisPoints > AutomaticWorkbenchTierPercentiles.BASIS_POINTS
                    || tieCount < 1) {
                throw new IllegalArgumentException("automatic Research Bench tier assignment is invalid");
            }
        }
    }
}
