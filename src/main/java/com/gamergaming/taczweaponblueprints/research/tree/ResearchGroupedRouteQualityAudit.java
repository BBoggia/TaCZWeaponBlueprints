package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;

import net.minecraft.resources.ResourceLocation;

/**
 * Read-only Phase-5 evidence for the live AND-of-OR weapon topology. The audit
 * reports distributions and semantic warnings; none of its values participate
 * in prerequisite selection, layout, research authority, or build acceptance.
 */
public final class ResearchGroupedRouteQualityAudit {
    public static final String GROUPED_INTERPRETATION = "live_grouped_routes_v1";
    public static final String HYBRID_INTERPRETATION = "live_hybrid_routes_v1";
    /** Compatibility alias for consumers written before deliberate hybrid routes. */
    public static final String INTERPRETATION = GROUPED_INTERPRETATION;

    private static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::toString);

    private ResearchGroupedRouteQualityAudit() {
    }

    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchPointAwardEconomyProjection.Projection pointIncome) {
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException(
                    "Grouped-route quality inputs cannot be null");
        }
        if (!presentation.available() || automaticDiagnostics == null
                || automaticDiagnostics.prerequisiteStrategy()
                        == PrerequisiteStrategy.LEGACY_AND
                || presentation.treeId()
                        .filter(automaticDiagnostics.treeId()::equals).isEmpty()) {
            return Audit.EMPTY;
        }
        presentation.validateAgainst(graph);
        ResearchTechTreePresentation.DomainView weapons = presentation.domain(Domain.WEAPONS)
                .orElse(null);
        if (weapons == null) {
            return Audit.EMPTY;
        }

        List<ResourceLocation> members = weapons.lanes().stream()
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::nodeId)
                .sorted(ID_ORDER)
                .toList();
        Set<ResourceLocation> memberSet = Set.copyOf(members);
        Map<ResourceLocation, List<List<ResourceLocation>>> groups = new LinkedHashMap<>();
        for (ResourceLocation member : members) {
            List<ResearchTreeGraph.RequirementGroup> publicGroups =
                    graph.requirementGroupsOf(member);
            if (publicGroups.stream().anyMatch(group ->
                    group.hiddenAlternativeCount() != 0
                            || group.externalAlternativeCount() != 0
                            || !memberSet.containsAll(group.visibleAlternativeIds()))) {
                // This audit is operator evidence and must never estimate from a
                // disclosure-filtered or cross-domain projection.
                return Audit.EMPTY;
            }
            groups.put(member, publicGroups.stream()
                    .sorted(Comparator.comparingInt(
                            ResearchTreeGraph.RequirementGroup::ordinal))
                    .map(ResearchTreeGraph.RequirementGroup::visibleAlternativeIds)
                    .toList());
        }

        Map<ResourceLocation, Integer> indexById = new LinkedHashMap<>();
        for (int index = 0; index < members.size(); index++) {
            indexById.put(members.get(index), index);
        }
        List<ResourceLocation> topological = topologicalOrder(
                members, memberSet, graph.edges());
        Ancestry ancestry = ancestry(topological, indexById, groups);
        Map<ResourceLocation, RouteBound> routes = routeBounds(
                topological, graph, groups);

        Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> matched =
                new LinkedHashMap<>();
        int automaticTargetCount = 0;
        int unmatchedAutomaticTargetCount = 0;
        for (AutomaticWeaponPlacementDiagnostics.Entry entry
                : automaticDiagnostics.entries().values()) {
            if (entry.state() != AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC
                    || !memberSet.contains(entry.blueprintId())) {
                continue;
            }
            automaticTargetCount++;
            AutomaticWeaponPrerequisiteDecision decision =
                    entry.prerequisiteDecision().orElse(null);
            List<List<ResourceLocation>> generated = entry.generatedRequirements().allOf()
                    .stream().map(value -> value.anyOf()).toList();
            if (decision == null || !generated.equals(groups.get(entry.blueprintId()))) {
                unmatchedAutomaticTargetCount++;
                continue;
            }
            matched.put(entry.blueprintId(), decision);
        }

        EnumMap<Phase, MutablePhase> phaseEvidence = new EnumMap<>(Phase.class);
        for (Phase phase : Phase.values()) {
            phaseEvidence.put(phase, new MutablePhase());
        }
        List<Integer> mandatoryShares = new ArrayList<>();
        List<Integer> alternativeOverlap = new ArrayList<>();
        List<Integer> alternativeDivergence = new ArrayList<>();
        List<Long> routeCostRatioLowerBounds = new ArrayList<>();
        List<Long> routeCostRatioUpperBounds = new ArrayList<>();
        int alternativeGroupCount = 0;
        int effectiveAlternativeGroupCount = 0;
        int dependentAlternativePairCount = 0;
        int exactRouteCostGroupCount = 0;
        int zeroCostImbalancedGroupCount = 0;

        for (Map.Entry<ResourceLocation, AutomaticWeaponPrerequisiteDecision> entry
                : matched.entrySet()) {
            ResourceLocation target = entry.getKey();
            AutomaticWeaponPrerequisiteDecision decision = entry.getValue();
            Phase phase = Phase.from(decision.strategy());
            MutablePhase mutable = phaseEvidence.get(phase);
            mutable.targetCount++;
            int targetIndex = indexById.get(target);
            int conservativeCount = ancestry.conservative()[targetIndex].cardinality();
            if (conservativeCount > 0) {
                int share = basisPoints(
                        ancestry.mandatory()[targetIndex].cardinality(),
                        conservativeCount);
                mandatoryShares.add(share);
                mutable.mandatoryAncestorShares.add(share);
            }

            for (List<ResourceLocation> alternatives : groups.get(target)) {
                alternatives.forEach(parent -> mutable.parentReferences.merge(
                        parent, 1, Math::addExact));
                if (alternatives.size() < 2) {
                    continue;
                }
                alternativeGroupCount++;
                mutable.alternativeGroupCount++;
                AlternativeResult result = alternativeResult(
                        alternatives, indexById, ancestry, routes);
                alternativeOverlap.add(result.mandatoryAncestryOverlapBasisPoints());
                alternativeDivergence.add(result.ancestryDivergenceBasisPoints());
                dependentAlternativePairCount = Math.addExact(
                        dependentAlternativePairCount,
                        result.dependentAlternativePairCount());
                if (result.effective()) {
                    effectiveAlternativeGroupCount++;
                    mutable.effectiveAlternativeGroupCount++;
                }
                if (result.routeCostExact()) {
                    exactRouteCostGroupCount++;
                }
                if (result.zeroCostImbalanced()) {
                    zeroCostImbalancedGroupCount++;
                } else {
                    routeCostRatioLowerBounds.add(
                            result.routeCostRatioLowerBoundBasisPoints());
                    routeCostRatioUpperBounds.add(
                            result.routeCostRatioUpperBoundBasisPoints());
                }
                FamilyClass familyClass = familyClass(decision, alternatives);
                switch (familyClass) {
                    case SAME_FAMILY -> mutable.sameFamilyAlternativeGroupCount++;
                    case CROSS_FAMILY -> mutable.crossFamilyAlternativeGroupCount++;
                    case UNCLASSIFIED -> mutable.unclassifiedAlternativeGroupCount++;
                }
            }
        }

        List<PhaseSummary> phases = java.util.Arrays.stream(Phase.values())
                .map(phase -> phaseEvidence.get(phase).freeze(phase))
                .toList();
        List<BranchEntrySummary> branchEntries = branchEntries(
                matched, groups, indexById, ancestry);
        IntDistribution branchRedundancy = IntDistribution.of(branchEntries.stream()
                .map(BranchEntrySummary::redundantEntranceCount).toList());
        IntDistribution branchOverlap = IntDistribution.of(branchEntries.stream()
                .map(BranchEntrySummary::mandatoryAncestryOverlapBasisPoints).toList());
        IntDistribution chains = singleRouteChains(members, groups);

        Set<ResourceLocation> referenced = graph.edges().stream()
                .filter(edge -> memberSet.contains(edge.prerequisiteId())
                        && memberSet.contains(edge.dependentId()))
                .map(ResearchTreeGraph.Edge::prerequisiteId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int maximumFiniteIncome = pointIncome == null
                ? 0 : pointIncome.maximumFinitePoints();
        List<TerminalRoute> terminalRoutes = members.stream()
                .filter(id -> !referenced.contains(id))
                .map(id -> new TerminalRoute(
                        id,
                        routes.get(id).lowerBound(),
                        routes.get(id).upperBound(),
                        routes.get(id).exact(),
                        affordability(routes.get(id), maximumFiniteIncome)))
                .toList();
        int unaffordableTerminalCount = Math.toIntExact(terminalRoutes.stream()
                .filter(value -> value.affordability() == Affordability.UNAFFORDABLE)
                .count());
        int indeterminateTerminalCount = Math.toIntExact(terminalRoutes.stream()
                .filter(value -> value.affordability() == Affordability.INDETERMINATE)
                .count());
        int approximateTerminalCount = Math.toIntExact(terminalRoutes.stream()
                .filter(value -> !value.exact()).count());

        List<Warning> warnings = new ArrayList<>();
        addWarning(warnings, WarningCode.UNMATCHED_AUTOMATIC_AUTHORITY,
                unmatchedAutomaticTargetCount);
        addWarning(warnings, WarningCode.INEFFECTIVE_ALTERNATIVE_GROUP,
                alternativeGroupCount - effectiveAlternativeGroupCount);
        addWarning(warnings, WarningCode.DEPENDENT_ALTERNATIVES,
                dependentAlternativePairCount);
        addWarning(warnings, WarningCode.SINGLE_BRANCH_ENTRANCE,
                Math.toIntExact(branchEntries.stream()
                        .filter(value -> value.distinctEntranceCount() < 2).count()));
        addWarning(warnings, WarningCode.UNAFFORDABLE_TERMINAL,
                unaffordableTerminalCount);
        addWarning(warnings, WarningCode.INDETERMINATE_TERMINAL_COST,
                indeterminateTerminalCount);
        addWarning(warnings, WarningCode.APPROXIMATE_AUTHORED_ROUTE_COST,
                approximateTerminalCount);

        String interpretation = automaticDiagnostics.prerequisiteStrategy()
                == PrerequisiteStrategy.HYBRID_ROUTES_V1
                        ? HYBRID_INTERPRETATION : GROUPED_INTERPRETATION;
        return new Audit(
                true,
                interpretation,
                members.size(),
                automaticTargetCount,
                matched.size(),
                unmatchedAutomaticTargetCount,
                alternativeGroupCount,
                effectiveAlternativeGroupCount,
                new AlternativeEvidence(
                        alternativeGroupCount,
                        effectiveAlternativeGroupCount,
                        dependentAlternativePairCount,
                        exactRouteCostGroupCount,
                        zeroCostImbalancedGroupCount,
                        IntDistribution.of(alternativeOverlap),
                        IntDistribution.of(alternativeDivergence),
                        LongDistribution.of(routeCostRatioLowerBounds),
                        LongDistribution.of(routeCostRatioUpperBounds)),
                IntDistribution.of(mandatoryShares),
                chains,
                phases,
                branchEntries,
                branchRedundancy,
                branchOverlap,
                maximumFiniteIncome,
                terminalRoutes,
                unaffordableTerminalCount,
                indeterminateTerminalCount,
                List.copyOf(warnings));
    }

    private static List<ResourceLocation> topologicalOrder(
            List<ResourceLocation> members,
            Set<ResourceLocation> memberSet,
            List<ResearchTreeGraph.Edge> edges) {
        Map<ResourceLocation, Integer> indegree = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new LinkedHashMap<>();
        members.forEach(id -> {
            indegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        });
        for (ResearchTreeGraph.Edge edge : edges) {
            if (!memberSet.contains(edge.prerequisiteId())
                    || !memberSet.contains(edge.dependentId())) {
                continue;
            }
            indegree.compute(edge.dependentId(),
                    (ignored, value) -> Math.addExact(value, 1));
            dependents.get(edge.prerequisiteId()).add(edge.dependentId());
        }
        PriorityQueue<ResourceLocation> ready = new PriorityQueue<>(ID_ORDER);
        indegree.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        List<ResourceLocation> result = new ArrayList<>(members.size());
        while (!ready.isEmpty()) {
            ResourceLocation node = ready.remove();
            result.add(node);
            dependents.get(node).stream().sorted(ID_ORDER).forEach(dependent -> {
                int remaining = indegree.compute(
                        dependent, (ignored, value) -> Math.subtractExact(value, 1));
                if (remaining == 0) {
                    ready.add(dependent);
                }
            });
        }
        if (result.size() != members.size()) {
            throw new IllegalArgumentException(
                    "Grouped-route quality topology contains a cycle");
        }
        return List.copyOf(result);
    }

    private static Ancestry ancestry(
            List<ResourceLocation> topological,
            Map<ResourceLocation, Integer> indexById,
            Map<ResourceLocation, List<List<ResourceLocation>>> groups) {
        BitSet[] mandatory = new BitSet[indexById.size()];
        BitSet[] conservative = new BitSet[indexById.size()];
        for (ResourceLocation node : topological) {
            BitSet nodeMandatory = new BitSet(indexById.size());
            BitSet nodeConservative = new BitSet(indexById.size());
            for (List<ResourceLocation> alternatives : groups.get(node)) {
                BitSet groupMandatory = null;
                BitSet groupConservative = new BitSet(indexById.size());
                for (ResourceLocation alternative : alternatives) {
                    int alternativeIndex = indexById.get(alternative);
                    BitSet route = (BitSet) mandatory[alternativeIndex].clone();
                    route.set(alternativeIndex);
                    if (groupMandatory == null) {
                        groupMandatory = (BitSet) route.clone();
                    } else {
                        groupMandatory.and(route);
                    }
                    BitSet possible = (BitSet) conservative[alternativeIndex].clone();
                    possible.set(alternativeIndex);
                    groupConservative.or(possible);
                }
                if (groupMandatory != null) {
                    nodeMandatory.or(groupMandatory);
                }
                nodeConservative.or(groupConservative);
            }
            int nodeIndex = indexById.get(node);
            mandatory[nodeIndex] = nodeMandatory;
            conservative[nodeIndex] = nodeConservative;
        }
        return new Ancestry(mandatory, conservative);
    }

    /**
     * Produces exact minimum costs while the traversed ancestry retains the
     * generated one-group shape. Authored multi-group nodes propagate safe
     * lower/upper bounds instead of pretending a greedy union is globally
     * optimal in the presence of shared ancestry.
     */
    private static Map<ResourceLocation, RouteBound> routeBounds(
            List<ResourceLocation> topological,
            ResearchTreeGraph graph,
            Map<ResourceLocation, List<List<ResourceLocation>>> groups) {
        Map<ResourceLocation, RouteBound> result = new LinkedHashMap<>();
        for (ResourceLocation node : topological) {
            long ownCost = graph.node(node).orElseThrow().pointCost();
            List<List<ResourceLocation>> requirements = groups.get(node);
            if (requirements.isEmpty()) {
                result.put(node, new RouteBound(ownCost, ownCost, true));
                continue;
            }
            long lowerParentCost = 0L;
            long upperParentCost = 0L;
            for (List<ResourceLocation> alternatives : requirements) {
                long minimumLower = alternatives.stream()
                        .map(result::get)
                        .mapToLong(RouteBound::lowerBound)
                        .min().orElse(0L);
                long minimumUpper = alternatives.stream()
                        .map(result::get)
                        .mapToLong(RouteBound::upperBound)
                        .min().orElse(0L);
                lowerParentCost = Math.max(lowerParentCost, minimumLower);
                upperParentCost = saturatedAdd(upperParentCost, minimumUpper);
            }
            long lower = saturatedAdd(ownCost, lowerParentCost);
            long upper = saturatedAdd(ownCost, upperParentCost);
            result.put(node, new RouteBound(
                    lower,
                    upper,
                    lower == upper));
        }
        return Map.copyOf(result);
    }

    private static AlternativeResult alternativeResult(
            List<ResourceLocation> alternatives,
            Map<ResourceLocation, Integer> indexById,
            Ancestry ancestry,
            Map<ResourceLocation, RouteBound> routes) {
        BitSet intersection = null;
        BitSet union = new BitSet(indexById.size());
        int dependentPairs = 0;
        long minimumLowerCost = Long.MAX_VALUE;
        long maximumLowerCost = 0L;
        long minimumUpperCost = Long.MAX_VALUE;
        long maximumUpperCost = 0L;
        boolean routeCostExact = true;
        for (int left = 0; left < alternatives.size(); left++) {
            ResourceLocation alternative = alternatives.get(left);
            int index = indexById.get(alternative);
            BitSet route = (BitSet) ancestry.mandatory()[index].clone();
            route.set(index);
            union.or(route);
            if (intersection == null) {
                intersection = (BitSet) route.clone();
            } else {
                intersection.and(route);
            }
            RouteBound bound = routes.get(alternative);
            minimumLowerCost = Math.min(minimumLowerCost, bound.lowerBound());
            maximumLowerCost = Math.max(maximumLowerCost, bound.lowerBound());
            minimumUpperCost = Math.min(minimumUpperCost, bound.upperBound());
            maximumUpperCost = Math.max(maximumUpperCost, bound.upperBound());
            routeCostExact &= bound.exact();
            for (int right = left + 1; right < alternatives.size(); right++) {
                int rightIndex = indexById.get(alternatives.get(right));
                if (ancestry.conservative()[index].get(rightIndex)
                        || ancestry.conservative()[rightIndex].get(index)) {
                    dependentPairs++;
                }
            }
        }
        int shared = intersection == null ? 0 : intersection.cardinality();
        int overlap = basisPoints(shared, union.cardinality());
        boolean zeroCostImbalanced = minimumLowerCost == 0L
                && maximumUpperCost > 0L;
        long lowerRatio = zeroCostImbalanced
                ? 0L : ratioBasisPoints(maximumLowerCost, minimumUpperCost);
        long upperRatio = zeroCostImbalanced
                ? 0L : ratioBasisPoints(maximumUpperCost, minimumLowerCost);
        return new AlternativeResult(
                dependentPairs == 0 && union.cardinality() > shared,
                dependentPairs,
                overlap,
                10_000 - overlap,
                routeCostExact,
                zeroCostImbalanced,
                lowerRatio,
                upperRatio);
    }

    private static List<BranchEntrySummary> branchEntries(
            Map<ResourceLocation, AutomaticWeaponPrerequisiteDecision> matched,
            Map<ResourceLocation, List<List<ResourceLocation>>> groups,
            Map<ResourceLocation, Integer> indexById,
            Ancestry ancestry) {
        Map<Integer, List<ResourceLocation>> targetsByBranch = new java.util.TreeMap<>();
        Map<Integer, Integer> entryRankByBranch = new java.util.TreeMap<>();
        matched.values().forEach(decision -> {
            if (decision.branchIndex().isPresent()
                    && decision.rankIndex() >= decision.familyStartIndex()) {
                entryRankByBranch.merge(
                        decision.branchIndex().orElseThrow(),
                        decision.rankIndex(),
                        Math::min);
            }
        });
        matched.forEach((target, decision) -> decision.branchIndex().ifPresent(branch -> {
            Integer entryRank = entryRankByBranch.get(branch);
            if (entryRank != null && decision.rankIndex() == entryRank) {
                targetsByBranch.computeIfAbsent(
                        branch, ignored -> new ArrayList<>()).add(target);
            }
        }));
        List<BranchEntrySummary> result = new ArrayList<>();
        targetsByBranch.forEach((branch, targets) -> {
            List<ResourceLocation> entrances = targets.stream()
                    .sorted(ID_ORDER)
                    .flatMap(target -> groups.get(target).stream())
                    .flatMap(List::stream)
                    .distinct()
                    .sorted(ID_ORDER)
                    .toList();
            BitSet intersection = null;
            BitSet union = new BitSet(indexById.size());
            for (ResourceLocation entrance : entrances) {
                int index = indexById.get(entrance);
                BitSet route = (BitSet) ancestry.mandatory()[index].clone();
                route.set(index);
                union.or(route);
                if (intersection == null) {
                    intersection = (BitSet) route.clone();
                } else {
                    intersection.and(route);
                }
            }
            int shared = intersection == null ? 0 : intersection.cardinality();
            int alternativeGroups = 0;
            int effectiveGroups = 0;
            for (ResourceLocation target : targets) {
                for (List<ResourceLocation> alternatives : groups.get(target)) {
                    if (alternatives.size() > 1) {
                        alternativeGroups++;
                        if (structurallyEffective(
                                alternatives, indexById, ancestry)) {
                            effectiveGroups++;
                        }
                    }
                }
            }
            result.add(new BranchEntrySummary(
                    branch,
                    targets.size(),
                    entrances.size(),
                    Math.max(0, entrances.size() - 1),
                    alternativeGroups,
                    effectiveGroups,
                    basisPoints(shared, union.cardinality())));
        });
        return List.copyOf(result);
    }

    private static boolean structurallyEffective(
            List<ResourceLocation> alternatives,
            Map<ResourceLocation, Integer> indexById,
            Ancestry ancestry) {
        BitSet intersection = null;
        BitSet union = new BitSet(indexById.size());
        for (int left = 0; left < alternatives.size(); left++) {
            int leftIndex = indexById.get(alternatives.get(left));
            BitSet route = (BitSet) ancestry.mandatory()[leftIndex].clone();
            route.set(leftIndex);
            union.or(route);
            if (intersection == null) {
                intersection = (BitSet) route.clone();
            } else {
                intersection.and(route);
            }
            for (int right = left + 1; right < alternatives.size(); right++) {
                int rightIndex = indexById.get(alternatives.get(right));
                if (ancestry.conservative()[leftIndex].get(rightIndex)
                        || ancestry.conservative()[rightIndex].get(leftIndex)) {
                    return false;
                }
            }
        }
        return intersection != null && union.cardinality() > intersection.cardinality();
    }

    private static IntDistribution singleRouteChains(
            List<ResourceLocation> members,
            Map<ResourceLocation, List<List<ResourceLocation>>> groups) {
        Map<ResourceLocation, List<ResourceLocation>> occurrences = new LinkedHashMap<>();
        members.forEach(id -> occurrences.put(id, new ArrayList<>()));
        groups.forEach((dependent, requirements) -> requirements.forEach(alternatives ->
                alternatives.forEach(parent -> occurrences.get(parent).add(dependent))));
        Map<ResourceLocation, ResourceLocation> previous = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> next = new LinkedHashMap<>();
        for (ResourceLocation dependent : members) {
            List<List<ResourceLocation>> requirements = groups.get(dependent);
            if (requirements.size() != 1 || requirements.get(0).size() != 1) {
                continue;
            }
            ResourceLocation parent = requirements.get(0).get(0);
            if (occurrences.get(parent).size() == 1) {
                previous.put(dependent, parent);
                next.put(parent, dependent);
            }
        }
        List<Integer> lengths = new ArrayList<>();
        for (ResourceLocation start : members) {
            if (!next.containsKey(start) || previous.containsKey(start)) {
                continue;
            }
            int length = 0;
            ResourceLocation current = start;
            while (next.containsKey(current)) {
                length++;
                current = next.get(current);
            }
            lengths.add(length);
        }
        return IntDistribution.of(lengths);
    }

    private static FamilyClass familyClass(
            AutomaticWeaponPrerequisiteDecision decision,
            List<ResourceLocation> alternatives) {
        List<AutomaticWeaponPrerequisiteDecision.ParentRelation> relations =
                alternatives.stream()
                        .map(decision.selectedParentRelations()::get)
                        .toList();
        if (relations.stream().allMatch(value -> value != null && value.sameFamily())) {
            return FamilyClass.SAME_FAMILY;
        }
        if (relations.stream().anyMatch(value -> value != null && value.crossFamily())) {
            return FamilyClass.CROSS_FAMILY;
        }
        return FamilyClass.UNCLASSIFIED;
    }

    private static Affordability affordability(RouteBound route, int income) {
        if (route.upperBound() <= income) {
            return Affordability.AFFORDABLE;
        }
        if (route.lowerBound() > income) {
            return Affordability.UNAFFORDABLE;
        }
        return Affordability.INDETERMINATE;
    }

    private static void addWarning(
            List<Warning> warnings,
            WarningCode code,
            int occurrences) {
        if (occurrences > 0) {
            warnings.add(new Warning(code, occurrences));
        }
    }

    private static int basisPoints(long numerator, long denominator) {
        if (denominator == 0L) {
            return numerator == 0L ? 10_000 : 0;
        }
        return Math.toIntExact(Math.min(
                10_000L,
                Math.round(numerator * 10_000.0 / denominator)));
    }

    private static long ratioBasisPoints(long maximum, long minimum) {
        if (minimum == 0L) {
            return maximum == 0L ? 10_000L : Long.MAX_VALUE;
        }
        if (maximum > Long.MAX_VALUE / 10_000L) {
            return Long.MAX_VALUE;
        }
        return Math.max(10_000L, maximum * 10_000L / minimum);
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public enum Phase {
        FOUNDATION("foundation"),
        SHARED_TRUNK("shared_trunk"),
        TRANSITION("transition"),
        SPECIALIZATION("specialization");

        private final String serializedName;

        Phase(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static Phase from(AutomaticWeaponPrerequisiteDecision.Strategy strategy) {
            return switch (strategy) {
                case FOUNDATION -> FOUNDATION;
                case SHARED_TRUNK -> SHARED_TRUNK;
                case TRANSITION_CROSS_FAMILY, TRANSITION_LOCAL -> TRANSITION;
                case SPECIALIZATION -> SPECIALIZATION;
            };
        }
    }

    public enum Affordability {
        AFFORDABLE("affordable"),
        UNAFFORDABLE("unaffordable"),
        INDETERMINATE("indeterminate");

        private final String serializedName;

        Affordability(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum WarningCode {
        UNMATCHED_AUTOMATIC_AUTHORITY("unmatched_automatic_authority"),
        INEFFECTIVE_ALTERNATIVE_GROUP("ineffective_alternative_group"),
        DEPENDENT_ALTERNATIVES("dependent_alternatives"),
        SINGLE_BRANCH_ENTRANCE("single_branch_entrance"),
        UNAFFORDABLE_TERMINAL("unaffordable_terminal"),
        INDETERMINATE_TERMINAL_COST("indeterminate_terminal_cost"),
        APPROXIMATE_AUTHORED_ROUTE_COST("approximate_authored_route_cost");

        private final String serializedName;

        WarningCode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public record Audit(
            boolean available,
            String interpretation,
            int weaponNodeCount,
            int automaticTargetCount,
            int matchedAutomaticTargetCount,
            int unmatchedAutomaticTargetCount,
            int alternativeGroupCount,
            int effectiveAlternativeGroupCount,
            AlternativeEvidence alternatives,
            IntDistribution mandatoryAncestorSharesBasisPoints,
            IntDistribution singleRouteChainLengths,
            List<PhaseSummary> phases,
            List<BranchEntrySummary> branchEntries,
            IntDistribution branchEntryRedundancy,
            IntDistribution branchEntryAncestryOverlapBasisPoints,
            int maximumFinitePointIncome,
            List<TerminalRoute> terminalRoutes,
            int unaffordableTerminalCount,
            int indeterminateTerminalCount,
            List<Warning> warnings) {
        public static final Audit EMPTY = new Audit(
                false,
                INTERPRETATION,
                0, 0, 0, 0, 0, 0,
                AlternativeEvidence.EMPTY,
                IntDistribution.EMPTY,
                IntDistribution.EMPTY,
                List.of(),
                List.of(),
                IntDistribution.EMPTY,
                IntDistribution.EMPTY,
                0,
                List.of(),
                0,
                0,
                List.of());

        public Audit {
            alternatives = alternatives == null
                    ? AlternativeEvidence.EMPTY : alternatives;
            mandatoryAncestorSharesBasisPoints = mandatoryAncestorSharesBasisPoints == null
                    ? IntDistribution.EMPTY : mandatoryAncestorSharesBasisPoints;
            singleRouteChainLengths = singleRouteChainLengths == null
                    ? IntDistribution.EMPTY : singleRouteChainLengths;
            phases = phases == null ? List.of() : List.copyOf(phases);
            branchEntries = branchEntries == null ? List.of() : List.copyOf(branchEntries);
            branchEntryRedundancy = branchEntryRedundancy == null
                    ? IntDistribution.EMPTY : branchEntryRedundancy;
            branchEntryAncestryOverlapBasisPoints =
                    branchEntryAncestryOverlapBasisPoints == null
                            ? IntDistribution.EMPTY
                            : branchEntryAncestryOverlapBasisPoints;
            terminalRoutes = terminalRoutes == null ? List.of() : List.copyOf(terminalRoutes);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            if (!GROUPED_INTERPRETATION.equals(interpretation)
                            && !HYBRID_INTERPRETATION.equals(interpretation)
                    || weaponNodeCount < 0 || automaticTargetCount < 0
                    || matchedAutomaticTargetCount < 0
                    || unmatchedAutomaticTargetCount < 0
                    || matchedAutomaticTargetCount + unmatchedAutomaticTargetCount
                            != automaticTargetCount
                    || alternativeGroupCount < 0
                    || effectiveAlternativeGroupCount < 0
                    || effectiveAlternativeGroupCount > alternativeGroupCount
                    || alternatives.groupCount() != alternativeGroupCount
                    || alternatives.effectiveGroupCount()
                            != effectiveAlternativeGroupCount
                    || maximumFinitePointIncome < 0
                    || unaffordableTerminalCount < 0
                    || indeterminateTerminalCount < 0
                    || unaffordableTerminalCount + indeterminateTerminalCount
                            > terminalRoutes.size()
                    || phases.stream().anyMatch(java.util.Objects::isNull)
                    || branchEntries.stream().anyMatch(java.util.Objects::isNull)
                    || terminalRoutes.stream().anyMatch(java.util.Objects::isNull)
                    || warnings.stream().anyMatch(java.util.Objects::isNull)
                    || available && phases.size() != Phase.values().length
                    || available && phases.stream().mapToInt(PhaseSummary::targetCount).sum()
                            != matchedAutomaticTargetCount
                    || available && phases.stream()
                            .mapToInt(PhaseSummary::alternativeGroupCount).sum()
                            != alternativeGroupCount
                    || warnings.stream().map(Warning::code).distinct().count()
                            != warnings.size()
                    || !available && (weaponNodeCount != 0 || automaticTargetCount != 0
                            || alternativeGroupCount != 0 || !phases.isEmpty()
                            || !branchEntries.isEmpty() || !terminalRoutes.isEmpty()
                            || !warnings.isEmpty())) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route quality audit");
            }
        }

        public int affordableTerminalCount() {
            return terminalRoutes.size()
                    - unaffordableTerminalCount - indeterminateTerminalCount;
        }

        public int warningOccurrenceCount() {
            return warnings.stream().mapToInt(Warning::occurrenceCount).sum();
        }
    }

    public record AlternativeEvidence(
            int groupCount,
            int effectiveGroupCount,
            int dependentAlternativePairCount,
            int exactRouteCostGroupCount,
            int zeroCostImbalancedGroupCount,
            IntDistribution mandatoryAncestryOverlapBasisPoints,
            IntDistribution ancestryDivergenceBasisPoints,
            LongDistribution routeCostRatioLowerBoundBasisPoints,
            LongDistribution routeCostRatioUpperBoundBasisPoints) {
        public static final AlternativeEvidence EMPTY = new AlternativeEvidence(
                0, 0, 0, 0, 0,
                IntDistribution.EMPTY,
                IntDistribution.EMPTY,
                LongDistribution.EMPTY,
                LongDistribution.EMPTY);

        public AlternativeEvidence {
            mandatoryAncestryOverlapBasisPoints =
                    mandatoryAncestryOverlapBasisPoints == null
                            ? IntDistribution.EMPTY
                            : mandatoryAncestryOverlapBasisPoints;
            ancestryDivergenceBasisPoints = ancestryDivergenceBasisPoints == null
                    ? IntDistribution.EMPTY : ancestryDivergenceBasisPoints;
            routeCostRatioLowerBoundBasisPoints =
                    routeCostRatioLowerBoundBasisPoints == null
                            ? LongDistribution.EMPTY
                            : routeCostRatioLowerBoundBasisPoints;
            routeCostRatioUpperBoundBasisPoints =
                    routeCostRatioUpperBoundBasisPoints == null
                            ? LongDistribution.EMPTY
                            : routeCostRatioUpperBoundBasisPoints;
            if (groupCount < 0 || effectiveGroupCount < 0
                    || effectiveGroupCount > groupCount
                    || dependentAlternativePairCount < 0
                    || exactRouteCostGroupCount < 0
                    || exactRouteCostGroupCount > groupCount
                    || zeroCostImbalancedGroupCount < 0
                    || zeroCostImbalancedGroupCount > groupCount
                    || mandatoryAncestryOverlapBasisPoints.sampleCount() != groupCount
                    || ancestryDivergenceBasisPoints.sampleCount() != groupCount
                    || routeCostRatioLowerBoundBasisPoints.sampleCount()
                            + zeroCostImbalancedGroupCount != groupCount
                    || routeCostRatioUpperBoundBasisPoints.sampleCount()
                            + zeroCostImbalancedGroupCount != groupCount) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route alternative evidence");
            }
        }
    }

    public record PhaseSummary(
            Phase phase,
            int targetCount,
            int alternativeGroupCount,
            int effectiveAlternativeGroupCount,
            int sameFamilyAlternativeGroupCount,
            int crossFamilyAlternativeGroupCount,
            int unclassifiedAlternativeGroupCount,
            IntDistribution parentFanOut,
            IntDistribution mandatoryAncestorSharesBasisPoints) {
        public PhaseSummary {
            parentFanOut = parentFanOut == null ? IntDistribution.EMPTY : parentFanOut;
            mandatoryAncestorSharesBasisPoints = mandatoryAncestorSharesBasisPoints == null
                    ? IntDistribution.EMPTY : mandatoryAncestorSharesBasisPoints;
            if (phase == null || targetCount < 0 || alternativeGroupCount < 0
                    || effectiveAlternativeGroupCount < 0
                    || effectiveAlternativeGroupCount > alternativeGroupCount
                    || sameFamilyAlternativeGroupCount < 0
                    || crossFamilyAlternativeGroupCount < 0
                    || unclassifiedAlternativeGroupCount < 0
                    || sameFamilyAlternativeGroupCount
                            + crossFamilyAlternativeGroupCount
                            + unclassifiedAlternativeGroupCount
                            != alternativeGroupCount) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route phase summary");
            }
        }

        public int alternativeDensityBasisPoints() {
            return targetCount == 0
                    ? 0 : basisPoints(alternativeGroupCount, targetCount);
        }

        public int sameFamilyDensityBasisPoints() {
            return targetCount == 0
                    ? 0 : basisPoints(sameFamilyAlternativeGroupCount, targetCount);
        }

        public int crossFamilyDensityBasisPoints() {
            return targetCount == 0
                    ? 0 : basisPoints(crossFamilyAlternativeGroupCount, targetCount);
        }
    }

    public record BranchEntrySummary(
            int branchIndex,
            int targetCount,
            int distinctEntranceCount,
            int redundantEntranceCount,
            int alternativeGroupCount,
            int effectiveAlternativeGroupCount,
            int mandatoryAncestryOverlapBasisPoints) {
        public BranchEntrySummary {
            if (branchIndex < 0 || targetCount < 1 || distinctEntranceCount < 0
                    || redundantEntranceCount != Math.max(0, distinctEntranceCount - 1)
                    || alternativeGroupCount < 0 || effectiveAlternativeGroupCount < 0
                    || effectiveAlternativeGroupCount > alternativeGroupCount
                    || mandatoryAncestryOverlapBasisPoints < 0
                    || mandatoryAncestryOverlapBasisPoints > 10_000) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route branch-entry summary");
            }
        }
    }

    public record TerminalRoute(
            ResourceLocation terminalId,
            long minimumRouteLowerBound,
            long minimumRouteUpperBound,
            boolean exact,
            Affordability affordability) {
        public TerminalRoute {
            if (terminalId == null || minimumRouteLowerBound < 0L
                    || minimumRouteUpperBound < minimumRouteLowerBound
                    || exact != (minimumRouteLowerBound == minimumRouteUpperBound)
                    || affordability == null) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route terminal route");
            }
        }
    }

    public record Warning(WarningCode code, int occurrenceCount) {
        public Warning {
            if (code == null || occurrenceCount < 1) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route warning");
            }
        }
    }

    public record IntDistribution(
            int sampleCount,
            int minimum,
            int median,
            int percentile90,
            int percentile95,
            int maximum) {
        public static final IntDistribution EMPTY =
                new IntDistribution(0, 0, 0, 0, 0, 0);

        public IntDistribution {
            if (sampleCount < 0 || minimum < 0 || minimum > median
                    || median > percentile90 || percentile90 > percentile95
                    || percentile95 > maximum
                    || sampleCount == 0 && maximum != 0) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route integer distribution");
            }
        }

        public static IntDistribution of(java.util.Collection<Integer> values) {
            if (values == null
                    || values.stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException(
                        "Grouped-route integer distribution contains an invalid value");
            }
            if (values.isEmpty()) {
                return EMPTY;
            }
            List<Integer> sorted = values.stream().sorted().toList();
            return new IntDistribution(
                    sorted.size(),
                    sorted.get(0),
                    percentile(sorted, 50),
                    percentile(sorted, 90),
                    percentile(sorted, 95),
                    sorted.get(sorted.size() - 1));
        }

        private static int percentile(List<Integer> sorted, int percentile) {
            int index = Math.max(0, Math.toIntExact(
                    (long) Math.ceil(percentile * sorted.size() / 100.0) - 1L));
            return sorted.get(Math.min(sorted.size() - 1, index));
        }
    }

    public record LongDistribution(
            int sampleCount,
            long minimum,
            long median,
            long percentile90,
            long percentile95,
            long maximum) {
        public static final LongDistribution EMPTY =
                new LongDistribution(0, 0L, 0L, 0L, 0L, 0L);

        public LongDistribution {
            if (sampleCount < 0 || minimum < 0L || minimum > median
                    || median > percentile90 || percentile90 > percentile95
                    || percentile95 > maximum
                    || sampleCount == 0 && maximum != 0L) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route long distribution");
            }
        }

        public static LongDistribution of(java.util.Collection<Long> values) {
            if (values == null
                    || values.stream().anyMatch(value -> value == null || value < 0L)) {
                throw new IllegalArgumentException(
                        "Grouped-route long distribution contains an invalid value");
            }
            if (values.isEmpty()) {
                return EMPTY;
            }
            List<Long> sorted = values.stream().sorted().toList();
            return new LongDistribution(
                    sorted.size(),
                    sorted.get(0),
                    percentile(sorted, 50),
                    percentile(sorted, 90),
                    percentile(sorted, 95),
                    sorted.get(sorted.size() - 1));
        }

        private static long percentile(List<Long> sorted, int percentile) {
            int index = Math.max(0, Math.toIntExact(
                    (long) Math.ceil(percentile * sorted.size() / 100.0) - 1L));
            return sorted.get(Math.min(sorted.size() - 1, index));
        }
    }

    private enum FamilyClass {
        SAME_FAMILY,
        CROSS_FAMILY,
        UNCLASSIFIED
    }

    private record Ancestry(BitSet[] mandatory, BitSet[] conservative) {
    }

    private record RouteBound(long lowerBound, long upperBound, boolean exact) {
    }

    private record AlternativeResult(
            boolean effective,
            int dependentAlternativePairCount,
            int mandatoryAncestryOverlapBasisPoints,
            int ancestryDivergenceBasisPoints,
            boolean routeCostExact,
            boolean zeroCostImbalanced,
            long routeCostRatioLowerBoundBasisPoints,
            long routeCostRatioUpperBoundBasisPoints) {
    }

    private static final class MutablePhase {
        private int targetCount;
        private int alternativeGroupCount;
        private int effectiveAlternativeGroupCount;
        private int sameFamilyAlternativeGroupCount;
        private int crossFamilyAlternativeGroupCount;
        private int unclassifiedAlternativeGroupCount;
        private final Map<ResourceLocation, Integer> parentReferences =
                new LinkedHashMap<>();
        private final List<Integer> mandatoryAncestorShares = new ArrayList<>();

        private PhaseSummary freeze(Phase phase) {
            return new PhaseSummary(
                    phase,
                    targetCount,
                    alternativeGroupCount,
                    effectiveAlternativeGroupCount,
                    sameFamilyAlternativeGroupCount,
                    crossFamilyAlternativeGroupCount,
                    unclassifiedAlternativeGroupCount,
                    IntDistribution.of(parentReferences.values()),
                    IntDistribution.of(mandatoryAncestorShares));
        }
    }
}
