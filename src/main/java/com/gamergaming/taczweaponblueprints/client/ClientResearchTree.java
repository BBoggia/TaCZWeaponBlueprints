package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeUnifiedLayoutEngine;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;

/** Compatibility facade that derives shared-kernel geometry lazily from atomic client data. */
public final class ClientResearchTree {
    private ClientResearchTree() {
    }

    public static Publication publication() {
        ClientResearchState.Publication state = ClientResearchState.publication();
        ResearchTreePublication tree = new ResearchTreePublication(
                state.graph(), state.presentation(), state.techTree());
        return new Publication(
                state.graph(),
                state.presentation(),
                state.techTree(),
                ResearchTreeUnifiedLayoutEngine.layout(tree));
    }

    public static ResearchTreeGraph graph() {
        return ClientResearchState.publication().graph();
    }

    public static ResearchTreeLayout layout() {
        ClientResearchState.Publication state = ClientResearchState.publication();
        return ResearchTreeUnifiedLayoutEngine.layout(new ResearchTreePublication(
                state.graph(), state.presentation()));
    }

    public static ResearchTreePresentation presentation() {
        return ClientResearchState.publication().presentation();
    }

    public static ResearchTechTreePresentation techTree() {
        return ClientResearchState.publication().techTree();
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
            ResearchTechTreePresentation techTree,
            ResearchTreeLayout layout) {
        public Publication {
            if (graph == null || presentation == null || techTree == null || layout == null) {
                throw new IllegalArgumentException("invalid client research tree publication");
            }
            ResearchTreePublication authoritative = new ResearchTreePublication(
                    graph, presentation, techTree);
            ResearchTreeGraph legacyGraph = authoritative.legacyGraph();
            if (legacyGraph.nodes().size() != layout.nodes().size()) {
                throw new IllegalArgumentException(
                        "client legacy research graph and layout sizes do not match");
            }
            for (int ordinal = 0; ordinal < legacyGraph.nodes().size(); ordinal++) {
                if (!legacyGraph.nodes().get(ordinal).blueprintId()
                        .equals(layout.nodes().get(ordinal).blueprintId())) {
                    throw new IllegalArgumentException(
                            "client legacy research graph and layout do not match");
                }
            }
        }
    }
}
