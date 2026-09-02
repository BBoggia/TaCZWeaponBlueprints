package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/** Session-local tracked research goal; cleared at the client connection boundary. */
public final class ClientResearchPlannerState {
    private static ResourceLocation targetId;

    private ClientResearchPlannerState() {
    }

    public static synchronized Optional<ResourceLocation> targetId() {
        return Optional.ofNullable(targetId);
    }

    public static synchronized boolean track(
            ResearchTreeGraph graph,
            ResourceLocation blueprintId) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree planner graph cannot be null");
        }
        Optional<ResearchTreeGraph.Node> target = graph.node(blueprintId)
                .filter(node -> node.visibility().revealsIdentity());
        if (target.isEmpty()) {
            return false;
        }
        targetId = target.orElseThrow().blueprintId();
        return true;
    }

    public static synchronized void retain(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree planner graph cannot be null");
        }
        if (targetId != null && graph.node(targetId)
                .filter(node -> node.visibility().revealsIdentity()).isEmpty()) {
            targetId = null;
        }
    }

    public static synchronized void clear() {
        targetId = null;
    }
}
