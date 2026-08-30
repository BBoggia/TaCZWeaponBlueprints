package com.gamergaming.taczweaponblueprints.research.tree;

import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.LayoutDefinition;

/** Resolves one deterministic semantic layer width from tree policy and population. */
public final class ResearchTechTreeLayerWidthResolver {
    private ResearchTechTreeLayerWidthResolver() {
    }

    public static int resolve(LayoutDefinition layout, int weaponPopulation) {
        if (layout == null
                || weaponPopulation < 0
                || weaponPopulation > ResearchTreeGraph.MAX_NODES) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layer-width inputs are invalid");
        }
        if (!layout.dynamic()) {
            return layout.maxNodesPerLayer();
        }
        int populationWidth = landscapeWidth(weaponPopulation);
        return Math.max(
                layout.minNodesPerLayer(),
                Math.min(layout.maxNodesPerLayer(), populationWidth));
    }

    /** Targets a 4:3 semantic rank grid without floating-point thresholds. */
    private static int landscapeWidth(int value) {
        int result = 0;
        long scaledPopulation = Math.multiplyExact(4L, value);
        while (Math.multiplyExact(3L, (long) result * result) < scaledPopulation) {
            result++;
        }
        return result;
    }
}
