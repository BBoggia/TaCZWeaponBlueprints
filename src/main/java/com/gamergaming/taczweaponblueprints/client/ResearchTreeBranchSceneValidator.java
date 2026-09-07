package com.gamergaming.taczweaponblueprints.client;

import static com.gamergaming.taczweaponblueprints.client.ResearchTreeScene.atMost;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeScene.close;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;

/** Validates final ownership and envelopes, rather than trusting intermediate allocation measurements. */
final class ResearchTreeBranchSceneValidator {
    private ResearchTreeBranchSceneValidator() { }

    static void validate(ResearchTreeSceneInput input, ResearchTreeScene scene) {
        var forest = ResearchTreeBranchStructure.resolve(input);
        var metrics = input.policy().regionMetrics();
        var nodeMetrics = input.policy().nodeMetrics();
        var routingPlan = scene.routing().isPresent() ? new ResearchTreeRoutingPlan(input) : null;
        double side = metrics.padding() + (routingPlan == null ? metrics.routingGutter()
                : routingPlan.gutter(input, scene.regions().size(), scene.routing().orElseThrow().statistics().spacingRetries()));
        Map<String, ResearchTreeScene.Region> regions = new HashMap<>();
        Map<ResourceLocation, ResearchTreeScene.Node> nodes = new HashMap<>();
        Map<ResourceLocation, ResearchTechTreeProjection.Placement> placements = new HashMap<>();
        Map<ResourceLocation, ResearchTreeScene.Bounds> markerBounds = new HashMap<>();
        scene.boundaries().forEach(b -> markerBounds.merge(b.links().get(0).localNodeId(), b.bounds(), ResearchTreeScene.Bounds::union));
        scene.routing().ifPresent(r -> r.junctions().forEach(j -> markerBounds.merge(j.group().id().dependentId(),
                j.bounds(), ResearchTreeScene.Bounds::union)));
        scene.nodes().forEach(n -> nodes.put(n.id(), n));
        input.topology().nodes().forEach(p -> placements.put(p.nodeId(), p));
        for (var region : scene.regions()) {
            require(regions.put(region.seed().id(), region) == null, "duplicate scene region");
            require(region.seed().equals(forest.seed(region.seed().id())), "scene region ownership mismatch");
            require(scene.extent().contains(region.bounds()), "region outside scene extent");
            require(atMost(2 * side + metrics.labelWidth(), region.bounds().width()), "region label/gutter allowance lost");
        }
        require(regions.size() == forest.seeds().size(), "scene region membership mismatch");
        for (var seed : forest.seeds()) {
            var region = regions.get(seed.id());
            require(region != null, "missing scene region");
            int minimum = Integer.MAX_VALUE, maximum = -1;
            double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            for (var member : seed.directMembers()) {
                var node = nodes.get(member);
                var paint = markerBounds.containsKey(member) ? node.paint().union(markerBounds.get(member)) : node.paint();
                require(insideContent(region.bounds(), paint, side, metrics), "owned node/marker outside region content");
                int rank = placements.get(member).rank();
                minimum = Math.min(minimum, rank); maximum = Math.max(maximum, rank);
                top = Math.min(top, paint.y()); bottom = Math.max(bottom, paint.bottom());
                if (routingPlan != null) top = Math.min(top, routingPlan.contentTop(node));
                for (var child : forest.children(seed.id())) {
                    require(!overlaps(paint, regions.get(child.id()).bounds()), "parent-owned node overlaps child envelope");
                }
            }
            for (var child : forest.children(seed.id())) {
                var c = regions.get(child.id());
                require(c != null && insideContent(region.bounds(), c.bounds(), side, metrics), "child outside parent content");
                minimum = Math.min(minimum, c.minimumRank()); maximum = Math.max(maximum, c.maximumRank());
                top = Math.min(top, c.bounds().y()); bottom = Math.max(bottom, c.bounds().bottom());
            }
            require(region.minimumRank() == minimum && region.maximumRank() == maximum, "region occupied range mismatch");
            require(close(region.bounds().y(), top - metrics.padding() - metrics.headerHeight())
                    && close(region.bounds().bottom(), bottom + metrics.padding()), "region is not bounded by its occupied content");
            separated(forest.children(seed.id()), regions, metrics.gap());
            var rows = seed.directMembers().stream().map(placements::get)
                    .collect(Collectors.groupingBy(ResearchTechTreeProjection.Placement::rank));
            for (var row : rows.values()) {
                var ordered = row.stream().sorted(ResearchTreeBranchStructure.NODE_ORDER).toList();
                ResearchTreeScene.Node previous = null;
                for (var p : ordered) {
                    var n = nodes.get(p.nodeId());
                    if (previous != null) require(atMost(previous.paint().right()
                            + input.policy().spacing().nodeGap(), n.paint().x()), "owned row order or spacing changed");
                    previous = n;
                }
            }
        }
        // Parent containment plus sibling/root gaps implies separation of all non-ancestors.
        separated(forest.roots(), regions, metrics.gap());

        var rows = input.topology().nodes().stream().collect(Collectors.groupingBy(
                ResearchTechTreeProjection.Placement::rank, TreeMap::new, Collectors.toList()));
        var rankIndex = new TreeMap<Integer, ResearchTreeScene.OccupiedRank>();
        for (var rank : scene.occupiedRanks()) require(rankIndex.put(rank.rank(), rank) == null, "duplicate occupied rank");
        require(rankIndex.keySet().equals(rows.keySet()), "occupied-rank membership mismatch");
        require(scene.occupiedRanks().equals(List.copyOf(rankIndex.values())), "occupied ranks are not canonical");
        Map<ResourceLocation, Integer> groupCounts = new HashMap<>();
        input.topology().groups().forEach(g -> groupCounts.merge(g.id().dependentId(), 1, Integer::sum));
        ResearchTreeScene.OccupiedRank above = null;
        for (var row : rankIndex.descendingMap().values()) {
            int depth = rows.get(row.rank()).stream().mapToInt(p -> forest.depth(forest.owner(p.nodeId()))).max().orElseThrow();
            int groups = rows.get(row.rank()).stream().mapToInt(p -> groupCounts.getOrDefault(p.nodeId(), 0)).max().orElse(0);
            require(atMost(depth * (metrics.padding() + metrics.headerHeight()) + nodeMetrics.paintPadding(), row.above())
                    && atMost(depth * metrics.padding() + nodeMetrics.paintPadding() + groups * metrics.junctionSpacing(), row.below()),
                    "occupied rank loses label, port, or group allowance");
            if (above != null) require(atMost(above.bodyY() + nodeMetrics.height() + above.below(), row.bodyY() - row.above()),
                    "occupied ranks overlap or reverse progression");
            for (var p : rows.get(row.rank())) require(nodes.get(p.nodeId()).body().y() == row.bodyY(),
                    "node left the shared occupied rank");
            above = row;
        }
    }

    private static boolean insideContent(ResearchTreeScene.Bounds outer, ResearchTreeScene.Bounds inner,
            double side, ResearchTreeScenePolicy.RegionMetrics metrics) {
        return atMost(outer.x() + side, inner.x()) && atMost(inner.right(), outer.right() - side)
                && atMost(outer.y() + metrics.padding() + metrics.headerHeight(), inner.y())
                && atMost(inner.bottom(), outer.bottom() - metrics.padding());
    }

    private static void separated(List<ResearchTreeBranchStructure.Seed> siblings,
            Map<String, ResearchTreeScene.Region> regions, double gap) {
        ResearchTreeScene.Region previous = null;
        for (var seed : siblings) {
            var region = regions.get(seed.id());
            if (previous != null) require(atMost(previous.bounds().right() + gap, region.bounds().x()),
                    "branch sibling order or reserved gap changed");
            previous = region;
        }
    }

    private static boolean overlaps(ResearchTreeScene.Bounds a, ResearchTreeScene.Bounds b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
