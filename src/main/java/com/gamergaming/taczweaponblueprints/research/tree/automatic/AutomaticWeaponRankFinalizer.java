package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;

import net.minecraft.resources.ResourceLocation;

/**
 * Finalizes provisional automatic ranks against the generated topology.
 * Prerequisites remain authoritative; this pass only lifts, width-bounds, and
 * compacts presentation coordinates.
 */
public final class AutomaticWeaponRankFinalizer {
    public AutomaticWeaponPlacementCandidateSnapshot finalizeRanks(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Collection<AutomaticWeaponPrerequisitePlan> plans) {
        return finalizeRanks(candidates, plans, Map.of(), false);
    }

    /**
     * Finalizes the shared automatic coordinates while reserving every selected
     * profile's authored rank occupancy. This keeps the published mixed topology,
     * rather than only its automatic subset, inside the tree-owned capacity.
     */
    public AutomaticWeaponPlacementCandidateSnapshot finalizeRanks(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation, Map<String, Integer>> authoredRanksByProfile) {
        return finalizeRanks(candidates, plans, authoredRanksByProfile, true);
    }

    private AutomaticWeaponPlacementCandidateSnapshot finalizeRanks(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation, Map<String, Integer>> authoredRanksByProfile,
            boolean requireAuthoredContexts) {
        if (candidates == null || plans == null
                || plans.stream().anyMatch(java.util.Objects::isNull)
                || authoredRanksByProfile == null) {
            throw new IllegalArgumentException(
                    "Automatic rank finalization inputs cannot be null");
        }
        if (!candidates.policy().usesDynamicLayers()
                || candidates.eligibleProposals().isEmpty()) {
            return candidates;
        }
        for (AutomaticWeaponPrerequisitePlan plan : plans) {
            if (!plan.matches(plan.profileId(), candidates)) {
                throw new IllegalArgumentException(
                        "Automatic rank finalization plan does not match its candidates");
            }
        }
        Map<ResourceLocation, Map<String, Integer>> authoredRanks = validateAuthoredRanks(
                candidates, plans, authoredRanksByProfile, requireAuthoredContexts);
        boolean mixedTopology = authoredRanks.values().stream()
                .anyMatch(ranks -> !ranks.isEmpty());

        Map<String, AutomaticWeaponPlacementProposal> proposals =
                candidates.eligibleProposals();
        Comparator<String> stableOrder = Comparator
                .comparingInt((String id) -> proposals.get(id)
                        .progressionCoordinate().rank())
                .thenComparingInt(id -> proposals.get(id).mechanicalScore())
                .thenComparingLong(id -> proposals.get(id)
                        .progressionCoordinate().siblingOrder())
                .thenComparing(id -> id);
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        Map<String, Set<String>> children = new LinkedHashMap<>();
        proposals.keySet().forEach(id -> {
            parents.put(id, new LinkedHashSet<>());
            children.put(id, new LinkedHashSet<>());
        });
        for (AutomaticWeaponPrerequisitePlan plan : plans) {
            plan.prerequisites().forEach((dependentId, prerequisiteIds) -> {
                String dependent = dependentId.toString();
                if (!proposals.containsKey(dependent)) {
                    return;
                }
                for (ResourceLocation prerequisiteId : prerequisiteIds) {
                    String prerequisite = prerequisiteId.toString();
                    if (proposals.containsKey(prerequisite)
                            && parents.get(dependent).add(prerequisite)) {
                        children.get(prerequisite).add(dependent);
                    }
                }
            });
        }

        Map<String, Integer> indegree = new HashMap<>();
        parents.forEach((id, values) -> indegree.put(id, values.size()));
        PriorityQueue<String> ready = new PriorityQueue<>(stableOrder);
        indegree.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        List<String> topological = new ArrayList<>(proposals.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            topological.add(id);
            children.get(id).stream().sorted(stableOrder).forEach(child -> {
                int remaining = indegree.compute(child, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(child);
                }
            });
        }
        if (topological.size() != proposals.size()) {
            throw new IllegalArgumentException(
                    "Automatic prerequisite topology contains a cycle");
        }

        int capacity = candidates.policy().maxNodesPerRank();
        Map<ResourceLocation, Map<Integer, Integer>> authoredWidths = authoredWidths(
                authoredRanks);
        Map<String, Integer> authoredParentMinimums = authoredParentMinimums(
                proposals, plans, authoredRanks);
        Map<String, Integer> assignedRanks = new LinkedHashMap<>();
        Map<Integer, Integer> rankWidths = new HashMap<>();
        for (List<String> batch : rankBatches(
                proposals, parents, topological, plans, mixedTopology)) {
            int rank = batch.stream()
                    .map(proposals::get)
                    .mapToInt(proposal -> proposal.progressionCoordinate().rank())
                    .max()
                    .orElseThrow();
            for (String id : batch) {
                rank = Math.max(rank, authoredParentMinimums.getOrDefault(id, 0));
                for (String parent : parents.get(id)) {
                    Integer parentRank = assignedRanks.get(parent);
                    if (parentRank != null) {
                        rank = Math.max(rank, Math.addExact(parentRank, 1));
                    }
                }
            }
            while (!canPlace(
                    rank, batch.size(), rankWidths, authoredWidths, capacity)) {
                rank = Math.addExact(rank, 1);
            }
            if (rank > ResearchTechTreeContract.MAX_PROGRESSION_RANK) {
                throw new IllegalArgumentException(
                        "Automatic rank finalization exceeds the supported rank range");
            }
            for (String id : batch) {
                assignedRanks.put(id, rank);
            }
            rankWidths.merge(rank, batch.size(), Math::addExact);
        }

        Map<Integer, Integer> compactRanks = new HashMap<>();
        if (!mixedTopology) {
            List<Integer> occupiedRanks = assignedRanks.values().stream()
                    .distinct()
                    .sorted()
                    .toList();
            for (int index = 0; index < occupiedRanks.size(); index++) {
                compactRanks.put(occupiedRanks.get(index), index);
            }
        }
        Map<String, AutomaticWeaponPlacementProposal> finalized = new LinkedHashMap<>();
        proposals.keySet().stream().sorted().forEach(id -> finalized.put(
                id,
                proposals.get(id).withProgressionCoordinate(
                        proposals.get(id).progressionCoordinate().withRank(
                                mixedTopology
                                        ? assignedRanks.get(id)
                                        : compactRanks.get(assignedRanks.get(id))))));

        validateFinalRanks(
                finalized, parents, plans, authoredRanks, capacity, mixedTopology);
        return candidates.withEligibleProposals(
                Collections.unmodifiableMap(finalized));
    }

    /**
     * Pure automatic topology retains each strict provisional rank as one batch.
     * Mixed topology instead uses residual-capacity batches below; compatibility
     * graphs with same-rank edges keep their safe node-by-node lift behavior.
     */
    private static List<List<String>> rankBatches(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, Set<String>> parents,
            List<String> topological,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            boolean mixedTopology) {
        boolean strictProvisionalOrder = parents.entrySet().stream().allMatch(entry ->
                entry.getValue().stream().allMatch(parent ->
                        proposals.get(parent).progressionCoordinate().rank()
                                < proposals.get(entry.getKey())
                                        .progressionCoordinate().rank()));
        if (!strictProvisionalOrder) {
            return topological.stream().map(List::of).toList();
        }
        Map<Integer, List<String>> byRank = new LinkedHashMap<>();
        topological.forEach(id -> byRank.computeIfAbsent(
                proposals.get(id).progressionCoordinate().rank(),
                ignored -> new ArrayList<>()).add(id));
        if (mixedTopology) {
            Map<String, AutomaticWeaponPrerequisitePlan.BranchCoordinate> branches =
                    canonicalBranchCoordinates(proposals, plans);
            List<List<String>> result = new ArrayList<>();
            byRank.values().forEach(rank -> addMixedRankBatches(rank, branches, result));
            return List.copyOf(result);
        }
        return byRank.values().stream().map(List::copyOf).toList();
    }

    /**
     * A mixed authored/automatic row usually has useful residual capacity. Shared
     * trunk members can consume it individually; once specialization begins, one
     * family's planned cross-section remains atomic so triangular branch levels
     * and terminal peer cohorts do not collapse into single-node ladders.
     */
    private static void addMixedRankBatches(
            List<String> rank,
            Map<String, AutomaticWeaponPrerequisitePlan.BranchCoordinate> branches,
            List<List<String>> result) {
        Map<Integer, List<String>> matureFamilies = new java.util.TreeMap<>();
        for (String id : rank) {
            AutomaticWeaponPrerequisitePlan.BranchCoordinate branch = branches.get(id);
            if (branch == null || branch.rankIndex() < branch.familyStartIndex()) {
                result.add(List.of(id));
                continue;
            }
            matureFamilies.computeIfAbsent(
                    branch.branchIndex(), ignored -> new ArrayList<>()).add(id);
        }
        matureFamilies.values().forEach(family -> result.add(List.copyOf(family)));
    }

    private static Map<String, AutomaticWeaponPrerequisitePlan.BranchCoordinate>
            canonicalBranchCoordinates(
                    Map<String, AutomaticWeaponPlacementProposal> proposals,
                    Collection<AutomaticWeaponPrerequisitePlan> plans) {
        Map<String, AutomaticWeaponPrerequisitePlan.BranchCoordinate> result =
                new HashMap<>();
        for (AutomaticWeaponPrerequisitePlan plan : plans) {
            plan.branchCoordinates().forEach((blueprintId, coordinate) -> {
                String id = blueprintId.toString();
                if (!proposals.containsKey(id)) {
                    return;
                }
                if (coordinate.rankIndex()
                        != proposals.get(id).progressionCoordinate().rank()) {
                    throw new IllegalArgumentException(
                            "Automatic branch coordinate does not match its provisional rank");
                }
                AutomaticWeaponPrerequisitePlan.BranchCoordinate previous =
                        result.putIfAbsent(id, coordinate);
                if (previous != null && !previous.equals(coordinate)) {
                    throw new IllegalArgumentException(
                            "Automatic rank finalization mixes branch coordinates");
                }
            });
        }
        return Map.copyOf(result);
    }

    private static Map<ResourceLocation, Map<String, Integer>> validateAuthoredRanks(
            AutomaticWeaponPlacementCandidateSnapshot candidates,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation, Map<String, Integer>> supplied,
            boolean required) {
        Set<ResourceLocation> planProfiles = plans.stream()
                .map(AutomaticWeaponPrerequisitePlan::profileId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (required && !supplied.keySet().equals(planProfiles)) {
            throw new IllegalArgumentException(
                    "Automatic rank finalization authored profiles are incomplete");
        }
        if (supplied.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Automatic rank finalization authored ranks cannot be null");
        }
        Map<ResourceLocation, Map<String, Integer>> result = new LinkedHashMap<>();
        supplied.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getValue() == null
                            || !entry.getValue().keySet().equals(
                                    candidates.authoredBlueprintIds())
                            || entry.getValue().entrySet().stream().anyMatch(rank ->
                                    rank.getKey() == null || rank.getKey().isBlank()
                                            || rank.getValue() == null || rank.getValue() < 0
                                            || rank.getValue()
                                                    > ResearchTechTreeContract
                                                            .MAX_PROGRESSION_RANK)) {
                        throw new IllegalArgumentException(
                                "Automatic rank finalization authored ranks are inconsistent");
                    }
                    result.put(entry.getKey(), Map.copyOf(entry.getValue()));
                });
        return Collections.unmodifiableMap(result);
    }

    private static Map<ResourceLocation, Map<Integer, Integer>> authoredWidths(
            Map<ResourceLocation, Map<String, Integer>> authoredRanks) {
        Map<ResourceLocation, Map<Integer, Integer>> result = new LinkedHashMap<>();
        authoredRanks.forEach((profile, ranks) -> {
            Map<Integer, Integer> widths = new HashMap<>();
            ranks.values().forEach(rank -> widths.merge(rank, 1, Math::addExact));
            result.put(profile, widths);
        });
        return result;
    }

    private static Map<String, Integer> authoredParentMinimums(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation, Map<String, Integer>> authoredRanks) {
        Map<String, Integer> result = new HashMap<>();
        for (AutomaticWeaponPrerequisitePlan plan : plans) {
            Map<String, Integer> profileRanks = authoredRanks.getOrDefault(
                    plan.profileId(), Map.of());
            plan.prerequisites().forEach((dependentId, prerequisiteIds) -> {
                String dependent = dependentId.toString();
                if (!proposals.containsKey(dependent)) {
                    return;
                }
                for (ResourceLocation prerequisiteId : prerequisiteIds) {
                    String prerequisite = prerequisiteId.toString();
                    if (proposals.containsKey(prerequisite)) {
                        continue;
                    }
                    Integer authoredRank = profileRanks.get(prerequisite);
                    if (authoredRank == null) {
                        throw new IllegalArgumentException(
                                "Generated automatic prerequisite is absent from authored ranks");
                    }
                    result.merge(
                            dependent,
                            Math.addExact(authoredRank, 1),
                            Math::max);
                }
            });
        }
        return result;
    }

    private static boolean canPlace(
            int rank,
            int batchSize,
            Map<Integer, Integer> automaticWidths,
            Map<ResourceLocation, Map<Integer, Integer>> authoredWidths,
            int capacity) {
        int automaticWidth = automaticWidths.getOrDefault(rank, 0);
        if (Math.addExact(automaticWidth, batchSize) > capacity) {
            return false;
        }
        return authoredWidths.values().stream().allMatch(widths ->
                Math.addExact(
                        Math.addExact(widths.getOrDefault(rank, 0), automaticWidth),
                        batchSize) <= capacity);
    }

    private static void validateFinalRanks(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, Set<String>> parents,
            Collection<AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation, Map<String, Integer>> authoredRanks,
            int capacity,
            boolean mixedTopology) {
        Map<Integer, Long> widths = proposals.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        proposal -> proposal.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()));
        if (widths.values().stream().anyMatch(width -> width > capacity)) {
            throw new IllegalStateException(
                    "Automatic rank finalization exceeded the tree layer capacity");
        }
        if (!mixedTopology) {
            List<Integer> ranks = widths.keySet().stream().sorted().toList();
            for (int index = 0; index < ranks.size(); index++) {
                if (ranks.get(index) != index) {
                    throw new IllegalStateException(
                            "Automatic rank finalization retained an empty rank");
                }
            }
        }
        Map<Integer, Long> automaticWidths = widths;
        int maximumAutomaticEdgeSpan = ResearchTechTreeContract.automaticEdgeRankSpanLimit(
                automaticWidths.size(),
                com.gamergaming.taczweaponblueprints.resource.research
                        .BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH);
        authoredRanks.forEach((profile, ranks) -> {
            Map<Integer, Long> authoredWidths = new HashMap<>();
            ranks.values().forEach(rank ->
                    authoredWidths.merge(rank, 1L, Math::addExact));
            if (automaticWidths.entrySet().stream().anyMatch(entry ->
                    Math.addExact(
                            entry.getValue(),
                            authoredWidths.getOrDefault(entry.getKey(), 0L)) > capacity)) {
                throw new IllegalStateException(
                        "Mixed authored and automatic rank exceeded the tree layer capacity for "
                                + profile);
            }
        });
        parents.forEach((dependent, prerequisiteIds) -> prerequisiteIds.forEach(prerequisite -> {
            int prerequisiteRank = proposals.get(prerequisite)
                    .progressionCoordinate().rank();
            int dependentRank = proposals.get(dependent).progressionCoordinate().rank();
            if (prerequisiteRank >= dependentRank) {
                throw new IllegalStateException(
                        "Automatic rank finalization retained a same-rank or backward edge");
            }
            if (!mixedTopology
                    && dependentRank - prerequisiteRank
                            > maximumAutomaticEdgeSpan) {
                throw new IllegalStateException(
                        "Automatic rank finalization retained an edge spanning more than "
                                + maximumAutomaticEdgeSpan
                                + " ranks");
            }
        }));
        for (AutomaticWeaponPrerequisitePlan plan : plans) {
            Map<String, Integer> profileRanks = authoredRanks.getOrDefault(
                    plan.profileId(), Map.of());
            plan.prerequisites().forEach((dependentId, prerequisiteIds) -> {
                AutomaticWeaponPlacementProposal dependent = proposals.get(
                        dependentId.toString());
                if (dependent == null) {
                    return;
                }
                prerequisiteIds.stream()
                        .filter(id -> !proposals.containsKey(id.toString()))
                        .forEach(prerequisite -> {
                            Integer prerequisiteRank = profileRanks.get(prerequisite.toString());
                            if (prerequisiteRank == null
                                    || prerequisiteRank
                                            >= dependent.progressionCoordinate().rank()) {
                                throw new IllegalStateException(
                                        "Automatic rank finalization retained an invalid authored parent");
                            }
                        });
            });
        }
    }
}
