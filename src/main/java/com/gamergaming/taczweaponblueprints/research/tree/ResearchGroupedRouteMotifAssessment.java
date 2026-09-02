package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayList;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

/**
 * Read-only Phase-6 decision gate for targeted automatic-route motifs.
 *
 * <p>The assessment consumes the Phase-5 semantic audit and the existing
 * server topology audit. It never changes ranks, prerequisites, requirements,
 * costs, layout, or publication authority. A recommendation means only that a
 * separately versioned motif prototype is justified.</p>
 */
public final class ResearchGroupedRouteMotifAssessment {
    public static final String CONTRACT = "evidence-gate-v1";
    public static final long ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS = 40_000L;
    public static final long ROUTE_COST_RATIO_MAX_REVIEW_LIMIT_BASIS_POINTS = 80_000L;
    public static final int MINIMUM_LADDER_P95_REVIEW_LIMIT = 6;

    private ResearchGroupedRouteMotifAssessment() {
    }

    public static Assessment assess(
            ResearchGroupedRouteQualityAudit.Audit quality,
            ResearchTechTreeTopologyAudit.Audit topology) {
        if (quality == null || topology == null) {
            throw new IllegalArgumentException(
                    "Grouped-route motif assessment inputs cannot be null");
        }
        if (!quality.available()) {
            return Assessment.EMPTY;
        }
        ResearchTechTreeTopologyAudit.DomainAudit weapons =
                topology.domain(Domain.WEAPONS).orElse(null);
        if (weapons == null || weapons.nodeCount() != quality.weaponNodeCount()) {
            return new Assessment(
                    false,
                    CONTRACT,
                    Decision.INSUFFICIENT_EVIDENCE,
                    quality.weaponNodeCount(),
                    ladderReviewLimit(quality.weaponNodeCount()),
                    ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS,
                    List.of(),
                    List.of(),
                    VisualEvidence.EMPTY);
        }

        int ladderLimit = ladderReviewLimit(quality.weaponNodeCount());
        int ladderMaximumLimit = Math.multiplyExact(ladderLimit, 2);
        int ineffectiveAlternatives = Math.subtractExact(
                quality.alternativeGroupCount(),
                quality.effectiveAlternativeGroupCount());
        int branchBottlenecks = Math.toIntExact(quality.branchEntries().stream()
                .filter(branch -> branch.distinctEntranceCount() < 2)
                .count());
        int ladderP95 = quality.singleRouteChainLengths().percentile95();
        int ladderMaximum = quality.singleRouteChainLengths().maximum();
        long routeCostRatioP95 = quality.alternatives()
                .routeCostRatioUpperBoundBasisPoints().percentile95();
        long routeCostRatioMaximum = quality.alternatives()
                .routeCostRatioUpperBoundBasisPoints().maximum();

        List<Signal> signals = List.of(
                Signal.count(
                        SignalCode.AUTHORITY_DRIFT,
                        quality.unmatchedAutomaticTargetCount(),
                        true),
                Signal.count(
                        SignalCode.INEFFECTIVE_ALTERNATIVE_GROUPS,
                        ineffectiveAlternatives,
                        true),
                Signal.count(
                        SignalCode.DEPENDENT_ALTERNATIVE_PAIRS,
                        quality.alternatives().dependentAlternativePairCount(),
                        true),
                Signal.count(
                        SignalCode.SINGLE_ENTRY_BRANCHES,
                        branchBottlenecks,
                        true),
                Signal.count(
                        SignalCode.ZERO_COST_IMBALANCED_GROUPS,
                        quality.alternatives().zeroCostImbalancedGroupCount(),
                        true),
                Signal.threshold(
                        SignalCode.SINGLE_ROUTE_LADDER_P95,
                        ladderP95,
                        ladderLimit,
                        true),
                Signal.threshold(
                        SignalCode.SINGLE_ROUTE_LADDER_MAXIMUM,
                        ladderMaximum,
                        ladderMaximumLimit,
                        true),
                Signal.threshold(
                        SignalCode.ROUTE_COST_RATIO_P95,
                        routeCostRatioP95,
                        ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS,
                        true),
                Signal.threshold(
                        SignalCode.ROUTE_COST_RATIO_MAXIMUM,
                        routeCostRatioMaximum,
                        ROUTE_COST_RATIO_MAX_REVIEW_LIMIT_BASIS_POINTS,
                        true));

        boolean authorityComplete = quality.unmatchedAutomaticTargetCount() == 0;
        boolean prototypeRecommended = signals.stream()
                .filter(signal -> signal.code() != SignalCode.AUTHORITY_DRIFT)
                .anyMatch(Signal::triggered);
        Decision decision = !authorityComplete
                ? Decision.INSUFFICIENT_EVIDENCE
                : prototypeRecommended
                        ? Decision.PROTOTYPE_TARGETED_MOTIFS
                        : Decision.RETAIN_CURRENT_GROUPED_ROUTES;

        List<Motif> motifs = new ArrayList<>();
        if (ineffectiveAlternatives > 0
                || quality.alternatives().dependentAlternativePairCount() > 0) {
            motifs.add(Motif.STAGGERED_DIAMONDS);
        }
        if (branchBottlenecks > 0) {
            motifs.add(Motif.MULTI_ENTRY_BRANCH_FANS);
        }
        if (ladderP95 > ladderLimit || ladderMaximum > ladderMaximumLimit) {
            motifs.add(Motif.BRANCH_LOCAL_DIAMONDS);
        }
        if (routeCostRatioP95
                > ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS
                || routeCostRatioMaximum
                        > ROUTE_COST_RATIO_MAX_REVIEW_LIMIT_BASIS_POINTS
                || quality.alternatives().zeroCostImbalancedGroupCount() > 0) {
            motifs.add(Motif.COST_BALANCED_ALTERNATIVES);
        }
        if (!authorityComplete) {
            motifs.clear();
        }

        return new Assessment(
                true,
                CONTRACT,
                decision,
                quality.weaponNodeCount(),
                ladderLimit,
                ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS,
                signals,
                motifs,
                new VisualEvidence(
                        weapons.approximateEdgeCrossingCount(),
                        false,
                        0L,
                        weapons.approximateEdgeCrossingCount() > 0L));
    }

    /** Catalog-scale-aware review limit; this is advisory and never a build gate. */
    static int ladderReviewLimit(int nodeCount) {
        if (nodeCount < 0) {
            throw new IllegalArgumentException("Weapon node count cannot be negative");
        }
        int logarithmic = nodeCount <= 1
                ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(nodeCount - 1);
        return Math.max(MINIMUM_LADDER_P95_REVIEW_LIMIT, logarithmic);
    }

    public enum Decision {
        INSUFFICIENT_EVIDENCE("insufficient_evidence"),
        RETAIN_CURRENT_GROUPED_ROUTES("retain_current_grouped_routes"),
        PROTOTYPE_TARGETED_MOTIFS("prototype_targeted_motifs");

        private final String serializedName;

        Decision(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum SignalCode {
        AUTHORITY_DRIFT("authority_drift"),
        INEFFECTIVE_ALTERNATIVE_GROUPS("ineffective_alternative_groups"),
        DEPENDENT_ALTERNATIVE_PAIRS("dependent_alternative_pairs"),
        SINGLE_ENTRY_BRANCHES("single_entry_branches"),
        ZERO_COST_IMBALANCED_GROUPS("zero_cost_imbalanced_groups"),
        SINGLE_ROUTE_LADDER_P95("single_route_ladder_p95"),
        SINGLE_ROUTE_LADDER_MAXIMUM("single_route_ladder_maximum"),
        ROUTE_COST_RATIO_P95("route_cost_ratio_p95"),
        ROUTE_COST_RATIO_MAXIMUM("route_cost_ratio_maximum");

        private final String serializedName;

        SignalCode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum Motif {
        STAGGERED_DIAMONDS("staggered_diamonds"),
        MULTI_ENTRY_BRANCH_FANS("multi_entry_branch_fans"),
        BRANCH_LOCAL_DIAMONDS("branch_local_diamonds"),
        COST_BALANCED_ALTERNATIVES("cost_balanced_alternatives");

        private final String serializedName;

        Motif(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public record Signal(
            SignalCode code,
            long observed,
            long reviewLimit,
            boolean triggered,
            boolean decisionRelevant) {
        public Signal {
            if (code == null || observed < 0L || reviewLimit < 0L
                    || triggered != (observed > reviewLimit)) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route motif signal");
            }
        }

        private static Signal count(
                SignalCode code,
                long observed,
                boolean decisionRelevant) {
            return threshold(code, observed, 0L, decisionRelevant);
        }

        private static Signal threshold(
                SignalCode code,
                long observed,
                long reviewLimit,
                boolean decisionRelevant) {
            return new Signal(
                    code,
                    observed,
                    reviewLimit,
                    observed > reviewLimit,
                    decisionRelevant);
        }
    }

    /**
     * The topology audit counts semantic-order inversions before the client
     * collapses alternative edges through rendered junctions. It is retained
     * for manual comparison, but cannot activate a motif recommendation.
     */
    public record VisualEvidence(
            long preJunctionApproximateCrossingCount,
            boolean postJunctionMeasurementAvailable,
            long postJunctionCrossingCount,
            boolean manualReviewRequired) {
        public static final VisualEvidence EMPTY =
                new VisualEvidence(0L, false, 0L, false);

        public VisualEvidence {
            if (preJunctionApproximateCrossingCount < 0L
                    || postJunctionCrossingCount < 0L
                    || !postJunctionMeasurementAvailable
                            && (postJunctionCrossingCount != 0L
                                    || manualReviewRequired
                                            != (preJunctionApproximateCrossingCount > 0L))) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route motif visual evidence");
            }
        }
    }

    public record Assessment(
            boolean available,
            String contract,
            Decision decision,
            int weaponNodeCount,
            int ladderP95ReviewLimit,
            long routeCostRatioP95ReviewLimitBasisPoints,
            List<Signal> signals,
            List<Motif> recommendedMotifs,
            VisualEvidence visualEvidence) {
        public static final Assessment EMPTY = new Assessment(
                false,
                CONTRACT,
                Decision.INSUFFICIENT_EVIDENCE,
                0,
                MINIMUM_LADDER_P95_REVIEW_LIMIT,
                ROUTE_COST_RATIO_P95_REVIEW_LIMIT_BASIS_POINTS,
                List.of(),
                List.of(),
                VisualEvidence.EMPTY);

        public Assessment {
            signals = signals == null ? List.of() : List.copyOf(signals);
            recommendedMotifs = recommendedMotifs == null
                    ? List.of() : List.copyOf(recommendedMotifs);
            visualEvidence = visualEvidence == null
                    ? VisualEvidence.EMPTY : visualEvidence;
            long authorityDrift = signals.stream()
                    .filter(value -> value.code() == SignalCode.AUTHORITY_DRIFT)
                    .mapToLong(Signal::observed).findFirst().orElse(0L);
            long decisiveSignals = signals.stream()
                    .filter(Signal::decisionRelevant)
                    .filter(Signal::triggered)
                    .filter(signal -> signal.code() != SignalCode.AUTHORITY_DRIFT)
                    .count();
            if (!CONTRACT.equals(contract) || decision == null
                    || weaponNodeCount < 0
                    || ladderP95ReviewLimit < MINIMUM_LADDER_P95_REVIEW_LIMIT
                    || routeCostRatioP95ReviewLimitBasisPoints < 10_000L
                    || signals.stream().anyMatch(java.util.Objects::isNull)
                    || recommendedMotifs.stream().anyMatch(java.util.Objects::isNull)
                    || signals.stream().map(Signal::code).distinct().count()
                            != signals.size()
                    || recommendedMotifs.stream().distinct().count()
                            != recommendedMotifs.size()
                    || !available && (decision != Decision.INSUFFICIENT_EVIDENCE
                            || !signals.isEmpty() || !recommendedMotifs.isEmpty()
                            || !visualEvidence.equals(VisualEvidence.EMPTY))
                    || available && signals.size() != SignalCode.values().length
                    || available && authorityDrift > 0L
                            && decision != Decision.INSUFFICIENT_EVIDENCE
                    || available && decision == Decision.INSUFFICIENT_EVIDENCE
                            && authorityDrift == 0L
                    || available && decision != Decision.PROTOTYPE_TARGETED_MOTIFS
                            && !recommendedMotifs.isEmpty()
                    || available && decision == Decision.RETAIN_CURRENT_GROUPED_ROUTES
                            && decisiveSignals != 0L
                    || available && decision == Decision.PROTOTYPE_TARGETED_MOTIFS
                            && (decisiveSignals == 0L || recommendedMotifs.isEmpty())) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route motif assessment");
            }
        }

        public Signal signal(SignalCode code) {
            return signals.stream().filter(value -> value.code() == code)
                    .findFirst().orElse(new Signal(code, 0L, 0L, false, false));
        }

        public int decisiveSignalCount() {
            return Math.toIntExact(signals.stream()
                    .filter(Signal::decisionRelevant)
                    .filter(Signal::triggered)
                    .filter(signal -> signal.code() != SignalCode.AUTHORITY_DRIFT)
                    .count());
        }

        public boolean motifPrototypeRecommended() {
            return decision == Decision.PROTOTYPE_TARGETED_MOTIFS;
        }
    }
}
