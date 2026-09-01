package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

import net.minecraft.resources.ResourceLocation;

/** Immutable explanation captured at the exact branch-aware parent-selection boundary. */
public record AutomaticWeaponPrerequisiteDecision(
        ResourceLocation blueprintId,
        Strategy strategy,
        Optional<Integer> branchIndex,
        int rankIndex,
        int familyStartIndex,
        int transitionEndIndex,
        int desiredParentCount,
        int secondParentQuotaBasisPoints,
        boolean secondParentEligible,
        Map<ResourceLocation, ParentRelation> selectedParentRelations,
        Optional<MergeRejection> mergeRejection,
        boolean depthShortcut,
        boolean terminalPeer,
        Optional<Integer> publishedRank,
        Optional<AlternativeRouteReview> alternativeRouteReview,
        GeneratedRequirementShape generatedRequirementShape) {
    /** Compatibility constructor for decisions recorded before rank publication. */
    public AutomaticWeaponPrerequisiteDecision(
            ResourceLocation blueprintId,
            Strategy strategy,
            Optional<Integer> branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex,
            int desiredParentCount,
            int secondParentQuotaBasisPoints,
            boolean secondParentEligible,
            Map<ResourceLocation, ParentRelation> selectedParentRelations,
            Optional<MergeRejection> mergeRejection,
            boolean depthShortcut,
            boolean terminalPeer) {
        this(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                secondParentQuotaBasisPoints,
                secondParentEligible,
                selectedParentRelations,
                mergeRejection,
                depthShortcut,
                terminalPeer,
                Optional.empty(),
                Optional.empty(),
                GeneratedRequirementShape.MANDATORY_SINGLETONS);
    }

    /** Compatibility constructor for decisions carrying a published rank. */
    public AutomaticWeaponPrerequisiteDecision(
            ResourceLocation blueprintId,
            Strategy strategy,
            Optional<Integer> branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex,
            int desiredParentCount,
            int secondParentQuotaBasisPoints,
            boolean secondParentEligible,
            Map<ResourceLocation, ParentRelation> selectedParentRelations,
            Optional<MergeRejection> mergeRejection,
            boolean depthShortcut,
            boolean terminalPeer,
            Optional<Integer> publishedRank) {
        this(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                secondParentQuotaBasisPoints,
                secondParentEligible,
                selectedParentRelations,
                mergeRejection,
                depthShortcut,
                terminalPeer,
                publishedRank,
                Optional.empty(),
                GeneratedRequirementShape.MANDATORY_SINGLETONS);
    }

    /** Compatibility constructor for decision fixtures predating Phase 8 evidence. */
    public AutomaticWeaponPrerequisiteDecision(
            ResourceLocation blueprintId,
            Strategy strategy,
            Optional<Integer> branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex,
            int desiredParentCount,
            Map<ResourceLocation, ParentRelation> selectedParentRelations,
            boolean depthShortcut,
            boolean terminalPeer) {
        this(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                desiredParentCount > 1 ? 10_000 : 0,
                desiredParentCount > 1,
                selectedParentRelations,
                Optional.empty(),
                depthShortcut,
                terminalPeer,
                Optional.empty(),
                Optional.empty(),
                GeneratedRequirementShape.MANDATORY_SINGLETONS);
    }

    /** Compatibility constructor for decisions created before explicit relationship intent. */
    public AutomaticWeaponPrerequisiteDecision(
            ResourceLocation blueprintId,
            Strategy strategy,
            Optional<Integer> branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex,
            int desiredParentCount,
            int secondParentQuotaBasisPoints,
            boolean secondParentEligible,
            Map<ResourceLocation, ParentRelation> selectedParentRelations,
            Optional<MergeRejection> mergeRejection,
            boolean depthShortcut,
            boolean terminalPeer,
            Optional<Integer> publishedRank,
            Optional<AlternativeRouteReview> alternativeRouteReview) {
        this(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                secondParentQuotaBasisPoints,
                secondParentEligible,
                selectedParentRelations,
                mergeRejection,
                depthShortcut,
                terminalPeer,
                publishedRank,
                alternativeRouteReview,
                GeneratedRequirementShape.MANDATORY_SINGLETONS);
    }

    public AutomaticWeaponPrerequisiteDecision {
        branchIndex = branchIndex == null ? Optional.empty() : branchIndex;
        mergeRejection = mergeRejection == null ? Optional.empty() : mergeRejection;
        publishedRank = publishedRank == null ? Optional.empty() : publishedRank;
        alternativeRouteReview = alternativeRouteReview == null
                ? Optional.empty() : alternativeRouteReview;
        if (blueprintId == null || strategy == null || generatedRequirementShape == null
                || branchIndex.filter(value -> value < 0
                        || value >= AutomaticWeaponBranchAnalyzer.MAX_BRANCHES).isPresent()
                || rankIndex < 0
                || rankIndex > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                || familyStartIndex < 0 || familyStartIndex > transitionEndIndex
                || transitionEndIndex > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                || desiredParentCount < 1
                || desiredParentCount
                        > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                || secondParentQuotaBasisPoints < 0
                || secondParentQuotaBasisPoints > 10_000
                || secondParentEligible != (desiredParentCount > 1)
                || selectedParentRelations == null
                || selectedParentRelations.size()
                        > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                || publishedRank.filter(value -> value < 0
                        || value > ResearchTechTreeContract.MAX_PROGRESSION_RANK).isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite decision is invalid");
        }
        LinkedHashMap<ResourceLocation, ParentRelation> parents = new LinkedHashMap<>();
        selectedParentRelations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || entry.getKey().equals(blueprintId)) {
                        throw new IllegalArgumentException(
                                "Automatic prerequisite decision parent is invalid");
                    }
                    parents.put(entry.getKey(), entry.getValue());
        });
        selectedParentRelations = Collections.unmodifiableMap(parents);
        if (generatedRequirementShape == GeneratedRequirementShape.ALTERNATIVE_ROUTES
                    && parents.size() > 2
                || generatedRequirementShape
                                == GeneratedRequirementShape
                                        .ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY
                        && parents.size() != 3) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite relationship shape is inconsistent");
        }
        if (mergeRejection.filter(value -> value.parentId().equals(blueprintId)
                || parents.containsKey(value.parentId())).isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite merge rejection is inconsistent");
        }
        if (alternativeRouteReview.filter(value ->
                value.parentId().equals(blueprintId)
                        || value.accepted()
                                != parents.containsKey(value.parentId())).isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite alternative-route review is inconsistent");
        }
        boolean rankMatchesStrategy = switch (strategy) {
            case FOUNDATION -> rankIndex == 0;
            case SHARED_TRUNK -> rankIndex > 0 && rankIndex < familyStartIndex;
            case TRANSITION_CROSS_FAMILY, TRANSITION_LOCAL ->
                    rankIndex >= familyStartIndex && rankIndex <= transitionEndIndex;
            case SPECIALIZATION -> rankIndex > transitionEndIndex;
        };
        if (!rankMatchesStrategy
                || depthShortcut && selectedParentRelations.values().stream()
                        .noneMatch(ParentRelation::sameFamily)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite decision contradicts its strategy");
        }
    }

    public AutomaticWeaponPrerequisiteDecision withPublishedRank(int rank) {
        return new AutomaticWeaponPrerequisiteDecision(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                secondParentQuotaBasisPoints,
                secondParentEligible,
                selectedParentRelations,
                mergeRejection,
                depthShortcut,
                terminalPeer,
                Optional.of(rank),
                alternativeRouteReview,
                generatedRequirementShape);
    }

    /**
     * Connects a provisional generated foundation to its one resolved automatic
     * entry point. Rank finalization subsequently lifts the dependent above the
     * shared provisional row.
     */
    public AutomaticWeaponPrerequisiteDecision withSingleSelectedParent(
            ResourceLocation parent,
            ParentRelation relation) {
        if (parent == null || relation == null
                || parent.equals(blueprintId)
                || !selectedParentRelations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite foundation parent is invalid");
        }
        return new AutomaticWeaponPrerequisiteDecision(
                blueprintId,
                strategy,
                branchIndex,
                rankIndex,
                familyStartIndex,
                transitionEndIndex,
                desiredParentCount,
                secondParentQuotaBasisPoints,
                secondParentEligible,
                Map.of(parent, relation),
                Optional.empty(),
                false,
                terminalPeer,
                publishedRank,
                Optional.empty(),
                GeneratedRequirementShape.MANDATORY_SINGLETONS);
    }

    public int sameFamilyParentCount() {
        return Math.toIntExact(selectedParentRelations.values().stream()
                .filter(ParentRelation::sameFamily).count());
    }

    public int crossFamilyParentCount() {
        return Math.toIntExact(selectedParentRelations.values().stream()
                .filter(ParentRelation::crossFamily).count());
    }

    public int unclassifiedParentCount() {
        return Math.toIntExact(selectedParentRelations.values().stream()
                .filter(value -> value == ParentRelation.UNCLASSIFIED).count());
    }

    public boolean mergeRejectedForClosureInflation() {
        return mergeRejection.filter(value -> value.reason()
                == MergeRejectionReason.CLOSURE_INFLATION).isPresent();
    }

    public boolean alternativeRouteAccepted() {
        return alternativeRouteReview.filter(AlternativeRouteReview::accepted).isPresent();
    }

    public boolean alternativeRouteRejectedForCostImbalance() {
        return alternativeRouteReview.filter(value -> value.outcome()
                == AlternativeRouteOutcome.REJECTED_PROVEN_COST_IMBALANCE).isPresent();
    }

    public boolean generatedMandatoryConvergence() {
        return switch (generatedRequirementShape) {
            case MANDATORY_SINGLETONS -> selectedParentRelations.size() > 1;
            case ALTERNATIVE_ROUTES -> false;
            case ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY ->
                    selectedParentRelations.size() == 3;
        };
    }

    public boolean generatedAlternativeRoutes() {
        return generatedRequirementShape.hasAlternativeRoutes()
                && selectedParentRelations.size() > 1;
    }

    /** Explicit relationship intent selected before generated parents are materialized. */
    public enum GeneratedRequirementShape {
        MANDATORY_SINGLETONS("mandatory_singletons", false, true),
        ALTERNATIVE_ROUTES("alternative_routes", true, false),
        ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY(
                "alternative_routes_with_mandatory_gateway", true, true);

        private final String serializedName;
        private final boolean alternativeRoutes;
        private final boolean mandatoryConvergence;

        GeneratedRequirementShape(
                String serializedName,
                boolean alternativeRoutes,
                boolean mandatoryConvergence) {
            this.serializedName = serializedName;
            this.alternativeRoutes = alternativeRoutes;
            this.mandatoryConvergence = mandatoryConvergence;
        }

        public String serializedName() {
            return serializedName;
        }

        public boolean hasAlternativeRoutes() {
            return alternativeRoutes;
        }

        public boolean hasMandatoryConvergence() {
            return mandatoryConvergence;
        }

        public ParentIntent intentForSelectedIndex(int index) {
            if (index < 0
                    || this == ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY
                            && index > 2) {
                throw new IllegalArgumentException(
                        "Generated requirement parent index is invalid");
            }
            return switch (this) {
                case MANDATORY_SINGLETONS -> ParentIntent.MANDATORY;
                case ALTERNATIVE_ROUTES -> ParentIntent.ALTERNATIVE;
                case ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY ->
                        index < 2 ? ParentIntent.ALTERNATIVE : ParentIntent.MANDATORY;
            };
        }
    }

    public enum ParentIntent {
        MANDATORY,
        ALTERNATIVE
    }

    public enum Strategy {
        FOUNDATION("foundation"),
        SHARED_TRUNK("shared_trunk"),
        TRANSITION_CROSS_FAMILY("transition_cross_family"),
        TRANSITION_LOCAL("transition_local"),
        SPECIALIZATION("specialization");

        private final String serializedName;

        Strategy(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum ParentRelation {
        SAME_FAMILY("same_family", true, false),
        CROSS_FAMILY("cross_family", false, true),
        AUTHORED_SAME_FAMILY("authored_same_family", true, false),
        AUTHORED_CROSS_FAMILY("authored_cross_family", false, true),
        UNCLASSIFIED("unclassified", false, false);

        private final String serializedName;
        private final boolean sameFamily;
        private final boolean crossFamily;

        ParentRelation(
                String serializedName,
                boolean sameFamily,
                boolean crossFamily) {
            this.serializedName = serializedName;
            this.sameFamily = sameFamily;
            this.crossFamily = crossFamily;
        }

        public String serializedName() {
            return serializedName;
        }

        public boolean sameFamily() {
            return sameFamily;
        }

        public boolean crossFamily() {
            return crossFamily;
        }
    }

    public record MergeRejection(
            ResourceLocation parentId,
            MergeRejectionReason reason,
            long existingClosureCost,
            long candidateClosureCost,
            long unionClosureCost,
            long maximumAllowedClosureCost) {
        public MergeRejection {
            long dominant = Math.max(existingClosureCost, candidateClosureCost);
            if (parentId == null || reason == null
                    || existingClosureCost < 0L || candidateClosureCost < 0L
                    || unionClosureCost < dominant
                    || maximumAllowedClosureCost < dominant
                    || unionClosureCost <= maximumAllowedClosureCost) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite merge rejection is invalid");
            }
        }
    }

    public enum MergeRejectionReason {
        CLOSURE_INFLATION("merge_rejected_closure_inflation");

        private final String serializedName;

        MergeRejectionReason(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    /** Bounded evidence used to accept or decline one generated OR parent. */
    public record AlternativeRouteReview(
            ResourceLocation parentId,
            AlternativeRouteOutcome outcome,
            long existingRouteCostLowerBound,
            long existingRouteCostUpperBound,
            long candidateRouteCostLowerBound,
            long candidateRouteCostUpperBound,
            long routeCostRatioLowerBoundBasisPoints,
            long routeCostRatioUpperBoundBasisPoints,
            int mandatoryAncestryOverlapBasisPoints,
            int divergentMandatoryNodeCount,
            boolean exact) {
        public AlternativeRouteReview {
            boolean provenImbalance = routeCostRatioLowerBoundBasisPoints
                    > AutomaticWeaponAlternativeRouteGuard
                            .MAXIMUM_PROVEN_ROUTE_COST_RATIO_BASIS_POINTS;
            AlternativeRouteOutcome expectedOutcome = provenImbalance
                    ? AlternativeRouteOutcome.REJECTED_PROVEN_COST_IMBALANCE
                    : exact
                            ? AlternativeRouteOutcome.ACCEPTED_EXACT
                            : AlternativeRouteOutcome.ACCEPTED_BOUNDED;
            if (parentId == null || outcome == null
                    || existingRouteCostLowerBound < 0L
                    || existingRouteCostUpperBound < existingRouteCostLowerBound
                    || candidateRouteCostLowerBound < 0L
                    || candidateRouteCostUpperBound < candidateRouteCostLowerBound
                    || routeCostRatioLowerBoundBasisPoints < 10_000L
                    || routeCostRatioUpperBoundBasisPoints
                            < routeCostRatioLowerBoundBasisPoints
                    || mandatoryAncestryOverlapBasisPoints < 0
                    || mandatoryAncestryOverlapBasisPoints > 10_000
                    || divergentMandatoryNodeCount < 0
                    || exact != (existingRouteCostLowerBound
                            == existingRouteCostUpperBound
                            && candidateRouteCostLowerBound
                                    == candidateRouteCostUpperBound)
                    || outcome != expectedOutcome) {
                throw new IllegalArgumentException(
                        "Automatic alternative-route review is invalid");
            }
        }

        public boolean accepted() {
            return outcome != AlternativeRouteOutcome.REJECTED_PROVEN_COST_IMBALANCE;
        }
    }

    public enum AlternativeRouteOutcome {
        ACCEPTED_EXACT("accepted_exact"),
        ACCEPTED_BOUNDED("accepted_bounded"),
        REJECTED_PROVEN_COST_IMBALANCE("rejected_proven_cost_imbalance");

        private final String serializedName;

        AlternativeRouteOutcome(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }
}
