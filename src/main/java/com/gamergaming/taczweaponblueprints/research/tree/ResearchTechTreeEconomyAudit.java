package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;

import net.minecraft.resources.ResourceLocation;

/** Read-only point-cost review; rank, tier, and bands never participate in cost. */
public final class ResearchTechTreeEconomyAudit {
    public static final String COST_AUTHORITY = "research_policy";

    private ResearchTechTreeEconomyAudit() {
    }

    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            ResearchPointAwardEconomyProjection.Projection pointIncome) {
        return audit(graph, presentation, pointIncome, ResearchCostMode.POINTS_AND_ITEMS);
    }

    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            ResearchPointAwardEconomyProjection.Projection pointIncome,
            ResearchCostMode researchCostMode) {
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException("Research Tech Tree economy inputs cannot be null");
        }
        if (!presentation.available()) {
            return new Audit(
                    COST_AUTHORITY,
                    false,
                    pointIncome,
                    List.of(),
                    researchCostMode == null
                            ? ResearchCostMode.POINTS_AND_ITEMS : researchCostMode);
        }
        presentation.validateAgainst(graph);
        ResearchPointAwardEconomyProjection.Projection stableIncome = pointIncome == null
                ? ResearchPointAwardEconomyProjection.Projection.EMPTY : pointIncome;
        Map<ResourceLocation, Domain> domainByNode = new LinkedHashMap<>();
        presentation.domains().forEach(domain -> domain.lanes().stream()
                .flatMap(lane -> lane.members().stream())
                .forEach(member -> domainByNode.put(member.nodeId(), domain.domain())));
        List<DomainEconomy> domains = presentation.domains().stream()
                .map(domain -> auditDomain(graph, domain, domainByNode, stableIncome.maximumFinitePoints()))
                .toList();
        return new Audit(
                COST_AUTHORITY,
                false,
                stableIncome,
                domains,
                researchCostMode == null
                        ? ResearchCostMode.POINTS_AND_ITEMS : researchCostMode);
    }

    private static DomainEconomy auditDomain(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation.DomainView domain,
            Map<ResourceLocation, Domain> domainByNode,
            int maximumFiniteIncome) {
        Set<ResourceLocation> members = domain.lanes().stream()
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = new LinkedHashMap<>();
        Map<ResourceLocation, List<DomainRequirementGroup>> requirementGroups =
                new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new LinkedHashMap<>();
        members.forEach(id -> {
            prerequisites.put(id, new ArrayList<>());
            dependents.put(id, new ArrayList<>());
            requirementGroups.put(id, graph.requirementGroupsOf(id).stream()
                    .map(group -> new DomainRequirementGroup(
                            group.visibleAlternativeIds().stream()
                                    .filter(alternative ->
                                            domainByNode.get(alternative) == domain.domain())
                                    .toList(),
                            group.hiddenAlternativeCount() > 0
                                    || group.externalAlternativeCount() > 0
                                    || group.visibleAlternativeIds().stream().anyMatch(
                                            alternative -> domainByNode.get(alternative)
                                                    != domain.domain())))
                    .toList());
        });
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            if (members.contains(edge.dependentId())
                    && domainByNode.get(edge.prerequisiteId()) == domain.domain()) {
                prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
                dependents.get(edge.prerequisiteId()).add(edge.dependentId());
            }
        }
        List<ResourceLocation> foundations = requirementGroups.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .allMatch(DomainRequirementGroup::externalAlternativeAvailable))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        List<ResourceLocation> leaves = dependents.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        Map<ResourceLocation, Integer> costs = new LinkedHashMap<>();
        members.forEach(id -> costs.put(id, graph.node(id).orElseThrow().pointCost()));
        long totalCost = costs.values().stream().mapToLong(Integer::longValue).sum();
        long foundationCost = foundations.stream().mapToLong(costs::get).sum();
        Map<ResourceLocation, Long> singlePathMemo = new LinkedHashMap<>();
        List<Long> singlePathCosts = leaves.stream()
                .map(id -> maximumSinglePathCost(
                        id, prerequisites, costs, singlePathMemo, new LinkedHashSet<>()))
                .toList();
        Map<ResourceLocation, Set<ResourceLocation>> closureMemo = new LinkedHashMap<>();
        List<Long> closureCosts = leaves.stream().map(id -> {
            Set<ResourceLocation> closure = selectedRouteClosure(
                    id, requirementGroups, costs, closureMemo, new LinkedHashSet<>());
            return closure.stream().mapToLong(costs::get).sum();
        }).toList();
        int mergeCount = (int) requirementGroups.values().stream()
                .filter(values -> values.size() > 1).count();
        int additionalMergePrerequisites = requirementGroups.values().stream()
                .mapToInt(values -> Math.max(0, values.size() - 1)).sum();
        int coverageBasisPoints = totalCost == 0
                ? 10_000
                : Math.min(10_000, Math.toIntExact(Math.round(
                        maximumFiniteIncome * 10_000.0 / totalCost)));
        return new DomainEconomy(
                domain.domain(),
                members.size(),
                totalCost,
                foundations.size(),
                foundationCost,
                leaves.size(),
                singlePathCosts.stream().mapToLong(Long::longValue).min().orElse(0L),
                singlePathCosts.stream().mapToLong(Long::longValue).max().orElse(0L),
                closureCosts.stream().mapToLong(Long::longValue).min().orElse(0L),
                closureCosts.stream().mapToLong(Long::longValue).max().orElse(0L),
                mergeCount,
                additionalMergePrerequisites,
                maximumFiniteIncome,
                coverageBasisPoints);
    }

    private static long maximumSinglePathCost(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, Integer> costs,
            Map<ResourceLocation, Long> memo,
            Set<ResourceLocation> visiting) {
        Long known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException("Research Tech Tree economy graph contains a cycle");
        }
        long parentCost = 0L;
        for (ResourceLocation parent : prerequisites.getOrDefault(node, List.of())) {
            parentCost = Math.max(parentCost, maximumSinglePathCost(
                    parent, prerequisites, costs, memo, visiting));
        }
        visiting.remove(node);
        long result = Math.addExact(parentCost, costs.getOrDefault(node, 0).longValue());
        memo.put(node, result);
        return result;
    }

    /**
     * Produces one valid deterministic unlock route: every mandatory group is
     * retained and its cheapest standalone alternative closure is selected.
     */
    private static Set<ResourceLocation> selectedRouteClosure(
            ResourceLocation node,
            Map<ResourceLocation, List<DomainRequirementGroup>> requirementGroups,
            Map<ResourceLocation, Integer> costs,
            Map<ResourceLocation, Set<ResourceLocation>> memo,
            Set<ResourceLocation> visiting) {
        Set<ResourceLocation> known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree grouped economy graph contains a cycle");
        }
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        result.add(node);
        for (DomainRequirementGroup group
                : requirementGroups.getOrDefault(node, List.of())) {
            List<Set<ResourceLocation>> candidates = new ArrayList<>();
            group.localAlternatives().stream()
                    .map(alternative -> selectedRouteClosure(
                            alternative, requirementGroups, costs, memo, visiting))
                    .forEach(candidates::add);
            if (group.externalAlternativeAvailable()) {
                candidates.add(Set.of());
            }
            Set<ResourceLocation> selected = candidates.stream()
                    .min(Comparator
                            .comparingLong((Set<ResourceLocation> closure) -> closure.stream()
                                    .mapToLong(id -> costs.getOrDefault(id, 0)).sum())
                            .thenComparing(closure -> closure.stream()
                                    .map(ResourceLocation::toString)
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining("\u0000"))))
                    .orElse(Set.of());
            result.addAll(selected);
        }
        visiting.remove(node);
        Set<ResourceLocation> immutable = Set.copyOf(result);
        memo.put(node, immutable);
        return immutable;
    }

    private record DomainRequirementGroup(
            List<ResourceLocation> localAlternatives,
            boolean externalAlternativeAvailable) {
        private DomainRequirementGroup {
            localAlternatives = List.copyOf(localAlternatives);
            if (localAlternatives.isEmpty() && !externalAlternativeAvailable) {
                throw new IllegalArgumentException(
                        "Research Tech Tree domain requirement has no alternatives");
            }
        }
    }

    public record Audit(
            String costAuthority,
            boolean automaticCostCurveEnabled,
            ResearchPointAwardEconomyProjection.Projection pointIncome,
            List<DomainEconomy> domains,
            ResearchCostMode researchCostMode) {
        public static final Audit EMPTY = new Audit(
                COST_AUTHORITY,
                false,
                ResearchPointAwardEconomyProjection.Projection.EMPTY,
                List.of(),
                ResearchCostMode.POINTS_AND_ITEMS);

        public Audit(
                String costAuthority,
                boolean automaticCostCurveEnabled,
                ResearchPointAwardEconomyProjection.Projection pointIncome,
                List<DomainEconomy> domains) {
            this(
                    costAuthority,
                    automaticCostCurveEnabled,
                    pointIncome,
                    domains,
                    ResearchCostMode.POINTS_AND_ITEMS);
        }

        public Audit {
            pointIncome = pointIncome == null
                    ? ResearchPointAwardEconomyProjection.Projection.EMPTY : pointIncome;
            domains = domains == null ? List.of() : List.copyOf(domains);
            if (!COST_AUTHORITY.equals(costAuthority)
                    || automaticCostCurveEnabled
                    || researchCostMode == null
                    || domains.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Invalid Research Tech Tree economy audit");
            }
        }

        public boolean pointCoverageApplicable() {
            return researchCostMode.pointsEnabled();
        }

        public Optional<DomainEconomy> domain(Domain domain) {
            return domains.stream().filter(value -> value.domain() == domain).findFirst();
        }
    }

    public record DomainEconomy(
            Domain domain,
            int nodeCount,
            long fullTreeCost,
            int foundationCount,
            long foundationCost,
            int leafCount,
            long minimumLeafSinglePathCost,
            long maximumLeafSinglePathCost,
            long minimumLeafUnlockClosureCost,
            long maximumLeafUnlockClosureCost,
            int andMergeCount,
            int additionalMergePrerequisiteCount,
            int maximumFinitePointIncome,
            int finiteIncomeCoverageBasisPoints) {
        public DomainEconomy {
            if (domain == null || nodeCount <= 0 || fullTreeCost < 0
                    || foundationCount <= 0 || foundationCost < 0 || leafCount <= 0
                    || minimumLeafSinglePathCost < 0 || maximumLeafSinglePathCost < 0
                    || minimumLeafSinglePathCost > maximumLeafSinglePathCost
                    || minimumLeafUnlockClosureCost < 0 || maximumLeafUnlockClosureCost < 0
                    || minimumLeafUnlockClosureCost > maximumLeafUnlockClosureCost
                    || andMergeCount < 0 || additionalMergePrerequisiteCount < 0
                    || maximumFinitePointIncome < 0 || finiteIncomeCoverageBasisPoints < 0
                    || finiteIncomeCoverageBasisPoints > 10_000) {
                throw new IllegalArgumentException("Invalid Research Tech Tree domain economy audit");
            }
        }

        public double finiteIncomeCoverage() {
            return finiteIncomeCoverageBasisPoints / 10_000.0;
        }
    }
}
