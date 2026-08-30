package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;

import net.minecraft.resources.ResourceLocation;

/** Structural evidence for one server-authoritative, disclosure-safe Tech Tree. */
public final class ResearchTechTreeTopologyAudit {
    private ResearchTechTreeTopologyAudit() {
    }

    public static Audit audit(ResearchTreeGraph graph, ResearchTechTreePresentation presentation) {
        return audit(graph, presentation, null, null);
    }

    /** Adds excluded-candidate evidence without making diagnostics authoritative. */
    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics) {
        return audit(graph, presentation, automaticDiagnostics, null);
    }

    /** Compares current parent sets with an explicit prior fixture when supplied. */
    public static Audit audit(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation presentation,
            AutomaticWeaponPlacementDiagnostics automaticDiagnostics,
            ParentFixture priorFixture) {
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException("Research Tech Tree topology inputs cannot be null");
        }
        if (!presentation.available()) {
            return Audit.EMPTY;
        }
        presentation.validateAgainst(graph);

        Map<ResourceLocation, Domain> domainByNode = new LinkedHashMap<>();
        presentation.domains().forEach(domain -> domain.lanes().stream()
                .flatMap(lane -> lane.members().stream())
                .forEach(member -> domainByNode.put(member.nodeId(), domain.domain())));
        int excludedAutomaticCount = automaticDiagnostics == null
                ? 0
                : Math.toIntExact(automaticDiagnostics.excludedAutomaticCount());
        List<DomainAudit> domains = presentation.domains().stream()
                .map(domain -> auditDomain(
                        graph,
                        domain,
                        domainByNode,
                        domain.domain() == Domain.WEAPONS ? excludedAutomaticCount : 0))
                .toList();
        ParentFixture current = ParentFixture.capture(graph, presentation);
        return new Audit(
                domains,
                priorFixture == null
                        ? ParentRetention.UNAVAILABLE
                        : ParentRetention.compare(priorFixture, current));
    }

    private static DomainAudit auditDomain(
            ResearchTreeGraph graph,
            ResearchTechTreePresentation.DomainView domain,
            Map<ResourceLocation, Domain> domainByNode,
            int excludedAutomaticCount) {
        Set<ResourceLocation> members = new LinkedHashSet<>();
        Map<ResourceLocation, ResearchTechTreePresentation.Member> presentationById =
                new LinkedHashMap<>();
        domain.lanes().stream().flatMap(lane -> lane.members().stream()).forEach(member -> {
            members.add(member.nodeId());
            presentationById.put(member.nodeId(), member);
        });

        Map<ResourceLocation, Set<ResourceLocation>> adjacency = new LinkedHashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> dependents = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> prerequisites = new LinkedHashMap<>();
        members.forEach(id -> {
            adjacency.put(id, new LinkedHashSet<>());
            dependents.put(id, new LinkedHashSet<>());
            prerequisites.put(id, new ArrayList<>());
        });

        int internalEdges = 0;
        int boundaryPrerequisites = 0;
        int unplacedPrerequisites = 0;
        List<RankedEdge> rankedEdges = new ArrayList<>();
        long totalEdgeRankSpan = 0L;
        int maximumEdgeRankSpan = 0;
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            if (!members.contains(edge.dependentId())) {
                continue;
            }
            Domain prerequisiteDomain = domainByNode.get(edge.prerequisiteId());
            if (prerequisiteDomain == null) {
                unplacedPrerequisites++;
                continue;
            }
            if (prerequisiteDomain != domain.domain()) {
                boundaryPrerequisites++;
                continue;
            }
            internalEdges++;
            adjacency.get(edge.prerequisiteId()).add(edge.dependentId());
            adjacency.get(edge.dependentId()).add(edge.prerequisiteId());
            dependents.get(edge.prerequisiteId()).add(edge.dependentId());
            prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
            int prerequisiteRank = presentationById.get(edge.prerequisiteId()).rank();
            int dependentRank = presentationById.get(edge.dependentId()).rank();
            int span = Math.subtractExact(dependentRank, prerequisiteRank);
            totalEdgeRankSpan = Math.addExact(totalEdgeRankSpan, span);
            maximumEdgeRankSpan = Math.max(maximumEdgeRankSpan, span);
            rankedEdges.add(new RankedEdge(
                    edge.prerequisiteId(), edge.dependentId(), prerequisiteRank, dependentRank));
        }

        Set<ResourceLocation> roots = new LinkedHashSet<>();
        prerequisites.forEach((id, values) -> {
            if (values.isEmpty()) {
                roots.add(id);
            }
        });
        Map<Integer, Integer> rankPopulations = new LinkedHashMap<>();
        presentationById.values().forEach(member ->
                rankPopulations.merge(member.rank(), 1, Math::addExact));
        int minimumRank = rankPopulations.keySet().stream().mapToInt(Integer::intValue)
                .min().orElse(0);
        int maximumRank = rankPopulations.keySet().stream().mapToInt(Integer::intValue)
                .max().orElse(0);
        int emptyRankCount = Math.subtractExact(
                Math.addExact(Math.subtractExact(maximumRank, minimumRank), 1),
                rankPopulations.size());
        int mergeCount = (int) prerequisites.values().stream()
                .filter(values -> values.size() > 1).count();
        int automaticCount = (int) presentationById.values().stream()
                .filter(member -> member.origin() == PlacementOrigin.AUTOMATIC).count();
        int manualCount = (int) presentationById.values().stream()
                .filter(member -> member.origin().authored()).count();

        return new DomainAudit(
                domain.domain(),
                members.size(),
                internalEdges,
                boundaryPrerequisites,
                unplacedPrerequisites,
                roots,
                componentCount(adjacency),
                reachableCount(roots, dependents),
                prerequisites.values().stream().mapToInt(List::size).max().orElse(0),
                dependents.values().stream().mapToInt(Set::size).max().orElse(0),
                maximumDepth(members, prerequisites),
                rankPopulations.values().stream().mapToInt(Integer::intValue).max().orElse(0),
                emptyRankCount,
                mergeCount,
                crossBranchMergeCount(roots, dependents, prerequisites),
                approximateCrossings(rankedEdges, presentationById),
                totalEdgeRankSpan,
                maximumEdgeRankSpan,
                manualCount,
                automaticCount,
                members.size() - automaticCount - manualCount,
                excludedAutomaticCount);
    }

    private static int componentCount(Map<ResourceLocation, Set<ResourceLocation>> adjacency) {
        Set<ResourceLocation> visited = new LinkedHashSet<>();
        int components = 0;
        for (ResourceLocation start : adjacency.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            components++;
            Deque<ResourceLocation> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                for (ResourceLocation neighbor : adjacency.get(pending.removeFirst())) {
                    if (visited.add(neighbor)) {
                        pending.addLast(neighbor);
                    }
                }
            }
        }
        return components;
    }

    private static int reachableCount(
            Set<ResourceLocation> roots,
            Map<ResourceLocation, Set<ResourceLocation>> dependents) {
        Set<ResourceLocation> reachable = new LinkedHashSet<>(roots);
        Deque<ResourceLocation> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            for (ResourceLocation dependent : dependents.getOrDefault(
                    pending.removeFirst(), Set.of())) {
                if (reachable.add(dependent)) {
                    pending.addLast(dependent);
                }
            }
        }
        return reachable.size();
    }

    private static int maximumDepth(
            Set<ResourceLocation> members,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites) {
        Map<ResourceLocation, Integer> memo = new LinkedHashMap<>();
        int maximum = 0;
        for (ResourceLocation member : members) {
            maximum = Math.max(maximum, depth(member, prerequisites, memo, new LinkedHashSet<>()));
        }
        return maximum;
    }

    private static int depth(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, Integer> memo,
            Set<ResourceLocation> visiting) {
        Integer known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException("Research Tech Tree topology contains a cycle");
        }
        int result = 0;
        for (ResourceLocation prerequisite : prerequisites.getOrDefault(node, List.of())) {
            result = Math.max(result, Math.addExact(
                    1, depth(prerequisite, prerequisites, memo, visiting)));
        }
        visiting.remove(node);
        memo.put(node, result);
        return result;
    }

    /** Counts merges whose parents descend from more than one stable branch seed. */
    private static int crossBranchMergeCount(
            Set<ResourceLocation> roots,
            Map<ResourceLocation, Set<ResourceLocation>> dependents,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites) {
        Set<ResourceLocation> seeds = new LinkedHashSet<>();
        if (roots.size() == 1) {
            ResourceLocation root = roots.iterator().next();
            seeds.addAll(dependents.getOrDefault(root, Set.of()));
            if (seeds.isEmpty()) {
                seeds.add(root);
            }
        } else {
            seeds.addAll(roots);
        }
        Map<ResourceLocation, Set<ResourceLocation>> memo = new LinkedHashMap<>();
        int count = 0;
        for (List<ResourceLocation> parents : prerequisites.values()) {
            if (parents.size() < 2) {
                continue;
            }
            Set<ResourceLocation> lineages = new LinkedHashSet<>();
            parents.forEach(parent -> lineages.addAll(branchSeeds(
                    parent, seeds, prerequisites, memo, new LinkedHashSet<>())));
            if (lineages.size() > 1) {
                count++;
            }
        }
        return count;
    }

    private static Set<ResourceLocation> branchSeeds(
            ResourceLocation node,
            Set<ResourceLocation> seeds,
            Map<ResourceLocation, List<ResourceLocation>> prerequisites,
            Map<ResourceLocation, Set<ResourceLocation>> memo,
            Set<ResourceLocation> visiting) {
        Set<ResourceLocation> known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException("Research Tech Tree topology contains a cycle");
        }
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (seeds.contains(node)) {
            result.add(node);
        } else {
            prerequisites.getOrDefault(node, List.of()).forEach(parent -> result.addAll(
                    branchSeeds(parent, seeds, prerequisites, memo, visiting)));
        }
        visiting.remove(node);
        Set<ResourceLocation> immutable = Set.copyOf(result);
        memo.put(node, immutable);
        return immutable;
    }

    /** Counts order inversions for edges sharing one source/destination rank pair. */
    private static long approximateCrossings(
            List<RankedEdge> edges,
            Map<ResourceLocation, ResearchTechTreePresentation.Member> members) {
        Map<Integer, Map<ResourceLocation, Integer>> orderByRank = new LinkedHashMap<>();
        members.values().stream().collect(java.util.stream.Collectors.groupingBy(
                ResearchTechTreePresentation.Member::rank,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList())).forEach((rank, values) -> {
                    Map<ResourceLocation, Integer> order = new LinkedHashMap<>();
                    List<ResearchTechTreePresentation.Member> sorted = values.stream()
                            .sorted(Comparator
                                    .comparingLong(ResearchTechTreePresentation.Member::siblingOrder)
                                    .thenComparing(value -> value.nodeId().toString()))
                            .toList();
                    for (int index = 0; index < sorted.size(); index++) {
                        order.put(sorted.get(index).nodeId(), index);
                    }
                    orderByRank.put(rank, order);
                });

        Map<RankPair, List<OrderedEdge>> groups = new LinkedHashMap<>();
        for (RankedEdge edge : edges) {
            groups.computeIfAbsent(
                    new RankPair(edge.prerequisiteRank(), edge.dependentRank()),
                    ignored -> new ArrayList<>()).add(new OrderedEdge(
                            orderByRank.get(edge.prerequisiteRank()).get(edge.prerequisiteId()),
                            orderByRank.get(edge.dependentRank()).get(edge.dependentId())));
        }
        long result = 0L;
        for (List<OrderedEdge> group : groups.values()) {
            group.sort(Comparator.comparingInt(OrderedEdge::sourceOrder)
                    .thenComparingInt(OrderedEdge::targetOrder));
            FenwickTree tree = new FenwickTree(
                    group.stream().mapToInt(OrderedEdge::targetOrder).max().orElse(0) + 1);
            int added = 0;
            for (int start = 0; start < group.size();) {
                int end = start + 1;
                while (end < group.size()
                        && group.get(end).sourceOrder() == group.get(start).sourceOrder()) {
                    end++;
                }
                for (int index = start; index < end; index++) {
                    result = Math.addExact(
                            result,
                            added - tree.prefixCount(group.get(index).targetOrder()));
                }
                for (int index = start; index < end; index++) {
                    tree.add(group.get(index).targetOrder());
                    added++;
                }
                start = end;
            }
        }
        return result;
    }

    public record Audit(List<DomainAudit> domains, ParentRetention parentRetention) {
        public static final Audit EMPTY = new Audit(List.of(), ParentRetention.UNAVAILABLE);

        public Audit {
            domains = domains == null ? List.of() : List.copyOf(domains);
            parentRetention = parentRetention == null
                    ? ParentRetention.UNAVAILABLE : parentRetention;
            if (domains.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Research Tech Tree domain audits cannot be null");
            }
        }

        public Audit(List<DomainAudit> domains) {
            this(domains, ParentRetention.UNAVAILABLE);
        }

        public Optional<DomainAudit> domain(Domain domain) {
            return domains.stream().filter(value -> value.domain() == domain).findFirst();
        }

        public boolean allDomainsUnified() {
            return !domains.isEmpty() && domains.stream().allMatch(DomainAudit::singleEntryUnified);
        }
    }

    public record DomainAudit(
            Domain domain,
            int nodeCount,
            int internalEdgeCount,
            int boundaryPrerequisiteCount,
            int unplacedPrerequisiteCount,
            Set<ResourceLocation> rootIds,
            int componentCount,
            int reachableNodeCount,
            int maximumPrerequisiteCount,
            int maximumDependentCount,
            int maximumDepth,
            int maximumRankPopulation,
            int emptyRankCount,
            int mergeCount,
            int crossBranchMergeCount,
            long approximateEdgeCrossingCount,
            long totalEdgeRankSpan,
            int maximumEdgeRankSpan,
            int manualNodeCount,
            int automaticNodeCount,
            int fallbackNodeCount,
            int excludedAutomaticCount) {
        public DomainAudit {
            rootIds = rootIds == null ? Set.of() : Set.copyOf(rootIds);
            if (domain == null || nodeCount <= 0 || internalEdgeCount < 0
                    || boundaryPrerequisiteCount < 0 || unplacedPrerequisiteCount < 0
                    || rootIds.isEmpty() || componentCount <= 0
                    || reachableNodeCount < 0 || reachableNodeCount > nodeCount
                    || maximumPrerequisiteCount < 0 || maximumDependentCount < 0
                    || maximumDepth < 0 || maximumRankPopulation <= 0 || emptyRankCount < 0
                    || mergeCount < 0 || crossBranchMergeCount < 0
                    || crossBranchMergeCount > mergeCount || approximateEdgeCrossingCount < 0L
                    || totalEdgeRankSpan < 0L || maximumEdgeRankSpan < 0
                    || manualNodeCount < 0 || automaticNodeCount < 0 || fallbackNodeCount < 0
                    || manualNodeCount + automaticNodeCount + fallbackNodeCount != nodeCount
                    || excludedAutomaticCount < 0) {
                throw new IllegalArgumentException("Invalid Research Tech Tree domain audit");
            }
        }

        public boolean singleEntryUnified() {
            return rootIds.size() == 1 && componentCount == 1
                    && reachableNodeCount == nodeCount && unplacedPrerequisiteCount == 0;
        }

        public double averageEdgeRankSpan() {
            return internalEdgeCount == 0 ? 0.0 : (double) totalEdgeRankSpan / internalEdgeCount;
        }
    }

    /** Immutable parent-set fixture suitable for authoring tools and tests. */
    public record ParentFixture(Map<ResourceLocation, Set<ResourceLocation>> parentsByNode) {
        public ParentFixture {
            if (parentsByNode == null) {
                throw new IllegalArgumentException("Research Tech Tree parent fixture cannot be null");
            }
            LinkedHashMap<ResourceLocation, Set<ResourceLocation>> copy = new LinkedHashMap<>();
            parentsByNode.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> {
                        if (entry.getKey() == null || entry.getValue() == null
                                || entry.getValue().stream().anyMatch(java.util.Objects::isNull)
                                || entry.getValue().contains(entry.getKey())) {
                            throw new IllegalArgumentException(
                                    "Research Tech Tree parent fixture is invalid");
                        }
                        LinkedHashSet<ResourceLocation> parents = new LinkedHashSet<>();
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(ResourceLocation::toString))
                                .forEach(parents::add);
                        copy.put(entry.getKey(), Collections.unmodifiableSet(parents));
                    });
            parentsByNode = Collections.unmodifiableMap(copy);
        }

        public static ParentFixture capture(
                ResearchTreeGraph graph,
                ResearchTechTreePresentation presentation) {
            if (graph == null || presentation == null) {
                throw new IllegalArgumentException("Research Tech Tree parent fixture inputs cannot be null");
            }
            if (presentation.available()) {
                presentation.validateAgainst(graph);
            }
            Set<ResourceLocation> members = presentation.domains().stream()
                    .flatMap(domain -> domain.lanes().stream())
                    .flatMap(lane -> lane.members().stream())
                    .map(ResearchTechTreePresentation.Member::nodeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<ResourceLocation, Set<ResourceLocation>> result = new LinkedHashMap<>();
            members.stream().sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(id -> result.put(id, graph.prerequisitesOf(id).stream()
                            .filter(members::contains)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
            return new ParentFixture(result);
        }
    }

    public record ParentRetention(
            boolean available,
            int comparedNodeCount,
            int retainedParentSetCount,
            int changedParentSetCount,
            int addedNodeCount,
            int removedNodeCount,
            int retentionBasisPoints,
            List<ResourceLocation> changedNodeIds) {
        public static final ParentRetention UNAVAILABLE = new ParentRetention(
                false, 0, 0, 0, 0, 0, 0, List.of());

        public ParentRetention {
            changedNodeIds = changedNodeIds == null ? List.of() : List.copyOf(changedNodeIds);
            if (comparedNodeCount < 0 || retainedParentSetCount < 0 || changedParentSetCount < 0
                    || retainedParentSetCount + changedParentSetCount != comparedNodeCount
                    || addedNodeCount < 0 || removedNodeCount < 0
                    || retentionBasisPoints < 0 || retentionBasisPoints > 10_000
                    || changedNodeIds.size() != changedParentSetCount
                    || changedNodeIds.stream().anyMatch(java.util.Objects::isNull)
                    || !available && (comparedNodeCount != 0 || retainedParentSetCount != 0
                            || changedParentSetCount != 0 || addedNodeCount != 0
                            || removedNodeCount != 0 || retentionBasisPoints != 0
                            || !changedNodeIds.isEmpty())) {
                throw new IllegalArgumentException("Invalid Research Tech Tree parent retention");
            }
        }

        private static ParentRetention compare(ParentFixture prior, ParentFixture current) {
            Set<ResourceLocation> shared = new LinkedHashSet<>(prior.parentsByNode().keySet());
            shared.retainAll(current.parentsByNode().keySet());
            List<ResourceLocation> changed = shared.stream()
                    .filter(id -> !prior.parentsByNode().get(id).equals(current.parentsByNode().get(id)))
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            int retained = shared.size() - changed.size();
            int basisPoints = shared.isEmpty()
                    ? 10_000
                    : Math.toIntExact(Math.round(retained * 10_000.0 / shared.size()));
            return new ParentRetention(
                    true,
                    shared.size(),
                    retained,
                    changed.size(),
                    current.parentsByNode().size() - shared.size(),
                    prior.parentsByNode().size() - shared.size(),
                    basisPoints,
                    changed);
        }

        public double retentionRate() {
            return retentionBasisPoints / 10_000.0;
        }
    }

    private record RankedEdge(
            ResourceLocation prerequisiteId,
            ResourceLocation dependentId,
            int prerequisiteRank,
            int dependentRank) {
    }

    private record RankPair(int prerequisiteRank, int dependentRank) {
    }

    private record OrderedEdge(int sourceOrder, int targetOrder) {
    }

    private static final class FenwickTree {
        private final int[] counts;

        private FenwickTree(int size) {
            counts = new int[Math.addExact(size, 1)];
        }

        private void add(int index) {
            for (int current = index + 1; current < counts.length; current += current & -current) {
                counts[current] = Math.addExact(counts[current], 1);
            }
        }

        private int prefixCount(int inclusiveIndex) {
            int result = 0;
            for (int current = inclusiveIndex + 1; current > 0; current -= current & -current) {
                result = Math.addExact(result, counts[current]);
            }
            return result;
        }
    }
}
