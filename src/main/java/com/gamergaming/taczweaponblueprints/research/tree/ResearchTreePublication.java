package com.gamergaming.taczweaponblueprints.research.tree;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * One disclosure-safe authoritative graph plus view-specific presentation
 * metadata. Legacy group membership is intentionally allowed to be a subset
 * of the graph; Tech Tree domains own the remaining published kinds.
 */
public record ResearchTreePublication(
        ResearchTreeGraph graph,
        ResearchTreePresentation presentation,
        ResearchTechTreePresentation techTree) {
    public static final ResearchTreePublication EMPTY = new ResearchTreePublication(
            ResearchTreeGraph.EMPTY,
            ResearchTreePresentation.EMPTY,
            ResearchTechTreePresentation.EMPTY);

    public ResearchTreePublication(
            ResearchTreeGraph graph,
            ResearchTreePresentation presentation) {
        this(graph, presentation, ResearchTechTreePresentation.EMPTY);
    }

    public ResearchTreePublication {
        if (graph == null || presentation == null || techTree == null) {
            throw new IllegalArgumentException("research publication fields cannot be null");
        }
        validate(graph, presentation);
        techTree.validateAgainst(graph);
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
        if (!graphNodeIds.containsAll(presentationNodeIds)) {
            throw new IllegalArgumentException(
                    "research presentation references a node outside the authoritative graph");
        }
        for (ResearchTreeGraph.Edge edge : graph.edges()) {
            var prerequisite = presentation.membership(edge.prerequisiteId());
            var dependent = presentation.membership(edge.dependentId());
            if (prerequisite.isEmpty() || dependent.isEmpty()) {
                continue;
            }
            int prerequisiteRank = prerequisite.orElseThrow().rank();
            int dependentRank = dependent.orElseThrow().rank();
            if (prerequisiteRank >= dependentRank) {
                throw new IllegalArgumentException(
                        "research presentation ranks contradict a prerequisite edge");
            }
        }
    }

    /** Public node IDs owned by the legacy Branches and All Weapons views. */
    public Set<ResourceLocation> legacyNodeIds() {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        presentation.groups().forEach(group -> group.members().forEach(member ->
                ids.add(member.nodeId())));
        return Set.copyOf(ids);
    }

    /** Weapon-scoped graph used by legacy layout and projection code. */
    public ResearchTreeGraph legacyGraph() {
        return graph.inducedSubgraph(legacyNodeIds());
    }

    /**
     * Normalizes a complete publication for compatibility code that only knows
     * the legacy group model. The authoritative publication remains unchanged.
     */
    public ResearchTreePublication legacyView() {
        if (legacyNodeIds().size() == graph.nodes().size()) {
            return this;
        }
        ResearchTreeGraph legacyGraph = legacyGraph();
        return legacyGraph.nodes().isEmpty()
                ? EMPTY
                : new ResearchTreePublication(
                        legacyGraph,
                        presentation,
                        ResearchTechTreePresentation.EMPTY);
    }

    /** True when player-state changes can reuse group projection layouts. */
    public boolean hasSamePresentationTopology(ResearchTreePublication other) {
        return other != null
                && graph.hasSameLayoutTopology(other.graph)
                && presentation.hasSameTopology(other.presentation);
    }
}
