package com.gamergaming.taczweaponblueprints.research.tree;

/** Swappable pure layout boundary used by client projection composers. */
@FunctionalInterface
public interface ResearchTreeLayoutStrategy {
    ResearchTreeLayout layout(ResearchTreePublication publication);

    static ResearchTreeLayoutStrategy layered(ResearchTreeLayoutPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("research layout policy cannot be null");
        }
        return publication -> ResearchTreeLayeredLayoutEngine.layout(publication, policy);
    }
}
