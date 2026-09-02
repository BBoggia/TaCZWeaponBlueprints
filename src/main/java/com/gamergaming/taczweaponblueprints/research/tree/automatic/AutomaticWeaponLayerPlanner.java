package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;

/** Assigns bounded, stat-ordered semantic ranks to one complete automatic population. */
public final class AutomaticWeaponLayerPlanner {
    /** Compatibility alias for the default configurable foundation count. */
    public static final int DEFAULT_FOUNDATION_COUNT =
            AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT;

    public Map<String, AutomaticWeaponPlacementProposal> assign(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            AutomaticWeaponPlacementPolicy policy) {
        if (proposals == null || policy == null
                || proposals.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId()))) {
            throw new IllegalArgumentException(
                    "Automatic weapon layer planner inputs are invalid");
        }
        if (!policy.usesDynamicLayers() || proposals.isEmpty()) {
            return immutableById(proposals);
        }

        List<AutomaticWeaponPlacementProposal> ordered = proposals.values().stream()
                .sorted(Comparator
                        .comparingInt(AutomaticWeaponPlacementProposal::mechanicalScore)
                        .thenComparingLong(value -> value.position().siblingOrder())
                        .thenComparing(AutomaticWeaponPlacementProposal::blueprintId))
                .toList();
        Map<String, AutomaticWeaponPlacementProposal> assigned = new LinkedHashMap<>();
        int cursor = 0;
        int rank = 0;
        while (cursor < ordered.size()) {
            int layerSize = rank == 0
                    ? Math.min(
                            Math.min(policy.foundationCount(), policy.maxNodesPerRank()),
                            ordered.size())
                    : Math.min(policy.maxNodesPerRank(), ordered.size() - cursor);
            for (int index = 0; index < layerSize; index++) {
                AutomaticWeaponPlacementProposal proposal = ordered.get(cursor++);
                Optional<net.minecraft.resources.ResourceLocation> bandId = policy
                        .bandForScore(proposal.mechanicalScore())
                        .map(AutomaticWeaponProgressionBand::id);
                assigned.put(
                        proposal.blueprintId(),
                        proposal.withProgressionCoordinate(new ProgressionCoordinate(
                                rank,
                                proposal.position().siblingOrder(),
                                bandId)));
            }
            rank++;
        }
        if (cursor != ordered.size()
                || assigned.values().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                value -> value.progressionCoordinate().rank(),
                                java.util.stream.Collectors.counting()))
                        .values().stream()
                        .anyMatch(count -> count > policy.maxNodesPerRank())) {
            throw new IllegalStateException(
                    "Automatic weapon layer planner exceeded its bounded rank width");
        }
        return immutableById(assigned);
    }

    private static Map<String, AutomaticWeaponPlacementProposal> immutableById(
            Map<String, AutomaticWeaponPlacementProposal> source) {
        Map<String, AutomaticWeaponPlacementProposal> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                copy.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(copy);
    }

}
