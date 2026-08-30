package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;

/**
 * Pure, catalog-order-independent mechanical placement proposal engine.
 * It has no research, prerequisite, datapack, networking, or layout authority.
 */
public final class AutomaticWeaponPlacementPlanner {
    static final long SIBLING_HASH_SPACE = 1L << 56;

    /**
     * Creates an explicitly review-marked, conservative placement when TaCZ cannot
     * expose enough runtime evidence to calculate a mechanical score. The item
     * type only selects a broad bounded band; the exact position is a stable ID
     * hash so catalog iteration order and unrelated pack changes cannot move it.
     */
    public AutomaticWeaponPlacementProposal conservativeFallback(
            String blueprintId,
            String itemType,
            String evidenceReason,
            AutomaticWeaponPlacementPolicy policy) {
        if (!validText(blueprintId) || !validText(evidenceReason) || policy == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon conservative fallback inputs are invalid");
        }
        ScoreBand band = conservativeBand(itemType);
        long stableOrder = stableOrder(blueprintId);
        int score = band.minimum() + (int) Long.remainderUnsigned(
                stableOrder, band.maximum() - band.minimum() + 1L);
        return new AutomaticWeaponPlacementProposal(
                blueprintId,
                score,
                0,
                new ProgressionPosition(
                        ResearchTechTreeContract.Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(
                                score, policy.levelsPerTier()),
                        Math.addExact(
                                Math.multiplyExact(score, SIBLING_HASH_SPACE),
                                stableOrder)),
                policy.levelsPerTier(),
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of(
                        "evidence_unavailable:" + evidenceReason,
                        "unscored_fallback"));
    }

    public AutomaticWeaponPlacementPlan plan(
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Collection<String> candidateIds,
            AutomaticWeaponPlacementPolicy policy) {
        if (scoresByBlueprint == null || candidateIds == null || policy == null
                || scoresByBlueprint.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || entry.getValue() == null)
                || candidateIds.stream().anyMatch(id -> !validText(id))) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement planner inputs are invalid");
        }
        if (scoresByBlueprint.size() > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || candidateIds.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement input count exceeds the limit");
        }
        Set<String> uniqueCandidates = new HashSet<>(candidateIds);
        if (uniqueCandidates.size() != candidateIds.size()) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement candidates contain duplicates");
        }

        List<ValidatedCandidate> valid = new ArrayList<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        uniqueCandidates.stream().sorted().forEach(id -> {
            WeaponMechanicalScore score = scoresByBlueprint.get(id);
            String rejection = rejection(id, score);
            if (rejection == null) {
                valid.add(new ValidatedCandidate(id, score, stableOrder(id)));
            } else {
                rejected.put(id, rejection);
            }
        });

        valid.sort(Comparator
                .comparingInt((ValidatedCandidate value) -> value.score().score())
                .thenComparingLong(ValidatedCandidate::stableOrder)
                .thenComparing(ValidatedCandidate::blueprintId));
        Map<Long, List<ValidatedCandidate>> candidatesByOrder = valid.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AutomaticWeaponPlacementPlanner::siblingOrder,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        candidatesByOrder.values().stream()
                .filter(collisions -> collisions.size() > 1)
                .flatMap(Collection::stream)
                .forEach(candidate -> rejected.put(
                        candidate.blueprintId(), "stable_sibling_order_collision"));
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        for (ValidatedCandidate candidate : valid) {
            if (rejected.containsKey(candidate.blueprintId())) {
                continue;
            }
            WeaponMechanicalScore score = candidate.score();
            int mechanicalScore = score.score();
            long siblingOrder = siblingOrder(candidate);
            List<String> reviewReasons = reviewReasons(score, policy);
            proposals.put(candidate.blueprintId(), new AutomaticWeaponPlacementProposal(
                    candidate.blueprintId(),
                    mechanicalScore,
                    score.rating().confidence(),
                    new ProgressionPosition(
                            score.rating().suggestedTier(),
                            score.rating().suggestedLevel(policy.levelsPerTier()),
                            siblingOrder),
                    policy.levelsPerTier(),
                    score.rating().formulaVersion(),
                    score.rating().referenceVersion(),
                    ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                    reviewReasons));
        }
        return new AutomaticWeaponPlacementPlan(
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                policy.levelsPerTier(),
                policy.reviewConfidenceThreshold(),
                candidateIds.size(),
                proposals,
                rejected);
    }

    private static String rejection(String id, WeaponMechanicalScore score) {
        if (score == null) {
            return "missing_mechanical_score";
        }
        if (!id.equals(score.evidence().blueprintId())) {
            return "mechanical_score_identity_mismatch";
        }
        if (!ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION.equals(
                score.rating().formulaVersion())) {
            return "incompatible_formula_version";
        }
        if (!ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION.equals(
                score.rating().referenceVersion())) {
            return "incompatible_reference_version";
        }
        return null;
    }

    private static List<String> reviewReasons(
            WeaponMechanicalScore score,
            AutomaticWeaponPlacementPolicy policy) {
        List<String> result = new ArrayList<>();
        if (score.rating().confidence() < policy.reviewConfidenceThreshold()) {
            result.add("low_confidence");
        }
        if (score.evidence().scriptControlled()) {
            result.add("script_controlled");
        }
        if (score.warnings().stream().anyMatch(warning ->
                warning.startsWith("missing_metric:")
                        || warning.startsWith("missing_reference:")
                        || warning.startsWith("insufficient_reference:"))) {
            result.add("incomplete_mechanical_evidence");
        }
        return result;
    }

    private static long siblingOrder(ValidatedCandidate candidate) {
        return Math.addExact(
                Math.multiplyExact(candidate.score().score(), SIBLING_HASH_SPACE),
                candidate.stableOrder());
    }

    private static long stableOrder(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0L;
            for (int index = 0; index < 7; index++) {
                result = (result << 8) | (hash[index] & 0xffL);
            }
            return result;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static ScoreBand conservativeBand(String itemType) {
        String normalized = itemType == null
                ? ""
                : itemType.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "pistol" -> new ScoreBand(8, 32);
            case "smg" -> new ScoreBand(20, 48);
            case "shotgun" -> new ScoreBand(24, 54);
            case "rifle" -> new ScoreBand(30, 62);
            case "mg", "lmg", "machine_gun" -> new ScoreBand(38, 67);
            case "sniper" -> new ScoreBand(44, 72);
            case "rpg", "launcher" -> new ScoreBand(42, 70);
            default -> new ScoreBand(15, 50);
        };
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private record ValidatedCandidate(
            String blueprintId,
            WeaponMechanicalScore score,
            long stableOrder) {
    }

    private record ScoreBand(int minimum, int maximum) {
        private ScoreBand {
            if (minimum < 0 || maximum > ResearchTechTreeContract.SCORE_MAX
                    || minimum > maximum) {
                throw new IllegalArgumentException(
                        "Automatic weapon conservative score band is invalid");
            }
        }
    }
}
