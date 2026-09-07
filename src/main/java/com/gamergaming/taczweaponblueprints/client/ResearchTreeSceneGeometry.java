package com.gamergaming.taczweaponblueprints.client;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Geometry assembly and structural fallback route helpers. */
final class ResearchTreeSceneGeometry {
    private ResearchTreeSceneGeometry() { }

    record PortalKey(ResourceLocation local,
            com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain domain,
            ResearchTechTreeProjection.Direction direction) { }

    static PortalKey portalKey(ResearchTechTreeProjection.BoundaryLink link) {
        return new PortalKey(link.localNodeId(), link.remoteDomain(), link.direction());
    }

    static ResearchTreeScene.Node node(ResourceLocation id, double x, double y,
            ResearchTreeScenePolicy.NodeMetrics metrics) {
        var body = new ResearchTreeScene.Bounds(x, y, metrics.width(), metrics.height());
        return new ResearchTreeScene.Node(id, body, body.inflate(metrics.paintPadding()));
    }

    static ResearchTreeScene assemble(ResearchTreeSceneInput input, String revision,
            List<ResearchTreeScene.Node> nodes, List<ResearchTreeScene.Requirement> groups,
            List<ResearchTreeScene.Route> routes, List<ResearchTreeScene.Boundary> boundaries) {
        ResearchTreeScene.Bounds extent = null;
        Map<ResourceLocation, ResearchTreeScene.Bounds> bandBounds = new LinkedHashMap<>();
        Map<ResourceLocation, ResearchTreeScene.Node> byId = new LinkedHashMap<>();
        for (var node : nodes) {
            byId.put(node.id(), node);
            extent = union(extent, node.paint());
        }
        for (var placement : input.topology().nodes()) {
            placement.bandId().ifPresent(id -> bandBounds.merge(id,
                    byId.get(placement.nodeId()).paint(), ResearchTreeScene.Bounds::union));
        }
        var bands = input.topology().bands().stream().map(id -> new ResearchTreeScene.Band(id,
                bandBounds.getOrDefault(id, new ResearchTreeScene.Bounds(0, 0, 0, 0)))).toList();
        for (var band : bands) extent = union(extent, band.bounds());
        for (var boundary : boundaries) extent = union(extent, boundary.bounds());
        for (var group : groups) {
            if (group.junction().isPresent()) {
                var p = group.junction().orElseThrow();
                extent = union(extent, new ResearchTreeScene.Bounds(p.x() - 4, p.y() - 4, 8, 8));
            }
        }
        for (var route : routes) {
            for (var p : route.points()) extent = union(extent, new ResearchTreeScene.Bounds(p.x(), p.y(), 0, 0));
        }
        if (extent == null) extent = new ResearchTreeScene.Bounds(0, 0, 0, 0);
        return new ResearchTreeScene(input.key(revision), extent, nodes, groups, routes, boundaries, bands);
    }

    private static ResearchTreeScene.Bounds union(ResearchTreeScene.Bounds a, ResearchTreeScene.Bounds b) {
        return a == null ? b : a.union(b);
    }

    /** Structural fallback only; these paths do not satisfy the grouped-rendering clearance contract. */
    static List<ResearchTreeScene.Route> outsideRoutes(ResearchTreeSceneInput input, List<ResearchTreeScene.Node> nodes) {
        Map<ResourceLocation, ResearchTreeScene.Node> positioned = new LinkedHashMap<>();
        nodes.forEach(n -> positioned.put(n.id(), n));
        double right = nodes.stream().mapToDouble(n -> n.body().right()).max().orElse(0);
        double padding = input.policy().nodeMetrics().paintPadding();
        var routes = new ArrayList<ResearchTreeScene.Route>();
        for (var edge : input.topology().edges()) {
            var source = positioned.get(edge.prerequisiteId()).body();
            var target = positioned.get(edge.dependentId()).body();
            double track = right + 128 + routes.size() * 2.0;
            double exit = source.y() - padding - 4;
            double approach = target.bottom() + padding + 4;
            routes.add(new ResearchTreeScene.Route(edge, List.of(
                    new ResearchTreeScene.Point(source.centerX(), source.y()),
                    new ResearchTreeScene.Point(source.centerX(), exit),
                    new ResearchTreeScene.Point(track, exit),
                    new ResearchTreeScene.Point(track, approach),
                    new ResearchTreeScene.Point(target.centerX(), approach),
                    new ResearchTreeScene.Point(target.centerX(), target.bottom()))));
        }
        return List.copyOf(routes);
    }

    static List<ResearchTreeScene.Boundary> outsideBoundaries(ResearchTreeSceneInput input, List<ResearchTreeScene.Node> nodes) {
        Map<ResourceLocation, ResearchTreeScene.Node> positioned = new LinkedHashMap<>();
        nodes.forEach(n -> positioned.put(n.id(), n));
        var targets = new LinkedHashMap<PortalKey, List<ResearchTechTreeProjection.BoundaryLink>>();
        for (var link : input.topology().boundaries()) {
            targets.computeIfAbsent(portalKey(link),
                    ignored -> new ArrayList<>()).add(link);
        }
        var boundaries = new ArrayList<ResearchTreeScene.Boundary>();
        var counts = new LinkedHashMap<ResourceLocation, Integer>();
        targets.forEach((key, links) -> {
            int column = counts.merge(key.local(), 1, Integer::sum) - 1;
            var body = positioned.get(key.local()).body();
            boundaries.add(new ResearchTreeScene.Boundary(links, new ResearchTreeScene.Bounds(
                    body.right() + input.policy().nodeMetrics().paintPadding() + 16 + column * 16, body.y(), 12, 12)));
        });
        return List.copyOf(boundaries);
    }
}
