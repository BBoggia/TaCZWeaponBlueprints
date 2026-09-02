package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot.TechTreeEntryBinding;

import net.minecraft.resources.ResourceLocation;

/** Compiles authored placement metadata into strict rank-based progression coordinates. */
final class ResearchTechTreeProgressionResolver {
    private ResearchTechTreeProgressionResolver() {
    }

    static Map<ResourceLocation, ProgressionCoordinate> resolve(
            ResourceLocation profileId,
            Map<ResourceLocation, List<ResourceLocation>> prerequisiteGraph,
            Map<ResourceLocation, TechTreeEntryBinding> placements) {
        if (profileId == null || prerequisiteGraph == null || placements == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree progression inputs cannot be null");
        }

        Set<ResourceLocation> allIds = new LinkedHashSet<>(prerequisiteGraph.keySet());
        prerequisiteGraph.values().forEach(allIds::addAll);
        List<ResourceLocation> orderedIds = new ArrayList<>(allIds);
        orderedIds.sort(Comparator.comparing(ResourceLocation::toString));

        Map<ResourceLocation, ProgressionCoordinate> resolved = new LinkedHashMap<>();
        Set<ResourceLocation> visiting = new LinkedHashSet<>();
        for (ResourceLocation blueprintId : orderedIds) {
            resolveOne(
                    profileId,
                    blueprintId,
                    prerequisiteGraph,
                    placements,
                    resolved,
                    visiting);
        }

        Map<ResourceLocation, ProgressionCoordinate> stable = new LinkedHashMap<>();
        orderedIds.forEach(id -> {
            ProgressionCoordinate coordinate = resolved.get(id);
            if (coordinate != null) {
                stable.put(id, coordinate);
            }
        });
        return Collections.unmodifiableMap(stable);
    }

    private static ProgressionCoordinate resolveOne(
            ResourceLocation profileId,
            ResourceLocation blueprintId,
            Map<ResourceLocation, List<ResourceLocation>> graph,
            Map<ResourceLocation, TechTreeEntryBinding> placements,
            Map<ResourceLocation, ProgressionCoordinate> resolved,
            Set<ResourceLocation> visiting) {
        ProgressionCoordinate existing = resolved.get(blueprintId);
        if (existing != null) {
            return existing;
        }
        TechTreeEntryBinding dependent = placements.get(blueprintId);
        if (dependent == null) {
            return null;
        }
        if (!visiting.add(blueprintId)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree progression cycle for profile " + profileId);
        }

        try {
            ProgressionCoordinate initial = initial(dependent);
            int resolvedRank = initial.rank();
            for (ResourceLocation prerequisiteId : graph.getOrDefault(blueprintId, List.of())) {
                TechTreeEntryBinding prerequisite = placements.get(prerequisiteId);
                if (prerequisite == null) {
                    continue;
                }
                ProgressionCoordinate prerequisiteCoordinate = resolveOne(
                        profileId,
                        prerequisiteId,
                        graph,
                        placements,
                        resolved,
                        visiting);
                if (prerequisiteCoordinate == null) {
                    continue;
                }

                if (dependent.bundle().format() == ResearchTechTreeEntryBundle.CURRENT_FORMAT) {
                    if (!ResearchTechTreeContract.progressionTransitionAllowed(
                            prerequisiteCoordinate, initial)) {
                        throw invalidExplicitEdge(
                                profileId,
                                blueprintId,
                                prerequisiteId,
                                initial.rank(),
                                prerequisiteCoordinate.rank());
                    }
                    continue;
                }

                if (prerequisite.bundle().format() == ResearchTechTreeEntryBundle.LEGACY_FORMAT) {
                    ProgressionCoordinate prerequisiteInitial = initial(prerequisite);
                    if (prerequisiteInitial.rank() > initial.rank()
                            || (prerequisiteInitial.rank() == initial.rank()
                                    && prerequisiteInitial.siblingOrder() >= initial.siblingOrder())) {
                        throw new IllegalArgumentException(
                                "legacy Research Tech Tree placement for " + blueprintId
                                        + " must appear after prerequisite " + prerequisiteId
                                        + " for profile " + profileId);
                    }
                }
                resolvedRank = Math.max(
                        resolvedRank,
                        Math.addExact(prerequisiteCoordinate.rank(), 1));
            }

            ProgressionCoordinate coordinate = initial.withRank(resolvedRank);
            resolved.put(blueprintId, coordinate);
            return coordinate;
        } finally {
            visiting.remove(blueprintId);
        }
    }

    private static ProgressionCoordinate initial(TechTreeEntryBinding binding) {
        return binding.entry().initialProgressionCoordinate(binding.bundle().format());
    }

    private static IllegalArgumentException invalidExplicitEdge(
            ResourceLocation profileId,
            ResourceLocation dependentId,
            ResourceLocation prerequisiteId,
            int dependentRank,
            int prerequisiteRank) {
        return new IllegalArgumentException(
                "format-2 Research Tech Tree rank " + dependentRank + " for " + dependentId
                        + " must be greater than prerequisite rank " + prerequisiteRank
                        + " for " + prerequisiteId + " in profile " + profileId);
    }
}
