package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;

/**
 * Bridge to the current 24-pixel engine. The public projection is private adapter context;
 * the returned scene contains only geometry and must match the state-free input exactly.
 */
public final class ResearchTreeCompatibilitySceneEngine implements ResearchTreeSceneEngine {
    public static final String REVISION = "compatibility-v1";
    private final ResearchTechTreeProjection projection;

    public ResearchTreeCompatibilitySceneEngine(ResearchTechTreeProjection projection) {
        this.projection = java.util.Objects.requireNonNull(projection);
    }

    private ResearchTechTreeProjection canonicalProjection() {
        // Canonicalize the old ordinal-based engine so equivalent public table orders agree.
        var ids = projection.graph().nodes().stream().map(n -> n.blueprintId())
                .sorted(Comparator.comparing(Object::toString)).toList();
        var graph = projection.graph().orderedInducedSubgraph(ids);
        var placements = new LinkedHashMap<net.minecraft.resources.ResourceLocation, ResearchTechTreeProjection.Placement>();
        ids.forEach(id -> placements.put(id, projection.placements().get(id)));
        return new ResearchTechTreeProjection(projection.domain(), projection.presentation(), graph,
                placements, projection.boundaryLinks(), projection.bands(), projection.maxNodesPerLayer());
    }

    @Override public String revision() { return REVISION; }

    @Override public ResearchTreeScene layout(ResearchTreeSceneInput input) {
        if (!input.withBranchSeeds(java.util.List.of()).topology().equals(
                ResearchTreeSceneInput.from(projection, input.policy()).topology())) {
            throw new IllegalArgumentException("compatibility projection does not match scene input");
        }
        if (!input.policy().nodeMetrics().equals(ResearchTreeScenePolicy.NodeMetrics.COMPATIBILITY)) {
            throw new IllegalArgumentException("compatibility engine requires its 24-pixel node metrics");
        }
        var canonical = canonicalProjection();
        var old = ResearchTechTreeLayoutEngine.layout(canonical, input.policy().spacing(), input.policy().viewportWidth());
        var index = ResearchTreeEdgeIndex.create(canonical.graph(), old.graphLayout(),
                ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
        var nodes = input.topology().nodes().stream().map(p -> {
            var position = old.graphLayout().position(p.nodeId()).orElseThrow();
            return ResearchTreeSceneGeometry.node(p.nodeId(), position.x(), position.y(), input.policy().nodeMetrics());
        }).toList();
        var junctions = new HashMap<ResearchTreeSceneInput.GroupId, ResearchTreeScene.Point>();
        index.requirementJunctions().forEach(g -> junctions.put(
                new ResearchTreeSceneInput.GroupId(g.key().dependentId(), g.key().ordinal()),
                new ResearchTreeScene.Point(g.x(), g.y())));
        var groups = input.topology().groups().stream().map(g -> new ResearchTreeScene.Requirement(g,
                Optional.ofNullable(junctions.get(g.id())))).toList();
        var routed = new HashMap<com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Edge, ResearchTreeScene.Route>();
        index.visible(-1_000_000, -1_000_000, 1_000_000, 1_000_000).forEach(edge -> {
            var points = new ArrayList<ResearchTreeScene.Point>();
            edge.points().forEach(p -> points.add(new ResearchTreeScene.Point(p.x(), p.y())));
            // The old polyline stops at the arrow base; include its actual blueprint endpoint.
            points.add(new ResearchTreeScene.Point(edge.endX(), edge.endY()));
            routed.put(edge.edge(), new ResearchTreeScene.Route(edge.edge(), points));
        });
        var routes = input.topology().edges().stream().map(routed::get).toList();
        var boundaries = old.portals().stream().map(p -> new ResearchTreeScene.Boundary(p.target().links(),
                new ResearchTreeScene.Bounds(p.x(), p.y(), ResearchTreeLayout.PORTAL_SIZE, ResearchTreeLayout.PORTAL_SIZE))).toList();
        return ResearchTreeSceneGeometry.assemble(input, revision(), nodes, groups, routes, boundaries);
    }
}
