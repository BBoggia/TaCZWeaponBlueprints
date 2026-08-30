package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.LayeringStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponRoleAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver;

import net.minecraft.resources.ResourceLocation;

/**
 * Per-weapon explanation assembled from the same immutable inputs used by
 * placement. It is observational and never feeds scores, ranks, parents, or cost
 * back into progression authority.
 */
public record ResearchTechTreeAuthoringReport(
        ResourceLocation profileId,
        Optional<ResourceLocation> treeId,
        long catalogRevision,
        long researchRevision,
        Map<ResourceLocation, Entry> entries) {
    private static final int FAN_OUT_REVIEW_LIMIT = 4;

    public ResearchTechTreeAuthoringReport {
        treeId = treeId == null ? Optional.empty() : treeId;
        if (profileId == null || catalogRevision < 0L || researchRevision < 0L
                || entries == null) {
            throw new IllegalArgumentException("Invalid Research Tech Tree authoring report");
        }
        LinkedHashMap<ResourceLocation, Entry> copy = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || !entry.getKey().equals(entry.getValue().blueprintId())) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree authoring entry is inconsistent");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        entries = Collections.unmodifiableMap(copy);
    }

    public static ResearchTechTreeAuthoringReport create(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            AutomaticWeaponEvidenceSnapshot evidence) {
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = new LinkedHashMap<>();
        if (catalog != null) {
            catalog.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> stableCatalog.put(entry.getKey(), entry.getValue()));
        }
        if (profileId == null) {
            throw new IllegalArgumentException("Authoring report profile cannot be null");
        }
        BlueprintResearchProfile profile = stableSnapshot.profiles().get(profileId);
        Optional<ResourceLocation> treeId = profile == null
                ? Optional.empty() : profile.techTree();
        if (automaticDiagnostics != null
                && (!automaticDiagnostics.profileId().equals(profileId)
                        || treeId.isPresent()
                                && !treeId.orElseThrow().equals(
                                        automaticDiagnostics.treeId()))) {
            throw new IllegalArgumentException(
                    "Automatic diagnostics do not match the authoring report");
        }
        AutomaticWeaponEvidenceSnapshot stableEvidence = evidence == null
                ? AutomaticWeaponEvidenceSnapshot.EMPTY : evidence;
        if (automaticDiagnostics != null && stableEvidence.catalogRevision() != 0L
                && stableEvidence.catalogRevision() != automaticDiagnostics.catalogRevision()) {
            throw new IllegalArgumentException(
                    "Mechanical evidence does not match automatic diagnostics");
        }

        Map<ResourceLocation, BlueprintResearchPolicy> definitions =
                new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> effectiveParents = new LinkedHashMap<>();
        stableCatalog.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null
                        && entry.getValue().getKind() == BlueprintKind.GUN)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    BlueprintResearchPolicy definition = BlueprintResearchPolicyResolver.resolve(
                            stableSnapshot,
                            stableCatalog,
                            profileId,
                            entry.getKey(),
                            null,
                            ignored -> false);
                    definitions.put(entry.getKey(), definition);
                    List<ResourceLocation> generated = automaticDiagnostics == null
                            ? List.of()
                            : automaticDiagnostics.entry(entry.getKey())
                                    .map(AutomaticWeaponPlacementDiagnostics.Entry::generatedPrerequisites)
                                    .orElse(List.of());
                    effectiveParents.put(
                            entry.getKey(),
                            generated.isEmpty() ? definition.prerequisites() : generated);
                });
        Map<ResourceLocation, Integer> fanOut = new LinkedHashMap<>();
        effectiveParents.values().stream().flatMap(List::stream)
                .forEach(parent -> fanOut.merge(parent, 1, Math::addExact));

        List<Integer> automaticRanks = automaticDiagnostics == null
                ? List.of()
                : automaticDiagnostics.entries().values().stream()
                        .flatMap(entry -> entry.proposal().stream())
                        .map(value -> value.progressionCoordinate().rank())
                        .distinct().sorted().toList();
        LinkedHashMap<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        definitions.forEach((blueprintId, definition) -> {
            AutomaticWeaponPlacementDiagnostics.Entry automatic = automaticDiagnostics == null
                    ? null : automaticDiagnostics.entry(blueprintId).orElse(null);
            AutomaticWeaponPlacementProposal proposal = automatic == null
                    ? null : automatic.proposal().orElse(null);
            WeaponMechanicalScore score = stableEvidence.scoresByBlueprint()
                    .get(blueprintId.toString());
            List<ResourceLocation> parents = effectiveParents.getOrDefault(blueprintId, List.of());
            Optional<AutomaticWeaponPrerequisiteDecision> prerequisiteDecision =
                    automatic == null
                            ? Optional.empty()
                            : automatic.prerequisiteDecision();
            List<ParentChoice> parentChoices = parents.stream().map(parent -> {
                Optional<Integer> similarity = similarity(
                        score,
                        stableEvidence.scoresByBlueprint().get(parent.toString()));
                int load = fanOut.getOrDefault(parent, 0);
                AutomaticWeaponPrerequisiteDecision.ParentRelation relation =
                        prerequisiteDecision.isEmpty()
                                ? null
                                : prerequisiteDecision.orElseThrow()
                                        .selectedParentRelations().get(parent);
                String relationship = relation == null
                        ? "unclassified" : relation.serializedName();
                return new ParentChoice(
                        parent, similarity, load, fanOutPenalty(load), relationship);
            }).toList();
            Optional<Integer> averageSimilarity = averageSimilarity(parentChoices);
            Optional<ResearchTechTreePlacementResolver.Placement> manualPlacement = treeId
                    .flatMap(tree -> ResearchTechTreePlacementResolver.resolveForProfile(
                            stableSnapshot,
                            profileId,
                            tree,
                            blueprintId,
                            stableCatalog.get(blueprintId)).placement());
            Optional<Integer> mechanicalScore = score == null
                    ? Optional.ofNullable(proposal).map(AutomaticWeaponPlacementProposal::mechanicalScore)
                    : Optional.of(score.score());
            Optional<Integer> rank = proposal == null
                    ? manualPlacement.map(value -> value.progressionCoordinate().rank())
                    : Optional.of(proposal.progressionCoordinate().rank());
            Optional<ResourceLocation> band = proposal == null
                    ? manualPlacement.flatMap(value -> value.progressionCoordinate().bandId())
                    : proposal.progressionCoordinate().bandId();
            List<String> reviewFallbackReasons = reviewFallbackReasons(automatic, proposal);
            entries.put(blueprintId, new Entry(
                    blueprintId,
                    state(automatic, manualPlacement),
                    mechanicalScore,
                    rank,
                    band,
                    parentChoices,
                    averageSimilarity,
                    parentChoices.stream().mapToInt(ParentChoice::fanOutPenalty).max().orElse(0),
                    parentChoiceReason(automatic, parents, definition),
                    mergeReason(automaticDiagnostics, automatic, proposal, parents, automaticRanks),
                    reviewFallbackReasons,
                    prerequisiteDecision));
        });
        return new ResearchTechTreeAuthoringReport(
                profileId,
                treeId,
                automaticDiagnostics == null ? stableEvidence.catalogRevision()
                        : automaticDiagnostics.catalogRevision(),
                automaticDiagnostics == null ? 0L : automaticDiagnostics.researchRevision(),
                entries);
    }

    private static String state(
            AutomaticWeaponPlacementDiagnostics.Entry automatic,
            Optional<ResearchTechTreePlacementResolver.Placement> manualPlacement) {
        if (automatic != null) {
            return automatic.state().serializedName();
        }
        return manualPlacement.isPresent() ? "manual" : "unplaced";
    }

    private static String parentChoiceReason(
            AutomaticWeaponPlacementDiagnostics.Entry automatic,
            List<ResourceLocation> parents,
            BlueprintResearchPolicy definition) {
        if (!definition.prerequisites().isEmpty()) {
            return "authored_prerequisite_authority";
        }
        if (automatic != null && automatic.prerequisiteDecision().isPresent()) {
            return automatic.prerequisiteDecision().orElseThrow().strategy().serializedName()
                    + "_selection";
        }
        if (!parents.isEmpty()) {
            return "generated_rank_anchor_selection";
        }
        if (automatic != null && automatic.reason().isPresent()) {
            return automatic.reason().orElseThrow();
        }
        return "foundation_or_independent";
    }

    private static String mergeReason(
            AutomaticWeaponPlacementDiagnostics diagnostics,
            AutomaticWeaponPlacementDiagnostics.Entry automatic,
            AutomaticWeaponPlacementProposal proposal,
            List<ResourceLocation> parents,
            List<Integer> automaticRanks) {
        if (automatic != null && automatic.prerequisiteDecision().isPresent()) {
            Optional<AutomaticWeaponPrerequisiteDecision.MergeRejection> rejection =
                    automatic.prerequisiteDecision().orElseThrow().mergeRejection();
            if (parents.size() < 2 && rejection.isPresent()) {
                return rejection.orElseThrow().reason().serializedName();
            }
        }
        if (parents.size() < 2) {
            return "none";
        }
        if (automatic == null || automatic.state()
                != AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC) {
            return "authored_and_merge";
        }
        if (automatic.prerequisiteDecision().isPresent()) {
            return switch (automatic.prerequisiteDecision().orElseThrow().strategy()) {
                case FOUNDATION -> "none";
                case SHARED_TRUNK -> "shared_trunk_interconnection";
                case TRANSITION_CROSS_FAMILY -> "transition_cross_family_merge";
                case TRANSITION_LOCAL -> "transition_same_family_merge";
                case SPECIALIZATION -> "specialization_merge";
            };
        }
        if (proposal == null || diagnostics == null
                || !diagnostics.mode().createsPrerequisite()) {
            return "generated_multi_parent";
        }
        int rankIndex = automaticRanks.indexOf(proposal.progressionCoordinate().rank());
        int mergeInterval = diagnostics.mergeInterval();
        if (diagnostics.layeringStrategy() != LayeringStrategy.DYNAMIC_STAT_LAYERS) {
            long connectedBefore = diagnostics.entries().values().stream()
                    .filter(entry -> entry.state()
                            == AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC)
                    .filter(entry -> !entry.generatedPrerequisites().isEmpty())
                    .flatMap(entry -> entry.proposal().stream())
                    .filter(value -> value.position().tier() == proposal.position().tier())
                    .filter(value -> value.position().siblingOrder()
                            < proposal.position().siblingOrder())
                    .count();
            if (proposal.position().tier() != ResearchTechTreeContract.Tier.STARTER
                    && connectedBefore == 0L) {
                return "tier_gateway";
            }
            return mergeInterval > 0 && (connectedBefore + 1L) % mergeInterval == 0L
                    ? "periodic_tier_merge"
                    : "generated_multi_parent";
        }
        int lowerTransitions = ResearchTechTreeContract.sharedMeshTransitionCount(
                automaticRanks.size());
        if (mergeInterval > 0
                && rankIndex > 0
                && rankIndex <= lowerTransitions
                && rankIndex % mergeInterval == 0) {
            return "periodic_rank_merge";
        }
        if (rankIndex >= 0 && rankIndex <= lowerTransitions) {
            return "lower_tree_interconnection";
        }
        return "generated_multi_parent";
    }

    private static List<String> reviewFallbackReasons(
            AutomaticWeaponPlacementDiagnostics.Entry automatic,
            AutomaticWeaponPlacementProposal proposal) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (proposal != null) {
            reasons.addAll(proposal.reviewReasons());
        }
        if (automatic != null) {
            automatic.reason().ifPresent(reasons::add);
        }
        return reasons.stream().sorted().toList();
    }

    private static Optional<Integer> averageSimilarity(List<ParentChoice> parents) {
        List<Integer> available = parents.stream().flatMap(value -> value.similarityScore().stream())
                .toList();
        return available.isEmpty()
                ? Optional.empty()
                : Optional.of(Math.toIntExact(Math.round(
                        available.stream().mapToInt(Integer::intValue).average().orElse(0.0))));
    }

    /** Strength-relative mechanical role affinity shared with automatic branch analysis. */
    private static Optional<Integer> similarity(
            WeaponMechanicalScore target,
            WeaponMechanicalScore parent) {
        if (target == null || parent == null) {
            return Optional.empty();
        }
        AutomaticWeaponRoleAnalyzer analyzer = new AutomaticWeaponRoleAnalyzer();
        java.util.OptionalInt result = analyzer.analyze(target)
                .similarityTo(analyzer.analyze(parent));
        return result.isPresent() ? Optional.of(result.getAsInt()) : Optional.empty();
    }

    private static int fanOutPenalty(int dependentCount) {
        return Math.min(100, Math.toIntExact(Math.round(
                dependentCount * 100.0 / FAN_OUT_REVIEW_LIMIT)));
    }

    public record Entry(
            ResourceLocation blueprintId,
            String state,
            Optional<Integer> mechanicalScore,
            Optional<Integer> assignedRank,
            Optional<ResourceLocation> bandId,
            List<ParentChoice> parentChoices,
            Optional<Integer> similarityScore,
            int fanOutPenalty,
            String parentChoiceReason,
            String mergeReason,
            List<String> reviewFallbackReasons,
            Optional<AutomaticWeaponPrerequisiteDecision> prerequisiteDecision) {
        /** Compatibility constructor for authoring fixtures predating Phase 6 provenance. */
        public Entry(
                ResourceLocation blueprintId,
                String state,
                Optional<Integer> mechanicalScore,
                Optional<Integer> assignedRank,
                Optional<ResourceLocation> bandId,
                List<ParentChoice> parentChoices,
                Optional<Integer> similarityScore,
                int fanOutPenalty,
                String parentChoiceReason,
                String mergeReason,
                List<String> reviewFallbackReasons) {
            this(
                    blueprintId,
                    state,
                    mechanicalScore,
                    assignedRank,
                    bandId,
                    parentChoices,
                    similarityScore,
                    fanOutPenalty,
                    parentChoiceReason,
                    mergeReason,
                    reviewFallbackReasons,
                    Optional.empty());
        }

        public Entry {
            mechanicalScore = mechanicalScore == null ? Optional.empty() : mechanicalScore;
            assignedRank = assignedRank == null ? Optional.empty() : assignedRank;
            bandId = bandId == null ? Optional.empty() : bandId;
            parentChoices = parentChoices == null ? List.of() : List.copyOf(parentChoices);
            similarityScore = similarityScore == null ? Optional.empty() : similarityScore;
            reviewFallbackReasons = reviewFallbackReasons == null
                    ? List.of() : List.copyOf(reviewFallbackReasons);
            prerequisiteDecision = prerequisiteDecision == null
                    ? Optional.empty() : prerequisiteDecision;
            if (blueprintId == null || !validText(state)
                    || mechanicalScore.filter(value -> value < 0 || value > 100).isPresent()
                    || assignedRank.filter(value -> value < 0
                            || value > ResearchTechTreeContract.MAX_PROGRESSION_RANK).isPresent()
                    || parentChoices.stream().anyMatch(java.util.Objects::isNull)
                    || parentChoices.stream().map(ParentChoice::parentId).distinct().count()
                            != parentChoices.size()
                    || similarityScore.filter(value -> value < 0 || value > 100).isPresent()
                    || fanOutPenalty < 0 || fanOutPenalty > 100
                    || !validText(parentChoiceReason) || !validText(mergeReason)
                    || prerequisiteDecision.filter(value ->
                            !value.blueprintId().equals(blueprintId)).isPresent()
                    || reviewFallbackReasons.stream().anyMatch(value -> !validText(value))) {
                throw new IllegalArgumentException("Invalid Research Tech Tree authoring entry");
            }
        }
    }

    public record ParentChoice(
            ResourceLocation parentId,
            Optional<Integer> similarityScore,
            int dependentLoad,
            int fanOutPenalty,
            String relationship) {
        /** Compatibility constructor for authoring fixtures predating parent provenance. */
        public ParentChoice(
                ResourceLocation parentId,
                Optional<Integer> similarityScore,
                int dependentLoad,
                int fanOutPenalty) {
            this(parentId, similarityScore, dependentLoad, fanOutPenalty, "unclassified");
        }

        public ParentChoice {
            similarityScore = similarityScore == null ? Optional.empty() : similarityScore;
            if (parentId == null
                    || similarityScore.filter(value -> value < 0 || value > 100).isPresent()
                    || dependentLoad < 0 || fanOutPenalty < 0 || fanOutPenalty > 100
                    || !validText(relationship)) {
                throw new IllegalArgumentException("Invalid Research Tech Tree parent choice");
            }
        }
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
