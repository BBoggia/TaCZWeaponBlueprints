package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/** Pure selection authority decision, independent from camera and networking. */
final class ResearchTreeSelectionController {
    Optional<Decision> resolve(ResearchTreeGraph graph, ResourceLocation blueprintId) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree selection graph cannot be null");
        }
        if (blueprintId == null) {
            return Optional.empty();
        }
        return graph.node(blueprintId).map(node -> new Decision(
                node,
                ResearchTreeInteractionPolicy.allowsServerSelection(node)));
    }

    record Decision(ResearchTreeGraph.Node node, boolean sendAuthoritativeSelection) {
        Decision {
            if (node == null) {
                throw new IllegalArgumentException("Research Tree selection node cannot be null");
            }
        }
    }
}
