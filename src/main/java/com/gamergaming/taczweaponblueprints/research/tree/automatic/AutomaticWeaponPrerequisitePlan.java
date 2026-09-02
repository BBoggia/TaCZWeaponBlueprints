package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable profile-specific authority proposal for connected automatic
 * placements. Every eligible candidate is either connected to one bounded set
 * of prerequisites or records a fail-open omission reason.
 */
public record AutomaticWeaponPrerequisitePlan(
        ResourceLocation profileId,
        ResourceLocation treeId,
        AutomaticPlacementMode mode,
        PrerequisiteStrategy prerequisiteStrategy,
        long catalogRevision,
        long researchRevision,
        int candidateCount,
        Map<ResourceLocation, List<ResourceLocation>> prerequisites,
        Map<ResourceLocation, ResearchRequirements> requirementGroups,
        Map<ResourceLocation, String> omittedCandidates,
        Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
        Map<ResourceLocation, BranchCoordinate> branchCoordinates) {
    /** Compatibility constructor for canonical plans predating strategy identity. */
    public AutomaticWeaponPrerequisitePlan(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            long catalogRevision,
            long researchRevision,
            int candidateCount,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, ResearchRequirements> requirementGroups,
            Map<ResourceLocation, String> omittedCandidates,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            Map<ResourceLocation, BranchCoordinate> branchCoordinates) {
        this(
                profileId,
                treeId,
                mode,
                PrerequisiteStrategy.LEGACY_AND,
                catalogRevision,
                researchRevision,
                candidateCount,
                prerequisites,
                requirementGroups,
                omittedCandidates,
                decisions,
                branchCoordinates);
    }

    /** Compatibility constructor for flat generated prerequisite plans. */
    public AutomaticWeaponPrerequisitePlan(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            long catalogRevision,
            long researchRevision,
            int candidateCount,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, String> omittedCandidates,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            Map<ResourceLocation, BranchCoordinate> branchCoordinates) {
        this(
                profileId,
                treeId,
                mode,
                PrerequisiteStrategy.LEGACY_AND,
                catalogRevision,
                researchRevision,
                candidateCount,
                prerequisites,
                singletonRequirements(prerequisites),
                omittedCandidates,
                decisions,
                branchCoordinates);
    }

    /** Compatibility constructor for plans created before canonical branch publication. */
    public AutomaticWeaponPrerequisitePlan(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            long catalogRevision,
            long researchRevision,
            int candidateCount,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, String> omittedCandidates,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions) {
        this(
                profileId,
                treeId,
                mode,
                catalogRevision,
                researchRevision,
                candidateCount,
                prerequisites,
                omittedCandidates,
                decisions,
                Map.of());
    }

    /** Compatibility constructor for plans created before Phase 6 provenance. */
    public AutomaticWeaponPrerequisitePlan(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            long catalogRevision,
            long researchRevision,
            int candidateCount,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, String> omittedCandidates) {
        this(
                profileId,
                treeId,
                mode,
                catalogRevision,
                researchRevision,
                candidateCount,
                prerequisites,
                omittedCandidates,
                Map.of(),
                Map.of());
    }

    public AutomaticWeaponPrerequisitePlan {
        if (profileId == null || treeId == null || mode == null
                || prerequisiteStrategy == null
                || catalogRevision < 0L || researchRevision < 0L
                || candidateCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || prerequisites == null || requirementGroups == null
                || omittedCandidates == null || decisions == null
                || branchCoordinates == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon prerequisite plan is invalid");
        }
        prerequisites = immutablePrerequisiteMap(prerequisites);
        requirementGroups = immutableMap(requirementGroups);
        omittedCandidates = immutableMap(omittedCandidates);
        decisions = immutableMap(decisions);
        branchCoordinates = immutableMap(branchCoordinates);
        if (!requirementsMatch(
                    prerequisites, requirementGroups, prerequisiteStrategy)
                || prerequisites.size() + omittedCandidates.size() != candidateCount
                || prerequisites.keySet().stream().anyMatch(omittedCandidates::containsKey)
                || (!mode.createsPrerequisite() && !prerequisites.isEmpty())
                || prerequisites.entrySet().stream().anyMatch(entry ->
                        entry.getValue().isEmpty()
                                || entry.getValue().size()
                                        > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                                || entry.getValue().contains(entry.getKey()))
                || omittedCandidates.values().stream().anyMatch(value ->
                        value == null || value.isBlank()
                                || !value.equals(value.trim()))
                || !decisionsValid(
                        prerequisites,
                        requirementGroups,
                        omittedCandidates,
                        decisions,
                        prerequisiteStrategy)
                || !branchCoordinatesValid(
                        prerequisites, omittedCandidates, decisions, branchCoordinates)) {
            throw new IllegalArgumentException(
                    "Automatic weapon prerequisite plan partition is inconsistent");
        }
    }

    public boolean matches(
            ResourceLocation profile,
            ResourceLocation tree,
            long catalog,
            long research) {
        return profileId.equals(profile)
                && treeId.equals(tree)
                && catalogRevision == catalog
                && researchRevision == research;
    }

    public boolean matches(
            ResourceLocation profile,
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        return candidates != null
                && matches(
                        profile,
                        candidates.treeId(),
                        candidates.catalogRevision(),
                        candidates.researchRevision())
                && mode == candidates.mode()
                && prerequisiteStrategy == candidates.policy().prerequisiteStrategy()
                && candidateCount == candidates.eligibleProposals().size();
    }

    public Optional<ResourceLocation> prerequisiteFor(ResourceLocation blueprintId) {
        return prerequisitesFor(blueprintId).stream().findFirst();
    }

    public List<ResourceLocation> prerequisitesFor(ResourceLocation blueprintId) {
        return blueprintId == null
                ? List.of()
                : prerequisites.getOrDefault(blueprintId, List.of());
    }

    /** Canonical generated requirements for the plan's versioned strategy. */
    public ResearchRequirements requirementsFor(ResourceLocation blueprintId) {
        return blueprintId == null
                ? ResearchRequirements.EMPTY
                : requirementGroups.getOrDefault(
                        blueprintId, ResearchRequirements.EMPTY);
    }

    public Optional<AutomaticWeaponPrerequisiteDecision> decisionFor(
            ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(decisions.get(blueprintId));
    }

    public Optional<BranchCoordinate> branchCoordinateFor(
            ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(branchCoordinates.get(blueprintId));
    }

    /** Reconciles planned rank ordinals with the coordinates actually published. */
    public AutomaticWeaponPrerequisitePlan withPublishedRanks(
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        if (!matches(profileId, candidates)) {
            throw new IllegalArgumentException(
                    "Published automatic ranks do not match their prerequisite plan");
        }
        Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> reconciled =
                new LinkedHashMap<>();
        decisions.forEach((blueprintId, decision) -> {
            int rank = candidates.eligibleProposal(blueprintId).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Published automatic ranks omit a prerequisite decision"))
                    .progressionCoordinate().rank();
            reconciled.put(blueprintId, decision.withPublishedRank(rank));
        });
        return new AutomaticWeaponPrerequisitePlan(
                profileId,
                treeId,
                mode,
                prerequisiteStrategy,
                catalogRevision,
                researchRevision,
                candidateCount,
                prerequisites,
                requirementGroups,
                omittedCandidates,
                reconciled,
                branchCoordinates);
    }

    /** Canonical planned branch/rank coordinates, independent of parent outcome. */
    public record BranchCoordinate(
            int branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex) {
        public BranchCoordinate {
            if (branchIndex < 0
                    || branchIndex >= AutomaticWeaponBranchAnalyzer.MAX_BRANCHES
                    || rankIndex < 0
                    || rankIndex > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    || familyStartIndex < 0
                    || familyStartIndex > transitionEndIndex
                    || transitionEndIndex
                            > ResearchTechTreeContract.MAX_PROGRESSION_RANK) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite branch coordinate is invalid");
            }
        }
    }

    private static boolean decisionsValid(
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, ResearchRequirements> requirementGroups,
            Map<ResourceLocation, String> omittedCandidates,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            PrerequisiteStrategy strategy) {
        for (Map.Entry<ResourceLocation, AutomaticWeaponPrerequisiteDecision> entry
                : decisions.entrySet()) {
            ResourceLocation blueprintId = entry.getKey();
            AutomaticWeaponPrerequisiteDecision decision = entry.getValue();
            if (!blueprintId.equals(decision.blueprintId())
                    || !prerequisites.containsKey(blueprintId)
                            && !omittedCandidates.containsKey(blueprintId)
                    || prerequisites.containsKey(blueprintId)
                            && !Set.copyOf(prerequisites.get(blueprintId)).equals(
                                    decision.selectedParentRelations().keySet())
                    || strategy == PrerequisiteStrategy.HYBRID_ROUTES_V1
                            && prerequisites.containsKey(blueprintId)
                            && !requirementsForDecision(
                                    decision.generatedRequirementShape(),
                                    prerequisites.get(blueprintId)).equals(
                                            requirementGroups.get(blueprintId))
                    || omittedCandidates.containsKey(blueprintId)
                            && !decision.selectedParentRelations().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static ResearchRequirements requirementsForDecision(
            AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape shape,
            List<ResourceLocation> parents) {
        if (shape == null || parents == null || parents.isEmpty()
                || parents.size() > 3) {
            return ResearchRequirements.EMPTY;
        }
        return switch (shape) {
            case MANDATORY_SINGLETONS -> ResearchRequirements.fromLegacy(parents);
            case ALTERNATIVE_ROUTES -> new ResearchRequirements(List.of(
                    new ResearchPrerequisiteGroup(parents)));
            case ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY ->
                    parents.size() == 3
                            ? new ResearchRequirements(List.of(
                                    new ResearchPrerequisiteGroup(
                                            parents.subList(0, 2)),
                                    ResearchPrerequisiteGroup.singleton(
                                            parents.get(2))))
                            : ResearchRequirements.EMPTY;
        };
    }

    private static boolean branchCoordinatesValid(
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, String> omittedCandidates,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            Map<ResourceLocation, BranchCoordinate> branchCoordinates) {
        if (!branchCoordinates.isEmpty()) {
            LinkedHashSet<ResourceLocation> candidates = new LinkedHashSet<>(
                    prerequisites.keySet());
            candidates.addAll(omittedCandidates.keySet());
            if (!branchCoordinates.keySet().equals(candidates)) {
                return false;
            }
        }
        return decisions.entrySet().stream().allMatch(entry -> {
            AutomaticWeaponPrerequisiteDecision decision = entry.getValue();
            BranchCoordinate coordinate = branchCoordinates.get(entry.getKey());
            return coordinate == null
                    || decision.branchIndex().isPresent()
                            && coordinate.branchIndex() == decision.branchIndex().orElseThrow()
                            && coordinate.rankIndex() == decision.rankIndex()
                            && coordinate.familyStartIndex()
                                    == decision.familyStartIndex()
                            && coordinate.transitionEndIndex()
                                    == decision.transitionEndIndex();
        });
    }

    private static Map<ResourceLocation, List<ResourceLocation>> immutablePrerequisiteMap(
            Map<ResourceLocation, List<ResourceLocation>> source) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || entry.getValue().stream().anyMatch(value -> value == null)) {
                        throw new IllegalArgumentException(
                                "Automatic prerequisite plan map contains null");
                    }
                    List<ResourceLocation> values = List.copyOf(entry.getValue());
                    if (values.stream().distinct().count() != values.size()) {
                        throw new IllegalArgumentException(
                                "Automatic prerequisite plan contains duplicate prerequisites");
                    }
                    copy.put(entry.getKey(), values);
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, ResearchRequirements> singletonRequirements(
            Map<ResourceLocation, List<ResourceLocation>> prerequisites) {
        if (prerequisites == null) {
            return Map.of();
        }
        LinkedHashMap<ResourceLocation, ResearchRequirements> requirements =
                new LinkedHashMap<>();
        prerequisites.forEach((id, values) -> requirements.put(
                id,
                ResearchRequirements.fromLegacy(
                        values == null ? List.of() : values)));
        return requirements;
    }

    private static boolean requirementsMatch(
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, ResearchRequirements> requirements,
            PrerequisiteStrategy strategy) {
        if (!requirements.keySet().equals(prerequisites.keySet())) {
            return false;
        }
        for (Map.Entry<ResourceLocation, ResearchRequirements> entry
                : requirements.entrySet()) {
            List<ResourceLocation> generated = prerequisites.get(entry.getKey());
            boolean valid = switch (strategy) {
                case LEGACY_AND -> entry.getValue().legacySingletons()
                        .filter(singletons -> Set.copyOf(singletons).equals(
                                Set.copyOf(generated)))
                        .isPresent();
                case GROUPED_ROUTES_V1 -> generated.size() <= 2
                        && entry.getValue().allOf().size() == 1
                        && Set.copyOf(entry.getValue().allOf().get(0).anyOf())
                                .equals(Set.copyOf(generated));
                case HYBRID_ROUTES_V1 -> generated.size() <= 3
                        && entry.getValue().allOf().size() <= 2
                        && entry.getValue().allOf().stream()
                                .allMatch(group -> group.anyOf().size() <= 2)
                        && entry.getValue().allOf().stream()
                                .filter(group -> group.anyOf().size() == 2)
                                .count() <= 1;
            };
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static <T> Map<ResourceLocation, T> immutableMap(
            Map<ResourceLocation, T> source) {
        LinkedHashMap<ResourceLocation, T> copy = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "Automatic prerequisite plan map contains null");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        return Collections.unmodifiableMap(copy);
    }
}
