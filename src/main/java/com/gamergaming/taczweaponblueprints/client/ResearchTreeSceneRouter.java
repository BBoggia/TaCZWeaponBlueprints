package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Orthogonal routing in measured rank escape rows and containment gutters. */
final class ResearchTreeSceneRouter {
    static final class CapacityException extends IllegalArgumentException {
        CapacityException(String message) { super(message); }
    }
    static final class Budget {
        long work;
        void use(long amount) {
            work += amount;
            if (work > ResearchTreeRouting.MAX_WORK) throw new IllegalArgumentException("routing work budget exhausted");
        }
    }
    private ResearchTreeSceneRouter() { }

    static ResearchTreeRouting route(ResearchTreeSceneInput input, ResearchTreeRoutingPlan plan,
            List<ResearchTreeScene.Node> nodes, List<ResearchTreeRouting.Junction> junctions,
            List<ResearchTreeScene.Boundary> boundaries, List<ResearchTreeScene.Region> regions,
            int retry, Budget budget) {
        var byId = new HashMap<ResourceLocation, ResearchTreeScene.Node>();
        nodes.forEach(n -> byId.put(n.id(), n));
        var forest = ResearchTreeBranchStructure.resolve(input);
        var byRegion = new HashMap<String, ResearchTreeScene.Region>();
        regions.forEach(r -> byRegion.put(r.seed().id(), r));
        var obstacles = new ResearchTreeRoutingValidator.Obstacles(nodes, junctions, boundaries, plan.headers(input, regions, retry));
        var reservations = new ResearchTreeRoutingValidator.Reservations();
        var result = new ArrayList<ResearchTreeRouting.Connection>();
        var groupIndex = new HashMap<ResearchTreeSceneInput.GroupId, ResearchTreeRouting.Junction>();
        var maximumBottom = new HashMap<Integer, Double>();
        var usedTracks = new LinkedHashSet<Double>();
        for (var j : junctions) {
            groupIndex.put(j.group().id(), j);
            maximumBottom.merge(plan.ranks.get(j.group().id().dependentId()), j.bounds().bottom(), Math::max);
            var target = byId.get(j.group().id().dependentId());
            var groups = plan.groups.get(target.id());
            int ordinal = groups.indexOf(j.group());
            double x = target.body().x() + target.body().width() * (ordinal + 1) / (groups.size() + 1);
            double y = target.paint().bottom() + plan.track * (ordinal + 1);
            add(result, reservations, obstacles, budget, new ResearchTreeRouting.Connection(
                    ResearchTreeRouting.Owner.trunk(j.group().id()), compact(List.of(j.outputPort(), point(j.outputPort().x(), y),
                    point(x, y), point(x, target.body().bottom())))));
        }
        var boundaryCounts = new HashMap<ResourceLocation, Integer>();
        for (var b : boundaries) boundaryCounts.merge(b.links().get(0).localNodeId(), 1, Integer::sum);
        var boundaryOrdinals = new HashMap<ResourceLocation, Integer>();
        for (int i = 0; i < boundaries.size(); i++) {
            var b = boundaries.get(i); var node = byId.get(b.links().get(0).localNodeId());
            int ordinal = boundaryOrdinals.merge(node.id(), 1, Integer::sum);
            double y = node.body().y() + node.body().height() * ordinal / (boundaryCounts.get(node.id()) + 1);
            double side = node.paint().right() + plan.track * ordinal;
            double top = Math.min(node.paint().y(), b.bounds().y()) - plan.track * ordinal;
            add(result, reservations, obstacles, budget, new ResearchTreeRouting.Connection(ResearchTreeRouting.Owner.boundary(i),
                    compact(List.of(point(node.body().right(), y), point(side, y), point(side, top),
                            point(b.bounds().centerX(), top), point(b.bounds().centerX(), b.bounds().y())))));
        }
        var sourceOrdinals = new HashMap<ResourceLocation, Integer>();
        var rowExits = new HashMap<Integer, Integer>();
        var rowArrivals = new HashMap<Integer, Integer>();
        record Alternative(ResearchTreeRouting.Owner owner, ResourceLocation source, ResourceLocation target,
                ResearchTreeScene.Point start, ResearchTreeScene.Point end, double exit, double approach) { }
        var alternatives = new ArrayList<Alternative>();
        int congested = 0;
        for (var group : input.topology().groups()) {
            var junction = groupIndex.get(group.id());
            int targetRank = plan.ranks.get(group.id().dependentId());
            for (int i = 0; i < group.alternatives().size(); i++) {
                var id = group.alternatives().get(i); var source = byId.get(id);
                int sourceOrdinal = sourceOrdinals.merge(id, 1, Integer::sum);
                double sourceX = source.body().x() + source.body().width() * sourceOrdinal / (plan.outgoing.get(id) + 1);
                var start = point(sourceX, source.body().y()); var end = junction.inputPorts().get(i);
                double exit = source.paint().y() - plan.track * (6 + rowExits.merge(plan.ranks.get(id), 1, Integer::sum));
                double approach = maximumBottom.get(targetRank) + plan.track * rowArrivals.merge(targetRank, 1, Integer::sum);
                var owner = ResearchTreeRouting.Owner.alternative(group.id(), id);
                alternatives.add(new Alternative(owner, id, group.id().dependentId(), start, end, exit, approach));
                // Protect all fixed port stems before choosing any optional direct path or corridor.
                // A locally clear early connection must not consume a later connection's only exit.
                reservations.add(new ResearchTreeRouting.Connection(owner, List.of(start, point(start.x(), exit))));
                reservations.add(new ResearchTreeRouting.Connection(owner, List.of(end, point(end.x(), approach))));
            }
        }
        for (var alternative : alternatives) {
            var owner = alternative.owner(); var start = alternative.start(); var end = alternative.end();
            double exit = alternative.exit(); double approach = alternative.approach();
            ResearchTreeRouting.Connection chosen = null;
            if (forest.owner(alternative.source()).equals(forest.owner(alternative.target()))) {
                var direct = new ResearchTreeRouting.Connection(owner, compact(List.of(start,
                        point(start.x(), approach), point(end.x(), approach), end)));
                if (reservations.clear(direct, budget) && obstacles.clear(direct, budget)) chosen = direct;
            }
            var candidates = chosen != null ? List.<Double>of() : tracks(forest, byRegion,
                    forest.owner(alternative.source()), forest.owner(alternative.target()),
                    input, plan.gutter(input, regions.size(), retry), budget);
            for (double track : candidates) {
                budget.use(1);
                var candidate = new ResearchTreeRouting.Connection(owner, compact(List.of(start, point(start.x(), exit),
                        point(track, exit), point(track, approach), point(end.x(), approach), end)));
                if (reservations.clear(candidate, budget) && obstacles.clear(candidate, budget)) {
                    chosen = candidate; usedTracks.add(track); break;
                }
                congested++;
            }
            if (chosen == null) throw new CapacityException("reserved routing gutters exhausted for " + owner);
            result.add(chosen); reservations.add(chosen);
        }
        var crossings = ResearchTreeRoutingValidator.crossings(result, budget);
        int bends = result.stream().mapToInt(c -> c.points().size() - 2).sum();
        double min = result.stream().flatMap(c -> c.points().stream()).mapToDouble(ResearchTreeScene.Point::x).min().orElse(0);
        double max = result.stream().flatMap(c -> c.points().stream()).mapToDouble(ResearchTreeScene.Point::x).max().orElse(0);
        return new ResearchTreeRouting(junctions, result, crossings,
                new ResearchTreeRouting.Statistics(retry, bends, usedTracks.size(), congested, max - min, budget.work));
    }

    private static void add(List<ResearchTreeRouting.Connection> result, ResearchTreeRoutingValidator.Reservations reservations,
            ResearchTreeRoutingValidator.Obstacles obstacles, Budget budget, ResearchTreeRouting.Connection connection) {
        if (!reservations.clear(connection, budget) || !obstacles.clear(connection, budget)) {
            throw new CapacityException("connector port fan-out collides: " + connection.owner());
        }
        result.add(connection); reservations.add(connection);
    }

    private static List<Double> tracks(ResearchTreeBranchStructure forest, Map<String, ResearchTreeScene.Region> regions,
            String source, String target, ResearchTreeSceneInput input, double gutter, Budget budget) {
        var owners = new LinkedHashSet<String>();
        owners.add(source); owners.add(target);
        for (String id : List.of(source, target)) {
            var parent = forest.seed(id).parentId();
            while (parent.isPresent()) { owners.add(parent.get()); parent = forest.seed(parent.get()).parentId(); }
        }
        // Shared corridors are resources, not a second containment tree. Region links may return or cross.
        double center = (regions.get(source).bounds().centerX() + regions.get(target).bounds().centerX()) / 2;
        budget.use(regions.size());
        regions.values().stream().sorted(java.util.Comparator
                .comparingDouble((ResearchTreeScene.Region r) -> Math.abs(r.bounds().centerX() - center))
                .thenComparing(r -> r.seed().id())).forEach(r -> owners.add(r.seed().id()));
        var result = new LinkedHashSet<Double>();
        double padding = input.policy().regionMetrics().padding();
        for (var id : owners) {
            var bounds = regions.get(id).bounds();
            double spacing = input.policy().regionMetrics().routingTrackSpacing();
            for (double offset = spacing; offset < gutter; offset += spacing) {
                budget.use(1);
                result.add(bounds.x() + padding + offset);
                result.add(bounds.right() - padding - offset);
                if (result.size() >= 4096) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    static List<ResearchTreeScene.Point> compact(List<ResearchTreeScene.Point> points) {
        var result = new ArrayList<ResearchTreeScene.Point>();
        for (var p : points) {
            if (!result.isEmpty() && result.get(result.size() - 1).equals(p)) continue;
            if (result.size() >= 2) {
                var a = result.get(result.size() - 2); var b = result.get(result.size() - 1);
                // Remove only a middle point, never a reversal.
                if ((a.x() == b.x() && b.x() == p.x() && (b.y() - a.y()) * (p.y() - b.y()) >= 0)
                        || (a.y() == b.y() && b.y() == p.y() && (b.x() - a.x()) * (p.x() - b.x()) >= 0)) result.remove(result.size() - 1);
            }
            result.add(p);
        }
        return List.copyOf(result);
    }
    private static ResearchTreeScene.Point point(double x, double y) { return new ResearchTreeScene.Point(x, y); }
}
