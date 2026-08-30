package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/** Resolves statistically equivalent one-to-three-weapon branch endpoints. */
public final class AutomaticWeaponTerminalClusterResolver {
    public static final int MAX_SCORE_TOLERANCE = 4;
    public static final int MAX_TERMINAL_MEMBERS = 3;
    public static final int MIN_FULL_METRIC_SIMILARITY = 88;
    public static final int MAX_INDIVIDUAL_METRIC_DISTANCE = 24;
    public static final int MIN_ROLE_SIMILARITY = 80;
    public static final int SECONDARY_FULL_METRIC_SIMILARITY = 94;
    public static final int SECONDARY_MAX_INDIVIDUAL_METRIC_DISTANCE = 12;
    public static final int SECONDARY_ROLE_SIMILARITY = 94;

    private static final Comparator<AutomaticWeaponRoleSignature> SCORE_ORDER =
            Comparator.comparingInt(AutomaticWeaponRoleSignature::mechanicalScore)
                    .reversed()
                    .thenComparing(AutomaticWeaponRoleSignature::blueprintId);

    public AutomaticWeaponTerminalCluster resolve(
            List<String> branchMemberIds,
            Map<String, AutomaticWeaponRoleSignature> signatures) {
        if (branchMemberIds == null || signatures == null
                || branchMemberIds.isEmpty()
                || branchMemberIds.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || branchMemberIds.stream().distinct().count() != branchMemberIds.size()
                || branchMemberIds.stream().anyMatch(id ->
                        id == null || signatures.get(id) == null
                                || !id.equals(signatures.get(id).blueprintId()))) {
            throw new IllegalArgumentException(
                    "Automatic terminal-cluster inputs are invalid");
        }
        List<AutomaticWeaponRoleSignature> reliable = branchMemberIds.stream()
                .map(signatures::get)
                .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                .sorted(SCORE_ORDER)
                .toList();
        if (reliable.isEmpty()) {
            return AutomaticWeaponTerminalCluster.none(0);
        }

        AutomaticWeaponRoleSignature anchor = reliable.get(0);
        int tolerance = adaptiveTolerance(anchor, reliable);
        List<AutomaticWeaponRoleSignature> equivalent = new ArrayList<>();
        SimilarityEnvelope equivalenceEnvelope = new SimilarityEnvelope(anchor);
        for (AutomaticWeaponRoleSignature candidate : reliable) {
            if (anchor.mechanicalScore() - candidate.mechanicalScore() > tolerance) {
                break;
            }
            if (equivalenceEnvelope.canInclude(
                    candidate,
                    MIN_FULL_METRIC_SIMILARITY,
                    MAX_INDIVIDUAL_METRIC_DISTANCE,
                    MIN_ROLE_SIMILARITY)) {
                equivalent.add(candidate);
                equivalenceEnvelope.include(candidate);
            }
        }
        if (equivalent.isEmpty() || !equivalent.get(0).equals(anchor)) {
            throw new IllegalStateException(
                    "Automatic terminal-cluster anchor was not retained");
        }

        List<AutomaticWeaponRoleSignature> selected;
        AutomaticWeaponTerminalCluster.Resolution resolution;
        Optional<String> diagnostic = Optional.empty();
        if (equivalent.size() == 1) {
            selected = equivalent;
            resolution = AutomaticWeaponTerminalCluster.Resolution.SINGLE;
        } else if (equivalent.size() <= MAX_TERMINAL_MEMBERS) {
            selected = equivalent;
            resolution = AutomaticWeaponTerminalCluster.Resolution.EQUIVALENT;
        } else {
            selected = secondaryRolePartition(anchor, equivalent);
            if (selected.size() < equivalent.size()
                    && selected.size() <= MAX_TERMINAL_MEMBERS) {
                resolution = AutomaticWeaponTerminalCluster.Resolution.ROLE_PARTITIONED;
            } else {
                selected = rankByAnchorAffinity(anchor, equivalent).stream()
                        .limit(MAX_TERMINAL_MEMBERS)
                        .toList();
                resolution = AutomaticWeaponTerminalCluster.Resolution.TRUNCATED;
                diagnostic = Optional.of(
                        AutomaticWeaponTerminalCluster.TRUNCATED_DIAGNOSTIC);
            }
        }

        Set<String> selectedIds = selected.stream()
                .map(AutomaticWeaponRoleSignature::blueprintId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> deferredIds = equivalent.stream()
                .map(AutomaticWeaponRoleSignature::blueprintId)
                .filter(id -> !selectedIds.contains(id))
                .sorted()
                .toList();
        return new AutomaticWeaponTerminalCluster(
                Optional.of(anchor.blueprintId()),
                selectedIds.stream().sorted().toList(),
                deferredIds,
                reliable.size(),
                equivalent.size(),
                tolerance,
                resolution,
                diagnostic);
    }

    private static int adaptiveTolerance(
            AutomaticWeaponRoleSignature anchor,
            List<AutomaticWeaponRoleSignature> reliable) {
        List<Integer> localGaps = new ArrayList<>();
        AutomaticWeaponRoleSignature previous = anchor;
        for (int index = 1; index < reliable.size(); index++) {
            AutomaticWeaponRoleSignature candidate = reliable.get(index);
            int distanceFromAnchor = anchor.mechanicalScore() - candidate.mechanicalScore();
            if (distanceFromAnchor > MAX_SCORE_TOLERANCE) {
                break;
            }
            if (!compatible(anchor, candidate)) {
                continue;
            }
            int gap = previous.mechanicalScore() - candidate.mechanicalScore();
            if (gap > 0 && gap <= MAX_SCORE_TOLERANCE) {
                localGaps.add(gap);
            }
            previous = candidate;
        }
        if (localGaps.isEmpty()) {
            return 1;
        }
        localGaps.sort(Integer::compareTo);
        int median = localGaps.get(localGaps.size() / 2);
        return Math.min(MAX_SCORE_TOLERANCE, Math.max(1, median + 1));
    }

    private static boolean compatible(
            AutomaticWeaponRoleSignature anchor,
            AutomaticWeaponRoleSignature candidate) {
        if (anchor.explosive() != candidate.explosive()) {
            return false;
        }
        return anchor.similarityTo(candidate).orElse(0) >= MIN_ROLE_SIMILARITY
                && fullMetricSimilarity(anchor, candidate).orElse(0)
                        >= MIN_FULL_METRIC_SIMILARITY
                && maximumMetricDistance(anchor, candidate).orElse(Integer.MAX_VALUE)
                        <= MAX_INDIVIDUAL_METRIC_DISTANCE;
    }

    private static List<AutomaticWeaponRoleSignature> secondaryRolePartition(
            AutomaticWeaponRoleSignature anchor,
            List<AutomaticWeaponRoleSignature> equivalent) {
        List<AutomaticWeaponRoleSignature> exactArchetype = equivalent.stream()
                .filter(candidate -> anchor.archetype().equals(candidate.archetype()))
                .toList();
        if (!exactArchetype.isEmpty()
                && exactArchetype.size() <= MAX_TERMINAL_MEMBERS
                && exactArchetype.size() < equivalent.size()) {
            return rankByAnchorAffinity(anchor, exactArchetype);
        }
        List<AutomaticWeaponRoleSignature> closestRole = new ArrayList<>();
        SimilarityEnvelope closestEnvelope = new SimilarityEnvelope(anchor);
        for (AutomaticWeaponRoleSignature candidate
                : rankByAnchorAffinity(anchor, equivalent)) {
            if (closestEnvelope.canInclude(
                    candidate,
                    SECONDARY_FULL_METRIC_SIMILARITY,
                    SECONDARY_MAX_INDIVIDUAL_METRIC_DISTANCE,
                    SECONDARY_ROLE_SIMILARITY)) {
                closestRole.add(candidate);
                closestEnvelope.include(candidate);
            }
        }
        if (!closestRole.isEmpty()
                && closestRole.size() <= MAX_TERMINAL_MEMBERS
                && closestRole.size() < equivalent.size()) {
            return rankByAnchorAffinity(anchor, closestRole);
        }
        return equivalent;
    }

    private static List<AutomaticWeaponRoleSignature> rankByAnchorAffinity(
            AutomaticWeaponRoleSignature anchor,
            List<AutomaticWeaponRoleSignature> candidates) {
        return candidates.stream().sorted(Comparator
                .comparingInt(AutomaticWeaponRoleSignature::mechanicalScore)
                .reversed()
                .thenComparing(
                        Comparator.comparingInt((AutomaticWeaponRoleSignature candidate) ->
                                fullMetricSimilarity(anchor, candidate).orElse(0)).reversed())
                .thenComparing(
                        Comparator.comparingInt((AutomaticWeaponRoleSignature candidate) ->
                                anchor.similarityTo(candidate).orElse(0)).reversed())
                .thenComparing(AutomaticWeaponRoleSignature::blueprintId))
                .toList();
    }

    static OptionalInt fullMetricSimilarity(
            AutomaticWeaponRoleSignature left,
            AutomaticWeaponRoleSignature right) {
        if (left == null || right == null
                || !left.scoredEvidence() || !right.scoredEvidence()) {
            return OptionalInt.empty();
        }
        long weightedDistance = 0L;
        int totalWeight = 0;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int leftValue = Math.addExact(
                    left.strengthBaseline(),
                    left.relativeMetricOffsets().get(metric.serializedName()));
            int rightValue = Math.addExact(
                    right.strengthBaseline(),
                    right.relativeMetricOffsets().get(metric.serializedName()));
            weightedDistance = Math.addExact(
                    weightedDistance,
                    Math.multiplyExact((long) metric.weight(),
                            Math.abs(leftValue - rightValue)));
            totalWeight = Math.addExact(totalWeight, metric.weight());
        }
        int normalizedDistance = Math.min(
                ResearchTechTreeContract.SCORE_MAX,
                Math.toIntExact(Math.addExact(
                        weightedDistance, totalWeight / 2L) / totalWeight));
        return OptionalInt.of(ResearchTechTreeContract.SCORE_MAX - normalizedDistance);
    }

    static OptionalInt maximumMetricDistance(
            AutomaticWeaponRoleSignature left,
            AutomaticWeaponRoleSignature right) {
        if (left == null || right == null
                || !left.scoredEvidence() || !right.scoredEvidence()) {
            return OptionalInt.empty();
        }
        int maximum = 0;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int leftValue = Math.addExact(
                    left.strengthBaseline(),
                    left.relativeMetricOffsets().get(metric.serializedName()));
            int rightValue = Math.addExact(
                    right.strengthBaseline(),
                    right.relativeMetricOffsets().get(metric.serializedName()));
            maximum = Math.max(maximum, Math.abs(leftValue - rightValue));
        }
        return OptionalInt.of(maximum);
    }

    /**
     * Conservative complete-link envelope. Summed metric ranges upper-bound the
     * distance of every pair already admitted, avoiding quadratic scans for
     * large exact-tie catalogs.
     */
    private static final class SimilarityEnvelope {
        private final int[] minimumFull = new int[MechanicalMetric.values().length];
        private final int[] maximumFull = new int[MechanicalMetric.values().length];
        private final int[] minimumRole = new int[MechanicalMetric.values().length];
        private final int[] maximumRole = new int[MechanicalMetric.values().length];
        private final boolean explosive;
        private final String firstArchetype;
        private boolean mixedArchetypes;

        private SimilarityEnvelope(AutomaticWeaponRoleSignature anchor) {
            if (anchor == null || !anchor.scoredEvidence()) {
                throw new IllegalArgumentException(
                        "Automatic terminal similarity envelope requires scored evidence");
            }
            explosive = anchor.explosive();
            firstArchetype = anchor.archetype();
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                int index = metric.ordinal();
                int full = fullMetricValue(anchor, metric);
                int role = anchor.relativeMetricOffsets().get(metric.serializedName());
                minimumFull[index] = full;
                maximumFull[index] = full;
                minimumRole[index] = role;
                maximumRole[index] = role;
            }
        }

        private boolean canInclude(
                AutomaticWeaponRoleSignature candidate,
                int minimumFullSimilarity,
                int maximumIndividualDistance,
                int minimumRoleSimilarity) {
            if (candidate == null || !candidate.scoredEvidence()
                    || candidate.explosive() != explosive) {
                return false;
            }
            long fullRange = 0L;
            long roleRange = 0L;
            int totalWeight = 0;
            int largestFullRange = 0;
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                int index = metric.ordinal();
                int full = fullMetricValue(candidate, metric);
                int role = candidate.relativeMetricOffsets().get(metric.serializedName());
                int nextFullRange = Math.max(maximumFull[index], full)
                        - Math.min(minimumFull[index], full);
                int nextRoleRange = Math.max(maximumRole[index], role)
                        - Math.min(minimumRole[index], role);
                largestFullRange = Math.max(largestFullRange, nextFullRange);
                fullRange = Math.addExact(
                        fullRange,
                        Math.multiplyExact((long) metric.weight(), nextFullRange));
                roleRange = Math.addExact(
                        roleRange,
                        Math.multiplyExact((long) metric.weight(), nextRoleRange));
                totalWeight = Math.addExact(totalWeight, metric.weight());
            }
            int fullSimilarity = ResearchTechTreeContract.SCORE_MAX - Math.min(
                    ResearchTechTreeContract.SCORE_MAX,
                    roundedDivide(fullRange, totalWeight));
            int metricRoleSimilarity = ResearchTechTreeContract.SCORE_MAX - Math.min(
                    ResearchTechTreeContract.SCORE_MAX,
                    roundedDivide(roleRange, totalWeight));
            boolean wouldMixArchetypes = mixedArchetypes
                    || !firstArchetype.equals(candidate.archetype());
            int roleSimilarity = roundedDivide(
                    Math.addExact(
                            Math.multiplyExact(
                                    (long) metricRoleSimilarity,
                                    AutomaticWeaponRoleSignature
                                            .METRIC_SIMILARITY_WEIGHT),
                            Math.multiplyExact(
                                    wouldMixArchetypes
                                            ? 0L : ResearchTechTreeContract.SCORE_MAX,
                                    AutomaticWeaponRoleSignature
                                            .ARCHETYPE_SIMILARITY_WEIGHT)),
                    100);
            return largestFullRange <= maximumIndividualDistance
                    && fullSimilarity >= minimumFullSimilarity
                    && roleSimilarity >= minimumRoleSimilarity;
        }

        private void include(AutomaticWeaponRoleSignature candidate) {
            if (candidate == null || !candidate.scoredEvidence()
                    || candidate.explosive() != explosive) {
                throw new IllegalArgumentException(
                        "Automatic terminal similarity member is invalid");
            }
            mixedArchetypes |= !firstArchetype.equals(candidate.archetype());
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                int index = metric.ordinal();
                int full = fullMetricValue(candidate, metric);
                int role = candidate.relativeMetricOffsets().get(metric.serializedName());
                minimumFull[index] = Math.min(minimumFull[index], full);
                maximumFull[index] = Math.max(maximumFull[index], full);
                minimumRole[index] = Math.min(minimumRole[index], role);
                maximumRole[index] = Math.max(maximumRole[index], role);
            }
        }

        private static int fullMetricValue(
                AutomaticWeaponRoleSignature signature,
                MechanicalMetric metric) {
            return Math.addExact(
                    signature.strengthBaseline(),
                    signature.relativeMetricOffsets().get(metric.serializedName()));
        }

        private static int roundedDivide(long numerator, int denominator) {
            if (numerator < 0L || denominator < 1) {
                throw new IllegalArgumentException(
                        "Automatic terminal similarity division is invalid");
            }
            return Math.toIntExact(
                    Math.addExact(numerator, denominator / 2L) / denominator);
        }
    }
}
