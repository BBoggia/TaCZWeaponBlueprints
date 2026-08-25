package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** One disclosure-safe graph and the presentation metadata derived for it. */
public record ResearchTreePublication(
        ResearchTreeGraph graph,
        ResearchTreePresentation presentation) {
    public static final ResearchTreePublication EMPTY = new ResearchTreePublication(
            ResearchTreeGraph.EMPTY,
            ResearchTreePresentation.EMPTY);

    public ResearchTreePublication {
        if (graph == null || presentation == null) {
            throw new IllegalArgumentException("research publication fields cannot be null");
        }
        validate(graph, presentation);
    }

    private static void validate(
            ResearchTreeGraph graph,
            ResearchTreePresentation presentation) {
        Set<ResourceLocation> graphNodeIds = new LinkedHashSet<>();
        graph.nodes().forEach(node -> graphNodeIds.add(node.blueprintId()));
        Set<ResourceLocation> presentationNodeIds = new LinkedHashSet<>();

        for (ResearchTreePresentation.Group group : presentation.groups()) {
            for (ResearchTreePresentation.Member member : group.members()) {
                ResearchTreeGraph.Node node = graph.node(member.nodeId()).orElseThrow(() ->
                        new IllegalArgumentException("research group member references an unknown graph node"));
                presentationNodeIds.add(member.nodeId());
                if (group.kind() == ResearchTreePresentation.Kind.UNDISCLOSED) {
                    if (node.visibility().revealsIdentity()) {
                        throw new IllegalArgumentException(
                                "identity-disclosed node cannot belong to the Undisclosed group");
                    }
                } else if (!node.visibility().revealsIdentity()) {
                    throw new IllegalArgumentException(
                            "anonymous node cannot belong to an identifying research group");
                }
            }
            group.iconNodeId().ifPresent(iconNodeId -> {
                ResearchTreeGraph.Node icon = graph.node(iconNodeId).orElseThrow(() ->
                        new IllegalArgumentException("research group icon references an unknown graph node"));
                if (!icon.visibility().revealsIcon()) {
                    throw new IllegalArgumentException("research group icon identifies an anonymous node");
                }
            });
        }
        if (!graphNodeIds.equals(presentationNodeIds)) {
            throw new IllegalArgumentException(
                    "research presentation must assign every public graph node exactly once");
        }
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            int prerequisiteRank = presentation.membership(edge.prerequisiteId())
                    .orElseThrow()
                    .rank();
            int dependentRank = presentation.membership(edge.dependentId())
                    .orElseThrow()
                    .rank();
            if (prerequisiteRank >= dependentRank) {
                throw new IllegalArgumentException(
                        "research presentation ranks contradict a prerequisite edge");
            }
        }
        if (graph.nodes().isEmpty() != presentation.groups().isEmpty()) {
            throw new IllegalArgumentException("empty research graph and presentation do not match");
        }
    }

    /** True when player-state changes can reuse group projection layouts. */
    public boolean hasSamePresentationTopology(ResearchTreePublication other) {
        return other != null
                && graph.hasSameLayoutTopology(other.graph)
                && presentation.hasSameTopology(other.presentation);
    }
}
