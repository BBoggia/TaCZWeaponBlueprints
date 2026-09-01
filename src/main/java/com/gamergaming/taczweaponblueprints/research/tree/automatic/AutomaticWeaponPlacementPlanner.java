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
        return conservativeFallback(
                blueprintId,
                itemType,
                evidenceReason,
                policy,
                AutomaticWeaponScoringModel.MECHANICAL_V2);
    }

    public AutomaticWeaponPlacementProposal conservativeFallback(
            String blueprintId,
            String itemType,
            String evidenceReason,
            AutomaticWeaponPlacementPolicy policy,
            AutomaticWeaponScoringModel scoringModel) {
        if (!validText(blueprintId) || !validText(evidenceReason) || policy == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon conservative fallback inputs are invalid");
        }
        if (scoringModel == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon conservative fallback scoring model cannot be null");
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
                scoringModel.formulaVersion(),
                scoringModel.referenceVersion(),
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of(
                        "evidence_unavailable:" + evidenceReason,
                        "unscored_fallback"));
    }

    public AutomaticWeaponPlacementPlan plan(
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Collection<String> candidateIds,
            AutomaticWeaponPlacementPolicy policy) {
        if (scoresByBlueprint == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement planner inputs are invalid");
        }
        Map<String, PlacementScore> scores = new LinkedHashMap<>();
        scoresByBlueprint.forEach((id, score) -> scores.put(
                id, score == null ? null : PlacementScore.mechanical(score)));
        return planScores(scores, candidateIds, policy);
    }

    public AutomaticWeaponPlacementPlan planCapabilities(
            Map<String, WeaponCapabilityScore> scoresByBlueprint,
            Collection<String> candidateIds,
            AutomaticWeaponPlacementPolicy policy) {
        if (scoresByBlueprint == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement planner inputs are invalid");
        }
        Map<String, PlacementScore> scores = new LinkedHashMap<>();
        scoresByBlueprint.forEach((id, score) -> scores.put(
                id, score == null ? null : PlacementScore.capability(score)));
        return planScores(scores, candidateIds, policy);
    }

    private static AutomaticWeaponPlacementPlan planScores(
            Map<String, PlacementScore> scoresByBlueprint,
            Collection<String> candidateIds,
            AutomaticWeaponPlacementPolicy policy) {
        if (candidateIds == null || policy == null
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
            PlacementScore score = scoresByBlueprint.get(id);
            String rejection = rejection(id, score);
            if (rejection == null) {
                valid.add(new ValidatedCandidate(id, score, stableOrder(id)));
            } else {
                rejected.put(id, rejection);
            }
        });

        valid.sort(Comparator
                .comparingInt((ValidatedCandidate value) ->
                        value.score().progressionScore())
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
            PlacementScore score = candidate.score();
            int mechanicalScore = score.progressionScore();
            long siblingOrder = siblingOrder(candidate);
            List<String> reviewReasons = reviewReasons(score, policy);
            proposals.put(candidate.blueprintId(), new AutomaticWeaponPlacementProposal(
                    candidate.blueprintId(),
                    mechanicalScore,
                    score.confidence(),
                    new ProgressionPosition(
                            ResearchTechTreeContract.Tier.forScore(mechanicalScore),
                            ResearchTechTreeContract.levelForScore(
                                    mechanicalScore, policy.levelsPerTier()),
                            siblingOrder),
                    policy.levelsPerTier(),
                    score.formulaVersion(),
                    score.referenceVersion(),
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

    private static String rejection(String id, PlacementScore score) {
        if (score == null) {
            return "missing_mechanical_score";
        }
        if (!id.equals(score.blueprintId())) {
            return "mechanical_score_identity_mismatch";
        }
        if (!score.scoringModel().formulaVersion().equals(score.formulaVersion())) {
            return "incompatible_formula_version";
        }
        if (!score.scoringModel().referenceVersion().equals(score.referenceVersion())) {
            return "incompatible_reference_version";
        }
        return null;
    }

    private static List<String> reviewReasons(
            PlacementScore score,
            AutomaticWeaponPlacementPolicy policy) {
        List<String> result = new ArrayList<>();
        if (score.confidence() < policy.reviewConfidenceThreshold()) {
            result.add("low_confidence");
        }
        if (score.scriptControlled()) {
            result.add("script_controlled");
        }
        if (score.warnings().stream().anyMatch(warning ->
                warning.startsWith("missing_metric:")
                        || warning.startsWith("missing_reference:")
                        || warning.startsWith("insufficient_reference:")
                        || warning.startsWith("missing_capability_metric:")
                        || warning.startsWith("missing_capability_reference:")
                        || warning.startsWith("insufficient_capability_reference:"))) {
            result.add("incomplete_mechanical_evidence");
        }
        return result;
    }

    private static long siblingOrder(ValidatedCandidate candidate) {
        return Math.addExact(
                Math.multiplyExact(candidate.score().progressionScore(), SIBLING_HASH_SPACE),
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
            PlacementScore score,
            long stableOrder) {
    }

    private record PlacementScore(
            String blueprintId,
            int progressionScore,
            int confidence,
            String formulaVersion,
            String referenceVersion,
            AutomaticWeaponScoringModel scoringModel,
            boolean scriptControlled,
            List<String> warnings) {
        private PlacementScore {
            if (!validText(blueprintId)
                    || progressionScore < 0
                    || progressionScore > ResearchTechTreeContract.SCORE_MAX
                    || confidence < 0 || confidence > ResearchTechTreeContract.SCORE_MAX
                    || !validText(formulaVersion) || !validText(referenceVersion)
                    || scoringModel == null || warnings == null) {
                throw new IllegalArgumentException(
                        "Automatic weapon progression score is invalid");
            }
            warnings = List.copyOf(warnings);
        }

        private static PlacementScore mechanical(WeaponMechanicalScore score) {
            return new PlacementScore(
                    score.evidence().blueprintId(),
                    score.score(),
                    score.rating().confidence(),
                    score.rating().formulaVersion(),
                    score.rating().referenceVersion(),
                    AutomaticWeaponScoringModel.MECHANICAL_V2,
                    score.evidence().scriptControlled(),
                    score.warnings());
        }

        private static PlacementScore capability(WeaponCapabilityScore score) {
            return new PlacementScore(
                    score.evidence().blueprintId(),
                    score.progressionScore(),
                    score.confidence(),
                    score.formulaVersion(),
                    score.referenceVersion(),
                    AutomaticWeaponScoringModel.CAPABILITY_V3,
                    score.evidence().scriptControlled(),
                    score.warnings());
        }
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
