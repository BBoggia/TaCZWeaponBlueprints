package com.gamergaming.taczweaponblueprints.research.tree;

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
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisiteDecision;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;

import net.minecraft.resources.ResourceLocation;

/**
 * Read-only Phase-0 evidence for interpreting existing generated multi-parent
 * selections as one hypothetical any-of group. This audit never feeds a route,
 * score, parent, rank, or cost back into research authority.
 */
public final class ResearchGroupedRouteBaselineAudit {
    public static final String INTERPRETATION = "generated_multi_parent_any_of";

    private ResearchGroupedRouteBaselineAudit() {
    }

    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics) {
        return audit(
                graph,
                presentation,
                automaticDiagnostics,
                ResearchPointAwardEconomyProjection.Projection.EMPTY);
    }

    /**
     * Audits the fully disclosed operator graph. Published edges remain the
     * current mandatory AND authority; only the returned estimate treats a
     * matched generated multi-parent set as one OR group.
     */
    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ResearchPointAwardEconomyProjection.Projection pointIncome) {
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException(
                    "Grouped-route baseline inputs cannot be null");
        }
        if (!presentation.available() || automaticDiagnostics == null
                || automaticDiagnostics.prerequisiteStrategy()
                        != PrerequisiteStrategy.LEGACY_AND) {
            return Audit.EMPTY;
        }
        presentation.validateAgainst(graph);
        Optional<ResearchTechTreePresentation.DomainView> weapons =
                presentation.domain(Domain.WEAPONS);
        if (weapons.isEmpty()) {
            return Audit.EMPTY;
        }
        ResearchPointAwardEconomyProjection.Projection stableIncome = pointIncome == null
                ? ResearchPointAwardEconomyProjection.Projection.EMPTY : pointIncome;

        Set<ResourceLocation> members = weapons.orElseThrow().lanes().stream()
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<ResourceLocation, List<ResourceLocation>> actualParents = new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> dependents = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> costs = new LinkedHashMap<>();
        members.stream().sorted(idOrder()).forEach(id -> {
            actualParents.put(id, new ArrayList<>());
            dependents.put(id, new LinkedHashSet<>());
            costs.put(id, graph.node(id).orElseThrow().pointCost());
        });
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            if (members.contains(edge.prerequisiteId())
                    && members.contains(edge.dependentId())) {
                actualParents.get(edge.dependentId()).add(edge.prerequisiteId());
                dependents.get(edge.prerequisiteId()).add(edge.dependentId());
            }
        }
        actualParents.replaceAll((ignored, parents) -> parents.stream()
                .sorted(idOrder()).toList());

        Map<ResourceLocation, List<ResourceLocation>> matchedGenerated =
                new LinkedHashMap<>();
        int automaticTargetCount = 0;
        int generatedReferenceCount = 0;
        int unmatchedGeneratedTargetCount = 0;
        for (AutomaticWeaponPlacementDiagnostics.Entry entry
                : automaticDiagnostics.entries().values()) {
            if (entry.state() != AutomaticWeaponPlacementDiagnostics.State.AUTOMATIC
                    || !members.contains(entry.blueprintId())) {
                continue;
            }
            automaticTargetCount++;
            generatedReferenceCount = Math.addExact(
                    generatedReferenceCount, entry.generatedPrerequisites().size());
            List<ResourceLocation> generated = entry.generatedPrerequisites().stream()
                    .filter(members::contains)
                    .sorted(idOrder())
                    .toList();
            List<ResourceLocation> published = actualParents.get(entry.blueprintId());
            if (generated.size() != entry.generatedPrerequisites().size()
                    || !generated.equals(published)) {
                unmatchedGeneratedTargetCount++;
                continue;
            }
            matchedGenerated.put(entry.blueprintId(), generated);
        }

        Map<ResourceLocation, List<ResourceLocation>> alternativeGroups =
                new LinkedHashMap<>();
        matchedGenerated.forEach((target, parents) -> {
            if (parents.size() > 1) {
                alternativeGroups.put(target, parents);
            }
        });
        int pairGroupCount = Math.toIntExact(alternativeGroups.values().stream()
                .filter(parents -> parents.size() == 2).count());
        int largerGroupCount = Math.subtractExact(
                alternativeGroups.size(), pairGroupCount);
        int maximumAlternativeCount = alternativeGroups.values().stream()
                .mapToInt(List::size).max().orElse(0);

        List<AutomaticWeaponPrerequisiteDecision> decisions = automaticDiagnostics.entries()
                .values().stream()
                .filter(entry -> members.contains(entry.blueprintId()))
                .flatMap(entry -> entry.prerequisiteDecision().stream())
                .toList();
        List<StrategySummary> strategies = java.util.Arrays.stream(
                        AutomaticWeaponPrerequisiteDecision.Strategy.values())
                .map(strategy -> strategySummary(strategy, decisions))
                .toList();
        List<BranchEntrySummary> branchEntries = branchEntries(decisions);
        IntDistribution generatedFanOut = fanOutDistribution(
                matchedGenerated.values().stream().flatMap(List::stream).toList());
        Map<ResourceLocation, Integer> singleParentChainMemo = new LinkedHashMap<>();
        int maximumSingleParentChain = matchedGenerated.keySet().stream()
                .mapToInt(target -> singleParentChain(
                        target,
                        matchedGenerated,
                        singleParentChainMemo,
                        new LinkedHashSet<>()))
                .max().orElse(0);

        AlternativeEvidence alternativeEvidence = alternativeEvidence(
                alternativeGroups, actualParents, costs);
        List<ResourceLocation> leaves = dependents.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(idOrder())
                .toList();
        RouteCostComparison routes = routeCosts(
                leaves,
                actualParents,
                alternativeGroups,
                costs,
                stableIncome.maximumFinitePoints());
        boolean estimateExact = actualParents.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .allMatch(entry -> alternativeGroups.containsKey(entry.getKey()));

        return new Audit(
                true,
                INTERPRETATION,
                members.size(),
                automaticTargetCount,
                matchedGenerated.size(),
                unmatchedGeneratedTargetCount,
                generatedReferenceCount,
                Math.toIntExact(matchedGenerated.values().stream()
                        .filter(parents -> parents.size() == 1).count()),
                alternativeGroups.size(),
                pairGroupCount,
                largerGroupCount,
                maximumAlternativeCount,
                maximumSingleParentChain,
                generatedFanOut,
                strategies,
                branchEntries,
                alternativeEvidence,
                new RouteCostComparison(
                        routes.leafCount(),
                        routes.maximumFinitePointIncome(),
                        routes.currentMandatoryClosureCosts(),
                        routes.counterfactualMinimumRouteEstimates(),
                        routes.counterfactualMaximumRouteEstimates(),
                        routes.currentAffordableLeafCount(),
                        routes.counterfactualAffordableLeafCount(),
                        estimateExact),
                fingerprint(
                        members, actualParents, matchedGenerated, costs,
                        automaticDiagnostics));
    }

    private static StrategySummary strategySummary(
            AutomaticWeaponPrerequisiteDecision.Strategy strategy,
            List<AutomaticWeaponPrerequisiteDecision> decisions) {
        List<AutomaticWeaponPrerequisiteDecision> selected = decisions.stream()
                .filter(decision -> decision.strategy() == strategy)
                .toList();
        List<ResourceLocation> references = selected.stream()
                .flatMap(decision -> decision.selectedParentRelations().keySet().stream())
                .toList();
        return new StrategySummary(
                strategy,
                selected.size(),
                Math.toIntExact(selected.stream()
                        .filter(value -> value.selectedParentRelations().isEmpty()).count()),
                Math.toIntExact(selected.stream()
                        .filter(value -> value.selectedParentRelations().size() == 1).count()),
                Math.toIntExact(selected.stream()
                        .filter(value -> value.selectedParentRelations().size() > 1).count()),
                references.size(),
                selected.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::sameFamilyParentCount).sum(),
                selected.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::crossFamilyParentCount).sum(),
                selected.stream().mapToInt(
                        AutomaticWeaponPrerequisiteDecision::unclassifiedParentCount).sum(),
                Math.toIntExact(selected.stream()
                        .filter(value -> value.branchIndex().isPresent())
                        .filter(value -> value.rankIndex() == value.familyStartIndex())
                        .count()),
                Math.toIntExact(selected.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision::terminalPeer).count()),
                Math.toIntExact(selected.stream()
                        .filter(AutomaticWeaponPrerequisiteDecision
                                ::mergeRejectedForClosureInflation)
                        .count()),
                fanOutDistribution(references));
    }

    private static List<BranchEntrySummary> branchEntries(
            List<AutomaticWeaponPrerequisiteDecision> decisions) {
        Map<Integer, List<AutomaticWeaponPrerequisiteDecision>> byBranch = decisions.stream()
                .filter(value -> value.branchIndex().isPresent())
                .filter(value -> value.rankIndex() == value.familyStartIndex())
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.branchIndex().orElseThrow(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()));
        return byBranch.entrySet().stream().map(entry -> new BranchEntrySummary(
                entry.getKey(),
                entry.getValue().size(),
                Math.toIntExact(entry.getValue().stream()
                        .filter(value -> value.selectedParentRelations().size() == 1).count()),
                Math.toIntExact(entry.getValue().stream()
                        .filter(value -> value.selectedParentRelations().size() > 1).count()),
                entry.getValue().stream()
                        .mapToInt(value -> value.selectedParentRelations().size()).sum()))
                .toList();
    }

    private static AlternativeEvidence alternativeEvidence(
            Map<ResourceLocation, List<ResourceLocation>> alternativeGroups,
            Map<ResourceLocation, List<ResourceLocation>> actualParents,
            Map<ResourceLocation, Integer> costs) {
        List<Integer> sharedAncestry = new ArrayList<>();
        List<Integer> divergence = new ArrayList<>();
        List<Integer> costBalance = new ArrayList<>();
        int dependentParentPairs = 0;
        for (List<ResourceLocation> alternatives : alternativeGroups.values()) {
            List<Set<ResourceLocation>> closures = alternatives.stream()
                    .map(parent -> actualClosure(parent, actualParents))
                    .toList();
            LinkedHashSet<ResourceLocation> union = new LinkedHashSet<>();
            closures.forEach(union::addAll);
            LinkedHashSet<ResourceLocation> intersection = closures.isEmpty()
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(closures.get(0));
            closures.stream().skip(1).forEach(intersection::retainAll);
            sharedAncestry.add(basisPoints(intersection.size(), union.size()));
            divergence.add(basisPoints(
                    Math.subtractExact(union.size(), intersection.size()), union.size()));
            List<Long> closureCosts = closures.stream()
                    .map(closure -> closureCost(closure, costs)).toList();
            long minimumCost = closureCosts.stream().mapToLong(Long::longValue)
                    .min().orElse(0L);
            long maximumCost = closureCosts.stream().mapToLong(Long::longValue)
                    .max().orElse(0L);
            costBalance.add(basisPoints(minimumCost, maximumCost));
            for (int left = 0; left < alternatives.size(); left++) {
                for (int right = left + 1; right < alternatives.size(); right++) {
                    ResourceLocation leftId = alternatives.get(left);
                    ResourceLocation rightId = alternatives.get(right);
                    if (closures.get(left).contains(rightId)
                            || closures.get(right).contains(leftId)) {
                        dependentParentPairs++;
                    }
                }
            }
        }
        return new AlternativeEvidence(
                alternativeGroups.size(),
                dependentParentPairs,
                IntDistribution.of(sharedAncestry),
                IntDistribution.of(divergence),
                IntDistribution.of(costBalance));
    }

    private static RouteCostComparison routeCosts(
            List<ResourceLocation> leaves,
            Map<ResourceLocation, List<ResourceLocation>> actualParents,
            Map<ResourceLocation, List<ResourceLocation>> alternativeGroups,
            Map<ResourceLocation, Integer> costs,
            int maximumFiniteIncome) {
        List<Long> currentCosts = new ArrayList<>();
        List<Long> minimumEstimates = new ArrayList<>();
        List<Long> maximumEstimates = new ArrayList<>();
        Map<ResourceLocation, Long> minimumScoreMemo = new LinkedHashMap<>();
        Map<ResourceLocation, Long> maximumScoreMemo = new LinkedHashMap<>();
        int currentAffordable = 0;
        int counterfactualAffordable = 0;
        for (ResourceLocation leaf : leaves) {
            long current = closureCost(actualClosure(leaf, actualParents), costs);
            long minimum = closureCost(counterfactualClosure(
                    leaf,
                    RouteChoice.MINIMUM,
                    actualParents,
                    alternativeGroups,
                    costs,
                    minimumScoreMemo), costs);
            long maximum = closureCost(counterfactualClosure(
                    leaf,
                    RouteChoice.MAXIMUM,
                    actualParents,
                    alternativeGroups,
                    costs,
                    maximumScoreMemo), costs);
            currentCosts.add(current);
            minimumEstimates.add(minimum);
            maximumEstimates.add(maximum);
            if (current <= maximumFiniteIncome) {
                currentAffordable++;
            }
            if (minimum <= maximumFiniteIncome) {
                counterfactualAffordable++;
            }
        }
        return new RouteCostComparison(
                leaves.size(),
                maximumFiniteIncome,
                LongDistribution.of(currentCosts),
                LongDistribution.of(minimumEstimates),
                LongDistribution.of(maximumEstimates),
                currentAffordable,
                counterfactualAffordable,
                false);
    }

    private static Set<ResourceLocation> actualClosure(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> actualParents) {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        java.util.ArrayDeque<ResourceLocation> pending = new java.util.ArrayDeque<>();
        pending.add(node);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (result.add(current)) {
                pending.addAll(actualParents.getOrDefault(current, List.of()));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<ResourceLocation> counterfactualClosure(
            ResourceLocation node,
            RouteChoice choice,
            Map<ResourceLocation, List<ResourceLocation>> actualParents,
            Map<ResourceLocation, List<ResourceLocation>> alternativeGroups,
            Map<ResourceLocation, Integer> costs,
            Map<ResourceLocation, Long> scoreMemo) {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        java.util.ArrayDeque<ResourceLocation> pending = new java.util.ArrayDeque<>();
        pending.add(node);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            List<ResourceLocation> alternatives = alternativeGroups.get(current);
            if (alternatives == null) {
                pending.addAll(actualParents.getOrDefault(current, List.of()));
                continue;
            }
            ResourceLocation selected = null;
            long selectedScore = 0L;
            for (ResourceLocation alternative : alternatives) {
                long score = routeScore(
                        alternative,
                        choice,
                        actualParents,
                        alternativeGroups,
                        costs,
                        scoreMemo,
                        new LinkedHashSet<>());
                if (selected == null
                        || choice.prefers(score, selectedScore)
                        || score == selectedScore
                                && alternative.toString().compareTo(selected.toString()) < 0) {
                    selected = alternative;
                    selectedScore = score;
                }
            }
            if (selected != null) {
                pending.add(selected);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Selection heuristic for the counterfactual. It is exact for the generated
     * one-group/single-parent shape and deliberately marked approximate when an
     * authored simultaneous merge remains in the graph.
     */
    private static long routeScore(
            ResourceLocation node,
            RouteChoice choice,
            Map<ResourceLocation, List<ResourceLocation>> actualParents,
            Map<ResourceLocation, List<ResourceLocation>> alternativeGroups,
            Map<ResourceLocation, Integer> costs,
            Map<ResourceLocation, Long> memo,
            Set<ResourceLocation> visiting) {
        Long known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Grouped-route counterfactual contains a cycle");
        }
        long parentScore = 0L;
        List<ResourceLocation> alternatives = alternativeGroups.get(node);
        if (alternatives != null) {
            Long selected = null;
            for (ResourceLocation alternative : alternatives) {
                long score = routeScore(
                        alternative,
                        choice,
                        actualParents,
                        alternativeGroups,
                        costs,
                        memo,
                        visiting);
                if (selected == null || choice.prefers(score, selected)) {
                    selected = score;
                }
            }
            parentScore = selected == null ? 0L : selected;
        } else {
            for (ResourceLocation parent : actualParents.getOrDefault(node, List.of())) {
                parentScore = saturatedAdd(
                        parentScore,
                        routeScore(
                                parent,
                                choice,
                                actualParents,
                                alternativeGroups,
                                costs,
                                memo,
                                visiting));
            }
        }
        visiting.remove(node);
        long result = saturatedAdd(costs.getOrDefault(node, 0), parentScore);
        memo.put(node, result);
        return result;
    }

    private static int singleParentChain(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, Integer> memo,
            Set<ResourceLocation> visiting) {
        Integer known = memo.get(node);
        if (known != null) {
            return known;
        }
        List<ResourceLocation> parents = generated.getOrDefault(node, List.of());
        if (parents.size() != 1) {
            memo.put(node, 0);
            return 0;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Generated prerequisite baseline contains a cycle");
        }
        int result = Math.addExact(
                1,
                singleParentChain(
                        parents.get(0), generated, memo, visiting));
        visiting.remove(node);
        memo.put(node, result);
        return result;
    }

    private static IntDistribution fanOutDistribution(
            List<ResourceLocation> parentReferences) {
        Map<ResourceLocation, Integer> fanOut = new LinkedHashMap<>();
        parentReferences.forEach(parent -> fanOut.merge(parent, 1, Math::addExact));
        return IntDistribution.of(fanOut.values());
    }

    private static long closureCost(
            Set<ResourceLocation> closure,
            Map<ResourceLocation, Integer> costs) {
        long result = 0L;
        for (ResourceLocation id : closure) {
            result = Math.addExact(result, costs.getOrDefault(id, 0));
        }
        return result;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static int basisPoints(long numerator, long denominator) {
        if (denominator == 0L) {
            return numerator == 0L ? 10_000 : 0;
        }
        return Math.toIntExact(Math.min(
                10_000L,
                Math.round(numerator * 10_000.0 / denominator)));
    }

    private static String fingerprint(
            Set<ResourceLocation> members,
            Map<ResourceLocation, List<ResourceLocation>> actualParents,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, Integer> costs,
            AutomaticWeaponPlacementDiagnostics diagnostics) {
        String canonical = members.stream().sorted(idOrder()).map(id -> {
            String strategy = diagnostics.entry(id)
                    .flatMap(AutomaticWeaponPlacementDiagnostics.Entry::prerequisiteDecision)
                    .map(value -> value.strategy().serializedName())
                    .orElse("-");
            return id + "@" + costs.getOrDefault(id, 0)
                    + "<-" + join(actualParents.getOrDefault(id, List.of()))
                    + "|generated=" + join(generated.getOrDefault(id, List.of()))
                    + "|strategy=" + strategy;
        }).collect(java.util.stream.Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                result.append(String.format(
                        java.util.Locale.ROOT, "%02x", element & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String join(List<ResourceLocation> ids) {
        return ids.stream().sorted(idOrder()).map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Comparator<ResourceLocation> idOrder() {
        return Comparator.comparing(ResourceLocation::toString);
    }

    private enum RouteChoice {
        MINIMUM {
            @Override
            boolean prefers(long candidate, long selected) {
                return candidate < selected;
            }
        },
        MAXIMUM {
            @Override
            boolean prefers(long candidate, long selected) {
                return candidate > selected;
            }
        };

        abstract boolean prefers(long candidate, long selected);
    }

    public record Audit(
            boolean available,
            String interpretation,
            int weaponNodeCount,
            int automaticTargetCount,
            int matchedGeneratedTargetCount,
            int unmatchedGeneratedTargetCount,
            int generatedReferenceCount,
            int singleParentTargetCount,
            int alternativeGroupCandidateCount,
            int pairGroupCandidateCount,
            int largerGroupCandidateCount,
            int maximumAlternativeCount,
            int maximumSingleParentChain,
            IntDistribution generatedFanOut,
            List<StrategySummary> strategies,
            List<BranchEntrySummary> branchEntries,
            AlternativeEvidence alternativeEvidence,
            RouteCostComparison routeCosts,
            String inputFingerprint) {
        public static final Audit EMPTY = new Audit(
                false,
                INTERPRETATION,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                IntDistribution.EMPTY,
                List.of(),
                List.of(),
                AlternativeEvidence.EMPTY,
                RouteCostComparison.EMPTY,
                "");

        public Audit {
            generatedFanOut = generatedFanOut == null
                    ? IntDistribution.EMPTY : generatedFanOut;
            strategies = strategies == null ? List.of() : List.copyOf(strategies);
            branchEntries = branchEntries == null ? List.of() : List.copyOf(branchEntries);
            alternativeEvidence = alternativeEvidence == null
                    ? AlternativeEvidence.EMPTY : alternativeEvidence;
            routeCosts = routeCosts == null ? RouteCostComparison.EMPTY : routeCosts;
            if (!INTERPRETATION.equals(interpretation)
                    || weaponNodeCount < 0 || automaticTargetCount < 0
                    || automaticTargetCount > weaponNodeCount
                    || matchedGeneratedTargetCount < 0
                    || matchedGeneratedTargetCount > automaticTargetCount
                    || unmatchedGeneratedTargetCount < 0
                    || matchedGeneratedTargetCount + unmatchedGeneratedTargetCount
                            != automaticTargetCount
                    || generatedReferenceCount < 0 || singleParentTargetCount < 0
                    || alternativeGroupCandidateCount < 0
                    || pairGroupCandidateCount < 0 || largerGroupCandidateCount < 0
                    || pairGroupCandidateCount + largerGroupCandidateCount
                            != alternativeGroupCandidateCount
                    || singleParentTargetCount + alternativeGroupCandidateCount
                            > matchedGeneratedTargetCount
                    || maximumAlternativeCount < 0 || maximumSingleParentChain < 0
                    || strategies.stream().anyMatch(java.util.Objects::isNull)
                    || branchEntries.stream().anyMatch(java.util.Objects::isNull)
                    || inputFingerprint == null
                    || available != !inputFingerprint.isEmpty()
                    || available && !inputFingerprint.matches("[0-9a-f]{64}")
                    || !available && (weaponNodeCount != 0 || automaticTargetCount != 0
                            || matchedGeneratedTargetCount != 0
                            || unmatchedGeneratedTargetCount != 0
                            || generatedReferenceCount != 0
                            || singleParentTargetCount != 0
                            || alternativeGroupCandidateCount != 0
                            || pairGroupCandidateCount != 0
                            || largerGroupCandidateCount != 0
                            || maximumAlternativeCount != 0
                            || maximumSingleParentChain != 0
                            || !generatedFanOut.equals(IntDistribution.EMPTY)
                            || !strategies.isEmpty() || !branchEntries.isEmpty()
                            || !alternativeEvidence.equals(AlternativeEvidence.EMPTY)
                            || !routeCosts.equals(RouteCostComparison.EMPTY))) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route baseline audit");
            }
        }
    }

    public record StrategySummary(
            AutomaticWeaponPrerequisiteDecision.Strategy strategy,
            int targetCount,
            int parentlessTargetCount,
            int singleParentTargetCount,
            int multiParentTargetCount,
            int parentReferenceCount,
            int sameFamilyReferenceCount,
            int crossFamilyReferenceCount,
            int unclassifiedReferenceCount,
            int branchEntryTargetCount,
            int terminalPeerCount,
            int closureInflationRejectionCount,
            IntDistribution parentFanOut) {
        public StrategySummary {
            parentFanOut = parentFanOut == null ? IntDistribution.EMPTY : parentFanOut;
            if (strategy == null || targetCount < 0 || parentlessTargetCount < 0
                    || singleParentTargetCount < 0 || multiParentTargetCount < 0
                    || parentlessTargetCount + singleParentTargetCount
                            + multiParentTargetCount != targetCount
                    || parentReferenceCount < 0
                    || sameFamilyReferenceCount < 0 || crossFamilyReferenceCount < 0
                    || unclassifiedReferenceCount < 0
                    || sameFamilyReferenceCount + crossFamilyReferenceCount
                            + unclassifiedReferenceCount != parentReferenceCount
                    || branchEntryTargetCount < 0 || branchEntryTargetCount > targetCount
                    || terminalPeerCount < 0 || terminalPeerCount > targetCount
                    || closureInflationRejectionCount < 0
                    || closureInflationRejectionCount > targetCount) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route strategy summary");
            }
        }
    }

    public record BranchEntrySummary(
            int branchIndex,
            int targetCount,
            int singleParentTargetCount,
            int multiParentTargetCount,
            int parentReferenceCount) {
        public BranchEntrySummary {
            if (branchIndex < 0 || targetCount < 1 || singleParentTargetCount < 0
                    || multiParentTargetCount < 0
                    || singleParentTargetCount + multiParentTargetCount > targetCount
                    || parentReferenceCount < 0) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route branch-entry summary");
            }
        }
    }

    public record AlternativeEvidence(
            int groupCount,
            int dependentAlternativePairCount,
            IntDistribution sharedAncestryBasisPoints,
            IntDistribution ancestryDivergenceBasisPoints,
            IntDistribution routeCostBalanceBasisPoints) {
        public static final AlternativeEvidence EMPTY = new AlternativeEvidence(
                0, 0, IntDistribution.EMPTY, IntDistribution.EMPTY, IntDistribution.EMPTY);

        public AlternativeEvidence {
            sharedAncestryBasisPoints = sharedAncestryBasisPoints == null
                    ? IntDistribution.EMPTY : sharedAncestryBasisPoints;
            ancestryDivergenceBasisPoints = ancestryDivergenceBasisPoints == null
                    ? IntDistribution.EMPTY : ancestryDivergenceBasisPoints;
            routeCostBalanceBasisPoints = routeCostBalanceBasisPoints == null
                    ? IntDistribution.EMPTY : routeCostBalanceBasisPoints;
            if (groupCount < 0 || dependentAlternativePairCount < 0
                    || sharedAncestryBasisPoints.sampleCount() != groupCount
                    || ancestryDivergenceBasisPoints.sampleCount() != groupCount
                    || routeCostBalanceBasisPoints.sampleCount() != groupCount) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route alternative evidence");
            }
        }
    }

    public record RouteCostComparison(
            int leafCount,
            int maximumFinitePointIncome,
            LongDistribution currentMandatoryClosureCosts,
            LongDistribution counterfactualMinimumRouteEstimates,
            LongDistribution counterfactualMaximumRouteEstimates,
            int currentAffordableLeafCount,
            int counterfactualAffordableLeafCount,
            boolean estimateExact) {
        public static final RouteCostComparison EMPTY = new RouteCostComparison(
                0,
                0,
                LongDistribution.EMPTY,
                LongDistribution.EMPTY,
                LongDistribution.EMPTY,
                0,
                0,
                false);

        public RouteCostComparison {
            currentMandatoryClosureCosts = currentMandatoryClosureCosts == null
                    ? LongDistribution.EMPTY : currentMandatoryClosureCosts;
            counterfactualMinimumRouteEstimates =
                    counterfactualMinimumRouteEstimates == null
                            ? LongDistribution.EMPTY : counterfactualMinimumRouteEstimates;
            counterfactualMaximumRouteEstimates =
                    counterfactualMaximumRouteEstimates == null
                            ? LongDistribution.EMPTY : counterfactualMaximumRouteEstimates;
            if (leafCount < 0 || maximumFinitePointIncome < 0
                    || currentMandatoryClosureCosts.sampleCount() != leafCount
                    || counterfactualMinimumRouteEstimates.sampleCount() != leafCount
                    || counterfactualMaximumRouteEstimates.sampleCount() != leafCount
                    || currentAffordableLeafCount < 0
                    || currentAffordableLeafCount > leafCount
                    || counterfactualAffordableLeafCount < 0
                    || counterfactualAffordableLeafCount > leafCount) {
                throw new IllegalArgumentException(
                        "Invalid grouped-route cost comparison");
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
        public static final IntDistribution EMPTY = new IntDistribution(0, 0, 0, 0, 0, 0);

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
            if (values == null || values.stream().anyMatch(value -> value == null || value < 0)) {
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
            if (values == null || values.stream().anyMatch(value -> value == null || value < 0L)) {
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
}
