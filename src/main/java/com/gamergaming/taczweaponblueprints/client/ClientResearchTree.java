package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

/** Atomic client publication of one validated graph and its derived layout. */
public final class ClientResearchTree {
    private ClientResearchTree() {
    }

    public static Publication publication() {
        ClientResearchState.Publication state = ClientResearchState.publication();
        return new Publication(state.graph(), state.presentation(), state.layout());
    }

    public static ResearchTreeGraph graph() {
        return ClientResearchState.publication().graph();
    }

    public static ResearchTreeLayout layout() {
        return ClientResearchState.publication().layout();
    }

    public static ResearchTreePresentation presentation() {
        return ClientResearchState.publication().presentation();
    }

    public static void publish(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("completed research tree publication cannot be null");
        }
        ClientResearchState.publishTreeOnly(publication);
    }

    public static void clear() {
        ClientResearchState.clear();
    }

    public record Publication(
            ResearchTreeGraph graph,
            ResearchTreePresentation presentation,
            ResearchTreeLayout layout) {
        public Publication {
            if (graph == null || presentation == null || layout == null
                    || graph.nodes().size() != layout.nodes().size()) {
                throw new IllegalArgumentException("invalid client research tree publication");
            }
            new ResearchTreePublication(graph, presentation);
            for (int ordinal = 0; ordinal < graph.nodes().size(); ordinal++) {
                if (!graph.nodes().get(ordinal).blueprintId()
                        .equals(layout.nodes().get(ordinal).blueprintId())) {
                    throw new IllegalArgumentException("client research graph and layout do not match");
                }
            }
        }
    }
}
