package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Produces deterministic strength-relative role signatures before topology assignment. */
public final class AutomaticWeaponRoleAnalyzer {
    public static final int MIN_BRANCH_SEED_CONFIDENCE =
            AutomaticWeaponPlacementPolicy.DEFAULT_REVIEW_CONFIDENCE_THRESHOLD;

    public Map<String, AutomaticWeaponRoleSignature> analyze(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Map<String, String> fallbackArchetypes) {
        if (proposals == null || scoresByBlueprint == null || fallbackArchetypes == null
                || proposals.size() > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || scoresByBlueprint.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || proposals.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId()))
                || scoresByBlueprint.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || entry.getValue() == null
                                || !entry.getKey().equals(
                                        entry.getValue().evidence().blueprintId()))
                || fallbackArchetypes.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || !validText(entry.getValue()))) {
            throw new IllegalArgumentException("Automatic weapon role-analysis inputs are invalid");
        }
        Map<String, AutomaticWeaponRoleSignature> result = new LinkedHashMap<>();
        proposals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String blueprintId = entry.getKey();
            AutomaticWeaponPlacementProposal proposal = entry.getValue();
            WeaponMechanicalScore score = scoresByBlueprint.get(blueprintId);
            result.put(blueprintId, score == null
                    ? unresolved(proposal, fallbackArchetypes.getOrDefault(
                            blueprintId, "unknown"))
                    : resolved(proposal, score));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Produces read-only role context for authored weapons. Authored signatures
     * may guide an automatic family toward an existing anchor, but never grant
     * permission to move or replace that authored placement.
     */
    public Map<String, AutomaticWeaponRoleSignature> analyzeAuthored(
            Set<String> authoredBlueprintIds,
            Map<String, WeaponMechanicalScore> scoresByBlueprint,
            Map<String, String> fallbackArchetypes) {
        if (authoredBlueprintIds == null || scoresByBlueprint == null
                || fallbackArchetypes == null
                || authoredBlueprintIds.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || authoredBlueprintIds.stream().anyMatch(value -> !validText(value))
                || scoresByBlueprint.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || entry.getValue() == null
                                || !entry.getKey().equals(
                                        entry.getValue().evidence().blueprintId()))
                || fallbackArchetypes.entrySet().stream().anyMatch(entry ->
                        !validText(entry.getKey()) || !validText(entry.getValue()))) {
            throw new IllegalArgumentException(
                    "Automatic authored-role analysis inputs are invalid");
        }
        Map<String, AutomaticWeaponRoleSignature> result = new LinkedHashMap<>();
        authoredBlueprintIds.stream().sorted().forEach(blueprintId -> {
            WeaponMechanicalScore score = scoresByBlueprint.get(blueprintId);
            result.put(blueprintId, score == null
                    ? unresolved(
                            blueprintId,
                            0,
                            0,
                            fallbackArchetypes.getOrDefault(blueprintId, "unknown"),
                            "unscored_authored_role_evidence")
                    : resolved(score, false));
        });
        return Collections.unmodifiableMap(result);
    }

    /** Produces the same role signature for observational and authoring consumers. */
    public AutomaticWeaponRoleSignature analyze(WeaponMechanicalScore score) {
        if (score == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon role analysis requires a mechanical score");
        }
        return resolved(score, false);
    }

    private static AutomaticWeaponRoleSignature resolved(
            AutomaticWeaponPlacementProposal proposal,
            WeaponMechanicalScore score) {
        if (!proposal.blueprintId().equals(score.evidence().blueprintId())
                || proposal.mechanicalScore() != score.score()
                || proposal.confidence() != score.rating().confidence()) {
            throw new IllegalArgumentException(
                    "Automatic weapon role score does not match its placement proposal");
        }
        return resolved(score, proposal.reviewRequired());
    }

    private static AutomaticWeaponRoleSignature resolved(
            WeaponMechanicalScore score,
            boolean proposalReviewRequired) {
        Map<String, Integer> metricValues = new LinkedHashMap<>();
        Set<String> missingMetrics = new LinkedHashSet<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            Integer value = score.metricScores().get(metric.serializedName());
            if (value == null) {
                value = WeaponMechanicalScorer.NEUTRAL_METRIC_SCORE;
                missingMetrics.add(metric.serializedName());
            }
            metricValues.put(metric.serializedName(), value);
        }
        int strengthBaseline = weightedBaseline(metricValues);
        Map<String, Integer> offsets = new LinkedHashMap<>();
        metricValues.forEach((metric, value) -> offsets.put(metric, value - strengthBaseline));

        Set<String> seedBlockReasons = new LinkedHashSet<>();
        if (score.rating().confidence() < MIN_BRANCH_SEED_CONFIDENCE) {
            seedBlockReasons.add("low_confidence");
        }
        if (score.evidence().scriptControlled()) {
            seedBlockReasons.add("script_controlled");
        }
        if (!missingMetrics.isEmpty()) {
            seedBlockReasons.add("incomplete_role_metrics");
        }
        if (proposalReviewRequired) {
            seedBlockReasons.add("proposal_review_required");
        }
        return new AutomaticWeaponRoleSignature(
                score.evidence().blueprintId(),
                score.score(),
                score.rating().confidence(),
                score.evidence().archetype(),
                score.evidence().explosive(),
                strengthBaseline,
                offsets,
                true,
                List.copyOf(seedBlockReasons));
    }

    private static AutomaticWeaponRoleSignature unresolved(
            AutomaticWeaponPlacementProposal proposal,
            String archetype) {
        return unresolved(
                proposal.blueprintId(),
                proposal.mechanicalScore(),
                proposal.confidence(),
                archetype,
                "unscored_role_evidence");
    }

    private static AutomaticWeaponRoleSignature unresolved(
            String blueprintId,
            int mechanicalScore,
            int confidence,
            String archetype,
            String reason) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            offsets.put(metric.serializedName(), 0);
        }
        return new AutomaticWeaponRoleSignature(
                blueprintId,
                mechanicalScore,
                confidence,
                archetype,
                false,
                mechanicalScore,
                offsets,
                false,
                List.of(reason));
    }

    private static int weightedBaseline(Map<String, Integer> metricValues) {
        long weighted = 0L;
        int totalWeight = 0;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            weighted = Math.addExact(
                    weighted,
                    Math.multiplyExact(
                            (long) metric.weight(),
                            metricValues.get(metric.serializedName())));
            totalWeight = Math.addExact(totalWeight, metric.weight());
        }
        return Math.toIntExact(Math.addExact(weighted, totalWeight / 2L) / totalWeight);
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
