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
        Optional<Integer> publishedRank) {
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
                Optional.empty());
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
                Optional.empty());
    }

    public AutomaticWeaponPrerequisiteDecision {
        branchIndex = branchIndex == null ? Optional.empty() : branchIndex;
        mergeRejection = mergeRejection == null ? Optional.empty() : mergeRejection;
        publishedRank = publishedRank == null ? Optional.empty() : publishedRank;
        if (blueprintId == null || strategy == null
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
        if (mergeRejection.filter(value -> value.parentId().equals(blueprintId)
                || parents.containsKey(value.parentId())).isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite merge rejection is inconsistent");
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
                Optional.of(rank));
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
}
