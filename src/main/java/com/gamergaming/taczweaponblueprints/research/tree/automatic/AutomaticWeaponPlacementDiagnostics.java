package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.LayeringStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.MergeIntervalBehavior;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.ReviewHandling;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, operator-facing explanation of one revision-matched automatic
 * placement publication. It is diagnostic only and never participates in
 * placement or research authority.
 */
public record AutomaticWeaponPlacementDiagnostics(
        ResourceLocation profileId,
        ResourceLocation treeId,
        AutomaticPlacementMode mode,
        ReviewHandling reviewHandling,
        LayeringStrategy layeringStrategy,
        PrerequisiteStrategy prerequisiteStrategy,
        int maxGeneratedPrerequisites,
        int mergeInterval,
        long catalogRevision,
        long researchRevision,
        int catalogWeaponCount,
        int topologyWeaponCount,
        int resolvedNodesPerLayer,
        PublicationSummary publicationSummary,
        Map<ResourceLocation, Entry> entries) {
    /** Compatibility constructor for diagnostics predating strategy identity. */
    public AutomaticWeaponPlacementDiagnostics(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            ReviewHandling reviewHandling,
            LayeringStrategy layeringStrategy,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            long catalogRevision,
            long researchRevision,
            int catalogWeaponCount,
            int topologyWeaponCount,
            int resolvedNodesPerLayer,
            PublicationSummary publicationSummary,
            Map<ResourceLocation, Entry> entries) {
        this(
                profileId,
                treeId,
                mode,
                reviewHandling,
                layeringStrategy,
                PrerequisiteStrategy.LEGACY_AND,
                maxGeneratedPrerequisites,
                mergeInterval,
                catalogRevision,
                researchRevision,
                catalogWeaponCount,
                topologyWeaponCount,
                resolvedNodesPerLayer,
                publicationSummary,
                entries);
    }

    public AutomaticWeaponPlacementDiagnostics {
        if (profileId == null || treeId == null || mode == null || reviewHandling == null
                || layeringStrategy == null || prerequisiteStrategy == null
                || maxGeneratedPrerequisites < 1
                || maxGeneratedPrerequisites
                        > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                || mergeInterval < 0
                || mergeInterval > AutomaticWeaponPlacementPolicy.MAX_MERGE_INTERVAL
                || catalogRevision < 0L || researchRevision < 0L
                || catalogWeaponCount < 0
                || catalogWeaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || topologyWeaponCount < 0 || topologyWeaponCount > catalogWeaponCount
                || resolvedNodesPerLayer
                        < AutomaticWeaponPlacementPolicy.MIN_MAX_NODES_PER_RANK
                || resolvedNodesPerLayer
                        > AutomaticWeaponPlacementPolicy.MAX_MAX_NODES_PER_RANK
                || publicationSummary == null
                || entries == null) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostics are invalid");
        }
        int strategyMaximumPrerequisites = switch (prerequisiteStrategy) {
            case LEGACY_AND -> AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES;
            case GROUPED_ROUTES_V1 -> 2;
            case HYBRID_ROUTES_V1 ->
                    AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES;
        };
        if (maxGeneratedPrerequisites > strategyMaximumPrerequisites) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostics exceed the strategy prerequisite limit");
        }
        LinkedHashMap<ResourceLocation, Entry> copy = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || !entry.getKey().equals(entry.getValue().blueprintId())) {
                        throw new IllegalArgumentException(
                                "Automatic placement diagnostic entry is inconsistent");
                    }
                    copy.put(entry.getKey(), entry.getValue());
                });
        if (copy.size() != catalogWeaponCount) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostic partition is incomplete");
        }
        if (copy.values().stream().anyMatch(entry ->
                entry.generatedPrerequisites().size() > maxGeneratedPrerequisites
                        || !requirementsMatchStrategy(
                                prerequisiteStrategy,
                                entry.generatedPrerequisites(),
                                entry.generatedRequirements())
                        || prerequisiteStrategy == PrerequisiteStrategy.HYBRID_ROUTES_V1
                                && !requirementsMatchDecisionShape(entry))) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostics contain invalid prerequisite groups");
        }
        long actualTopologyWeaponCount = copy.values().stream()
                .filter(entry -> entry.state() == State.AUTHORED
                        || entry.state() == State.AUTOMATIC)
                .count();
        if (actualTopologyWeaponCount != topologyWeaponCount) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostic topology population is inconsistent");
        }
        long actualAutomaticCandidateCount = copy.values().stream()
                .filter(entry -> entry.state() == State.AUTOMATIC)
                .count();
        if (actualAutomaticCandidateCount != publicationSummary.candidateCount()) {
            throw new IllegalArgumentException(
                    "Automatic placement publication summary is inconsistent");
        }
        entries = Collections.unmodifiableMap(copy);
    }

    /** Compatibility constructor for fixtures predating dynamic layer widths. */
    public AutomaticWeaponPlacementDiagnostics(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            ReviewHandling reviewHandling,
            LayeringStrategy layeringStrategy,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            long catalogRevision,
            long researchRevision,
            int catalogWeaponCount,
            Map<ResourceLocation, Entry> entries) {
        this(
                profileId,
                treeId,
                mode,
                reviewHandling,
                layeringStrategy,
                maxGeneratedPrerequisites,
                mergeInterval,
                catalogRevision,
                researchRevision,
                catalogWeaponCount,
                topologyWeaponCount(entries),
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                compatibilityPublicationSummary(mode, entries),
                entries);
    }

    /** Compatibility constructor for direct fixtures predating Phase 9 policy evidence. */
    public AutomaticWeaponPlacementDiagnostics(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            ReviewHandling reviewHandling,
            long catalogRevision,
            long researchRevision,
            int catalogWeaponCount,
            Map<ResourceLocation, Entry> entries) {
        this(
                profileId,
                treeId,
                mode,
                reviewHandling,
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_GENERATED_PREREQUISITES,
                AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL,
                catalogRevision,
                researchRevision,
                catalogWeaponCount,
                topologyWeaponCount(entries),
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                compatibilityPublicationSummary(mode, entries),
                entries);
    }

    /** Existing direct diagnostic fixtures retain the legacy exclusion policy. */
    public AutomaticWeaponPlacementDiagnostics(
            ResourceLocation profileId,
            ResourceLocation treeId,
            AutomaticPlacementMode mode,
            long catalogRevision,
            long researchRevision,
            int catalogWeaponCount,
            Map<ResourceLocation, Entry> entries) {
        this(
                profileId,
                treeId,
                mode,
                ReviewHandling.EXCLUDE,
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_GENERATED_PREREQUISITES,
                AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL,
                catalogRevision,
                researchRevision,
                catalogWeaponCount,
                entries);
    }

    public static AutomaticWeaponPlacementDiagnostics create(
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponPrerequisitePlan prerequisitePlan) {
        if (profileId == null || candidates == null || prerequisitePlan == null
                || !prerequisitePlan.matches(profileId, candidates)) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostic inputs are inconsistent");
        }
        LinkedHashMap<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        candidates.authoredBlueprintIds().forEach(value -> add(entries, new Entry(
                parseId(value), State.AUTHORED, Optional.empty(), List.of(), Optional.empty())));
        candidates.unplacedBlueprintIds().forEach(value -> add(entries, new Entry(
                parseId(value), State.UNPLACED, Optional.empty(), List.of(), Optional.empty())));
        candidates.excludedAutomaticCandidates().forEach((value, reason) -> add(entries, new Entry(
                parseId(value),
                State.EXCLUDED_AUTOMATIC,
                Optional.empty(),
                List.of(),
                Optional.of(reason))));
        candidates.eligibleProposals().forEach((value, proposal) -> {
            ResourceLocation id = parseId(value);
            List<ResourceLocation> prerequisites = prerequisitePlan.prerequisitesFor(id);
            Optional<String> omission = Optional.ofNullable(
                    prerequisitePlan.omittedCandidates().get(id));
            add(entries, new Entry(
                    id,
                    State.AUTOMATIC,
                    Optional.of(proposal),
                    prerequisites,
                    prerequisitePlan.requirementsFor(id),
                    omission,
                    prerequisitePlan.decisionFor(id)));
        });
        return new AutomaticWeaponPlacementDiagnostics(
                profileId,
                candidates.treeId(),
                candidates.mode(),
                candidates.policy().reviewHandling(),
                candidates.policy().layeringStrategy(),
                prerequisitePlan.prerequisiteStrategy(),
                candidates.policy().maxGeneratedPrerequisites(),
                candidates.policy().mergeInterval(),
                candidates.catalogRevision(),
                candidates.researchRevision(),
                candidates.catalogWeaponCount(),
                Math.addExact(
                        candidates.authoredBlueprintIds().size(),
                        candidates.eligibleProposals().size()),
                candidates.policy().maxNodesPerRank(),
                PublicationSummary.create(prerequisitePlan),
                entries);
    }

    public Optional<Entry> entry(ResourceLocation blueprintId) {
        return blueprintId == null
                ? Optional.empty()
                : Optional.ofNullable(entries.get(blueprintId));
    }

    public MergeIntervalBehavior mergeIntervalBehavior() {
        return AutomaticWeaponPlacementPolicy.mergeIntervalBehavior(
                layeringStrategy,
                prerequisiteStrategy,
                maxGeneratedPrerequisites,
                mergeInterval);
    }

    public String generatedParentCostGuard() {
        return switch (prerequisiteStrategy) {
            case LEGACY_AND ->
                    AutomaticWeaponPlacementPolicy.LEGACY_GENERATED_PARENT_COST_GUARD;
            case GROUPED_ROUTES_V1 ->
                    AutomaticWeaponPlacementPolicy.GROUPED_GENERATED_PARENT_COST_GUARD;
            case HYBRID_ROUTES_V1 ->
                    AutomaticWeaponPlacementPolicy.HYBRID_GENERATED_PARENT_COST_GUARD;
        };
    }

    public long count(State state) {
        if (state == null) {
            return 0L;
        }
        return entries.values().stream().filter(entry -> entry.state() == state).count();
    }

    public int generatedPrerequisiteCount() {
        return entries.values().stream()
                .mapToInt(entry -> entry.generatedPrerequisites().size())
                .sum();
    }

    /** Number of authoritative generated AND-groups represented by this plan. */
    public int generatedRequirementGroupCount() {
        return entries.values().stream()
                .mapToInt(entry -> entry.generatedRequirements().allOf().size())
                .sum();
    }

    /** Number of generated groups that offer more than one valid route. */
    public int generatedAlternativeGroupCount() {
        return Math.toIntExact(entries.values().stream()
                .flatMap(entry -> entry.generatedRequirements().allOf().stream())
                .filter(group -> group.anyOf().size() > 1)
                .count());
    }

    /** Generated targets whose selected parents are all mandatory. */
    public int generatedMandatoryConvergenceCount() {
        return Math.toIntExact(entries.values().stream()
                .filter(entry -> entry.generatedPrerequisites().size() > 1)
                .flatMap(entry -> entry.prerequisiteDecision().stream())
                .filter(decision -> decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .MANDATORY_SINGLETONS)
                .count());
    }

    /** Generated targets that combine one OR route group with a mandatory gate. */
    public int generatedMixedRequirementCount() {
        return Math.toIntExact(entries.values().stream()
                .flatMap(entry -> entry.prerequisiteDecision().stream())
                .filter(decision -> decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES_WITH_MANDATORY_GATEWAY)
                .count());
    }

    /** Generated targets offering a pure alternative route pair. */
    public int generatedAlternativeRouteDecisionCount() {
        return Math.toIntExact(entries.values().stream()
                .filter(entry -> entry.generatedPrerequisites().size() > 1)
                .flatMap(entry -> entry.prerequisiteDecision().stream())
                .filter(decision -> decision.generatedRequirementShape()
                        == AutomaticWeaponPrerequisiteDecision.GeneratedRequirementShape
                                .ALTERNATIVE_ROUTES)
                .count());
    }

    public BranchTopologySummary branchTopologySummary() {
        List<AutomaticWeaponPrerequisiteDecision> decisions = entries.values().stream()
                .flatMap(entry -> entry.prerequisiteDecision().stream())
                .toList();
        if (decisions.isEmpty()) {
            return BranchTopologySummary.UNAVAILABLE;
        }
        int familyStart = decisions.get(0).familyStartIndex();
        int transitionEnd = decisions.get(0).transitionEndIndex();
        if (decisions.stream().anyMatch(value ->
                value.familyStartIndex() != familyStart
                        || value.transitionEndIndex() != transitionEnd)) {
            throw new IllegalStateException(
                    "Automatic prerequisite diagnostics mix branch boundaries");
        }
        Map<ResourceLocation, Integer> fanOut = new LinkedHashMap<>();
        decisions.forEach(decision -> decision.selectedParentRelations().keySet()
                .forEach(parent -> fanOut.merge(parent, 1, Math::addExact)));
        return new BranchTopologySummary(
                true,
                familyStart,
                transitionEnd,
                Math.max(1, decisions.stream()
                        .flatMap(value -> value.branchIndex().stream())
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(-1) + 1),
                countStrategy(decisions,
                        AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION),
                countStrategy(decisions,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SHARED_TRUNK),
                Math.addExact(
                        countStrategy(decisions, AutomaticWeaponPrerequisiteDecision.Strategy
                                .TRANSITION_CROSS_FAMILY),
                        countStrategy(decisions, AutomaticWeaponPrerequisiteDecision.Strategy
                                .TRANSITION_LOCAL)),
                countStrategy(decisions,
                        AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION),
                decisions.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::sameFamilyParentCount).sum(),
                decisions.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::crossFamilyParentCount).sum(),
                decisions.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::unclassifiedParentCount).sum(),
                Math.toIntExact(decisions.stream()
                        .filter(value -> value.selectedParentRelations().size() > 1)
                        .filter(value -> value.crossFamilyParentCount() == 0)
                        .count()),
                Math.toIntExact(decisions.stream()
                        .filter(value -> value.selectedParentRelations().size() > 1)
                        .filter(value -> value.crossFamilyParentCount() > 0)
                        .count()),
                Math.toIntExact(decisions.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision::depthShortcut).count()),
                Math.toIntExact(decisions.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision::terminalPeer).count()),
                Math.toIntExact(decisions.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision
                                ::mergeRejectedForClosureInflation)
                        .count()),
                Math.toIntExact(decisions.stream()
                        .filter(value -> value.alternativeRouteReview().isPresent())
                        .count()),
                Math.toIntExact(decisions.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision
                                ::alternativeRouteAccepted)
                        .count()),
                Math.toIntExact(decisions.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision
                                ::alternativeRouteRejectedForCostImbalance)
                        .count()),
                fanOut.values().stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private static int countStrategy(
            List<AutomaticWeaponPrerequisiteDecision> decisions,
            AutomaticWeaponPrerequisiteDecision.Strategy strategy) {
        return Math.toIntExact(decisions.stream()
                .filter(value -> value.strategy() == strategy).count());
    }

    private static int topologyWeaponCount(Map<ResourceLocation, Entry> entries) {
        if (entries == null) {
            return 0;
        }
        return Math.toIntExact(entries.values().stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> entry.state() == State.AUTHORED
                        || entry.state() == State.AUTOMATIC)
                .count());
    }

    public long excludedAutomaticCount() {
        return count(State.EXCLUDED_AUTOMATIC) + count(State.EXCLUDED_FALLBACK);
    }

    private static void add(Map<ResourceLocation, Entry> entries, Entry entry) {
        if (entries.put(entry.blueprintId(), entry) != null) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostic categories overlap at "
                            + entry.blueprintId());
        }
    }

    private static ResourceLocation parseId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Automatic placement diagnostic ID is invalid");
        }
        return id;
    }

    private static boolean requirementsMatchStrategy(
            PrerequisiteStrategy strategy,
            List<ResourceLocation> prerequisites,
            ResearchRequirements requirements) {
        if (!Set.copyOf(prerequisites).equals(Set.copyOf(
                requirements.conservativeAlternatives()))) {
            return false;
        }
        return switch (strategy) {
            case LEGACY_AND -> requirements.legacySingletons().isPresent();
            case GROUPED_ROUTES_V1 -> prerequisites.isEmpty()
                    ? requirements.allOf().isEmpty()
                    : prerequisites.size() <= 2
                            && requirements.allOf().size() == 1;
            case HYBRID_ROUTES_V1 -> prerequisites.isEmpty()
                    ? requirements.allOf().isEmpty()
                    : prerequisites.size() <= 3
                            && requirements.allOf().size() <= 2
                            && requirements.allOf().stream()
                                    .allMatch(group -> group.anyOf().size() <= 2);
        };
    }

    private static boolean requirementsMatchDecisionShape(Entry entry) {
        if (entry.generatedPrerequisites().isEmpty()) {
            return true;
        }
        AutomaticWeaponPrerequisiteDecision decision =
                entry.prerequisiteDecision().orElse(null);
        if (decision == null) {
            return false;
        }
        List<ResourceLocation> parents = entry.generatedPrerequisites();
        ResearchRequirements expected = switch (decision.generatedRequirementShape()) {
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
        return expected.equals(entry.generatedRequirements());
    }

    public enum State {
        AUTHORED("authored"),
        AUTOMATIC("automatic"),
        EXCLUDED_AUTOMATIC("excluded_automatic"),
        /** Compatibility value for diagnostics constructed before candidate expansion. */
        @Deprecated(forRemoval = false)
        EXCLUDED_FALLBACK("excluded_fallback"),
        UNPLACED("unplaced");

        private final String serializedName;

        State(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public record Entry(
            ResourceLocation blueprintId,
            State state,
            Optional<AutomaticWeaponPlacementProposal> proposal,
            List<ResourceLocation> generatedPrerequisites,
            ResearchRequirements generatedRequirements,
            Optional<String> reason,
            Optional<AutomaticWeaponPrerequisiteDecision> prerequisiteDecision) {
        /** Compatibility constructor for diagnostics predating canonical groups. */
        public Entry(
                ResourceLocation blueprintId,
                State state,
                Optional<AutomaticWeaponPlacementProposal> proposal,
                List<ResourceLocation> generatedPrerequisites,
                Optional<String> reason,
                Optional<AutomaticWeaponPrerequisiteDecision> prerequisiteDecision) {
            this(
                    blueprintId,
                    state,
                    proposal,
                    generatedPrerequisites,
                    ResearchRequirements.fromLegacy(
                            generatedPrerequisites == null
                                    ? List.of()
                                    : generatedPrerequisites),
                    reason,
                    prerequisiteDecision);
        }

        public Entry(
                ResourceLocation blueprintId,
                State state,
                Optional<AutomaticWeaponPlacementProposal> proposal,
                List<ResourceLocation> generatedPrerequisites,
                Optional<String> reason) {
            this(
                    blueprintId,
                    state,
                    proposal,
                    generatedPrerequisites,
                    reason,
                    Optional.empty());
        }

        public Entry(
                ResourceLocation blueprintId,
                State state,
                Optional<AutomaticWeaponPlacementProposal> proposal,
                Optional<ResourceLocation> generatedPrerequisite,
                Optional<String> reason) {
            this(
                    blueprintId,
                    state,
                    proposal,
                    generatedPrerequisite == null
                            ? List.of()
                            : generatedPrerequisite.stream().toList(),
                    reason,
                    Optional.empty());
        }

        public Entry {
            proposal = proposal == null ? Optional.empty() : proposal;
            generatedPrerequisites = generatedPrerequisites == null
                    ? List.of()
                    : List.copyOf(generatedPrerequisites);
            reason = reason == null ? Optional.empty() : reason;
            prerequisiteDecision = prerequisiteDecision == null
                    ? Optional.empty() : prerequisiteDecision;
            Set<ResourceLocation> selectedPrerequisites = Set.copyOf(
                    generatedPrerequisites);
            if (blueprintId == null || state == null || generatedRequirements == null
                    || proposal.filter(value -> !value.blueprintId().equals(
                            blueprintId.toString())).isPresent()
                    || reason.filter(String::isBlank).isPresent()
                    || generatedPrerequisites.stream().anyMatch(value -> value == null)
                    || generatedPrerequisites.stream().distinct().count()
                            != generatedPrerequisites.size()
                    || generatedPrerequisites.size()
                            > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                    || !selectedPrerequisites.equals(Set.copyOf(
                            generatedRequirements.conservativeAlternatives()))
                    || (state == State.AUTOMATIC) != proposal.isPresent()
                    || (state != State.AUTOMATIC
                            && !generatedPrerequisites.isEmpty())
                    || prerequisiteDecision.filter(value ->
                            state != State.AUTOMATIC
                                    || !value.blueprintId().equals(blueprintId)
                                    || !value.selectedParentRelations().keySet().equals(
                                            selectedPrerequisites)).isPresent()
                    || (state == State.AUTOMATIC
                            && !generatedPrerequisites.isEmpty() == reason.isPresent())
                    || (state != State.AUTOMATIC
                            && ((state == State.EXCLUDED_AUTOMATIC
                                    || state == State.EXCLUDED_FALLBACK)
                                    != reason.isPresent()))) {
                throw new IllegalArgumentException(
                        "Automatic placement diagnostic decision is invalid");
            }
        }

        /** Compatibility view for integrations that only display one anchor. */
        public Optional<ResourceLocation> generatedPrerequisite() {
            return generatedPrerequisites.stream().findFirst();
        }
    }

    public record BranchTopologySummary(
            boolean available,
            int familyStartIndex,
            int transitionEndIndex,
            int branchCount,
            int foundationNodeCount,
            int sharedTrunkNodeCount,
            int transitionNodeCount,
            int specializationNodeCount,
            int sameFamilyEdgeCount,
            int crossFamilyEdgeCount,
            int unclassifiedEdgeCount,
            int sameFamilyMergeCount,
            int crossFamilyMergeCount,
            int depthShortcutCount,
            int terminalPeerCount,
            int closureInflationRejectionCount,
            int alternativeRouteReviewCount,
            int acceptedAlternativeRouteCount,
            int rejectedAlternativeRouteCostImbalanceCount,
            int maximumFanOut) {
        public static final BranchTopologySummary UNAVAILABLE =
                new BranchTopologySummary(
                        false, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0);

        public BranchTopologySummary {
            if (available ? familyStartIndex < 0 || transitionEndIndex < familyStartIndex
                    : familyStartIndex != -1 || transitionEndIndex != -1
                            || branchCount != 0 || foundationNodeCount != 0
                            || sharedTrunkNodeCount != 0 || transitionNodeCount != 0
                            || specializationNodeCount != 0 || sameFamilyEdgeCount != 0
                            || crossFamilyEdgeCount != 0 || unclassifiedEdgeCount != 0
                            || sameFamilyMergeCount != 0 || crossFamilyMergeCount != 0
                            || depthShortcutCount != 0 || terminalPeerCount != 0
                            || closureInflationRejectionCount != 0
                            || alternativeRouteReviewCount != 0
                            || acceptedAlternativeRouteCount != 0
                            || rejectedAlternativeRouteCostImbalanceCount != 0
                            || maximumFanOut != 0) {
                throw new IllegalArgumentException(
                        "Automatic branch topology summary is invalid");
            }
            if (available && (branchCount < 1 || foundationNodeCount < 0
                    || sharedTrunkNodeCount < 0 || transitionNodeCount < 0
                    || specializationNodeCount < 0 || sameFamilyEdgeCount < 0
                    || crossFamilyEdgeCount < 0 || unclassifiedEdgeCount < 0
                    || sameFamilyMergeCount < 0 || crossFamilyMergeCount < 0
                    || depthShortcutCount < 0 || terminalPeerCount < 0
                    || closureInflationRejectionCount < 0
                    || alternativeRouteReviewCount < 0
                    || acceptedAlternativeRouteCount < 0
                    || rejectedAlternativeRouteCostImbalanceCount < 0
                    || acceptedAlternativeRouteCount
                            + rejectedAlternativeRouteCostImbalanceCount
                            != alternativeRouteReviewCount
                    || maximumFanOut < 0)) {
                throw new IllegalArgumentException(
                        "Automatic branch topology counts are invalid");
            }
        }
    }

    /**
     * Aggregate proof that a connected automatic plan reached its final public
     * coordinates without losing canonical branch or finalized-rank evidence.
     */
    public record PublicationSummary(
            boolean applicable,
            int candidateCount,
            int canonicalBranchCoordinateCount,
            int prerequisiteDecisionCount,
            int publishedRankCount,
            int unexpectedParentlessCandidateCount) {
        /** Compatibility constructor for summaries predating connectivity proof. */
        public PublicationSummary(
                boolean applicable,
                int candidateCount,
                int canonicalBranchCoordinateCount,
                int prerequisiteDecisionCount,
                int publishedRankCount) {
            this(
                    applicable,
                    candidateCount,
                    canonicalBranchCoordinateCount,
                    prerequisiteDecisionCount,
                    publishedRankCount,
                    0);
        }

        public PublicationSummary {
            if (candidateCount < 0
                    || candidateCount
                            > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                    || canonicalBranchCoordinateCount < 0
                    || canonicalBranchCoordinateCount > candidateCount
                    || prerequisiteDecisionCount < 0
                    || prerequisiteDecisionCount > candidateCount
                    || publishedRankCount < 0
                    || publishedRankCount > prerequisiteDecisionCount
                    || unexpectedParentlessCandidateCount < 0
                    || unexpectedParentlessCandidateCount > candidateCount
                    || (!applicable && (canonicalBranchCoordinateCount != 0
                            || prerequisiteDecisionCount != 0
                            || publishedRankCount != 0
                            || unexpectedParentlessCandidateCount != 0))) {
                throw new IllegalArgumentException(
                        "Automatic publication summary is invalid");
            }
        }

        private static PublicationSummary create(
                AutomaticWeaponPrerequisitePlan plan) {
            boolean applicable = plan.mode().createsPrerequisite();
            return new PublicationSummary(
                    applicable,
                    plan.candidateCount(),
                    applicable ? plan.branchCoordinates().size() : 0,
                    applicable ? plan.decisions().size() : 0,
                    applicable
                            ? Math.toIntExact(plan.decisions().values().stream()
                                    .filter(value -> value.publishedRank().isPresent())
                                    .count())
                            : 0,
                    applicable ? countUnexpectedParentlessCandidates(plan) : 0);
        }

        public boolean canonicalBranchCoordinatesAvailable() {
            return applicable && canonicalBranchCoordinateCount > 0;
        }

        public boolean canonicalBranchCoordinatesComplete() {
            return !applicable
                    || canonicalBranchCoordinateCount == candidateCount;
        }

        public boolean rankReconciliationComplete() {
            return !applicable
                    || prerequisiteDecisionCount == candidateCount
                            && publishedRankCount == prerequisiteDecisionCount;
        }

        public boolean connectedTopologyComplete() {
            return !applicable || unexpectedParentlessCandidateCount == 0;
        }

        public boolean complete() {
            return canonicalBranchCoordinatesComplete()
                    && rankReconciliationComplete()
                    && connectedTopologyComplete();
        }

        public int canonicalBranchCoverageBasisPoints() {
            return coverageBasisPoints(canonicalBranchCoordinateCount, candidateCount);
        }

        public int publishedRankCoverageBasisPoints() {
            return coverageBasisPoints(publishedRankCount, candidateCount);
        }

        private static int coverageBasisPoints(int covered, int total) {
            return total == 0
                    ? 10_000
                    : Math.floorDiv(Math.multiplyExact(covered, 10_000), total);
        }
    }

    private static PublicationSummary compatibilityPublicationSummary(
            AutomaticPlacementMode mode,
            Map<ResourceLocation, Entry> entries) {
        if (entries == null) {
            return new PublicationSummary(false, 0, 0, 0, 0, 0);
        }
        int candidates = Math.toIntExact(entries.values().stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> entry.state() == State.AUTOMATIC)
                .count());
        boolean applicable = mode != null && mode.createsPrerequisite();
        int decisions = applicable
                ? Math.toIntExact(entries.values().stream()
                        .filter(java.util.Objects::nonNull)
                        .flatMap(entry -> entry.prerequisiteDecision().stream())
                        .count())
                : 0;
        int published = applicable
                ? Math.toIntExact(entries.values().stream()
                        .filter(java.util.Objects::nonNull)
                        .flatMap(entry -> entry.prerequisiteDecision().stream())
                        .filter(value -> value.publishedRank().isPresent())
                        .count())
                : 0;
        return new PublicationSummary(
                applicable, candidates, 0, decisions, published, 0);
    }

    private static int countUnexpectedParentlessCandidates(
            AutomaticWeaponPrerequisitePlan plan) {
        return Math.toIntExact(plan.omittedCandidates().entrySet().stream()
                .filter(entry -> {
                    AutomaticWeaponPrerequisitePlan.BranchCoordinate coordinate =
                            plan.branchCoordinates().get(entry.getKey());
                    return coordinate != null && coordinate.rankIndex() > 0;
                })
                .filter(entry -> !intentionallyIndependent(entry.getValue()))
                .count());
    }

    private static boolean intentionallyIndependent(String omissionReason) {
        return "authored_prerequisites".equals(omissionReason)
                || "entry_point".equals(omissionReason)
                || "review_policy_independent".equals(omissionReason);
    }
}
