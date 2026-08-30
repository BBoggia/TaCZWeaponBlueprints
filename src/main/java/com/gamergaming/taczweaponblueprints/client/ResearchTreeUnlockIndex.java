package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable AND-aware counts of dependents that are, or would become,
 * researchable after one prerequisite is learned.
 */
public final class ResearchTreeUnlockIndex {
    public static final ResearchTreeUnlockIndex EMPTY = new ResearchTreeUnlockIndex(
            Map.of(), Map.of(), Set.of());

    private final Map<ResourceLocation, Integer> unlocksAfterLearning;
    private final Map<ResourceLocation, Integer> availableDependents;
    private final Set<ResourceLocation> learnedBlueprints;

    private ResearchTreeUnlockIndex(
            Map<ResourceLocation, Integer> unlocksAfterLearning,
            Map<ResourceLocation, Integer> availableDependents,
            Set<ResourceLocation> learnedBlueprints) {
        this.unlocksAfterLearning = unlocksAfterLearning;
        this.availableDependents = availableDependents;
        this.learnedBlueprints = learnedBlueprints;
    }

    public static ResearchTreeUnlockIndex create(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree unlock graph cannot be null");
        }
        if (graph.nodes().isEmpty()) {
            return EMPTY;
        }
        Map<ResourceLocation, ResearchTreeGraph.Node> nodes = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> requirements = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> future = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> available = new LinkedHashMap<>();
        java.util.LinkedHashSet<ResourceLocation> learned = new java.util.LinkedHashSet<>();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            nodes.put(node.blueprintId(), node);
            requirements.put(node.blueprintId(), new ArrayList<>());
            future.put(node.blueprintId(), 0);
            available.put(node.blueprintId(), 0);
            if (node.learned()) {
                learned.add(node.blueprintId());
            }
        }
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            requirements.get(edge.dependentId()).add(edge.prerequisiteId());
        }
        for (ResearchTreeGraph.Node dependent : graph.nodes()) {
            if (dependent.learned() || !dependent.visibility().revealsIdentity()) {
                continue;
            }
            List<ResourceLocation> prerequisites = requirements.get(dependent.blueprintId());
            if (dependent.availability()
                    == ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED) {
                ResourceLocation onlyUnlearned = null;
                int unlearnedCount = 0;
                for (ResourceLocation prerequisite : prerequisites) {
                    if (!nodes.get(prerequisite).learned()) {
                        onlyUnlearned = prerequisite;
                        unlearnedCount++;
                    }
                }
                if (unlearnedCount == 1) {
                    increment(future, onlyUnlearned);
                }
            } else if (dependent.availability()
                    == ResearchTreeGraph.Availability.AVAILABLE) {
                for (ResourceLocation prerequisite : prerequisites) {
                    if (nodes.get(prerequisite).learned()) {
                        increment(available, prerequisite);
                    }
                }
            }
        }
        return new ResearchTreeUnlockIndex(
                Map.copyOf(future), Map.copyOf(available), Set.copyOf(learned));
    }

    /**
     * Counts dependents whose last missing prerequisite is this unlearned node.
     * For a learned node, counts its currently available direct dependents.
     */
    public int immediateUnlockCount(ResourceLocation blueprintId) {
        if (blueprintId == null) {
            return 0;
        }
        return learnedBlueprints.contains(blueprintId)
                ? availableDependents.getOrDefault(blueprintId, 0)
                : unlocksAfterLearning.getOrDefault(blueprintId, 0);
    }

    public int unlocksAfterLearning(ResourceLocation blueprintId) {
        return blueprintId == null
                ? 0
                : unlocksAfterLearning.getOrDefault(blueprintId, 0);
    }

    private static void increment(
            Map<ResourceLocation, Integer> counts,
            ResourceLocation blueprintId) {
        counts.computeIfPresent(
                blueprintId, (ignored, count) -> Math.addExact(count, 1));
    }
}
