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
     * Capability-v3 counterpart. The stable vector slots remain compatible with
     * the topology engine, but each slot can blend several related v3 metrics so
     * explosion, retention, gravity, charge, projectile count, and penetration
     * all contribute to branch identity.
     */
    public Map<String, AutomaticWeaponRoleSignature> analyzeCapabilities(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, WeaponCapabilityScore> scoresByBlueprint,
            Map<String, String> fallbackArchetypes) {
        validateCapabilityInputs(proposals, scoresByBlueprint, fallbackArchetypes);
        Map<String, AutomaticWeaponRoleSignature> result = new LinkedHashMap<>();
        proposals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String blueprintId = entry.getKey();
            AutomaticWeaponPlacementProposal proposal = entry.getValue();
            WeaponCapabilityScore score = scoresByBlueprint.get(blueprintId);
            result.put(blueprintId, score == null
                    ? unresolved(proposal, fallbackArchetypes.getOrDefault(
                            blueprintId, "unknown"))
                    : resolvedCapability(proposal, score));
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

    public Map<String, AutomaticWeaponRoleSignature> analyzeCapabilitiesAuthored(
            Set<String> authoredBlueprintIds,
            Map<String, WeaponCapabilityScore> scoresByBlueprint,
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
                    "Automatic authored capability-role analysis inputs are invalid");
        }
        Map<String, AutomaticWeaponRoleSignature> result = new LinkedHashMap<>();
        authoredBlueprintIds.stream().sorted().forEach(blueprintId -> {
            WeaponCapabilityScore score = scoresByBlueprint.get(blueprintId);
            result.put(blueprintId, score == null
                    ? unresolved(
                            blueprintId,
                            0,
                            0,
                            fallbackArchetypes.getOrDefault(blueprintId, "unknown"),
                            "unscored_authored_role_evidence")
                    : resolvedCapability(score, false));
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

    /** Produces capability-v3 role context for observational consumers. */
    public AutomaticWeaponRoleSignature analyze(WeaponCapabilityScore score) {
        if (score == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon role analysis requires a capability score");
        }
        return resolvedCapability(score, false);
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

    private static AutomaticWeaponRoleSignature resolvedCapability(
            AutomaticWeaponPlacementProposal proposal,
            WeaponCapabilityScore score) {
        if (!proposal.blueprintId().equals(score.evidence().blueprintId())
                || proposal.mechanicalScore() != score.progressionScore()
                || proposal.confidence() != score.confidence()) {
            throw new IllegalArgumentException(
                    "Automatic weapon capability role score does not match its placement proposal");
        }
        return resolvedCapability(score, proposal.reviewRequired());
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

    private static AutomaticWeaponRoleSignature resolvedCapability(
            WeaponCapabilityScore score,
            boolean proposalReviewRequired) {
        Map<String, Integer> metricValues = new LinkedHashMap<>();
        Set<String> missingMetrics = new LinkedHashSet<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            List<CapabilityMetric> capabilityMetrics = capabilityMetricsForRole(metric);
            List<Integer> available = capabilityMetrics.stream()
                    .map(capability -> score.metricScores().get(capability.serializedName()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            Integer value = available.isEmpty()
                    ? WeaponCapabilityScorer.NEUTRAL_METRIC_SCORE
                    : roundedAverage(available);
            if (available.isEmpty()) {
                missingMetrics.add(capabilityMetrics.stream()
                        .map(CapabilityMetric::serializedName)
                        .collect(java.util.stream.Collectors.joining("+")));
            }
            metricValues.put(metric.serializedName(), value);
        }
        int strengthBaseline = weightedBaseline(metricValues);
        Map<String, Integer> offsets = new LinkedHashMap<>();
        metricValues.forEach((metric, value) -> offsets.put(metric, value - strengthBaseline));

        Set<String> seedBlockReasons = new LinkedHashSet<>();
        if (score.confidence() < MIN_BRANCH_SEED_CONFIDENCE) {
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
                score.progressionScore(),
                score.confidence(),
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

    private static List<CapabilityMetric> capabilityMetricsForRole(MechanicalMetric metric) {
        return switch (metric) {
            case SUSTAINED_DPS -> List.of(CapabilityMetric.SUSTAINED_DPS);
            case EFFECTIVE_DAMAGE -> List.of(
                    CapabilityMetric.IMPACT_DAMAGE, CapabilityMetric.EXPLOSION_DAMAGE);
            case HEADSHOT_MULTIPLIER -> List.of(
                    CapabilityMetric.HEADSHOT_MULTIPLIER, CapabilityMetric.CONTROL_EFFECTS);
            case EFFECTIVE_RANGE -> List.of(
                    CapabilityMetric.EFFECTIVE_RANGE, CapabilityMetric.EXPLOSION_RADIUS);
            case ARMOR_EFFECTIVENESS -> List.of(
                    CapabilityMetric.ARMOR_IGNORE, CapabilityMetric.TARGET_PENETRATION);
            case PROJECTILE_SPEED -> List.of(
                    CapabilityMetric.PROJECTILE_SPEED, CapabilityMetric.DAMAGE_RETENTION);
            case AIMED_INACCURACY -> List.of(
                    CapabilityMetric.AIMED_INACCURACY, CapabilityMetric.PROJECTILE_GRAVITY);
            case RECOIL_MAGNITUDE -> List.of(
                    CapabilityMetric.RECOIL_MAGNITUDE, CapabilityMetric.CHARGE_SECONDS);
            case MAGAZINE_CAPACITY -> List.of(
                    CapabilityMetric.MAGAZINE_CAPACITY, CapabilityMetric.PROJECTILE_COUNT);
            case RELOAD_SECONDS -> List.of(
                    CapabilityMetric.EMPTY_RELOAD_SECONDS,
                    CapabilityMetric.TACTICAL_RELOAD_SECONDS);
            case AIM_TIME -> List.of(CapabilityMetric.AIM_TIME);
            case DRAW_TIME -> List.of(CapabilityMetric.DRAW_TIME);
            case WEIGHT -> List.of(CapabilityMetric.WEIGHT);
            case AIM_MOVEMENT -> List.of(CapabilityMetric.AIM_MOVEMENT);
            case FIRE_MODE_COUNT -> List.of(CapabilityMetric.FIRE_MODE_COUNT);
            case ATTACHMENT_TYPE_COUNT -> List.of(CapabilityMetric.ATTACHMENT_TYPE_COUNT);
        };
    }

    private static int roundedAverage(List<Integer> values) {
        return (values.stream().mapToInt(Integer::intValue).sum() + values.size() / 2)
                / values.size();
    }

    private static void validateCapabilityInputs(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, WeaponCapabilityScore> scoresByBlueprint,
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
            throw new IllegalArgumentException(
                    "Automatic weapon capability-role analysis inputs are invalid");
        }
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
