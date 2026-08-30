package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidateClassification;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicyResolver;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreePlacementResolver;

import net.minecraft.resources.ResourceLocation;

/** Pure, deterministic connector for the explicitly enabled connected mode. */
public final class AutomaticWeaponPrerequisitePlanner {
    static final int BRANCH_TRANSITION_END_NUMERATOR = 3;
    static final int BRANCH_TRANSITION_END_DENOMINATOR = 4;
    static final int FULL_SECOND_PARENT_QUOTA_BASIS_POINTS = 10_000;
    static final int TERMINAL_SECOND_PARENT_QUOTA_BASIS_POINTS = 2_000;
    static final int FOUNDATION_SECOND_PARENT_QUOTA_BASIS_POINTS = 5_000;
    private static final Comparator<ProgressionPosition> POSITION_ORDER = Comparator
            .comparingInt((ProgressionPosition value) -> value.tier().ordinal())
            .thenComparingInt(ProgressionPosition::level)
            .thenComparingLong(ProgressionPosition::siblingOrder);
    private static final Comparator<ProgressionCoordinate> COORDINATE_ORDER = Comparator
            .comparingInt(ProgressionCoordinate::rank)
            .thenComparingLong(ProgressionCoordinate::siblingOrder);
    private final int maximumTotalPrerequisites;

    public AutomaticWeaponPrerequisitePlanner() {
        this(BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES);
    }

    AutomaticWeaponPrerequisitePlanner(int maximumTotalPrerequisites) {
        if (maximumTotalPrerequisites < 0
                || maximumTotalPrerequisites
                        > BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite edge budget is out of bounds");
        }
        this.maximumTotalPrerequisites = maximumTotalPrerequisites;
    }

    public AutomaticWeaponPrerequisitePlan plan(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates) {
        return planInternal(research, catalog, profileId, candidates, null);
    }

    /**
     * Plans current dynamic topology from the exact pre-topology classification
     * that produced the positioned snapshot.
     */
    public AutomaticWeaponPrerequisitePlan plan(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponCandidateClassification classification) {
        if (!classificationMatches(candidates, classification)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite branch evidence does not match its candidates");
        }
        return planInternal(
                research, catalog, profileId, candidates, classification);
    }

    private AutomaticWeaponPrerequisitePlan planInternal(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponCandidateClassification classification) {
        if (research == null || catalog == null || profileId == null
                || candidates == null) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite planner inputs cannot be null");
        }
        BlueprintResearchProfile profile = research.profiles().get(profileId);
        if (profile == null
                || profile.techTree().filter(candidates.treeId()::equals).isEmpty()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite profile does not select the candidate tree");
        }

        List<ResourceLocation> eligibleIds = candidates.eligibleProposals().keySet().stream()
                .map(ResourceLocation::tryParse)
                .peek(id -> {
                    if (id == null) {
                        throw new IllegalArgumentException(
                                "Automatic prerequisite candidate ID is invalid");
                    }
                })
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        Map<ResourceLocation, String> omitted = new LinkedHashMap<>();
        if (!candidates.mode().createsPrerequisite()) {
            eligibleIds.forEach(id -> omitted.put(
                    id, "mode_does_not_create_prerequisites"));
            return result(profileId, candidates, Map.of(), omitted);
        }

        Map<ResourceLocation, BlueprintResearchPolicy> policies = new LinkedHashMap<>();
        catalog.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> policies.put(id, BlueprintResearchPolicyResolver.resolve(
                        research, catalog, profileId, id, null, ignored -> false)));
        Map<ResourceLocation, List<ResourceLocation>> basePrerequisites =
                new LinkedHashMap<>();
        policies.forEach((id, policy) ->
                basePrerequisites.put(id, policy.prerequisites()));
        long authoredPrerequisiteCount = basePrerequisites.values().stream()
                .mapToLong(List::size)
                .sum();
        int remainingPrerequisiteBudget = authoredPrerequisiteCount
                        >= maximumTotalPrerequisites
                ? 0
                : Math.toIntExact(maximumTotalPrerequisites - authoredPrerequisiteCount);

        Map<ResourceLocation, PositionedWeapon> positioned = positionedWeapons(
                research, catalog, candidates, policies);
        if (candidates.policy().usesDynamicLayers()) {
            return planDynamicLayers(
                    profileId,
                    candidates,
                    eligibleIds,
                    policies,
                    basePrerequisites,
                    positioned,
                    remainingPrerequisiteBudget,
                    omitted,
                    classification);
        }
        AnchorIndex anchorIndex = AnchorIndex.create(
                positioned, policies, candidates);
        List<ResourceLocation> orderedTargets = eligibleIds.stream()
                .sorted(Comparator
                        .comparing((ResourceLocation id) -> positioned.get(id).position(),
                                POSITION_ORDER)
                        .thenComparing(ResourceLocation::toString))
                .toList();
        Map<ResourceLocation, List<ResourceLocation>> generated = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> prerequisiteDepths = new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> prerequisiteClosures =
                new LinkedHashMap<>();
        Map<Tier, Integer> connectedByTier = new java.util.EnumMap<>(Tier.class);
        for (ResourceLocation target : orderedTargets) {
            var proposal = candidates.eligibleProposals().get(target.toString());
            if (proposal == null) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite target has no placement proposal");
            }
            if (proposal.reviewRequired()
                    && !candidates.policy().reviewHandling().createsPrerequisite()) {
                omitted.put(target, "review_policy_independent");
                continue;
            }
            BlueprintResearchPolicy targetPolicy = policies.get(target);
            PositionedWeapon targetWeapon = positioned.get(target);
            if (targetPolicy == null || targetWeapon == null || !usable(targetPolicy)) {
                omitted.put(target, "target_not_selectable");
                continue;
            }
            if (!targetPolicy.prerequisites().isEmpty()) {
                omitted.put(target, "authored_prerequisites");
                continue;
            }

            List<ResourceLocation> anchors = anchorCandidates(
                    target, targetWeapon.position(), positioned, anchorIndex);
            int connectedInTier = connectedByTier.getOrDefault(
                    targetWeapon.position().tier(), 0);
            boolean tierGateway = targetWeapon.position().tier() != Tier.STARTER
                    && connectedInTier == 0;
            boolean periodicMerge = candidates.policy().mergeInterval() > 0
                    && (connectedInTier + 1) % candidates.policy().mergeInterval() == 0;
            int desiredCount = 1;
            if (tierGateway) {
                desiredCount = Math.min(
                        candidates.policy().maxGeneratedPrerequisites(), 2);
            } else if (periodicMerge) {
                desiredCount = candidates.policy().maxGeneratedPrerequisites();
            }
            if (remainingPrerequisiteBudget == 0) {
                omitted.put(target, "maximum_total_prerequisites");
                continue;
            }
            List<ResourceLocation> selected = selectDepthSafeAnchors(
                    target,
                    anchors,
                    Math.min(desiredCount, remainingPrerequisiteBudget),
                    basePrerequisites,
                    generated,
                    prerequisiteDepths,
                    prerequisiteClosures,
                    positioned,
                    policies,
                    false,
                    false,
                    false).selected();
            if (selected.isEmpty()) {
                omitted.put(target, anchors.isEmpty()
                        ? "no_earlier_anchor"
                        : "maximum_prerequisite_depth");
                continue;
            }
            generated.put(target, selected);
            remainingPrerequisiteBudget = Math.subtractExact(
                    remainingPrerequisiteBudget, selected.size());
            connectedByTier.put(targetWeapon.position().tier(), connectedInTier + 1);
        }
        validateGeneratedPositions(generated, positioned, false);
        return result(profileId, candidates, generated, omitted);
    }

    private static boolean classificationMatches(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            AutomaticWeaponCandidateClassification classification) {
        if (candidates == null || classification == null
                || !classification.treeId().equals(candidates.treeId())
                || classification.mode() != candidates.mode()
                || classification.catalogRevision() != candidates.catalogRevision()
                || classification.researchRevision() != candidates.researchRevision()
                || classification.catalogWeaponCount() != candidates.catalogWeaponCount()
                || !classification.eligibleProposals().keySet().equals(
                        candidates.eligibleProposals().keySet())
                || !classification.excludedAutomaticCandidates().equals(
                        candidates.excludedAutomaticCandidates())
                || !classification.authoredBlueprintIds().equals(
                        candidates.authoredBlueprintIds())
                || !classification.unplacedBlueprintIds().equals(
                        candidates.unplacedBlueprintIds())) {
            return false;
        }
        if (!classification.eligibleProposals().isEmpty()
                && classification.branchModel().branchLimit()
                        != AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                                candidates.policy().maxNodesPerRank())) {
            return false;
        }
        return classification.eligibleProposals().entrySet().stream().allMatch(entry -> {
            AutomaticWeaponPlacementProposal positioned =
                    candidates.eligibleProposals().get(entry.getKey());
            AutomaticWeaponPlacementProposal raw = entry.getValue();
            return positioned != null
                    && raw.blueprintId().equals(positioned.blueprintId())
                    && raw.mechanicalScore() == positioned.mechanicalScore()
                    && raw.confidence() == positioned.confidence()
                    && raw.position().equals(positioned.position())
                    && raw.progressionCoordinate().siblingOrder()
                            == positioned.progressionCoordinate().siblingOrder()
                    && raw.levelsPerTier() == positioned.levelsPerTier()
                    && raw.formulaVersion().equals(positioned.formulaVersion())
                    && raw.referenceVersion().equals(positioned.referenceVersion())
                    && raw.placementVersion().equals(positioned.placementVersion())
                    && raw.reviewReasons().equals(positioned.reviewReasons());
        });
    }

    private static AutomaticWeaponPrerequisitePlan result(
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, String> omitted) {
        return result(profileId, candidates, generated, omitted, Map.of());
    }

    private static AutomaticWeaponPrerequisitePlan result(
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, String> omitted,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions) {
        return result(
                profileId, candidates, generated, omitted, decisions, Map.of());
    }

    private static AutomaticWeaponPrerequisitePlan result(
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, String> omitted,
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            Map<ResourceLocation, AutomaticWeaponPrerequisitePlan.BranchCoordinate>
                    branchCoordinates) {
        return new AutomaticWeaponPrerequisitePlan(
                profileId,
                candidates.treeId(),
                candidates.mode(),
                candidates.catalogRevision(),
                candidates.researchRevision(),
                candidates.eligibleProposals().size(),
                generated,
                omitted,
                decisions,
                branchCoordinates);
    }

    /**
     * Connects bounded stat layers as a widening and narrowing mesh. Early
     * transitions prefer two adjacent parents so paths meet; later transitions
     * taper toward a sparse branch-local second-parent floor. In a mixed tree,
     * each automatic
     * foundation node first bridges to one authored node at the same or an earlier
     * provisional rank; presentation normalization then lifts the generated branch
     * into strict prerequisite order. Automatic-to-automatic edges always cross
     * ranks.
     */
    private static AutomaticWeaponPrerequisitePlan planDynamicLayers(
            ResourceLocation profileId,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            List<ResourceLocation> eligibleIds,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, PositionedWeapon> positioned,
            int remainingPrerequisiteBudget,
            Map<ResourceLocation, String> omitted,
            AutomaticWeaponCandidateClassification classification) {
        NavigableMap<Integer, List<ResourceLocation>> automaticByRank = new TreeMap<>();
        NavigableMap<Integer, List<ResourceLocation>> authoredByRank = new TreeMap<>();
        positioned.forEach((id, weapon) -> {
            if (!usable(policies.get(id))) {
                return;
            }
            if (weapon.authored()) {
                authoredByRank.computeIfAbsent(
                        weapon.coordinate().rank(), ignored -> new ArrayList<>()).add(id);
                return;
            }
            var proposal = candidates.eligibleProposal(id);
            boolean independentReview = proposal.filter(value -> value.reviewRequired()
                    && !candidates.policy().reviewHandling().createsPrerequisite()).isPresent();
            if (!independentReview) {
                automaticByRank.computeIfAbsent(
                        weapon.coordinate().rank(), ignored -> new ArrayList<>()).add(id);
            }
        });
        Comparator<ResourceLocation> coordinateOrder = Comparator
                .comparing((ResourceLocation id) -> positioned.get(id).coordinate(),
                        COORDINATE_ORDER)
                .thenComparing(ResourceLocation::toString);
        automaticByRank.replaceAll((rank, ids) -> ids.stream()
                .sorted(coordinateOrder).toList());
        authoredByRank.replaceAll((rank, ids) -> ids.stream()
                .sorted(coordinateOrder).toList());

        AutomaticWeaponBranchModel branchModel = classification == null
                ? null : classification.branchModel();
        BranchTopologyContext branchContext = branchModel == null
                        || branchModel.equals(AutomaticWeaponBranchModel.EMPTY)
                ? null
                : BranchTopologyContext.create(
                        branchModel,
                        classification.roleSignatures(),
                        classification.authoredRoleSignatures(),
                        automaticByRank,
                        authoredByRank,
                        positioned,
                        policies,
                        coordinateOrder);

        List<Integer> automaticRanks = candidates.eligibleProposals().values().stream()
                .map(value -> value.progressionCoordinate().rank())
                .distinct()
                .sorted()
                .toList();
        Map<Integer, Integer> rankIndexes = new LinkedHashMap<>();
        for (int index = 0; index < automaticRanks.size(); index++) {
            rankIndexes.put(automaticRanks.get(index), index);
        }
        int branchFamilyStart = branchContext == null
                ? 0
                : branchFamilyStartIndex(
                        candidates.eligibleProposals().size(),
                        candidates.policy(),
                        automaticRanks.size());
        int branchTransitionEnd = branchContext == null
                ? 0
                : branchTransitionEndIndex(
                        automaticRanks.size(), branchFamilyStart);
        Map<ResourceLocation, AutomaticWeaponPrerequisitePlan.BranchCoordinate>
                branchCoordinates = new LinkedHashMap<>();
        if (branchContext != null) {
            for (ResourceLocation target : eligibleIds) {
                int rankIndex = rankIndexes.get(candidates.eligibleProposal(target)
                        .orElseThrow().progressionCoordinate().rank());
                branchCoordinates.put(
                        target,
                        new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                branchContext.branchIndex(target),
                                rankIndex,
                                branchFamilyStart,
                                branchTransitionEnd));
            }
        }
        Set<ResourceLocation> scheduledSecondParents = branchContext == null
                || candidates.policy().maxGeneratedPrerequisites() < 2
                        ? Set.of()
                        : secondParentSchedule(
                                automaticByRank,
                                rankIndexes,
                                branchFamilyStart,
                                branchTransitionEnd,
                                policies,
                                branchContext);
        List<ResourceLocation> orderedTargets = eligibleIds.stream()
                .sorted(coordinateOrder)
                .toList();
        Map<ResourceLocation, List<ResourceLocation>> generated = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> prerequisiteDepths = new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> prerequisiteClosures =
                new LinkedHashMap<>();
        Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions =
                new LinkedHashMap<>();
        for (ResourceLocation target : orderedTargets) {
            var proposal = candidates.eligibleProposal(target).orElseThrow();
            BlueprintResearchPolicy targetPolicy = policies.get(target);
            PositionedWeapon targetWeapon = positioned.get(target);
            if (targetWeapon == null) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite target has no positioned weapon");
            }
            int targetRank = targetWeapon.coordinate().rank();
            List<ResourceLocation> currentLayer = automaticByRank.getOrDefault(
                    targetRank, List.of());
            int targetIndex = currentLayer.indexOf(target);
            int rankIndex = rankIndexes.getOrDefault(targetRank, 0);
            boolean foundationLayer = rankIndex == 0;
            ParentSelection selection = branchContext == null
                    ? legacyDynamicSelection(
                            target,
                            targetRank,
                            Math.max(0, targetIndex),
                            Math.max(1, currentLayer.size()),
                            rankIndex,
                            automaticRanks.size(),
                            foundationLayer,
                            candidates,
                            automaticByRank,
                            authoredByRank)
                    : branchAwareSelection(
                            target,
                            targetRank,
                            rankIndex,
                            automaticRanks.size(),
                            branchFamilyStart,
                            branchTransitionEnd,
                            foundationLayer,
                            candidates,
                            branchContext,
                            scheduledSecondParents.contains(target));
            if (proposal.reviewRequired()
                    && !candidates.policy().reviewHandling().createsPrerequisite()) {
                omitted.put(target, "review_policy_independent");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        AnchorSelection.EMPTY, branchContext);
                continue;
            }
            if (targetPolicy == null || !usable(targetPolicy)) {
                omitted.put(target, "target_not_selectable");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        AnchorSelection.EMPTY, branchContext);
                continue;
            }
            if (!targetPolicy.prerequisites().isEmpty()) {
                omitted.put(target, "authored_prerequisites");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        AnchorSelection.EMPTY, branchContext);
                continue;
            }
            List<ResourceLocation> anchors = selection.anchors();
            if (anchors.isEmpty()) {
                omitted.put(target, "generated_root");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        AnchorSelection.EMPTY, branchContext);
                continue;
            }
            if (remainingPrerequisiteBudget == 0) {
                omitted.put(target, "maximum_total_prerequisites");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        AnchorSelection.EMPTY, branchContext);
                continue;
            }

            AnchorSelection anchorSelection = selectDepthSafeAnchors(
                    target,
                    anchors,
                    Math.min(selection.desiredCount(), remainingPrerequisiteBudget),
                    basePrerequisites,
                    generated,
                    prerequisiteDepths,
                    prerequisiteClosures,
                    positioned,
                    policies,
                    true,
                    branchContext != null,
                    foundationLayer);
            List<ResourceLocation> selected = anchorSelection.selected();
            if (selected.isEmpty()) {
                omitted.put(target, "maximum_prerequisite_depth");
                recordBranchDecision(
                        decisions, target, targetRank, selection,
                        anchorSelection, branchContext);
                continue;
            }
            generated.put(target, selected);
            recordBranchDecision(
                    decisions, target, targetRank, selection,
                    anchorSelection, branchContext);
            remainingPrerequisiteBudget = Math.subtractExact(
                    remainingPrerequisiteBudget, selected.size());
        }
        validateGeneratedPositions(generated, positioned, true);
        return result(
                profileId,
                candidates,
                generated,
                omitted,
                decisions,
                branchCoordinates);
    }

    private static void recordBranchDecision(
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> decisions,
            ResourceLocation target,
            int targetRank,
            ParentSelection selection,
            AnchorSelection anchorSelection,
            BranchTopologyContext context) {
        if (selection.branchBasis().isEmpty()) {
            return;
        }
        BranchSelectionBasis basis = selection.branchBasis().orElseThrow();
        List<ResourceLocation> selected = anchorSelection.selected();
        Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision.ParentRelation> relations =
                new LinkedHashMap<>();
        selected.forEach(parent -> relations.put(
                parent, context.parentRelation(target, parent)));
        boolean depthShortcut = selected.stream().anyMatch(parent ->
                context.depthShortcut(target, targetRank, parent));
        AutomaticWeaponPrerequisiteDecision decision =
                new AutomaticWeaponPrerequisiteDecision(
                        target,
                        basis.strategy(),
                        Optional.of(basis.branchIndex()),
                        basis.rankIndex(),
                        basis.familyStartIndex(),
                        basis.transitionEndIndex(),
                        selection.desiredCount(),
                        basis.secondParentQuotaBasisPoints(),
                        basis.secondParentEligible(),
                        relations,
                        anchorSelection.mergeRejection(),
                        depthShortcut,
                        basis.terminalPeer());
        if (decisions.put(target, decision) != null) {
            throw new IllegalStateException(
                    "Automatic prerequisite decision was recorded twice");
        }
    }

    private static ParentSelection legacyDynamicSelection(
            ResourceLocation target,
            int targetRank,
            int targetIndex,
            int targetLayerSize,
            int rankIndex,
            int occupiedRankCount,
            boolean foundationLayer,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            NavigableMap<Integer, List<ResourceLocation>> automaticByRank,
            NavigableMap<Integer, List<ResourceLocation>> authoredByRank) {
        List<ResourceLocation> anchors = foundationLayer
                ? dynamicFoundationAnchorCandidates(target, targetRank, authoredByRank)
                : dynamicAnchorCandidates(
                        target,
                        targetRank,
                        targetIndex,
                        targetLayerSize,
                        automaticByRank,
                        authoredByRank);
        int lowerInterconnectionTransitions =
                ResearchTechTreeContract.sharedMeshTransitionCount(occupiedRankCount);
        boolean interconnectedBase = rankIndex <= lowerInterconnectionTransitions;
        boolean periodicMerge = candidates.policy().mergeInterval() > 0
                && interconnectedBase
                && rankIndex > 0
                && rankIndex % candidates.policy().mergeInterval() == 0;
        int desiredCount = foundationLayer
                ? 1
                : interconnectedBase
                        ? Math.min(candidates.policy().maxGeneratedPrerequisites(), 2)
                        : 1;
        if (!foundationLayer && periodicMerge) {
            desiredCount = candidates.policy().maxGeneratedPrerequisites();
        }
        return new ParentSelection(anchors, desiredCount);
    }

    private static ParentSelection branchAwareSelection(
            ResourceLocation target,
            int targetRank,
            int rankIndex,
            int occupiedRankCount,
            int familyStart,
            int transitionEnd,
            boolean foundationLayer,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            BranchTopologyContext context,
            boolean secondParentScheduled) {
        if (foundationLayer) {
            int quota = context.matchingFoundationAnchorCount(target, targetRank) >= 2
                    ? FOUNDATION_SECOND_PARENT_QUOTA_BASIS_POINTS : 0;
            boolean secondParent = candidates.policy().maxGeneratedPrerequisites() >= 2
                    && secondParentScheduled;
            return new ParentSelection(
                    context.foundationAnchors(target, targetRank),
                    secondParent ? 2 : 1,
                    context.basis(
                            target,
                            AutomaticWeaponPrerequisiteDecision.Strategy.FOUNDATION,
                            rankIndex,
                            familyStart,
                            transitionEnd,
                            quota,
                            secondParent));
        }

        boolean trunk = rankIndex < familyStart;
        boolean transition = !trunk && rankIndex <= transitionEnd;
        int secondParentQuota = secondParentQuotaBasisPoints(
                rankIndex, familyStart, transitionEnd);
        boolean secondParent = candidates.policy().maxGeneratedPrerequisites() >= 2
                && secondParentScheduled;
        boolean periodicThirdParent = candidates.policy().maxGeneratedPrerequisites() >= 3
                && secondParent
                && candidates.policy().mergeInterval() > 0
                && rankIndex > 0
                && rankIndex % candidates.policy().mergeInterval() == 0;
        int desiredCount = periodicThirdParent ? 3 : secondParent ? 2 : 1;
        boolean crossFamilyMerge = false;
        if (trunk) {
            crossFamilyMerge = secondParent;
        } else if (transition) {
            int length = transitionEnd - familyStart + 1;
            int step = rankIndex - familyStart;
            crossFamilyMerge = secondParent && step * 2 < length;
        }
        AutomaticWeaponPrerequisiteDecision.Strategy strategy = trunk
                ? AutomaticWeaponPrerequisiteDecision.Strategy.SHARED_TRUNK
                : transition
                        ? crossFamilyMerge
                                ? AutomaticWeaponPrerequisiteDecision.Strategy
                                        .TRANSITION_CROSS_FAMILY
                                : AutomaticWeaponPrerequisiteDecision.Strategy.TRANSITION_LOCAL
                        : AutomaticWeaponPrerequisiteDecision.Strategy.SPECIALIZATION;
        return new ParentSelection(
                context.anchors(target, targetRank, trunk, crossFamilyMerge),
                desiredCount,
                context.basis(
                        target,
                        strategy,
                        rankIndex,
                        familyStart,
                        transitionEnd,
                        secondParentQuota,
                        secondParent));
    }

    static int secondParentQuotaBasisPoints(
            int rankIndex,
            int familyStart,
            int transitionEnd) {
        if (rankIndex < 0 || familyStart < 0 || transitionEnd < familyStart) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite maturity curve is invalid");
        }
        if (rankIndex < familyStart) {
            return FULL_SECOND_PARENT_QUOTA_BASIS_POINTS;
        }
        if (rankIndex > transitionEnd) {
            return TERMINAL_SECOND_PARENT_QUOTA_BASIS_POINTS;
        }
        int span = transitionEnd - familyStart;
        if (span == 0) {
            return FULL_SECOND_PARENT_QUOTA_BASIS_POINTS;
        }
        int step = rankIndex - familyStart;
        int decline = Math.floorDiv(
                Math.multiplyExact(
                        FULL_SECOND_PARENT_QUOTA_BASIS_POINTS
                                - TERMINAL_SECOND_PARENT_QUOTA_BASIS_POINTS,
                        step),
                span);
        return FULL_SECOND_PARENT_QUOTA_BASIS_POINTS - decline;
    }

    private static Set<ResourceLocation> secondParentSchedule(
            NavigableMap<Integer, List<ResourceLocation>> automaticByRank,
            Map<Integer, Integer> rankIndexes,
            int familyStart,
            int transitionEnd,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            BranchTopologyContext context) {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        automaticByRank.forEach((rank, targets) -> {
            int rankIndex = rankIndexes.getOrDefault(rank, 0);
            int quota = rankIndex == 0
                    ? FOUNDATION_SECOND_PARENT_QUOTA_BASIS_POINTS
                    : secondParentQuotaBasisPoints(
                            rankIndex, familyStart, transitionEnd);
            List<ResourceLocation> eligible = targets.stream()
                    .filter(target -> policies.get(target).prerequisites().isEmpty())
                    .filter(target -> rankIndex != 0
                            || context.matchingFoundationAnchorCount(target, rank) >= 2)
                    .toList();
            Map<ResourceLocation, Integer> branches = new LinkedHashMap<>();
            eligible.forEach(target -> branches.put(target, context.branchIndex(target)));
            result.addAll(stratifiedQuotaSelection(eligible, branches, quota));
        });
        return Set.copyOf(result);
    }

    static Set<ResourceLocation> stratifiedQuotaSelection(
            List<ResourceLocation> targets,
            Map<ResourceLocation, Integer> branchByTarget,
            int quotaBasisPoints) {
        if (targets == null || branchByTarget == null
                || quotaBasisPoints < 0
                || quotaBasisPoints > FULL_SECOND_PARENT_QUOTA_BASIS_POINTS
                || targets.stream().anyMatch(java.util.Objects::isNull)
                || targets.stream().distinct().count() != targets.size()
                || !branchByTarget.keySet().containsAll(targets)
                || targets.stream().anyMatch(target ->
                        branchByTarget.get(target) == null
                                || branchByTarget.get(target) < 0
                                || branchByTarget.get(target)
                                        >= AutomaticWeaponBranchAnalyzer.MAX_BRANCHES)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite quota population is invalid");
        }
        int seatCount = Math.floorDiv(
                Math.addExact(
                        Math.multiplyExact(targets.size(), quotaBasisPoints),
                        FULL_SECOND_PARENT_QUOTA_BASIS_POINTS / 2),
                FULL_SECOND_PARENT_QUOTA_BASIS_POINTS);
        if (seatCount == 0) {
            return Set.of();
        }
        Map<Integer, List<ResourceLocation>> targetsByBranch = new TreeMap<>();
        targets.forEach(target -> targetsByBranch.computeIfAbsent(
                branchByTarget.get(target), ignored -> new ArrayList<>()).add(target));
        Comparator<ResourceLocation> selectionOrder = Comparator
                .comparingInt((ResourceLocation target) -> stableIndex(
                        target, FULL_SECOND_PARENT_QUOTA_BASIS_POINTS))
                .thenComparing(ResourceLocation::toString);
        targetsByBranch.replaceAll((branch, branchTargets) -> branchTargets.stream()
                .sorted(selectionOrder).toList());
        Map<Integer, Integer> seatsByBranch = new LinkedHashMap<>();
        int allocated = 0;
        for (Map.Entry<Integer, List<ResourceLocation>> entry
                : targetsByBranch.entrySet()) {
            int seats = Math.floorDiv(
                    Math.multiplyExact(entry.getValue().size(), seatCount),
                    targets.size());
            seatsByBranch.put(entry.getKey(), seats);
            allocated = Math.addExact(allocated, seats);
        }
        List<Integer> remainderOrder = targetsByBranch.keySet().stream()
                .sorted(Comparator
                        .comparingInt((Integer branch) -> Math.floorMod(
                                Math.multiplyExact(
                                        targetsByBranch.get(branch).size(), seatCount),
                                targets.size()))
                        .reversed()
                        .thenComparingInt(Integer::intValue))
                .toList();
        for (int index = 0; allocated < seatCount; index++, allocated++) {
            seatsByBranch.merge(
                    remainderOrder.get(index % remainderOrder.size()),
                    1,
                    Math::addExact);
        }
        LinkedHashSet<ResourceLocation> selected = new LinkedHashSet<>();
        targetsByBranch.forEach((branch, branchTargets) -> selected.addAll(
                branchTargets.subList(0, seatsByBranch.getOrDefault(branch, 0))));
        return Set.copyOf(selected);
    }

    static int branchFamilyStartIndex(
            int candidateCount,
            AutomaticWeaponPlacementPolicy policy,
            int occupiedRankCount) {
        if (candidateCount < 1 || policy == null || !policy.usesDynamicLayers()
                || occupiedRankCount < 1) {
            throw new IllegalArgumentException(
                    "Automatic branch prerequisite population is invalid");
        }
        int plannedRankCount = AutomaticWeaponBranchLayerPlanner.targetRankCount(
                candidateCount, policy);
        return Math.min(
                occupiedRankCount - 1,
                AutomaticWeaponBranchLayerPlanner.sharedRankCount(plannedRankCount));
    }

    static int branchTransitionEndIndex(
            int occupiedRankCount,
            int familyStart) {
        if (occupiedRankCount < 1 || familyStart < 0
                || familyStart >= occupiedRankCount) {
            throw new IllegalArgumentException(
                    "Automatic branch prerequisite rank count is invalid");
        }
        int transitions = occupiedRankCount - 1;
        int taperedEnd = Math.floorDiv(
                Math.multiplyExact(transitions, BRANCH_TRANSITION_END_NUMERATOR),
                BRANCH_TRANSITION_END_DENOMINATOR);
        return Math.max(
                familyStart,
                Math.max(
                        ResearchTechTreeContract.sharedMeshTransitionCount(
                                occupiedRankCount),
                        taperedEnd));
    }

    private static List<ResourceLocation> dynamicFoundationAnchorCandidates(
            ResourceLocation target,
            int targetRank,
            NavigableMap<Integer, List<ResourceLocation>> authoredByRank) {
        Map.Entry<Integer, List<ResourceLocation>> authoredLayer =
                authoredByRank.floorEntry(targetRank);
        return authoredLayer == null
                ? List.of()
                : rotated(target, authoredLayer.getValue());
    }

    private static List<ResourceLocation> dynamicAnchorCandidates(
            ResourceLocation target,
            int targetRank,
            int targetIndex,
            int targetLayerSize,
            NavigableMap<Integer, List<ResourceLocation>> automaticByRank,
            NavigableMap<Integer, List<ResourceLocation>> authoredByRank) {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        Map.Entry<Integer, List<ResourceLocation>> automaticLayer =
                automaticByRank.lowerEntry(targetRank);
        if (automaticLayer != null) {
            List<ResourceLocation> parents = automaticLayer.getValue();
            if (!parents.isEmpty()) {
                int primary = Math.min(
                        parents.size() - 1,
                        Math.floorDiv(targetIndex * parents.size(), targetLayerSize));
                for (int offset = 0; offset < parents.size(); offset++) {
                    result.add(parents.get((primary + offset) % parents.size()));
                }
            }
        }
        Map.Entry<Integer, List<ResourceLocation>> authoredLayer =
                authoredByRank.lowerEntry(targetRank);
        if (authoredLayer != null) {
            result.addAll(rotated(target, authoredLayer.getValue()));
        }
        Map.Entry<Integer, List<ResourceLocation>> automaticRoots =
                automaticByRank.firstEntry();
        if (automaticRoots != null && automaticRoots.getKey() < targetRank
                && automaticRoots != automaticLayer) {
            result.addAll(rotated(target, automaticRoots.getValue()));
        }
        result.remove(target);
        return List.copyOf(result);
    }

    private static Map<ResourceLocation, PositionedWeapon> positionedWeapons(
            BlueprintResearchSnapshot research,
            Map<ResourceLocation, BlueprintData> catalog,
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        Map<ResourceLocation, PositionedWeapon> result = new LinkedHashMap<>();
        for (String value : candidates.authoredBlueprintIds()) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            BlueprintData data = id == null ? null : catalog.get(id);
            if (data == null) {
                throw new IllegalArgumentException(
                        "Authored automatic-placement candidate is absent from the catalog");
            }
            var placement = ResearchTechTreePlacementResolver.resolve(
                    research, candidates.treeId(), id, data).placement().orElseThrow();
            if (!placement.origin().authored()) {
                throw new IllegalArgumentException(
                        "Authored candidate no longer has an authored placement");
            }
            result.put(id, new PositionedWeapon(
                    new ProgressionPosition(
                            placement.tier(), placement.level(), placement.order()),
                    placement.progressionCoordinate(),
                    true));
        }
        candidates.eligibleProposals().forEach((value, proposal) -> {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null || !catalog.containsKey(id) || !policies.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Eligible automatic-placement candidate is absent from the catalog");
            }
            result.put(id, new PositionedWeapon(
                    proposal.position(), proposal.progressionCoordinate(), false));
        });
        return Map.copyOf(result);
    }

    private static List<ResourceLocation> anchorCandidates(
            ResourceLocation target,
            ProgressionPosition targetPosition,
            Map<ResourceLocation, PositionedWeapon> positioned,
            AnchorIndex anchorIndex) {
        ProgressionBucket targetBucket = ProgressionBucket.of(targetPosition);
        List<ResourceLocation> sameBucket = anchorIndex.usableByBucket()
                .getOrDefault(targetBucket, List.of()).stream()
                .filter(id -> !id.equals(target)
                        && POSITION_ORDER.compare(
                                positioned.get(id).position(), targetPosition) < 0)
                .toList();

        ProgressionBucket previousBucket = anchorIndex.usableByBucket().lowerKey(targetBucket);
        List<ResourceLocation> previousAuthored = previousBucket == null
                ? List.of()
                : anchorIndex.authoredByBucket().getOrDefault(previousBucket, List.of()).stream()
                        .filter(id -> !id.equals(target))
                        .toList();
        Set<ResourceLocation> authored = Set.copyOf(previousAuthored);
        List<ResourceLocation> previousAutomatic = previousBucket == null
                ? List.of()
                : anchorIndex.usableByBucket().get(previousBucket).stream()
                        .filter(id -> !id.equals(target) && !authored.contains(id))
                        .toList();

        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        result.addAll(rotated(target, sameBucket));
        result.addAll(rotated(target, previousAuthored));
        result.addAll(rotated(target, previousAutomatic));
        return List.copyOf(result);
    }

    private static List<ResourceLocation> rotated(
            ResourceLocation target,
            List<ResourceLocation> candidates) {
        if (candidates.size() < 2) {
            return candidates;
        }
        List<ResourceLocation> result = new ArrayList<>(candidates);
        Collections.rotate(result, -stableIndex(target, result.size()));
        return List.copyOf(result);
    }

    private static Comparator<ResourceLocation> anchorOrder(
            Map<ResourceLocation, PositionedWeapon> positioned) {
        return Comparator
                .comparing((ResourceLocation id) -> positioned.get(id).position(), POSITION_ORDER)
                .thenComparing(ResourceLocation::toString);
    }

    private static AnchorSelection selectDepthSafeAnchors(
            ResourceLocation target,
            List<ResourceLocation> anchors,
            int desiredCount,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, Integer> prerequisiteDepths,
            Map<ResourceLocation, Set<ResourceLocation>> prerequisiteClosures,
            Map<ResourceLocation, PositionedWeapon> positioned,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            boolean rankAuthoritative,
            boolean economyAware,
            boolean directNodeGraceAllowed) {
        List<ResourceLocation> selected = new ArrayList<>();
        Optional<AutomaticWeaponPrerequisiteDecision.MergeRejection> mergeRejection =
                Optional.empty();
        for (ResourceLocation anchor : anchors) {
            int anchorDepth = prerequisiteDepth(
                    anchor,
                    basePrerequisites,
                    generated,
                    prerequisiteDepths,
                    new LinkedHashSet<>());
            boolean redundant = selected.stream().anyMatch(existing ->
                    !independentSameAutomaticRank(
                            anchor, existing, positioned, rankAuthoritative)
                            && (dependsOn(anchor, existing, basePrerequisites, generated,
                                    new LinkedHashSet<>())
                                    || dependsOn(existing, anchor,
                                            basePrerequisites, generated,
                                            new LinkedHashSet<>())));
            if (anchorDepth < BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH
                    && !redundant) {
                if (economyAware && !selected.isEmpty()) {
                    Optional<AutomaticWeaponPrerequisiteDecision.MergeRejection> rejected =
                            closureInflationRejection(
                                    anchor,
                                    selected,
                                    basePrerequisites,
                                    generated,
                                    prerequisiteClosures,
                                    policies,
                                    directNodeGraceAllowed);
                    if (rejected.isPresent()) {
                        if (mergeRejection.isEmpty()) {
                            mergeRejection = rejected;
                        }
                        continue;
                    }
                }
                selected.add(anchor);
                if (selected.size() >= desiredCount) {
                    break;
                }
            }
        }
        return new AnchorSelection(selected, mergeRejection);
    }

    private static Optional<AutomaticWeaponPrerequisiteDecision.MergeRejection>
            closureInflationRejection(
                    ResourceLocation candidate,
                    List<ResourceLocation> selected,
                    Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
                    Map<ResourceLocation, List<ResourceLocation>> generated,
                    Map<ResourceLocation, Set<ResourceLocation>> prerequisiteClosures,
                    Map<ResourceLocation, BlueprintResearchPolicy> policies,
                    boolean directNodeGraceAllowed) {
        LinkedHashSet<ResourceLocation> existingClosure = new LinkedHashSet<>();
        selected.forEach(parent -> existingClosure.addAll(prerequisiteClosure(
                parent,
                basePrerequisites,
                generated,
                prerequisiteClosures,
                new LinkedHashSet<>())));
        Set<ResourceLocation> candidateClosure = prerequisiteClosure(
                candidate,
                basePrerequisites,
                generated,
                prerequisiteClosures,
                new LinkedHashSet<>());
        long existingCost = closureCost(existingClosure, policies);
        long candidateCost = closureCost(candidateClosure, policies);
        LinkedHashSet<ResourceLocation> union = new LinkedHashSet<>(existingClosure);
        union.addAll(candidateClosure);
        long unionCost = closureCost(union, policies);
        long dominantCost = Math.max(existingCost, candidateCost);
        long directNodeGrace = directNodeGraceAllowed
                ? selected.stream()
                        .mapToLong(parent -> pointCost(parent, policies))
                        .max()
                        .orElse(0L)
                : 0L;
        if (directNodeGraceAllowed) {
            directNodeGrace = Math.max(
                    directNodeGrace, pointCost(candidate, policies));
        }
        if (closureInflationAllowed(
                existingCost,
                candidateCost,
                unionCost,
                directNodeGrace)) {
            return Optional.empty();
        }
        long maximumAllowed = maximumAllowedClosureCost(
                existingCost, candidateCost, directNodeGrace);
        return Optional.of(new AutomaticWeaponPrerequisiteDecision.MergeRejection(
                candidate,
                AutomaticWeaponPrerequisiteDecision.MergeRejectionReason
                        .CLOSURE_INFLATION,
                existingCost,
                candidateCost,
                unionCost,
                maximumAllowed));
    }

    static boolean closureInflationAllowed(
            long existingClosureCost,
            long candidateClosureCost,
            long unionClosureCost,
            long directNodeGrace) {
        if (existingClosureCost < 0L || candidateClosureCost < 0L
                || unionClosureCost < Math.max(
                        existingClosureCost, candidateClosureCost)
                || directNodeGrace < 0L) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite closure costs are invalid");
        }
        return unionClosureCost <= maximumAllowedClosureCost(
                existingClosureCost, candidateClosureCost, directNodeGrace);
    }

    private static long maximumAllowedClosureCost(
            long existingClosureCost,
            long candidateClosureCost,
            long directNodeGrace) {
        long dominantCost = Math.max(existingClosureCost, candidateClosureCost);
        long ratioGrace = Math.addExact(
                Math.floorDiv(dominantCost, 2L), dominantCost % 2L);
        long grace = Math.max(ratioGrace, directNodeGrace);
        return dominantCost > Long.MAX_VALUE - grace
                ? Long.MAX_VALUE : dominantCost + grace;
    }

    private static Set<ResourceLocation> prerequisiteClosure(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, Set<ResourceLocation>> memo,
            Set<ResourceLocation> visiting) {
        Set<ResourceLocation> known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite plan encountered a cycle");
        }
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        result.add(node);
        for (ResourceLocation prerequisite : effectivePrerequisites(
                node, basePrerequisites, generated)) {
            result.addAll(prerequisiteClosure(
                    prerequisite,
                    basePrerequisites,
                    generated,
                    memo,
                    visiting));
        }
        visiting.remove(node);
        Set<ResourceLocation> frozen = Collections.unmodifiableSet(result);
        memo.put(node, frozen);
        return frozen;
    }

    private static long closureCost(
            Set<ResourceLocation> closure,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        long result = 0L;
        for (ResourceLocation id : closure) {
            result = Math.addExact(result, pointCost(id, policies));
        }
        return result;
    }

    private static int pointCost(
            ResourceLocation id,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        BlueprintResearchPolicy policy = policies.get(id);
        return policy == null ? 0 : policy.researchCost().points();
    }

    private static boolean independentSameAutomaticRank(
            ResourceLocation left,
            ResourceLocation right,
            Map<ResourceLocation, PositionedWeapon> positioned,
            boolean rankAuthoritative) {
        PositionedWeapon leftPosition = positioned.get(left);
        PositionedWeapon rightPosition = positioned.get(right);
        return rankAuthoritative
                && leftPosition != null && rightPosition != null
                && !leftPosition.authored() && !rightPosition.authored()
                && leftPosition.coordinate().rank() == rightPosition.coordinate().rank();
    }

    private static boolean dependsOn(
            ResourceLocation node,
            ResourceLocation possiblePrerequisite,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Set<ResourceLocation> visiting) {
        if (node.equals(possiblePrerequisite)) {
            return true;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite plan encountered a cycle");
        }
        for (ResourceLocation prerequisite : effectivePrerequisites(
                node, basePrerequisites, generated)) {
            if (prerequisite.equals(possiblePrerequisite)
                    || dependsOn(
                            prerequisite,
                            possiblePrerequisite,
                            basePrerequisites,
                            generated,
                            visiting)) {
                visiting.remove(node);
                return true;
            }
        }
        visiting.remove(node);
        return false;
    }

    private static List<ResourceLocation> effectivePrerequisites(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, List<ResourceLocation>> generated) {
        List<ResourceLocation> authored = basePrerequisites.getOrDefault(node, List.of());
        return authored.isEmpty()
                ? generated.getOrDefault(node, List.of())
                : authored;
    }

    private static int prerequisiteDepth(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> basePrerequisites,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, Integer> memo,
            Set<ResourceLocation> visiting) {
        Integer known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite plan encountered a cycle");
        }
        int depth = 1;
        List<ResourceLocation> prerequisites = effectivePrerequisites(
                node, basePrerequisites, generated);
        for (ResourceLocation prerequisite : prerequisites) {
            depth = Math.max(depth, 1 + prerequisiteDepth(
                    prerequisite, basePrerequisites, generated, memo, visiting));
        }
        visiting.remove(node);
        memo.put(node, depth);
        return depth;
    }

    private static void validateGeneratedPositions(
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, PositionedWeapon> positioned,
            boolean rankAuthoritative) {
        generated.forEach((dependent, prerequisites) -> {
            PositionedWeapon dependentPosition = positioned.get(dependent);
            for (ResourceLocation prerequisite : prerequisites) {
                PositionedWeapon prerequisitePosition = positioned.get(prerequisite);
                boolean transitionAllowed = dependentPosition != null
                        && prerequisitePosition != null
                        && (rankAuthoritative
                                ? ResearchTechTreeContract.progressionTransitionAllowed(
                                        prerequisitePosition.coordinate(),
                                        dependentPosition.coordinate())
                                        || authoredFoundationBridgeAllowed(
                                                prerequisitePosition,
                                                dependentPosition)
                                : ResearchTechTreeContract.progressionTransitionAllowed(
                                        prerequisitePosition.position(),
                                        dependentPosition.position()));
                if (!transitionAllowed) {
                    throw new IllegalArgumentException(
                            "Automatic prerequisite contradicts progression position");
                }
            }
        });
    }

    private static boolean authoredFoundationBridgeAllowed(
            PositionedWeapon prerequisite,
            PositionedWeapon dependent) {
        return prerequisite.authored()
                && !dependent.authored()
                && prerequisite.coordinate().rank() == dependent.coordinate().rank();
    }

    private static boolean usable(BlueprintResearchPolicy policy) {
        return policy != null
                && policy.available()
                && policy.journalEnabled()
                && policy.treeEnabled()
                && policy.researchEnabled()
                && !policy.requiresDiscovery()
                && policy.visibility().allowsServerSelection();
    }

    private static int stableIndex(ResourceLocation target, int size) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(target.toString().getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = (value << 8) | (hash[index] & 0xffL);
            }
            return (int) Math.floorMod(value, size);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record PositionedWeapon(
            ProgressionPosition position,
            ProgressionCoordinate coordinate,
            boolean authored) {
        private PositionedWeapon {
            if (position == null || coordinate == null) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite position cannot be null");
            }
        }
    }

    private record AnchorSelection(
            List<ResourceLocation> selected,
            Optional<AutomaticWeaponPrerequisiteDecision.MergeRejection> mergeRejection) {
        private static final AnchorSelection EMPTY =
                new AnchorSelection(List.of(), Optional.empty());

        private AnchorSelection {
            selected = selected == null ? List.of() : List.copyOf(selected);
            mergeRejection = mergeRejection == null ? Optional.empty() : mergeRejection;
            List<ResourceLocation> stableSelected = selected;
            if (selected.stream().anyMatch(java.util.Objects::isNull)
                    || selected.stream().distinct().count() != selected.size()
                    || mergeRejection.filter(value -> stableSelected.contains(
                            value.parentId())).isPresent()) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite anchor result is invalid");
            }
        }
    }

    private record ParentSelection(
            List<ResourceLocation> anchors,
            int desiredCount,
            Optional<BranchSelectionBasis> branchBasis) {
        private ParentSelection(
                List<ResourceLocation> anchors,
                int desiredCount) {
            this(anchors, desiredCount, Optional.empty());
        }

        private ParentSelection(
                List<ResourceLocation> anchors,
                int desiredCount,
                BranchSelectionBasis branchBasis) {
            this(anchors, desiredCount, Optional.of(branchBasis));
        }

        private ParentSelection {
            branchBasis = branchBasis == null ? Optional.empty() : branchBasis;
            if (anchors == null || anchors.stream().anyMatch(java.util.Objects::isNull)
                    || desiredCount < 1
                    || desiredCount
                            > AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite parent selection is invalid");
            }
            anchors = List.copyOf(new LinkedHashSet<>(anchors));
        }
    }

    private record BranchSelectionBasis(
            AutomaticWeaponPrerequisiteDecision.Strategy strategy,
            int branchIndex,
            int rankIndex,
            int familyStartIndex,
            int transitionEndIndex,
            int secondParentQuotaBasisPoints,
            boolean secondParentEligible,
            boolean terminalPeer) {
        private BranchSelectionBasis {
            if (strategy == null || branchIndex < 0
                    || branchIndex >= AutomaticWeaponBranchAnalyzer.MAX_BRANCHES
                    || rankIndex < 0 || familyStartIndex < 0
                    || transitionEndIndex < familyStartIndex
                    || secondParentQuotaBasisPoints < 0
                    || secondParentQuotaBasisPoints
                            > FULL_SECOND_PARENT_QUOTA_BASIS_POINTS) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite branch selection basis is invalid");
            }
        }
    }

    /**
     * Immutable branch/rank index derived from the exact canonical model retained
     * beside the positioned snapshot. It changes parent preference only; all
     * authored policy, depth, edge-budget, and progression checks remain in the
     * common planner.
     */
    private record BranchTopologyContext(
            AutomaticWeaponBranchModel model,
            Map<String, AutomaticWeaponRoleSignature> roleSignatures,
            Map<String, AutomaticWeaponRoleSignature> authoredRoleSignatures,
            NavigableMap<Integer, List<ResourceLocation>> automaticByRank,
            NavigableMap<Integer, List<ResourceLocation>> authoredByRank,
            Map<Integer, NavigableMap<Integer, List<ResourceLocation>>> automaticByBranch,
            Map<Integer, List<ResourceLocation>> authoredAnchorsByBranch) {
        private static BranchTopologyContext create(
                AutomaticWeaponBranchModel model,
                Map<String, AutomaticWeaponRoleSignature> roleSignatures,
                Map<String, AutomaticWeaponRoleSignature> authoredRoleSignatures,
                NavigableMap<Integer, List<ResourceLocation>> automaticByRank,
                NavigableMap<Integer, List<ResourceLocation>> authoredByRank,
                Map<ResourceLocation, PositionedWeapon> positioned,
                Map<ResourceLocation, BlueprintResearchPolicy> policies,
                Comparator<ResourceLocation> coordinateOrder) {
            if (model == null || roleSignatures == null
                    || authoredRoleSignatures == null
                    || automaticByRank == null || authoredByRank == null
                    || positioned == null || policies == null || coordinateOrder == null
                    || model.equals(AutomaticWeaponBranchModel.EMPTY)) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite branch topology is invalid");
            }

            Map<Integer, NavigableMap<Integer, List<ResourceLocation>>> byBranch =
                    new LinkedHashMap<>();
            model.branches().forEach(branch ->
                    byBranch.put(branch.index(), new TreeMap<>()));
            automaticByRank.forEach((rank, ids) -> ids.forEach(id -> {
                Integer branch = model.branchIndexByBlueprint().get(id.toString());
                if (branch == null || !byBranch.containsKey(branch)) {
                    throw new IllegalArgumentException(
                            "Automatic prerequisite candidate has no canonical branch");
                }
                byBranch.get(branch).computeIfAbsent(
                        rank, ignored -> new ArrayList<>()).add(id);
            }));

            Map<Integer, NavigableMap<Integer, List<ResourceLocation>>> frozenBranches =
                    new LinkedHashMap<>();
            byBranch.forEach((branch, ranks) -> {
                TreeMap<Integer, List<ResourceLocation>> frozenRanks = new TreeMap<>();
                ranks.forEach((rank, ids) -> frozenRanks.put(
                        rank, ids.stream().sorted(coordinateOrder).toList()));
                frozenBranches.put(
                        branch, Collections.unmodifiableNavigableMap(frozenRanks));
            });

            Map<Integer, List<ResourceLocation>> authoredAnchors = new LinkedHashMap<>();
            model.branches().forEach(branch -> {
                List<ResourceLocation> anchors = branch.authoredAnchorBlueprintIds().stream()
                        .map(ResourceLocation::tryParse)
                        .filter(java.util.Objects::nonNull)
                        .filter(positioned::containsKey)
                        .filter(id -> positioned.get(id).authored())
                        .filter(id -> usable(policies.get(id)))
                        .sorted(coordinateOrder)
                        .toList();
                authoredAnchors.put(branch.index(), anchors);
            });
            return new BranchTopologyContext(
                    model,
                    Map.copyOf(roleSignatures),
                    Map.copyOf(authoredRoleSignatures),
                    automaticByRank,
                    authoredByRank,
                    Collections.unmodifiableMap(frozenBranches),
                    Collections.unmodifiableMap(authoredAnchors));
        }

        private List<ResourceLocation> foundationAnchors(
                ResourceLocation target,
                int targetRank) {
            LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
            int branch = branchIndex(target);
            List<ResourceLocation> local = authoredAnchorsByBranch.getOrDefault(
                    branch, List.of()).stream()
                    .filter(id -> authoredRank(id) <= targetRank)
                    .toList();
            result.addAll(rotated(target, local));
            result.addAll(dynamicFoundationAnchorCandidates(
                    target, targetRank, authoredByRank));
            result.remove(target);
            return List.copyOf(result);
        }

        private int matchingFoundationAnchorCount(
                ResourceLocation target,
                int targetRank) {
            int branch = branchIndex(target);
            return Math.toIntExact(authoredAnchorsByBranch.getOrDefault(
                    branch, List.of()).stream()
                    .filter(id -> authoredRank(id) <= targetRank)
                    .count());
        }

        private BranchSelectionBasis basis(
                ResourceLocation target,
                AutomaticWeaponPrerequisiteDecision.Strategy strategy,
                int rankIndex,
                int familyStartIndex,
                int transitionEndIndex,
                int secondParentQuotaBasisPoints,
                boolean secondParentEligible) {
            int branch = branchIndex(target);
            boolean terminal = model.branches().get(branch).terminalBlueprintIds()
                    .contains(target.toString());
            return new BranchSelectionBasis(
                    strategy,
                    branch,
                    rankIndex,
                    familyStartIndex,
                    transitionEndIndex,
                    secondParentQuotaBasisPoints,
                    secondParentEligible,
                    terminal);
        }

        private AutomaticWeaponPrerequisiteDecision.ParentRelation parentRelation(
                ResourceLocation target,
                ResourceLocation parent) {
            int targetBranch = branchIndex(target);
            Integer automaticBranch = model.branchIndexByBlueprint().get(parent.toString());
            if (automaticBranch != null) {
                return automaticBranch == targetBranch
                        ? AutomaticWeaponPrerequisiteDecision.ParentRelation.SAME_FAMILY
                        : AutomaticWeaponPrerequisiteDecision.ParentRelation.CROSS_FAMILY;
            }
            for (AutomaticWeaponBranchModel.Branch branch : model.branches()) {
                if (branch.authoredAnchorBlueprintIds().contains(parent.toString())) {
                    return branch.index() == targetBranch
                            ? AutomaticWeaponPrerequisiteDecision.ParentRelation
                                    .AUTHORED_SAME_FAMILY
                            : AutomaticWeaponPrerequisiteDecision.ParentRelation
                                    .AUTHORED_CROSS_FAMILY;
                }
            }
            return AutomaticWeaponPrerequisiteDecision.ParentRelation.UNCLASSIFIED;
        }

        private boolean depthShortcut(
                ResourceLocation target,
                int targetRank,
                ResourceLocation parent) {
            int branch = branchIndex(target);
            if (model.branchIndexByBlueprint().get(parent.toString()) == null
                    || model.branchIndexByBlueprint().get(parent.toString()) != branch) {
                return false;
            }
            NavigableMap<Integer, List<ResourceLocation>> ranks =
                    automaticByBranch.get(branch);
            if (ranks == null) {
                return false;
            }
            Map.Entry<Integer, List<ResourceLocation>> immediate = ranks.lowerEntry(targetRank);
            Map.Entry<Integer, List<ResourceLocation>> foundation = ranks.firstEntry();
            return immediate != null && foundation != null
                    && foundation.getKey() < immediate.getKey()
                    && foundation.getValue().contains(parent);
        }

        private List<ResourceLocation> anchors(
                ResourceLocation target,
                int targetRank,
                boolean trunk,
                boolean crossFamilyMerge) {
            int branch = branchIndex(target);
            List<ResourceLocation> global = globalCandidates(target, targetRank);
            List<ResourceLocation> local = localCandidates(
                    target, targetRank, branch);
            List<ResourceLocation> other = global.stream()
                    .filter(id -> branchIndexOrDefault(id, branch) != branch)
                    .toList();
            List<ResourceLocation> branchAuthored = authoredAnchorsByBranch
                    .getOrDefault(branch, List.of()).stream()
                    .filter(id -> authoredRank(id) < targetRank)
                    .toList();

            LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
            if (trunk) {
                addDiverseGlobal(result, global, branch);
                result.addAll(local);
            } else if (crossFamilyMerge) {
                addFirst(result, local);
                addFirst(result, other);
                result.addAll(local);
                result.addAll(other);
            } else {
                result.addAll(local);
                result.addAll(rotated(target, branchAuthored));
            }
            if (trunk || crossFamilyMerge || result.isEmpty()) {
                result.addAll(global);
            }
            result.remove(target);
            return List.copyOf(result);
        }

        private List<ResourceLocation> globalCandidates(
                ResourceLocation target,
                int targetRank) {
            List<ResourceLocation> current = automaticByRank.getOrDefault(
                    targetRank, List.of());
            return rankAfterPrimary(target, dynamicAnchorCandidates(
                    target,
                    targetRank,
                    Math.max(0, current.indexOf(target)),
                    Math.max(1, current.size()),
                    automaticByRank,
                    authoredByRank));
        }

        private List<ResourceLocation> localCandidates(
                ResourceLocation target,
                int targetRank,
                int branch) {
            NavigableMap<Integer, List<ResourceLocation>> ranks =
                    automaticByBranch.getOrDefault(branch, new TreeMap<>());
            Map.Entry<Integer, List<ResourceLocation>> parents = ranks.lowerEntry(targetRank);
            if (parents == null || parents.getValue().isEmpty()) {
                return List.of();
            }
            List<ResourceLocation> targets = ranks.getOrDefault(targetRank, List.of(target));
            int targetIndex = Math.max(0, targets.indexOf(target));
            int primary = Math.min(
                    parents.getValue().size() - 1,
                    Math.floorDiv(
                            targetIndex * parents.getValue().size(),
                            Math.max(1, targets.size())));
            List<ResourceLocation> result = new ArrayList<>(parents.getValue().size());
            for (int offset = 0; offset < parents.getValue().size(); offset++) {
                result.add(parents.getValue().get(
                        (primary + offset) % parents.getValue().size()));
            }
            Map.Entry<Integer, List<ResourceLocation>> roots = ranks.firstEntry();
            if (roots != null && roots.getKey() < targetRank
                    && roots.getKey() < parents.getKey()) {
                result.addAll(rotated(target, roots.getValue()));
            }
            return rankAfterPrimary(target, result);
        }

        /**
         * The proportional first candidate preserves horizontal continuity and
         * distributes fan-out. Remaining candidates prefer mechanical affinity;
         * the closure guard later favors overlapping ancestry and rejects costly
         * disjoint alternatives.
         */
        private List<ResourceLocation> rankAfterPrimary(
                ResourceLocation target,
                List<ResourceLocation> candidates) {
            if (candidates.size() < 3) {
                return List.copyOf(candidates);
            }
            ResourceLocation primary = candidates.get(0);
            List<ResourceLocation> remainder = candidates.subList(1, candidates.size())
                    .stream()
                    .sorted(Comparator
                            .comparingInt((ResourceLocation id) ->
                                    roleSimilarity(target, id)).reversed()
                            .thenComparingInt(id -> mechanicalScoreDistance(target, id))
                            .thenComparing(ResourceLocation::toString))
                    .toList();
            List<ResourceLocation> result = new ArrayList<>(candidates.size());
            result.add(primary);
            result.addAll(remainder);
            return List.copyOf(result);
        }

        private int roleSimilarity(
                ResourceLocation target,
                ResourceLocation candidate) {
            AutomaticWeaponRoleSignature targetRole = roleSignature(target);
            AutomaticWeaponRoleSignature candidateRole = roleSignature(candidate);
            return targetRole == null || candidateRole == null
                    ? 0
                    : targetRole.similarityTo(candidateRole).orElse(0);
        }

        private int mechanicalScoreDistance(
                ResourceLocation target,
                ResourceLocation candidate) {
            AutomaticWeaponRoleSignature targetRole = roleSignature(target);
            AutomaticWeaponRoleSignature candidateRole = roleSignature(candidate);
            return targetRole == null || candidateRole == null
                    ? ResearchTechTreeContract.SCORE_MAX
                    : Math.abs(targetRole.mechanicalScore()
                            - candidateRole.mechanicalScore());
        }

        private AutomaticWeaponRoleSignature roleSignature(ResourceLocation id) {
            AutomaticWeaponRoleSignature automatic = roleSignatures.get(id.toString());
            return automatic == null
                    ? authoredRoleSignatures.get(id.toString()) : automatic;
        }

        private void addDiverseGlobal(
                LinkedHashSet<ResourceLocation> result,
                List<ResourceLocation> global,
                int targetBranch) {
            addFirst(result, global);
            if (global.isEmpty()) {
                return;
            }
            int firstBranch = branchIndexOrDefault(global.get(0), targetBranch);
            global.stream()
                    .filter(id -> branchIndexOrDefault(id, firstBranch) != firstBranch)
                    .findFirst()
                    .ifPresent(result::add);
            result.addAll(global);
        }

        private static void addFirst(
                LinkedHashSet<ResourceLocation> target,
                List<ResourceLocation> source) {
            if (!source.isEmpty()) {
                target.add(source.get(0));
            }
        }

        private int branchIndex(ResourceLocation id) {
            Integer branch = model.branchIndexByBlueprint().get(id.toString());
            if (branch == null) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite target has no canonical branch");
            }
            return branch;
        }

        private int branchIndexOrDefault(ResourceLocation id, int fallback) {
            Integer automatic = model.branchIndexByBlueprint().get(id.toString());
            if (automatic != null) {
                return automatic;
            }
            for (AutomaticWeaponBranchModel.Branch branch : model.branches()) {
                if (branch.authoredAnchorBlueprintIds().contains(id.toString())) {
                    return branch.index();
                }
            }
            return fallback;
        }

        private int authoredRank(ResourceLocation id) {
            for (Map.Entry<Integer, List<ResourceLocation>> entry
                    : authoredByRank.entrySet()) {
                if (entry.getValue().contains(id)) {
                    return entry.getKey();
                }
            }
            return Integer.MAX_VALUE;
        }
    }

    /** Immutable per-plan lookup that keeps anchor discovery linear in bucket size. */
    private record AnchorIndex(
            NavigableMap<ProgressionBucket, List<ResourceLocation>> usableByBucket,
            NavigableMap<ProgressionBucket, List<ResourceLocation>> authoredByBucket) {
        private static AnchorIndex create(
                Map<ResourceLocation, PositionedWeapon> positioned,
                Map<ResourceLocation, BlueprintResearchPolicy> policies,
                AutomaticWeaponPlacementCandidateSnapshot candidates) {
            NavigableMap<ProgressionBucket, List<ResourceLocation>> usableBuckets =
                    new TreeMap<>();
            NavigableMap<ProgressionBucket, List<ResourceLocation>> authoredBuckets =
                    new TreeMap<>();
            positioned.forEach((id, weapon) -> {
                var proposal = candidates.eligibleProposal(id);
                boolean independentReview = !weapon.authored()
                        && proposal.filter(value -> value.reviewRequired()
                                && !candidates.policy().reviewHandling()
                                        .createsPrerequisite())
                                .isPresent();
                if (!usable(policies.get(id)) || independentReview) {
                    return;
                }
                ProgressionBucket bucket = ProgressionBucket.of(weapon.position());
                usableBuckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(id);
                if (weapon.authored()) {
                    authoredBuckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(id);
                }
            });
            Comparator<ResourceLocation> order = anchorOrder(positioned);
            return new AnchorIndex(
                    freeze(usableBuckets, order),
                    freeze(authoredBuckets, order));
        }

        private static NavigableMap<ProgressionBucket, List<ResourceLocation>> freeze(
                NavigableMap<ProgressionBucket, List<ResourceLocation>> source,
                Comparator<ResourceLocation> order) {
            TreeMap<ProgressionBucket, List<ResourceLocation>> copy = new TreeMap<>();
            source.forEach((bucket, ids) -> copy.put(
                    bucket,
                    ids.stream().sorted(order).toList()));
            return Collections.unmodifiableNavigableMap(copy);
        }
    }

    private record ProgressionBucket(Tier tier, int level)
            implements Comparable<ProgressionBucket> {
        private static ProgressionBucket of(ProgressionPosition position) {
            return new ProgressionBucket(position.tier(), position.level());
        }

        private ProgressionBucket {
            if (tier == null || level < 0
                    || level >= ResearchTechTreeContract.MAX_LEVELS_PER_TIER) {
                throw new IllegalArgumentException(
                        "Automatic prerequisite bucket is invalid");
            }
        }

        @Override
        public int compareTo(ProgressionBucket other) {
            int tierComparison = Integer.compare(tier.ordinal(), other.tier.ordinal());
            return tierComparison != 0
                    ? tierComparison
                    : Integer.compare(level, other.level);
        }
    }
}
