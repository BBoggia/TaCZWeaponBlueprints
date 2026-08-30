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
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException("Research Tech Tree economy inputs cannot be null");
        }
        if (!presentation.available()) {
            return Audit.EMPTY;
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
        return new Audit(COST_AUTHORITY, false, stableIncome, domains);
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
        Map<ResourceLocation, List<ResourceLocation>> dependents = new LinkedHashMap<>();
        members.forEach(id -> {
            prerequisites.put(id, new ArrayList<>());
            dependents.put(id, new ArrayList<>());
        });
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            if (members.contains(edge.dependentId())
                    && domainByNode.get(edge.prerequisiteId()) == domain.domain()) {
                prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
                dependents.get(edge.prerequisiteId()).add(edge.dependentId());
            }
        }
        List<ResourceLocation> foundations = prerequisites.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
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
        int totalCost = costs.values().stream().mapToInt(Integer::intValue).sum();
        int foundationCost = foundations.stream().mapToInt(costs::get).sum();
        Map<ResourceLocation, Integer> singlePathMemo = new LinkedHashMap<>();
        List<Integer> singlePathCosts = leaves.stream()
                .map(id -> maximumSinglePathCost(
                        id, prerequisites, costs, singlePathMemo, new LinkedHashSet<>()))
                .toList();
        List<Integer> closureCosts = leaves.stream().map(id -> {
            Set<ResourceLocation> closure = prerequisiteClosure(id, prerequisites);
            return closure.stream().mapToInt(costs::get).sum();
        }).toList();
        int mergeCount = (int) prerequisites.values().stream()
                .filter(values -> values.size() > 1).count();
        int additionalMergePrerequisites = prerequisites.values().stream()
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
                singlePathCosts.stream().mapToInt(Integer::intValue).min().orElse(0),
                singlePathCosts.stream().mapToInt(Integer::intValue).max().orElse(0),
                closureCosts.stream().mapToInt(Integer::intValue).min().orElse(0),
                closureCosts.stream().mapToInt(Integer::intValue).max().orElse(0),
                mergeCount,
                additionalMergePrerequisites,
                maximumFiniteIncome,
                coverageBasisPoints);
    }

    private static int maximumSinglePathCost(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, Integer> costs,
            Map<ResourceLocation, Integer> memo,
            Set<ResourceLocation> visiting) {
        Integer known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException("Research Tech Tree economy graph contains a cycle");
        }
        int parentCost = 0;
        for (ResourceLocation parent : prerequisites.getOrDefault(node, List.of())) {
            parentCost = Math.max(parentCost, maximumSinglePathCost(
                    parent, prerequisites, costs, memo, visiting));
        }
        visiting.remove(node);
        int result = Math.addExact(costs.getOrDefault(node, 0), parentCost);
        memo.put(node, result);
        return result;
    }

    private static Set<ResourceLocation> prerequisiteClosure(
            ResourceLocation start,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        Deque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (result.add(current)) {
                pending.addAll(prerequisites.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    public record Audit(
            String costAuthority,
            boolean automaticCostCurveEnabled,
            ResearchPointAwardEconomyProjection.Projection pointIncome,
            List<DomainEconomy> domains) {
        public static final Audit EMPTY = new Audit(
                COST_AUTHORITY,
                false,
                ResearchPointAwardEconomyProjection.Projection.EMPTY,
                List.of());

        public Audit {
            pointIncome = pointIncome == null
                    ? ResearchPointAwardEconomyProjection.Projection.EMPTY : pointIncome;
            domains = domains == null ? List.of() : List.copyOf(domains);
            if (!COST_AUTHORITY.equals(costAuthority)
                    || automaticCostCurveEnabled
                    || domains.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Invalid Research Tech Tree economy audit");
            }
        }

        public Optional<DomainEconomy> domain(Domain domain) {
            return domains.stream().filter(value -> value.domain() == domain).findFirst();
        }
    }

    public record DomainEconomy(
            Domain domain,
            int nodeCount,
            int fullTreeCost,
            int foundationCount,
            int foundationCost,
            int leafCount,
            int minimumLeafSinglePathCost,
            int maximumLeafSinglePathCost,
            int minimumLeafUnlockClosureCost,
            int maximumLeafUnlockClosureCost,
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
